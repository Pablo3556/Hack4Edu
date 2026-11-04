package es.hack4edu.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Indexación: genera las fichas, calcula sus embeddings y las guarda en ia.embeddings. */
@ApplicationScoped
public class IngestaRag {

    @Inject
    GeneradorFichas generador;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<TextSegment> store;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "rag.ingesta.al-arrancar", defaultValue = "true")
    boolean alArrancar;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.embedding-model.model-id")
    String modeloEmbeddings;

    void alIniciar(@Observes StartupEvent ev) {
        if (!alArrancar) {
            return;
        }
        // En segundo plano para no retrasar el arranque de la app
        Thread.ofVirtual().name("ingesta-rag").start(() -> {
            try {
                if (numeroFichas() == 0) {
                    ingestar();
                } else {
                    Log.infof("RAG: ia.embeddings ya tiene %d fichas; no se reindexa (POST /api/rag/ingesta para forzar)",
                            numeroFichas());
                }
            } catch (Exception e) {
                Log.errorf(e, "RAG: la indexación inicial ha fallado. ¿Están cargados los datos y arrancado Ollama?");
            }
        });
    }

    /** Reconstruye el índice completo. Devuelve el número de fichas indexadas. */
    public synchronized int ingestar() {
        List<TextSegment> fichas = generador.generar();
        if (fichas.size() <= 2) {
            throw new IllegalStateException("No hay datos agregados: ejecuta antes ./scripts/cargar_kaggle.sh");
        }
        Log.infof("RAG: calculando embeddings de %d fichas con %s…", fichas.size(), modeloEmbeddings);
        long t0 = System.currentTimeMillis();
        List<Embedding> embeddings = embeddingModel.embedAll(fichas).content();
        store.removeAll();
        store.addAll(embeddings, fichas);
        Log.infof("RAG: %d fichas indexadas en %d s", fichas.size(), (System.currentTimeMillis() - t0) / 1000);
        return fichas.size();
    }

    public int numeroFichas() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM ia.embeddings")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("No se puede leer ia.embeddings: " + e.getMessage(), e);
        }
    }

    public String modeloEmbeddings() {
        return modeloEmbeddings;
    }
}
