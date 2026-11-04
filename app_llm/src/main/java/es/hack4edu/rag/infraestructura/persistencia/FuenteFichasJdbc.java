package es.hack4edu.rag.infraestructura.persistencia;

import es.hack4edu.rag.aplicacion.puertos.FuenteFichas;
import es.hack4edu.rag.dominio.Ficha;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adaptador de salida: convierte las vistas agregadas de {@code analitica} en "fichas" de texto para el RAG.
 * <p>
 * Solo lee lo que puede leer {@code app_lector}: agregados con umbral mínimo de grupo.
 * Ninguna ficha contiene datos de un alumno concreto.
 * <p>
 * Metadatos de cada ficha (se usan para filtrar la búsqueda):
 * {@code tipo} (resumen | prioridades | equidad | factor | metodologia | calidad),
 * {@code ambito} (global | centro | grupo), {@code centro_id}, {@code grupo_id}.
 */
@ApplicationScoped
public class FuenteFichasJdbc implements FuenteFichas {

    private static final Locale ES = Locale.forLanguageTag("es-ES");

    @Inject
    DataSource dataSource;

    @Override
    public List<Ficha> generar() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            Map<Integer, String> centros = new HashMap<>();
            Map<Integer, String> tipoCentro = new HashMap<>();
            try (ResultSet rs = st.executeQuery("SELECT id, nombre, tipo_centro FROM analitica.centro")) {
                while (rs.next()) {
                    centros.put(rs.getInt("id"), rs.getString("nombre"));
                    tipoCentro.put(rs.getInt("id"), rs.getString("tipo_centro"));
                }
            }
            Map<Integer, String> grupos = new HashMap<>();
            try (ResultSet rs = st.executeQuery("SELECT id, nombre FROM analitica.grupo")) {
                while (rs.next()) {
                    grupos.put(rs.getInt("id"), rs.getString("nombre"));
                }
            }
            Nombres nombres = new Nombres(centros, tipoCentro, grupos);

            List<Ficha> fichas = new ArrayList<>();
            fichas.add(metodologia(st));
            fichas.addAll(resumenes(st, nombres));
            fichas.addAll(prioridades(st, nombres));
            fichas.addAll(equidad(st, nombres));
            fichas.addAll(detalleFactores(st));
            fichas.add(calidad(st));
            return fichas;
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron leer las vistas de analitica: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Fichas
    // ------------------------------------------------------------------

    private Ficha metodologia(Statement st) throws SQLException {
        StringBuilder sb = new StringBuilder("""
                Metodología y catálogo de factores (ámbito: global).
                Datos: dataset sintético "Student Performance Factors" de Kaggle (licencia CC0), unos 6.600 alumnos.
                Los centros y grupos son ficticios y se asignaron al azar: las diferencias entre centros o grupos \
                son ruido de muestreo y no deben interpretarse como diferencias reales.
                Privacidad: no se publica ninguna cifra calculada con menos de 10 alumnos; si un nivel de un factor \
                no llega a 10, se oculta el factor completo en ese ámbito. La dificultad de aprendizaje no se publica.
                Brecha de un factor: nota media del nivel con mejor resultado menos la del nivel con peor resultado. \
                Brecha estandarizada: brecha dividida por la desviación típica de la nota en el ámbito \
                (muy pequeña < 0,2; pequeña < 0,5; moderada < 0,8; grande >= 0,8).
                Prioridad = brecha × proporción de alumnado en el peor nivel × accionabilidad. Solo se calcula para \
                factores accionables. Fiabilidad: alta si los dos niveles comparados tienen 100 alumnos o más, \
                media si tienen 30 o más, baja en otro caso.
                Son asociaciones observadas, no relaciones causales; cualquier actuación debe revisarse con el equipo docente.
                Factores:
                """);
        try (ResultSet rs = st.executeQuery("""
                SELECT codigo, nombre, dimension_reto, tipo, accionabilidad, palanca, descripcion
                FROM analitica.factor ORDER BY tipo, accionabilidad DESC NULLS LAST, nombre""")) {
            while (rs.next()) {
                sb.append("- ").append(rs.getString("nombre"))
                        .append(" (").append(rs.getString("tipo"))
                        .append(", dimensión ").append(rs.getString("dimension_reto"));
                BigDecimal acc = rs.getBigDecimal("accionabilidad");
                if (acc != null) {
                    sb.append(", accionabilidad ").append(num(acc, 2));
                }
                sb.append("): ").append(rs.getString("descripcion"));
                String palanca = rs.getString("palanca");
                if (palanca != null) {
                    sb.append(" Línea de actuación a explorar: ").append(palanca).append('.');
                }
                sb.append('\n');
            }
        }
        sb.append("Los factores contextuales (renta, estudios de la familia, distancia, titularidad, género) ")
                .append("sirven para vigilar la equidad y nunca reciben prioridad de intervención.");
        return ficha(sb.toString(), "metodologia", "global", null, null);
    }

