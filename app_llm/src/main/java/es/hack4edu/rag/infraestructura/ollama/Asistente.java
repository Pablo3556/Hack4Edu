package es.hack4edu.rag.infraestructura.ollama;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import es.hack4edu.rag.dominio.Instrucciones;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Asistente con el modelo de chat por defecto (perfil "equilibrado", quarkus.langchain4j.ollama.chat-model.*).
 * Los demás perfiles están en {@link AsistenteRapido} y {@link AsistentePreciso}; {@link ModelosOllama} elige.
 */
@RegisterAiService(
        retrievalAugmentor = RecuperadorFichas.class,
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface Asistente {

    @SystemMessage(Instrucciones.SISTEMA)
    Result<String> responder(@UserMessage String pregunta);
}
