-- =====================================================================
-- V7 · Ingesta de CSV por docentes y evolución agregada por periodos
--
--   · analitica.periodo     → dimensión temporal de las cargas (trimestres)
--   · staging.observacion   → histórico por periodo (filas individuales: NUNCA
--                             accesible para la app, como alumno_spf)
--   · staging.alumno_spf    → sigue siendo la "fotografía actual" que leen las
--                             vistas de V4; para cada grupo contiene su último periodo
--   · staging.carga         → ampliada para auditar quién importa qué y cuándo
--   · staging.spf_tipar()   → el tipado de transformar_spf extraído a una función,
--                             reutilizada por la carga de Kaggle y por la de docentes
--   · app_ingesta           → solo INSERT en spf_raw y EXECUTE de los procedimientos
--                             de ingesta (SECURITY DEFINER). Nada más.
--
-- El rol app_ingesta se crea en db/init/01-rol-ingesta.sh (requiere superusuario).
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_ingesta') THEN
        RAISE EXCEPTION 'Falta el rol app_ingesta. Ejecuta una vez: '
            'docker compose up -d db && docker compose exec db bash /docker-entrypoint-initdb.d/01-rol-ingesta.sh';
    END IF;
END;
$$;

-- Ninguna función nueva de staging es ejecutable por PUBLIC (por defecto sí lo sería)
ALTER DEFAULT PRIVILEGES IN SCHEMA staging REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

-- ---------------------------------------------------------------------
-- Periodos (catálogo, sin datos personales → analitica)
-- ---------------------------------------------------------------------
CREATE TABLE analitica.periodo (
    id           smallint PRIMARY KEY,
    codigo       text     NOT NULL UNIQUE,
    nombre       text     NOT NULL,
    orden        smallint NOT NULL UNIQUE,
    fecha_inicio date,
    fecha_fin    date,
    sintetico    boolean  NOT NULL DEFAULT false,
    CHECK (fecha_inicio IS NULL OR fecha_fin IS NULL OR fecha_inicio < fecha_fin)
);
COMMENT ON TABLE analitica.periodo IS
  'Periodos de evaluación. id 0 = línea base sintética (dataset de Kaggle), no seleccionable para importar.';

INSERT INTO analitica.periodo (id, codigo, nombre, orden, fecha_inicio, fecha_fin, sintetico) VALUES
    (0, 'base',       'Línea base (datos sintéticos)', 0, NULL,         NULL,         true),
    (1, '2025-26-T1', '2025-26 · 1.er trimestre',      1, '2025-09-01', '2025-12-22', false),
    (2, '2025-26-T2', '2025-26 · 2.º trimestre',       2, '2026-01-07', '2026-03-27', false),
    (3, '2025-26-T3', '2025-26 · 3.er trimestre',      3, '2026-04-06', '2026-06-23', false),
    (4, '2026-27-T1', '2026-27 · 1.er trimestre',      4, '2026-09-01', '2026-12-22', false),
    (5, '2026-27-T2', '2026-27 · 2.º trimestre',       5, '2027-01-07', '2027-03-26', false),
    (6, '2026-27-T3', '2026-27 · 3.er trimestre',      6, '2027-04-05', '2027-06-23', false);

-- ---------------------------------------------------------------------
-- Auditoría de cargas
-- ---------------------------------------------------------------------
ALTER TABLE staging.carga
    ADD COLUMN tipo          text     NOT NULL DEFAULT 'kaggle' CHECK (tipo IN ('kaggle', 'docente')),
    ADD COLUMN usuario       text,
    ADD COLUMN centro_id     smallint REFERENCES analitica.centro (id),
    ADD COLUMN grupo_id      smallint REFERENCES analitica.grupo (id),
    ADD COLUMN periodo_id    smallint REFERENCES analitica.periodo (id),
    ADD COLUMN filas_validas integer,
    ADD COLUMN filas_error   integer,
    ADD COLUMN estado        text     NOT NULL DEFAULT 'completada'
        CHECK (estado IN ('pendiente', 'rechazada', 'completada', 'sustituida', 'cancelada', 'caducada', 'error')),
    ADD COLUMN resultado     text,
    ADD COLUMN actualizado   timestamptz;