    private List<Ficha> resumenes(Statement st, Nombres nombres) throws SQLException {
        List<Ficha> out = new ArrayList<>();
        try (ResultSet rs = st.executeQuery("""
                SELECT * FROM analitica.vw_resumen
                ORDER BY centro_id NULLS FIRST, grupo_id NULLS FIRST""")) {
            while (rs.next()) {
                Ambito a = Ambito.de(rs);
                String texto = "Resumen de indicadores. Ámbito: " + nombres.etiqueta(a) + ".\n"
                        + "Alumnos: " + rs.getInt("n_alumnos") + ".\n"
                        + "Nota media del examen: " + num(rs.getBigDecimal("nota_media"), 2)
                        + (a.tipo().equals("global") ? "" :
                        " (diferencia con la media global: " + signo(rs.getBigDecimal("dif_vs_global")) + " puntos)")
                        + "; mediana " + num(rs.getBigDecimal("nota_mediana"), 1)
                        + "; percentil 25 " + num(rs.getBigDecimal("nota_p25"), 1)
                        + "; percentil 75 " + num(rs.getBigDecimal("nota_p75"), 1) + ".\n"
                        + "Asistencia media: " + num(rs.getBigDecimal("asistencia_media"), 1) + " %; "
                        + "alumnado con asistencia inferior al 80 %: " + num(rs.getBigDecimal("pct_asistencia_baja"), 1) + " %.\n"
                        + "Horas de estudio semanales medias: " + num(rs.getBigDecimal("horas_estudio_media"), 1)
                        + "; sesiones de tutoría al mes medias: " + num(rs.getBigDecimal("tutorias_media"), 2) + ".\n"
                        + "Alumnado con acceso bajo a recursos: " + num(rs.getBigDecimal("pct_recursos_bajos"), 1) + " %; "
                        + "con calidad docente percibida baja: " + num(rs.getBigDecimal("pct_calidad_docente_baja"), 1) + " %; "
                        + "con actividades extraescolares: " + num(rs.getBigDecimal("pct_extraescolares"), 1) + " %; "
                        + "con implicación familiar baja: " + num(rs.getBigDecimal("pct_implicacion_familiar_baja"), 1) + " %.";
                out.add(ficha(texto, "resumen", a));
            }
        }
        return out;
    }

