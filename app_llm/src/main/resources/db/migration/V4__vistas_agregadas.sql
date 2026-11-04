-- =====================================================================
-- V4 · Vistas agregadas
--   Regla de privacidad: ninguna cifra se publica si la calcula un grupo
--   de menos de analitica.k_min() alumnos. En las vistas por factor, si un
--   solo nivel no llega al umbral se oculta el factor entero en ese ámbito
--   (evita deducir la celda pequeña restando las demás).
--
--   ambito = 'global' | 'centro' | 'grupo'
--   Las vistas NO usan security_invoker: se ejecutan con permisos de su
--   propietario, y así app_lector lee agregados sin poder tocar staging.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Resumen por ámbito
-- ---------------------------------------------------------------------
CREATE VIEW analitica.vw_resumen AS
WITH agg AS (
    SELECT
        CASE WHEN GROUPING(a.centro_id) = 1 THEN 'global'
             WHEN GROUPING(a.grupo_id)  = 1 THEN 'centro'
             ELSE 'grupo' END                                                   AS ambito,
        a.centro_id,
        a.grupo_id,
        count(*)::integer                                                       AS n_alumnos,
        avg(a.nota_examen)                                                      AS nota_media,
        percentile_cont(0.25) WITHIN GROUP (ORDER BY a.nota_examen)             AS nota_p25,
        percentile_cont(0.50) WITHIN GROUP (ORDER BY a.nota_examen)             AS nota_mediana,
        percentile_cont(0.75) WITHIN GROUP (ORDER BY a.nota_examen)             AS nota_p75,
        avg(a.asistencia)                                                       AS asistencia_media,
        avg((a.asistencia < 80)::int) * 100                                     AS pct_asistencia_baja,
        avg(a.horas_estudio)                                                    AS horas_estudio_media,
        avg(a.tutorias)                                                         AS tutorias_media,
        avg((a.acceso_recursos = 'bajo')::int) * 100                            AS pct_recursos_bajos,
        avg((a.calidad_docente = 'bajo')::int) * 100                            AS pct_calidad_docente_baja,
        avg(a.extraescolares::int) * 100                                        AS pct_extraescolares,
        avg((a.implicacion_familiar = 'bajo')::int) * 100                       AS pct_implicacion_familiar_baja
    FROM staging.alumno_spf a
    GROUP BY GROUPING SETS ((), (a.centro_id), (a.centro_id, a.grupo_id))
)
SELECT
    agg.ambito,
    agg.centro_id,
    c.nombre                                                                    AS centro,
    c.tipo_centro,
    agg.grupo_id,
    g.nombre                                                                    AS grupo,
    agg.n_alumnos,
    round(agg.nota_media, 2)                                                    AS nota_media,
    round(agg.nota_p25::numeric, 1)                                             AS nota_p25,
    round(agg.nota_mediana::numeric, 1)                                         AS nota_mediana,
    round(agg.nota_p75::numeric, 1)                                             AS nota_p75,
    round(agg.nota_media - max(agg.nota_media) FILTER (WHERE agg.ambito = 'global') OVER (), 2)
                                                                                AS dif_vs_global,
    round(agg.asistencia_media, 1)                                              AS asistencia_media,
    round(agg.pct_asistencia_baja, 1)                                           AS pct_asistencia_baja,
    round(agg.horas_estudio_media, 1)                                           AS horas_estudio_media,
    round(agg.tutorias_media, 2)                                                AS tutorias_media,
    round(agg.pct_recursos_bajos, 1)                                            AS pct_recursos_bajos,
    round(agg.pct_calidad_docente_baja, 1)                                      AS pct_calidad_docente_baja,
    round(agg.pct_extraescolares, 1)                                            AS pct_extraescolares,
    round(agg.pct_implicacion_familiar_baja, 1)                                 AS pct_implicacion_familiar_baja
FROM agg
LEFT JOIN analitica.centro c ON c.id = agg.centro_id
LEFT JOIN analitica.grupo  g ON g.id = agg.grupo_id
WHERE agg.n_alumnos >= analitica.k_min();

COMMENT ON VIEW analitica.vw_resumen IS
  'Indicadores básicos por ámbito (global, centro, grupo). pct_asistencia_baja = % con asistencia < 80 %.';

