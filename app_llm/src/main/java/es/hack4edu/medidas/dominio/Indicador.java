package es.hack4edu.medidas.dominio;

import java.util.Arrays;
import java.util.Optional;

/**
 * Indicadores de analitica.vw_evolucion que una medida puede vigilar.
 * umbral = cambio mínimo (en la unidad del indicador) para hablar de mejora o empeoramiento.
 */
public enum Indicador {

    NOTA_MEDIA("nota_media", "Nota media", "pts", true, 1.0),
    ASISTENCIA_MEDIA("asistencia_media", "Asistencia media", "pp", true, 1.0),
    PCT_ASISTENCIA_BAJA("pct_asistencia_baja", "Alumnado con asistencia < 80 %", "pp", false, 2.0),
    HORAS_ESTUDIO_MEDIA("horas_estudio_media", "Horas de estudio a la semana", "h", true, 0.5),
    TUTORIAS_MEDIA("tutorias_media", "Tutorías al mes", "", true, 0.2);

    private final String codigo;
    private final String nombre;
    private final String unidad;
    private final boolean mejorSiSube;
    private final double umbral;

    Indicador(String codigo, String nombre, String unidad, boolean mejorSiSube, double umbral) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.unidad = unidad;
        this.mejorSiSube = mejorSiSube;
        this.umbral = umbral;
    }

    public String codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    public String unidad() {
        return unidad;
    }

    public boolean mejorSiSube() {
        return mejorSiSube;
    }

    public double umbral() {
        return umbral;
    }

    public static Optional<Indicador> de(String codigo) {
        return Arrays.stream(values()).filter(i -> i.codigo.equals(codigo)).findFirst();
    }
}
