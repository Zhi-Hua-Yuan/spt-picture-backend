# 第一阶段：构建
FROM maven:3.8.8-jdk-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

# 第二阶段：运行
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 从 builder 阶段复制 JAR 文件（注意路径！）
COPY --from=builder /app/target/spt-picture-backend-*.jar ./app.jar

# 暴露端口（根据你的应用调整）
EXPOSE 8123

# 启动命令（带 profile）
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar", "--spring.profiles.active=prod"]