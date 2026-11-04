#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Se ejecuta UNA sola vez, cuando el volumen de PostgreSQL está vacío.
# Crea los roles y la extensión pgvector (ambas cosas requieren superusuario).
#   escuela_admin → propietario de la BD; lo usa Flyway y el script de carga.
#   app_lector    → lo usa Quarkus; solo ve analitica (y escribe en ia).
# ---------------------------------------------------------------------
set -euo pipefail

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v admin_pw="$ESCUELA_ADMIN_PASSWORD" \
     -v app_pw="$APP_LECTOR_PASSWORD" \
     -v db="$POSTGRES_DB" <<'EOSQL'
CREATE ROLE escuela_admin LOGIN PASSWORD :'admin_pw';
CREATE ROLE app_lector    LOGIN PASSWORD :'app_pw' NOSUPERUSER NOCREATEDB NOCREATEROLE;

ALTER DATABASE :"db" OWNER TO escuela_admin;
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE :"db" TO app_lector;

-- pgvector para los embeddings de LangChain4j
CREATE EXTENSION IF NOT EXISTS vector;

-- Quarkus encuentra sus tablas sin cualificar el esquema
ALTER ROLE app_lector SET search_path = analitica, ia, public;
EOSQL
