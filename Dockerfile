FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /workspace/target/pdf-printing-app-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

USER app

ENTRYPOINT ["sh", "-c", "java -Dserver.port=$PORT -jar /app/app.jar"]
