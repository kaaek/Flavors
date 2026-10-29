# One image that serves both the React app and the API (docs/Architecture.md §11).
# Build from the repository root: docker build -t flavors .

# --- Stage 1: build the React app ---------------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /build
# Dependency manifests first, so this layer is cached until package*.json changes.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- Stage 2: package the Spring Boot jar, with the React build inside it -----------------
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /build
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
# Download dependencies before copying the sources, for the same caching reason.
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
# Files in src/main/resources/static are served by Spring Boot (SpaForwardingConfig).
COPY --from=frontend /build/dist src/main/resources/static
# Tests run in CI (and need Docker), so they are skipped when building the image.
RUN ./mvnw -B -q -DskipTests package

# --- Stage 3: the image that actually runs ------------------------------------------------
FROM eclipse-temurin:25-jre
# Never run as root inside the container.
RUN useradd --system --no-create-home flavors
USER flavors
WORKDIR /app
COPY --from=backend /build/target/flavors.jar app.jar
# Railway provides PORT (application.yml reads it) and the PG* database variables.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
