package es.hack4edu.direccion.dominio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Cuándo se considera que el alumnado progresa más o menos de lo esperado (lógica pura). */
class ProgresoDocenteTest {

    private static ProgresoDocente.Estimacion estimar(double progreso, double errorTipico) {
        return ProgresoDocente.estimar(new ProgresoDocente.Medicion(progreso, errorTipico));
    }

    @Test
    void diferenciaClaraYRelevanteSeMarca() {
        assertEquals(ProgresoDocente.POR_ENCIMA, estimar(1.2, 0.3).veredicto());   // IC 0,61 … 1,79
        assertEquals(ProgresoDocente.POR_DEBAJO, estimar(-1.2, 0.3).veredicto());
    }

    @Test
    void siElIntervaloIncluyeElCeroEsLoEsperado() {
        ProgresoDocente.Estimacion e = estimar(0.9, 0.5);                          // IC −0,08 … 1,88
        assertEquals(ProgresoDocente.EN_LO_ESPERADO, e.veredicto());
        assertEquals(-0.08, e.icInferior(), 1e-9);
        assertEquals(1.88, e.icSuperior(), 1e-9);
    }

    @Test
    void unaDiferenciaPequenaNoSeMarcaAunqueSeaPrecisa() {
        // Con mucho alumnado el intervalo es estrecho, pero 0,3 puntos sobre 100 no es relevante
        assertEquals(ProgresoDocente.EN_LO_ESPERADO, estimar(0.3, 0.05).veredicto());
        assertEquals(ProgresoDocente.EN_LO_ESPERADO, estimar(-0.49, 0.05).veredicto());
        assertEquals(ProgresoDocente.POR_DEBAJO, estimar(-0.5, 0.05).veredicto());
    }

    @Test
    void sinMedicionNoHayVeredicto() {
        assertNull(ProgresoDocente.estimar(null).veredicto());
        assertNull(ProgresoDocente.estimar(new ProgresoDocente.Medicion(1.0, null)).veredicto());
        assertNull(ProgresoDocente.estimar(new ProgresoDocente.Medicion(null, 0.2)).progreso());
    }
}
