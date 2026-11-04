package es.hack4edu.evolucion.aplicacion.puertos;

import es.hack4edu.evolucion.dominio.MotorAlertas;

import java.util.List;
import java.util.Map;

/** Puerto de salida: indicadores agregados por periodo (analitica.vw_evolucion). */
public interface RepositorioEvolucion {

    /** Serie de un centro (grupo == null) o de un grupo, ordenada por periodo. */
    List<MotorAlertas.Punto> serie(int centro, Integer grupo);

    /** Series de todos los grupos del centro, por nombre de grupo. */
    Map<String, List<MotorAlertas.Punto>> seriesDeLosGrupos(int centro);
}
