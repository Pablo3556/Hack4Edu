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

## Asistente: elegir el modelo

El usuario puede elegir con qué modelo responde el asistente. Todos usan las mismas
instrucciones, el mismo RAG y el mismo modelo de embeddings (`embeddinggemma`), así que
cambiar de modelo no obliga a reindexar.

| `modelo` | Modelo de Ollama | Para qué |
|---|---|---|
| `rapido` | `llama3.2:1b` | Responde antes, con menos detalle |
| `equilibrado` (por defecto) | `llama3.2:3b` | Recomendado para la demo |
| `preciso` | `qwen3.5:4b` | Más cuidado, más lento (~5,5 GB) |

```bash
ollama pull llama3.2:1b && ollama pull llama3.2:3b && ollama pull qwen3.5:4b && ollama pull embeddinggemma

# Qué modelos hay y cuáles están descargados
curl localhost:8080/api/modelos

# Preguntar con un modelo concreto ("modelo" es opcional)
curl -X POST localhost:8080/api/asistente -H 'Content-Type: application/json' \
     -d '{"pregunta": "¿Qué deberíamos priorizar en el Centro Demo 03?", "modelo": "preciso"}'
```

Los modelos concretos se cambian sin tocar código con `OLLAMA_MODELO_RAPIDO`,
`OLLAMA_CHAT_MODEL` (equilibrado) y `OLLAMA_MODELO_PRECISO`.

## Análisis y evolución (docentes)

Pestaña **Análisis y evolución** de la web. Tiene tres partes:

**A · Importar datos del grupo.** El docente sube un CSV con notas y asistencia de su grupo para un
periodo (trimestre). El proceso tiene dos fases:

1. *Validación* (`POST /api/ingesta`): se lee el CSV (separador `,` `;` o tabulador; UTF-8 o Windows-1252),
   se detectan las columnas aunque tengan otros nombres («Nota final», «Asistencia (%)», «Apoyo familiar»…)
   y se muestra una vista previa: filas, registros válidos, campos vacíos, errores de formato, valores fuera
   de rango, columnas detectadas y una muestra. El docente puede corregir la correspondencia de columnas
   (`PUT /api/ingesta/{token}/mapeo`). **No se guarda nada.**
2. *Confirmación* (`POST /api/ingesta/{token}/confirmar`): solo si no hay errores. Los datos pasan por la misma
   transformación que el CSV de Kaggle (`staging.spf_tipar`) y quedan en el histórico del grupo.

Obligatorias: *Nota final* y *Asistencia (%)*. Las columnas identificativas (nombre, DNI, correo…) y las
sensibles (dificultades de aprendizaje…) se ignoran siempre. Hay una plantilla en `GET /api/ingesta/plantilla`
y archivos de ejemplo en `datos/ejemplos/`.

**B · Evolución del grupo** (`GET /api/evolucion?centro=3&grupo=10`). Indicadores por periodo y alertas
tempranas **agregadas**: bajadas de asistencia o nota frente al periodo anterior, tendencias de 3 periodos,
aumento de la brecha entre grupos y comparación con el conjunto de centros. Solo hay alerta si hay dos
periodos con ≥ 10 registros y la diferencia supera un umbral práctico y se distingue del azar (|z| ≥ 1,96).

**C · Simulador «¿qué pasaría si…?»** (`GET /api/simulador?…&factor=tutorias&desde=2&hacia=3&porcentaje=100`).
Aplica la asociación histórica entre un factor accionable y la nota a un reparto distinto del alumnado,
con intervalo de confianza. Si los datos no permiten estimar (niveles ocultos por el umbral o asociación
indistinguible de cero), lo dice en vez de inventar un resultado.

> El dataset de Kaggle es una foto fija sintética: **no hay predicciones individuales ni etiquetas de
> riesgo**. Todo se calcula sobre medias de grupos de al menos 10 registros.

### Usuarios y seguridad

| Usuario | Contraseña (`.env`) | Puede |
|---|---|---|
| `docente` | `DOCENTE_PASSWORD` | importar CSV y ver sus importaciones |
| `direccion` | `DIRECCION_PASSWORD` | lo mismo y ver las importaciones de todo el profesorado |

- La app usa dos conexiones: `app_lector` (dashboard, solo lectura de `analitica`) y `app_ingesta`
  (solo `INSERT` en `staging.spf_raw` y `EXECUTE` de los procedimientos de ingesta).
- Límites: 2 MB, 5.000 filas y 60 columnas por archivo. El archivo no se guarda: la vista previa vive en
  memoria y caduca a los 20 minutos.
- Cada intento queda auditado en `staging.carga` (usuario, archivo, fecha, recuentos, estado y resultado).
- Las peticiones que modifican datos exigen la cabecera `X-Hack4Edu: 1` (protección CSRF).

### Base de datos ya creada antes de V7

El rol `app_ingesta` necesita superusuario, así que en un volumen existente hay que crearlo una vez,
**antes** de arrancar la app (si no, la migración V7 se detiene con un mensaje que lo explica):

```bash
docker compose up -d db
docker compose exec db bash /docker-entrypoint-initdb.d/01-rol-ingesta.sh
```