    private List<Ficha> prioridades(Statement st, Nombres nombres) throws SQLException {
        Map<Ambito, StringBuilder> porAmbito = new LinkedHashMap<>();
        try (ResultSet rs = st.executeQuery("""
                SELECT * FROM analitica.vw_prioridades
                ORDER BY centro_id NULLS FIRST, grupo_id NULLS FIRST, puesto""")) {
            while (rs.next()) {
                Ambito a = Ambito.de(rs);
                StringBuilder sb = porAmbito.computeIfAbsent(a, k -> new StringBuilder(
                        "Prioridades de intervención (factores accionables, de mayor a menor prioridad). Ámbito: "
                                + nombres.etiqueta(k) + ".\n"));
                sb.append(rs.getInt("puesto")).append(". ").append(rs.getString("factor_nombre"))
                        .append(" (dimensión ").append(rs.getString("dimension_reto")).append("): ")
                        .append("el nivel con peor nota es '").append(rs.getString("nivel_peor")).append("' (")
                        .append(num(rs.getBigDecimal("pct_peor"), 1)).append(" % del alumnado, nota media ")
                        .append(num(rs.getBigDecimal("nota_peor"), 2)).append(") frente a '")
                        .append(rs.getString("nivel_mejor")).append("' (nota media ")
                        .append(num(rs.getBigDecimal("nota_mejor"), 2)).append("). Brecha ")
                        .append(num(rs.getBigDecimal("brecha"), 2)).append(" puntos (")
                        .append(num(rs.getBigDecimal("brecha_estandarizada"), 2)).append(" desviaciones típicas, ")
                        .append(rs.getString("magnitud")).append("). Prioridad ")
                        .append(num(rs.getBigDecimal("prioridad"), 3)).append(", fiabilidad ")
                        .append(rs.getString("fiabilidad")).append(". Línea de actuación a explorar: ")
                        .append(rs.getString("palanca")).append(".\n");
            }
        }
        List<Ficha> out = new ArrayList<>();
        porAmbito.forEach((a, sb) -> {
            sb.append("Aviso: asociación observada en datos sintéticos; no implica causalidad. Revisar con el equipo docente.");
            out.add(ficha(sb.toString(), "prioridades", a));
        });
        return out;
    }

    private List<Ficha> equidad(Statement st, Nombres nombres) throws SQLException {
        Map<Ambito, StringBuilder> porAmbito = new LinkedHashMap<>();
        try (ResultSet rs = st.executeQuery("""
                SELECT * FROM analitica.vw_factor_brecha
                WHERE tipo = 'contextual'
                ORDER BY centro_id NULLS FIRST, grupo_id NULLS FIRST, brecha DESC""")) {
            while (rs.next()) {
                Ambito a = Ambito.de(rs);
                StringBuilder sb = porAmbito.computeIfAbsent(a, k -> new StringBuilder(
                        "Brechas de equidad (factores contextuales: sirven para vigilar la equidad, no para priorizar). Ámbito: "
                                + nombres.etiqueta(k) + ".\n"));
                sb.append("- ").append(rs.getString("factor_nombre")).append(": '")
                        .append(rs.getString("nivel_peor")).append("' (")
                        .append(num(rs.getBigDecimal("pct_peor"), 1)).append(" % del alumnado, nota media ")
                        .append(num(rs.getBigDecimal("nota_peor"), 2)).append(") frente a '")
                        .append(rs.getString("nivel_mejor")).append("' (nota media ")
                        .append(num(rs.getBigDecimal("nota_mejor"), 2)).append("). Brecha ")
                        .append(num(rs.getBigDecimal("brecha"), 2)).append(" puntos (")
                        .append(num(rs.getBigDecimal("brecha_estandarizada"), 2))
                        .append(" desviaciones típicas), fiabilidad ").append(rs.getString("fiabilidad")).append(".\n");
            }
        }
        List<Ficha> out = new ArrayList<>();
        porAmbito.forEach((a, sb) -> out.add(ficha(sb.toString().strip(), "equidad", a)));
        return out;
    }

