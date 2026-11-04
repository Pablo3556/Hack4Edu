package es.hack4edu.evolucion.infraestructura.persistencia;

import es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas;
import es.hack4edu.evolucion.aplicacion.puertos.RepositorioEvolucion;
import es.hack4edu.evolucion.dominio.MotorAlertas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.decimal;
import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.entero;
import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.fecha;

/** Adaptador de salida: lee analitica.vw_evolucion con el rol app_lector. */
@ApplicationScoped
public class EvolucionJdbc implements RepositorioEvolucion {

    @Inject
    JdbcConsultas consultas;

    @Override
    public List<MotorAlertas.Punto> serie(int centro, Integer grupo) {
        return grupo == null
                ? consultas.lista("SELECT * FROM analitica.vw_evolucion WHERE ambito = 'centro' AND centro_id = ? "
                + "ORDER BY periodo_orden", List.of(centro), EvolucionJdbc::punto)
                : consultas.lista("SELECT * FROM analitica.vw_evolucion WHERE ambito = 'grupo' AND centro_id = ? "
                + "AND grupo_id = ? ORDER BY periodo_orden", List.of(centro, grupo), EvolucionJdbc::punto);
    }

    @Override
    public Map<String, List<MotorAlertas.Punto>> seriesDeLosGrupos(int centro) {
        Map<String, List<MotorAlertas.Punto>> grupos = new LinkedHashMap<>();
        consultas.lista("SELECT * FROM analitica.vw_evolucion WHERE ambito = 'grupo' AND centro_id = ? "
                        + "ORDER BY grupo_id, periodo_orden", List.of(centro),
                rs -> Map.entry(rs.getString("grupo"), punto(rs)))
                .forEach(e -> grupos.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return grupos;
    }

    static MotorAlertas.Punto punto(ResultSet rs) throws SQLException {
        return new MotorAlertas.Punto(rs.getInt("periodo_id"), rs.getString("periodo"), rs.getInt("periodo_orden"),
                rs.getBoolean("periodo_sintetico"), rs.getInt("n_alumnos"), rs.getInt("n_grupos"),
                decimal(rs, "nota_media"), decimal(rs, "nota_sd"), entero(rs, "n_asistencia"),
                decimal(rs, "asistencia_media"), decimal(rs, "asistencia_sd"), decimal(rs, "pct_asistencia_baja"),
                decimal(rs, "horas_estudio_media"), decimal(rs, "tutorias_media"), fecha(rs, "actualizado"));
    }
}