-- ---------------------------------------------------------------------
-- Interna: resultados por nivel de factor, ya filtrados por el umbral
-- ---------------------------------------------------------------------
CREATE VIEW staging.vw_factor_nivel_publicable AS
WITH por_nivel AS (
    SELECT
        CASE WHEN GROUPING(l.centro_id) = 1 THEN 'global'
             WHEN GROUPING(l.grupo_id)  = 1 THEN 'centro'
             ELSE 'grupo' END                       AS ambito,
        l.centro_id, l.grupo_id, l.factor, l.orden,
        count(*)::integer                           AS n,
        avg(l.nota_examen)                          AS nota_media,
        avg(l.asistencia)                           AS asistencia_media
    FROM staging.vw_spf_largo l
    GROUP BY GROUPING SETS ((l.factor, l.orden),
                            (l.centro_id, l.factor, l.orden),
                            (l.centro_id, l.grupo_id, l.factor, l.orden))
),
ambito AS (
    SELECT
        CASE WHEN GROUPING(a.centro_id) = 1 THEN 'global'
             WHEN GROUPING(a.grupo_id)  = 1 THEN 'centro'
             ELSE 'grupo' END                       AS ambito,
        a.centro_id, a.grupo_id,
        count(*)::integer                           AS n_total,
        avg(a.nota_examen)                          AS nota_media_ambito,
        stddev_samp(a.nota_examen)                  AS nota_sd_ambito
    FROM staging.alumno_spf a
    GROUP BY GROUPING SETS ((), (a.centro_id), (a.centro_id, a.grupo_id))
),
unido AS (
    SELECT p.*,
           a.n_total, a.nota_media_ambito, a.nota_sd_ambito,
           sum(p.n) OVER w::integer AS n_factor,
           min(p.n) OVER w          AS n_min_nivel
    FROM por_nivel p
    JOIN ambito a
      ON a.ambito = p.ambito
     AND a.centro_id IS NOT DISTINCT FROM p.centro_id
     AND a.grupo_id  IS NOT DISTINCT FROM p.grupo_id
    WINDOW w AS (PARTITION BY p.ambito, p.centro_id, p.grupo_id, p.factor)
)
SELECT * FROM unido
WHERE n_min_nivel >= analitica.k_min();

-- ---------------------------------------------------------------------
-- Resultado por nivel de cada factor
-- ---------------------------------------------------------------------
CREATE VIEW analitica.vw_factor_nivel AS
SELECT
    v.ambito, v.centro_id, v.grupo_id,
    v.factor, f.nombre                                       AS factor_nombre, f.tipo, f.dimension_reto,
    v.orden, fn.etiqueta                                     AS nivel,
    v.n,
    round(100.0 * v.n / v.n_factor, 1)                       AS pct_ambito,
    round(v.nota_media, 2)                                   AS nota_media,
    round(v.nota_media - v.nota_media_ambito, 2)             AS dif_vs_media_ambito,
    round(v.asistencia_media, 1)                             AS asistencia_media,
    round(100.0 * (v.n_total - v.n_factor) / v.n_total, 1)   AS pct_sin_dato
FROM staging.vw_factor_nivel_publicable v
JOIN analitica.factor f        ON f.codigo = v.factor
JOIN analitica.factor_nivel fn ON fn.factor = v.factor AND fn.orden = v.orden;

COMMENT ON VIEW analitica.vw_factor_nivel IS
  'Nota media por nivel de cada factor y ámbito. Base de las gráficas de barras.';

-- ---------------------------------------------------------------------
-- Brecha por factor: nivel con peor resultado frente al de mejor resultado
-- ---------------------------------------------------------------------
CREATE VIEW analitica.vw_factor_brecha AS
WITH r AS (
    SELECT v.*,
           row_number() OVER (PARTITION BY v.ambito, v.centro_id, v.grupo_id, v.factor
                              ORDER BY v.nota_media ASC, v.orden ASC)   AS rn_peor,
           row_number() OVER (PARTITION BY v.ambito, v.centro_id, v.grupo_id, v.factor
                              ORDER BY v.nota_media DESC, v.orden DESC) AS rn_mejor,
           count(*)     OVER (PARTITION BY v.ambito, v.centro_id, v.grupo_id, v.factor) AS n_niveles
    FROM staging.vw_factor_nivel_publicable v
)
SELECT
    p.ambito, p.centro_id, p.grupo_id,
    p.factor, f.nombre                                                  AS factor_nombre,
    f.tipo, f.dimension_reto,
    fp.etiqueta                                                         AS nivel_peor,
    p.n                                                                 AS n_peor,
    round(100.0 * p.n / p.n_factor, 1)                                  AS pct_peor,
    round(p.nota_media, 2)                                              AS nota_peor,
    fm.etiqueta                                                         AS nivel_mejor,
    m.n                                                                 AS n_mejor,
    round(m.nota_media, 2)                                              AS nota_mejor,
    round(m.nota_media - p.nota_media, 2)                               AS brecha,
    -- Brecha en desviaciones típicas del ámbito (aproximación a la d de Cohen)
    round((m.nota_media - p.nota_media) / NULLIF(p.nota_sd_ambito, 0), 2) AS brecha_estandarizada,
    -- Prioridad = brecha × proporción en el peor nivel × accionabilidad (solo factores accionables)
    CASE WHEN f.tipo = 'accionable'
         THEN round((m.nota_media - p.nota_media) * p.n / p.n_factor * f.accionabilidad, 3)
    END                                                                 AS prioridad,
    f.accionabilidad,
    CASE WHEN LEAST(p.n, m.n) >= 100 THEN 'alta'
         WHEN LEAST(p.n, m.n) >= 30  THEN 'media'
         ELSE 'baja' END                                                AS fiabilidad,
    f.palanca
