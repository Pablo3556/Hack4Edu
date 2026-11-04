-- =====================================================================
-- V2 · Dataset Kaggle "Student Performance Factors" (lainguyn123)
--   Dataset SINTÉTICO, licencia CC0, ~6.607 filas × 20 columnas.
--   No trae centro, grupo ni fechas: se asignan centros y grupos
--   ficticios (de forma determinista) para poder demostrar las vistas
--   por centro y por grupo. Las diferencias entre centros son, por
--   tanto, ruido de muestreo y no deben interpretarse.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Catálogos (no contienen datos personales → viven en analitica)
-- ---------------------------------------------------------------------
CREATE TABLE analitica.centro (
    id          smallint PRIMARY KEY,
    nombre      text     NOT NULL UNIQUE,
    tipo_centro text     NOT NULL CHECK (tipo_centro IN ('publico', 'privado')),
    sintetico   boolean  NOT NULL DEFAULT true
);
COMMENT ON TABLE analitica.centro IS 'Centros de la demo. sintetico = true: centro ficticio asignado al dataset de Kaggle.';

CREATE TABLE analitica.grupo (
    id        smallint PRIMARY KEY,
    centro_id smallint NOT NULL REFERENCES analitica.centro (id),
    nombre    text     NOT NULL,
    UNIQUE (centro_id, nombre)
);

-- 6 centros públicos y 3 privados (≈ proporción público/privado del dataset)
INSERT INTO analitica.centro (id, nombre, tipo_centro) VALUES
    (1, 'Centro Demo 01', 'publico'),
    (2, 'Centro Demo 02', 'publico'),
    (3, 'Centro Demo 03', 'publico'),
    (4, 'Centro Demo 04', 'publico'),
    (5, 'Centro Demo 05', 'publico'),
    (6, 'Centro Demo 06', 'publico'),
    (7, 'Centro Demo 07', 'privado'),
    (8, 'Centro Demo 08', 'privado'),
    (9, 'Centro Demo 09', 'privado');

-- 4 grupos por centro: id = (centro-1)*4 + 1..4
INSERT INTO analitica.grupo (id, centro_id, nombre)
SELECT (c.id - 1) * 4 + g.n, c.id, 'Grupo ' || chr(64 + g.n)
FROM analitica.centro c
CROSS JOIN generate_series(1, 4) AS g(n);

-- ---------------------------------------------------------------------
-- Tabla cruda: espejo exacto del CSV (todo texto, mismo orden de columnas)
-- ---------------------------------------------------------------------
CREATE TABLE staging.spf_raw (
    fila                        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hours_studied               text,
    attendance                  text,
    parental_involvement        text,
    access_to_resources         text,
    extracurricular_activities  text,
    sleep_hours                 text,
    previous_scores             text,
    motivation_level            text,
    internet_access             text,
    tutoring_sessions           text,
    family_income               text,
    teacher_quality             text,
    school_type                 text,
    peer_influence              text,
    physical_activity           text,
    learning_disabilities       text,
    parental_education_level    text,
    distance_from_home          text,
    gender                      text,
    exam_score                  text
);
COMMENT ON TABLE staging.spf_raw IS 'Zona de aterrizaje del CSV. Se vacía tras cada transformación.';

-- ---------------------------------------------------------------------
-- Tabla tipada y en castellano
-- ---------------------------------------------------------------------
CREATE TYPE staging.nivel AS ENUM ('bajo', 'medio', 'alto');

