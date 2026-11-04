# Hack4Edu · La escuela que aprende de sus datos

RETO 13: convertir datos educativos en información comprensible y recomendaciones
priorizadas para docentes, equipos directivos y responsables educativos, con datos
agregados, sin decisiones automáticas sobre alumnos y con todo ejecutándose en local.

## Requisitos

| Herramienta | Para qué | Notas |
|---|---|---|
| [Docker](https://docs.docker.com/get-docker/) con Docker Compose v2 | Base de datos + aplicación | Docker Desktop en macOS/Windows |
| [Ollama](https://ollama.com/download) | Modelos de lenguaje del asistente | Se ejecuta en tu equipo, **no** dentro del contenedor |
| Java 21 | Solo para desarrollo local (`./mvnw`) | No hace falta para ejecutar con Docker |

Unos 4 GB de disco para los modelos básicos (más ~5,5 GB si descargas el modelo `preciso`).

## Arranque con un clic (macOS)

Con Docker Desktop y Ollama instalados, haz **doble clic en `Hack4Edu.command`**. El script abre Docker y
Ollama si están cerrados, crea `.env` con contraseñas aleatorias la primera vez, descarga los modelos que
falten, arranca el contenedor y abre el navegador. Al terminar muestra los usuarios `docente` y `direccion`
con sus contraseñas. Para pararlo: doble clic en `Parar Hack4Edu.command`.

¿Prefieres un icono para el Dock? Ejecuta una vez `bash scripts/crear_app_macos.sh`: crea `Hack4Edu.app`
en la carpeta del proyecto (no la muevas de ahí; arrástrala al Dock).

> La primera vez que abras el `.command`, macOS puede bloquearlo por ser de «un desarrollador no
> identificado»: clic derecho → **Abrir** → **Abrir**. Solo hace falta una vez.

## Cómo se ejecuta (paso a paso)

**1. Clona el repositorio**

```bash
git clone https://github.com/Pablo3556/Hack4Edu.git
cd Hack4Edu
```

**2. Crea el archivo `.env`** con las contraseñas (no se sube a Git):

```bash
cp .env.example .env
```

Cambia los valores `cambia-…`. Evita en las contraseñas los caracteres `@ : / ? # %`.

**3. Descarga los modelos en Ollama** (una sola vez, con Ollama abierto):

```bash
ollama pull llama3.2:3b          # chat (obligatorio)
ollama pull embeddinggemma       # embeddings del asistente (obligatorio)
ollama pull llama3.2:1b          # opcional: modelo «rápido»
ollama pull qwen3.5:4b           # opcional: modelo «preciso»
```

**4. Arranca la aplicación**

```bash
docker compose up -d --build
docker compose logs -f app       # sigue el arranque (Ctrl+C para salir de los logs)
```

Cuando aparezcan `[hack4edu] Dataset cargado` y `[hack4edu] Asistente indexado`, abre
**http://localhost:8080**.

La primera vez, si la base está vacía, el contenedor carga el dataset de demostración y reindexa el asistente
(`CARGAR_DATOS=true`; ponlo a `false` en `.env` para evitarlo). Si Ollama no está disponible en ese momento,
la app funciona igual y el asistente se indexa después con `curl -X POST localhost:8080/api/rag/ingesta`.

**5. (Opcional) Añade datos de evolución** para ver las pestañas de evolución, alertas y medidas con contenido:

```bash
bash scripts/generar_datos_demo.sh               # 4 trimestres sintéticos para los 36 grupos
curl -X POST localhost:8080/api/rag/ingesta      # reindexa el asistente con los datos nuevos
```

### Comprobar que funciona

```bash
curl -s localhost:8080/api/rag/estado            # nº de fichas indexadas (~157)
curl -s localhost:8080/api/modelos               # modelos disponibles y descargados
```

### Qué queda en marcha

| Qué | Dónde |
|---|---|
| Aplicación | http://localhost:8080 (puerto configurable con `APP_PUERTO`) |
| PostgreSQL | `127.0.0.1:5432`, solo desde este equipo (`DB_PUERTO`) |
| Datos | volumen `pgdata` (sobrevive a `docker compose down`) |
| Migraciones | las aplica la app al arrancar; a mano: `docker compose --profile migraciones run --rm migraciones` |
| Ollama | `OLLAMA_URL` (por defecto `http://host.docker.internal:11434`, el del equipo) |

Es un **único contenedor** con PostgreSQL 16 + pgvector y la aplicación. Ollama no va dentro de la imagen
porque los modelos pesan varios GB.

### Comandos útiles

```bash
docker compose logs -f app           # logs de la base de datos y de la aplicación
docker compose down                  # parar (conserva los datos)
docker compose down -v               # parar y borrar la base de datos
docker compose up -d --build         # reconstruir tras cambiar el código
```

> `scripts/cargar_kaggle.sh` **sustituye** la línea base y borra los trimestres sintéticos y las importaciones.
> Si lo ejecutas de nuevo, vuelve a lanzar `generar_datos_demo.sh`.

### Problemas frecuentes

| Síntoma | Solución |
|---|---|
| `define POSTGRES_PASSWORD en .env` al arrancar | Falta el archivo `.env`: `cp .env.example .env` |
| `port is already allocated` | El puerto 8080 o 5432 está ocupado: pon otro en `.env` (`APP_PUERTO=8081`, `DB_PUERTO=5433`) |
| «No se pudo indexar el asistente» en los logs | Ollama no está abierto o faltan modelos. Ábrelo, haz los `ollama pull` y ejecuta `curl -X POST localhost:8080/api/rag/ingesta` |
| En **Linux**, el contenedor no llega a Ollama | Ollama escucha solo en `127.0.0.1`. Arráncalo con `OLLAMA_HOST=0.0.0.0 ollama serve` (o configúralo en su servicio de systemd) |
| Cambié las contraseñas de `.env` y ya no conecta | Las contraseñas se fijan al crear el volumen. Para empezar de cero: `docker compose down -v` y vuelve a arrancar |

## Desarrollo local (sin construir la imagen)

La base de datos va en el contenedor y la aplicación se ejecuta en tu equipo con recarga en caliente:

```bash
cp .env.example .env
APP_PUERTO=8081 docker compose up -d --build     # base de datos en 127.0.0.1:5432 (la app del contenedor queda en 8081)
cd app_llm
set -a && source ../.env && set +a               # exporta las contraseñas
./mvnw quarkus:dev                                # → http://localhost:8080
./mvnw test                                       # tests (no necesitan base de datos)
```

## Estructura

| Ruta | Contenido |
|---|---|
| `Dockerfile` · `docker-compose.yml` · `docker/` | Contenedor único (PostgreSQL + aplicación) y su script de arranque |
| `app_llm/` | Aplicación Quarkus (API REST, web, asistente con LangChain4j + Ollama) |
| `app_llm/src/main/java/es/hack4edu/` | Código en **arquitectura hexagonal**, un paquete por módulo: ver [docs/arquitectura.md](docs/arquitectura.md) |
| `app_llm/src/main/resources/META-INF/resources/` | Interfaz web: `index.html`, `css/` y `js/` |
| `app_llm/src/main/resources/db/migration/` | Migraciones Flyway: esquema, vistas agregadas y permisos |
| `db/` | Inicialización de PostgreSQL (roles, pgvector) y [documentación de la base de datos](db/README.md) |
| `datos/` | Datasets de entrada (Student Performance Factors, Kaggle, CC0) y CSV de ejemplo |
| `scripts/` | Carga de datos |
| `docs/` | Documentación de arquitectura |

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

## Resumen semanal, panel de dirección y seguimiento de medidas

Tres secciones de la web (pestañas junto a «Análisis y evolución»):

| Pestaña | Endpoint | Qué hace |
|---|---|---|
| Resumen semanal | `GET /api/resumen-semanal?centro=&grupo=` | Tres prioridades del grupo (o centro) en lenguaje llano, con la línea a explorar, la fuente de cada cifra (vista y ámbito), señales de evolución y medidas en curso. Es determinista: no pasa por el LLM, así que cada afirmación es verificable. Botón «Copiar resumen» para la reunión. |
| Panel de dirección | `GET /api/direccion/semaforo?centro=` | Semáforo verde/ámbar/rojo por grupo frente al conjunto de centros (nota, asistencia, % asistencia baja, acceso a recursos, tutorías) y brechas de equidad del centro. Los umbrales están en `direccion/dominio/Semaforo.java` y se muestran en pantalla. |
| Seguimiento de medidas | `GET/POST /api/medidas`, `PUT /api/medidas/{id}/estado` | Se registra una medida («tutoría entre iguales en 2.º B») con el indicador a vigilar y el periodo de inicio. Al importar datos posteriores se muestra el antes y el después **descontando** lo que cambió en los demás grupos (o centros). Registrar o cambiar el estado exige usuario `docente` o `direccion` y la cabecera `X-Hack4Edu: 1`. |

Las medidas se guardan en `ia.medida` (migración V8) y no contienen datos de alumnos. La valoración es una
asociación temporal, no una prueba de causalidad, y así se indica en pantalla.

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
docker compose up -d
docker compose exec app bash /docker-entrypoint-initdb.d/01-rol-ingesta.sh
```
