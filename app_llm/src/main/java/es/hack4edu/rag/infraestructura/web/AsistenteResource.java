package es.hack4edu.rag.infraestructura.web;

import es.hack4edu.rag.aplicacion.IndexarFichas;
import es.hack4edu.rag.aplicacion.PreguntarAlAsistente;
import es.hack4edu.rag.dominio.Asistencia;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/** Adaptador de entrada REST del asistente y de la gestión de su índice. */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class AsistenteResource {

    @Inject
    PreguntarAlAsistente asistente;

    @Inject
    IndexarFichas indexador;

    /**
     * Pregunta al asistente. Ej.:
     * {"pregunta": "¿Qué deberíamos priorizar en el Centro Demo 03?", "modelo": "preciso"}
     * "modelo" es opcional: "rapido", "equilibrado" (por defecto) o "preciso" (ver GET /api/modelos).
     */
    @POST
    @Path("/asistente")
    public Asistencia.Respuesta preguntar(Asistencia.Pregunta p) {
        return asistente.preguntar(p);
    }

    /** Modelos que puede elegir el usuario y si están descargados en Ollama. */
    @GET
    @Path("/modelos")
    public Asistencia.Listado modelos() {
        return asistente.modelos();
    }

    /** Regenera el índice (tras recargar el CSV o cambiar de modelo de embeddings). */
    @POST
    @Path("/rag/ingesta")
    @RolesAllowed({"direccion", "administracion"})
    public Map<String, Object> reindexar() {
        return Map.of("fichas", indexador.ingestar(), "modeloEmbeddings", indexador.modeloEmbeddings());
    }

    @GET
    @Path("/rag/estado")
    public Map<String, Object> estado() {
        return Map.of("fichas", indexador.numeroFichas(), "modeloEmbeddings", indexador.modeloEmbeddings());
    }
}
