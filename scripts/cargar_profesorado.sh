#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Carga el profesorado y sus asignaciones a grupos en la base de datos.
#
#   ./scripts/cargar_profesorado.sh [profesorado.csv] [asignaciones_docentes.csv]
#
# Por defecto usa datos/profesorado.csv y datos/asignaciones_docentes.csv (sintéticos,
# generados con scripts/generar_profesorado.py) y el contenedor "app" de docker compose.
# Para otra instancia, define PSQL_URL (como escuela_admin).
# Es idempotente: cada ejecución sustituye el profesorado anterior.
# ---------------------------------------------------------------------
set -euo pipefail

PROF="${1:-datos/profesorado.csv}"
ASIG="${2:-datos/asignaciones_docentes.csv}"

comprobar() {  # archivo cabecera-esperada
  if [[ ! -f "$1" ]]; then
    echo "No encuentro $1 (genéralo con: python3 scripts/generar_profesorado.py)" >&2
    exit 1
  fi
  local cab
  cab=$(head -n 1 "$1" | sed $'1s/^\xEF\xBB\xBF//' | tr -d '\r')
  if [[ "$cab" != "$2" ]]; then
    echo "La cabecera de $1 no coincide con la esperada:" >&2
    echo "  esperada: $2" >&2
    echo "  recibida: $cab" >&2
    exit 1
  fi
}

COL_PROF="codigo,centro_id,anios_experiencia,nivel_formacion,horas_formacion_anio,tipo_contrato"
COL_ASIG="codigo_profesor,centro_id,grupo,asignatura,horas_semana,tutor"
comprobar "$PROF" "$COL_PROF"
comprobar "$ASIG" "$COL_ASIG"

if [[ -n "${PSQL_URL:-}" ]]; then
  PSQL=(psql "$PSQL_URL")
else
  PSQL=(docker compose exec -T app psql -U escuela_admin -d escuela)
fi

# Cada CSV llega por la entrada estándar de su propia llamada; la transformación valida
# ambos juntos y, si algo falla, no toca el profesorado que ya hubiera.
"${PSQL[@]}" -v ON_ERROR_STOP=1 -q \
  -c "TRUNCATE staging.profesor_raw, staging.asignacion_raw RESTART IDENTITY" \
  -c "\\copy staging.profesor_raw ($COL_PROF) FROM pstdin WITH (FORMAT csv, HEADER true)" < "$PROF"
"${PSQL[@]}" -v ON_ERROR_STOP=1 -q \
  -c "\\copy staging.asignacion_raw ($COL_ASIG) FROM pstdin WITH (FORMAT csv, HEADER true)" < "$ASIG"
"${PSQL[@]}" -v ON_ERROR_STOP=1 -c "CALL staging.transformar_profesorado()"
