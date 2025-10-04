# ---------------------------
# 1) Build Stage
# ---------------------------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# Copy only pom.xml first to leverage cached dependency layer
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Copy source code and build
COPY src ./src
RUN mvn -q -DskipTests package

# ---------------------------
# 2) Runtime Stage
# ---------------------------
FROM eclipse-temurin:17-jre-alpine

# Add a non-root user
RUN addgroup -S app && adduser -S app -G app \
    && apk add --no-cache ttf-dejavu curl

WORKDIR /app

# Copy built JAR from previous stage (adjust name if artifactId/version changes)
COPY --from=build /workspace/target/pdf-printing-app-0.0.1-SNAPSHOT.jar app.jar

# Expose the internal port (Render overrides with $PORT)
EXPOSE 8080

# Optional: remove HEALTHCHECK if actuator is not available
# HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
#   CMD curl -f http://localhost:${PORT}/ || exit 1

# Use non-root user
USER app

# Entry point passes critical config via system properties so they override application.properties
ENTRYPOINT ["sh", "-c", "java -Dserver.port=$PORT -jar /app/app.jar"]

# ---------------------------
# Usage Examples:
# ---------------------------
# Build locally: docker build -t hkprint:latest .
# Run locally:   docker run -e PORT=8080 -p 8080:8080 --env DB_PASSWORD=secret --env GITHUB_TOKEN=ghp_xxx hkprint:latest
