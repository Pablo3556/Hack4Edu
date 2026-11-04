package es.hack4edu.datos.infraestructura.persistencia;

import es.hack4edu.compartido.dominio.Ambito;
import es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas;
import es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.FiltroSql;
import es.hack4edu.datos.aplicacion.puertos.RepositorioAgregados;
import es.hack4edu.datos.dominio.Agregados;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.decimal;
import static es.hack4edu.compartido.infraestructura.persistencia.JdbcConsultas.entero;

/** Adaptador de salida: lee las vistas agregadas de "analitica" con el rol app_lector. */
@ApplicationScoped
public class AgregadosJdbc implements RepositorioAgregados {

    @Inject
    JdbcConsultas consultas;

    @Override
    public List<Agregados.Centro> ambitos() {
        record FilaAmbito(int centroId, String centro, String tipo, int grupoId, String grupo) {
        }
        List<FilaAmbito> filas = consultas.lista("""
                SELECT c.id AS centro_id, c.nombre AS centro, c.tipo_centro, g.id AS grupo_id, g.nombre AS grupo
                FROM analitica.centro c
                JOIN analitica.grupo g ON g.centro_id = c.id
                ORDER BY c.id, g.id""", List.of(),
                rs -> new FilaAmbito(rs.getInt("centro_id"), rs.getString("centro"), rs.getString("tipo_centro"),
                        rs.getInt("grupo_id"), rs.getString("grupo")));
        Map<Integer, Agregados.Centro> centros = new LinkedHashMap<>();
        for (FilaAmbito f : filas) {
            centros.computeIfAbsent(f.centroId(), id -> new Agregados.Centro(id, f.centro(), f.tipo(), new ArrayList<>()))
                    .grupos().add(new Agregados.Grupo(f.grupoId(), f.grupo()));
        }
        return List.copyOf(centros.values());
    }

    @Override
    public Optional<Agregados.Resumen> resumen(Ambito ambito) {
        FiltroSql f = FiltroSql.de(ambito);
        return consultas.lista("SELECT * FROM analitica.vw_resumen WHERE " + f.sql(), f.params(),
                AgregadosJdbc::resumen).stream().findFirst();
    }

    @Override
    public List<Agregados.Resumen> resumenDeCentros() {
        return consultas.lista("SELECT * FROM analitica.vw_resumen WHERE ambito = 'centro' ORDER BY centro_id",
                List.of(), AgregadosJdbc::resumen);
    }

    @Override
    public List<Agregados.Resumen> resumenDelCentroYSusGrupos(int centro) {
        return consultas.lista("SELECT * FROM analitica.vw_resumen WHERE ambito IN ('centro', 'grupo') "
                + "AND centro_id = ? ORDER BY ambito DESC, grupo_id", List.of(centro), AgregadosJdbc::resumen);
    }

    @Override
    public List<Agregados.Prioridad> prioridades(Ambito ambito) {
        FiltroSql f = FiltroSql.de(ambito);
        return consultas.lista("SELECT * FROM analitica.vw_prioridades WHERE " + f.sql() + " ORDER BY puesto, factor",
                f.params(), rs -> new Agregados.Prioridad(entero(rs, "puesto"), rs.getString("factor"),
                        rs.getString("factor_nombre"), rs.getString("dimension_reto"), rs.getString("nivel_peor"),
                        decimal(rs, "pct_peor"), decimal(rs, "nota_peor"), rs.getString("nivel_mejor"),
                        decimal(rs, "nota_mejor"), decimal(rs, "brecha"), decimal(rs, "brecha_estandarizada"),
                        rs.getString("magnitud"), decimal(rs, "accionabilidad"), decimal(rs, "prioridad"),
                        rs.getString("fiabilidad"), rs.getString("palanca"), rs.getString("aviso")));
    }