    /** Una ficha por factor con la nota media de cada nivel (solo ámbito global). */
    private List<Ficha> detalleFactores(Statement st) throws SQLException {
        Map<String, StringBuilder> porFactor = new LinkedHashMap<>();
        try (ResultSet rs = st.executeQuery("""
                SELECT * FROM analitica.vw_factor_nivel
                WHERE ambito = 'global'
                ORDER BY tipo, factor, orden""")) {
            while (rs.next()) {
                String factor = rs.getString("factor");
                StringBuilder sb = porFactor.get(factor);
                if (sb == null) {
                    sb = new StringBuilder("Detalle del factor ").append(rs.getString("factor_nombre"))
                            .append(" (").append(rs.getString("tipo")).append(", dimensión ")
                            .append(rs.getString("dimension_reto")).append("). Ámbito: global (todos los centros). ")
                            .append("Alumnado sin dato: ").append(num(rs.getBigDecimal("pct_sin_dato"), 1)).append(" %.\n");
                    porFactor.put(factor, sb);
                }
                sb.append("- Nivel '").append(rs.getString("nivel")).append("': ")
                        .append(rs.getInt("n")).append(" alumnos (")
                        .append(num(rs.getBigDecimal("pct_ambito"), 1)).append(" %), nota media ")
                        .append(num(rs.getBigDecimal("nota_media"), 2)).append(" (")
                        .append(signo(rs.getBigDecimal("dif_vs_media_ambito"))).append(" frente a la media), asistencia media ")
                        .append(num(rs.getBigDecimal("asistencia_media"), 1)).append(" %.\n");
            }
        }
        List<Ficha> out = new ArrayList<>();
        porFactor.forEach((f, sb) -> {
            Ficha t = ficha(sb.toString().strip(), "factor", "global", null, null);
            t.metadatos().put("factor", f);
            out.add(t);
        });
        return out;
    }

    private Ficha calidad(Statement st) throws SQLException {
        StringBuilder sb = new StringBuilder("Calidad de los datos (ámbito: global).\n");
        boolean cabecera = false;
        try (ResultSet rs = st.executeQuery("SELECT * FROM analitica.vw_calidad_datos ORDER BY n_ausentes DESC, columna")) {
            while (rs.next()) {
                if (!cabecera) {
                    sb.append("Total de alumnos cargados: ").append(rs.getInt("n_total"))
                            .append(". Última carga: ").append(rs.getString("ultima_carga"))
                            .append(". Notas de examen superiores a 100 recortadas a 100: ")
                            .append(rs.getString("notas_recortadas")).append(".\nValores ausentes por columna:\n");
                    cabecera = true;
                }
                if (rs.getInt("n_ausentes") > 0) {
                    sb.append("- ").append(rs.getString("columna")).append(": ").append(rs.getInt("n_ausentes"))
                            .append(" (").append(num(rs.getBigDecimal("pct_ausentes"), 2)).append(" %).\n");
                }
            }
        }
        sb.append("El resto de columnas no tiene valores ausentes.");
        return ficha(sb.toString(), "calidad", "global", null, null);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    record Ambito(String tipo, Integer centroId, Integer grupoId) {
        static Ambito de(ResultSet rs) throws SQLException {
            return new Ambito(rs.getString("ambito"),
                    (Integer) rs.getObject("centro_id", Integer.class),
                    (Integer) rs.getObject("grupo_id", Integer.class));
        }
    }

    record Nombres(Map<Integer, String> centros, Map<Integer, String> tipoCentro, Map<Integer, String> grupos) {
        String etiqueta(Ambito a) {
            return switch (a.tipo()) {
                case "global" -> "global (todos los centros)";
                case "centro" -> centro(a.centroId());
                default -> grupos.get(a.grupoId()) + " del " + centro(a.centroId());
            };
        }

        private String centro(Integer id) {
            String tipo = "publico".equals(tipoCentro.get(id)) ? "público" : "privado";
            return centros.get(id) + " (centro " + tipo + ", centro_id " + id + ")";
        }
    }

    private static Ficha ficha(String texto, String tipo, Ambito a) {
        return ficha(texto, tipo, a.tipo(), a.centroId(), a.grupoId());
    }

    static Ficha ficha(String texto, String tipo, String ambito, Integer centroId, Integer grupoId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo);
        m.put("ambito", ambito);
        if (centroId != null) {
            m.put("centro_id", centroId);
        }
        if (grupoId != null) {
            m.put("grupo_id", grupoId);
        }
        return new Ficha(texto, m);
    }

    static String num(BigDecimal v, int decimales) {
        return v == null ? "sin dato" : String.format(ES, "%." + decimales + "f", v);
    }

    static String signo(BigDecimal v) {
        return v == null ? "sin dato" : String.format(ES, "%+.2f", v);
    }
}
