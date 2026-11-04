package es.hack4edu.datos;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Consultas de solo lectura con la conexión por defecto (app_lector): únicamente
 * vistas y catálogos de "analitica". Compartido por todos los recursos REST.
 */
@ApplicationScoped
public class Consultas {

    @FunctionalInterface
    public interface Fila<T> {
        T leer(ResultSet rs) throws SQLException;
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
            throw error(Response.Status.SERVICE_UNAVAILABLE,
                    "No se puede leer la base de datos: " + e.getMessage());
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

    public static WebApplicationException error(Response.Status estado, String mensaje) {
        return new WebApplicationException(Response.status(estado).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", mensaje)).build());
    }
}
