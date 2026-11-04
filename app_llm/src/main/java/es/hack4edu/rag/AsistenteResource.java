package es.hack4edu.rag;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class AsistenteResource {

    /** modelo es opcional: "rapido", "equilibrado" (por defecto) o "preciso" (ver GET /api/modelos). */
    public record Pregunta(String pregunta, String modelo) {
    }

    public record Fuente(Map<String, Object> metadatos, String extracto) {
    }

    public record Respuesta(String respuesta, String modelo, String modeloOllama, List<Fuente> fuentes) {
    }

    @Inject
    CatalogoModelos catalogo;

    @Inject
    IngestaRag ingesta;

    /**
     * Pregunta al asistente. Ej.:
     * {"pregunta": "¿Qué deberíamos priorizar en el Centro Demo 03?", "modelo": "preciso"}
     */
    @POST
    @Path("/asistente")
    public Respuesta preguntar(Pregunta p) {
        if (p == null || p.pregunta() == null || p.pregunta().isBlank()) {
            throw new BadRequestException("Falta el campo 'pregunta'");
        }
        String modelo = (p.modelo() == null || p.modelo().isBlank()) ? CatalogoModelos.POR_DEFECTO : p.modelo().strip().toLowerCase();
        Result<String> r;
        try {
            r = catalogo.responder(modelo, p.pregunta().strip());
        } catch (CatalogoModelos.ModeloNoDisponibleException e) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON).entity(Map.of("error", e.getMessage())).build());
        }
        List<Fuente> fuentes = r.sources() == null ? List.of() : r.sources().stream().map(AsistenteResource::fuente).toList();
        return new Respuesta(r.content(), modelo, catalogo.modeloOllama(modelo), fuentes);
    }

    /** Modelos que puede elegir el usuario y si están descargados en Ollama. */
    @GET
    @Path("/modelos")
    public CatalogoModelos.Listado modelos() {
        return catalogo.listar();
    }

    /** Regenera el índice (tras recargar el CSV o cambiar de modelo de embeddings). */
    @POST
    @Path("/rag/ingesta")
    public Map<String, Object> reindexar() {
        return Map.of("fichas", ingesta.ingestar(), "modeloEmbeddings", ingesta.modeloEmbeddings());
    }

    @GET
    @Path("/rag/estado")
    public Map<String, Object> estado() {
        return Map.of("fichas", ingesta.numeroFichas(), "modeloEmbeddings", ingesta.modeloEmbeddings());
    }

    private static Fuente fuente(Content c) {
        String texto = c.textSegment().text();
        return new Fuente(c.textSegment().metadata().toMap(), texto.length() > 200 ? texto.substring(0, 200) + "…" : texto);
    }
}
