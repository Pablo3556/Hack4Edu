package es.hack4edu.evolucion.aplicacion;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.compartido.dominio.ErrorAplicacion;
import es.hack4edu.datos.aplicacion.ConsultaAgregados;
import es.hack4edu.datos.dominio.Agregados;
import es.hack4edu.evolucion.aplicacion.puertos.RepositorioSimulador;
import es.hack4edu.evolucion.dominio.CatalogoSimulador;
import es.hack4edu.evolucion.dominio.Simulador;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Caso de uso: simulador "¿Qué pasaría si…?" sobre los factores ACCIONABLES (los contextuales, como la renta
 * o el género, no se simulan: no son palancas del centro).
 */
@ApplicationScoped
public class SimularEscenario {

    @Inject
    RepositorioSimulador repositorio;

    @Inject
    ConsultaAgregados datos;

    /** Factores accionables con sus niveles y si se pueden simular en el ámbito elegido. */
    public List<CatalogoSimulador.FactorSimulable> factores(Ambito ambito) {
        return repositorio.factoresSimulables(ambito);
    }

    public Simulador.Resultado simular(Ambito ambito, String factor, Integer desde, Integer hacia, Integer porcentaje) {
        if (factor == null || desde == null || hacia == null) {
            throw ErrorAplicacion.peticionInvalida("Indica el factor, el nivel de partida y el de llegada.");
        }
        int pct = porcentaje == null ? 100 : porcentaje;
        if (pct < 0 || pct > 100) {
            throw ErrorAplicacion.peticionInvalida("El porcentaje debe estar entre 0 y 100.");
        }
        CatalogoSimulador.InfoFactor info = repositorio.factorAccionable(factor).orElseThrow(() ->
                ErrorAplicacion.peticionInvalida(
                        "Solo se pueden simular factores accionables (los de contexto no son palancas del centro)."));

        List<CatalogoSimulador.NivelConSinDato> niveles = repositorio.nivelesDelAmbito(factor, ambito);
        List<Simulador.NivelAsociacion> asociacion = repositorio.asociacionGlobal(factor);
        if (asociacion.isEmpty()) {
            throw ErrorAplicacion.noEncontrado("No hay datos suficientes de este factor. ¿Están cargados los datos?");
        }
        Double notaMedia = datos.buscarResumen(ambito).map(Agregados.Resumen::notaMedia).orElse(null);
        String nombreAmbito = ambito.centro() == null ? "el conjunto de centros" : datos.nombreAmbito(ambito);

        return Simulador.simular(new Simulador.Entrada(nombreAmbito, info.nombre(), info.accionabilidad(),
                info.palanca(), niveles.stream().map(CatalogoSimulador.NivelConSinDato::nivel).toList(),
                niveles.isEmpty() ? null : niveles.get(0).pctSinDato(), asociacion, notaMedia, desde, hacia, pct));
    }
}
