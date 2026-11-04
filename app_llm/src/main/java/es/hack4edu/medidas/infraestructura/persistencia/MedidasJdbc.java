package es.hack4edu.medidas.infraestructura.persistencia;

import es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas;
import es.hack4edu.medidas.aplicacion.puertos.RepositorioMedidas;
import es.hack4edu.medidas.dominio.Indicador;
import es.hack4edu.medidas.dominio.Medidas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.decimal;
import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.entero;
import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.fecha;

/**
 * Adaptador de salida: guarda las medidas en ia.medida (V8) y lee la evolución de analitica.vw_evolucion.
 * Todo con el rol app_lector.
 */
@ApplicationScoped
public class MedidasJdbc implements RepositorioMedidas {

    private static final String SELECT_MEDIDA = """
            SELECT m.id, m.centro_id, c.nombre AS centro, m.grupo_id, g.nombre AS grupo, m.factor,
                   f.nombre AS factor_nombre, m.titulo, m.descripcion, m.indicador, m.periodo_inicio,
                   p.nombre AS periodo_nombre, p.orden AS periodo_orden, m.estado, m.creada_por, m.creada
            FROM ia.medida m
            JOIN analitica.centro c  ON c.id = m.centro_id
            JOIN analitica.periodo p ON p.id = m.periodo_inicio
            LEFT JOIN analitica.grupo g   ON g.id = m.grupo_id
            LEFT JOIN analitica.factor f  ON f.codigo = m.factor
            """;

    private static final String[] COLUMNAS = Arrays.stream(Indicador.values()).map(Indicador::codigo)
            .toArray(String[]::new);

    @Inject
    JdbcConsultas consultas;

    @Override
    public List<Medidas.Periodo> periodosReales() {
        return consultas.lista("SELECT id, codigo, nombre FROM analitica.periodo WHERE NOT sintetico ORDER BY orden",
                List.of(), rs -> new Medidas.Periodo(rs.getInt("id"), rs.getString("codigo"), rs.getString("nombre")));
    }

    @Override
    public boolean existeCentro(int centro) {
        return !consultas.lista("SELECT 1 FROM analitica.centro WHERE id = ?", List.of(centro), rs -> 1).isEmpty();
    }

    @Override
    public boolean grupoPerteneceAlCentro(int grupo, int centro) {
        return !consultas.lista("SELECT 1 FROM analitica.grupo WHERE id = ? AND centro_id = ?",
                List.of(grupo, centro), rs -> 1).isEmpty();
    }

    @Override
    public boolean existeFactor(String codigo) {
        return !consultas.lista("SELECT 1 FROM analitica.factor WHERE codigo = ?", List.of(codigo), rs -> 1).isEmpty();
    }

    @Override
    public int insertar(Medidas.NuevaMedida m, String creadaPor) {
        List<Object> params = new ArrayList<>();
        params.add(m.centro());
        params.add(m.grupo());
        params.add(m.factor());
        params.add(m.titulo());
        params.add(m.descripcion());
        params.add(m.indicador());
        params.add(m.periodoInicio());
        params.add(creadaPor);
        return consultas.lista("""
                INSERT INTO ia.medida (centro_id, grupo_id, factor, titulo, descripcion, indicador, periodo_inicio, creada_por)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", params, rs -> rs.getInt(1)).get(0);
    }

    @Override
    public boolean cambiarEstado(int id, String estado) {
        return !consultas.lista("UPDATE ia.medida SET estado = ?, actualizada = now() WHERE id = ? RETURNING id",
                List.of(estado, id), rs -> rs.getInt(1)).isEmpty();
    }

    @Override
    public List<Medidas.MedidaRegistrada> buscar(int centro, Integer grupo) {
        String where = grupo == null ? "WHERE m.centro_id = ?"
                : "WHERE m.centro_id = ? AND (m.grupo_id = ? OR m.grupo_id IS NULL)";
        List<Object> params = grupo == null ? List.of(centro) : List.of(centro, grupo);
        return consultas.lista(SELECT_MEDIDA + where + " ORDER BY (m.estado = 'en_curso') DESC, m.creada DESC",
                params, MedidasJdbc::registrada);
    }

    @Override
    public Optional<Medidas.MedidaRegistrada> porId(int id) {
        return consultas.lista(SELECT_MEDIDA + "WHERE m.id = ?", List.of(id), MedidasJdbc::registrada)
                .stream().findFirst();
    }

    @Override
    public Map<Integer, String> nombresDePeriodos() {
        Map<Integer, String> nombres = new HashMap<>();
        consultas.lista("SELECT orden, nombre FROM analitica.periodo", List.of(),
                rs -> nombres.put(rs.getInt("orden"), rs.getString("nombre")));
        return nombres;
    }

    @Override
    public List<Medidas.ValorPeriodo> evolucionDeLosGrupos(int centro) {
        return valores("ambito = 'grupo' AND centro_id = ?", List.of(centro));
    }

    @Override
    public List<Medidas.ValorPeriodo> evolucionDeLosCentros() {
        return valores("ambito = 'centro'", List.of());
    }

    @Override
    public Map<Integer, Integer> enCursoPorGrupo(int centro) {
        Map<Integer, Integer> medidas = new HashMap<>();
        consultas.lista("SELECT grupo_id, count(*) AS n FROM ia.medida WHERE centro_id = ? AND estado = 'en_curso' "
                        + "AND grupo_id IS NOT NULL GROUP BY grupo_id", List.of(centro),
                rs -> medidas.put(entero(rs, "grupo_id"), entero(rs, "n")));
        return medidas;
    }

    private List<Medidas.ValorPeriodo> valores(String filtro, List<Object> params) {
        return consultas.lista("SELECT centro_id, grupo_id, periodo_orden, " + String.join(", ", COLUMNAS)
                        + " FROM analitica.vw_evolucion WHERE " + filtro + " ORDER BY periodo_orden", params,
                rs -> {
                    Map<String, Double> v = new HashMap<>();
                    for (String col : COLUMNAS) {
                        v.put(col, decimal(rs, col));
                    }
                    return new Medidas.ValorPeriodo(entero(rs, "centro_id"), entero(rs, "grupo_id"),
                            rs.getInt("periodo_orden"), v);
                });
    }

    private static Medidas.MedidaRegistrada registrada(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Medidas.MedidaRegistrada(rs.getInt("id"), rs.getInt("centro_id"), rs.getString("centro"),
                entero(rs, "grupo_id"), rs.getString("grupo"), rs.getString("factor"), rs.getString("factor_nombre"),
                rs.getString("titulo"), rs.getString("descripcion"),
                Indicador.de(rs.getString("indicador")).orElseThrow(), rs.getInt("periodo_inicio"),
                rs.getString("periodo_nombre"), rs.getInt("periodo_orden"), rs.getString("estado"),
                rs.getString("creada_por"), fecha(rs, "creada"));
    }
}
