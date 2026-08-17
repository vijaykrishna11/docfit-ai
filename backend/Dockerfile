# Multi-stage build: Maven/Java 21 builder, slim JRE runtime. Not used by local dev (which runs
# ./mvnw spring-boot:run directly against docker-compose's Postgres) -- this is for a real
# deployment target, not exercised by this phase (CLAUDE.md: do not deploy).

FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# A fresh build stage has no local repo yet, so this always needs network access -- it exists to
# put dependency resolution in its own Docker layer (cached separately from source changes), not
# to run offline.
RUN ./mvnw -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

# Runs as a non-root user; no build tools or source in the final image.
RUN useradd --system --create-home --shell /usr/sbin/nologin docfitai
COPY --from=builder /build/target/backend-*.jar app.jar
RUN chown docfitai:docfitai app.jar
USER docfitai

EXPOSE 8080
# All configuration (datasource, JWT secret, CORS origins, synthetic-data flag, import flags)
# comes from environment variables at container run time -- see README.md's env var table.
# No secret or credential is baked into this image.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
