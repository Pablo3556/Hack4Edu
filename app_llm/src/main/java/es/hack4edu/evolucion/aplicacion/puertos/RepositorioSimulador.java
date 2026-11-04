package es.hack4edu.evolucion.aplicacion.puertos;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.evolucion.dominio.CatalogoSimulador;
import es.hack4edu.evolucion.dominio.Simulador;

import java.util.List;
import java.util.Optional;

/** Puerto de salida: datos agregados que necesita el simulador "¿qué pasaría si…?". */
public interface RepositorioSimulador {

    List<CatalogoSimulador.FactorSimulable> factoresSimulables(Ambito ambito);

    /** Vacío si el factor no existe o no es accionable. */
    Optional<CatalogoSimulador.InfoFactor> factorAccionable(String codigo);

    List<CatalogoSimulador.NivelConSinDato> nivelesDelAmbito(String factor, Ambito ambito);

    /** Nota media y dispersión por nivel en el conjunto de centros (base de la asociación observada). */
    List<Simulador.NivelAsociacion> asociacionGlobal(String factor);
}
