package es.hack4edu.direccion.dominio;

import java.util.List;
import java.util.Map;

/** Lo que ve la dirección: semáforo por grupo y brechas de equidad del centro. */
public final class PanelDireccion {

    private PanelDireccion() {
    }

    public static final String CRITERIO = "Cada color compara el grupo con el conjunto de centros: verde = igual, mejor o "
            + "hasta una pequeña diferencia desfavorable; ámbar = diferencia moderada; rojo = diferencia grande. "
            + "El color del grupo es el peor de sus indicadores. Equidad: verde < 0,2 · ámbar 0,2–0,5 · rojo ≥ 0,5 "
            + "desviaciones típicas.";

    public static final String AVISO = "Los centros y grupos de la demo son ficticios y el alumnado se repartió al azar: "
            + "las diferencias son ruido estadístico y no deben interpretarse. El semáforo orienta dónde mirar primero; "
            + "no clasifica alumnos ni sustituye el criterio del equipo docente.";

    public record Celda(String codigo, Double valor, Double referencia, Double diferencia, String color) {
    }

    public record Fila(Integer grupoId, String nombre, Integer nAlumnos, String color, List<Celda> celdas,
                       int medidasEnCurso) {
    }

    public record Equidad(String factor, String factorNombre, String nivelPeor, String nivelMejor, Double pctPeor,
                          Double brecha, Double brechaEstandarizada, String color, String fiabilidad) {
    }

    public record Indicador(String codigo, String nombre, String unidad, boolean mejorSiSube) {
    }

    public record Panel(String centro, List<Indicador> indicadores, Fila centroFila, List<Fila> grupos,
                        List<String> gruposSinDatos, Map<String, Integer> recuento, List<Equidad> equidad,
                        String criterio, String aviso) {
    }
}
