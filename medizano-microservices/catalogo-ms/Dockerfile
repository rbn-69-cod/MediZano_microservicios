# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -pl catalogo-ms -am package -DskipTests

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 medizano
WORKDIR /app
COPY --from=build /workspace/catalogo-ms/target/*.jar app.jar
USER medizano
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
