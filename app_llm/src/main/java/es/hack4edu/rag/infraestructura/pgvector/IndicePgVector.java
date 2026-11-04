package es.hack4edu.rag.infraestructura.pgvector;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import es.hack4edu.rag.aplicacion.puertos.IndiceFichas;
import es.hack4edu.rag.dominio.Ficha;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Adaptador de salida: índice vectorial en PostgreSQL (pgvector, tabla ia.embeddings) con los embeddings
 * calculados por Ollama a través de LangChain4j.
 */
@ApplicationScoped
public class IndicePgVector implements IndiceFichas {

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<TextSegment> store;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.embedding-model.model-id")
    String modeloEmbeddings;

    @Override
    public void reemplazar(List<Ficha> fichas) {
        List<TextSegment> segmentos = fichas.stream().map(IndicePgVector::segmento).toList();
        List<Embedding> embeddings = embeddingModel.embedAll(segmentos).content();
        store.removeAll();
        store.addAll(embeddings, segmentos);
    }

    @Override
    public int numeroFichas() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM ia.embeddings")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("No se puede leer ia.embeddings: " + e.getMessage(), e);
        }
    }

    @Override
    public String modeloEmbeddings() {
        return modeloEmbeddings;
    }

    private static TextSegment segmento(Ficha f) {
        Metadata m = new Metadata();
        f.metadatos().forEach((k, v) -> {
            if (v instanceof Integer i) {
                m.put(k, i);
            } else {
                m.put(k, String.valueOf(v));
            }
        });
        return TextSegment.from(f.texto(), m);
    }
}
