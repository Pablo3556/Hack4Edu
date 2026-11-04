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
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class AsistenteResource {

    public record Pregunta(String pregunta) {
    }

    public record Fuente(Map<String, Object> metadatos, String extracto) {
    }

    public record Respuesta(String respuesta, List<Fuente> fuentes) {
    }

    @Inject
    Asistente asistente;

    @Inject
    IngestaRag ingesta;

    /** Pregunta al asistente. Ej.: {"pregunta": "¿Qué deberíamos priorizar en el Centro Demo 03?"} */
    @POST
    @Path("/asistente")
    public Respuesta preguntar(Pregunta p) {
        if (p == null || p.pregunta() == null || p.pregunta().isBlank()) {
            throw new BadRequestException("Falta el campo 'pregunta'");
        }
        Result<String> r = asistente.responder(p.pregunta().strip());
        List<Fuente> fuentes = r.sources() == null ? List.of() : r.sources().stream().map(AsistenteResource::fuente).toList();
        return new Respuesta(r.content(), fuentes);
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
