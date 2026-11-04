package es.hack4edu.evolucion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Alertas agregadas y simulador (lógica pura, sin base de datos). */
class EvolucionTest {

    private static MotorAlertas.Punto p(int id, boolean sintetico, int n, double nota, double asistencia, double pctBaja) {
        return new MotorAlertas.Punto(id, "P" + id, id, sintetico, n, 1, nota, 10.0, n, asistencia, 8.0, pctBaja,
                20.0, 1.5, "2026-11-04T10:00:00Z");
    }

    private static MotorAlertas.Resultado calcular(List<MotorAlertas.Punto> serie) {
        return MotorAlertas.calcular(new MotorAlertas.Entrada("Grupo B", serie, Map.of(), "Centro 3",
                new MotorAlertas.Referencia("conjunto de centros", 67.0, 80.0), null));
    }

    @Test
    void conUnSoloPeriodoNoHayAlertasDeEvolucion() {
        MotorAlertas.Resultado r = calcular(List.of(p(0, true, 150, 67, 80, 40)));
        assertFalse(r.datosSuficientes());
        assertTrue(r.alertas().isEmpty());
        assertTrue(r.mensajeDatos().contains("Solo hay datos de un periodo"));
    }

    @Test
    void bajadaGrandeYSignificativaDeAsistenciaEsDeAtencion() {
        MotorAlertas.Resultado r = calcular(List.of(p(1, false, 60, 67, 85, 30), p(2, false, 60, 67, 78, 33)));
        MotorAlertas.Alerta a = r.alertas().stream().filter(x -> x.codigo().equals("asistencia_media")).findFirst().orElseThrow();
        assertEquals(MotorAlertas.Nivel.ATENCION, a.nivel());
        assertEquals(-7.0, a.variacion(), 1e-9);
        assertEquals("P2", a.periodo());
        assertEquals("P1", a.periodoReferencia());
    }

    @Test
    void variacionesPequenasONoSignificativasNoGeneranAlerta() {
        // -1,5 puntos: por debajo del umbral práctico
        assertTrue(calcular(List.of(p(1, false, 500, 67, 80, 40), p(2, false, 500, 65.5, 80, 40))).alertas().isEmpty());
        // -3 puntos con 12 registros por periodo: no se distingue del azar
        assertTrue(calcular(List.of(p(1, false, 12, 67, 80, 40), p(2, false, 12, 64, 80, 40))).alertas().stream()
                .noneMatch(a -> a.codigo().equals("nota_media")));
    }

    @Test
    void tendenciaDescendenteConTresPeriodosReales() {
        MotorAlertas.Resultado r = calcular(List.of(p(1, false, 30, 70, 80, 40), p(2, false, 30, 68.5, 80, 40),
                p(3, false, 30, 67, 80, 40)));
        assertTrue(r.alertas().stream().anyMatch(a -> a.codigo().equals("nota_media_tendencia")));
    }

    @Test
    void lasAlertasNuncaNombranAlumnos() {
        MotorAlertas.Resultado r = calcular(List.of(p(1, false, 60, 72, 88, 20), p(2, false, 60, 62, 70, 60)));
        assertFalse(r.alertas().isEmpty());
        r.alertas().forEach(a -> assertFalse(a.mensaje().toLowerCase().contains("alumno ")));
    }

    private static Simulador.Entrada entrada(List<Simulador.NivelAmbito> ambito, double notaB, int desde, int hacia, int pct) {
        return new Simulador.Entrada("Grupo B", "Tutorías", 0.9, "Ampliar tutorías", ambito, 0.0,
                List.of(new Simulador.NivelAsociacion(1, "0", 1000, 66.0, 4.0),
                        new Simulador.NivelAsociacion(2, "1", 1000, notaB, 4.0)), 66.5, desde, hacia, pct);
    }

    private static final List<Simulador.NivelAmbito> AMBITO = List.of(
            new Simulador.NivelAmbito(1, "0", 50, 50.0, 66.0), new Simulador.NivelAmbito(2, "1", 50, 50.0, 67.0));

    @Test
    void simulacionAplicaLaAsociacionALaProporcionQueCambia() {
        Simulador.Resultado r = Simulador.simular(entrada(AMBITO, 68.0, 1, 2, 50));
        assertTrue(r.estimable());
        assertEquals(25.0, r.proporcionAfectada(), 1e-9);    // 50 % del 50 %
        assertEquals(0.5, r.variacion(), 1e-9);              // 0,25 × 2 puntos
        assertEquals(67.0, r.notaEscenario(), 1e-9);
        assertTrue(r.variacionMin() < r.variacion() && r.variacion() < r.variacionMax());
        assertEquals(25.0, r.niveles().get(0).pctEscenario(), 1e-9);
        assertEquals(75.0, r.niveles().get(1).pctEscenario(), 1e-9);
        assertTrue(r.explicacion().contains("no implica que el cambio la produzca"));
        assertEquals(Simulador.AVISO, r.aviso());
    }

    @Test
    void sinAsociacionDistinguibleOSinDatosNoSeEstima() {
        Simulador.Resultado igual = Simulador.simular(entrada(AMBITO, 66.1, 1, 2, 100));
        assertFalse(igual.estimable());
        assertTrue(igual.motivo().contains("no se distingue de cero"));
        assertNull(igual.notaEscenario());

        Simulador.Resultado oculto = Simulador.simular(entrada(List.of(), 70.0, 1, 2, 100));
        assertFalse(oculto.estimable());
        assertTrue(oculto.motivo().contains("menos de 10 registros"));

        assertFalse(Simulador.simular(entrada(AMBITO, 70.0, 2, 2, 100)).estimable());
    }
}
