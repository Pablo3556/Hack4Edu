package es.hack4edu.rag.aplicacion;

import es.hack4edu.rag.aplicacion.puertos.FuenteFichas;
import es.hack4edu.rag.aplicacion.puertos.IndiceFichas;
import es.hack4edu.rag.dominio.Ficha;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/** Caso de uso: indexación. Genera las fichas, las convierte en vectores y las guarda en el índice. */
@ApplicationScoped
public class IndexarFichas {

    @Inject
    FuenteFichas fuente;

    @Inject
    IndiceFichas indice;

    /** Reconstruye el índice completo. Devuelve el número de fichas indexadas. */
    public synchronized int ingestar() {
        List<Ficha> fichas = fuente.generar();
        if (fichas.size() <= 2) {
            throw new IllegalStateException("No hay datos agregados: ejecuta antes ./scripts/cargar_kaggle.sh");
        }
        Log.infof("RAG: calculando embeddings de %d fichas con %s…", fichas.size(), indice.modeloEmbeddings());
        long t0 = System.currentTimeMillis();
        indice.reemplazar(fichas);
        Log.infof("RAG: %d fichas indexadas en %d s", fichas.size(), (System.currentTimeMillis() - t0) / 1000);
        return fichas.size();
    }

    /** Indexa solo si el índice está vacío (arranque de la aplicación). */
    public void ingestarSiEstaVacio() {
        int n = indice.numeroFichas();
        if (n == 0) {
            ingestar();
        } else {
            Log.infof("RAG: el índice ya tiene %d fichas; no se reindexa (POST /api/rag/ingesta para forzar)", n);
        }
    }

    public int numeroFichas() {
        return indice.numeroFichas();
    }

    public String modeloEmbeddings() {
        return indice.modeloEmbeddings();
    }
}
