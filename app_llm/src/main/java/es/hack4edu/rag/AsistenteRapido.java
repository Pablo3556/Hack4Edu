package es.hack4edu.rag;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/** Mismo asistente con el perfil "rapido" (quarkus.langchain4j.ollama.rapido.*). */
@RegisterAiService(
        modelName = "rapido",
        retrievalAugmentor = RecuperadorFichas.class,
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface AsistenteRapido {

    @SystemMessage(Instrucciones.SISTEMA)
    Result<String> responder(@UserMessage String pregunta);
}