COMMENT ON COLUMN staging.carga.origen        IS 'Nombre original del archivo (el archivo en sí no se conserva).';
COMMENT ON COLUMN staging.carga.filas_leidas  IS 'Registros procesados (filas de datos del CSV).';
COMMENT ON COLUMN staging.carga.filas_cargadas IS 'Registros incorporados a la base de datos.';
COMMENT ON COLUMN staging.carga.usuario       IS 'Usuario de la aplicación que hizo la importación (docente).';
COMMENT ON COLUMN staging.carga.estado        IS
  'pendiente (validada, a la espera de confirmación) · rechazada (no supera la validación) · completada · '
  'sustituida (otra carga del mismo grupo y periodo la reemplazó) · cancelada · caducada · error.';

UPDATE staging.carga SET filas_validas = filas_cargadas, filas_error = 0,
                         resultado = 'Carga del dataset de Kaggle (scripts/cargar_kaggle.sh).'
WHERE tipo = 'kaggle';

-- Zona de aterrizaje compartida: cada importación de docente marca sus filas.
-- carga_id NULL = filas del script de Kaggle.
ALTER TABLE staging.spf_raw ADD COLUMN carga_id integer REFERENCES staging.carga (id);
CREATE INDEX ON staging.spf_raw (carga_id);

-- ---------------------------------------------------------------------
-- Histórico por periodo. Mismas columnas y restricciones que alumno_spf.
-- ---------------------------------------------------------------------
CREATE TABLE staging.observacion (
    LIKE staging.alumno_spf INCLUDING CONSTRAINTS INCLUDING DEFAULTS,
    periodo_id smallint NOT NULL REFERENCES analitica.periodo (id),
    carga_id   integer  NOT NULL REFERENCES staging.carga (id),
    PRIMARY KEY (id),
    FOREIGN KEY (centro_id) REFERENCES analitica.centro (id),
    FOREIGN KEY (grupo_id)  REFERENCES analitica.grupo (id)
);
COMMENT ON TABLE staging.observacion IS
  'Registros por periodo (línea base + importaciones de docentes). Solo accesible mediante vistas agregadas.';
CREATE INDEX ON staging.observacion (grupo_id, periodo_id);
CREATE INDEX ON staging.observacion (centro_id, periodo_id);

-- Identificadores técnicos de los registros importados por docentes (no son identificadores reales;
-- empiezan lejos de los nº de fila del CSV de Kaggle para no colisionar nunca).
CREATE SEQUENCE staging.observacion_id START 100000000;

-- ---------------------------------------------------------------------
-- Tipado de spf_raw (antes, dentro de transformar_spf). Valores estrictos:
-- un valor no previsto hace fallar la carga con un mensaje claro.
-- ---------------------------------------------------------------------
CREATE FUNCTION staging.spf_tipar(p_carga integer)
RETURNS TABLE (
    fila bigint, horas_estudio smallint, asistencia smallint, implicacion_familiar staging.nivel,
    acceso_recursos staging.nivel, extraescolares boolean, horas_sueno smallint, nota_previa smallint,
    motivacion staging.nivel, acceso_internet boolean, tutorias smallint, renta_familiar staging.nivel,
    calidad_docente staging.nivel, tipo_centro text, influencia_pares text, actividad_fisica smallint,
    dificultad_aprendizaje boolean, estudios_familia text, distancia_casa text, genero text,
    nota_examen smallint, nota_sin_recortar smallint)
