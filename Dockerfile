# dockerfile
FROM maven:3.8.8-jdk-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B package -DskipTests


CMD ["java","-Xms256m","-Xmx512m","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar","--spring.profiles.active=prod"]
