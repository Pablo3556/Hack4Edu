-- =====================================================================
-- V8 · Seguimiento de medidas
--   Medidas de mejora que un centro o grupo decide aplicar ("tutoría entre
--   iguales en 2.º B"). NO contienen datos de alumnos: solo ámbito, factor,
--   indicador a vigilar, periodo de inicio y un texto corto.
--   Viven en "ia" (tablas propias de la app, escribibles por app_lector).
--   El efecto se mide siempre sobre vistas agregadas (vw_evolucion), así que
--   hereda el umbral mínimo de grupo.
-- =====================================================================

CREATE TABLE ia.medida (
    id             integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    centro_id      smallint NOT NULL REFERENCES analitica.centro (id),
    grupo_id       smallint REFERENCES analitica.grupo (id),
    factor         text REFERENCES analitica.factor (codigo),
    titulo         text NOT NULL CHECK (char_length(titulo) BETWEEN 3 AND 120),
    descripcion    text CHECK (char_length(descripcion) <= 500),
    indicador      text NOT NULL CHECK (indicador IN
                       ('nota_media', 'asistencia_media', 'pct_asistencia_baja', 'horas_estudio_media', 'tutorias_media')),
    periodo_inicio smallint NOT NULL REFERENCES analitica.periodo (id),
    estado         text NOT NULL DEFAULT 'en_curso' CHECK (estado IN ('en_curso', 'finalizada', 'descartada')),
    creada_por     text,
    creada         timestamptz NOT NULL DEFAULT now(),
    actualizada    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX medida_ambito_idx ON ia.medida (centro_id, grupo_id);

COMMENT ON TABLE ia.medida IS
  'Medidas de mejora registradas por docentes o dirección. Sin datos personales; su efecto se evalúa con vw_evolucion.';

GRANT SELECT, INSERT, UPDATE ON ia.medida TO app_lector;
