package es.hack4edu.evolucion;

import es.hack4edu.datos.Consultas;
import es.hack4edu.datos.DatosResource;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;

import static es.hack4edu.datos.Consultas.decimal;
import static es.hack4edu.datos.Consultas.entero;
import static es.hack4edu.datos.Consultas.error;

/**
 * Simulador "¿Qué pasaría si…?" sobre los factores ACCIONABLES (los contextuales, como la renta
 * o el género, no se simulan: no son palancas del centro). Reutiliza vw_factor_nivel,
 * vw_factor_brecha (brecha y fiabilidad del ámbito) y la accionabilidad del catálogo.
 */
@Path("/api/simulador")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class SimuladorResource {

    public record Nivel(int orden, String etiqueta) {
    }

    public record FactorSimulable(String codigo, String nombre, Double accionabilidad, String palanca,
                                  List<Nivel> niveles, boolean disponibleEnAmbito, String brechaAmbito) {
    }

    @Inject
    Consultas consultas;

    /** Factores accionables con sus niveles y si se pueden simular en el ámbito elegido. */
    @GET
    @Path("/factores")
    public List<FactorSimulable> factores(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo) {
        DatosResource.Filtro f = filtro(centro, grupo);
        record Fila(String codigo, String nombre, Double accionabilidad, String palanca, int orden, String etiqueta,
                    boolean disponible, String brecha) {
        }
        List<Object> params = new ArrayList<>(f.params());
        params.addAll(f.params());
        List<Fila> filas = consultas.lista("""
                SELECT f.codigo, f.nombre, f.accionabilidad, f.palanca, fn.orden, fn.etiqueta,
                       EXISTS (SELECT 1 FROM analitica.vw_factor_nivel v
                               WHERE v.factor = f.codigo AND %1$s) AS disponible,
                       (SELECT format('«%%s» frente a «%%s»: %%s puntos (fiabilidad %%s)',
                                      v.nivel_mejor, v.nivel_peor, v.brecha, v.fiabilidad)
                        FROM analitica.vw_factor_brecha v WHERE v.factor = f.codigo AND %1$s) AS brecha
                FROM analitica.factor f
                JOIN analitica.factor_nivel fn ON fn.factor = f.codigo
                WHERE f.tipo = 'accionable'
                ORDER BY f.accionabilidad DESC, f.nombre, fn.orden""".formatted(prefijar(f.sql())), params,
                rs -> new Fila(rs.getString("codigo"), rs.getString("nombre"), decimal(rs, "accionabilidad"),
                        rs.getString("palanca"), rs.getInt("orden"), rs.getString("etiqueta"),
                        rs.getBoolean("disponible"), rs.getString("brecha")));
        List<FactorSimulable> res = new ArrayList<>();
        for (Fila fila : filas) {
            FactorSimulable ultimo = res.isEmpty() ? null : res.get(res.size() - 1);
            if (ultimo == null || !ultimo.codigo().equals(fila.codigo())) {
                ultimo = new FactorSimulable(fila.codigo(), fila.nombre(), fila.accionabilidad(), fila.palanca(),
                        new ArrayList<>(), fila.disponible(), fila.brecha());
                res.add(ultimo);
            }
            ultimo.niveles().add(new Nivel(fila.orden(), fila.etiqueta()));
        }
        return res;
    }

    /** Ej.: /api/simulador?centro=3&grupo=10&factor=tutorias&desde=2&hacia=3&porcentaje=100 */
    @GET
    public Simulador.Resultado simular(@QueryParam("centro") Integer centro, @QueryParam("grupo") Integer grupo,
                                       @QueryParam("factor") String factor, @QueryParam("desde") Integer desde,
                                       @QueryParam("hacia") Integer hacia, @QueryParam("porcentaje") Integer porcentaje) {
        if (factor == null || desde == null || hacia == null) {
            throw error(Response.Status.BAD_REQUEST, "Indica el factor, el nivel de partida y el de llegada.");
        }
        int pct = porcentaje == null ? 100 : porcentaje;
        if (pct < 0 || pct > 100) {
            throw error(Response.Status.BAD_REQUEST, "El porcentaje debe estar entre 0 y 100.");
        }
        DatosResource.Filtro f = filtro(centro, grupo);

        record Info(String nombre, Double accionabilidad, String palanca) {
        }
        Info info = consultas.lista("SELECT nombre, accionabilidad, palanca FROM analitica.factor "
                        + "WHERE codigo = ? AND tipo = 'accionable'", List.of(factor),
                rs -> new Info(rs.getString("nombre"), decimal(rs, "accionabilidad"), rs.getString("palanca")))
                .stream().findFirst().orElseThrow(() -> error(Response.Status.BAD_REQUEST,
                        "Solo se pueden simular factores accionables (los de contexto no son palancas del centro)."));

        List<Object> params = new ArrayList<>(List.of(factor));
        params.addAll(f.params());
        record NivelConSinDato(Simulador.NivelAmbito nivel, Double pctSinDato) {
        }
        List<NivelConSinDato> ambito = consultas.lista(
                "SELECT orden, nivel, n, pct_ambito, nota_media, pct_sin_dato FROM analitica.vw_factor_nivel "
                        + "WHERE factor = ? AND " + f.sql() + " ORDER BY orden", params,
                rs -> new NivelConSinDato(new Simulador.NivelAmbito(rs.getInt("orden"), rs.getString("nivel"),
                        rs.getInt("n"), decimal(rs, "pct_ambito"), decimal(rs, "nota_media")),
                        decimal(rs, "pct_sin_dato")));

        List<Simulador.NivelAsociacion> asociacion = consultas.lista("""
                SELECT s.orden, fn.etiqueta, s.n, s.nota_media, s.nota_sd
                FROM analitica.vw_factor_nivel_sd s
                JOIN analitica.factor_nivel fn ON fn.factor = s.factor AND fn.orden = s.orden
                WHERE s.ambito = 'global' AND s.factor = ?
                ORDER BY s.orden""", List.of(factor),
                rs -> new Simulador.NivelAsociacion(rs.getInt("orden"), rs.getString("etiqueta"), rs.getInt("n"),
                        decimal(rs, "nota_media"), decimal(rs, "nota_sd")));
        if (asociacion.isEmpty()) {
            throw error(Response.Status.NOT_FOUND, "No hay datos suficientes de este factor. ¿Están cargados los datos?");
        }

        Double notaMedia = consultas.lista("SELECT nota_media FROM analitica.vw_resumen WHERE " + f.sql(),
                f.params(), rs -> decimal(rs, "nota_media")).stream().findFirst().orElse(null);
        String nombreAmbito = nombreAmbito(centro, grupo);

        return Simulador.simular(new Simulador.Entrada(nombreAmbito, info.nombre(), info.accionabilidad(),
                info.palanca(), ambito.stream().map(NivelConSinDato::nivel).toList(),
                ambito.isEmpty() ? null : ambito.get(0).pctSinDato(), asociacion, notaMedia, desde, hacia, pct));
    }

    private static DatosResource.Filtro filtro(Integer centro, Integer grupo) {
        return DatosResource.filtro(centro, grupo);
    }

    /** "ambito = 'grupo' AND centro_id = ?" → "v.ambito = … AND v.centro_id = ?" para las subconsultas. */
    private static String prefijar(String sql) {
        return sql.replace("ambito", "v.ambito").replace("centro_id", "v.centro_id").replace("grupo_id", "v.grupo_id");
    }

    private String nombreAmbito(Integer centro, Integer grupo) {
        if (centro == null) {
            return "el conjunto de centros";
        }
        String sql = grupo == null ? "SELECT nombre FROM analitica.centro WHERE id = ?"
                : "SELECT c.nombre || ' · ' || g.nombre FROM analitica.grupo g JOIN analitica.centro c "
                + "ON c.id = g.centro_id WHERE c.id = ? AND g.id = ?";
        return consultas.lista(sql, grupo == null ? List.of(centro) : List.of(centro, grupo), rs -> rs.getString(1))
                .stream().findFirst().orElseThrow(() -> error(Response.Status.NOT_FOUND, "Ese centro o grupo no existe."));
    }
}
