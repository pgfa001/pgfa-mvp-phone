# Build stage
FROM gradle:8.7-jdk17 AS build
WORKDIR /app

COPY . .

RUN gradle buildFatJar --no-daemon

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/*-all.jar app.jar

EXPOSE 8080

CMD ["sh", "-c", "java -jar app.jar"]