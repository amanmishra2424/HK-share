# ---------- BUILD STAGE ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first (dependency cache)
COPY pom.xml .
RUN mvn -B -e -C -DskipTests dependency:go-offline

# Now copy source
COPY src ./src
RUN mvn -B -DskipTests package

# ---------- RUNTIME STAGE ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
