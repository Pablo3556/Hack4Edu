package es.hack4edu.ingesta.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Esquema interno de la importación de docentes: un campo por columna de staging.spf_raw.
 *
 * Mismas reglas que la carga de Kaggle (cargar_kaggle.sh + staging.transformar_spf):
 *   · rangos = restricciones CHECK de staging.alumno_spf (V2)
 *   · valores categóricos = listas de staging.mapear / a_nivel / a_bool (V2)
 * Aquí, además, se aceptan nombres de columna y valores en castellano: cada valor se
 * normaliza al formato del CSV original antes de llegar a spf_raw, así que la base de
 * datos aplica exactamente la misma transformación (y vuelve a validar) en ambos casos.
 *
 * No se incluyen a propósito:
 *   · Learning_Disabilities (dato sensible: la ingesta de docentes no lo recoge)
 *   · School_Type (la titularidad la da el centro elegido, no el archivo)
 */
public enum CampoIngesta {

    NOTA_EXAMEN("nota_examen", "Nota final", "exam_score", Tipo.ENTERO, 0, 100, true, true,
            "Nota del examen o de la evaluación, de 0 a 100.",
            "nota", "nota final", "notas", "calificacion", "calificacion final", "nota examen", "nota del examen",
            "nota evaluacion", "puntuacion", "exam score", "score"),
    ASISTENCIA("asistencia", "Asistencia (%)", "attendance", Tipo.ENTERO, 0, 100, true, false,
            "Porcentaje de clases a las que ha asistido, de 0 a 100.",
            "asistencia", "porcentaje asistencia", "porcentaje de asistencia", "asistencia porcentaje",
            "asistencia a clase", "attendance"),
    HORAS_ESTUDIO("horas_estudio", "Horas de estudio", "hours_studied", Tipo.ENTERO, 0, 80, false, false,
            "Horas de estudio a la semana (0–80).",
            "horas de estudio", "horas estudio", "estudio semanal", "horas estudio semana", "hours studied"),
    NOTA_PREVIA("nota_previa", "Nota previa", "previous_scores", Tipo.ENTERO, 0, 100, false, false,
            "Nota de la evaluación anterior (0–100).",
            "nota previa", "nota anterior", "notas anteriores", "nota evaluacion anterior", "previous scores"),
    TUTORIAS("tutorias", "Sesiones de tutoría", "tutoring_sessions", Tipo.ENTERO, 0, 30, false, false,
            "Sesiones de tutoría o refuerzo al mes (0–30).",
            "tutorias", "sesiones de tutoria", "sesiones tutoria", "tutoria", "refuerzo", "tutoring sessions"),
    HORAS_SUENO("horas_sueno", "Horas de sueño", "sleep_hours", Tipo.ENTERO, 0, 24, false, false,
            "Horas de sueño por noche (0–24).",
            "horas de sueno", "horas sueno", "sueno", "sleep hours"),
    ACTIVIDAD_FISICA("actividad_fisica", "Actividad física", "physical_activity", Tipo.ENTERO, 0, 40, false, false,
            "Horas de actividad física a la semana (0–40).",
            "actividad fisica", "horas actividad fisica", "deporte", "physical activity"),
    IMPLICACION_FAMILIAR("implicacion_familiar", "Implicación familiar", "parental_involvement", Tipo.NIVEL, 0, 0,
            false, false, "Nivel de implicación de la familia: bajo, medio o alto.",
            "implicacion familiar", "apoyo familiar", "implicacion de la familia", "participacion familiar",
            "parental involvement"),
    ACCESO_RECURSOS("acceso_recursos", "Acceso a recursos", "access_to_resources", Tipo.NIVEL, 0, 0, false, false,
            "Nivel de acceso a recursos educativos: bajo, medio o alto.",
            "acceso a recursos", "acceso recursos", "recursos", "access to resources"),
    MOTIVACION("motivacion", "Motivación", "motivation_level", Tipo.NIVEL, 0, 0, false, false,
            "Nivel de motivación: bajo, medio o alto.",
            "motivacion", "nivel de motivacion", "motivation level"),
    RENTA_FAMILIAR("renta_familiar", "Renta familiar", "family_income", Tipo.NIVEL, 0, 0, false, false,
            "Nivel de ingresos de la familia: bajo, medio o alto (solo para vigilar la equidad).",
            "renta familiar", "ingresos familiares", "nivel de renta", "renta", "family income"),
    CALIDAD_DOCENTE("calidad_docente", "Calidad docente percibida", "teacher_quality", Tipo.NIVEL, 0, 0, false, false,
            "Valoración de la calidad docente: baja, media o alta.",
            "calidad docente", "calidad del profesorado", "teacher quality"),
    EXTRAESCOLARES("extraescolares", "Actividades extraescolares", "extracurricular_activities", Tipo.BOOLEANO, 0, 0,
            false, false, "Participa en actividades extraescolares: sí o no.",
            "extraescolares", "actividades extraescolares", "extracurricular activities"),
    ACCESO_INTERNET("acceso_internet", "Acceso a internet", "internet_access", Tipo.BOOLEANO, 0, 0, false, false,
            "Dispone de acceso a internet: sí o no.",
            "acceso a internet", "acceso internet", "internet", "internet access"),
    INFLUENCIA_PARES("influencia_pares", "Influencia de los compañeros", "peer_influence", Tipo.CATEGORIA, 0, 0,
            false, false, "Influencia de los compañeros: negativa, neutra o positiva.",
            "influencia de los companeros", "influencia companeros", "influencia pares", "peer influence"),
    ESTUDIOS_FAMILIA("estudios_familia", "Estudios de la familia", "parental_education_level", Tipo.CATEGORIA, 0, 0,
            false, false, "Máximo nivel de estudios de la familia: secundaria, grado o posgrado (solo equidad).",
            "estudios de la familia", "estudios familia", "nivel educativo familia", "estudios padres",
            "parental education level"),
    DISTANCIA_CASA("distancia_casa", "Distancia al centro", "distance_from_home", Tipo.CATEGORIA, 0, 0, false, false,
            "Distancia entre casa y centro: cerca, media o lejos (solo equidad).",
            "distancia al centro", "distancia casa", "distancia", "distance from home"),
    GENERO("genero", "Género", "gender", Tipo.CATEGORIA, 0, 0, false, false,
            "Mujer u hombre. Solo se usa para vigilar brechas de equidad.",
            "genero", "sexo", "gender");

