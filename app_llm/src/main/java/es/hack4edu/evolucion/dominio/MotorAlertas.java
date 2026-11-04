package es.hack4edu.evolucion.dominio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Alertas tempranas AGREGADAS (por grupo o centro), calculadas solo con vistas agregadas.
 *
 * Reglas del proyecto que este código respeta:
 *   · Nunca se habla de alumnos concretos: solo de medias y porcentajes de ámbitos con ≥ 10 registros.
 *   · Solo hay alerta si hay datos suficientes: dos periodos comparables y una diferencia que supera
 *     un umbral práctico Y que se distingue del azar (|z| ≥ 1,96, aprox. 95 %).
 *   · Son avisos informativos para el docente, no decisiones ni predicciones.
 */
public final class MotorAlertas {

    static final double Z_95 = 1.96;

    /** Indicadores de un ámbito en un periodo (fila de analitica.vw_evolucion). */
    public record Punto(int periodoId, String periodo, int orden, boolean sintetico, int nAlumnos, int nGrupos,
                        Double notaMedia, Double notaSd, Integer nAsistencia, Double asistenciaMedia,
                        Double asistenciaSd, Double pctAsistenciaBaja, Double horasEstudioMedia,
                        Double tutoriasMedia, String actualizado) {
    }

    /** Referencia del conjunto de centros (fila global de analitica.vw_resumen). */
    public record Referencia(String nombre, Double notaMedia, Double asistenciaMedia) {
    }

    /** Factor accionable con mayor prioridad en el ámbito (analitica.vw_prioridades). */
    public record PrioridadPrincipal(String factorNombre, String nivelPeor, Double pctPeor, Double brecha) {
    }

    public record Entrada(String ambito, List<Punto> serie, Map<String, List<Punto>> gruposDelCentro,
                          String centro, Referencia referencia, PrioridadPrincipal prioridad) {
    }

    public enum Nivel { ATENCION, INFORMATIVA, POSITIVA }

    public record Alerta(String indicador, String codigo, String ambito, String periodo, String periodoReferencia,
                         Double valorActual, Double valorReferencia, Double variacion, String unidad,
                         String mensaje, String contexto, Nivel nivel, String actualizado) {
    }

    public record Resultado(boolean datosSuficientes, String mensajeDatos, List<Alerta> alertas, String orientacion) {
    }

    private record Indicador(String codigo, String nombre, String unidad, double umbral, double umbralAtencion,
                             boolean mejorSiSube) {
    }

    private static final Indicador NOTA = new Indicador("nota_media", "Nota media", "puntos", 2.0, 5.0, true);
    private static final Indicador ASISTENCIA = new Indicador("asistencia_media", "Asistencia media", "pp", 2.0, 5.0, true);
    private static final Indicador ASISTENCIA_BAJA =
            new Indicador("pct_asistencia_baja", "Alumnado con asistencia < 80 %", "pp", 5.0, 10.0, false);

    private MotorAlertas() {
    }

    public static Resultado calcular(Entrada e) {
        List<Punto> serie = e.serie().stream().sorted(Comparator.comparingInt(Punto::orden)).toList();
        List<Alerta> alertas = new ArrayList<>();
        String mensajeDatos;
        boolean suficientes = serie.size() >= 2;

        if (serie.isEmpty()) {
            return new Resultado(false, "No hay datos publicables de " + e.ambito()
                    + " (menos de 10 registros o sin importaciones).", List.of(), null);
        }
        Punto actual = serie.get(serie.size() - 1);

        if (suficientes) {
            Punto anterior = serie.get(serie.size() - 2);
            mensajeDatos = "Se compara «" + actual.periodo() + "» con «" + anterior.periodo() + "».";
            String notaSintetica = anterior.sintetico() || actual.sintetico()
                    ? " El periodo de referencia es la línea base sintética: la comparación ilustra la "
                    + "herramienta, pero no permite sacar conclusiones." : "";
            String contextoN = "Comparado con «" + anterior.periodo() + "» (" + anterior.nAlumnos() + " → "
                    + actual.nAlumnos() + " registros).";

            media(alertas, e, NOTA, actual, anterior, actual.notaMedia(), actual.notaSd(), actual.nAlumnos(),
                    anterior.notaMedia(), anterior.notaSd(), anterior.nAlumnos(), contextoN + notaSintetica);
            media(alertas, e, ASISTENCIA, actual, anterior, actual.asistenciaMedia(), actual.asistenciaSd(),
                    actual.nAsistencia(), anterior.asistenciaMedia(), anterior.asistenciaSd(),
                    anterior.nAsistencia(), contextoN + notaSintetica);
            proporcion(alertas, e, ASISTENCIA_BAJA, actual, anterior, contextoN + notaSintetica);
            tendencia(alertas, e, serie, NOTA, Punto::notaMedia);
            tendencia(alertas, e, serie, ASISTENCIA, Punto::asistenciaMedia);
            brechaEntreGrupos(alertas, e, actual, anterior);
        } else {
            mensajeDatos = "Solo hay datos de un periodo («" + actual.periodo() + "»). Las alertas de evolución "
                    + "aparecerán cuando se importe al menos otro periodo de " + e.ambito() + ".";
        }

        // Comparación con el conjunto de centros: solo con datos reales (no la línea base sintética)
        if (!actual.sintetico()) {
            comparables(alertas, e, actual);
        }

        if (alertas.isEmpty() && suficientes) {
            mensajeDatos += " No se observan variaciones que superen los umbrales y se distingan del azar.";
        }
        alertas.sort(Comparator.comparing(Alerta::nivel));
        boolean hayAvisos = alertas.stream().anyMatch(a -> a.nivel() != Nivel.POSITIVA);
        return new Resultado(suficientes, mensajeDatos, List.copyOf(alertas), hayAvisos ? orientacion(e) : null);
    }

