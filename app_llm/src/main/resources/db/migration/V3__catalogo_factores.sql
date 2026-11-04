-- =====================================================================
-- V3 · Catálogo de factores
--   tipo = 'accionable' → la escuela puede actuar sobre él (entra en la priorización)
--   tipo = 'contextual' → sirve para entender y vigilar la equidad, NUNCA para recomendar
--   accionabilidad (0–1) → cuánto margen real tiene el centro. Es una decisión de diseño
--   documentada y editable, no un dato del dataset.
-- =====================================================================

-- Umbral mínimo de alumnos para publicar cualquier cifra agregada
CREATE FUNCTION analitica.k_min()
RETURNS integer
LANGUAGE sql IMMUTABLE AS 'SELECT 10';
COMMENT ON FUNCTION analitica.k_min() IS 'Tamaño mínimo de grupo para mostrar un agregado (anti-reidentificación).';

CREATE TABLE analitica.factor (
    codigo           text PRIMARY KEY,
    nombre           text NOT NULL,
    dimension_reto   text NOT NULL CHECK (dimension_reto IN
                         ('asistencia', 'aprendizaje', 'docencia', 'recursos', 'participacion', 'contexto')),
    tipo             text NOT NULL CHECK (tipo IN ('accionable', 'contextual')),
    accionabilidad   numeric(3, 2) CHECK (accionabilidad BETWEEN 0 AND 1),
    palanca          text,      -- línea de actuación a explorar (no es una prescripción)
    descripcion      text NOT NULL,
    CHECK ((tipo = 'accionable') = (accionabilidad IS NOT NULL))
);

INSERT INTO analitica.factor (codigo, nombre, dimension_reto, tipo, accionabilidad, palanca, descripcion) VALUES
  ('asistencia',           'Asistencia',                 'asistencia',    'accionable', 0.90,
   'Seguimiento temprano del absentismo con tutoría y familias',
   'Porcentaje de clases a las que asiste el alumnado (tramos).'),
  ('tutorias',             'Sesiones de tutoría',        'docencia',      'accionable', 0.90,
   'Ampliar tutorías o refuerzo en los grupos con menos sesiones',
   'Sesiones de tutoría o refuerzo al mes.'),
  ('acceso_recursos',      'Acceso a recursos',          'recursos',      'accionable', 0.80,
   'Préstamo de materiales, biblioteca y espacios de estudio',
   'Nivel de acceso a recursos educativos.'),
  ('calidad_docente',      'Calidad docente percibida',  'docencia',      'accionable', 0.70,
   'Formación del profesorado y acompañamiento entre docentes',
   'Valoración de la calidad docente (tiene valores ausentes en origen).'),
  ('horas_estudio',        'Horas de estudio',           'aprendizaje',   'accionable', 0.60,
   'Planes de estudio guiado y técnicas de estudio',
   'Horas de estudio semanales (tramos).'),
  ('extraescolares',       'Actividades extraescolares', 'participacion', 'accionable', 0.70,
   'Oferta de actividades en el propio centro',
   'Participa o no en actividades extraescolares.'),
  ('implicacion_familiar', 'Implicación familiar',       'participacion', 'accionable', 0.50,
   'Canales de participación de las familias (escuela de familias, reuniones)',
   'Nivel de implicación de la familia.'),
  ('motivacion',           'Motivación',                 'aprendizaje',   'accionable', 0.50,
   'Metodologías activas y orientación',
   'Nivel de motivación del alumnado.'),
  ('acceso_internet',      'Acceso a internet',          'recursos',      'accionable', 0.50,
   'Préstamo de dispositivos y conectividad',
   'Dispone o no de acceso a internet.'),
  ('influencia_pares',     'Influencia de los compañeros','participacion', 'accionable', 0.40,
   'Programas de convivencia y mentoría entre iguales',
   'Influencia de los compañeros: negativa, neutra o positiva.'),
  ('actividad_fisica',     'Actividad física',           'participacion', 'accionable', 0.40,
   'Recreos activos y deporte escolar',
   'Horas de actividad física semanales (tramos).'),
  ('horas_sueno',          'Horas de sueño',             'contexto',      'accionable', 0.20,
   'Sensibilización a familias sobre descanso',
   'Horas de sueño por noche (tramos). Margen de actuación escaso.'),
  ('renta_familiar',       'Renta familiar',             'contexto',      'contextual', NULL, NULL,
   'Nivel de ingresos de la familia.'),
  ('estudios_familia',     'Estudios de la familia',     'contexto',      'contextual', NULL, NULL,
   'Máximo nivel de estudios de la familia (tiene valores ausentes en origen).'),
  ('distancia_casa',       'Distancia al centro',        'contexto',      'contextual', NULL, NULL,
   'Distancia entre casa y centro (tiene valores ausentes en origen).'),
  ('tipo_centro',          'Titularidad del centro',     'contexto',      'contextual', NULL, NULL,
   'Centro público o privado.'),
  ('genero',               'Género',                     'contexto',      'contextual', NULL, NULL,
   'Solo para vigilar brechas de equidad.');

-- Niveles de cada factor, en su orden natural
CREATE TABLE analitica.factor_nivel (
    factor   text     NOT NULL REFERENCES analitica.factor (codigo),
    orden    smallint NOT NULL,
    etiqueta text     NOT NULL,
    PRIMARY KEY (factor, orden)
);