LANGUAGE sql STABLE AS $$
    SELECT
        r.fila,
        staging.a_entero(r.hours_studied),
        staging.a_entero(r.attendance),
        staging.a_nivel(r.parental_involvement, 'Parental_Involvement'),
        staging.a_nivel(r.access_to_resources, 'Access_to_Resources'),
        staging.a_bool(r.extracurricular_activities, 'Extracurricular_Activities'),
        staging.a_entero(r.sleep_hours),
        staging.a_entero(r.previous_scores),
        staging.a_nivel(r.motivation_level, 'Motivation_Level'),
        staging.a_bool(r.internet_access, 'Internet_Access'),
        staging.a_entero(r.tutoring_sessions),
        staging.a_nivel(r.family_income, 'Family_Income'),
        staging.a_nivel(r.teacher_quality, 'Teacher_Quality'),
        staging.mapear(r.school_type, 'School_Type', ARRAY['Public', 'Private'], ARRAY['publico', 'privado']),
        staging.mapear(r.peer_influence, 'Peer_Influence',
                       ARRAY['Negative', 'Neutral', 'Positive'], ARRAY['negativa', 'neutra', 'positiva']),
        staging.a_entero(r.physical_activity),
        staging.a_bool(r.learning_disabilities, 'Learning_Disabilities'),
        staging.mapear(r.parental_education_level, 'Parental_Education_Level',
                       ARRAY['High School', 'College', 'Postgraduate'], ARRAY['secundaria', 'grado', 'posgrado']),
        staging.mapear(r.distance_from_home, 'Distance_from_Home',
                       ARRAY['Near', 'Moderate', 'Far'], ARRAY['cerca', 'media', 'lejos']),
        staging.mapear(r.gender, 'Gender', ARRAY['Female', 'Male'], ARRAY['mujer', 'hombre']),
        LEAST(staging.a_entero(r.exam_score), 100)::smallint,
        staging.a_entero(r.exam_score)
    FROM staging.spf_raw r
    WHERE r.carga_id IS NOT DISTINCT FROM p_carga
$$;

-- ---------------------------------------------------------------------
-- Fotografía actual de un grupo = sus registros del último periodo con datos
-- ---------------------------------------------------------------------
CREATE PROCEDURE staging.refrescar_foto_grupo(p_grupo smallint)
LANGUAGE plpgsql AS $$
DECLARE
    v_periodo smallint;
BEGIN
    SELECT o.periodo_id INTO v_periodo
    FROM staging.observacion o
    JOIN analitica.periodo p ON p.id = o.periodo_id
    WHERE o.grupo_id = p_grupo
    ORDER BY p.orden DESC
    LIMIT 1;

    IF v_periodo IS NULL THEN
        RETURN;
    END IF;

    DELETE FROM staging.alumno_spf WHERE grupo_id = p_grupo;
    INSERT INTO staging.alumno_spf (
        id, centro_id, grupo_id, horas_estudio, asistencia, implicacion_familiar, acceso_recursos,
        extraescolares, horas_sueno, nota_previa, motivacion, acceso_internet, tutorias, renta_familiar,
        calidad_docente, tipo_centro, influencia_pares, actividad_fisica, dificultad_aprendizaje,
        estudios_familia, distancia_casa, genero, nota_examen)
    SELECT
        id, centro_id, grupo_id, horas_estudio, asistencia, implicacion_familiar, acceso_recursos,
        extraescolares, horas_sueno, nota_previa, motivacion, acceso_internet, tutorias, renta_familiar,
        calidad_docente, tipo_centro, influencia_pares, actividad_fisica, dificultad_aprendizaje,
        estudios_familia, distancia_casa, genero, nota_examen
    FROM staging.observacion
    WHERE grupo_id = p_grupo AND periodo_id = v_periodo;
END;
$$;

-- ---------------------------------------------------------------------
-- Carga de Kaggle (scripts/cargar_kaggle.sh). Mismo comportamiento que en V2,
-- usando spf_tipar(), y además:
--   · guarda la línea base (periodo 0) en staging.observacion
--   · conserva los datos importados por docentes (vuelve a aplicarlos a la foto)
-- ---------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE staging.transformar_spf(origen text DEFAULT 'StudentPerformanceFactors.csv')
LANGUAGE plpgsql AS $$
DECLARE
    v_leidas    integer;
    v_cargadas  integer;
    v_recortes  integer;
    v_carga     integer;
    v_grupo     smallint;
