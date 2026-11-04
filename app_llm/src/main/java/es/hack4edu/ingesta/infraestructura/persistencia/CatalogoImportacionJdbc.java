package es.hack4edu.ingesta.infraestructura.persistencia;

import es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas;
import es.hack4edu.ingesta.aplicacion.puertos.CatalogoImportacion;
import es.hack4edu.ingesta.dominio.Importacion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.entero;
import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.fecha;

/** Adaptador de salida: catálogos y auditoría de cargas (vistas de analitica, rol app_lector). */
@ApplicationScoped
public class CatalogoImportacionJdbc implements CatalogoImportacion {

    @Inject
    JdbcConsultas consultas;

    @Override
    public List<Importacion.Periodo> periodos() {
        return consultas.lista("""
                SELECT id, codigo, nombre, fecha_inicio::text AS ini, fecha_fin::text AS fin,
                       coalesce(current_date BETWEEN fecha_inicio AND fecha_fin, false) AS actual
                FROM analitica.periodo
                WHERE NOT sintetico
                ORDER BY orden""", List.of(),
                rs -> new Importacion.Periodo(rs.getInt("id"), rs.getString("codigo"), rs.getString("nombre"),
                        rs.getString("ini"), rs.getString("fin"), rs.getBoolean("actual")));
    }

    @Override
    public Optional<String> nombreDelGrupo(int grupo, int centro) {
        return consultas.lista("""
                SELECT c.nombre || ' · ' || g.nombre AS nombre
                FROM analitica.grupo g JOIN analitica.centro c ON c.id = g.centro_id
                WHERE g.id = ? AND g.centro_id = ?""", List.of(grupo, centro), rs -> rs.getString("nombre"))
                .stream().findFirst();
    }

    @Override
    public Optional<String> nombreDelPeriodoImportable(int periodo) {
        return consultas.lista("SELECT nombre FROM analitica.periodo WHERE id = ? AND NOT sintetico", List.of(periodo),
                rs -> rs.getString("nombre")).stream().findFirst();
    }

    @Override
    public List<Importacion.Carga> historial(boolean todas, String usuario) {
        String sql = "SELECT * FROM analitica.vw_cargas WHERE tipo = 'docente'"
                + (todas ? "" : " AND usuario = ?") + " ORDER BY id DESC LIMIT 20";
        return consultas.lista(sql, todas ? List.of() : List.of(usuario),
                rs -> new Importacion.Carga(entero(rs, "id"), fecha(rs, "fecha"), fecha(rs, "actualizado"),
                        rs.getString("usuario"), rs.getString("archivo"), rs.getString("centro"),
                        rs.getString("grupo"), rs.getString("periodo"), entero(rs, "filas_procesadas"),
                        entero(rs, "filas_validas"), entero(rs, "filas_error"), entero(rs, "filas_cargadas"),
                        rs.getString("estado"), rs.getString("resultado")));
    }
}
