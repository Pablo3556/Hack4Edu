package es.hack4edu.rag.dominio;

import java.util.Map;

/**
 * Trozo de texto sobre datos AGREGADOS que el asistente puede recuperar. Nunca contiene datos de un alumno.
 * Metadatos: tipo (resumen | prioridades | equidad | factor | metodologia | calidad), ambito
 * (global | centro | grupo), centro_id y grupo_id; sirven para filtrar la búsqueda.
 */
public record Ficha(String texto, Map<String, Object> metadatos) {
}