    @Override
    public List<Agregados.Brecha> brechas(Ambito ambito) {
        FiltroSql f = FiltroSql.de(ambito);
        return consultas.lista("SELECT * FROM analitica.vw_factor_brecha WHERE " + f.sql()
                        + " ORDER BY tipo, brecha_estandarizada DESC NULLS LAST", f.params(),
                rs -> new Agregados.Brecha(rs.getString("factor"), rs.getString("factor_nombre"), rs.getString("tipo"),
                        rs.getString("dimension_reto"), rs.getString("nivel_peor"), rs.getString("nivel_mejor"),
                        decimal(rs, "pct_peor"), decimal(rs, "brecha"), decimal(rs, "brecha_estandarizada"),
                        rs.getString("fiabilidad")));
    }

    @Override
    public List<Agregados.Factor> factores() {
        return consultas.lista("""
                SELECT codigo, nombre, tipo, dimension_reto, descripcion
                FROM analitica.factor
                ORDER BY tipo, accionabilidad DESC NULLS LAST, nombre""", List.of(),
                rs -> new Agregados.Factor(rs.getString("codigo"), rs.getString("nombre"), rs.getString("tipo"),
                        rs.getString("dimension_reto"), rs.getString("descripcion")));
    }

    @Override
    public List<Agregados.NivelFactor> nivelesDeFactor(String codigo, Ambito ambito) {
        FiltroSql f = FiltroSql.de(ambito);
        List<Object> params = new ArrayList<>();
        params.add(codigo);
        params.addAll(f.params());
        return consultas.lista("SELECT * FROM analitica.vw_factor_nivel WHERE factor = ? AND " + f.sql() + " ORDER BY orden",
                params, rs -> new Agregados.NivelFactor(entero(rs, "orden"), rs.getString("nivel"), entero(rs, "n"),
                        decimal(rs, "pct_ambito"), decimal(rs, "nota_media"), decimal(rs, "dif_vs_media_ambito"),
                        decimal(rs, "asistencia_media"), decimal(rs, "pct_sin_dato")));
    }

    @Override
    public List<Agregados.Calidad> calidad() {
        return consultas.lista("""
                SELECT columna, n_ausentes, pct_ausentes, n_total
                FROM analitica.vw_calidad_datos
                ORDER BY n_ausentes DESC, columna""", List.of(),
                rs -> new Agregados.Calidad(rs.getString("columna"), entero(rs, "n_ausentes"),
                        decimal(rs, "pct_ausentes"), entero(rs, "n_total")));
    }

    @Override
    public Optional<String> nombreAmbito(Ambito ambito) {
        if (ambito.centro() == null) {
            return Optional.empty();
        }
        return (ambito.grupo() == null
                ? consultas.lista("SELECT nombre FROM analitica.centro WHERE id = ?", List.of(ambito.centro()),
                rs -> rs.getString(1))
                : consultas.lista("""
                        SELECT c.nombre || ' · ' || g.nombre FROM analitica.grupo g
                        JOIN analitica.centro c ON c.id = g.centro_id WHERE g.id = ? AND c.id = ?""",
                List.of(ambito.grupo(), ambito.centro()), rs -> rs.getString(1))).stream().findFirst();
    }

    private static Agregados.Resumen resumen(ResultSet rs) throws SQLException {
        return new Agregados.Resumen(rs.getString("ambito"), entero(rs, "centro_id"), rs.getString("centro"),
                rs.getString("tipo_centro"), entero(rs, "grupo_id"), rs.getString("grupo"), entero(rs, "n_alumnos"),
                decimal(rs, "nota_media"), decimal(rs, "nota_p25"), decimal(rs, "nota_mediana"),
                decimal(rs, "nota_p75"), decimal(rs, "dif_vs_global"), decimal(rs, "asistencia_media"),
                decimal(rs, "pct_asistencia_baja"), decimal(rs, "horas_estudio_media"), decimal(rs, "tutorias_media"),
                decimal(rs, "pct_recursos_bajos"), decimal(rs, "pct_calidad_docente_baja"),
                decimal(rs, "pct_extraescolares"), decimal(rs, "pct_implicacion_familiar_baja"));
    }
}
