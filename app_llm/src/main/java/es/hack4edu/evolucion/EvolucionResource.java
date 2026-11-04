package es.hack4edu.evolucion;

import es.hack4edu.datos.Consultas;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static es.hack4edu.datos.Consultas.decimal;
import static es.hack4edu.datos.Consultas.entero;
import static es.hack4edu.datos.Consultas.error;
import static es.hack4edu.datos.Consultas.fecha;

/**
 * Evolución agregada de un grupo o centro por periodos, con alertas tempranas.
 * Solo lee vistas agregadas (vw_evolucion, vw_resumen, vw_prioridades) como app_lector.
 */
@Path("/api/evolucion")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class EvolucionResource {

    public record Comparacion(String codigo, String indicador, String unidad, Double anterior, Double actual,
                              Double variacion, boolean mejorSiSube) {
    }

    public record Evolucion(String ambito, String tipoAmbito, List<MotorAlertas.Punto> serie,
                            List<MotorAlertas.Punto> serieCentro, Map<String, List<MotorAlertas.Punto>> grupos,
                            String periodoActual, String periodoAnterior, List<Comparacion> comparaciones,
                            boolean datosSuficientes, String mensajeDatos, List<MotorAlertas.Alerta> alertas,
                            String orientacion, String ultimaActualizacion, String aviso) {
    }

    static final String AVISO = "Indicadores agregados de grupos con al menos 10 registros. Las alertas son "
            + "informativas: describen cambios en medias y porcentajes, no señalan ni clasifican a ningún alumno "
            + "y no son predicciones.";

    @Inject
    Consultas consultas;

    @GET
    public Evolucion evolucion(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        if (centro == null) {
            throw error(Response.Status.BAD_REQUEST, "Elige un centro (rol Dirección) o un grupo (rol Docente).");
        }
        boolean esGrupo = grupo != null;
        String ambito = nombreAmbito(centro, grupo);
        String nombreCentro = nombreAmbito(centro, null);

        List<MotorAlertas.Punto> serie = esGrupo
                ? puntos("ambito = 'grupo' AND centro_id = ? AND grupo_id = ?", List.of(centro, grupo))
                : puntos("ambito = 'centro' AND centro_id = ?", List.of(centro));
        List<MotorAlertas.Punto> serieCentro = esGrupo
                ? puntos("ambito = 'centro' AND centro_id = ?", List.of(centro)) : List.of();

        Map<String, List<MotorAlertas.Punto>> grupos = new LinkedHashMap<>();
        consultas.lista("SELECT * FROM analitica.vw_evolucion WHERE ambito = 'grupo' AND centro_id = ? "
                        + "ORDER BY grupo_id, periodo_orden", List.of(centro),
                rs -> Map.entry(rs.getString("grupo"), punto(rs)))
                .forEach(e -> grupos.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));

        MotorAlertas.Referencia referencia = consultas.lista(
                "SELECT nota_media, asistencia_media FROM analitica.vw_resumen WHERE ambito = 'global'", List.of(),
                rs -> new MotorAlertas.Referencia("conjunto de centros", decimal(rs, "nota_media"),
                        decimal(rs, "asistencia_media"))).stream().findFirst().orElse(null);

        String filtroPrioridad = esGrupo ? "ambito = 'grupo' AND centro_id = ? AND grupo_id = ?"
                : "ambito = 'centro' AND centro_id = ?";
        MotorAlertas.PrioridadPrincipal prioridad = consultas.lista(
                "SELECT factor_nombre, nivel_peor, pct_peor, brecha FROM analitica.vw_prioridades WHERE "
                        + filtroPrioridad + " AND prioridad IS NOT NULL ORDER BY puesto LIMIT 1",
                esGrupo ? List.of(centro, grupo) : List.of(centro),
                rs -> new MotorAlertas.PrioridadPrincipal(rs.getString("factor_nombre"), rs.getString("nivel_peor"),
                        decimal(rs, "pct_peor"), decimal(rs, "brecha"))).stream().findFirst().orElse(null);

        MotorAlertas.Resultado r = MotorAlertas.calcular(new MotorAlertas.Entrada(ambito, serie,
                grupos, nombreCentro, referencia, prioridad));

        MotorAlertas.Punto actual = serie.isEmpty() ? null : serie.get(serie.size() - 1);
        MotorAlertas.Punto anterior = serie.size() < 2 ? null : serie.get(serie.size() - 2);
        String ultima = serie.stream().map(MotorAlertas.Punto::actualizado).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);

        return new Evolucion(ambito, esGrupo ? "grupo" : "centro", serie, serieCentro, esGrupo ? Map.of() : grupos,
                actual == null ? null : actual.periodo(), anterior == null ? null : anterior.periodo(),
                comparaciones(actual, anterior), r.datosSuficientes(), r.mensajeDatos(), r.alertas(), r.orientacion(), ultima, AVISO);
    }

    static List<Comparacion> comparaciones(MotorAlertas.Punto a, MotorAlertas.Punto b) {
        if (a == null) {
            return List.of();
        }
        List<Comparacion> c = new ArrayList<>();
        c.add(comparacion("n_alumnos", "Registros", "", b == null ? null : (double) b.nAlumnos(), (double) a.nAlumnos(), true));
        c.add(comparacion("nota_media", "Nota media", "pts", b == null ? null : b.notaMedia(), a.notaMedia(), true));
        c.add(comparacion("asistencia_media", "Asistencia media", "%", b == null ? null : b.asistenciaMedia(),
                a.asistenciaMedia(), true));
        c.add(comparacion("pct_asistencia_baja", "Asisten menos del 80 %", "%", b == null ? null : b.pctAsistenciaBaja(),
                a.pctAsistenciaBaja(), false));
        c.add(comparacion("horas_estudio_media", "Horas de estudio (semana)", "h", b == null ? null : b.horasEstudioMedia(),
                a.horasEstudioMedia(), true));
        c.add(comparacion("tutorias_media", "Tutorías al mes", "", b == null ? null : b.tutoriasMedia(),
                a.tutoriasMedia(), true));
        return c;
    }

    private static Comparacion comparacion(String codigo, String nombre, String unidad, Double antes, Double ahora,
                                           boolean mejorSiSube) {
        Double var = antes == null || ahora == null ? null : Math.round((ahora - antes) * 100) / 100.0;
        return new Comparacion(codigo, nombre, unidad, antes, ahora, var, mejorSiSube);
    }

    private List<MotorAlertas.Punto> puntos(String filtro, List<Object> params) {
        return consultas.lista("SELECT * FROM analitica.vw_evolucion WHERE " + filtro + " ORDER BY periodo_orden",
                params, EvolucionResource::punto);
    }

    private String nombreAmbito(int centro, Integer grupo) {
        List<String> n = grupo == null
                ? consultas.lista("SELECT nombre FROM analitica.centro WHERE id = ?", List.of(centro), rs -> rs.getString(1))
                : consultas.lista("""
                        SELECT c.nombre || ' · ' || g.nombre FROM analitica.grupo g
                        JOIN analitica.centro c ON c.id = g.centro_id WHERE g.id = ? AND c.id = ?""",
                List.of(grupo, centro), rs -> rs.getString(1));
        if (n.isEmpty()) {
            throw error(Response.Status.NOT_FOUND, "Ese centro o grupo no existe.");
        }
        return n.get(0);
    }

    static MotorAlertas.Punto punto(ResultSet rs) throws SQLException {
        return new MotorAlertas.Punto(rs.getInt("periodo_id"), rs.getString("periodo"), rs.getInt("periodo_orden"),
                rs.getBoolean("periodo_sintetico"), rs.getInt("n_alumnos"), rs.getInt("n_grupos"),
                decimal(rs, "nota_media"), decimal(rs, "nota_sd"), entero(rs, "n_asistencia"),
                decimal(rs, "asistencia_media"), decimal(rs, "asistencia_sd"), decimal(rs, "pct_asistencia_baja"),
                decimal(rs, "horas_estudio_media"), decimal(rs, "tutorias_media"), fecha(rs, "actualizado"));
    }
}
