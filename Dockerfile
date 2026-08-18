# Same-origin production build (CLAUDE.md "Render Same-Origin Deployment"): one Java process
# serves both the API and the built React SPA from one Render Web Service / one public origin,
# which is what keeps the HttpOnly/Secure/SameSite=Lax refresh cookie working without a paid
# custom domain (see docs/production-deployment-plan.md "Critical: cookie/CORS deployment
# topology"). Build from the REPOSITORY ROOT (not backend/) -- this needs both frontend/ and
# backend/ in its build context. backend/Dockerfile (backend-only) is unchanged and still valid
# for a future split-service topology or local backend-only image builds.

# ---- Stage 1: build the React/Vite frontend ----
FROM node:22-alpine AS frontend-builder
WORKDIR /build/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
# Deliberately no VITE_API_BASE_URL here -- the production build must default to
# window.location.origin (same-origin), never a baked-in hostname (CLAUDE.md "Frontend API Base
# URL" -- see frontend/src/api/client.ts's resolveApiBaseUrl). An operator building a genuinely
# split-topology image later can still pass --build-arg to override this stage if ever needed.
RUN npm run build

# ---- Stage 2: build the Spring Boot backend, embedding the frontend's static output ----
FROM eclipse-temurin:21-jdk AS backend-builder
WORKDIR /build/backend

COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -q dependency:go-offline

COPY backend/src/ src/
# Spring Boot serves classpath:/static/** automatically -- placing the built SPA here before
# `package` bundles it straight into the jar. This only ever happens inside this isolated build
# stage; nothing is written back to the host source tree or committed to git.
COPY --from=frontend-builder /build/frontend/dist/ src/main/resources/static/
RUN ./mvnw -q -DskipTests package

# ---- Stage 3: minimal runtime -- no Node, no Maven/JDK build tooling, no secrets ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin docfitai
COPY --from=backend-builder /build/backend/target/backend-*.jar app.jar
RUN chown docfitai:docfitai app.jar
USER docfitai

EXPOSE 8080
# All configuration (datasource, JWT secret, CORS origins, synthetic-data flag, import flags,
# PORT) comes from environment variables at container run time -- see
# docs/web-deployment-checklist.md for the exact required variables. No secret or credential is
# baked into this image.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
