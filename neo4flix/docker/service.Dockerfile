# syntax=docker/dockerfile:1
# One Dockerfile for every Spring Boot service: docker build --build-arg SERVICE=movie-service ...
# Stage 1 compiles inside Docker (no local Maven/JDK needed); stage 2 is a small JRE image.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY security-common security-common
COPY user-service user-service
COPY movie-service movie-service
COPY rating-service rating-service
COPY recommendation-service recommendation-service
COPY gateway gateway
ARG SERVICE
# -pl SERVICE -am: build only this service and the modules it depends on (security-common).
# The cache mount keeps ~/.m2 between builds, so dependencies are downloaded once.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -pl ${SERVICE} -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# Never run the application as root inside the container.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
ARG SERVICE
COPY --from=build /src/${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT.jar app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
