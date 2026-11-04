-- =====================================================================
-- V9 · Profesorado y progreso del alumnado de sus grupos
--
--   · staging.profesor            → docentes (sintéticos, solo un código; sin nombre)
--   · staging.asignacion_docente  → qué asignaturas da cada docente en qué grupo y quién es tutor/a
--   · staging.transformar_profesorado() → *_raw (CSV) → tablas tipadas, con validación estricta
--   · analitica.vw_progreso_grupo / vw_progreso_docente → lo que lee la app
--
-- Cómo se mide "si la clase rinde": PROGRESO FRENTE A LO ESPERADO (valor añadido).
--   Para cada alumno, nota esperada = nota media del conjunto de centros entre el alumnado con
--   un punto de partida y un contexto parecidos (tramo de nota previa, acceso a recursos,
--   implicación familiar y renta). Progreso = nota real − nota esperada.
--   Se ajusta solo por lo que el docente no controla. NO se ajusta por asistencia, horas de
--   estudio ni motivación: el docente puede influir en ellas.
--   La app recibe la media y su error típico, y decide en el dominio si la diferencia se
--   distingue del azar (intervalo de confianza del 95 %).
--
-- Reglas de privacidad (las mismas de V4):
--   · Ninguna cifra sale de un grupo con menos de analitica.k_min() alumnos.
--   · Los grupos por debajo del umbral tampoco cuentan para el resultado de sus docentes
--     (si no, se podría deducir restando el resultado de dos docentes).
--   · No se cargan ni la nota media del CSV del docente ni la evaluación de dirección:
--     el resultado se calcula siempre a partir de los datos de alumnado.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Zonas de aterrizaje (todo texto, como spf_raw)
-- ---------------------------------------------------------------------
CREATE TABLE staging.profesor_raw (
    fila                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo               text,
    centro_id            text,
    anios_experiencia    text,
    nivel_formacion      text,
    horas_formacion_anio text,
    tipo_contrato        text
);

CREATE TABLE staging.asignacion_raw (
    fila            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo_profesor text,
    centro_id       text,
    grupo           text,
    asignatura      text,
    horas_semana    text,
    tutor           text
);

COMMENT ON TABLE staging.profesor_raw   IS 'Aterrizaje de profesorado.csv. Se vacía tras cada transformación.';
COMMENT ON TABLE staging.asignacion_raw IS 'Aterrizaje de asignaciones_docentes.csv. Se vacía tras cada transformación.';

-- ---------------------------------------------------------------------
-- Tablas tipadas
-- ---------------------------------------------------------------------
CREATE TABLE staging.profesor (
    codigo               text     PRIMARY KEY CHECK (codigo ~ '^[A-Za-z0-9_-]{1,20}$'),
    centro_id            smallint NOT NULL REFERENCES analitica.centro (id),
    anios_experiencia    smallint NOT NULL CHECK (anios_experiencia BETWEEN 0 AND 50),
    nivel_formacion      text     NOT NULL CHECK (nivel_formacion IN ('grado', 'master', 'doctorado')),
    horas_formacion_anio smallint NOT NULL CHECK (horas_formacion_anio BETWEEN 0 AND 500),
    tipo_contrato        text     NOT NULL CHECK (tipo_contrato IN ('completo', 'parcial'))
);
COMMENT ON TABLE staging.profesor IS
  'Profesorado (sintético). Sin nombres. Acceso solo a través de vistas agregadas de analitica.';

CREATE TABLE staging.asignacion_docente (
    profesor     text     NOT NULL REFERENCES staging.profesor (codigo) ON DELETE CASCADE,
    grupo_id     smallint NOT NULL REFERENCES analitica.grupo (id),
    asignatura   text     NOT NULL CHECK (char_length(asignatura) BETWEEN 1 AND 60),
    horas_semana smallint NOT NULL CHECK (horas_semana BETWEEN 1 AND 30),
    tutor        boolean  NOT NULL DEFAULT false,
    PRIMARY KEY (profesor, grupo_id, asignatura)
);
CREATE INDEX ON staging.asignacion_docente (grupo_id);
-- Como mucho un tutor o tutora por grupo
CREATE UNIQUE INDEX asignacion_un_tutor_por_grupo ON staging.asignacion_docente (grupo_id) WHERE tutor;

