FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends wget curl && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 medizano
WORKDIR /app
COPY services/medizano-reportes-ms/target/*.jar app.jar
USER medizano
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]
