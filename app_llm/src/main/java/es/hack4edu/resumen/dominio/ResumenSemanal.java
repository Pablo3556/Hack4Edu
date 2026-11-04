package es.hack4edu.resumen.dominio;

import java.util.List;

/** Resumen semanal de un grupo o centro, con la fuente de cada cifra. */
public final class ResumenSemanal {

    private ResumenSemanal() {
    }

    public static final String AVISO = "Son asociaciones observadas en los datos agregados, no causas, y las propuestas son líneas "
            + "para revisar con el equipo docente, no decisiones. El resumen no señala ni clasifica a ningún alumno: "
            + "cada cifra sale de grupos de al menos 10.";

    /** Dónde se puede comprobar una cifra. */
    public record Fuente(String vista, String ambito, String detalle) {
    }

    public record Prioridad(int puesto, String factor, String factorNombre, String titulo, String detalle,
                            String queProbar, String magnitud, String fiabilidad, Fuente fuente,
                            List<String> medidasRelacionadas) {
    }

    public record Senal(String nivel, String titulo, String detalle, Fuente fuente) {
    }

    public record MedidaBreve(int id, String titulo, String grupo, String indicador, String periodoInicio,
                              String veredicto, String mensaje) {
    }

    public record Resumen(String ambito, String tipoAmbito, String semana, String generado, String periodo,
                          boolean datosSuficientes, String mensaje, List<Prioridad> prioridades,
                          List<Senal> senales, List<MedidaBreve> medidasEnCurso, String aviso) {
    }
}
