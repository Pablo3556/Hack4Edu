# Base de datos

PostgreSQL 16 + pgvector con el dataset de Kaggle **Student Performance Factors**
(lainguyn123, sintético, CC0), en `datos/StudentPerformanceFactors.csv`.
Las migraciones Flyway (`app_llm/src/main/resources/db/migration`) crean el esquema,
las vistas agregadas y los permisos. La app solo puede leer agregados.

## Puesta en marcha

Desde la raíz del repo:

```bash
cp .env.example .env                       # y cambia las contraseñas
docker compose up -d db                    # crea roles y pgvector (solo la primera vez)
docker compose run --rm migraciones        # aplica V1…V5 (o arranca la app Quarkus)
./scripts/cargar_kaggle.sh                 # carga el CSV (se puede repetir)
```

Para la app, exporta las mismas variables antes de `./mvnw quarkus:dev`
(Quarkus también lee un `.env` en `app_llm/`):

```bash
cd app_llm
set -a; source ../.env; set +a
./mvnw quarkus:dev
```

## Esquemas

| Esquema | Contenido | Quién accede |
|---|---|---|
| `staging` | Filas por alumno: `spf_raw` (CSV tal cual, se vacía tras cargar) y `alumno_spf` (tipado, en castellano) | Solo `escuela_admin` |
| `analitica` | Catálogos (`centro`, `grupo`, `factor`, `factor_nivel`) y vistas agregadas | `app_lector`, solo lectura |
| `ia` | Tablas de la app (embeddings de LangChain4j) | `app_lector`, lectura y escritura |

## Vistas para la app

Todas tienen la columna `ambito` (`global`, `centro` o `grupo`) con `centro_id` y `grupo_id`.

| Vista | Para qué |
|---|---|
| `vw_resumen` | Nota media, cuartiles, asistencia, % con asistencia < 80 %, tutorías, recursos… y diferencia con la media global |
| `vw_factor_nivel` | Nota media por nivel de cada factor (barras de Chart.js) |
| `vw_factor_brecha` | Brecha entre el nivel con peor y mejor nota, en puntos y en desviaciones típicas |
| `vw_prioridades` | Ranking de factores accionables: `prioridad = brecha × % en el peor nivel × accionabilidad`, con magnitud, fiabilidad, palanca y aviso |
| `vw_calidad_datos` | Valores ausentes por columna y datos de la última carga |

Ejemplo:

```sql
SELECT puesto, factor_nombre, nivel_peor, pct_peor, brecha, magnitud, prioridad, palanca
FROM analitica.vw_prioridades
WHERE ambito = 'centro' AND centro_id = 3
ORDER BY puesto;
```

## Privacidad por diseño

- **Umbral mínimo de grupo.** `analitica.k_min()` vale 10. No se publica ninguna cifra calculada con menos alumnos. En las vistas por factor, si un nivel no llega al umbral, se oculta el factor entero en ese ámbito, para que no se pueda deducir la celda pequeña restando las demás.
- **Sin acceso a filas individuales.** `app_lector` no tiene acceso a `staging`. Las vistas se ejecutan con los permisos de su propietario, y por eso no usan `security_invoker`.
- **Datos sensibles fuera.** `dificultad_aprendizaje` se guarda pero no aparece en ninguna vista.
- **Factores contextuales sin prioridad.** Renta, estudios de la familia, distancia, titularidad y género aparecen para analizar la equidad, pero nunca reciben prioridad.
- **Mínima copia de datos.** La tabla cruda se vacía después de cada carga.

## Decisiones sobre el dataset

- **Centros y grupos ficticios.** El dataset no tiene centro ni grupo. Se reparten los alumnos de forma determinista entre 9 centros ficticios (6 públicos y 3 privados, respetando `School_Type`), con 4 grupos por centro. Como el reparto es aleatorio, **las diferencias entre centros son ruido**. Lo interpretable son los factores.
- **Notas fuera de rango.** Las `Exam_Score` mayores que 100 se recortan a 100 y se cuentan en `staging.carga` (en el CSV real hay una).
- **Valores ausentes.** Los vacíos (78 en `Teacher_Quality`, 90 en `Parental_Education_Level` y 67 en `Distance_from_Home`) pasan a NULL y no cuentan en ningún nivel.
- **Valores inesperados.** Un valor no previsto en una columna categórica hace fallar la carga con un mensaje claro.
- **Accionabilidad.** Los pesos de `analitica.factor.accionabilidad` son una decisión del equipo, no un dato. Cambiadlos en una nueva migración (V6…) si el jurado o los docentes proponen otros.

## Reglas para nuevas migraciones

- No modifiquéis una migración ya aplicada: Flyway detecta el cambio de checksum y la app no arranca. Cread una nueva `V6__...sql`.
- En las vistas de `analitica` no llaméis a funciones de `staging`: PostgreSQL comprueba el permiso EXECUTE con el usuario que consulta (`app_lector`), y fallaría.

## Reiniciar desde cero

```bash
docker compose down -v      # borra el volumen: se vuelven a crear los roles
```
