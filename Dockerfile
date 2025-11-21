# dockerfile
FROM maven:3.8.8-jdk-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app
COPY --from=builder /app/target/*.jar ./app.jar
EXPOSE 8123

# 如果需要以非 root 用户运行，可在此处添加创建/切换用户的命令（按需启用）
# RUN addgroup -S appgroup && adduser -S appuser -G appgroup
# USER appuser

CMD ["java","-Xms256m","-Xmx512m","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar","--spring.profiles.active=prod"]
