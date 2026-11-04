package es.hack4edu.evolucion.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "¿Qué pasaría si…?": reparte de otra forma al alumnado de un ámbito entre los niveles de un
 * factor accionable y aplica la ASOCIACIÓN HISTÓRICA observada entre ese factor y la nota.
 *
 *   s  = proporción del ámbito que cambia de nivel
 *   d  = nota media (nivel destino) − nota media (nivel origen), con todos los datos disponibles
 *   Δ  = s · d   e IC95 = s · (d ± 1,96 · √(sdA²/nA + sdB²/nB))
 *
 * Es una descripción de los datos, no un modelo causal: si la asociación no se distingue de
 * cero, o el ámbito no tiene datos publicables, no se estima nada y se dice por qué.
 */
public final class Simulador {

    public static final String AVISO = "Esta simulación representa asociaciones observadas en los datos históricos "
            + "disponibles. No constituye una predicción ni demuestra causalidad.";

    /** Nivel del factor en el ámbito simulado (analitica.vw_factor_nivel). */
    public record NivelAmbito(int orden, String etiqueta, int n, double pct, Double nota) {
    }

    /** Nivel del factor con todos los datos (analitica.vw_factor_nivel_sd, ámbito global). */
    public record NivelAsociacion(int orden, String etiqueta, int n, double nota, Double sd) {
    }

    public record Entrada(String ambito, String factor, Double accionabilidad, String palanca,
                          List<NivelAmbito> nivelesAmbito, Double pctSinDato, List<NivelAsociacion> asociacion,
                          Double notaMediaAmbito, int desde, int hacia, int porcentaje) {
    }

    public record NivelEscenario(int orden, String etiqueta, Double pctActual, Double pctEscenario,
                                 Double notaAsociada, Integer nAsociada) {
    }

    public record Resultado(String ambito, String factor, String nivelDesde, String nivelHacia, int porcentaje,
                            boolean estimable, String motivo, Double notaActual, Double notaEscenario,
                            Double variacion, Double variacionMin, Double variacionMax, Double proporcionAfectada,
                            Double diferenciaAsociada, Double diferenciaMin, Double diferenciaMax, String fiabilidad,
                            Double accionabilidad, String palanca, List<NivelEscenario> niveles, String explicacion,
                            String aviso) {
    }

    private Simulador() {
    }

