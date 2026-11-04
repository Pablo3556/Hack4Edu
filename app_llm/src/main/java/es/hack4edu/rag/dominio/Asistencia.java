package es.hack4edu.rag.dominio;

import java.util.List;
import java.util.Map;

/** Lo que ve el usuario al preguntar al asistente. */
public final class Asistencia {

    private Asistencia() {
    }

    public static final String MODELO_POR_DEFECTO = "equilibrado";

    public record Pregunta(String pregunta, String modelo) {
    }

    /** Ficha en la que se apoyó la respuesta. */
    public record Fuente(Map<String, Object> metadatos, String extracto) {
    }

    /** Respuesta de un modelo, antes de saber cuál era. */
    public record RespuestaModelo(String contenido, List<Fuente> fuentes) {
    }

    public record Respuesta(String respuesta, String modelo, String modeloOllama, List<Fuente> fuentes) {
    }

    /** Perfil de modelo que puede elegir el usuario. instalado = null si no se sabe (Ollama sin respuesta). */
    public record Modelo(String id, String nombre, String descripcion, String modeloOllama,
                         boolean porDefecto, Boolean instalado) {
    }

    public record Listado(boolean ollamaDisponible, List<Modelo> modelos) {
    }
}
