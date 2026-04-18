# Build inside Docker so `docker compose build discord-bot` works without a local Maven package.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
# Base JRE image has no curl; healthcheck needs it.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/jebaited-discord-bot.jar /app/jebaited-discord-bot.jar
EXPOSE 8787
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8787/health/live || exit 1
ENTRYPOINT ["java", "-jar", "/app/jebaited-discord-bot.jar"]