INSERT INTO analitica.factor_nivel (factor, orden, etiqueta) VALUES
  ('asistencia', 1, '<70 %'), ('asistencia', 2, '70–79 %'), ('asistencia', 3, '80–89 %'), ('asistencia', 4, '≥90 %'),
  ('tutorias', 1, '0'), ('tutorias', 2, '1'), ('tutorias', 3, '2'), ('tutorias', 4, '3 o más'),
  ('horas_estudio', 1, '<10 h'), ('horas_estudio', 2, '10–19 h'), ('horas_estudio', 3, '20–29 h'), ('horas_estudio', 4, '≥30 h'),
  ('horas_sueno', 1, '<6 h'), ('horas_sueno', 2, '6–7 h'), ('horas_sueno', 3, '≥8 h'),
  ('actividad_fisica', 1, '0–1 h'), ('actividad_fisica', 2, '2–3 h'), ('actividad_fisica', 3, '≥4 h'),
  ('acceso_recursos', 1, 'bajo'), ('acceso_recursos', 2, 'medio'), ('acceso_recursos', 3, 'alto'),
  ('calidad_docente', 1, 'baja'), ('calidad_docente', 2, 'media'), ('calidad_docente', 3, 'alta'),
  ('implicacion_familiar', 1, 'baja'), ('implicacion_familiar', 2, 'media'), ('implicacion_familiar', 3, 'alta'),
  ('motivacion', 1, 'baja'), ('motivacion', 2, 'media'), ('motivacion', 3, 'alta'),
  ('renta_familiar', 1, 'baja'), ('renta_familiar', 2, 'media'), ('renta_familiar', 3, 'alta'),
  ('extraescolares', 1, 'no'), ('extraescolares', 2, 'sí'),
  ('acceso_internet', 1, 'no'), ('acceso_internet', 2, 'sí'),
  ('influencia_pares', 1, 'negativa'), ('influencia_pares', 2, 'neutra'), ('influencia_pares', 3, 'positiva'),
  ('estudios_familia', 1, 'secundaria'), ('estudios_familia', 2, 'grado'), ('estudios_familia', 3, 'posgrado'),
  ('distancia_casa', 1, 'cerca'), ('distancia_casa', 2, 'media'), ('distancia_casa', 3, 'lejos'),
  ('tipo_centro', 1, 'público'), ('tipo_centro', 2, 'privado'),
  ('genero', 1, 'mujer'), ('genero', 2, 'hombre');

-- ---------------------------------------------------------------------
-- Vista interna en formato largo: una fila por alumno y factor.
-- Vive en staging (contiene filas individuales); la app no la ve.
-- Ojo: PostgreSQL comprueba el permiso EXECUTE de las funciones con el
-- usuario que consulta la vista, no con su propietario. Por eso aquí solo
-- se usan funciones del sistema y expresiones CASE, nunca funciones de staging.
-- ---------------------------------------------------------------------
CREATE VIEW staging.vw_spf_largo AS
SELECT a.id AS alumno_id, a.centro_id, a.grupo_id, f.factor, f.orden, a.nota_examen, a.asistencia
FROM staging.alumno_spf a
CROSS JOIN LATERAL (VALUES
    ('asistencia',           (width_bucket(a.asistencia,       ARRAY[70, 80, 90]) + 1)::smallint),
    ('tutorias',             (width_bucket(a.tutorias,         ARRAY[1, 2, 3])    + 1)::smallint),
    ('horas_estudio',        (width_bucket(a.horas_estudio,    ARRAY[10, 20, 30]) + 1)::smallint),
    ('horas_sueno',          (width_bucket(a.horas_sueno,      ARRAY[6, 8])       + 1)::smallint),
    ('actividad_fisica',     (width_bucket(a.actividad_fisica, ARRAY[2, 4])       + 1)::smallint),
    ('acceso_recursos',      CASE a.acceso_recursos WHEN 'bajo' THEN 1 WHEN 'medio' THEN 2 WHEN 'alto' THEN 3 END::smallint),
    ('calidad_docente',      CASE a.calidad_docente WHEN 'bajo' THEN 1 WHEN 'medio' THEN 2 WHEN 'alto' THEN 3 END::smallint),
    ('implicacion_familiar', CASE a.implicacion_familiar WHEN 'bajo' THEN 1 WHEN 'medio' THEN 2 WHEN 'alto' THEN 3 END::smallint),
    ('motivacion',           CASE a.motivacion WHEN 'bajo' THEN 1 WHEN 'medio' THEN 2 WHEN 'alto' THEN 3 END::smallint),
    ('renta_familiar',       CASE a.renta_familiar WHEN 'bajo' THEN 1 WHEN 'medio' THEN 2 WHEN 'alto' THEN 3 END::smallint),
    ('extraescolares',       CASE a.extraescolares  WHEN false THEN 1 WHEN true THEN 2 END::smallint),
    ('acceso_internet',      CASE a.acceso_internet WHEN false THEN 1 WHEN true THEN 2 END::smallint),
    ('influencia_pares',     CASE a.influencia_pares WHEN 'negativa' THEN 1 WHEN 'neutra' THEN 2 WHEN 'positiva' THEN 3 END::smallint),
    ('estudios_familia',     CASE a.estudios_familia WHEN 'secundaria' THEN 1 WHEN 'grado' THEN 2 WHEN 'posgrado' THEN 3 END::smallint),
    ('distancia_casa',       CASE a.distancia_casa  WHEN 'cerca' THEN 1 WHEN 'media' THEN 2 WHEN 'lejos' THEN 3 END::smallint),
    ('tipo_centro',          CASE a.tipo_centro     WHEN 'publico' THEN 1 WHEN 'privado' THEN 2 END::smallint),
    ('genero',               CASE a.genero          WHEN 'mujer' THEN 1 WHEN 'hombre' THEN 2 END::smallint)
) AS f(factor, orden)
WHERE f.orden IS NOT NULL;   -- los valores ausentes no cuentan en ningún nivel
