package es.hack4edu.compartido.dominio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmbitoTest {

    @Test
    void elTipoDependeDeLosParametros() {
        assertEquals("global", Ambito.de(null, null).tipo());
        assertEquals("centro", Ambito.de(3, null).tipo());
        assertEquals("grupo", Ambito.de(3, 10).tipo());
    }

    @Test
    void ungrupoSinCentroEsUnaPeticionInvalida() {
        ErrorAplicacion e = assertThrows(ErrorAplicacion.class, () -> Ambito.de(null, 10));
        assertEquals(ErrorAplicacion.Tipo.PETICION_INVALIDA, e.tipo());
    }
}
