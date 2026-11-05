<p align="center">
  <img src="docs/icono/adastra.svg" alt="Ad Astra" width="110">
</p>

<h1 align="center">La escuela que aprende de sus datos</h1>

<p align="center">
  <strong>Ad Astra</strong> · Hack4Edu · Reto 13<br>
  Convierte los datos educativos en información comprensible y prioridades de actuación<br>
  para docentes, equipos directivos y administración educativa.
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-orange">
  <img alt="Quarkus" src="https://img.shields.io/badge/Quarkus-3.40-4695EB">
  <img alt="PostgreSQL 16 + pgvector" src="https://img.shields.io/badge/PostgreSQL-16%20%2B%20pgvector-336791">
  <img alt="Ollama" src="https://img.shields.io/badge/IA-Ollama%20(local)-black">
  <img alt="Docker" src="https://img.shields.io/badge/Docker-un%20contenedor-2496ED">
</p>

---

## Índice

1. [Qué es](#qué-es)
2. [Principios](#principios)
3. [Funcionalidades](#funcionalidades)
4. [Usuarios, roles y permisos](#usuarios-roles-y-permisos)
5. [Puesta en marcha](#puesta-en-marcha)
6. [Configuración (`.env`)](#configuración-env)
7. [Datos](#datos)
8. [Asistente con IA (RAG)](#asistente-con-ia-rag)
9. [API REST](#api-rest)
10. [Arquitectura](#arquitectura)
11. [Seguridad y privacidad](#seguridad-y-privacidad)
12. [Desarrollo y pruebas](#desarrollo-y-pruebas)
13. [Estructura del repositorio](#estructura-del-repositorio)
14. [Problemas frecuentes](#problemas-frecuentes)

---

## Qué es

Una aplicación web que se ejecuta **en tu propio equipo** y responde a preguntas como:

- *¿Qué deberíamos priorizar en este grupo?*
- *¿Ha bajado la asistencia respecto al trimestre pasado?*
- *¿Qué grupos de mi centro necesitan atención?*
- *¿La medida que pusimos en marcha está teniendo efecto?*

Para responderlas cruza los indicadores de rendimiento (nota, asistencia, horas de estudio, tutorías,
apoyo familiar, acceso a recursos…). Los presenta en paneles, gráficas y un resumen en lenguaje llano,
y añade un asistente conversacional que responde con las cifras reales y cita de dónde sale cada una.

## Principios

| Principio | Cómo se cumple |
|---|---|
| **Solo datos agregados** | La aplicación nunca lee datos de alumnos: solo vistas con grupos de **al menos 10 registros**. Si un nivel no llega, se oculta. |
| **Sin decisiones automáticas sobre personas** | No hay predicciones individuales ni etiquetas de riesgo. Todo son medias de grupo, con su incertidumbre. |
| **Todo en local** | Base de datos, aplicación y modelos de lenguaje (Ollama) se ejecutan en el equipo. Ningún dato sale de él. |
| **Cada cifra es verificable** | El resumen semanal es determinista (no usa IA) e indica la fuente de cada número. El asistente cita sus fichas. |
| **Honestidad estadística** | Las alertas exigen un tamaño mínimo y una diferencia distinguible del azar. El simulador avisa cuando no se puede estimar. |

## Funcionalidades

La web se organiza en pestañas. Cada usuario solo ve las que le corresponden ([ver permisos](#usuarios-roles-y-permisos)).

### 📊 Panel general
- **Indicadores clave** del ámbito elegido (grupo, centro o conjunto de centros) comparados con la media global.
- **Prioridades de actuación**: ranking de factores accionables según su relación con la nota y el
  margen de mejora, con el desglose de cada uno.
- **Explorar un factor**: nota media por nivel de cualquier factor.
- **Equidad**: brechas por factores de contexto (renta familiar, nivel educativo de los padres, distancia…).
- **Comparativa de centros** (solo administración).
- **Calidad de los datos**: valores ausentes por columna.
- **Pregunta al asistente**: chat con IA local sobre los datos agregados.

### 📈 Análisis y evolución
- **Importar datos del grupo** desde un CSV en dos fases: primero valida y muestra una vista previa
  sin guardar nada, y solo escribe en la base de datos cuando el docente confirma.
- **Evolución por trimestres** con **alertas tempranas agregadas**: bajadas de nota o asistencia,
  tendencias de 3 periodos, brechas que crecen. Solo hay alerta si la diferencia supera un umbral práctico
  y es estadísticamente distinguible del azar (|z| ≥ 1,96).
- **Simulador «¿qué pasaría si…?»**: estima el efecto de cambiar un factor (por ejemplo, más tutorías)
  con intervalo de confianza.

### 📝 Resumen semanal
Las **tres prioridades** del grupo en lenguaje llano, con la línea de actuación, la fuente de cada cifra,
las señales de evolución y las medidas en curso. Incluye un botón «Copiar resumen» para llevarlo a la reunión.

### 🚦 Panel de dirección
**Semáforo verde/ámbar/rojo por grupo** frente al conjunto de centros (nota, asistencia, % de asistencia baja,
acceso a recursos, tutorías) y brechas de equidad del centro. Los umbrales se muestran en pantalla.

**Progreso del alumnado y equipo docente.** Para cada grupo y para cada docente del centro, cuánto progresa el
alumnado **frente a lo esperado** según su punto de partida y su contexto (nota previa, acceso a recursos,
implicación familiar y renta), con su intervalo de confianza del 95 %. Solo se marca «por encima» o «por debajo»
si el intervalo no incluye el 0 **y** la diferencia es de al menos 0,5 puntos; lo demás es «en lo esperado».
- No se usa la nota bruta (premiaría a quien tiene los grupos más favorables) ni la evaluación de dirección.
- El resultado de un docente es el de sus grupos y lo comparte con el resto del equipo que les da clase:
  orienta conversaciones de mejora, **no es una evaluación del desempeño** (ver [Seguridad y privacidad](#seguridad-y-privacidad)).
- Los docentes se listan por código, nunca ordenados por resultado.

### ✅ Seguimiento de medidas
Registra una medida (por ejemplo, «tutoría entre iguales en 2.º B») con el indicador a vigilar y el periodo de
inicio. Con los datos posteriores muestra el **antes y el después**, descontando lo que cambió en el resto de
grupos. Es una asociación temporal, no una prueba de causalidad, y así se indica en pantalla.

## Usuarios, roles y permisos

La web pide iniciar sesión. Hay tres usuarios de demostración. **No hay contraseñas fijas**: cada instalación
genera las suyas en el archivo `.env`, que no se sube a Git ([cómo verlas](#dónde-están-las-contraseñas)).

| Usuario | Pestañas | Datos que puede consultar | Puede modificar |
|---|---|---|---|
| `docente` | Panel, Análisis, Resumen semanal, Medidas | **Solo su grupo** (y las cifras globales como referencia) | Importar CSV y registrar medidas de su grupo |
| `direccion` | Las del docente + Panel de dirección | **Su centro** y todos sus grupos | Lo mismo en su centro; ve el historial de importaciones de todo el profesorado |
| `administracion` | Panel, Panel de dirección, Medidas | **Todos los centros** (sin bajar a grupos) y la comparativa entre centros | Nada: solo consulta |

Estos límites los aplica **el servidor** en cada petición. Ocultar pestañas en la web es solo comodidad:
si alguien pide datos fuera de su ámbito, recibe `403`. El asistente también filtra las fichas que consulta
según el usuario.

El centro y el grupo de cada usuario se configuran en `.env` (por defecto, centro 1 y grupo 1):

```dotenv
DIRECCION_CENTRO=1
DOCENTE_CENTRO=1
DOCENTE_GRUPO=1
```

> Fuera de una demo, estos tres usuarios se sustituirían por el directorio del centro (OIDC/LDAP).

### Dónde están las contraseñas

- **Arranque con un clic**: la primera vez se crea `.env` con contraseñas aleatorias y, al terminar de
  arrancar, **se muestran en pantalla** junto a cada usuario (en la terminal o, con los iconos, en una ventana).
- **En cualquier momento**: abre `.env` o ejecuta `grep _PASSWORD .env`.

En el login, el **usuario** es `docente`, `direccion` o `administracion`, y la **contraseña** es el valor que
aparece tras el `=` en `DOCENTE_PASSWORD=…`, `DIRECCION_PASSWORD=…` o `ADMINISTRACION_PASSWORD=…`
(no el nombre de la variable).

## Puesta en marcha

### Requisitos

| Herramienta | Para qué | Notas |
|---|---|---|
| [Docker](https://docs.docker.com/get-docker/) con Docker Compose v2 | Base de datos + aplicación | Docker Desktop en macOS/Windows |
| [Ollama](https://ollama.com/download) | Modelos de lenguaje del asistente | Se ejecuta en tu equipo, **no** dentro del contenedor |
| Java 21 | Solo para desarrollo (`./mvnw`) | No hace falta para ejecutar con Docker |

Se necesitan unos 4 GB de disco para los modelos básicos (5,5 GB más si descargas el modelo `preciso`).
Sin Ollama la aplicación funciona igual; solo el asistente queda desactivado.

### Opción A · Arranque con un clic (recomendado)

Los lanzadores abren Docker y Ollama si están cerrados, crean `.env` con contraseñas aleatorias la primera vez,
descargan los modelos que falten, arrancan el contenedor, abren el navegador y muestran los usuarios con sus
contraseñas.

| Sistema | Arrancar | Parar |
|---|---|---|
| **macOS** | Doble clic en `Ad Astra.command` | Doble clic en `Parar Ad Astra.command` |
| **Linux** | `bash AdAstra-linux.sh` | `bash Parar-AdAstra-linux.sh` |
| **Windows** | Doble clic en `AdAstra-windows.bat` | Doble clic en `Parar-AdAstra-windows.bat` |

- **macOS**: la primera vez puede bloquearlo por ser de «un desarrollador no identificado». Haz clic derecho →
  **Abrir** → **Abrir**.
- **Windows**: si SmartScreen avisa, pulsa **Más información** → **Ejecutar de todas formas**.
- **Linux**: si Ollama solo escucha en `127.0.0.1`, ábrelo con `OLLAMA_HOST=0.0.0.0 ollama serve`.

#### Iconos para arrancar y parar sin terminal

Si prefieres un icono, crea una vez los accesos **Ad Astra** y **Parar Ad Astra** con el logotipo de la aplicación:

| Sistema | Comando (una sola vez) | Dónde aparecen |
|---|---|---|
| **macOS** | `bash scripts/crear_app_macos.sh` | `Ad Astra.app` y `Parar Ad Astra.app` en la carpeta del proyecto (arrástralas al Dock) |
| **Linux** | `bash scripts/crear_acceso_linux.sh` | Menú de aplicaciones (y Escritorio, si existe) |
| **Windows** | `powershell -ExecutionPolicy Bypass -File scripts\crear_acceso_windows.ps1` | Escritorio y menú Inicio |

Estos accesos **no abren ninguna terminal**: informan del progreso con notificaciones, muestran los usuarios y
contraseñas en una ventana al terminar y guardan toda la salida en `adastra.log` (en la carpeta del proyecto).
Si algo falla, la ventana de error indica la ruta de ese archivo. En Linux, las ventanas usan `zenity`,
`kdialog` o `notify-send` si están instalados.

> Los accesos apuntan a la carpeta del proyecto: no la muevas ni saques las `.app` de ella después de crearlos.

### Opción B · Paso a paso

```bash
# 1. Clonar
git clone https://github.com/Pablo3556/Hack4Edu.git
cd Hack4Edu

# 2. Crear .env y cambiar los valores «cambia-…» (evita los caracteres @ : / ? # %)
cp .env.example .env

# 3. Descargar los modelos (una vez, con Ollama abierto)
ollama pull llama3.2:3b          # chat (obligatorio para el asistente)
ollama pull embeddinggemma       # embeddings (obligatorio para el asistente)
ollama pull llama3.2:1b          # opcional: modelo «rápido»
ollama pull qwen3.5:4b           # opcional: modelo «preciso»

# 4. Arrancar
docker compose up -d --build
docker compose logs -f app       # Ctrl+C para salir de los logs
```

Cuando aparezcan `[adastra] Dataset cargado` y `[adastra] Asistente indexado`, abre
**http://localhost:8080** e inicia sesión.

**5. (Opcional) Datos de evolución.** Para ver con contenido la evolución, las alertas y las medidas:

```bash
set -a && source .env && set +a
bash scripts/generar_datos_demo.sh                                   # 4 trimestres sintéticos para los 36 grupos
curl -u "direccion:$DIRECCION_PASSWORD" -X POST localhost:8080/api/rag/ingesta   # reindexar el asistente
```

### Qué queda en marcha

| Qué | Dónde |
|---|---|
| Aplicación | http://localhost:8080 (`APP_PUERTO`) |
| PostgreSQL | `127.0.0.1:5432`, solo accesible desde este equipo (`DB_PUERTO`) |
| Datos | Volumen `hack4edu_pgdata` (se conserva tras `docker compose down`) |
| Ollama | El del equipo, en `OLLAMA_URL` (por defecto `http://host.docker.internal:11434`) |

Es un **único contenedor** con PostgreSQL 16 + pgvector y la aplicación. Ollama no va dentro de la imagen
porque los modelos pesan varios GB.

> **Cambio de nombre a Ad Astra.** El proyecto de Docker Compose ahora se llama `adastra` (imagen y contenedor
> incluidos), pero el volumen mantiene su nombre anterior, `hack4edu_pgdata`, para que **no se pierdan los datos**
> creados antes del cambio. Si aún tienes en marcha el contenedor antiguo, páralo una vez con
> `docker compose -p hack4edu down` antes de arrancar el nuevo.

### Comandos útiles

```bash
docker compose up -d --build     # arrancar o reconstruir tras cambiar el código (también HTML/CSS/JS)
docker compose logs -f app       # ver logs
docker compose down              # parar (conserva los datos)
docker compose down -v           # parar y BORRAR la base de datos
```

## Configuración (`.env`)

Plantilla en [`.env.example`](.env.example). `.env` nunca se sube a Git.

| Variable | Obligatoria | Para qué |
|---|---|---|
| `POSTGRES_PASSWORD`, `ESCUELA_ADMIN_PASSWORD` | Sí | Superusuario y propietario de la base de datos |
| `APP_LECTOR_PASSWORD`, `APP_INGESTA_PASSWORD` | Sí | Conexiones de la app (lectura e importación) |
| `DOCENTE_PASSWORD`, `DIRECCION_PASSWORD`, `ADMINISTRACION_PASSWORD` | Sí | Contraseñas de los usuarios de la web |
| `DOCENTE_CENTRO`, `DOCENTE_GRUPO`, `DIRECCION_CENTRO` | No (`1`) | Ámbito de cada usuario |
| `SESION_CLAVE` | No | Cifra la cookie de sesión (16+ caracteres). Sin ella, reiniciar la app cierra las sesiones |
| `APP_PUERTO`, `DB_PUERTO` | No (`8080`, `5432`) | Puertos publicados en el equipo |
| `CARGAR_DATOS` | No (`true`) | Cargar el dataset de demostración si la base está vacía |
| `OLLAMA_URL` | No | Dirección de Ollama |
| `OLLAMA_MODELO_RAPIDO`, `OLLAMA_CHAT_MODEL`, `OLLAMA_MODELO_PRECISO` | No | Modelos de chat de cada perfil |
| `OLLAMA_EMBEDDING_MODEL`, `RAG_EMBEDDING_DIM` | No (`embeddinggemma`, `768`) | Modelo de embeddings (si lo cambias, reindexa) |

> Las contraseñas de la base de datos se fijan al crear el volumen. Si las cambias después, empieza de cero
> con `docker compose down -v`.

## Datos

- **Línea base**: [Student Performance Factors](https://www.kaggle.com/datasets/lainguyn123/student-performance-factors)
  (Kaggle, licencia CC0), con 6.607 registros sintéticos repartidos al azar en **9 centros ficticios con 4 grupos cada uno**.
  Por eso las diferencias entre centros son ruido estadístico y no deben interpretarse; la web lo advierte.
- **Trimestres de evolución**: `scripts/generar_datos_demo.sh` genera 4 trimestres sintéticos.
- **Profesorado (sintético)**: [`datos/profesorado.csv`](datos/profesorado.csv) (72 docentes, 8 por centro, solo
  con código: experiencia, titulación, horas de formación y tipo de contrato) y
  [`datos/asignaciones_docentes.csv`](datos/asignaciones_docentes.csv) (qué asignatura da cada docente en qué grupo,
  horas y tutoría). Se generan con `python3 scripts/generar_profesorado.py` y se cargan con
  `bash scripts/cargar_profesorado.sh` (el contenedor lo hace solo la primera vez). El CSV de docentes **no lleva
  notas**: el progreso se calcula siempre con los datos de alumnado.
- **Importaciones de docentes**: CSV con separador `,`, `;` o tabulador, en UTF-8 o Windows-1252. Las
  columnas se reconocen aunque tengan otros nombres («Nota final», «Asistencia (%)»…).
  - Obligatorias: *Nota final* y *Asistencia (%)*.
  - Las columnas identificativas (nombre, DNI, correo…) y sensibles (dificultades de aprendizaje…) **se ignoran siempre**.
  - Límites: 2 MB, 5.000 filas y 60 columnas por archivo.
  - Plantilla descargable desde la web y ejemplos en [`datos/ejemplos/`](datos/ejemplos/), uno de ellos con errores a propósito.

> `scripts/cargar_kaggle.sh` **sustituye** la línea base y borra los trimestres sintéticos y las importaciones.
> Después, vuelve a ejecutar `generar_datos_demo.sh`.

## Asistente con IA (RAG)

1. Al arrancar, la app convierte las vistas agregadas en **~157 fichas de texto**: resumen, prioridades y
   brechas por ámbito, detalle de cada factor, metodología y calidad de datos.
2. Calcula sus embeddings con Ollama (`embeddinggemma`) y los guarda en PostgreSQL con **pgvector**.
3. Para cada pregunta recupera las 6 fichas más parecidas, filtradas por el centro o grupo que nombre la
   pregunta **y por el ámbito del usuario**, y se las pasa al modelo como contexto.
4. La respuesta incluye las fichas en las que se apoya.

**Ninguna ficha contiene datos de alumnos.** El usuario elige el modelo:

| Perfil | Modelo de Ollama | Para qué |
|---|---|---|
| `rapido` | `llama3.2:1b` | Responde antes, con menos detalle |
| `equilibrado` (por defecto) | `llama3.2:3b` | Recomendado para la demo |
| `preciso` | `qwen3.5:4b` | Más cuidadoso y más lento |

Todos comparten instrucciones, índice y modelo de embeddings, así que cambiar de modelo no obliga a reindexar.

## API REST

Todas las rutas exigen sesión. Desde scripts o `curl` se puede usar HTTP Basic:

```bash
set -a && source .env && set +a
curl -u "docente:$DOCENTE_PASSWORD" "localhost:8080/api/datos/resumen?centro=1&grupo=1"
```

Ámbito de las consultas: sin parámetros, el conjunto de centros; `?centro=3`, un centro;
`?centro=3&grupo=10`, un grupo. Si el ámbito no es del usuario, la respuesta es `403`.

| Método y ruta | Roles | Qué hace |
|---|---|---|
| `GET /api/sesion` · `POST /api/sesion/salir` | todos | Usuario, rol y ámbito · cerrar sesión |
| `GET /api/datos/ambitos` | todos | Centros y grupos que puede consultar el usuario |
| `GET /api/datos/resumen` · `prioridades` · `brechas` · `factor/{codigo}` | todos | Indicadores del ámbito |
| `GET /api/datos/factores` · `calidad` | todos | Catálogo de factores y calidad de datos |
| `GET /api/datos/centros` | administracion | Comparativa entre centros |
| `GET /api/evolucion` | docente, direccion | Evolución por periodos y alertas |
| `GET /api/simulador/factores` · `GET /api/simulador` | docente, direccion | Simulador «¿qué pasaría si…?» |
| `GET /api/resumen-semanal` | docente, direccion | Tres prioridades con su fuente |
| `GET /api/direccion/semaforo?centro=` | direccion, administracion | Semáforo por grupo |
| `GET /api/direccion/progreso-docente?centro=` | direccion, administracion | Progreso frente a lo esperado por grupo y docente |
| `GET /api/medidas` · `indicadores` · `periodos` | todos | Medidas del ámbito y catálogos |
| `POST /api/medidas` · `PUT /api/medidas/{id}/estado` | docente, direccion | Registrar medida o cambiar su estado ¹ |
| `POST /api/ingesta` → `PUT /{token}/mapeo` → `POST /{token}/confirmar` | docente, direccion | Importar CSV: validar, corregir columnas, confirmar ¹ |
| `GET /api/ingesta/historial` · `campos` · `periodos` | docente, direccion | Historial y catálogos de importación |
| `GET /api/ingesta/plantilla` | público | Plantilla CSV |
| `POST /api/asistente` · `GET /api/modelos` | todos | Preguntar al asistente · modelos disponibles |
| `GET /api/rag/estado` · `POST /api/rag/ingesta` | todos · direccion, administracion | Estado del índice · reindexar |

¹ Las peticiones que modifican datos exigen además la cabecera `X-AdAstra: 1` (protección CSRF).

```bash
curl -u "docente:$DOCENTE_PASSWORD" localhost:8080/api/asistente \
     -H 'Content-Type: application/json' \
     -d '{"pregunta": "¿Qué deberíamos priorizar en mi grupo?", "modelo": "equilibrado"}'
```

## Arquitectura

```
                ┌──────────────────────── Contenedor Docker ────────────────────────┐
 Navegador ───▶ │  Quarkus (Java 21)                     PostgreSQL 16 + pgvector   │
 (HTML/JS)      │  ├─ Web estática (login, panel)        ├─ staging   (datos brutos) │
                │  ├─ API REST + control de acceso  ───▶ ├─ analitica (vistas k≥10) │
                │  └─ Asistente (LangChain4j)            └─ ia        (embeddings,   │
                │            │                                          medidas)     │
                └────────────┼──────────────────────────────────────────────────────┘
                             ▼
                   Ollama en el equipo (chat + embeddings)
```

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Quarkus 3.40, JAX-RS, JDBC, Flyway (9 migraciones) |
| IA | LangChain4j (quarkus-langchain4j) + Ollama + pgvector |
| Base de datos | PostgreSQL 16 con pgvector |
| Frontend | HTML, CSS y JavaScript sin frameworks, gráficas con Chart.js |
| Despliegue | Un único contenedor Docker y lanzadores de un clic para macOS, Linux y Windows |

El código sigue una **arquitectura hexagonal** (puertos y adaptadores), con un paquete por módulo:
`compartido`, `datos`, `evolucion`, `ingesta`, `rag`, `direccion`, `medidas` y `resumen`. Cada módulo tiene
las capas `dominio` (Java puro), `aplicacion` (casos de uso y puertos) e `infraestructura` (REST, JDBC, Ollama).
Un test (`ArquitecturaHexagonalTest`) comprueba que nadie rompe la regla de dependencias.

Más detalle en [docs/arquitectura.md](docs/arquitectura.md) y [db/README.md](db/README.md).

## Seguridad y privacidad

- **Inicio de sesión** con formulario. La sesión se guarda en una cookie cifrada, `HttpOnly` y
  `SameSite=Strict`, que caduca a las 8 horas.
- **Permisos en el servidor**: rol por endpoint (`@RolesAllowed`) y ámbito (centro o grupo) en cada consulta.
- **Mínimo privilegio en la base de datos**: la app usa dos conexiones.
  - `app_lector`: solo lee las vistas agregadas.
  - `app_ingesta`: solo inserta en la tabla de entrada y ejecuta los procedimientos de carga.
- **Umbral k = 10**: ninguna cifra sale de menos de 10 registros.
- **Profesorado**: sus datos viven en `staging` (sin nombres) y la app solo lee vistas agregadas. Los grupos por
  debajo del umbral tampoco cuentan para el resultado de sus docentes (evita deducirlos restando). Con datos
  reales, usar información del profesorado para evaluar su rendimiento es un uso de **alto riesgo** según el
  Reglamento Europeo de IA (anexo III, punto 4) y exige informar a la plantilla, evaluación de impacto (RGPD) y
  ninguna decisión automática. Por eso la pantalla lo presenta como apoyo a la mejora, sin rankings.
- **Importaciones**: el archivo no se guarda. La vista previa vive en memoria y caduca a los 20 minutos.
  Cada intento queda auditado (usuario, archivo, fecha, recuentos y resultado).
- **CSRF**: las escrituras exigen la cabecera `X-AdAstra: 1`.
- **Secretos** solo en `.env` (excluido de Git). PostgreSQL solo escucha en `127.0.0.1`.

## Desarrollo y pruebas

La base de datos va en el contenedor y la aplicación se ejecuta en tu equipo con recarga en caliente:

```bash
cp .env.example .env
APP_PUERTO=8081 docker compose up -d --build     # BD en 127.0.0.1:5432 (la app del contenedor queda en 8081)
cd app_llm
set -a && source ../.env && set +a
./mvnw quarkus:dev                               # → http://localhost:8080
./mvnw test                                      # 48 tests, no necesitan base de datos
```

Los tests cubren las reglas del dominio (ingesta de CSV, evolución, semáforo, valoración de medidas),
el filtro de ámbito del asistente, la traducción de ámbito a SQL y las reglas de arquitectura.

### Base de datos creada antes de la migración V7

El rol `app_ingesta` necesita superusuario, así que en un volumen antiguo hay que crearlo una vez,
**antes** de arrancar la app:

```bash
docker compose up -d
docker compose exec app bash /docker-entrypoint-initdb.d/01-rol-ingesta.sh
```

## Estructura del repositorio

```
Hack4Edu/
├── Ad Astra.command · AdAstra-linux.sh · AdAstra-windows.bat/.ps1   lanzadores de un clic (y sus «Parar»)
├── Dockerfile · docker-compose.yml · docker/       contenedor único y su script de arranque
├── .env.example                                    plantilla de configuración
├── app_llm/                                        aplicación Quarkus
│   └── src/main/
│       ├── java/es/hack4edu/<modulo>/              código (dominio · aplicacion · infraestructura)
│       └── resources/
│           ├── META-INF/resources/                 web: login.html, index.html, css/, js/, img/
│           ├── db/migration/                       migraciones Flyway V1–V9
│           └── application.properties
├── db/                                             inicialización de PostgreSQL y documentación
├── datos/                                          dataset de Kaggle, profesorado sintético y CSV de ejemplo
├── scripts/                                        carga de datos y creación de accesos directos (macOS, Linux, Windows)
├── Iconos_AdAstra/                                 icono de la app para cada sistema (.icns, .ico, PNG)
└── docs/                                           arquitectura e icono
```

## Problemas frecuentes

| Síntoma | Solución |
|---|---|
| `define POSTGRES_PASSWORD en .env` al arrancar | Falta `.env`: `cp .env.example .env` |
| `port is already allocated` | Puerto ocupado. Si sigue en marcha el contenedor de antes del cambio de nombre, páralo con `docker compose -p hack4edu down`; si no, pon otro puerto en `.env` (`APP_PUERTO=8081`, `DB_PUERTO=5433`) |
| El icono no hace nada o muestra un error | Mira `adastra.log` en la carpeta del proyecto: ahí queda toda la salida del arranque y la parada sin terminal |
| «Usuario o contraseña incorrectos» | La contraseña es el valor tras el `=` en `.env`, no el nombre de la variable. El usuario va sin tilde: `direccion` |
| Cambié HTML/CSS/JS y no veo el cambio | Reconstruye con `docker compose up -d --build` y recarga con Cmd/Ctrl + Shift + R |
| `Error 403` en algún panel | Ese centro o grupo no es del usuario. Revisa `DOCENTE_CENTRO`, `DOCENTE_GRUPO` y `DIRECCION_CENTRO` |
| «No se pudo indexar el asistente» | Ollama está cerrado o faltan modelos: ábrelo, haz los `ollama pull` y reindexa (`POST /api/rag/ingesta`) |
| «El modelo … no está descargado» | Ejecuta el `ollama pull` que indica el mensaje o elige otro perfil |
| En **Linux** el contenedor no llega a Ollama | Arranca Ollama con `OLLAMA_HOST=0.0.0.0 ollama serve` |
| Cambié las contraseñas de la BD y ya no conecta | Se fijan al crear el volumen: `docker compose down -v` y vuelve a arrancar (borra los datos) |
| Se cierra la sesión al reiniciar | Define `SESION_CLAVE` en `.env` (16+ caracteres) |

---

<p align="center">
  <strong>Ad Astra</strong> · Hecho para Hack4Edu · Datos sintéticos (Kaggle, CC0) y centros ficticios
</p>
