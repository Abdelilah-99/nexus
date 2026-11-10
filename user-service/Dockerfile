ARG BASE_REGISTRY=docker.io/library
FROM ${BASE_REGISTRY}/eclipse-temurin:17-jre-alpine

WORKDIR /app
RUN apk add --no-cache wget
COPY target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]