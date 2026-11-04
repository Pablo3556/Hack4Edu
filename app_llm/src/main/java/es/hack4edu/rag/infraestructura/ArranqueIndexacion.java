package es.hack4edu.rag.infraestructura;

import es.hack4edu.rag.aplicacion.IndexarFichas;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Adaptador de entrada: al arrancar la aplicación, indexa las fichas si el índice está vacío. */
@ApplicationScoped
public class ArranqueIndexacion {

    @Inject
    IndexarFichas indexador;

    @ConfigProperty(name = "rag.ingesta.al-arrancar", defaultValue = "true")
    boolean alArrancar;

    void alIniciar(@Observes StartupEvent ev) {
        if (!alArrancar) {
            return;
        }
        // En segundo plano para no retrasar el arranque de la app
        Thread.ofVirtual().name("ingesta-rag").start(() -> {
            try {
                indexador.ingestarSiEstaVacio();
            } catch (IllegalStateException e) {
                // Lo normal en una instalación nueva: todavía no hay datos cargados
                Log.warnf("RAG: no se ha indexado al arrancar (%s). Cuando haya datos y Ollama esté listo: "
                        + "POST /api/rag/ingesta", e.getMessage());
            } catch (Exception e) {
                Log.errorf(e, "RAG: la indexación inicial ha fallado. ¿Están cargados los datos y arrancado Ollama?");
            }
        });
    }
}
