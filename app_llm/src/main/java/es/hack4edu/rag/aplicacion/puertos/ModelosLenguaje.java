package es.hack4edu.rag.aplicacion.puertos;

import es.hack4edu.rag.dominio.Asistencia;

/**
 * Puerto de salida: modelos de lenguaje que responden con las fichas recuperadas.
 * Todos usan las mismas instrucciones y el mismo índice, así que cambiar de modelo no obliga a reindexar.
 */
public interface ModelosLenguaje {

    /** Perfiles disponibles e indicación de si su modelo está descargado. */
    Asistencia.Listado listar();

    /**
     * Responde con el perfil indicado.
     *
     * @throws es.hack4edu.compartido.dominio.ErrorAplicacion si el perfil no existe o su modelo no está descargado
     */
    Asistencia.RespuestaModelo responder(String perfil, String pregunta);

    /** Nombre del modelo concreto detrás de un perfil, o null si no existe. */
    String modeloConcreto(String perfil);
}
