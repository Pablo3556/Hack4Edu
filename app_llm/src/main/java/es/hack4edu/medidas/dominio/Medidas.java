package es.hack4edu.medidas.dominio;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modelo del seguimiento de medidas. Una medida no contiene datos de alumnos: solo ámbito, factor,
 * indicador a vigilar, periodo de inicio y un texto corto.
 */
public final class Medidas {

    private Medidas() {
    }

    public static final Set<String> ESTADOS = Set.of("en_curso", "finalizada", "descartada");

    public static final String AVISO = "Las medidas no deben contener nombres ni datos de alumnos. La valoración compara el "
            + "indicador antes y después descontando lo que cambió en los demás grupos o centros; es una asociación "
            + "temporal, no una prueba de que la medida causara el cambio.";

    public record IndicadorInfo(String codigo, String nombre, String unidad, boolean mejorSiSube) {
    }

    public record Periodo(int id, String codigo, String nombre) {
    }

    /** Datos de una medida nueva, tal y como llegan (sin validar). */
    public record NuevaMedida(Integer centro, Integer grupo, String factor, String titulo, String descripcion,
                              String indicador, Integer periodoInicio) {
    }

    /** Medida tal y como está guardada, sin su valoración. */
    public record MedidaRegistrada(int id, int centroId, String centro, Integer grupoId, String grupo, String factor,
                                   String factorNombre, String titulo, String descripcion, Indicador indicador,
                                   int periodoInicio, String periodoNombre, int periodoOrden, String estado,
                                   String creadaPor, String creada) {
    }

    /** Medida con su valoración (lo que ve el usuario). */
    public record Medida(int id, int centroId, String centro, Integer grupoId, String grupo, String factor,
                         String factorNombre, String titulo, String descripcion, String indicador,
                         String indicadorNombre, String unidad, int periodoInicio, String periodoInicioNombre,
                         String estado, String creadaPor, String creada, EvaluadorMedida.Evaluacion evaluacion) {
    }

    public record Listado(List<Medida> medidas, String aviso) {
    }

    /** Valores de los indicadores de un centro o grupo en un periodo (fila de analitica.vw_evolucion). */
    public record ValorPeriodo(Integer centroId, Integer grupoId, int orden, Map<String, Double> valores) {
    }
}
