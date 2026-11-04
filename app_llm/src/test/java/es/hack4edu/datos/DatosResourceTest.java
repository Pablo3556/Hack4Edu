package es.hack4edu.datos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Prueba unitaria (sin arrancar Quarkus ni PostgreSQL) de cómo se traduce el ámbito a SQL. */
class DatosResourceTest {

    @Test
    void sinParametrosEsGlobal() {
        DatosResource.Filtro f = DatosResource.filtro(null, null);
        assertEquals("ambito = 'global'", f.sql());
        assertEquals(List.of(), f.params());
    }

    @Test
    void conCentroEsAmbitoCentro() {
        DatosResource.Filtro f = DatosResource.filtro(3, null);
        assertEquals("ambito = 'centro' AND centro_id = ?", f.sql());
        assertEquals(List.of(3), f.params());
    }

    @Test
    void conCentroYGrupoEsAmbitoGrupo() {
        DatosResource.Filtro f = DatosResource.filtro(3, 10);
        assertEquals("ambito = 'grupo' AND centro_id = ? AND grupo_id = ?", f.sql());
        assertEquals(List.of(3, 10), f.params());
    }
}