BEGIN
    SELECT count(*) INTO v_leidas FROM staging.spf_raw WHERE carga_id IS NULL;
    IF v_leidas = 0 THEN
        RAISE EXCEPTION 'staging.spf_raw está vacía: carga antes el CSV';
    END IF;

    SELECT count(*) INTO v_recortes FROM staging.spf_tipar(NULL) t WHERE t.nota_sin_recortar > 100;

    TRUNCATE staging.alumno_spf;

    WITH t AS (
        SELECT t.fila::integer AS id, t.*
        FROM staging.spf_tipar(NULL) t
    ),
    centros AS (
        SELECT tipo_centro, array_agg(id ORDER BY id) AS ids
        FROM analitica.centro
        WHERE sintetico
        GROUP BY tipo_centro
    ),
    con_centro AS (
        SELECT t.*,
               c.ids[staging.cubo('centro-' || t.id, cardinality(c.ids))] AS centro_id
        FROM t
        JOIN centros c ON c.tipo_centro = t.tipo_centro
    ),
    grupos AS (
        SELECT centro_id, array_agg(id ORDER BY id) AS ids
        FROM analitica.grupo
        GROUP BY centro_id
    )
    INSERT INTO staging.alumno_spf (
        id, centro_id, grupo_id, horas_estudio, asistencia, implicacion_familiar, acceso_recursos,
        extraescolares, horas_sueno, nota_previa, motivacion, acceso_internet, tutorias, renta_familiar,
        calidad_docente, tipo_centro, influencia_pares, actividad_fisica, dificultad_aprendizaje,
        estudios_familia, distancia_casa, genero, nota_examen)
    SELECT
        cc.id, cc.centro_id, g.ids[staging.cubo('grupo-' || cc.id, cardinality(g.ids))],
        cc.horas_estudio, cc.asistencia, cc.implicacion_familiar, cc.acceso_recursos,
        cc.extraescolares, cc.horas_sueno, cc.nota_previa, cc.motivacion, cc.acceso_internet, cc.tutorias,
        cc.renta_familiar, cc.calidad_docente, cc.tipo_centro, cc.influencia_pares, cc.actividad_fisica,
        cc.dificultad_aprendizaje, cc.estudios_familia, cc.distancia_casa, cc.genero, cc.nota_examen
    FROM con_centro cc
    JOIN grupos g ON g.centro_id = cc.centro_id;

    GET DIAGNOSTICS v_cargadas = ROW_COUNT;

    IF v_cargadas <> v_leidas THEN
        RAISE EXCEPTION 'Se leyeron % filas pero solo se cargaron % (¿School_Type vacío o centro sin grupos?)',
            v_leidas, v_cargadas;
    END IF;

    INSERT INTO staging.carga (origen, filas_leidas, filas_cargadas, notas_recortadas,
                               tipo, filas_validas, filas_error, estado, resultado)
    VALUES (origen, v_leidas, v_cargadas, v_recortes, 'kaggle', v_cargadas, 0, 'completada',
            format('Carga del dataset de Kaggle: %s registros, %s notas recortadas a 100.', v_cargadas, v_recortes))
    RETURNING id INTO v_carga;

    -- Línea base (periodo 0) del histórico
    DELETE FROM staging.observacion WHERE periodo_id = 0;
    INSERT INTO staging.observacion SELECT a.*, 0::smallint, v_carga FROM staging.alumno_spf a;

    -- Los grupos con datos de docentes vuelven a mostrar su último periodo
    FOR v_grupo IN SELECT DISTINCT grupo_id FROM staging.observacion WHERE periodo_id <> 0 LOOP
        CALL staging.refrescar_foto_grupo(v_grupo);
    END LOOP;

    -- Minimizar copias de datos individuales: la tabla cruda se vacía.
    DELETE FROM staging.spf_raw WHERE carga_id IS NULL;

    RAISE NOTICE 'Carga completada: % alumnos, % notas recortadas a 100', v_cargadas, v_recortes;
