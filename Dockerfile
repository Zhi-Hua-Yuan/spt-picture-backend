# 针对 Spring Boot 应用的简化 Dockerfile（使用本地已构建的 JAR）
# 说明：因为你已经在本地生成了 target 下的 jar，本文件直接把该 jar 复制到运行镜像中，跳过镜像内的 Maven 构建步骤。
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 构建时传入要复制的 JAR 文件，默认值指向当前项目的构建产物（请根据实际文件名调整）
ARG JAR_FILE=target/spt-picture-backend-0.0.1-SNAPSHOT.jar
# 把本地构建的 jar 复制到镜像内（确保在 docker build 时构建上下文包含 target/）
COPY ${JAR_FILE} ./app.jar

# 暴露默认的 Spring Boot 端口（如有不同请修改）
EXPOSE 8123

# 以下为非 root 用户创建与切换（你要求注释掉）
# RUN addgroup -S appgroup && adduser -S appuser -G appgroup
# USER appuser

# JVM 参数：根据需要调整 Xms/Xmx；优化随机数种子来源
ENTRYPOINT ["java","-Xms256m","-Xmx512m","-Djava.security.egd=file:/dev/./urandom","-jar","/app/app.jar"]
