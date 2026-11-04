package es.hack4edu.ingesta.aplicacion;

import es.hack4edu.compartido.dominio.ErrorAplicacion;
import es.hack4edu.ingesta.aplicacion.puertos.AlmacenVistasPrevias;
import es.hack4edu.ingesta.aplicacion.puertos.CatalogoImportacion;
import es.hack4edu.ingesta.aplicacion.puertos.FalloRepositorioCargas;
import es.hack4edu.ingesta.aplicacion.puertos.RepositorioCargas;
import es.hack4edu.ingesta.dominio.CampoIngesta;
import es.hack4edu.ingesta.dominio.Importacion;
import es.hack4edu.ingesta.dominio.LectorCsv;
import es.hack4edu.ingesta.dominio.Previsualizacion;
import es.hack4edu.ingesta.dominio.ValidadorIngesta;
import es.hack4edu.rag.aplicacion.IndexarFichas;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Casos de uso de la importación de CSV por docentes, en dos fases:
 *   1. subir: valida y devuelve la vista previa (no escribe datos de alumnos)
 *      remapear: cambia las correspondencias de columnas y revalida
 *   2. confirmar: solo entonces se escriben los datos
 * Cada intento queda auditado (usuario, archivo, fecha, recuentos, estado).
 */
@ApplicationScoped
public class ImportarDatos {

    @Inject
    RepositorioCargas cargas;

    @Inject
    CatalogoImportacion catalogo;

    @Inject
    AlmacenVistasPrevias almacen;

    @Inject
    IndexarFichas asistente;

    @ConfigProperty(name = "ingesta.max-bytes", defaultValue = "2097152")
    long maxBytes;

    @ConfigProperty(name = "ingesta.max-filas", defaultValue = "5000")
    int maxFilas;

    @ConfigProperty(name = "ingesta.max-columnas", defaultValue = "60")
    int maxColumnas;

    /** Esquema interno: campos, cuáles son obligatorios y qué valores admiten. */
    public List<Map<String, Object>> campos() {
        return ValidadorIngesta.esquema();
    }

    public List<Importacion.Periodo> periodos() {
        return catalogo.periodos();
    }

    /** Comprueba los datos de la petición antes de leer el archivo. */
    public void validarSubida(String nombre, long tamano, Integer centro, Integer grupo, Integer periodo) {
        if (centro == null || grupo == null || periodo == null) {
            throw ErrorAplicacion.peticionInvalida("Indica el centro, el grupo y el periodo de los datos.");
        }
        String ext = nombre.toLowerCase(Locale.ROOT);
        if (!ext.endsWith(".csv") && !ext.endsWith(".txt")) {
            throw ErrorAplicacion.peticionInvalida(
                    "Solo se admiten archivos .csv. Desde la hoja de cálculo: «Guardar como → CSV».");
        }
        if (tamano > maxBytes) {
            throw ErrorAplicacion.demasiadoGrande("El archivo ocupa " + (tamano / 1024) + " KB; el máximo es "
                    + (maxBytes / 1024) + " KB.");
        }
    }

    /** Fase 1: valida el archivo y deja una vista previa pendiente de confirmar. */
    public Importacion.VistaPrevia subir(byte[] bytes, String nombre, String usuario, int centro, int grupo, int periodo) {
        Importacion.Destino destino = destino(centro, grupo, periodo);

        LectorCsv.Archivo csv;
        try {
            csv = LectorCsv.leer(bytes, new LectorCsv.Limites(maxFilas, maxColumnas, 500));
        } catch (LectorCsv.CsvNoValido e) {
            auditarRechazo(usuario, nombre, grupo, periodo, e.getMessage());
            throw ErrorAplicacion.peticionInvalida(e.getMessage());
        }

        ValidadorIngesta.Informe informe = ValidadorIngesta.validar(csv, ValidadorIngesta.proponerMapeo(csv.cabecera()));
        int cargaId;
        try {
            cargaId = cargas.abrirCarga(usuario, nombre, grupo, periodo, informe.resumen().filasTotal(),
                    informe.resumen().filasValidas(), informe.resumen().filasConError(), estado(informe),
                    informe.resumenTexto());
        } catch (FalloRepositorioCargas e) {
            throw ErrorAplicacion.noDisponible("No se pudo registrar la importación: " + e.getMessage());
        }
        Previsualizacion p;
        try {
            p = almacen.guardar(usuario, cargaId, nombre, grupo, periodo, destino.ambito(), destino.periodo(), csv,
                    informe);
        } catch (IllegalStateException e) {
            cerrarSinVista(cargaId, e.getMessage());
            throw ErrorAplicacion.noDisponible(e.getMessage());
        }
        return vista(p);
    }

    /** Cambia el mapeo de columnas y vuelve a validar. */
    public Importacion.VistaPrevia remapear(String token, String usuario, List<String> campos) {
        Previsualizacion p = previa(token, usuario);
        if (campos == null) {
            throw ErrorAplicacion.peticionInvalida("Falta el mapeo de columnas.");
        }
        if (campos.size() != p.csv.cabecera().size()) {
            throw ErrorAplicacion.peticionInvalida("El mapeo debe tener una entrada por columna del archivo ("
                    + p.csv.cabecera().size() + ").");
        }
        List<ValidadorIngesta.Columna> columnas = ValidadorIngesta.aplicarMapeo(p.csv.cabecera(),
                p.informe.columnas(), campos);
        ValidadorIngesta.Informe informe = ValidadorIngesta.validar(p.csv, columnas);
        try {
            cargas.actualizarCarga(p.cargaId, estado(informe), informe.resumen().filasValidas(),
                    informe.resumen().filasConError(), informe.resumenTexto() + " (mapeo revisado)");
        } catch (FalloRepositorioCargas e) {
            throw ErrorAplicacion.noDisponible("No se pudo registrar la revalidación: " + e.getMessage());
        }
        p.informe = informe;
        return vista(p);
    }

