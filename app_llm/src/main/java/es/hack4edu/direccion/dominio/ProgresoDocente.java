package es.hack4edu.direccion.dominio;

import java.util.List;
import java.util.Map;

/**
 * Progreso del alumnado frente a lo esperado, por grupo y por docente (lógica pura, sin base de datos).
 *
 * "Progreso" = nota real − nota esperada para alumnado con el mismo punto de partida y contexto
 * (lo calcula la vista analitica.vw_progreso_*). Aquí solo se decide si la diferencia se distingue
 * del azar: el intervalo del 95 % no puede incluir el 0 y, además, la diferencia debe ser relevante
 * (al menos {@link #DIFERENCIA_RELEVANTE} puntos). Con decenas de grupos, alguno saldría "fuera"
 * por pura casualidad: exigir las dos cosas evita señalar ruido.
 */
public final class ProgresoDocente {

    private ProgresoDocente() {
    }

    public static final double Z_95 = 1.96;
    /** Puntos (sobre 100) por debajo de los cuales una diferencia no se considera relevante. */
    public static final double DIFERENCIA_RELEVANTE = 0.5;

    public static final String POR_ENCIMA = "por_encima";
    public static final String EN_LO_ESPERADO = "en_lo_esperado";
    public static final String POR_DEBAJO = "por_debajo";

    public static final String METODO = "Para cada alumno se calcula su nota esperada: la media del conjunto de centros "
            + "entre el alumnado con un punto de partida y un contexto parecidos (nota previa, acceso a recursos, "
            + "implicación familiar y renta). El progreso es la nota real menos la esperada, en puntos sobre 100. "
            + "No se descuentan la asistencia, las horas de estudio ni la motivación, porque el profesorado puede "
            + "influir en ellas. Solo se marca un grupo o docente cuando el intervalo del 95 % no incluye el 0 y la "
            + "diferencia es de al menos 0,5 puntos.";

    public static final String AVISO = "El resultado de un docente es el del alumnado de sus grupos y lo comparte con "
            + "el resto del equipo que da clase en ellos (quien da clase a todos los grupos tiene el resultado del "
            + "centro). Orienta conversaciones de mejora y acompañamiento; no es una evaluación del desempeño ni debe "
            + "usarse para decisiones sobre personas. Profesorado y alumnado de la demo son sintéticos: las "
            + "diferencias son ruido estadístico.";

    /** Media del progreso y su error típico, tal como llegan de la vista. */
    public record Medicion(Double progreso, Double errorTipico) {
    }

    /** Intervalo del 95 % y veredicto. Todo null si no hay medición. */
    public record Estimacion(Double progreso, Double icInferior, Double icSuperior, String veredicto) {
    }

    public record Grupo(int grupoId, String nombre, int nAlumnos, String tutor, int nDocentes, Integer horasSemana,
                        Double experienciaMedia, Double formacionMedia, Estimacion progreso) {
    }

    public record Docente(String codigo, int aniosExperiencia, String nivelFormacion, int horasFormacionAnio,
                          String tipoContrato, Integer horasSemana, Integer nGrupos, String grupos, String asignaturas,
                          Integer nAlumnos, Estimacion progreso) {
    }

    public record Panel(String centro, boolean hayProfesorado, Grupo centroFila, List<Grupo> grupos,
                        List<Docente> docentes, Map<String, Integer> recuentoGrupos,
                        String metodo, String aviso) {
    }

    public static Estimacion estimar(Medicion m) {
        if (m == null || m.progreso() == null || m.errorTipico() == null) {
            return new Estimacion(null, null, null, null);
        }
        double margen = Z_95 * m.errorTipico();
        double inf = m.progreso() - margen;
        double sup = m.progreso() + margen;
        return new Estimacion(m.progreso(), redondear(inf), redondear(sup), veredicto(m.progreso(), inf, sup));
    }

    static String veredicto(double progreso, double inf, double sup) {
        if (Math.abs(progreso) < DIFERENCIA_RELEVANTE) {
            return EN_LO_ESPERADO;
        }
        if (inf > 0) {
            return POR_ENCIMA;
        }
        return sup < 0 ? POR_DEBAJO : EN_LO_ESPERADO;
    }

    private static double redondear(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
