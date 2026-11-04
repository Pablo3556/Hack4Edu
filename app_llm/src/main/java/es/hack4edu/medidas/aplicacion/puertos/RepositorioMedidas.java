package es.hack4edu.medidas.aplicacion.puertos;

import es.hack4edu.medidas.dominio.Medidas;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Puerto de salida: medidas registradas y evolución de los indicadores que se vigilan. */
public interface RepositorioMedidas {

    /** Periodos reales (sin la línea base sintética), por orden. */
    List<Medidas.Periodo> periodosReales();

    boolean existeCentro(int centro);

    boolean grupoPerteneceAlCentro(int grupo, int centro);

    boolean existeFactor(String codigo);

    /** Guarda una medida ya validada y devuelve su id. */
    int insertar(Medidas.NuevaMedida medida, String creadaPor);

    /** @return false si no existe la medida */
    boolean cambiarEstado(int id, String estado);

    /** Medidas del centro; con grupo, las de ese grupo y las que afectan a todo el centro. Las en curso primero. */
    List<Medidas.MedidaRegistrada> buscar(int centro, Integer grupo);

    Optional<Medidas.MedidaRegistrada> porId(int id);

    /** Nombre de cada periodo por su orden. */
    Map<Integer, String> nombresDePeriodos();

    /** Indicadores por periodo de todos los grupos de un centro. */
    List<Medidas.ValorPeriodo> evolucionDeLosGrupos(int centro);

    /** Indicadores por periodo de todos los centros. */
    List<Medidas.ValorPeriodo> evolucionDeLosCentros();

    /** Número de medidas en curso por grupo del centro. */
    Map<Integer, Integer> enCursoPorGrupo(int centro);
}
