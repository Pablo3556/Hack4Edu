#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Rol app_ingesta: lo usa Quarkus SOLO para la importación de CSV de docentes.
#   · INSERT en staging.spf_raw y EXECUTE de los procedimientos de ingesta
#     (los permisos concretos los da la migración V7).
#   · Sin lectura de analitica, sin escritura en tablas analíticas, sin
#     permisos administrativos.
#
# Se ejecuta solo al crear el volumen. Es idempotente: en una base de datos
# ya existente, ejecutadlo UNA vez (antes de arrancar la app con V7):
#   docker compose up -d db     # recrea el contenedor con APP_INGESTA_PASSWORD
#   docker compose exec db bash /docker-entrypoint-initdb.d/01-rol-ingesta.sh
# ---------------------------------------------------------------------
set -euo pipefail

psql -v ON_ERROR_STOP=1 \
     --username "${POSTGRES_USER:-postgres}" --dbname "${POSTGRES_DB:-escuela}" \
     -v ingesta_pw="${APP_INGESTA_PASSWORD:?define APP_INGESTA_PASSWORD}" <<'EOSQL'
SELECT 'CREATE ROLE app_ingesta LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_ingesta')
\gexec
ALTER ROLE app_ingesta PASSWORD :'ingesta_pw';
-- Ni siquiera ve el esquema public por defecto
ALTER ROLE app_ingesta SET search_path = pg_catalog;
EOSQL