    /** Fase 2: el docente confirma y se escriben los datos. */
    public Importacion.Resultado confirmar(String token, String usuario) {
        Previsualizacion p = previa(token, usuario);
        ValidadorIngesta.Informe informe = p.informe;
        if (!informe.puedeConfirmar()) {
            throw ErrorAplicacion.conflicto("La importación no se puede confirmar todavía.",
                    Map.of("bloqueos", informe.bloqueos()));
        }
        try {
            cargas.confirmar(p.cargaId, informe.registros());
        } catch (FalloRepositorioCargas e) {
            Log.warnf("Ingesta: la carga %d ha fallado al transformar: %s", p.cargaId, e.getMessage());
            almacen.cerrar(p, "error", "La transformación ha fallado: " + e.getMessage());
            throw ErrorAplicacion.noProcesable("La base de datos ha rechazado la importación y no se ha guardado nada: "
                    + e.getMessage());
        }
        almacen.quitar(p);
        reindexarAsistente();
        int n = informe.registros().size();
        return new Importacion.Resultado(p.cargaId, "completada", n, p.ambito, p.periodo,
                n + " registros incorporados a " + p.ambito + " (" + p.periodo + "). Los indicadores, la evolución "
                        + "y el asistente ya usan los datos nuevos.");
    }

    /** Descarta la vista previa sin importar nada. */
    public void cancelar(String token, String usuario) {
        Previsualizacion p = previa(token, usuario);
        almacen.cerrar(p, "cancelada", "Cancelada por el docente antes de confirmar; los datos se descartaron.");
    }

    /** Últimas importaciones: las propias (docente) o todas (dirección). */
    public List<Importacion.Carga> historial(boolean todas, String usuario) {
        return catalogo.historial(todas, usuario);
    }

    /** Plantilla CSV con los nombres de columna en castellano (Excel la abre bien: «;» y BOM). */
    public String plantilla() {
        List<CampoIngesta> campos = List.of(CampoIngesta.values());
        String cabecera = campos.stream().map(CampoIngesta::etiqueta).collect(Collectors.joining(";"));
        String ejemplo1 = "72;91;18;68;2;7;3;media;alta;media;media;alta;sí;sí;positiva;grado;cerca;mujer";
        String ejemplo2 = "64;78;12;61;1;6;2;baja;media;baja;baja;media;no;sí;neutra;secundaria;media;hombre";
        return "\uFEFF" + cabecera + "\r\n" + ejemplo1 + "\r\n" + ejemplo2 + "\r\n";
    }

    /** Solo el nombre (sin rutas), sin caracteres de control y con longitud acotada. */
    public static String nombreSeguro(String nombre) {
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

    // -----------------------------------------------------------------

    private Previsualizacion previa(String token, String usuario) {
        return almacen.obtener(token, usuario).orElseThrow(() -> ErrorAplicacion.noEncontrado(
                "Esta vista previa ya no existe (caducó, se canceló o pertenece a otro usuario). Vuelve a subir el archivo."));
    }

    private Importacion.Destino destino(int centro, int grupo, int periodo) {
        String ambito = catalogo.nombreDelGrupo(grupo, centro).orElseThrow(() ->
                ErrorAplicacion.peticionInvalida("El grupo elegido no pertenece a ese centro."));
        String nombrePeriodo = catalogo.nombreDelPeriodoImportable(periodo).orElseThrow(() ->
                ErrorAplicacion.peticionInvalida("El periodo elegido no existe o no admite importaciones."));
        return new Importacion.Destino(ambito, nombrePeriodo);
    }

    private void auditarRechazo(String usuario, String archivo, int grupo, int periodo, String motivo) {
        try {
            cargas.abrirCarga(usuario, archivo, grupo, periodo, 0, 0, 0, "rechazada", motivo);
        } catch (FalloRepositorioCargas e) {
            Log.warnf("Ingesta: no se pudo auditar un archivo rechazado: %s", e.getMessage());
        }
    }

    private void cerrarSinVista(int cargaId, String motivo) {
        try {
            cargas.actualizarCarga(cargaId, "cancelada", null, null, motivo);
        } catch (FalloRepositorioCargas e) {
            Log.warnf("Ingesta: no se pudo cerrar la carga %d: %s", cargaId, e.getMessage());
        }
    }

    /** Las fichas del asistente se regeneran en segundo plano con los datos nuevos. */
    private void reindexarAsistente() {
        Thread.ofVirtual().name("reindexado-tras-ingesta").start(() -> {
            try {
                asistente.ingestar();
            } catch (Exception e) {
                Log.warnf("RAG: no se pudo reindexar tras la importación (¿Ollama apagado?): %s", e.getMessage());
            }
        });
    }

    private static String estado(ValidadorIngesta.Informe informe) {
        return informe.puedeConfirmar() ? "pendiente" : "rechazada";
    }

    private static Importacion.VistaPrevia vista(Previsualizacion p) {
        ValidadorIngesta.Informe i = p.informe;
        return new Importacion.VistaPrevia(p.token, p.cargaId, p.archivo, p.csv.codificacion(), p.csv.separadorLegible(),
                p.ambito, p.periodo, i.columnas(), i.resumen(), i.resumenTexto(), i.problemasMapeo(), i.bloqueos(),
                i.avisos(), i.incidencias(), i.incidenciasOmitidas(), i.muestra(), i.puedeConfirmar(),
                p.expira.toString());
    }
}