    public enum Tipo { ENTERO, NIVEL, BOOLEANO, CATEGORIA }

    /** Problema de un valor concreto. */
    public enum Problema { FORMATO, RANGO }

    public static final class ValorNoValido extends Exception {
        private final Problema problema;

        ValorNoValido(Problema problema, String mensaje) {
            super(mensaje, null, false, false);
            this.problema = problema;
        }

        public Problema problema() {
            return problema;
        }
    }

    private final String codigo;
    private final String etiqueta;
    private final String columnaRaw;
    private final Tipo tipo;
    private final int min;
    private final int max;
    private final boolean columnaObligatoria;
    private final boolean valorObligatorio;
    private final String descripcion;
    private final Set<String> sinonimos;

    CampoIngesta(String codigo, String etiqueta, String columnaRaw, Tipo tipo, int min, int max,
                 boolean columnaObligatoria, boolean valorObligatorio, String descripcion, String... sinonimos) {
        this.codigo = codigo;
        this.etiqueta = etiqueta;
        this.columnaRaw = columnaRaw;
        this.tipo = tipo;
        this.min = min;
        this.max = max;
        this.columnaObligatoria = columnaObligatoria;
        this.valorObligatorio = valorObligatorio;
        this.descripcion = descripcion;
        this.sinonimos = Arrays.stream(sinonimos).map(CampoIngesta::clave).collect(Collectors.toUnmodifiableSet());
    }

    public String codigo() { return codigo; }
    public String etiqueta() { return etiqueta; }
    /** Columna de staging.spf_raw en la que se guarda (formato del CSV de Kaggle). */
    public String columnaRaw() { return columnaRaw; }
    public Tipo tipo() { return tipo; }
    public int min() { return min; }
    public int max() { return max; }
    /** El archivo debe tener una columna asignada a este campo. */
    public boolean columnaObligatoria() { return columnaObligatoria; }
    /** Cada registro debe tener valor (NOT NULL en staging.alumno_spf). */
    public boolean valorObligatorio() { return valorObligatorio; }
    public String descripcion() { return descripcion; }

    /** Valores admitidos, para mostrarlos en la interfaz. */
    public List<String> valoresAdmitidos() {
        return switch (tipo) {
            case ENTERO -> List.of();
            case NIVEL -> List.of("bajo", "medio", "alto");
            case BOOLEANO -> List.of("sí", "no");
            case CATEGORIA -> categorias().values().stream().map(Categoria::mostrar).distinct().toList();
        };
    }

    public static Optional<CampoIngesta> porCodigo(String codigo) {
        return Arrays.stream(values()).filter(c -> c.codigo.equals(codigo)).findFirst();
    }

