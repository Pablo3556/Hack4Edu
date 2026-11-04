package es.hack4edu.rag.infraestructura.ollama;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import es.hack4edu.rag.dominio.Instrucciones;
import io.quarkiverse.langchain4j.RegisterAiService;

/** Mismo asistente con el perfil "preciso" (quarkus.langchain4j.ollama.preciso.*). */
@RegisterAiService(
        modelName = "preciso",
        retrievalAugmentor = RecuperadorFichas.class,
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface AsistentePreciso {

    @SystemMessage(Instrucciones.SISTEMA)
    Result<String> responder(@UserMessage String pregunta);
}