END;
$$;

-- ---------------------------------------------------------------------
-- Ingesta de docentes (la app como app_ingesta). SECURITY DEFINER: se ejecutan
-- con los permisos de escuela_admin, pero solo hacen lo que está escrito aquí.
-- ---------------------------------------------------------------------

-- Fase 1: registra el resultado de la validación (pendiente o rechazada)
CREATE FUNCTION staging.abrir_carga(
    p_usuario text, p_archivo text, p_grupo smallint, p_periodo smallint,
    p_filas integer, p_validas integer, p_errores integer, p_estado text, p_resultado text)
RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    v_id     integer;
    v_centro smallint;
BEGIN
    IF p_estado NOT IN ('pendiente', 'rechazada') THEN
        RAISE EXCEPTION 'Estado inicial no válido: %', p_estado;
    END IF;
    IF coalesce(btrim(p_usuario), '') = '' THEN
        RAISE EXCEPTION 'Falta el usuario que realiza la importación';
    END IF;
    SELECT centro_id INTO v_centro FROM analitica.grupo WHERE id = p_grupo;
    IF v_centro IS NULL THEN
        RAISE EXCEPTION 'El grupo % no existe', p_grupo;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM analitica.periodo WHERE id = p_periodo AND NOT sintetico) THEN
        RAISE EXCEPTION 'El periodo % no existe o no admite importaciones', p_periodo;
    END IF;

    INSERT INTO staging.carga (origen, filas_leidas, filas_cargadas, notas_recortadas, tipo, usuario,
                               centro_id, grupo_id, periodo_id, filas_validas, filas_error, estado, resultado)
    VALUES (left(coalesce(p_archivo, 'sin nombre'), 200), p_filas, 0, 0, 'docente', left(p_usuario, 100),
            v_centro, p_grupo, p_periodo, p_validas, p_errores, p_estado, left(p_resultado, 2000))
    RETURNING id INTO v_id;
    RETURN v_id;
END;
$$;

-- Revalidación tras cambiar el mapeo, cancelación, caducidad o error
CREATE FUNCTION staging.actualizar_carga(
    p_id integer, p_estado text, p_validas integer, p_errores integer, p_resultado text)
RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF p_estado NOT IN ('pendiente', 'rechazada', 'cancelada', 'caducada', 'error') THEN
        RAISE EXCEPTION 'Estado no válido: %', p_estado;
    END IF;
    UPDATE staging.carga
    SET estado = p_estado,
        filas_validas = coalesce(p_validas, filas_validas),
        filas_error = coalesce(p_errores, filas_error),
        resultado = left(p_resultado, 2000),
        actualizado = now()
    WHERE id = p_id AND tipo = 'docente' AND estado IN ('pendiente', 'rechazada');
    IF NOT FOUND THEN
        RAISE EXCEPTION 'La carga % no existe o ya está cerrada', p_id;
    END IF;
    -- Restos de una confirmación fallida
    DELETE FROM staging.spf_raw WHERE carga_id = p_id;
END;
$$;

-- Fase 2: tras la confirmación del docente, spf_raw (filas de esta carga) → observacion + foto
CREATE PROCEDURE staging.transformar_ingesta(p_carga integer)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    c           staging.carga%ROWTYPE;
    v_leidas    integer;
    v_cargadas  integer;
    v_tipo      text;
    v_ambito    text;
    v_periodo   text;