-- ---------------------------------------------------------------------
-- Transformación: *_raw → profesor + asignacion_docente (sustituye lo anterior)
-- Uso: cargar los dos CSV en las tablas raw y después  CALL staging.transformar_profesorado();
-- ---------------------------------------------------------------------
CREATE PROCEDURE staging.transformar_profesorado()
LANGUAGE plpgsql AS $$
DECLARE
    v_prof   integer;
    v_asig   integer;
    v_malo   text;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM staging.profesor_raw) THEN
        RAISE EXCEPTION 'staging.profesor_raw está vacía: carga antes profesorado.csv';
    END IF;

    -- Comprobaciones con mensajes claros (las restricciones CHECK son la última barrera)
    SELECT string_agg(DISTINCT coalesce(codigo, '(vacío)'), ', ') INTO v_malo
    FROM (SELECT btrim(codigo) AS codigo, count(*) OVER (PARTITION BY btrim(codigo)) AS n FROM staging.profesor_raw) x
    WHERE n > 1 OR coalesce(codigo, '') = '';
    IF v_malo IS NOT NULL THEN
        RAISE EXCEPTION 'Códigos de docente vacíos o repetidos en profesorado.csv: %', v_malo;
    END IF;

    TRUNCATE staging.asignacion_docente, staging.profesor;

    INSERT INTO staging.profesor (codigo, centro_id, anios_experiencia, nivel_formacion, horas_formacion_anio,
                                  tipo_contrato)
    SELECT btrim(r.codigo),
           staging.a_entero(r.centro_id),
           staging.a_entero(r.anios_experiencia),
           staging.mapear(r.nivel_formacion, 'nivel_formacion',
                          ARRAY['Grado', 'Máster', 'Master', 'Doctorado'],
                          ARRAY['grado', 'master', 'master', 'doctorado']),
           staging.a_entero(r.horas_formacion_anio),
           staging.mapear(r.tipo_contrato, 'tipo_contrato',
                          ARRAY['Tiempo completo', 'Tiempo parcial'], ARRAY['completo', 'parcial'])
    FROM staging.profesor_raw r;
    GET DIAGNOSTICS v_prof = ROW_COUNT;

    -- Cada asignación debe apuntar a un docente conocido de ese mismo centro y a un grupo de ese centro
    SELECT string_agg(format('fila %s (%s, centro %s, grupo %s)', r.fila, r.codigo_profesor, r.centro_id, r.grupo),
                      '; ' ORDER BY r.fila) INTO v_malo
    FROM staging.asignacion_raw r
    LEFT JOIN staging.profesor p ON p.codigo = btrim(r.codigo_profesor)
                                AND p.centro_id = staging.a_entero(r.centro_id)
    LEFT JOIN analitica.grupo g  ON g.centro_id = staging.a_entero(r.centro_id)
                                AND g.nombre = 'Grupo ' || upper(btrim(r.grupo))
    WHERE p.codigo IS NULL OR g.id IS NULL;
    IF v_malo IS NOT NULL THEN
        RAISE EXCEPTION 'Asignaciones con docente o grupo que no existe en ese centro: %', v_malo;
    END IF;

    INSERT INTO staging.asignacion_docente (profesor, grupo_id, asignatura, horas_semana, tutor)
    SELECT btrim(r.codigo_profesor), g.id, btrim(r.asignatura), staging.a_entero(r.horas_semana),
           coalesce(staging.mapear(r.tutor, 'tutor', ARRAY['sí', 'si', 'no'], ARRAY['true', 'true', 'false'])::boolean,
                    false)
    FROM staging.asignacion_raw r
    JOIN analitica.grupo g ON g.centro_id = staging.a_entero(r.centro_id)
                          AND g.nombre = 'Grupo ' || upper(btrim(r.grupo));
    GET DIAGNOSTICS v_asig = ROW_COUNT;

    -- Minimizar copias: las tablas crudas se vacían
    TRUNCATE staging.profesor_raw, staging.asignacion_raw RESTART IDENTITY;

    RAISE NOTICE 'Profesorado cargado: % docentes, % asignaciones', v_prof, v_asig;
