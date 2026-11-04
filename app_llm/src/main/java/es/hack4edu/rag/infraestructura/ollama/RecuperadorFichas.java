package es.hack4edu.rag.infraestructura.ollama;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.function.Supplier;

/** Recuperación: busca en ia.embeddings las fichas más parecidas a la pregunta. */
@ApplicationScoped
public class RecuperadorFichas implements Supplier<RetrievalAugmentor> {

    @Inject
    EmbeddingStore<TextSegment> store;

    @Inject
    EmbeddingModel embeddingModel;

    @ConfigProperty(name = "rag.max-resultados", defaultValue = "6")
    int maxResultados;

    @ConfigProperty(name = "rag.score-minimo", defaultValue = "0.5")
    double scoreMinimo;

    private volatile RetrievalAugmentor augmentor;

    @Override
    public RetrievalAugmentor get() {
        if (augmentor == null) {
            augmentor = DefaultRetrievalAugmentor.builder()
                    .contentRetriever(EmbeddingStoreContentRetriever.builder()
                            .embeddingStore(store)
                            .embeddingModel(embeddingModel)
                            .maxResults(maxResultados)
                            .minScore(scoreMinimo)
                            .dynamicFilter(query -> FiltroAmbito.desde(query.text()))
                            .build())
                    .build();
        }
        return augmentor;
    }
}