BEGIN
    SELECT * INTO c FROM staging.carga WHERE id = p_carga FOR UPDATE;
    IF NOT FOUND OR c.tipo <> 'docente' THEN
        RAISE EXCEPTION 'La carga % no existe', p_carga;
    END IF;
    IF c.estado <> 'pendiente' THEN
        RAISE EXCEPTION 'La carga % no se puede confirmar (estado: %)', p_carga, c.estado;
    END IF;

    SELECT count(*) INTO v_leidas FROM staging.spf_raw WHERE carga_id = p_carga;
    IF v_leidas = 0 THEN
        RAISE EXCEPTION 'No hay registros de la carga % en la zona de aterrizaje', p_carga;
    END IF;
    IF v_leidas <> c.filas_validas THEN
        RAISE EXCEPTION 'Se esperaban % registros válidos y han llegado %', c.filas_validas, v_leidas;
    END IF;

    SELECT ce.tipo_centro, ce.nombre || ' · ' || g.nombre INTO v_tipo, v_ambito
    FROM analitica.grupo g JOIN analitica.centro ce ON ce.id = g.centro_id
    WHERE g.id = c.grupo_id;
    SELECT nombre INTO v_periodo FROM analitica.periodo WHERE id = c.periodo_id;

    -- Una sola carga vigente por grupo y periodo: la nueva sustituye a la anterior
    DELETE FROM staging.observacion WHERE grupo_id = c.grupo_id AND periodo_id = c.periodo_id;
    UPDATE staging.carga SET estado = 'sustituida', actualizado = now(),
           resultado = coalesce(resultado, '') || format(' Sustituida por la carga %s.', p_carga)
    WHERE tipo = 'docente' AND estado = 'completada'
      AND grupo_id = c.grupo_id AND periodo_id = c.periodo_id;

    INSERT INTO staging.observacion (
        id, centro_id, grupo_id, horas_estudio, asistencia, implicacion_familiar, acceso_recursos,
        extraescolares, horas_sueno, nota_previa, motivacion, acceso_internet, tutorias, renta_familiar,
        calidad_docente, tipo_centro, influencia_pares, actividad_fisica, dificultad_aprendizaje,
        estudios_familia, distancia_casa, genero, nota_examen, periodo_id, carga_id)
    SELECT
        nextval('staging.observacion_id')::integer, c.centro_id, c.grupo_id,
        t.horas_estudio, t.asistencia, t.implicacion_familiar, t.acceso_recursos,
        t.extraescolares, t.horas_sueno, t.nota_previa, t.motivacion, t.acceso_internet, t.tutorias,
        t.renta_familiar, t.calidad_docente,
        v_tipo,                      -- la titularidad la da el centro, no el archivo
        t.influencia_pares, t.actividad_fisica,
        NULL,                        -- dato sensible: la ingesta de docentes nunca lo guarda
        t.estudios_familia, t.distancia_casa, t.genero, t.nota_examen,
        c.periodo_id, p_carga
    FROM staging.spf_tipar(p_carga) t;

    GET DIAGNOSTICS v_cargadas = ROW_COUNT;

    CALL staging.refrescar_foto_grupo(c.grupo_id);

    -- El archivo nunca se guarda y sus filas crudas se borran en la misma transacción
    DELETE FROM staging.spf_raw WHERE carga_id = p_carga;

    UPDATE staging.carga
    SET estado = 'completada', filas_cargadas = v_cargadas, actualizado = now(),
        resultado = format('%s registros incorporados a %s (%s).', v_cargadas, v_ambito, v_periodo)
    WHERE id = p_carga;
END;
$$;

-- ---------------------------------------------------------------------
-- Vistas agregadas nuevas (mismas reglas que V4: umbral k_min, sin security_invoker)
-- ---------------------------------------------------------------------