    // -----------------------------------------------------------------

    /** Diferencia de medias entre dos periodos (prueba z de Welch). */
    private static void media(List<Alerta> alertas, Entrada e, Indicador ind, Punto actual, Punto anterior,
                              Double m1, Double sd1, Integer n1, Double m0, Double sd0, Integer n0, String contexto) {
        if (m1 == null || m0 == null || sd1 == null || sd0 == null || n1 == null || n0 == null || n1 < 2 || n0 < 2) {
            return;
        }
        double d = m1 - m0;
        double se = Math.sqrt(sd1 * sd1 / n1 + sd0 * sd0 / n0);
        double z = se == 0 ? 0 : d / se;
        if (Math.abs(d) < ind.umbral() || Math.abs(z) < Z_95) {
            return;
        }
        boolean empeora = (d < 0) == ind.mejorSiSube();
        String verbo = d < 0 ? "ha bajado" : "ha subido";
        Nivel nivel = !empeora ? Nivel.POSITIVA : Math.abs(d) >= ind.umbralAtencion() ? Nivel.ATENCION : Nivel.INFORMATIVA;
        alertas.add(new Alerta(ind.nombre(), ind.codigo(), e.ambito(), actual.periodo(), anterior.periodo(), m1, m0,
                d, ind.unidad(),
                ind.nombre() + " de " + e.ambito() + " " + verbo + " " + num(Math.abs(d)) + " " + ind.unidad()
                        + " (de " + num(m0) + " a " + num(m1) + ").",
                contexto + " Diferencia distinguible del azar (z = " + num(z) + ").",
                nivel, actual.actualizado()));
    }

    /** Diferencia de proporciones entre dos periodos (prueba z de dos proporciones). */
    private static void proporcion(List<Alerta> alertas, Entrada e, Indicador ind, Punto actual, Punto anterior,
                                   String contexto) {
        Double p1 = actual.pctAsistenciaBaja(), p0 = anterior.pctAsistenciaBaja();
        Integer n1 = actual.nAsistencia(), n0 = anterior.nAsistencia();
        if (p1 == null || p0 == null || n1 == null || n0 == null) {
            return;
        }
        double d = p1 - p0;
        double pc = (p1 / 100 * n1 + p0 / 100 * n0) / (n1 + n0);
        double se = Math.sqrt(pc * (1 - pc) * (1.0 / n1 + 1.0 / n0)) * 100;
        double z = se == 0 ? 0 : d / se;
        if (Math.abs(d) < ind.umbral() || Math.abs(z) < Z_95) {
            return;
        }
        boolean empeora = d > 0;
        Nivel nivel = !empeora ? Nivel.POSITIVA : Math.abs(d) >= ind.umbralAtencion() ? Nivel.ATENCION : Nivel.INFORMATIVA;
        alertas.add(new Alerta(ind.nombre(), ind.codigo(), e.ambito(), actual.periodo(), anterior.periodo(), p1, p0,
                d, ind.unidad(),
                "El porcentaje del alumnado de " + e.ambito() + " con asistencia inferior al 80 % "
                        + (d > 0 ? "ha aumentado" : "ha disminuido") + " " + num(Math.abs(d)) + " pp (de "
                        + num(p0) + " % a " + num(p1) + " %).",
                contexto + " Diferencia distinguible del azar (z = " + num(z) + ").",
                nivel, actual.actualizado()));
    }

    /** Tres o más periodos seguidos a la baja (solo periodos reales, sin la línea base). */
    private static void tendencia(List<Alerta> alertas, Entrada e, List<Punto> serie, Indicador ind,
                                  java.util.function.Function<Punto, Double> valor) {
        List<Punto> reales = serie.stream().filter(p -> !p.sintetico() && valor.apply(p) != null).toList();
        if (reales.size() < 3) {
            return;
        }
        int k = reales.size() - 1;
        int tramo = 1;
        while (k > 0 && valor.apply(reales.get(k)) < valor.apply(reales.get(k - 1))) {
            tramo++;
            k--;
        }
        if (tramo < 3) {
            return;
        }
        Punto inicio = reales.get(reales.size() - tramo), fin = reales.get(reales.size() - 1);
        double caida = valor.apply(fin) - valor.apply(inicio);
        if (Math.abs(caida) < ind.umbral()) {
            return;
        }
        alertas.add(new Alerta(ind.nombre(), ind.codigo() + "_tendencia", e.ambito(), fin.periodo(), inicio.periodo(),
                valor.apply(fin), valor.apply(inicio), caida, ind.unidad(),
                ind.nombre() + " de " + e.ambito() + " muestra una tendencia descendente: baja en " + (tramo - 1)
                        + " periodos seguidos, " + num(Math.abs(caida)) + " " + ind.unidad() + " en total.",
                "Desde «" + inicio.periodo() + "» hasta «" + fin.periodo() + "». Tendencia descriptiva: no es una "
                        + "previsión de lo que pasará.",
                Nivel.ATENCION, fin.actualizado()));
    }

