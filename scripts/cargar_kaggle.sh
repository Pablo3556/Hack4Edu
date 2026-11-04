#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Carga StudentPerformanceFactors.csv en la base de datos.
#
#   ./scripts/cargar_kaggle.sh [ruta/al/StudentPerformanceFactors.csv]
#
# Por defecto usa el contenedor "app" de docker compose. Para cargar contra
# otra instancia, define PSQL_URL (como escuela_admin), p. ej.:
#   PSQL_URL=postgresql://escuela_admin:clave@localhost:5432/escuela ./scripts/cargar_kaggle.sh
#
# Es idempotente: cada ejecución sustituye los datos anteriores.
# ---------------------------------------------------------------------
set -euo pipefail

CSV="${1:-datos/StudentPerformanceFactors.csv}"

if [[ ! -f "$CSV" ]]; then
  echo "No encuentro $CSV" >&2
  echo "Descárgalo de https://www.kaggle.com/datasets/lainguyn123/student-performance-factors" >&2
  exit 1
fi

CABECERA_ESPERADA="Hours_Studied,Attendance,Parental_Involvement,Access_to_Resources,Extracurricular_Activities,Sleep_Hours,Previous_Scores,Motivation_Level,Internet_Access,Tutoring_Sessions,Family_Income,Teacher_Quality,School_Type,Peer_Influence,Physical_Activity,Learning_Disabilities,Parental_Education_Level,Distance_from_Home,Gender,Exam_Score"
CABECERA=$(head -n 1 "$CSV" | sed $'1s/^\xEF\xBB\xBF//' | tr -d '\r')
if [[ "$CABECERA" != "$CABECERA_ESPERADA" ]]; then
  echo "La cabecera del CSV no coincide con la esperada:" >&2
  echo "  esperada: $CABECERA_ESPERADA" >&2
  echo "  recibida: $CABECERA" >&2
  exit 1
fi

COLUMNAS="hours_studied, attendance, parental_involvement, access_to_resources, extracurricular_activities, sleep_hours, previous_scores, motivation_level, internet_access, tutoring_sessions, family_income, teacher_quality, school_type, peer_influence, physical_activity, learning_disabilities, parental_education_level, distance_from_home, gender, exam_score"

if [[ -n "${PSQL_URL:-}" ]]; then
  PSQL=(psql "$PSQL_URL")
else
  PSQL=(docker compose exec -T app psql -U escuela_admin -d escuela)
fi

"${PSQL[@]}" -v ON_ERROR_STOP=1 --single-transaction \
  -c "TRUNCATE staging.spf_raw RESTART IDENTITY" \
  -c "\\copy staging.spf_raw ($COLUMNAS) FROM pstdin WITH (FORMAT csv, HEADER true)" \
  -c "CALL staging.transformar_spf('$(basename "$CSV")')" \
  < "$CSV"

"${PSQL[@]}" -c "SELECT ambito, centro, n_alumnos, nota_media, asistencia_media
                 FROM analitica.vw_resumen WHERE ambito IN ('global', 'centro')
                 ORDER BY centro_id NULLS FIRST"
