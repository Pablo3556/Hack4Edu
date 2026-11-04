# Arquitectura

La aplicación (`app_llm`) sigue una **arquitectura hexagonal** (puertos y adaptadores). El código se organiza
primero por *módulo* (de qué trata) y dentro de cada módulo por *capa* (qué papel juega).

```
es.hack4edu
├── compartido        núcleo común (ámbito, errores, acceso JDBC, traducción de errores a HTTP)
├── datos             indicadores agregados: resumen, prioridades, brechas, factores, calidad
├── evolucion         evolución por periodos, alertas tempranas y simulador «¿qué pasaría si…?»
├── ingesta           importación de CSV por docentes (validar → vista previa → confirmar)
├── rag               asistente: fichas, índice vectorial y modelos de lenguaje
├── direccion         panel de dirección: semáforo por grupo y brechas de equidad
├── medidas           seguimiento de medidas de mejora y valoración de su efecto
└── resumen           resumen semanal con las tres prioridades y la fuente de cada cifra
```

Cada módulo tiene las mismas tres capas:

```
<modulo>/
├── dominio/            Java puro: modelos, reglas y cálculos. No conoce frameworks.
├── aplicacion/         casos de uso (lo que hace la aplicación) y, en puertos/, las interfaces que necesita.
└── infraestructura/    adaptadores que conectan con el exterior
    ├── web/              entrada: recursos REST
    ├── persistencia/     salida: PostgreSQL por JDBC
    └── ollama/ pgvector/ memoria/   salida: modelos de lenguaje, índice vectorial, almacén en memoria
```

## Regla de dependencias

```
infraestructura ──▶ aplicacion ──▶ dominio
   (adaptadores)     (casos de uso)    (reglas)
```

Las flechas solo van hacia dentro. Un caso de uso pide lo que necesita a un **puerto** (una interfaz en
`aplicacion/puertos/`) y un **adaptador** de `infraestructura/` lo implementa. Así, por ejemplo,
`ConsultaAgregados` no sabe si los datos vienen de PostgreSQL, y `PreguntarAlAsistente` no sabe si el modelo
es de Ollama.

| Capa | Puede usar | No puede usar |
|---|---|---|
| `dominio` | Java estándar y el dominio de otros módulos | frameworks (Quarkus, Jakarta, JDBC, LangChain4j), `aplicacion`, `infraestructura` |
| `aplicacion` | `dominio`, sus puertos, casos de uso de otros módulos, CDI, configuración, logs | HTTP (`jakarta.ws`), JDBC, LangChain4j, cualquier `infraestructura` |
| `infraestructura` | todo lo anterior y los frameworks | la `infraestructura` de otro módulo |

Los módulos se comunican **por sus casos de uso**, nunca por sus adaptadores. Por ejemplo, `resumen` usa
`ConsultaAgregados` (datos), `ConsultarEvolucion` (evolucion) y `GestionarMedidas` (medidas).

`ArquitecturaHexagonalTest` comprueba estas reglas leyendo los `import` de cada fichero: si alguien las rompe,
los tests fallan.

## Errores

Las capas internas no conocen HTTP. Lanzan `ErrorAplicacion` (petición inválida, no encontrado, conflicto…) y
`ManejadorErrores` lo traduce a JSON `{"error": "…"}` con el código que corresponde.

## Privacidad por diseño

- El esquema `staging` (datos de alumnos) solo lo lee la base de datos. La aplicación lee únicamente vistas
  agregadas del esquema `analitica`, con umbral mínimo de 10 alumnos (`analitica.k_min()`).
- Dos conexiones con permisos mínimos: `app_lector` (solo lectura de `analitica` y tablas propias de `ia`) y
  `app_ingesta` (solo `INSERT` en la tabla de entrada y `EXECUTE` de los procedimientos de carga).
- Las vistas previas de importación viven solo en memoria y caducan a los 20 minutos.

## Añadir una funcionalidad

1. **Dominio**: modela el concepto y escribe sus reglas como Java puro, con tests unitarios.
2. **Puerto**: si necesita datos o servicios externos, declara una interfaz en `aplicacion/puertos/`.
3. **Caso de uso**: una clase en `aplicacion/` que coordina dominio y puertos.
4. **Adaptadores**: implementa el puerto en `infraestructura/persistencia/` (u otro) y expón el caso de uso en
   `infraestructura/web/`.

## Pruebas

| Tipo | Dónde | Necesita |
|---|---|---|
| Reglas del dominio | `src/test/java/.../<modulo>/dominio/` | nada |
| Reglas de arquitectura | `ArquitecturaHexagonalTest` | nada |
| Traducción de ámbito a SQL | `compartido/infraestructura/persistencia/` | nada |

```bash
cd app_llm && ./mvnw test
```