CREATE TABLE staging.alumno_spf (
    id                      integer  PRIMARY KEY,          -- nº de fila del CSV (no es un identificador real)
    centro_id               smallint NOT NULL REFERENCES analitica.centro (id),
    grupo_id                smallint NOT NULL REFERENCES analitica.grupo (id),
    horas_estudio           smallint CHECK (horas_estudio BETWEEN 0 AND 80),        -- horas/semana
    asistencia              smallint CHECK (asistencia BETWEEN 0 AND 100),          -- % de clases
    implicacion_familiar    staging.nivel,
    acceso_recursos         staging.nivel,
    extraescolares          boolean,
    horas_sueno             smallint CHECK (horas_sueno BETWEEN 0 AND 24),          -- horas/noche
    nota_previa             smallint CHECK (nota_previa BETWEEN 0 AND 100),
    motivacion              staging.nivel,
    acceso_internet         boolean,
    tutorias                smallint CHECK (tutorias BETWEEN 0 AND 30),             -- sesiones/mes
    renta_familiar          staging.nivel,
    calidad_docente         staging.nivel,                                          -- tiene nulos en origen
    tipo_centro             text CHECK (tipo_centro IN ('publico', 'privado')),
    influencia_pares        text CHECK (influencia_pares IN ('negativa', 'neutra', 'positiva')),
    actividad_fisica        smallint CHECK (actividad_fisica BETWEEN 0 AND 40),     -- horas/semana
    dificultad_aprendizaje  boolean,                                                -- dato sensible: no se expone
    estudios_familia        text CHECK (estudios_familia IN ('secundaria', 'grado', 'posgrado')),
    distancia_casa          text CHECK (distancia_casa IN ('cerca', 'media', 'lejos')),
    genero                  text CHECK (genero IN ('mujer', 'hombre')),
    nota_examen             smallint NOT NULL CHECK (nota_examen BETWEEN 0 AND 100),
    CONSTRAINT grupo_del_centro CHECK (((grupo_id - 1) / 4) + 1 = centro_id)
);
COMMENT ON TABLE staging.alumno_spf IS 'Student Performance Factors tipado. Sintético. Acceso solo a través de vistas agregadas.';
COMMENT ON COLUMN staging.alumno_spf.dificultad_aprendizaje IS 'Categoría sensible: excluida de todas las vistas de analitica.';

CREATE INDEX ON staging.alumno_spf (centro_id, grupo_id);

-- Registro de cargas (auditoría y calidad de datos)
CREATE TABLE staging.carga (
    id                  integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha               timestamptz NOT NULL DEFAULT now(),
    origen              text        NOT NULL,
    filas_leidas        integer     NOT NULL,
    filas_cargadas      integer     NOT NULL,
    notas_recortadas    integer     NOT NULL   -- Exam_Score > 100 en origen, recortado a 100
);

-- ---------------------------------------------------------------------
-- Traducción estricta de valores: si llega un valor no previsto, la carga
-- falla con un mensaje claro en lugar de guardar un NULL silencioso.
-- ---------------------------------------------------------------------
CREATE FUNCTION staging.mapear(valor text, columna text, desde text[], hacia text[])
RETURNS text
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    pos integer;
BEGIN
    IF valor IS NULL OR btrim(valor) = '' THEN
        RETURN NULL;
    END IF;
    pos := array_position(desde, btrim(valor));
    IF pos IS NULL THEN
        RAISE EXCEPTION 'Valor no previsto en %: "%" (esperados: %)', columna, valor, desde;
    END IF;
    RETURN hacia[pos];
END;
$$;

CREATE FUNCTION staging.a_nivel(valor text, columna text)
RETURNS staging.nivel
LANGUAGE sql IMMUTABLE AS $$
    SELECT staging.mapear(valor, columna, ARRAY['Low', 'Medium', 'High'], ARRAY['bajo', 'medio', 'alto'])::staging.nivel
$$;

CREATE FUNCTION staging.a_bool(valor text, columna text)
RETURNS boolean
LANGUAGE sql IMMUTABLE AS $$
    SELECT staging.mapear(valor, columna, ARRAY['No', 'Yes'], ARRAY['false', 'true'])::boolean
$$;

CREATE FUNCTION staging.a_entero(valor text)
RETURNS smallint
LANGUAGE sql IMMUTABLE AS $$
    SELECT round(NULLIF(btrim(valor), '')::numeric)::smallint
$$;

-- Hash determinista y no negativo para repartir alumnos entre centros y grupos
CREATE FUNCTION staging.cubo(semilla text, n integer)
RETURNS integer
LANGUAGE sql IMMUTABLE AS $$
    SELECT 1 + mod(hashtext(semilla)::bigint + 2147483648, n)::integer
