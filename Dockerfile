# 基础镜像：选择轻量的OpenJDK 17 Alpine版本（适配你的项目JDK版本）
FROM openjdk:17-jdk-alpine

# 防止时区问题，设置容器时区为东八区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 创建工作目录（后续操作都在这个目录下）
WORKDIR /app

# 复制本地打包好的jar包到容器的/app目录下，并重命名为app.jar（简化后续命令）
COPY target/spt-picture-backend-0.0.1-SNAPSHOT.jar /app/app.jar

# 暴露端口（必须和prod配置中的server.port一致：8123）
EXPOSE 8123

# 容器启动命令：启动jar包并激活prod环境
# --spring.profiles.active=prod 强制激活生产环境配置
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]