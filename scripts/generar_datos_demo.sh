#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Genera DATOS DE DEMOSTRACIÓN (sintéticos) con evolución por periodos.
#
#   ./scripts/generar_datos_demo.sh            crea 4 trimestres (2025-26 T1…T3 y 2026-27 T1) para los 36 grupos
#   ./scripts/generar_datos_demo.sh limpiar    los borra y deja solo la línea base de Kaggle
#
# Requisitos: base de datos levantada y ./scripts/cargar_kaggle.sh ejecutado (la línea base).
# Cada grupo recibe hasta 150 registros por periodo, muestreados de su línea base, y una "historia"
# según su número de grupo (id % 6):
#   0 estable · 1 asistencia a la baja · 2 notas al alza · 3 notas a la baja
#   4 asistencia al alza · 5 estable y caída de notas en el último trimestre
# Pasan por staging.abrir_carga + staging.transformar_ingesta, el mismo camino que la
# importación de docentes (auditoría incluida, usuario «demo»). Es repetible: volver a
# ejecutarlo sustituye los datos anteriores. Después, reindexad el asistente:
#   curl -u "direccion:$DIRECCION_PASSWORD" -X POST localhost:8080/api/rag/ingesta
# ---------------------------------------------------------------------
set -euo pipefail

if [[ -n "${PSQL_URL:-}" ]]; then
  PSQL=(psql "$PSQL_URL")
else
  PSQL=(docker compose exec -T app psql -U escuela_admin -d escuela)
fi

if [[ "${1:-}" == "limpiar" ]]; then
  "${PSQL[@]}" -v ON_ERROR_STOP=1 --single-transaction <<'EOSQL'
DELETE FROM staging.observacion WHERE carga_id IN (SELECT id FROM staging.carga WHERE usuario = 'demo');
DELETE FROM staging.carga WHERE usuario = 'demo';
DO $$
DECLARE g smallint;
BEGIN
    FOR g IN SELECT id FROM analitica.grupo LOOP
        CALL staging.refrescar_foto_grupo(g);
    END LOOP;
END;
$$;
SELECT periodo_id, count(*) AS registros FROM staging.observacion GROUP BY 1 ORDER BY 1;
EOSQL
  exit 0
fi

"${PSQL[@]}" -v ON_ERROR_STOP=1 <<'EOSQL'
SELECT setseed(0.42);   -- los mismos datos en cada ejecución

CREATE FUNCTION pg_temp.mapa(valor text, desde text[], hacia text[]) RETURNS text
LANGUAGE sql IMMUTABLE AS 'SELECT hacia[array_position(desde, valor)]';

DO $$
DECLARE
    gr    record;
    p     integer;
    t     integer;
    n     integer;
    carga integer;
    esc   integer;
    da    numeric;   -- desplazamiento de la asistencia
    dn    numeric;   -- desplazamiento de la nota
BEGIN
    IF NOT EXISTS (SELECT 1 FROM staging.observacion WHERE periodo_id = 0) THEN
        RAISE EXCEPTION 'No hay línea base: ejecuta antes ./scripts/cargar_kaggle.sh';
    END IF;

    FOR gr IN SELECT g.id, g.nombre, c.nombre AS centro
              FROM analitica.grupo g JOIN analitica.centro c ON c.id = g.centro_id ORDER BY g.id LOOP
        esc := gr.id % 6;
        FOR p IN 1..4 LOOP
            t := p - 1;
            da := CASE esc WHEN 1 THEN -5 * t WHEN 2 THEN t WHEN 4 THEN 4 * t ELSE 0 END;
            dn := CASE esc WHEN 1 THEN -t WHEN 2 THEN 2 * t WHEN 3 THEN -2 * t WHEN 4 THEN t
                           WHEN 5 THEN CASE WHEN p = 4 THEN -5 ELSE 0 END ELSE 0 END;
            SELECT least(150, count(*)) INTO n FROM staging.observacion WHERE grupo_id = gr.id AND periodo_id = 0;

            carga := staging.abrir_carga('demo',
                format('demo_%s_%s_periodo%s.csv', replace(gr.centro, ' ', ''), replace(gr.nombre, ' ', ''), p),
                gr.id::smallint, p::smallint, n, n, 0, 'pendiente',
                'Datos de demostración generados (sintéticos), historia ' || esc);

            INSERT INTO staging.spf_raw (carga_id, hours_studied, attendance, parental_involvement,
                access_to_resources, extracurricular_activities, sleep_hours, previous_scores, motivation_level,
                internet_access, tutoring_sessions, family_income, teacher_quality, peer_influence,
                physical_activity, parental_education_level, distance_from_home, gender, exam_score)
            SELECT carga, o.horas_estudio::text,
                   greatest(0, least(100, round(o.asistencia + da)))::text,
                   pg_temp.mapa(o.implicacion_familiar::text, '{bajo,medio,alto}', '{Low,Medium,High}'),
                   pg_temp.mapa(o.acceso_recursos::text,      '{bajo,medio,alto}', '{Low,Medium,High}'),
                   CASE o.extraescolares WHEN true THEN 'Yes' WHEN false THEN 'No' END,
                   o.horas_sueno::text, o.nota_previa::text,
                   pg_temp.mapa(o.motivacion::text,           '{bajo,medio,alto}', '{Low,Medium,High}'),
                   CASE o.acceso_internet WHEN true THEN 'Yes' WHEN false THEN 'No' END,
                   o.tutorias::text,
                   pg_temp.mapa(o.renta_familiar::text,       '{bajo,medio,alto}', '{Low,Medium,High}'),
                   pg_temp.mapa(o.calidad_docente::text,      '{bajo,medio,alto}', '{Low,Medium,High}'),
                   pg_temp.mapa(o.influencia_pares,           '{negativa,neutra,positiva}', '{Negative,Neutral,Positive}'),
                   o.actividad_fisica::text,
                   pg_temp.mapa(o.estudios_familia,           '{secundaria,grado,posgrado}', '{High School,College,Postgraduate}'),
                   pg_temp.mapa(o.distancia_casa,             '{cerca,media,lejos}', '{Near,Moderate,Far}'),
                   pg_temp.mapa(o.genero,                     '{mujer,hombre}', '{Female,Male}'),
                   greatest(0, least(100, round(o.nota_examen + dn)))::text
            FROM (SELECT * FROM staging.observacion WHERE grupo_id = gr.id AND periodo_id = 0
                  ORDER BY random() LIMIT n) o;

            CALL staging.transformar_ingesta(carga);
        END LOOP;
    END LOOP;
END;
$$;

SELECT periodo_id, count(DISTINCT grupo_id) AS grupos, count(*) AS registros
FROM staging.observacion GROUP BY 1 ORDER BY 1;
EOSQL
echo "Listo. Reindexa el asistente con: curl -u \"direccion:\$DIRECCION_PASSWORD\" -X POST localhost:8080/api/rag/ingesta"
