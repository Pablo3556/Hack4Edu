package es.hack4edu.ingesta;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.postgresql.util.PSQLException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Escrituras de la ingesta con la conexión "ingesta" (rol app_ingesta).
 * Ese rol solo puede insertar en staging.spf_raw y ejecutar estos tres puntos de entrada:
 * staging.abrir_carga, staging.actualizar_carga y staging.transformar_ingesta (V7).
 */
@ApplicationScoped
public class RepositorioIngesta {

    private static final List<CampoIngesta> CAMPOS = List.of(CampoIngesta.values());
    private static final String INSERT_RAW = "INSERT INTO staging.spf_raw (carga_id, "
            + CAMPOS.stream().map(CampoIngesta::columnaRaw).collect(Collectors.joining(", "))
            + ") VALUES (?" + ", ?".repeat(CAMPOS.size()) + ")";
    private static final int LOTE = 500;

    @Inject
    @io.quarkus.agroal.DataSource("ingesta")
    DataSource dataSource;

    /** Registra el resultado de la validación. Devuelve el id de la carga. */
    public int abrirCarga(String usuario, String archivo, int grupo, int periodo, int filas, int validas,
                          int errores, String estado, String resultado) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT staging.abrir_carga(?, ?, ?::smallint, ?::smallint, ?, ?, ?, ?, ?)")) {
            ps.setString(1, usuario);
            ps.setString(2, archivo);
            ps.setInt(3, grupo);
            ps.setInt(4, periodo);
            ps.setInt(5, filas);
            ps.setInt(6, validas);
            ps.setInt(7, errores);
            ps.setString(8, estado);
            ps.setString(9, resultado);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void actualizarCarga(int id, String estado, Integer validas, Integer errores, String resultado)
            throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT staging.actualizar_carga(?, ?, ?, ?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, estado);
            if (validas == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, validas);
            if (errores == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, errores);
            ps.setString(5, resultado);
            ps.execute();
        }
    }

    /**
     * Fase 2: inserta los registros normalizados en spf_raw y ejecuta la transformación,
     * todo en una transacción (o se importa entero o no se importa nada).
     */
    public void confirmar(int cargaId, List<Map<CampoIngesta, String>> registros) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(INSERT_RAW)) {
                    int pendientes = 0;
                    for (Map<CampoIngesta, String> r : registros) {
                        ps.setInt(1, cargaId);
                        for (int i = 0; i < CAMPOS.size(); i++) {
                            ps.setString(i + 2, r.get(CAMPOS.get(i)));
                        }
                        ps.addBatch();
                        if (++pendientes == LOTE) {
                            ps.executeBatch();
                            pendientes = 0;
                        }
                    }
                    if (pendientes > 0) {
                        ps.executeBatch();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("CALL staging.transformar_ingesta(?)")) {
                    ps.setInt(1, cargaId);
                    ps.execute();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Mensaje del servidor PostgreSQL sin el envoltorio técnico. */
    static String mensaje(SQLException e) {
        SQLException actual = e;
        while (actual != null) {
            if (actual instanceof PSQLException p && p.getServerErrorMessage() != null) {
                return p.getServerErrorMessage().getMessage();
            }
            actual = actual.getNextException();
        }
        return e.getMessage();
    }
}