    /** Diferencia de nota media entre el mejor y el peor grupo del centro, con los mismos grupos en ambos periodos. */
    private static void brechaEntreGrupos(List<Alerta> alertas, Entrada e, Punto actual, Punto anterior) {
        if (e.gruposDelCentro() == null || e.gruposDelCentro().isEmpty()) {
            return;
        }
        List<Double[]> pares = new ArrayList<>(); // [actual, anterior] por grupo con datos en ambos periodos
        for (List<Punto> serieGrupo : e.gruposDelCentro().values()) {
            Double a = valorEn(serieGrupo, actual.periodoId()), b = valorEn(serieGrupo, anterior.periodoId());
            if (a != null && b != null) {
                pares.add(new Double[]{a, b});
            }
        }
        if (pares.size() < 2) {
            return;
        }
        double brecha1 = pares.stream().mapToDouble(p -> p[0]).max().orElseThrow()
                - pares.stream().mapToDouble(p -> p[0]).min().orElseThrow();
        double brecha0 = pares.stream().mapToDouble(p -> p[1]).max().orElseThrow()
                - pares.stream().mapToDouble(p -> p[1]).min().orElseThrow();
        double d = brecha1 - brecha0;
        if (d < 3.0) {
            return;
        }
        alertas.add(new Alerta("Brecha entre grupos", "brecha_grupos", e.centro(), actual.periodo(), anterior.periodo(),
                brecha1, brecha0, d, "puntos",
                "La diferencia de nota media entre grupos de " + e.centro() + " ha aumentado de " + num(brecha0)
                        + " a " + num(brecha1) + " puntos.",
                "Comparación descriptiva de los " + pares.size() + " grupos con datos en «" + anterior.periodo()
                        + "» y «" + actual.periodo() + "».",
                d >= 6.0 ? Nivel.ATENCION : Nivel.INFORMATIVA, actual.actualizado()));
    }

    /** Ámbito frente al conjunto de centros en el último periodo. */
    private static void comparables(List<Alerta> alertas, Entrada e, Punto actual) {
        Referencia ref = e.referencia();
        if (ref == null) {
            return;
        }
        comparable(alertas, e, actual, ASISTENCIA, actual.asistenciaMedia(), actual.asistenciaSd(),
                actual.nAsistencia(), ref.asistenciaMedia(), 3.0, ref);
        comparable(alertas, e, actual, NOTA, actual.notaMedia(), actual.notaSd(), actual.nAlumnos(),
                ref.notaMedia(), 3.0, ref);
    }

    private static void comparable(List<Alerta> alertas, Entrada e, Punto actual, Indicador ind, Double m, Double sd,
                                   Integer n, Double refMedia, double umbral, Referencia ref) {
        if (m == null || sd == null || n == null || refMedia == null || n < 2) {
            return;
        }
        double d = m - refMedia;
        double se = sd / Math.sqrt(n);
        double z = se == 0 ? 0 : d / se;
        if (d > -umbral || z > -Z_95) {
            return; // solo se avisa cuando está por debajo
        }
        alertas.add(new Alerta(ind.nombre(), ind.codigo() + "_comparables", e.ambito(), actual.periodo(), ref.nombre(),
                m, refMedia, d, ind.unidad(),
                ind.nombre() + " de " + e.ambito() + " (" + num(m) + ") está por debajo de la del " + ref.nombre()
                        + " (" + num(refMedia) + "): " + num(Math.abs(d)) + " " + ind.unidad() + " menos.",
                "Comparación con el " + ref.nombre() + " (indicadores actuales). Diferencia distinguible del azar "
                        + "(z = " + num(z) + ").",
                Nivel.INFORMATIVA, actual.actualizado()));
    }

    private static Double valorEn(List<Punto> serie, int periodoId) {
        return serie.stream().filter(p -> p.periodoId() == periodoId).map(Punto::notaMedia)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** Para orientar la revisión con el equipo: el factor accionable con más prioridad del ámbito. */
    private static String orientacion(Entrada e) {
        PrioridadPrincipal p = e.prioridad();
        if (p == null) {
            return null;
        }
        return "Para orientar la revisión con el equipo: en " + e.ambito() + " el factor accionable con más "
                + "prioridad es «" + p.factorNombre() + "» (" + num(p.pctPeor()) + " % en «" + p.nivelPeor()
                + "», brecha de " + num(p.brecha()) + " puntos). Es una asociación, no una causa.";
    }

    static String num(Double v) {
        return v == null ? "–" : String.format(Locale.forLanguageTag("es-ES"), "%.1f", v);
    }
}
