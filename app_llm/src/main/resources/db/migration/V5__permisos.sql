-- =====================================================================
-- V5 · Permisos de la aplicación
--   app_lector (rol con el que se conecta Quarkus):
--     · analitica → solo SELECT (tablas de catálogo y vistas agregadas)
--     · ia        → puede crear y escribir sus tablas (embeddings)
--     · staging   → sin acceso de ningún tipo
--   El rol se crea en db/init/00-roles.sh (requiere superusuario).
-- =====================================================================

REVOKE ALL ON ALL TABLES    IN SCHEMA staging FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA staging FROM PUBLIC;
REVOKE ALL ON ALL PROCEDURES IN SCHEMA staging FROM PUBLIC;
REVOKE ALL ON SCHEMA staging FROM app_lector;

GRANT USAGE ON SCHEMA analitica TO app_lector;
GRANT SELECT ON ALL TABLES IN SCHEMA analitica TO app_lector;
GRANT EXECUTE ON FUNCTION analitica.k_min() TO app_lector;
-- Las vistas que se creen en migraciones futuras también serán legibles
ALTER DEFAULT PRIVILEGES IN SCHEMA analitica GRANT SELECT ON TABLES TO app_lector;

GRANT USAGE, CREATE ON SCHEMA ia TO app_lector;
