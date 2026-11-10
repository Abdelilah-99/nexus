ARG BASE_REGISTRY=docker.io/library
FROM ${BASE_REGISTRY}/eclipse-temurin:17-jre-alpine

WORKDIR /app

# wget is used by the Docker healthcheck
RUN apk add --no-cache wget && \
    addgroup -S appgroup && \
    adduser -S appuser -G appgroup && \
    mkdir -p /app/uploads && \
    chown -R appuser:appgroup /app

# JAR already built by Jenkins/Maven
COPY target/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8083

VOLUME ["/app/uploads"]

HEALTHCHECK --interval=30s \
            --timeout=5s \
            --start-period=40s \
            --retries=3 \
    CMD wget --quiet --tries=1 --spider \
        http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]