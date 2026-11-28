# One image per service. Build from the REPOSITORY ROOT, because the service needs libs/:
#   docker build -f services/_template/Dockerfile -t flavors-template .
# When you copy the template to services/<name>/, change only the SERVICE argument's default.
ARG SERVICE=_template

# --- Stage 1: build the shared libraries and the service ---------------------------------
FROM eclipse-temurin:25-jdk AS build
ARG SERVICE
WORKDIR /build
COPY libs/ libs/
COPY services/${SERVICE}/ service/
# Tests run in CI (and need Docker), so they are skipped when building the image.
RUN libs/mvnw -B -q -f libs/pom.xml install -DskipTests \
 && service/mvnw -B -q -f service/pom.xml -DskipTests package

# --- Stage 2: the image that actually runs -----------------------------------------------
FROM eclipse-temurin:25-jre
# Never run as root inside the container.
RUN useradd --system --no-create-home flavors
USER flavors
WORKDIR /app
COPY --from=build /build/service/target/service.jar app.jar
# PORT, the PG* database variables, RABBITMQ_* and FLAVORS_JWKS_URI come from the environment.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
