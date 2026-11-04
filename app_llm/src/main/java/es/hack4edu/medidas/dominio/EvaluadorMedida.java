package es.hack4edu.medidas.dominio;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Valora el efecto observado de una medida comparando el indicador del ámbito en el periodo de inicio
 * con el último periodo posterior, y restando lo que cambió en los ámbitos de referencia (los demás
 * grupos del centro, o los demás centros) para no atribuir a la medida una tendencia general.
 * Lógica pura, sin base de datos. Es una asociación temporal, no una prueba de causalidad.
 */
public final class EvaluadorMedida {

    private EvaluadorMedida() {
    }

    public static final String SIN_DATOS_BASE = "sin_datos_base";
    public static final String PENDIENTE = "pendiente";
    public static final String EVALUADA = "evaluada";

    public static final String MEJORA = "mejora";
    public static final String SIN_CAMBIO = "sin_cambio";
    public static final String EMPEORA = "empeora";

    public record Evaluacion(String estado, String periodoBase, String periodoSeguimiento, Double antes,
                             Double despues, Double variacion, Double variacionReferencia, Double efectoNeto,
                             String veredicto, String mensaje) {
    }

    /**
     * @param propio    valor del indicador por orden de periodo en el ámbito de la medida
     * @param pares     lo mismo para cada ámbito de referencia
     * @param nombres   nombre legible de cada periodo (por orden)
     * @param ordenBase orden del periodo en que empezó la medida
     */
    public static Evaluacion evaluar(Indicador ind, Map<Integer, Double> propio, List<Map<Integer, Double>> pares,
                                     Map<Integer, String> nombres, int ordenBase) {
        Double antes = propio.get(ordenBase);
        if (antes == null) {
            return new Evaluacion(SIN_DATOS_BASE, nombres.get(ordenBase), null, null, null, null, null, null, null,
                    "Todavía no hay datos de este ámbito en el periodo de inicio (" + nombres.get(ordenBase)
                            + "): no se puede medir el antes.");
        }
        OptionalInt ultimo = propio.entrySet().stream().filter(e -> e.getKey() > ordenBase && e.getValue() != null)
                .mapToInt(Map.Entry::getKey).max();
        if (ultimo.isEmpty()) {
            return new Evaluacion(PENDIENTE, nombres.get(ordenBase), null, antes, null, null, null, null, null,
                    "Se podrá valorar cuando se importen datos de un periodo posterior a " + nombres.get(ordenBase) + ".");
        }
        int orden = ultimo.getAsInt();
        double despues = propio.get(orden);
        double variacion = redondear(despues - antes);

        Double variacionRef = variacionMediaPares(pares, ordenBase, orden);
        double neto = variacionRef == null ? variacion : redondear(variacion - variacionRef);
        double favorable = ind.mejorSiSube() ? neto : -neto;
        String veredicto = favorable >= ind.umbral() ? MEJORA : favorable <= -ind.umbral() ? EMPEORA : SIN_CAMBIO;

        String periodoSeg = nombres.get(orden);
        StringBuilder m = new StringBuilder();
        m.append(ind.nombre()).append(": ").append(num(antes)).append(" → ").append(num(despues)).append(' ')
                .append(ind.unidad()).append(" entre ").append(nombres.get(ordenBase)).append(" y ").append(periodoSeg)
                .append(" (").append(signo(variacion)).append(").");
        if (variacionRef != null) {
            m.append(" En los ámbitos de referencia cambió ").append(signo(variacionRef)).append("; descontado, el cambio atribuible es ")
                    .append(signo(neto)).append('.');
        } else {
            m.append(" No hay ámbitos de referencia con datos en ambos periodos, así que el cambio no se ha descontado.");
        }
        m.append(switch (veredicto) {
            case MEJORA -> " Evolución favorable.";
            case EMPEORA -> " Evolución desfavorable: conviene revisar la medida con el equipo.";
            default -> " Sin cambio apreciable (menos de " + num(ind.umbral()) + " " + ind.unidad() + ").";
        });
        m.append(" Asociación temporal: no demuestra que la medida sea la causa.");
        return new Evaluacion(EVALUADA, nombres.get(ordenBase), periodoSeg, antes, despues, variacion, variacionRef,
                neto, veredicto, m.toString());
    }

    /** Media de la variación de los ámbitos de referencia que tienen dato en los dos periodos; null si ninguno. */
    static Double variacionMediaPares(List<Map<Integer, Double>> pares, int desde, int hasta) {
        double suma = 0;
        int n = 0;
        for (Map<Integer, Double> p : pares) {
            Double a = p.get(desde);
            Double b = p.get(hasta);
            if (a != null && b != null) {
                suma += b - a;
                n++;
            }
        }
        return n == 0 ? null : redondear(suma / n);
    }

    private static double redondear(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String num(double v) {
        return String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", v);
    }

    private static String signo(double v) {
        return (v > 0 ? "+" : "") + num(v);
    }
}
