-- =====================================================================
-- V6 · Almacén de vectores del RAG (LangChain4j + pgvector)
--   La tabla la crea Flyway (escuela_admin) y no la app, porque app_lector
--   tiene "analitica" primero en su search_path y allí no puede crear nada.
--   Columnas = las que espera PgVectorEmbeddingStore de LangChain4j.
--   "vector" sin dimensión: admite cualquier modelo de embeddings (llama3.2:3b
--   produce 3072 dimensiones, por encima del límite de 2000 de los índices
--   HNSW/IVFFlat; con unos cientos de fichas la búsqueda exacta es inmediata).
--   Solo guarda fichas de datos AGREGADOS de analitica, nunca filas de alumnos.
-- =====================================================================

CREATE TABLE ia.embeddings (
    embedding_id uuid PRIMARY KEY,
    embedding    public.vector NOT NULL,
    text         text NULL,
    metadata     json NULL
);

COMMENT ON TABLE ia.embeddings IS
  'Fichas de texto generadas a partir de las vistas agregadas de analitica, con su embedding (RAG).';

GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON ia.embeddings TO app_lector;
