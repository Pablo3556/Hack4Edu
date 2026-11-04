#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Arranque del contenedor único: PostgreSQL (pgvector) + aplicación.
#   1. Arranca PostgreSQL con el entrypoint oficial (crea roles y pgvector la primera vez).
#   2. Cuando acepta conexiones, arranca la aplicación. Su Flyway aplica las migraciones.
#   3. Opcional (CARGAR_DATOS=true): si la base no tiene datos, carga el dataset y reindexa el asistente;
#      si no tiene profesorado, carga el profesorado sintético.
# Si un proceso se cae, el contenedor termina para que Docker lo reinicie.
# ---------------------------------------------------------------------
set -uo pipefail

for v in POSTGRES_PASSWORD ESCUELA_ADMIN_PASSWORD APP_LECTOR_PASSWORD APP_INGESTA_PASSWORD DOCENTE_PASSWORD DIRECCION_PASSWORD ADMINISTRACION_PASSWORD; do
  if [ -z "${!v:-}" ]; then
    echo "Falta la variable $v (defínela en .env)" >&2
    exit 1
  fi
done

export POSTGRES_DB="${POSTGRES_DB:-escuela}" POSTGRES_USER="${POSTGRES_USER:-postgres}"
RAIZ=/opt/hack4edu

# ---- 1. PostgreSQL ---------------------------------------------------
docker-entrypoint.sh postgres &
PG=$!

until pg_isready -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -q; do
  if ! kill -0 "$PG" 2>/dev/null; then
    echo "PostgreSQL no ha arrancado" >&2
    exit 1
  fi
  sleep 1
done
echo "[hack4edu] PostgreSQL listo"

# ---- 2. Aplicación ---------------------------------------------------
gosu app java ${JAVA_OPTS:-} -Dquarkus.http.host=0.0.0.0 \
  -Djava.util.logging.manager=org.jboss.logmanager.LogManager -jar /app/quarkus-run.jar &
APP=$!

# ---- 3. Datos de demostración (solo si la base está vacía) -----------
cargar_datos() {
  # La API exige usuario: se usa el de dirección (HTTP Basic)
  local cred="direccion:${DIRECCION_PASSWORD}"
  until curl -sf -o /dev/null -u "$cred" http://127.0.0.1:8080/api/datos/ambitos; do sleep 2; done
  export PSQL_URL="postgresql://escuela_admin:${ESCUELA_ADMIN_PASSWORD}@127.0.0.1:5432/${POSTGRES_DB}"
  n=$(psql "$PSQL_URL" -tAc "SELECT count(*) FROM staging.carga WHERE tipo = 'kaggle'" 2>/dev/null || echo "?")
  if [ "$n" = "0" ]; then
    echo "[hack4edu] Cargando el dataset de demostración…"
    (cd "$RAIZ" && bash scripts/cargar_kaggle.sh datos/StudentPerformanceFactors.csv >/dev/null) \
      && echo "[hack4edu] Dataset cargado" || { echo "[hack4edu] La carga del dataset ha fallado" >&2; return; }
    curl -sf -X POST --max-time 1800 -u "$cred" http://127.0.0.1:8080/api/rag/ingesta >/dev/null \
      && echo "[hack4edu] Asistente indexado" \
      || echo "[hack4edu] No se pudo indexar el asistente (¿Ollama accesible en \$OLLAMA_URL con los modelos descargados?). Reintenta: POST /api/rag/ingesta"
  else
    echo "[hack4edu] La base ya tiene datos; no se carga el dataset"
  fi
  # Profesorado sintético: también en bases ya existentes que aún no lo tienen
  np=$(psql "$PSQL_URL" -tAc "SELECT count(*) FROM staging.profesor" 2>/dev/null || echo "?")
  if [ "$np" = "0" ]; then
    (cd "$RAIZ" && bash scripts/cargar_profesorado.sh >/dev/null) \
      && echo "[hack4edu] Profesorado cargado" || echo "[hack4edu] La carga del profesorado ha fallado" >&2
  fi
}
if [ "${CARGAR_DATOS:-false}" = "true" ]; then
  cargar_datos &
  CARGA=$!
fi

# ---- Parada ordenada: primero la app y después PostgreSQL ------------
parar() {
  kill -TERM "$APP" 2>/dev/null; [ -n "${CARGA:-}" ] && kill "$CARGA" 2>/dev/null
  wait "$APP" 2>/dev/null
  kill -INT "$PG" 2>/dev/null   # apagado rápido de PostgreSQL
  wait "$PG" 2>/dev/null
}
trap 'parar; exit 0' TERM INT

wait -n "$PG" "$APP"
echo "[hack4edu] Un proceso ha terminado; se detiene el contenedor" >&2
parar
exit 1
