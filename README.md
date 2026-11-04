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
```

Detalles en [db/README.md](db/README.md).
