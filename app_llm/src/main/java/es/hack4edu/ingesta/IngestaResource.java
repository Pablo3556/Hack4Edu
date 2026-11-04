package es.hack4edu.ingesta;

import es.hack4edu.datos.Consultas;
import es.hack4edu.rag.IngestaRag;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static es.hack4edu.datos.Consultas.entero;
import static es.hack4edu.datos.Consultas.error;
import static es.hack4edu.datos.Consultas.fecha;

/**
 * Importación de CSV por docentes, en dos fases:
 *   1. POST /api/ingesta                 → sube y valida; devuelve la vista previa (no escribe datos)
 *      PUT  /api/ingesta/{token}/mapeo   → cambia las correspondencias de columnas y revalida
 *   2. POST /api/ingesta/{token}/confirmar → solo entonces se escribe en la base de datos
 * Cada intento queda auditado en staging.carga (usuario, archivo, fecha, recuentos, estado).
 */
@Path("/api/ingesta")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
@RolesAllowed("docente")
public class IngestaResource {

    public record Periodo(int id, String codigo, String nombre, String fechaInicio, String fechaFin, boolean actual) {
    }

    public record VistaPrevia(String token, int cargaId, String archivo, String codificacion, String separador,
                              String ambito, String periodo, List<ValidadorIngesta.Columna> columnas,
                              ValidadorIngesta.Resumen resumen, String resumenTexto, List<String> problemasMapeo,
                              List<String> bloqueos, List<String> avisos,
                              List<ValidadorIngesta.Incidencia> incidencias, int incidenciasOmitidas,
                              ValidadorIngesta.Muestra muestra, boolean puedeConfirmar, String expira) {
    }

    /** Campo interno asignado a cada columna del archivo, por posición (null o "" = ignorar). */
    public record Mapeo(List<String> campos) {
    }

    public record Resultado(int cargaId, String estado, int filasCargadas, String ambito, String periodo,
                            String mensaje) {
    }

    public record Carga(Integer id, String fecha, String actualizado, String usuario, String archivo, String centro,
                        String grupo, String periodo, Integer filasProcesadas, Integer filasValidas,
                        Integer filasError, Integer filasCargadas, String estado, String resultado) {
    }

    private record Destino(String ambito, String periodo) {
    }

    @Inject
    SecurityIdentity identidad;

    @Inject
    Consultas consultas;

    @Inject
    RepositorioIngesta repositorio;

    @Inject
    AlmacenPrevisualizaciones almacen;

    @Inject
    IngestaRag rag;

    @ConfigProperty(name = "ingesta.max-bytes", defaultValue = "2097152")
    long maxBytes;

    @ConfigProperty(name = "ingesta.max-filas", defaultValue = "5000")
    int maxFilas;

    @ConfigProperty(name = "ingesta.max-columnas", defaultValue = "60")
    int maxColumnas;

    /** Esquema interno: campos, cuáles son obligatorios y qué valores admiten. */
    @GET
    @Path("/campos")
    public List<Map<String, Object>> campos() {
        return ValidadorIngesta.esquema();
    }

    /** Periodos a los que se puede importar (la línea base sintética no). */
    @GET
    @Path("/periodos")
    public List<Periodo> periodos() {
        return consultas.lista("""
                SELECT id, codigo, nombre, fecha_inicio::text AS ini, fecha_fin::text AS fin,
                       coalesce(current_date BETWEEN fecha_inicio AND fecha_fin, false) AS actual
                FROM analitica.periodo
                WHERE NOT sintetico
                ORDER BY orden""", List.of(),
                rs -> new Periodo(rs.getInt("id"), rs.getString("codigo"), rs.getString("nombre"),
                        rs.getString("ini"), rs.getString("fin"), rs.getBoolean("actual")));
    }

