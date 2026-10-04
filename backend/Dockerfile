FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B clean package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S snip && adduser -S snip -G snip
WORKDIR /app
COPY --from=build /workspace/target/snip-api-*.jar app.jar
USER snip
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

