# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------
# Imagen de la aplicación (Quarkus, JVM). Se construye desde la carpeta app_llm:
#   docker build -t hack4edu-app app_llm
# o, mejor, con el docker-compose.yml de la raíz (docker compose up --build).
# ---------------------------------------------------------------------

# --- Etapa 1: compilar -------------------------------------------------
# Se usa la imagen oficial de Maven y no ./mvnw: en Linux el wrapper descarga el .tar.gz de Maven y no
# coincide con el distributionSha256Sum (que es el del .zip), así que fallaría la validación.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY src src

# La caché de ~/.m2 evita descargar las dependencias en cada build.
# Los tests (rápidos y sin base de datos) se ejecutan en CI o con ./mvnw test, no al construir la imagen.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# --- Etapa 2: ejecutar -------------------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app

# Capas ordenadas de menos a más cambiante para aprovechar la caché
COPY --from=build --chown=app:app /build/target/quarkus-app/lib/ lib/
COPY --from=build --chown=app:app /build/target/quarkus-app/*.jar ./
COPY --from=build --chown=app:app /build/target/quarkus-app/app/ app/
COPY --from=build --chown=app:app /build/target/quarkus-app/quarkus/ quarkus/

USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -jar quarkus-run.jar"]