    /** Fase 1: subida y validación. No escribe ningún dato de alumnos. */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response subir(@RestForm("archivo") FileUpload archivo, @RestForm("centro") Integer centro,
                          @RestForm("grupo") Integer grupo, @RestForm("periodo") Integer periodo) {
        if (archivo == null || archivo.uploadedFile() == null) {
            throw error(Response.Status.BAD_REQUEST, "Selecciona un archivo CSV.");
        }
        byte[] bytes;
        String nombre = nombreSeguro(archivo.fileName());
        try {
            if (centro == null || grupo == null || periodo == null) {
                throw error(Response.Status.BAD_REQUEST, "Indica el centro, el grupo y el periodo de los datos.");
            }
            String ext = nombre.toLowerCase(Locale.ROOT);
            if (!ext.endsWith(".csv") && !ext.endsWith(".txt")) {
                throw error(Response.Status.BAD_REQUEST,
                        "Solo se admiten archivos .csv. Desde la hoja de cálculo: «Guardar como → CSV».");
            }
            if (archivo.size() > maxBytes) {
                throw error(Response.Status.REQUEST_ENTITY_TOO_LARGE, "El archivo ocupa "
                        + (archivo.size() / 1024) + " KB; el máximo es " + (maxBytes / 1024) + " KB.");
            }
            bytes = Files.readAllBytes(archivo.uploadedFile());
        } catch (IOException e) {
            throw error(Response.Status.BAD_REQUEST, "No se pudo leer el archivo subido.");
        } finally {
            borrar(archivo.uploadedFile());
        }

        Destino destino = destino(centro, grupo, periodo);
        String usuario = usuario();

        LectorCsv.Archivo csv;
        try {
            csv = LectorCsv.leer(bytes, new LectorCsv.Limites(maxFilas, maxColumnas, 500));
        } catch (LectorCsv.CsvNoValido e) {
            auditarRechazo(usuario, nombre, grupo, periodo, e.getMessage());
            throw error(Response.Status.BAD_REQUEST, e.getMessage());
        }

        ValidadorIngesta.Informe informe = ValidadorIngesta.validar(csv, ValidadorIngesta.proponerMapeo(csv.cabecera()));
        int cargaId;
        try {
            cargaId = repositorio.abrirCarga(usuario, nombre, grupo, periodo, informe.resumen().filasTotal(),
                    informe.resumen().filasValidas(), informe.resumen().filasConError(), estado(informe),
                    informe.resumenTexto());
        } catch (SQLException e) {
            throw error(Response.Status.SERVICE_UNAVAILABLE,
                    "No se pudo registrar la importación: " + RepositorioIngesta.mensaje(e));
        }
        AlmacenPrevisualizaciones.Previsualizacion p;
        try {
            p = almacen.guardar(usuario, cargaId, nombre, grupo, periodo, destino.ambito(), destino.periodo(), csv,
                    informe);
        } catch (IllegalStateException e) {
            cerrarSinVista(cargaId, e.getMessage());
            throw error(Response.Status.SERVICE_UNAVAILABLE, e.getMessage());
        }
        return Response.ok(vista(p)).build();
    }

    /** Cambia el mapeo de columnas y vuelve a validar. */
    @PUT
    @Path("/{token}/mapeo")
    @Consumes(MediaType.APPLICATION_JSON)
    public VistaPrevia remapear(@PathParam("token") String token, Mapeo mapeo) {
        AlmacenPrevisualizaciones.Previsualizacion p = previa(token);
        if (mapeo == null || mapeo.campos() == null) {
            throw error(Response.Status.BAD_REQUEST, "Falta el mapeo de columnas.");
        }
        if (mapeo.campos().size() != p.csv.cabecera().size()) {
            throw error(Response.Status.BAD_REQUEST, "El mapeo debe tener una entrada por columna del archivo ("
                    + p.csv.cabecera().size() + ").");
        }
        List<ValidadorIngesta.Columna> columnas = ValidadorIngesta.aplicarMapeo(p.csv.cabecera(),
                p.informe.columnas(), mapeo.campos());
        ValidadorIngesta.Informe informe = ValidadorIngesta.validar(p.csv, columnas);
        try {
            repositorio.actualizarCarga(p.cargaId, estado(informe), informe.resumen().filasValidas(),
                    informe.resumen().filasConError(), informe.resumenTexto() + " (mapeo revisado)");
        } catch (SQLException e) {
            throw error(Response.Status.SERVICE_UNAVAILABLE,
                    "No se pudo registrar la revalidación: " + RepositorioIngesta.mensaje(e));
        }
        p.informe = informe;
        return vista(p);
    }

