package es.hack4edu.direccion.dominio;

import java.util.Collection;
import java.util.List;

/**
 * Reglas del semáforo del panel de dirección (lógica pura, sin base de datos).
 * Compara un grupo con el conjunto de centros: el color describe cuánto se aleja del
 * conjunto en sentido desfavorable. No clasifica a ningún alumno.
 */
public final class Semaforo {

    private Semaforo() {
    }

    public static final String VERDE = "verde";
    public static final String AMBAR = "ambar";
    public static final String ROJO = "rojo";

    /**
     * tolVerde / tolAmbar: cuánto puede empeorar el indicador respecto al conjunto (en su unidad)
     * para seguir en verde / en ámbar. Más allá, rojo.
     */
    public record Criterio(String codigo, String nombre, String unidad, boolean mejorSiSube,
                           double tolVerde, double tolAmbar) {
    }

    public static final List<Criterio> CRITERIOS = List.of(
            new Criterio("nota_media", "Nota media", "pts", true, 1.0, 3.0),
            new Criterio("asistencia_media", "Asistencia media", "pp", true, 2.0, 5.0),
            new Criterio("pct_asistencia_baja", "Asisten menos del 80 %", "pp", false, 3.0, 8.0),
            new Criterio("pct_recursos_bajos", "Acceso bajo a recursos", "pp", false, 3.0, 8.0),
            new Criterio("tutorias_media", "Tutorías al mes", "", true, 0.2, 0.5));

    /** Color de un indicador frente a la referencia; null si falta alguno de los dos valores. */
    public static String colorIndicador(Criterio c, Double valor, Double referencia) {
        if (valor == null || referencia == null) {
            return null;
        }
        double empeora = c.mejorSiSube() ? referencia - valor : valor - referencia;
        if (empeora <= c.tolVerde()) {
            return VERDE;
        }
        return empeora <= c.tolAmbar() ? AMBAR : ROJO;
    }

    /** Color de una brecha de equidad (en desviaciones típicas): coincide con las magnitudes de vw_prioridades. */
    public static String colorBrecha(Double brechaEstandarizada) {
        if (brechaEstandarizada == null) {
            return null;
        }
        double b = Math.abs(brechaEstandarizada);
        return b < 0.2 ? VERDE : b < 0.5 ? AMBAR : ROJO;
    }

    /** El peor de los colores (rojo > ámbar > verde); null si no hay ninguno. */
    public static String peor(Collection<String> colores) {
        String peor = null;
        for (String c : colores) {
            if (c == null) {
                continue;
            }
            if (ROJO.equals(c)) {
                return ROJO;
            }
            if (AMBAR.equals(c) || peor == null) {
                peor = c;
            }
        }
        return peor;
    }
}
