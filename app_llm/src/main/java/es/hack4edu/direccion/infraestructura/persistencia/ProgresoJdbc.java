package es.hack4edu.direccion.infraestructura.persistencia;

import es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas;
import es.hack4edu.direccion.aplicacion.puertos.RepositorioProgreso;
import es.hack4edu.direccion.dominio.ProgresoDocente;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.decimal;
import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.entero;

/** Adaptador de salida: lee analitica.vw_progreso_grupo y vw_progreso_docente (V9) con el rol app_lector. */
@ApplicationScoped
public class ProgresoJdbc implements RepositorioProgreso {

    @Inject
    JdbcConsultas consultas;

    @Override
    public List<FilaGrupo> grupos(int centro) {
        return consultas.lista("""
                SELECT * FROM analitica.vw_progreso_grupo
                WHERE centro_id = ? ORDER BY ambito DESC, grupo_id""", List.of(centro),
                rs -> new FilaGrupo(entero(rs, "grupo_id"), rs.getString("grupo"), rs.getInt("n_alumnos"),
                        rs.getString("tutor"), rs.getInt("n_docentes"), entero(rs, "horas_semana"),
                        decimal(rs, "experiencia_media"), decimal(rs, "formacion_media"), medicion(rs)));
    }

    @Override
    public List<FilaDocente> docentes(int centro) {
        return consultas.lista("""
                SELECT * FROM analitica.vw_progreso_docente
                WHERE centro_id = ? ORDER BY codigo""", List.of(centro),
                rs -> new FilaDocente(rs.getString("codigo"), rs.getInt("anios_experiencia"),
                        rs.getString("nivel_formacion"), rs.getInt("horas_formacion_anio"),
                        rs.getString("tipo_contrato"), entero(rs, "horas_semana"), entero(rs, "n_grupos"),
                        rs.getString("grupos"), rs.getString("asignaturas"), entero(rs, "n_alumnos"), medicion(rs)));
    }

    private static ProgresoDocente.Medicion medicion(ResultSet rs) throws SQLException {
        return new ProgresoDocente.Medicion(decimal(rs, "progreso"), decimal(rs, "error_tipico"));
    }
}