    /** Fase 2: el docente confirma y se escriben los datos. */
    @POST
    @Path("/{token}/confirmar")
    public Resultado confirmar(@PathParam("token") String token) {
        AlmacenPrevisualizaciones.Previsualizacion p = previa(token);
        ValidadorIngesta.Informe informe = p.informe;
        if (!informe.puedeConfirmar()) {
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "La importación no se puede confirmar todavía.",
                            "bloqueos", informe.bloqueos()))
                    .build());
        }
        try {
            repositorio.confirmar(p.cargaId, informe.registros());
        } catch (SQLException e) {
            String motivo = RepositorioIngesta.mensaje(e);
            Log.warnf("Ingesta: la carga %d ha fallado al transformar: %s", p.cargaId, motivo);
            almacen.cerrar(p, "error", "La transformación ha fallado: " + motivo);
            throw error(Response.Status.fromStatusCode(422),
                    "La base de datos ha rechazado la importación y no se ha guardado nada: " + motivo);
        }
        almacen.quitar(p);
        reindexarAsistente();
        int n = informe.registros().size();
        return new Resultado(p.cargaId, "completada", n, p.ambito, p.periodo,
                n + " registros incorporados a " + p.ambito + " (" + p.periodo + "). Los indicadores, la evolución "
                        + "y el asistente ya usan los datos nuevos.");
    }

    /** Descarta la vista previa sin importar nada. */
    @DELETE
    @Path("/{token}")
    public Response cancelar(@PathParam("token") String token) {
        AlmacenPrevisualizaciones.Previsualizacion p = previa(token);
        almacen.cerrar(p, "cancelada", "Cancelada por el docente antes de confirmar; los datos se descartaron.");
        return Response.noContent().build();
    }

    /** Últimas importaciones: las propias (docente) o todas (dirección). */
    @GET
    @Path("/historial")
    public List<Carga> historial() {
        boolean todas = identidad.hasRole("direccion");
        String sql = "SELECT * FROM analitica.vw_cargas WHERE tipo = 'docente'"
                + (todas ? "" : " AND usuario = ?") + " ORDER BY id DESC LIMIT 20";
        return consultas.lista(sql, todas ? List.of() : List.of(usuario()),
                rs -> new Carga(entero(rs, "id"), fecha(rs, "fecha"), fecha(rs, "actualizado"),
                        rs.getString("usuario"), rs.getString("archivo"), rs.getString("centro"),
                        rs.getString("grupo"), rs.getString("periodo"), entero(rs, "filas_procesadas"),
                        entero(rs, "filas_validas"), entero(rs, "filas_error"), entero(rs, "filas_cargadas"),
                        rs.getString("estado"), rs.getString("resultado")));
    }

    /** Plantilla CSV con los nombres de columna en castellano (Excel la abre bien: «;» y BOM). */
    @GET
    @Path("/plantilla")
    @Produces("text/csv")
    public Response plantilla() {
        List<CampoIngesta> campos = List.of(CampoIngesta.values());
        String cabecera = campos.stream().map(CampoIngesta::etiqueta).collect(Collectors.joining(";"));
        String ejemplo1 = "72;91;18;68;2;7;3;media;alta;media;media;alta;sí;sí;positiva;grado;cerca;mujer";
        String ejemplo2 = "64;78;12;61;1;6;2;baja;media;baja;baja;media;no;sí;neutra;secundaria;media;hombre";
        String csv = "﻿" + cabecera + "\r\n" + ejemplo1 + "\r\n" + ejemplo2 + "\r\n";
        return Response.ok(csv.getBytes(StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=\"plantilla_importacion.csv\"")
                .build();
    }

    // -----------------------------------------------------------------

    private AlmacenPrevisualizaciones.Previsualizacion previa(String token) {
        return almacen.obtener(token, usuario()).orElseThrow(() -> error(Response.Status.NOT_FOUND,
                "Esta vista previa ya no existe (caducó, se canceló o pertenece a otro usuario). Vuelve a subir el archivo."));
    }

    private Destino destino(int centro, int grupo, int periodo) {
        List<String> ambito = consultas.lista("""
                SELECT c.nombre || ' · ' || g.nombre AS nombre
                FROM analitica.grupo g JOIN analitica.centro c ON c.id = g.centro_id
                WHERE g.id = ? AND g.centro_id = ?""", List.of(grupo, centro), rs -> rs.getString("nombre"));
        if (ambito.isEmpty()) {
            throw error(Response.Status.BAD_REQUEST, "El grupo elegido no pertenece a ese centro.");
        }
        List<String> nombrePeriodo = consultas.lista(
                "SELECT nombre FROM analitica.periodo WHERE id = ? AND NOT sintetico", List.of(periodo),
                rs -> rs.getString("nombre"));
        if (nombrePeriodo.isEmpty()) {
            throw error(Response.Status.BAD_REQUEST, "El periodo elegido no existe o no admite importaciones.");
        }
        return new Destino(ambito.get(0), nombrePeriodo.get(0));
    }

    private void auditarRechazo(String usuario, String archivo, int grupo, int periodo, String motivo) {
        try {
            repositorio.abrirCarga(usuario, archivo, grupo, periodo, 0, 0, 0, "rechazada", motivo);
        } catch (SQLException e) {
            Log.warnf("Ingesta: no se pudo auditar un archivo rechazado: %s", RepositorioIngesta.mensaje(e));
        }
    }

    private void cerrarSinVista(int cargaId, String motivo) {
        try {
            repositorio.actualizarCarga(cargaId, "cancelada", null, null, motivo);
        } catch (SQLException e) {
            Log.warnf("Ingesta: no se pudo cerrar la carga %d: %s", cargaId, RepositorioIngesta.mensaje(e));
        }
    }

    /** Las fichas del asistente se regeneran en segundo plano con los datos nuevos. */
    private void reindexarAsistente() {
        Thread.ofVirtual().name("reindexado-tras-ingesta").start(() -> {
            try {
                rag.ingestar();
            } catch (Exception e) {
                Log.warnf("RAG: no se pudo reindexar tras la importación (¿Ollama apagado?): %s", e.getMessage());
            }
        });
    }

    private static String estado(ValidadorIngesta.Informe informe) {
        return informe.puedeConfirmar() ? "pendiente" : "rechazada";
    }

    private VistaPrevia vista(AlmacenPrevisualizaciones.Previsualizacion p) {
        ValidadorIngesta.Informe i = p.informe;
        return new VistaPrevia(p.token, p.cargaId, p.archivo, p.csv.codificacion(), p.csv.separadorLegible(),
                p.ambito, p.periodo, i.columnas(), i.resumen(), i.resumenTexto(), i.problemasMapeo(), i.bloqueos(),
                i.avisos(), i.incidencias(), i.incidenciasOmitidas(), i.muestra(), i.puedeConfirmar(),
                p.expira.toString());
    }

    private String usuario() {
        return identidad.getPrincipal().getName();
    }

    /** Solo el nombre (sin rutas), sin caracteres de control y con longitud acotada. */
    static String nombreSeguro(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "sin_nombre.csv";
        }
        String base = nombre.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (base.isEmpty()) {
            return "sin_nombre.csv";
        }
        return base.length() > 120 ? base.substring(0, 120) : base;
    }

    private static void borrar(java.nio.file.Path ruta) {
        if (ruta == null) return;
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException e) {
            Log.warnf("Ingesta: no se pudo borrar el temporal %s", ruta);
        }
    }
}
