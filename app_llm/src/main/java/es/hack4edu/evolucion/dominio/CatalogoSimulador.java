package es.hack4edu.evolucion.dominio;

import java.util.List;

/** Factores accionables que se pueden simular y qué dato hay de ellos en un ámbito. */
public final class CatalogoSimulador {

    private CatalogoSimulador() {
    }

    public record Nivel(int orden, String etiqueta) {
    }

    public record FactorSimulable(String codigo, String nombre, Double accionabilidad, String palanca,
                                  List<Nivel> niveles, boolean disponibleEnAmbito, String brechaAmbito) {
    }

    public record InfoFactor(String nombre, Double accionabilidad, String palanca) {
    }

    /** Un nivel del factor en el ámbito, con el porcentaje de alumnado sin dato en ese factor. */
    public record NivelConSinDato(Simulador.NivelAmbito nivel, Double pctSinDato) {
    }
}