END;
$$;

-- ---------------------------------------------------------------------
-- Interna: progreso de cada alumno frente a lo esperado (NUNCA legible por la app)
-- ---------------------------------------------------------------------
CREATE VIEW staging.vw_progreso_alumno AS
WITH base AS (
    SELECT a.id, a.centro_id, a.grupo_id, a.nota_examen,
           CASE WHEN a.nota_previa IS NULL THEN NULL
                WHEN a.nota_previa < 60 THEN 1
                WHEN a.nota_previa < 75 THEN 2
                WHEN a.nota_previa < 90 THEN 3
                ELSE 4 END AS tramo_previa,
           a.acceso_recursos, a.implicacion_familiar, a.renta_familiar
    FROM staging.alumno_spf a
),
esperado AS (
    SELECT b.*,
           avg(b.nota_examen) OVER fino   AS esperada_fina,
           count(*)           OVER fino   AS n_fino,
           avg(b.nota_examen) OVER grueso AS esperada_gruesa
    FROM base b
    WINDOW fino   AS (PARTITION BY b.tramo_previa, b.acceso_recursos, b.implicacion_familiar, b.renta_familiar),
           grueso AS (PARTITION BY b.tramo_previa)
)
SELECT id, centro_id, grupo_id,
       -- Si el perfil exacto tiene poco alumnado de referencia, se compara solo por nota previa
       nota_examen - CASE WHEN n_fino >= 30 THEN esperada_fina ELSE esperada_gruesa END AS progreso
FROM esperado;

-- Interna: progreso por grupo, ya filtrado por el umbral
CREATE VIEW staging.vw_progreso_grupo_publicable AS
SELECT centro_id, grupo_id,
       count(*)::integer                                   AS n_alumnos,
       avg(progreso)                                       AS progreso,
       stddev_samp(progreso) / sqrt(count(*))              AS error_tipico
FROM staging.vw_progreso_alumno
GROUP BY centro_id, grupo_id
HAVING count(*) >= analitica.k_min();

-- ---------------------------------------------------------------------
-- Progreso por grupo (y del centro), con el equipo docente que le da clase
-- ---------------------------------------------------------------------
CREATE VIEW analitica.vw_progreso_grupo AS
WITH centro AS (
    SELECT p.centro_id, count(*)::integer AS n_alumnos, avg(p.progreso) AS progreso,
           stddev_samp(p.progreso) / sqrt(count(*)) AS error_tipico
    FROM staging.vw_progreso_alumno p
    JOIN staging.vw_progreso_grupo_publicable g USING (centro_id, grupo_id)
    GROUP BY p.centro_id
),
equipo AS (
    SELECT a.grupo_id,
           count(DISTINCT a.profesor)::integer                     AS n_docentes,
           max(a.profesor) FILTER (WHERE a.tutor)                  AS tutor,
           sum(a.horas_semana)::integer                            AS horas_semana
    FROM staging.asignacion_docente a
    GROUP BY a.grupo_id
),
perfil AS (
    -- Una fila por docente y grupo, para que quien da dos asignaturas no cuente doble
    SELECT d.grupo_id,
           avg(p.anios_experiencia)                                AS experiencia_media,
           avg(p.horas_formacion_anio)                             AS formacion_media
    FROM (SELECT DISTINCT grupo_id, profesor FROM staging.asignacion_docente) d
    JOIN staging.profesor p ON p.codigo = d.profesor
    GROUP BY d.grupo_id
)
SELECT 'grupo'::text AS ambito, g.centro_id, g.grupo_id, gr.nombre AS grupo, g.n_alumnos,
       round(g.progreso::numeric, 2)                           AS progreso,
       round(g.error_tipico::numeric, 3)                       AS error_tipico,
       e.tutor, coalesce(e.n_docentes, 0)                      AS n_docentes, e.horas_semana,
       round(pf.experiencia_media::numeric, 1)                 AS experiencia_media,
       round(pf.formacion_media::numeric, 0)                   AS formacion_media
