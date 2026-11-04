package es.hack4edu.compartido.infraestructura.persistencia;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.compartido.dominio.ErrorAplicacion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Consultas con la conexión por defecto (app_lector): únicamente vistas y catálogos de "analitica"
 * y las tablas propias de "ia". Lo usan los adaptadores de persistencia de todos los módulos.
 */
@ApplicationScoped
public class JdbcConsultas {

    @FunctionalInterface
    public interface Fila<T> {
        T leer(ResultSet rs) throws SQLException;
    }

    /** Condición SQL de ámbito para las vistas agregadas, con sus parámetros. */
    public record FiltroSql(String sql, List<Object> params) {

        public static FiltroSql de(Ambito a) {
            if (a.grupo() != null) {
                return new FiltroSql("ambito = 'grupo' AND centro_id = ? AND grupo_id = ?", List.of(a.centro(), a.grupo()));
            }
            if (a.centro() != null) {
                return new FiltroSql("ambito = 'centro' AND centro_id = ?", List.of(a.centro()));
            }
            return new FiltroSql("ambito = 'global'", List.of());
        }
    }

    @Inject
    DataSource dataSource;

    public <T> List<T> lista(String sql, List<?> params, Fila<T> fila) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            List<T> filas = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    filas.add(fila.leer(rs));
                }
            }
            return filas;
        } catch (SQLException e) {
            throw ErrorAplicacion.noDisponible("No se puede leer la base de datos: " + e.getMessage());
        }
    }

    public static Double decimal(ResultSet rs, String columna) throws SQLException {
        BigDecimal v = rs.getBigDecimal(columna);
        return v == null ? null : v.doubleValue();
    }

    public static Integer entero(ResultSet rs, String columna) throws SQLException {
        Object v = rs.getObject(columna);
        return v instanceof Number n ? n.intValue() : null;
    }

    /** Marca de tiempo en ISO-8601 (o null). */
    public static String fecha(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime v = rs.getObject(columna, OffsetDateTime.class);
        return v == null ? null : v.toString();
    }
}