    /** Campo que corresponde a un nombre de columna del archivo, si se reconoce. */
    public static Optional<CampoIngesta> detectar(String cabecera) {
        String k = clave(cabecera);
        if (k.isEmpty()) {
            return Optional.empty();
        }
        for (CampoIngesta c : values()) {
            if (c.sinonimos.contains(k) || clave(c.codigo).equals(k) || clave(c.columnaRaw).equals(k)
                    || clave(c.etiqueta).equals(k)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /** ¿El valor cuenta como vacío? */
    public static boolean vacio(String valor) {
        if (valor == null) {
            return true;
        }
        String k = valor.strip().toLowerCase(Locale.ROOT);
        return k.isEmpty() || k.equals("na") || k.equals("n/a") || k.equals("null") || k.equals("nan") || k.equals("-");
    }

    /**
     * Normaliza un valor no vacío al formato del CSV original (el que entiende staging.spf_tipar).
     * @throws ValorNoValido con un mensaje en castellano si el valor no es válido
     */
    public String normalizar(String valor) throws ValorNoValido {
        String v = valor.strip();
        return switch (tipo) {
            case ENTERO -> entero(v);
            case NIVEL -> buscar(v, NIVELES);
            case BOOLEANO -> buscar(v, BOOLEANOS);
            case CATEGORIA -> buscar(v, categorias());
        };
    }

    /** Valor en castellano para la vista previa, a partir del valor ya normalizado. */
    public String mostrar(String normalizado) {
        if (normalizado == null) {
            return null;
        }
        Map<String, Categoria> mapa = switch (tipo) {
            case ENTERO -> null;
            case NIVEL -> NIVELES;
            case BOOLEANO -> BOOLEANOS;
            case CATEGORIA -> categorias();
        };
        if (mapa == null) {
            return normalizado;
        }
        return mapa.values().stream().filter(c -> c.raw().equals(normalizado)).map(Categoria::mostrar)
                .findFirst().orElse(normalizado);
    }

    // -----------------------------------------------------------------

    private String entero(String v) throws ValorNoValido {
        String limpio = v.replaceAll("[\\s\\u00A0]", "");
        if (limpio.endsWith("%")) {
            limpio = limpio.substring(0, limpio.length() - 1);
        }
        limpio = limpio.replace(',', '.');
        BigDecimal n;
        try {
            n = new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            throw new ValorNoValido(Problema.FORMATO, "«" + recortar(v) + "» no es un número");
        }
        // Mismo redondeo que staging.a_entero (round de numeric: mitad hacia arriba en valor absoluto)
        BigDecimal r = n.setScale(0, RoundingMode.HALF_UP);
        if (r.compareTo(BigDecimal.valueOf(min)) < 0 || r.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new ValorNoValido(Problema.RANGO,
                    "«" + recortar(v) + "» está fuera del rango permitido (" + min + "–" + max + ")");
        }
        return r.toPlainString();
    }

    private String buscar(String v, Map<String, Categoria> mapa) throws ValorNoValido {
        Categoria c = mapa.get(clave(v));
        if (c == null) {
            throw new ValorNoValido(Problema.FORMATO, "«" + recortar(v) + "» no es un valor válido (se espera: "
                    + String.join(", ", valoresAdmitidos()) + ")");
        }
        return c.raw();
    }

    private record Categoria(String raw, String mostrar) {
    }

    private static Map<String, Categoria> mapa(Object... pares) {
        Map<String, Categoria> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 3) {
            Categoria c = new Categoria((String) pares[i], (String) pares[i + 1]);
            for (String sinonimo : ((String) pares[i + 2]).split("\\|")) {
                m.put(clave(sinonimo), c);
            }
        }
        return m;
    }

    private static final Map<String, Categoria> NIVELES = mapa(
            "Low", "bajo", "low|bajo|baja",
            "Medium", "medio", "medium|medio|media",
            "High", "alto", "high|alto|alta");

    private static final Map<String, Categoria> BOOLEANOS = mapa(
            "Yes", "sí", "yes|si|s|true|verdadero|1",
            "No", "no", "no|n|false|falso|0");

    private static final Map<String, Categoria> PARES = mapa(
            "Negative", "negativa", "negative|negativa|negativo",
            "Neutral", "neutra", "neutral|neutra|neutro",
            "Positive", "positiva", "positive|positiva|positivo");

    private static final Map<String, Categoria> ESTUDIOS = mapa(
            "High School", "secundaria", "high school|secundaria|eso|bachillerato|fp|formacion profesional",
            "College", "grado", "college|grado|universidad|universitarios|licenciatura|diplomatura",
            "Postgraduate", "posgrado", "postgraduate|posgrado|postgrado|master|doctorado");

    private static final Map<String, Categoria> DISTANCIAS = mapa(
            "Near", "cerca", "near|cerca",
            "Moderate", "media", "moderate|media|moderada|intermedia",
            "Far", "lejos", "far|lejos|lejana");

    private static final Map<String, Categoria> GENEROS = mapa(
            "Female", "mujer", "female|mujer|femenino",
            "Male", "hombre", "male|hombre|masculino");

    private Map<String, Categoria> categorias() {
        return switch (this) {
            case INFLUENCIA_PARES -> PARES;
            case ESTUDIOS_FAMILIA -> ESTUDIOS;
            case DISTANCIA_CASA -> DISTANCIAS;
            case GENERO -> GENEROS;
            default -> Map.of();
        };
    }

    /** Clave de comparación: minúsculas, sin tildes y sin signos ("Asistencia (%)" → "asistencia"). */
    static String clave(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    static String recortar(String v) {
        return v.length() > 40 ? v.substring(0, 40) + "…" : v;
    }
}