FROM staging.vw_progreso_grupo_publicable g
JOIN analitica.grupo gr ON gr.id = g.grupo_id
LEFT JOIN equipo e  ON e.grupo_id = g.grupo_id
LEFT JOIN perfil pf ON pf.grupo_id = g.grupo_id
UNION ALL
SELECT 'centro', c.centro_id, NULL, NULL, c.n_alumnos,
       round(c.progreso::numeric, 2), round(c.error_tipico::numeric, 3),
       NULL, (SELECT count(*)::integer FROM staging.profesor p WHERE p.centro_id = c.centro_id), NULL,
       (SELECT round(avg(p.anios_experiencia), 1) FROM staging.profesor p WHERE p.centro_id = c.centro_id),
       (SELECT round(avg(p.horas_formacion_anio), 0) FROM staging.profesor p WHERE p.centro_id = c.centro_id)
FROM centro c;

COMMENT ON VIEW analitica.vw_progreso_grupo IS
  'Progreso del alumnado frente a lo esperado (nota real − esperada por punto de partida y contexto), por grupo '
  'y centro, con el equipo docente. Grupos con menos de k_min alumnos excluidos.';

-- ---------------------------------------------------------------------
-- Progreso del alumnado de los grupos de cada docente
-- ---------------------------------------------------------------------
CREATE VIEW analitica.vw_progreso_docente AS
WITH asig AS (
    SELECT a.profesor,
           a.grupo_id,
           bool_or(a.tutor)                                         AS tutor,
           sum(a.horas_semana)                                      AS horas,
           string_agg(a.asignatura, ', ' ORDER BY a.asignatura)     AS asignaturas
    FROM staging.asignacion_docente a
    GROUP BY a.profesor, a.grupo_id
),
resumen AS (
    SELECT s.profesor,
           sum(s.horas)::integer                                    AS horas_semana,
           count(*)::integer                                        AS n_grupos,
           string_agg(gr.nombre || CASE WHEN s.tutor THEN ' (tutoría)' ELSE '' END, ' · ' ORDER BY gr.nombre) AS grupos,
           (SELECT string_agg(DISTINCT x.asignatura, ', ' ORDER BY x.asignatura)
              FROM staging.asignacion_docente x WHERE x.profesor = s.profesor) AS asignaturas
    FROM asig s
    JOIN analitica.grupo gr ON gr.id = s.grupo_id
    GROUP BY s.profesor
),
progreso AS (
    -- Solo el alumnado de grupos publicables (umbral k_min)
    SELECT s.profesor, count(*)::integer AS n_alumnos, avg(p.progreso) AS progreso,
           stddev_samp(p.progreso) / sqrt(count(*)) AS error_tipico,
           count(DISTINCT s.grupo_id)::integer AS n_grupos_con_datos
    FROM asig s
    JOIN staging.vw_progreso_grupo_publicable g ON g.grupo_id = s.grupo_id
    JOIN staging.vw_progreso_alumno p ON p.grupo_id = s.grupo_id
    GROUP BY s.profesor
)
SELECT pr.centro_id, pr.codigo,
       pr.anios_experiencia, pr.nivel_formacion, pr.horas_formacion_anio, pr.tipo_contrato,
       r.horas_semana, r.n_grupos, r.grupos, r.asignaturas,
       pg.n_grupos_con_datos, pg.n_alumnos,
       round(pg.progreso::numeric, 2)      AS progreso,
       round(pg.error_tipico::numeric, 3)  AS error_tipico
FROM staging.profesor pr
LEFT JOIN resumen r   ON r.profesor = pr.codigo
LEFT JOIN progreso pg ON pg.profesor = pr.codigo AND pg.n_alumnos >= analitica.k_min();

COMMENT ON VIEW analitica.vw_progreso_docente IS
  'Por docente: datos profesionales (sin nombre) y progreso frente a lo esperado del alumnado de sus grupos. '
  'El resultado es compartido con el resto del equipo de esos grupos: orienta, no evalúa a la persona.';

-- vw_* nuevas de analitica: legibles por app_lector por los privilegios por defecto de V5
