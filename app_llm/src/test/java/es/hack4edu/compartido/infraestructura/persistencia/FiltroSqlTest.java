package es.hack4edu.compartido.infraestructura.persistencia;

import es.hack4edu.compartido.dominio.Ambito;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Cómo se traduce el ámbito a SQL (sin arrancar Quarkus ni PostgreSQL). */
class FiltroSqlTest {

    @Test
    void sinParametrosEsGlobal() {
        JdbcConsultas.FiltroSql f = JdbcConsultas.FiltroSql.de(Ambito.GLOBAL);
        assertEquals("ambito = 'global'", f.sql());
        assertEquals(List.of(), f.params());
    }

    @Test
    void conCentroEsAmbitoCentro() {
        JdbcConsultas.FiltroSql f = JdbcConsultas.FiltroSql.de(Ambito.de(3, null));
        assertEquals("ambito = 'centro' AND centro_id = ?", f.sql());
        assertEquals(List.of(3), f.params());
    }

    @Test
    void conCentroYGrupoEsAmbitoGrupo() {
        JdbcConsultas.FiltroSql f = JdbcConsultas.FiltroSql.de(Ambito.de(3, 10));
        assertEquals("ambito = 'grupo' AND centro_id = ? AND grupo_id = ?", f.sql());
        assertEquals(List.of(3, 10), f.params());
    }
}
