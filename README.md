# Hack4Edu · La escuela que aprende de sus datos

RETO 13: convertir datos educativos en información comprensible y recomendaciones
priorizadas para docentes, equipos directivos y responsables educativos, con datos
agregados, sin decisiones automáticas sobre alumnos y con todo ejecutándose en local.

## Estructura

| Ruta | Contenido |
|---|---|
| `app_llm/` | Aplicación Quarkus (API REST, web, asistente con LangChain4j + Ollama) |
| `app_llm/src/main/resources/db/migration/` | Migraciones Flyway: esquema, vistas agregadas y permisos |
| `db/` | Inicialización de PostgreSQL (roles, pgvector) y [documentación de la base de datos](db/README.md) |
| `datos/` | Datasets de entrada (Student Performance Factors, Kaggle, CC0) |
| `scripts/` | Carga de datos |
| `docker-compose.yml` | PostgreSQL + pgvector y Flyway |

## Arranque rápido

```bash
cp .env.example .env                  # cambia las contraseñas
docker compose up -d db
docker compose run --rm migraciones
./scripts/cargar_kaggle.sh
ollama pull llama3.2:3b               # modelo de chat (Llama)
ollama pull embeddinggemma            # modelo de embeddings
cd app_llm && set -a && source ../.env && set +a && ./mvnw quarkus:dev
```

## Asistente RAG

Al arrancar, la app convierte las vistas agregadas de `analitica` en ~157 fichas de texto
(resumen, prioridades y brechas de equidad por ámbito, detalle de cada factor, metodología
y calidad de datos), calcula sus embeddings con Ollama y las guarda en `ia.embeddings` (pgvector).
Cada pregunta recupera las 6 fichas más parecidas (filtrando por centro/grupo si la pregunta
los nombra) y se las pasa a **Llama 3.2** como contexto. Ninguna ficha contiene datos de alumnos.

```bash
curl -s localhost:8080/api/asistente -H 'Content-Type: application/json' \
     -d '{"pregunta": "¿Qué deberíamos priorizar en el Centro Demo 03?"}'
curl -s localhost:8080/api/rag/estado            # nº de fichas indexadas
curl -s -X POST localhost:8080/api/rag/ingesta   # reindexar tras recargar el CSV
```

Modelos configurables con `OLLAMA_CHAT_MODEL`, `OLLAMA_EMBEDDING_MODEL` y `RAG_EMBEDDING_DIM`
(si cambias el de embeddings, reindexa). Ollama no publica un modelo de embeddings de Llama y
los modelos de chat Llama no sirven `/api/embed`, por eso los embeddings usan `embeddinggemma`
(multilingüe, 768 dimensiones).

Detalles en [db/README.md](db/README.md).
