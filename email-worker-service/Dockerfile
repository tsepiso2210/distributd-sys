# =============================================================
# Dockerfile - email-worker-service
# Multi-stage build: Maven builds the JAR, slim JRE runs it
# =============================================================

# ---- Build Stage ----
FROM eclipse-temurin:17-jdk-alpine AS build

# Install Maven (not bundled in eclipse-temurin)
RUN apk add --no-cache maven

WORKDIR /workspace

# Copy parent POM and ALL module POMs first (better Docker layer caching)
COPY pom.xml                            ./pom.xml
COPY email-common/pom.xml              email-common/pom.xml
COPY email-api-service/pom.xml         email-api-service/pom.xml
COPY email-worker-service/pom.xml      email-worker-service/pom.xml
COPY email-client-javafx/pom.xml       email-client-javafx/pom.xml

# Pre-download dependencies (cached unless pom.xml files change)
RUN mvn dependency:go-offline \
    -pl email-common,email-worker-service \
    --no-transfer-progress \
    || true

# Copy source code
COPY email-common/src       email-common/src
COPY email-worker-service/src email-worker-service/src

# Build the worker service jar (also builds email-common as a dependency)
RUN mvn package \
    -pl email-common,email-worker-service \
    -am \
    -DskipTests \
    --no-transfer-progress

# ---- Runtime Stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /workspace/email-worker-service/target/*.jar app.jar

EXPOSE 8082

HEALTHCHECK --interval=15s --timeout=10s --retries=10 \
    CMD wget -q --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar"]
