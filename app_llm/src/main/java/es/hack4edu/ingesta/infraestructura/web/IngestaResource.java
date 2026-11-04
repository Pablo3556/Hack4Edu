package es.hack4edu.ingesta.infraestructura.web;

import es.hack4edu.compartido.dominio.ErrorAplicacion;
import es.hack4edu.compartido.infraestructura.web.ControlAcceso;
import es.hack4edu.ingesta.aplicacion.ImportarDatos;
import es.hack4edu.ingesta.dominio.Importacion;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.PermitAll;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de entrada REST de la importación de CSV por docentes (solo traduce HTTP y delega en ImportarDatos):
 *   POST /api/ingesta                  → sube y valida; devuelve la vista previa (no escribe datos)
 *   PUT  /api/ingesta/{token}/mapeo    → cambia las correspondencias de columnas y revalida
 *   POST /api/ingesta/{token}/confirmar → solo entonces se escribe en la base de datos
 */
@Path("/api/ingesta")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
@RolesAllowed("docente")
public class IngestaResource {

    /** Campo interno asignado a cada columna del archivo, por posición (null o "" = ignorar). */
    public record Mapeo(List<String> campos) {
    }

    @Inject
    SecurityIdentity identidad;

    @Inject
    ControlAcceso acceso;

    @Inject
    ImportarDatos importacion;

    /** Esquema interno: campos, cuáles son obligatorios y qué valores admiten. */
    @GET
    @Path("/campos")
    public List<Map<String, Object>> campos() {
        return importacion.campos();
    }

    /** Periodos a los que se puede importar (la línea base sintética no). */
    @GET
    @Path("/periodos")
    public List<Importacion.Periodo> periodos() {
        return importacion.periodos();
    }

    /** Fase 1: subida y validación. No escribe ningún dato de alumnos. */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response subir(@RestForm("archivo") FileUpload archivo, @RestForm("centro") Integer centro,
                          @RestForm("grupo") Integer grupo, @RestForm("periodo") Integer periodo) {
        if (archivo == null || archivo.uploadedFile() == null) {
            throw ErrorAplicacion.peticionInvalida("Selecciona un archivo CSV.");
        }
        String nombre = ImportarDatos.nombreSeguro(archivo.fileName());
        byte[] bytes;
        try {
            acceso.comprobar(centro, grupo);
            importacion.validarSubida(nombre, archivo.size(), centro, grupo, periodo);
            bytes = Files.readAllBytes(archivo.uploadedFile());
        } catch (IOException e) {
            throw ErrorAplicacion.peticionInvalida("No se pudo leer el archivo subido.");
        } finally {
            borrar(archivo.uploadedFile());
        }
        return Response.ok(importacion.subir(bytes, nombre, usuario(), centro, grupo, periodo)).build();
    }

    /** Cambia el mapeo de columnas y vuelve a validar. */
    @PUT
    @Path("/{token}/mapeo")
    @Consumes(MediaType.APPLICATION_JSON)
    public Importacion.VistaPrevia remapear(@PathParam("token") String token, Mapeo mapeo) {
        return importacion.remapear(token, usuario(), mapeo == null ? null : mapeo.campos());
    }

    /** Fase 2: el docente confirma y se escriben los datos. */
    @POST
    @Path("/{token}/confirmar")
    public Importacion.Resultado confirmar(@PathParam("token") String token) {
        return importacion.confirmar(token, usuario());
    }

    /** Descarta la vista previa sin importar nada. */
    @DELETE
    @Path("/{token}")
    public Response cancelar(@PathParam("token") String token) {
        importacion.cancelar(token, usuario());
        return Response.noContent().build();
    }

    /** Últimas importaciones: las propias (docente) o todas (dirección). */
    @GET
    @Path("/historial")
    public List<Importacion.Carga> historial() {
        return importacion.historial(identidad.hasRole("direccion"), usuario());
    }

    /**
     * Pública: no contiene datos y se descarga con un enlace normal, donde el navegador no pide la
     * contraseña (con @RolesAllowed el enlace fallaba con 401 antes de iniciar sesión).
     */
    @GET
    @Path("/plantilla")
    @PermitAll
    @Produces("text/csv")
    public Response plantilla() {
        return Response.ok(importacion.plantilla().getBytes(StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=\"plantilla_importacion.csv\"")
                .build();
    }

    private String usuario() {
        return identidad.getPrincipal().getName();
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