$$;

-- ---------------------------------------------------------------------
-- Transformación: staging.spf_raw → staging.alumno_spf
-- Uso: cargar el CSV en staging.spf_raw y después  CALL staging.transformar_spf();
-- ---------------------------------------------------------------------
CREATE PROCEDURE staging.transformar_spf(origen text DEFAULT 'StudentPerformanceFactors.csv')
LANGUAGE plpgsql AS $$
DECLARE
    v_leidas    integer;
    v_cargadas  integer;
    v_recortes  integer;
BEGIN
    SELECT count(*) INTO v_leidas FROM staging.spf_raw;
    IF v_leidas = 0 THEN
        RAISE EXCEPTION 'staging.spf_raw está vacía: carga antes el CSV';
    END IF;

    SELECT count(*) INTO v_recortes
    FROM staging.spf_raw
    WHERE staging.a_entero(exam_score) > 100;

    TRUNCATE staging.alumno_spf;

    WITH t AS (
        SELECT
            r.fila::integer                                                         AS id,
            staging.a_entero(r.hours_studied)                                       AS horas_estudio,
            staging.a_entero(r.attendance)                                          AS asistencia,
            staging.a_nivel(r.parental_involvement, 'Parental_Involvement')         AS implicacion_familiar,
            staging.a_nivel(r.access_to_resources, 'Access_to_Resources')           AS acceso_recursos,
            staging.a_bool(r.extracurricular_activities, 'Extracurricular_Activities') AS extraescolares,
            staging.a_entero(r.sleep_hours)                                         AS horas_sueno,
            staging.a_entero(r.previous_scores)                                     AS nota_previa,
            staging.a_nivel(r.motivation_level, 'Motivation_Level')                 AS motivacion,
            staging.a_bool(r.internet_access, 'Internet_Access')                    AS acceso_internet,
            staging.a_entero(r.tutoring_sessions)                                   AS tutorias,
            staging.a_nivel(r.family_income, 'Family_Income')                       AS renta_familiar,
            staging.a_nivel(r.teacher_quality, 'Teacher_Quality')                   AS calidad_docente,
            staging.mapear(r.school_type, 'School_Type',
                           ARRAY['Public', 'Private'], ARRAY['publico', 'privado']) AS tipo_centro,
            staging.mapear(r.peer_influence, 'Peer_Influence',
                           ARRAY['Negative', 'Neutral', 'Positive'],
                           ARRAY['negativa', 'neutra', 'positiva'])                 AS influencia_pares,
            staging.a_entero(r.physical_activity)                                   AS actividad_fisica,
            staging.a_bool(r.learning_disabilities, 'Learning_Disabilities')        AS dificultad_aprendizaje,
            staging.mapear(r.parental_education_level, 'Parental_Education_Level',
                           ARRAY['High School', 'College', 'Postgraduate'],
                           ARRAY['secundaria', 'grado', 'posgrado'])                AS estudios_familia,
            staging.mapear(r.distance_from_home, 'Distance_from_Home',
                           ARRAY['Near', 'Moderate', 'Far'],
                           ARRAY['cerca', 'media', 'lejos'])                        AS distancia_casa,
            staging.mapear(r.gender, 'Gender',
                           ARRAY['Female', 'Male'], ARRAY['mujer', 'hombre'])       AS genero,
            LEAST(staging.a_entero(r.exam_score), 100)                              AS nota_examen
        FROM staging.spf_raw r
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

    INSERT INTO staging.carga (origen, filas_leidas, filas_cargadas, notas_recortadas)
    VALUES (origen, v_leidas, v_cargadas, v_recortes);

    -- Minimizar copias de datos individuales: la tabla cruda se vacía.
    TRUNCATE staging.spf_raw RESTART IDENTITY;

    RAISE NOTICE 'Carga completada: % alumnos, % notas recortadas a 100', v_cargadas, v_recortes;
END;
$$;