-- Indicadores por periodo, centro y grupo
CREATE VIEW analitica.vw_evolucion AS
WITH agg AS (
    SELECT
        o.periodo_id,
        CASE WHEN GROUPING(o.grupo_id) = 1 THEN 'centro' ELSE 'grupo' END       AS ambito,
        o.centro_id,
        o.grupo_id,
        count(*)::integer                                                       AS n_alumnos,
        count(DISTINCT o.grupo_id)::integer                                     AS n_grupos,
        avg(o.nota_examen)                                                      AS nota_media,
        stddev_samp(o.nota_examen)                                              AS nota_sd,
        count(o.asistencia)::integer                                            AS n_asistencia,
        avg(o.asistencia)                                                       AS asistencia_media,
        stddev_samp(o.asistencia)                                               AS asistencia_sd,
        avg((o.asistencia < 80)::int) * 100                                     AS pct_asistencia_baja,
        avg(o.horas_estudio)                                                    AS horas_estudio_media,
        avg(o.tutorias)                                                         AS tutorias_media,
        max(coalesce(ca.actualizado, ca.fecha))                                 AS actualizado
    FROM staging.observacion o
    JOIN staging.carga ca ON ca.id = o.carga_id
    GROUP BY GROUPING SETS ((o.periodo_id, o.centro_id), (o.periodo_id, o.centro_id, o.grupo_id))
)
SELECT
    agg.ambito, agg.centro_id, c.nombre AS centro, agg.grupo_id, g.nombre AS grupo,
    agg.periodo_id, p.codigo AS periodo_codigo, p.nombre AS periodo, p.orden AS periodo_orden,
    p.sintetico AS periodo_sintetico,
    agg.n_alumnos, agg.n_grupos,
    round(agg.nota_media, 2)                                                    AS nota_media,
    round(agg.nota_sd::numeric, 2)                                              AS nota_sd,
    CASE WHEN agg.n_asistencia >= analitica.k_min() THEN agg.n_asistencia END   AS n_asistencia,
    CASE WHEN agg.n_asistencia >= analitica.k_min() THEN round(agg.asistencia_media, 1) END    AS asistencia_media,
    CASE WHEN agg.n_asistencia >= analitica.k_min() THEN round(agg.asistencia_sd::numeric, 2) END AS asistencia_sd,
    CASE WHEN agg.n_asistencia >= analitica.k_min() THEN round(agg.pct_asistencia_baja, 1) END AS pct_asistencia_baja,
    round(agg.horas_estudio_media, 1)                                           AS horas_estudio_media,
    round(agg.tutorias_media, 2)                                                AS tutorias_media,
    agg.actualizado
FROM agg
JOIN analitica.periodo p ON p.id = agg.periodo_id
LEFT JOIN analitica.centro c ON c.id = agg.centro_id
LEFT JOIN analitica.grupo  g ON g.id = agg.grupo_id
WHERE agg.n_alumnos >= analitica.k_min();

COMMENT ON VIEW analitica.vw_evolucion IS
  'Indicadores agregados por periodo (centro y grupo). n_grupos = grupos con datos en ese periodo.';

-- Dispersión de la nota por nivel de factor (para el intervalo del simulador)
CREATE VIEW analitica.vw_factor_nivel_sd AS
WITH sd AS (
    SELECT
        CASE WHEN GROUPING(l.centro_id) = 1 THEN 'global'
             WHEN GROUPING(l.grupo_id)  = 1 THEN 'centro'
             ELSE 'grupo' END              AS ambito,
        l.centro_id, l.grupo_id, l.factor, l.orden,
        stddev_samp(l.nota_examen)         AS nota_sd
    FROM staging.vw_spf_largo l
    GROUP BY GROUPING SETS ((l.factor, l.orden),
                            (l.centro_id, l.factor, l.orden),
                            (l.centro_id, l.grupo_id, l.factor, l.orden))
)
SELECT v.ambito, v.centro_id, v.grupo_id, v.factor, v.orden, v.n,
       round(v.nota_media, 2)          AS nota_media,
       round(sd.nota_sd::numeric, 2)   AS nota_sd
FROM staging.vw_factor_nivel_publicable v
JOIN sd ON sd.ambito = v.ambito AND sd.factor = v.factor AND sd.orden = v.orden
       AND sd.centro_id IS NOT DISTINCT FROM v.centro_id
       AND sd.grupo_id  IS NOT DISTINCT FROM v.grupo_id;

