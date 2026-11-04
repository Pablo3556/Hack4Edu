package es.hack4edu.rag.infraestructura.ollama;

import dev.langchain4j.store.embedding.filter.Filter;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Traduce la pregunta a un filtro de metadatos para la búsqueda vectorial.
 * Los embeddings distinguen mal "Centro Demo 03" de "Centro Demo 05", así que,
 * si la pregunta nombra un centro (y opcionalmente un grupo), se restringe la
 * búsqueda a sus fichas.
 */
public final class FiltroAmbito {

    private static final Pattern CENTRO = Pattern.compile("\\bcentro(?:\\s+demo)?\\s+0*([1-9])\\b");
    private static final Pattern GRUPO = Pattern.compile("\\bgrupo\\s+([a-d])\\b");
    private static final Pattern GRUPOS = Pattern.compile("\\bgrupos?\\b");
    private static final Pattern GLOBAL = Pattern.compile("\\b(global|en general|todos los centros)\\b");

    private FiltroAmbito() {
    }

    /** @return el filtro, o {@code null} si la pregunta no nombra ningún centro. */
    public static Filter desde(String pregunta) {
        String q = normalizar(pregunta);
        Matcher c = CENTRO.matcher(q);
        if (!c.find()) {
            if (GLOBAL.matcher(q).find()) {
                return metadataKey("ambito").isEqualTo("global");
            }
            // Sin centro concreto: fichas globales y de centro (las de grupo son demasiadas)
            return metadataKey("ambito").isNotEqualTo("grupo");
        }
        int centro = Integer.parseInt(c.group(1));
        Matcher g = GRUPO.matcher(q);
        if (g.find()) {
            int grupo = (centro - 1) * 4 + (g.group(1).charAt(0) - 'a') + 1;
            return metadataKey("grupo_id").isEqualTo(grupo)
                    .or(metadataKey("centro_id").isEqualTo(centro).and(metadataKey("ambito").isEqualTo("centro")));
        }
        if (GRUPOS.matcher(q).find()) {
            return metadataKey("centro_id").isEqualTo(centro);
        }
        return metadataKey("centro_id").isEqualTo(centro).and(metadataKey("ambito").isEqualTo("centro"));
    }

    static String normalizar(String s) {
        String sinTildes = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }
}
