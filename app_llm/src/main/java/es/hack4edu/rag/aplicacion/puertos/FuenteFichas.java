package es.hack4edu.rag.aplicacion.puertos;

import es.hack4edu.rag.dominio.Ficha;

import java.util.List;

/** Puerto de salida: genera las fichas de texto a partir de los datos agregados. */
public interface FuenteFichas {

    List<Ficha> generar();
}
