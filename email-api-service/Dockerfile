# =============================================================
# Dockerfile - email-api-service
# Multi-stage build: Maven builds the JAR, slim JRE runs it
# =============================================================

# ---- Build Stage ----
# eclipse-temurin includes the JDK; maven is installed via apk
FROM eclipse-temurin:17-jdk-alpine AS build

# Install Maven
RUN apk add --no-cache maven

WORKDIR /workspace

# Copy parent POM and ALL module POMs first (better Docker layer caching)
# When only source changes, this layer is cached
COPY pom.xml                            ./pom.xml
COPY email-common/pom.xml              email-common/pom.xml
COPY email-api-service/pom.xml         email-api-service/pom.xml
COPY email-worker-service/pom.xml      email-worker-service/pom.xml
COPY email-client-javafx/pom.xml       email-client-javafx/pom.xml

# Pre-download all dependencies (cached unless pom.xml files change)
RUN mvn dependency:go-offline \
    -pl email-common,email-api-service \
    --no-transfer-progress \
    || true

# Copy source code
COPY email-common/src      email-common/src
COPY email-api-service/src email-api-service/src

# Build the API service jar (also builds email-common as a dependency)
# -DskipTests: unit tests run in CI, not in Docker build
RUN mvn package \
    -pl email-common,email-api-service \
    -am \
    -DskipTests \
    --no-transfer-progress

# ---- Runtime Stage ----
# JRE only (much smaller image than JDK)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /workspace/email-api-service/target/*.jar app.jar

# Expose the API port
EXPOSE 8081

# Health check so Docker Compose waits until the service is ready
HEALTHCHECK --interval=15s --timeout=10s --retries=10 \
    CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar"]