COMMENT ON VIEW analitica.vw_factor_nivel_sd IS
  'Nota media y desviación típica por nivel de factor (mismo umbral que vw_factor_nivel).';

-- Auditoría legible (sin datos de alumnos)
CREATE VIEW analitica.vw_cargas AS
SELECT ca.id, ca.fecha, coalesce(ca.actualizado, ca.fecha) AS actualizado, ca.tipo, ca.usuario,
       ca.origen AS archivo, ca.centro_id, ce.nombre AS centro, ca.grupo_id, g.nombre AS grupo,
       ca.periodo_id, p.nombre AS periodo,
       ca.filas_leidas AS filas_procesadas, ca.filas_validas, ca.filas_error, ca.filas_cargadas,
       ca.estado, ca.resultado
FROM staging.carga ca
LEFT JOIN analitica.centro  ce ON ce.id = ca.centro_id
LEFT JOIN analitica.grupo   g  ON g.id = ca.grupo_id
LEFT JOIN analitica.periodo p  ON p.id = ca.periodo_id;

COMMENT ON VIEW analitica.vw_cargas IS 'Registro de importaciones: qué archivo, quién, cuándo y con qué resultado.';

-- La calidad de datos se refiere a la última carga que llegó a completarse
CREATE OR REPLACE VIEW analitica.vw_calidad_datos AS
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
LEFT JOIN LATERAL (SELECT fecha, notas_recortadas FROM staging.carga
                   WHERE estado IN ('completada', 'sustituida') ORDER BY id DESC LIMIT 1) ca ON true;

-- ---------------------------------------------------------------------
-- Línea base con los datos ya cargados (si la base de datos no está vacía)
-- ---------------------------------------------------------------------
INSERT INTO staging.observacion
SELECT a.*, 0::smallint, k.id
FROM staging.alumno_spf a
CROSS JOIN (SELECT max(id) AS id FROM staging.carga WHERE tipo = 'kaggle') k
WHERE k.id IS NOT NULL;

-- ---------------------------------------------------------------------
-- Permisos
-- ---------------------------------------------------------------------
REVOKE ALL ON ALL FUNCTIONS  IN SCHEMA staging FROM PUBLIC;
REVOKE ALL ON ALL PROCEDURES IN SCHEMA staging FROM PUBLIC;

-- app_ingesta: conectarse, ver el esquema staging (para nombrar sus objetos),
-- INSERT en spf_raw y EXECUTE de los tres puntos de entrada de la ingesta. Nada más.
DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO app_ingesta', current_database());
END;
$$;
GRANT USAGE ON SCHEMA staging TO app_ingesta;
GRANT INSERT (carga_id, hours_studied, attendance, parental_involvement, access_to_resources,
              extracurricular_activities, sleep_hours, previous_scores, motivation_level, internet_access,
              tutoring_sessions, family_income, teacher_quality, school_type, peer_influence,
              physical_activity, learning_disabilities, parental_education_level, distance_from_home,
              gender, exam_score)
    ON staging.spf_raw TO app_ingesta;
GRANT EXECUTE ON FUNCTION staging.abrir_carga(text, text, smallint, smallint, integer, integer, integer, text, text)
    TO app_ingesta;
GRANT EXECUTE ON FUNCTION staging.actualizar_carga(integer, text, integer, integer, text) TO app_ingesta;
GRANT EXECUTE ON PROCEDURE staging.transformar_ingesta(integer) TO app_ingesta;

-- app_lector: las vistas y tablas nuevas de analitica ya son legibles por los
-- privilegios por defecto de V5; se repite de forma explícita por claridad.
GRANT SELECT ON analitica.periodo, analitica.vw_evolucion, analitica.vw_factor_nivel_sd, analitica.vw_cargas
    TO app_lector;
