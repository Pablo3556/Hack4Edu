-- =====================================================================
-- V1 · Esquemas
--   staging   → datos a nivel de alumno. NUNCA accesible para la app.
--   analitica → catálogos y vistas agregadas. Lo único que lee la app.
--   ia        → tablas de la app (embeddings de LangChain4j / pgvector).
-- Se ejecuta como escuela_admin (propietario de la base de datos).
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS staging;
CREATE SCHEMA IF NOT EXISTS analitica;
CREATE SCHEMA IF NOT EXISTS ia;

COMMENT ON SCHEMA staging   IS 'Datos individuales (crudos y tipados). Acceso solo para escuela_admin.';
COMMENT ON SCHEMA analitica IS 'Catálogos y vistas agregadas con umbral mínimo de grupo. Lectura para app_lector.';
COMMENT ON SCHEMA ia        IS 'Tablas propias de la aplicación (embeddings de fichas).';

-- Nadie salvo el propietario puede usar staging ni crear objetos en public.
REVOKE ALL ON SCHEMA staging FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
