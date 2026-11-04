package es.hack4edu.direccion.dominio;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas del semáforo (lógica pura, sin base de datos). */
class SemaforoTest {

    private static Semaforo.Criterio criterio(String codigo) {
        return Semaforo.CRITERIOS.stream().filter(c -> c.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    @Test
    void indicadorQueMejoraConSubeEstaEnVerdeSiSuperaAlConjunto() {
        assertEquals("verde", Semaforo.colorIndicador(criterio("nota_media"), 70.0, 67.0));
    }

    @Test
    void notaBajaPasaDeVerdeAAmbarYRojo() {
        Semaforo.Criterio nota = criterio("nota_media");
        assertEquals("verde", Semaforo.colorIndicador(nota, 66.0, 67.0));   // -1,0: dentro de la tolerancia
        assertEquals("ambar", Semaforo.colorIndicador(nota, 65.0, 67.0));   // -2,0
        assertEquals("ambar", Semaforo.colorIndicador(nota, 64.0, 67.0));   // -3,0: límite del ámbar
        assertEquals("rojo", Semaforo.colorIndicador(nota, 63.9, 67.0));
    }

    @Test
    void enIndicadoresDondeBajarEsMejorLaDireccionSeInvierte() {
        Semaforo.Criterio baja = criterio("pct_asistencia_baja");
        assertEquals("verde", Semaforo.colorIndicador(baja, 20.0, 30.0));   // menos alumnado con poca asistencia
        assertEquals("ambar", Semaforo.colorIndicador(baja, 36.0, 30.0));
        assertEquals("rojo", Semaforo.colorIndicador(baja, 40.0, 30.0));
    }

    @Test
    void sinValorOSinReferenciaNoHayColor() {
        assertNull(Semaforo.colorIndicador(criterio("nota_media"), null, 67.0));
        assertNull(Semaforo.colorIndicador(criterio("nota_media"), 67.0, null));
    }

    @Test
    void brechasDeEquidadSiguenLasMagnitudesDeLasPrioridades() {
        assertEquals("verde", Semaforo.colorBrecha(0.19));
        assertEquals("ambar", Semaforo.colorBrecha(0.2));
        assertEquals("ambar", Semaforo.colorBrecha(0.49));
        assertEquals("rojo", Semaforo.colorBrecha(0.5));
        assertEquals("rojo", Semaforo.colorBrecha(-0.8));  // el signo no importa
        assertNull(Semaforo.colorBrecha(null));
    }

    @Test
    void elColorDelGrupoEsElPeorDeSusIndicadores() {
        assertEquals("rojo", Semaforo.peor(List.of("verde", "rojo", "ambar")));
        assertEquals("ambar", Semaforo.peor(List.of("verde", "ambar", "verde")));
        assertEquals("verde", Semaforo.peor(Arrays.asList("verde", null)));
        assertNull(Semaforo.peor(Arrays.asList(null, null)));
    }
}
