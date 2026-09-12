# syntax=docker/dockerfile:1

# --- Build stage: compile and package the fat jar ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Cache dependencies separately from source for faster rebuilds.
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -e -DskipTests package

# --- Runtime stage: slim JRE, non-root, healthcheck ---
FROM eclipse-temurin:21-jre-alpine AS runtime
# curl for the container HEALTHCHECK; create an unprivileged user to run as.
RUN apk add --no-cache curl \
    && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/wallet-service-*.jar app.jar
USER app
EXPOSE 8080
# Respect container memory limits (important on 512MB free tiers).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD curl -fsS "http://localhost:${PORT:-8080}/actuator/health/liveness" || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
