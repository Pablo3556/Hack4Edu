package es.hack4edu.medidas.dominio;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Valoración de medidas (lógica pura, sin base de datos). */
class EvaluadorMedidaTest {

    private static final Map<Integer, String> NOMBRES = Map.of(4, "2026-27 · 1.er trimestre", 5, "2026-27 · 2.º trimestre",
            6, "2026-27 · 3.er trimestre");

    @Test
    void sinDatosEnElPeriodoDeInicioNoSePuedeMedirElAntes() {
        EvaluadorMedida.Evaluacion e = EvaluadorMedida.evaluar(Indicador.ASISTENCIA_MEDIA, Map.of(5, 80.0), List.of(),
                NOMBRES, 4);
        assertEquals(EvaluadorMedida.SIN_DATOS_BASE, e.estado());
        assertNull(e.veredicto());
    }

    @Test
    void sinPeriodoPosteriorQuedaPendiente() {
        EvaluadorMedida.Evaluacion e = EvaluadorMedida.evaluar(Indicador.ASISTENCIA_MEDIA, Map.of(4, 80.0), List.of(),
                NOMBRES, 4);
        assertEquals(EvaluadorMedida.PENDIENTE, e.estado());
        assertEquals(80.0, e.antes());
        assertNull(e.despues());
    }

    @Test
    void mejoraRealSeDescuentaDeLaTendenciaGeneral() {
        // El grupo sube 6 pp; los demás grupos suben 4: efecto atribuible +2
        EvaluadorMedida.Evaluacion e = EvaluadorMedida.evaluar(Indicador.ASISTENCIA_MEDIA,
                Map.of(4, 80.0, 5, 86.0),
                List.of(Map.of(4, 78.0, 5, 82.0), Map.of(4, 82.0, 5, 86.0)), NOMBRES, 4);
        assertEquals(EvaluadorMedida.EVALUADA, e.estado());
        assertEquals(6.0, e.variacion(), 1e-9);
        assertEquals(4.0, e.variacionReferencia(), 1e-9);
        assertEquals(2.0, e.efectoNeto(), 1e-9);
        assertEquals(EvaluadorMedida.MEJORA, e.veredicto());
        assertTrue(e.mensaje().contains("no demuestra"));
    }

    @Test
    void unaSubidaQueSoloSigueLaTendenciaGeneralNoEsMejora() {
        EvaluadorMedida.Evaluacion e = EvaluadorMedida.evaluar(Indicador.NOTA_MEDIA,
                Map.of(4, 60.0, 5, 65.0), List.of(Map.of(4, 60.0, 5, 65.0)), NOMBRES, 4);
        assertEquals(0.0, e.efectoNeto(), 1e-9);
        assertEquals(EvaluadorMedida.SIN_CAMBIO, e.veredicto());
    }

    @Test
    void enIndicadoresDondeBajarEsMejorUnaBajadaEsFavorable() {
        EvaluadorMedida.Evaluacion e = EvaluadorMedida.evaluar(Indicador.PCT_ASISTENCIA_BAJA,
                Map.of(4, 40.0, 5, 30.0), List.of(), NOMBRES, 4);
        assertEquals(-10.0, e.efectoNeto(), 1e-9);
        assertEquals(EvaluadorMedida.MEJORA, e.veredicto());
        assertTrue(e.mensaje().contains("no se ha descontado"));
    }

    @Test
    void empeoramientoSuperiorAlUmbralSeAvisa() {
        EvaluadorMedida.Evaluacion e = EvaluadorMedida.evaluar(Indicador.NOTA_MEDIA,
                Map.of(4, 70.0, 5, 66.0), List.of(), NOMBRES, 4);
        assertEquals(EvaluadorMedida.EMPEORA, e.veredicto());
    }

    @Test
    void usaElUltimoPeriodoPosteriorYIgnoraLosAnteriores() {
        EvaluadorMedida.Evaluacion e = EvaluadorMedida.evaluar(Indicador.NOTA_MEDIA,
                Map.of(3, 50.0, 4, 60.0, 5, 62.0, 6, 66.0), List.of(), NOMBRES, 4);
        assertEquals("2026-27 · 3.er trimestre", e.periodoSeguimiento());
        assertEquals(6.0, e.variacion(), 1e-9);
    }

    @Test
    void losPaisSinDatosEnAmbosPeriodosNoEntranEnLaReferencia() {
        assertNull(EvaluadorMedida.variacionMediaPares(List.of(Map.of(4, 1.0), Map.of(5, 2.0)), 4, 5));
    }
}
