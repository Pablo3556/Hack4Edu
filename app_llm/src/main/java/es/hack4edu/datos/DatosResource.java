package es.hack4edu.datos;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API de solo lectura para la interfaz web. Lee únicamente las vistas agregadas del
 * esquema "analitica" (conexión como app_lector): nunca hay filas de alumnos.
 *
 * Ámbito de cada consulta: sin parámetros = global; ?centro=3 = centro; ?centro=3&grupo=10 = grupo.
 */
@Path("/api/datos")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class DatosResource {

    public record Grupo(int id, String nombre) {
    }

    public record Centro(int id, String nombre, String tipo, List<Grupo> grupos) {
    }

    public record Resumen(String ambito, Integer centroId, String centro, String tipoCentro, Integer grupoId,
                          String grupo, Integer nAlumnos, Double notaMedia, Double notaP25, Double notaMediana,
                          Double notaP75, Double difVsGlobal, Double asistenciaMedia, Double pctAsistenciaBaja,
                          Double horasEstudioMedia, Double tutoriasMedia, Double pctRecursosBajos,
                          Double pctCalidadDocenteBaja, Double pctExtraescolares, Double pctImplicacionFamiliarBaja) {
    }

    public record ResumenAmbito(Resumen ambito, Resumen global) {
    }

    public record Prioridad(Integer puesto, String factor, String factorNombre, String dimension, String nivelPeor,
                            Double pctPeor, Double notaPeor, String nivelMejor, Double notaMejor, Double brecha,
                            Double brechaEstandarizada, String magnitud, Double accionabilidad, Double prioridad,
                            String fiabilidad, String palanca, String aviso) {
    }

    public record Brecha(String factor, String factorNombre, String tipo, String dimension, String nivelPeor,
                         String nivelMejor, Double pctPeor, Double brecha, Double brechaEstandarizada) {
    }

    public record Factor(String codigo, String nombre, String tipo, String dimension, String descripcion) {
    }

    public record NivelFactor(Integer orden, String nivel, Integer n, Double pctAmbito, Double notaMedia,
                              Double difVsMediaAmbito, Double asistenciaMedia, Double pctSinDato) {
    }

    public record Calidad(String columna, Integer nAusentes, Double pctAusentes, Integer nTotal) {
    }

    @Inject
    DataSource dataSource;

    /** Centros y grupos para los selectores. */
    @GET
    @Path("/ambitos")
    public List<Centro> ambitos() {
        record FilaAmbito(int centroId, String centro, String tipo, int grupoId, String grupo) {
        }
        List<FilaAmbito> filas = consultar("""
                SELECT c.id AS centro_id, c.nombre AS centro, c.tipo_centro, g.id AS grupo_id, g.nombre AS grupo
                FROM analitica.centro c
                JOIN analitica.grupo g ON g.centro_id = c.id
                ORDER BY c.id, g.id""", List.of(),
                rs -> new FilaAmbito(rs.getInt("centro_id"), rs.getString("centro"), rs.getString("tipo_centro"),
                        rs.getInt("grupo_id"), rs.getString("grupo")));
        Map<Integer, Centro> centros = new LinkedHashMap<>();
        for (FilaAmbito f : filas) {
            centros.computeIfAbsent(f.centroId(), id -> new Centro(id, f.centro(), f.tipo(), new ArrayList<>()))
                    .grupos().add(new Grupo(f.grupoId(), f.grupo()));
        }
        return List.copyOf(centros.values());
    }

    /** Indicadores del ámbito pedido y, para comparar, los globales. */
    @GET
    @Path("/resumen")
    public ResumenAmbito resumen(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        Filtro f = filtro(centro, grupo);
        List<Resumen> ambito = consultar("SELECT * FROM analitica.vw_resumen WHERE " + f.sql(), f.params(),
                DatosResource::resumen);
        List<Resumen> global = consultar("SELECT * FROM analitica.vw_resumen WHERE ambito = 'global'", List.of(),
                DatosResource::resumen);
        if (ambito.isEmpty()) {
            throw error(Response.Status.NOT_FOUND,
                    "Sin datos para ese ámbito (no existe o tiene menos de 10 alumnos). ¿Están cargados los datos?");
        }
        return new ResumenAmbito(ambito.get(0), global.isEmpty() ? null : global.get(0));
    }

    /** Resumen de todos los centros (comparativa). */
    @GET
    @Path("/centros")
    public List<Resumen> centros() {
        return consultar("SELECT * FROM analitica.vw_resumen WHERE ambito = 'centro' ORDER BY centro_id",
                List.of(), DatosResource::resumen);
    }

    /** Ranking de intervenciones del ámbito, con su desglose. */
    @GET
    @Path("/prioridades")
    public List<Prioridad> prioridades(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        Filtro f = filtro(centro, grupo);
        return consultar("SELECT * FROM analitica.vw_prioridades WHERE " + f.sql() + " ORDER BY puesto, factor",
                f.params(), rs -> new Prioridad(entero(rs, "puesto"), rs.getString("factor"),
                        rs.getString("factor_nombre"), rs.getString("dimension_reto"), rs.getString("nivel_peor"),
                        decimal(rs, "pct_peor"), decimal(rs, "nota_peor"), rs.getString("nivel_mejor"),
                        decimal(rs, "nota_mejor"), decimal(rs, "brecha"), decimal(rs, "brecha_estandarizada"),
                        rs.getString("magnitud"), decimal(rs, "accionabilidad"), decimal(rs, "prioridad"),
                        rs.getString("fiabilidad"), rs.getString("palanca"), rs.getString("aviso")));
    }

    /** Brecha de todos los factores (accionables y contextuales) en el ámbito. */
    @GET
    @Path("/brechas")
    public List<Brecha> brechas(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        Filtro f = filtro(centro, grupo);
        return consultar("SELECT * FROM analitica.vw_factor_brecha WHERE " + f.sql()
                        + " ORDER BY tipo, brecha_estandarizada DESC NULLS LAST", f.params(),
                rs -> new Brecha(rs.getString("factor"), rs.getString("factor_nombre"), rs.getString("tipo"),
                        rs.getString("dimension_reto"), rs.getString("nivel_peor"), rs.getString("nivel_mejor"),
                        decimal(rs, "pct_peor"), decimal(rs, "brecha"), decimal(rs, "brecha_estandarizada")));
    }

    /** Catálogo de factores para el selector. */
    @GET
    @Path("/factores")
    public List<Factor> factores() {
        return consultar("""
                SELECT codigo, nombre, tipo, dimension_reto, descripcion
                FROM analitica.factor
                ORDER BY tipo, accionabilidad DESC NULLS LAST, nombre""", List.of(),
                rs -> new Factor(rs.getString("codigo"), rs.getString("nombre"), rs.getString("tipo"),
                        rs.getString("dimension_reto"), rs.getString("descripcion")));
    }

    /** Nota media por nivel de un factor en el ámbito. Vacío si el factor está oculto por el umbral. */
    @GET
    @Path("/factor/{codigo}")
    public List<NivelFactor> factor(@PathParam("codigo") String codigo, @QueryParam("centro") Integer centro,
                                    @QueryParam("grupo") Integer grupo) {
        Filtro f = filtro(centro, grupo);
        List<Object> params = new ArrayList<>();
        params.add(codigo);
        params.addAll(f.params());
        return consultar("SELECT * FROM analitica.vw_factor_nivel WHERE factor = ? AND " + f.sql() + " ORDER BY orden",
                params, rs -> new NivelFactor(entero(rs, "orden"), rs.getString("nivel"), entero(rs, "n"),
                        decimal(rs, "pct_ambito"), decimal(rs, "nota_media"), decimal(rs, "dif_vs_media_ambito"),
                        decimal(rs, "asistencia_media"), decimal(rs, "pct_sin_dato")));
    }

    /** Valores ausentes por columna. */
    @GET
    @Path("/calidad")
    public List<Calidad> calidad() {
        return consultar("""
                SELECT columna, n_ausentes, pct_ausentes, n_total
                FROM analitica.vw_calidad_datos
                ORDER BY n_ausentes DESC, columna""", List.of(),
                rs -> new Calidad(rs.getString("columna"), entero(rs, "n_ausentes"), decimal(rs, "pct_ausentes"),
                        entero(rs, "n_total")));
    }

    // -----------------------------------------------------------------
    // Utilidades
    // -----------------------------------------------------------------

    record Filtro(String sql, List<Object> params) {
    }

    /** Traduce los parámetros a la condición de ámbito de las vistas. */
    static Filtro filtro(Integer centro, Integer grupo) {
        if (grupo != null) {
            if (centro == null) {
                throw error(Response.Status.BAD_REQUEST, "Para elegir un grupo hay que indicar también el centro");
            }
            return new Filtro("ambito = 'grupo' AND centro_id = ? AND grupo_id = ?", List.of(centro, grupo));
        }
        if (centro != null) {
            return new Filtro("ambito = 'centro' AND centro_id = ?", List.of(centro));
        }
        return new Filtro("ambito = 'global'", List.of());
    }

    @FunctionalInterface
    private interface Fila<T> {
        T leer(ResultSet rs) throws SQLException;
    }

    private <T> List<T> consultar(String sql, List<Object> params, Fila<T> fila) {
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

    private static Resumen resumen(ResultSet rs) throws SQLException {
        return new Resumen(rs.getString("ambito"), entero(rs, "centro_id"), rs.getString("centro"),
                rs.getString("tipo_centro"), entero(rs, "grupo_id"), rs.getString("grupo"), entero(rs, "n_alumnos"),
                decimal(rs, "nota_media"), decimal(rs, "nota_p25"), decimal(rs, "nota_mediana"),
                decimal(rs, "nota_p75"), decimal(rs, "dif_vs_global"), decimal(rs, "asistencia_media"),
                decimal(rs, "pct_asistencia_baja"), decimal(rs, "horas_estudio_media"), decimal(rs, "tutorias_media"),
                decimal(rs, "pct_recursos_bajos"), decimal(rs, "pct_calidad_docente_baja"),
                decimal(rs, "pct_extraescolares"), decimal(rs, "pct_implicacion_familiar_baja"));
    }

    private static Double decimal(ResultSet rs, String columna) throws SQLException {
        BigDecimal v = rs.getBigDecimal(columna);
        return v == null ? null : v.doubleValue();
    }

    private static Integer entero(ResultSet rs, String columna) throws SQLException {
        Object v = rs.getObject(columna);
        return v instanceof Number n ? n.intValue() : null;
    }

    private static WebApplicationException error(Response.Status estado, String mensaje) {
        return new WebApplicationException(Response.status(estado).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", mensaje)).build());
    }
}
