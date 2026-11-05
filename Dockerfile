# ---------------------------------------------------------------------
# Imagen única de Hack4Edu: PostgreSQL 16 + pgvector + aplicación Quarkus.
# Se construye desde la raíz del repositorio:  docker compose up -d --build
# Ollama (los modelos de lenguaje) NO va dentro: se usa el del equipo (OLLAMA_URL).
# ---------------------------------------------------------------------

# --- Etapa 1: compilar la aplicación -------------------------------------
# Imagen oficial de Maven y no ./mvnw: en Linux el wrapper descarga el .tar.gz de Maven y no coincide con
# el distributionSha256Sum (que es el del .zip), así que fallaría la validación.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY app_llm/pom.xml ./
COPY app_llm/src src
# Los tests (rápidos y sin base de datos) se ejecutan con ./mvnw test, no al construir la imagen.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# --- Etapa 2: solo para copiar un JRE ------------------------------------
FROM eclipse-temurin:21-jre AS jre

# --- Etapa 3: imagen final -------------------------------------------------
FROM pgvector/pgvector:pg16

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --home-dir /app app

COPY --from=jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk PATH=/opt/java/openjdk/bin:$PATH

# Aplicación (capas de menos a más cambiante)
COPY --from=build --chown=app:app /build/target/quarkus-app/lib/ /app/lib/
COPY --from=build --chown=app:app /build/target/quarkus-app/*.jar /app/
COPY --from=build --chown=app:app /build/target/quarkus-app/app/ /app/app/
COPY --from=build --chown=app:app /build/target/quarkus-app/quarkus/ /app/quarkus/

# Roles y pgvector (se ejecutan solo cuando el volumen está vacío), datos de demostración y arranque
COPY db/init/ /docker-entrypoint-initdb.d/
COPY scripts/ /opt/hack4edu/scripts/
COPY datos/StudentPerformanceFactors.csv datos/profesorado.csv datos/asignaciones_docentes.csv /opt/hack4edu/datos/
COPY docker/entrypoint.sh /usr/local/bin/hack4edu-entrypoint

ENV POSTGRES_DB=escuela \
    POSTGRES_USER=postgres \
    JAVA_OPTS="-XX:MaxRAMPercentage=50"

VOLUME /var/lib/postgresql/data
EXPOSE 8080 5432
ENTRYPOINT ["hack4edu-entrypoint"]
