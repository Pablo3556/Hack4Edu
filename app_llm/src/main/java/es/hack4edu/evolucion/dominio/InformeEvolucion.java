package es.hack4edu.evolucion.dominio;

import java.util.List;
import java.util.Map;

/** Resultado de consultar la evolución de un grupo o centro por periodos. */
public final class InformeEvolucion {

    private InformeEvolucion() {
    }

    public record Comparacion(String codigo, String indicador, String unidad, Double anterior, Double actual,
                              Double variacion, boolean mejorSiSube) {
    }

    public record Evolucion(String ambito, String tipoAmbito, List<MotorAlertas.Punto> serie,
                            List<MotorAlertas.Punto> serieCentro, Map<String, List<MotorAlertas.Punto>> grupos,
                            String periodoActual, String periodoAnterior, List<Comparacion> comparaciones,
                            boolean datosSuficientes, String mensajeDatos, List<MotorAlertas.Alerta> alertas,
                            String orientacion, String ultimaActualizacion, String aviso) {
    }
}
