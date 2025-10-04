# Multi-stage build for Spring Boot PDF Printing Application
# 1) Build stage
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# Copy only pom first to leverage cached dependency layer
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn -q -DskipTests package

# 2) Runtime stage (lean JRE image)
FROM eclipse-temurin:17-jre-alpine

# Add a non-root user
RUN addgroup -S app && adduser -S app -G app \
    && apk add --no-cache ttf-dejavu curl

WORKDIR /app

# Copy built jar from previous stage (adjust name if artifactId/version changes)
COPY --from=build /workspace/target/pdf-printing-app-0.0.1-SNAPSHOT.jar app.jar


# Expose the internal port
EXPOSE 8080

# Healthcheck (root path since actuator not included). Adjust if you add actuator.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT}/ || exit 1

USER app

# Entry point passes critical config via system properties so they override application.properties

# Usage examples:
# Build:   docker build -t hkprint:latest .
# Run:     docker run -p 8080:8080 --env DB_PASSWORD=secret --env GITHUB_TOKEN=ghp_xxx hkprint:latest
# Compose: create a docker-compose.yml with a MySQL service named 'mysql' (see documentation you can generate later)