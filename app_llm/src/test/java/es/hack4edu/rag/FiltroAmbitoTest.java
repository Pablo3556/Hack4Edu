package es.hack4edu.rag;

import org.junit.jupiter.api.Test;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FiltroAmbitoTest {

    @Test
    void sinCentroExcluyeGrupos() {
        assertEquals(metadataKey("ambito").isNotEqualTo("grupo"),
                FiltroAmbito.desde("¿Qué factor tiene más brecha?"));
    }

    @Test
    void globalSoloFichasGlobales() {
        assertEquals(metadataKey("ambito").isEqualTo("global"),
                FiltroAmbito.desde("¿Qué priorizar a nivel global?"));
    }

    @Test
    void centroConCeroDelante() {
        assertEquals(metadataKey("centro_id").isEqualTo(3).and(metadataKey("ambito").isEqualTo("centro")),
                FiltroAmbito.desde("Prioridades del Centro Demo 03"));
    }

    @Test
    void grupoDeUnCentro() {
        // Grupo B del centro 3 → id (3-1)*4 + 2 = 10
        assertEquals(metadataKey("grupo_id").isEqualTo(10)
                        .or(metadataKey("centro_id").isEqualTo(3).and(metadataKey("ambito").isEqualTo("centro"))),
                FiltroAmbito.desde("¿Cómo va el grupo B del centro 3?"));
    }

    @Test
    void todosLosGruposDeUnCentro() {
        assertEquals(metadataKey("centro_id").isEqualTo(7),
                FiltroAmbito.desde("Compara los grupos del Centro Demo 07"));
    }
}