    public static Resultado simular(Entrada e) {
        Optional<NivelAsociacion> a = e.asociacion().stream().filter(n -> n.orden() == e.desde()).findFirst();
        Optional<NivelAsociacion> b = e.asociacion().stream().filter(n -> n.orden() == e.hacia()).findFirst();
        Optional<NivelAmbito> enDesde = e.nivelesAmbito().stream().filter(n -> n.orden() == e.desde()).findFirst();
        String etDesde = a.map(NivelAsociacion::etiqueta).orElse("?");
        String etHacia = b.map(NivelAsociacion::etiqueta).orElse("?");
        int porcentaje = Math.max(0, Math.min(100, e.porcentaje()));

        // Proporción del ámbito que cambia (los registros sin dato del factor no cambian)
        double pctDesde = enDesde.map(NivelAmbito::pct).orElse(0.0);
        double conDato = 1 - (e.pctSinDato() == null ? 0 : e.pctSinDato()) / 100;
        double s = pctDesde / 100 * conDato * porcentaje / 100;

        List<NivelEscenario> niveles = niveles(e, porcentaje, pctDesde);

        String motivo = null;
        if (e.desde() == e.hacia()) {
            motivo = "El nivel de partida y el de llegada son el mismo: no hay ningún cambio que simular.";
        } else if (a.isEmpty() || b.isEmpty()) {
            motivo = "No hay datos publicables suficientes de alguno de los dos niveles (umbral de 10 registros).";
        } else if (e.nivelesAmbito().isEmpty() || e.notaMediaAmbito() == null) {
            motivo = "En " + e.ambito() + " algún nivel de «" + e.factor() + "» tiene menos de 10 registros, así que "
                    + "su distribución no se publica y no se puede simular aquí. Prueba con el centro o el conjunto.";
        } else if (pctDesde == 0) {
            motivo = "En " + e.ambito() + " no hay alumnado en «" + etDesde + "»: no hay a quién aplicar el cambio.";
        } else if (porcentaje == 0) {
            motivo = "Con un 0 % del alumnado que cambia de nivel, el escenario es igual al actual.";
        }

        Double d = null, dMin = null, dMax = null;
        String fiabilidad = null;
        if (a.isPresent() && b.isPresent()) {
            NivelAsociacion na = a.get(), nb = b.get();
            d = nb.nota() - na.nota();
            if (na.sd() != null && nb.sd() != null) {
                double se = Math.sqrt(na.sd() * na.sd() / na.n() + nb.sd() * nb.sd() / nb.n());
                dMin = d - 1.96 * se;
                dMax = d + 1.96 * se;
            }
            int nMin = Math.min(na.n(), nb.n());
            fiabilidad = nMin >= 100 ? "alta" : nMin >= 30 ? "media" : "baja";
            if (motivo == null && (dMin == null || (dMin <= 0 && dMax >= 0))) {
                motivo = "En los datos disponibles, la diferencia de nota entre «" + etDesde + "» y «" + etHacia
                        + "» (" + MotorAlertas.num(d) + " puntos" + (dMin == null ? "" : ", IC95 de "
                        + MotorAlertas.num(dMin) + " a " + MotorAlertas.num(dMax)) + ") no se distingue de cero. "
                        + "No es posible estimar una variación fiable.";
            }
        }

        if (motivo != null) {
            return new Resultado(e.ambito(), e.factor(), etDesde, etHacia, porcentaje, false, motivo,
                    e.notaMediaAmbito(), null, null, null, null, pct(s), r2(d), r2(dMin), r2(dMax), fiabilidad,
                    e.accionabilidad(), e.palanca(), niveles, null, AVISO);
        }

        double delta = s * d, deltaMin = s * dMin, deltaMax = s * dMax;
        double escenario = e.notaMediaAmbito() + delta;
        String explicacion = "En los datos históricos, el alumnado en «" + etHacia + "» obtiene de media "
                + MotorAlertas.num(Math.abs(d)) + " puntos " + (d > 0 ? "más" : "menos") + " que el que está en «"
                + etDesde + "» (" + a.get().n() + " y " + b.get().n() + " registros). Si el " + porcentaje
                + " % del alumnado de " + e.ambito() + " que está en «" + etDesde + "» (el "
                + MotorAlertas.num(s * 100) + " % del ámbito) pasara a «" + etHacia + "» y esa asociación se mantuviera, "
                + "la nota media se situaría en torno a " + MotorAlertas.num(escenario) + " (entre "
                + MotorAlertas.num(e.notaMediaAmbito() + deltaMin) + " y " + MotorAlertas.num(e.notaMediaAmbito() + deltaMax)
                + "), frente a " + MotorAlertas.num(e.notaMediaAmbito()) + " ahora. La diferencia observada puede "
                + "deberse a otros factores que acompañan a «" + etHacia + "»: no implica que el cambio la produzca.";
        if ("baja".equals(fiabilidad)) {
            explicacion += " Fiabilidad baja: alguno de los niveles tiene pocos registros.";
        }
        return new Resultado(e.ambito(), e.factor(), etDesde, etHacia, porcentaje, true, null, e.notaMediaAmbito(),
                r2(escenario), r2(delta), r2(deltaMin), r2(deltaMax), pct(s), r2(d), r2(dMin), r2(dMax), fiabilidad,
                e.accionabilidad(), e.palanca(), niveles, explicacion, AVISO);
    }

    private static List<NivelEscenario> niveles(Entrada e, int porcentaje, double pctDesde) {
        List<NivelEscenario> res = new ArrayList<>();
        double movido = pctDesde * porcentaje / 100;
        for (NivelAsociacion n : e.asociacion()) {
            Double actual = e.nivelesAmbito().stream().filter(x -> x.orden() == n.orden()).map(NivelAmbito::pct)
                    .findFirst().orElse(e.nivelesAmbito().isEmpty() ? null : 0.0);
            Double escenario = actual;
            if (actual != null && e.desde() != e.hacia()) {
                if (n.orden() == e.desde()) escenario = actual - movido;
                if (n.orden() == e.hacia()) escenario = actual + movido;
            }
            res.add(new NivelEscenario(n.orden(), n.etiqueta(), r1(actual), r1(escenario), r2(n.nota()), n.n()));
        }
        return res;
    }

    private static Double r1(Double v) {
        return v == null ? null : Math.round(v * 10) / 10.0;
    }

    private static Double r2(Double v) {
        return v == null ? null : Math.round(v * 100) / 100.0;
    }

    private static Double pct(double s) {
        return Math.round(s * 1000) / 10.0;
    }
}
