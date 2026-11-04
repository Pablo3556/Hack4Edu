package es.hack4edu.rag.aplicacion.puertos;

import es.hack4edu.rag.dominio.Ficha;

import java.util.List;

/** Puerto de salida: índice de búsqueda por similitud sobre las fichas. */
public interface IndiceFichas {

    /** Calcula los vectores de las fichas y sustituye el contenido del índice. */
    void reemplazar(List<Ficha> fichas);

    int numeroFichas();

    /** Modelo que calcula los vectores. */
    String modeloEmbeddings();
}
