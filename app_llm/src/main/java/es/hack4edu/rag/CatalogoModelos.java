package es.hack4edu.rag;

import dev.langchain4j.service.Result;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Modelos de chat que puede elegir el usuario. Todos usan las mismas instrucciones,
 * el mismo RAG y el mismo modelo de embeddings: cambiar de modelo no obliga a reindexar.
 * Los modelos concretos de Ollama se configuran en application.properties.
 */
@ApplicationScoped
public class CatalogoModelos {

    public static final String POR_DEFECTO = "equilibrado";

    /** Lo que ve el usuario al elegir. */
    public record Modelo(String id, String nombre, String descripcion, String modeloOllama,
                         boolean porDefecto, Boolean instalado) {
    }

    /** Ollama sin respuesta: instalado = null (desconocido). */
    public record Listado(boolean ollamaDisponible, List<Modelo> modelos) {
    }

    public static class ModeloNoDisponibleException extends RuntimeException {
        public ModeloNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }

    private record Perfil(String nombre, String descripcion, String modeloOllama,
                          Function<String, Result<String>> asistente) {
    }

    @Inject
    Asistente equilibrado;

    @Inject
    AsistenteRapido rapido;

    @Inject
    AsistentePreciso preciso;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.base-url", defaultValue = "http://localhost:11434")
    String ollamaUrl;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.chat-model.model-id")
    String modeloEquilibrado;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.rapido.chat-model.model-id")
    String modeloRapido;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.preciso.chat-model.model-id")
    String modeloPreciso;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private Map<String, Perfil> perfiles;

    @PostConstruct
    void iniciar() {
        perfiles = new LinkedHashMap<>();
        perfiles.put("rapido", new Perfil("Rápido",
                "Responde antes, con menos detalle. Útil si el equipo va justo de memoria o CPU.",
                modeloRapido, rapido::responder));
        perfiles.put(POR_DEFECTO, new Perfil("Equilibrado",
                "Buen equilibrio entre velocidad y calidad. Recomendado para la demo.",
                modeloEquilibrado, equilibrado::responder));
        perfiles.put("preciso", new Perfil("Preciso",
                "Respuestas más cuidadas, pero más lento y con más memoria (~5,5 GB).",
                modeloPreciso, preciso::responder));
    }

    /** Lista los perfiles e indica si su modelo está descargado en Ollama. */
    public Listado listar() {
        Optional<Set<String>> instalados = modelosInstalados();
        List<Modelo> modelos = perfiles.entrySet().stream()
                .map(e -> new Modelo(e.getKey(), e.getValue().nombre(), e.getValue().descripcion(),
                        e.getValue().modeloOllama(), POR_DEFECTO.equals(e.getKey()),
                        instalados.map(s -> s.contains(conEtiqueta(e.getValue().modeloOllama()))).orElse(null)))
                .toList();
        return new Listado(instalados.isPresent(), modelos);
    }

    /** Responde con el perfil pedido (o el de por defecto si viene vacío). */
    public Result<String> responder(String id, String pregunta) {
        String clave = (id == null || id.isBlank()) ? POR_DEFECTO : id.strip().toLowerCase();
        Perfil perfil = perfiles.get(clave);
        if (perfil == null) {
            throw new ModeloNoDisponibleException(
                    "Modelo desconocido: '" + id + "'. Opciones: " + String.join(", ", perfiles.keySet()));
        }
        modelosInstalados().ifPresent(instalados -> {
            if (!instalados.contains(conEtiqueta(perfil.modeloOllama()))) {
                throw new ModeloNoDisponibleException("El modelo " + perfil.modeloOllama()
                        + " no está descargado en Ollama. Ejecuta: ollama pull " + perfil.modeloOllama());
            }
        });
        return perfil.asistente().apply(pregunta);
    }

    public String modeloOllama(String id) {
        Perfil p = perfiles.get((id == null || id.isBlank()) ? POR_DEFECTO : id.strip().toLowerCase());
        return p == null ? null : p.modeloOllama();
    }

    /** Nombres de los modelos descargados en Ollama (GET /api/tags), o vacío si Ollama no responde. */
    Optional<Set<String>> modelosInstalados() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(ollamaUrl.replaceAll("/+$", "") + "/api/tags"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return Optional.empty();
            }
            Set<String> nombres = new HashSet<>();
            try (JsonReader r = Json.createReader(new StringReader(resp.body()))) {
                for (JsonValue v : r.readObject().getJsonArray("models")) {
                    JsonObject m = v.asJsonObject();
                    nombres.add(conEtiqueta(m.getString("name", m.getString("model", ""))));
                }
            }
            return Optional.of(nombres);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            Log.debugf("No se pudo consultar Ollama en %s: %s", ollamaUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /** Ollama guarda "embeddinggemma" como "embeddinggemma:latest". */
    static String conEtiqueta(String modelo) {
        return modelo.contains(":") ? modelo : modelo + ":latest";
    }
}
