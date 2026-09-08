# 占位镜像：真正的 Spring Boot 后端还没有 Dockerfile，
# 先用这个空壳跑通 docker compose（暴露 8080，/actuator/health 返回 200）。
# 后续接入真实构建（Gradle bootJar + JRE 基础镜像）后替换本文件。
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
