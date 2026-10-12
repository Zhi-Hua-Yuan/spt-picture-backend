# Backwards-compatible entry point for the Core API image.
# Use Dockerfile.service with --build-arg SERVICE=... for any module.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -ntp -DskipTests -pl picture-core-api -am package

FROM eclipse-temurin:17-jre
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY --from=build /workspace/picture-core-api/target/picture-core-api-0.0.1-SNAPSHOT.jar /app/app.jar
RUN useradd --system --create-home --uid 10001 appuser && chown -R appuser:appuser /app
USER appuser
EXPOSE 8123
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