FROM r p
JOIN r m
  ON m.ambito = p.ambito
 AND m.factor = p.factor
 AND m.centro_id IS NOT DISTINCT FROM p.centro_id
 AND m.grupo_id  IS NOT DISTINCT FROM p.grupo_id
 AND m.rn_mejor = 1
JOIN analitica.factor f        ON f.codigo = p.factor
JOIN analitica.factor_nivel fp ON fp.factor = p.factor AND fp.orden = p.orden
JOIN analitica.factor_nivel fm ON fm.factor = m.factor AND fm.orden = m.orden
WHERE p.rn_peor = 1
  AND p.n_niveles >= 2;

COMMENT ON VIEW analitica.vw_factor_brecha IS
  'Brecha de nota entre el nivel con peor y mejor resultado de cada factor. Asociación, no causalidad.';

-- ---------------------------------------------------------------------
-- Ranking de intervenciones (solo factores accionables)
-- ---------------------------------------------------------------------
CREATE VIEW analitica.vw_prioridades AS
SELECT
    rank() OVER (PARTITION BY b.ambito, b.centro_id, b.grupo_id ORDER BY b.prioridad DESC) AS puesto,
    b.ambito, b.centro_id, b.grupo_id,
    b.factor, b.factor_nombre, b.dimension_reto,
    b.nivel_peor, b.pct_peor, b.nota_peor,
    b.nivel_mejor, b.nota_mejor,
    b.brecha, b.brecha_estandarizada,
    CASE WHEN abs(b.brecha_estandarizada) < 0.2 THEN 'muy pequeña'
         WHEN abs(b.brecha_estandarizada) < 0.5 THEN 'pequeña'
         WHEN abs(b.brecha_estandarizada) < 0.8 THEN 'moderada'
         ELSE 'grande' END                                       AS magnitud,
    b.accionabilidad, b.prioridad, b.fiabilidad, b.palanca,
    'Asociación observada en datos sintéticos; no implica causalidad. Revisar con el equipo docente.'::text AS aviso
FROM analitica.vw_factor_brecha b
WHERE b.tipo = 'accionable';

COMMENT ON VIEW analitica.vw_prioridades IS
  'Intervenciones ordenadas por prioridad = brecha × % afectado × accionabilidad, con su desglose.';

-- ---------------------------------------------------------------------
-- Calidad de datos (solo recuentos)
-- ---------------------------------------------------------------------
CREATE VIEW analitica.vw_calidad_datos AS
WITH t AS (
    SELECT count(*)                         AS n,
           count(*) - count(horas_estudio)        AS horas_estudio,
           count(*) - count(asistencia)           AS asistencia,
           count(*) - count(implicacion_familiar) AS implicacion_familiar,
           count(*) - count(acceso_recursos)      AS acceso_recursos,
           count(*) - count(extraescolares)       AS extraescolares,
           count(*) - count(horas_sueno)          AS horas_sueno,
           count(*) - count(nota_previa)          AS nota_previa,
           count(*) - count(motivacion)           AS motivacion,
           count(*) - count(acceso_internet)      AS acceso_internet,
           count(*) - count(tutorias)             AS tutorias,
           count(*) - count(renta_familiar)       AS renta_familiar,
           count(*) - count(calidad_docente)      AS calidad_docente,
           count(*) - count(influencia_pares)     AS influencia_pares,
           count(*) - count(actividad_fisica)     AS actividad_fisica,
           count(*) - count(estudios_familia)     AS estudios_familia,
           count(*) - count(distancia_casa)       AS distancia_casa,
           count(*) - count(genero)               AS genero
    FROM staging.alumno_spf
)
SELECT c.columna,
       c.n_ausentes::integer,
       round(100.0 * c.n_ausentes / NULLIF(t.n, 0), 2) AS pct_ausentes,
       t.n::integer                                    AS n_total,
       ca.fecha                                        AS ultima_carga,
       ca.notas_recortadas
FROM t
CROSS JOIN LATERAL (VALUES
    ('horas_estudio', t.horas_estudio), ('asistencia', t.asistencia),
    ('implicacion_familiar', t.implicacion_familiar), ('acceso_recursos', t.acceso_recursos),
    ('extraescolares', t.extraescolares), ('horas_sueno', t.horas_sueno),
    ('nota_previa', t.nota_previa), ('motivacion', t.motivacion),
    ('acceso_internet', t.acceso_internet), ('tutorias', t.tutorias),
    ('renta_familiar', t.renta_familiar), ('calidad_docente', t.calidad_docente),
    ('influencia_pares', t.influencia_pares), ('actividad_fisica', t.actividad_fisica),
    ('estudios_familia', t.estudios_familia), ('distancia_casa', t.distancia_casa),
    ('genero', t.genero)
) AS c(columna, n_ausentes)
LEFT JOIN LATERAL (SELECT fecha, notas_recortadas FROM staging.carga ORDER BY id DESC LIMIT 1) ca ON true;

COMMENT ON VIEW analitica.vw_calidad_datos IS 'Valores ausentes por columna y datos de la última carga.';
