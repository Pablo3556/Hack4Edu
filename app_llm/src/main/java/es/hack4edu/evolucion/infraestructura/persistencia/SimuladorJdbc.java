package es.hack4edu.evolucion.infraestructura.persistencia;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas;
import es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.FiltroSql;
import es.hack4edu.evolucion.aplicacion.puertos.RepositorioSimulador;
import es.hack4edu.evolucion.dominio.CatalogoSimulador;
import es.hack4edu.evolucion.dominio.Simulador;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.decimal;

/** Adaptador de salida: vistas agregadas que alimentan el simulador (rol app_lector). */
@ApplicationScoped
public class SimuladorJdbc implements RepositorioSimulador {

    @Inject
    JdbcConsultas consultas;

    @Override
    public List<CatalogoSimulador.FactorSimulable> factoresSimulables(Ambito ambito) {
        FiltroSql f = FiltroSql.de(ambito);
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
        List<CatalogoSimulador.FactorSimulable> res = new ArrayList<>();
        for (Fila fila : filas) {
            CatalogoSimulador.FactorSimulable ultimo = res.isEmpty() ? null : res.get(res.size() - 1);
            if (ultimo == null || !ultimo.codigo().equals(fila.codigo())) {
                ultimo = new CatalogoSimulador.FactorSimulable(fila.codigo(), fila.nombre(), fila.accionabilidad(),
                        fila.palanca(), new ArrayList<>(), fila.disponible(), fila.brecha());
                res.add(ultimo);
            }
            ultimo.niveles().add(new CatalogoSimulador.Nivel(fila.orden(), fila.etiqueta()));
        }
        return res;
    }

    @Override
    public Optional<CatalogoSimulador.InfoFactor> factorAccionable(String codigo) {
        return consultas.lista("SELECT nombre, accionabilidad, palanca FROM analitica.factor "
                        + "WHERE codigo = ? AND tipo = 'accionable'", List.of(codigo),
                rs -> new CatalogoSimulador.InfoFactor(rs.getString("nombre"), decimal(rs, "accionabilidad"),
                        rs.getString("palanca"))).stream().findFirst();
    }

    @Override
    public List<CatalogoSimulador.NivelConSinDato> nivelesDelAmbito(String factor, Ambito ambito) {
        FiltroSql f = FiltroSql.de(ambito);
        List<Object> params = new ArrayList<>(List.of(factor));
        params.addAll(f.params());
        return consultas.lista(
                "SELECT orden, nivel, n, pct_ambito, nota_media, pct_sin_dato FROM analitica.vw_factor_nivel "
                        + "WHERE factor = ? AND " + f.sql() + " ORDER BY orden", params,
                rs -> new CatalogoSimulador.NivelConSinDato(
                        new Simulador.NivelAmbito(rs.getInt("orden"), rs.getString("nivel"), rs.getInt("n"),
                                decimal(rs, "pct_ambito"), decimal(rs, "nota_media")),
                        decimal(rs, "pct_sin_dato")));
    }

    @Override
    public List<Simulador.NivelAsociacion> asociacionGlobal(String factor) {
        return consultas.lista("""
                SELECT s.orden, fn.etiqueta, s.n, s.nota_media, s.nota_sd
                FROM analitica.vw_factor_nivel_sd s
                JOIN analitica.factor_nivel fn ON fn.factor = s.factor AND fn.orden = s.orden
                WHERE s.ambito = 'global' AND s.factor = ?
                ORDER BY s.orden""", List.of(factor),
                rs -> new Simulador.NivelAsociacion(rs.getInt("orden"), rs.getString("etiqueta"), rs.getInt("n"),
                        decimal(rs, "nota_media"), decimal(rs, "nota_sd")));
    }

    /** "ambito = 'grupo' AND centro_id = ?" → "v.ambito = … AND v.centro_id = ?" para las subconsultas. */
    private static String prefijar(String sql) {
        return sql.replace("ambito", "v.ambito").replace("centro_id", "v.centro_id").replace("grupo_id", "v.grupo_id");
    }
}
