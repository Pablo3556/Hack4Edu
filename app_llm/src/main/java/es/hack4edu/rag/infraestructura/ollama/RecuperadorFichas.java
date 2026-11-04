package es.hack4edu.rag.infraestructura.ollama;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import es.hack4edu.compartido.infraestructura.web.ControlAcceso;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.function.Supplier;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Recuperación: busca en ia.embeddings las fichas más parecidas a la pregunta,
 * solo entre las del ámbito del usuario (además de las globales, que sirven de referencia).
 */
@ApplicationScoped
public class RecuperadorFichas implements Supplier<RetrievalAugmentor> {

    @Inject
    EmbeddingStore<TextSegment> store;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    ControlAcceso acceso;

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
                            .dynamicFilter(query -> filtro(query.text()))
                            .build())
                    .build();
        }
        return augmentor;
    }

    /**
     * Filtro de la pregunta limitado al ámbito del usuario. El docente solo tiene su grupo, así que
     * se busca siempre en sus fichas (aunque la pregunta no lo nombre) y en las globales.
     */
    private Filter filtro(String pregunta) {
        ControlAcceso.Permiso p = acceso.permiso();
        Filter propio = filtroDelUsuario(p);
        return "docente".equals(p.rol()) ? propio : FiltroAmbito.desde(pregunta).and(propio);
    }

    /** Fichas que puede ver el usuario de la petición (ver ControlAcceso). */
    private static Filter filtroDelUsuario(ControlAcceso.Permiso p) {
        Filter global = metadataKey("ambito").isEqualTo("global");
        return switch (p.rol()) {
            case "administracion" -> metadataKey("ambito").isNotEqualTo("grupo");
            case "direccion" -> global.or(metadataKey("centro_id").isEqualTo(p.centro()));
            default -> global.or(metadataKey("grupo_id").isEqualTo(p.grupo()));
        };
    }
}
