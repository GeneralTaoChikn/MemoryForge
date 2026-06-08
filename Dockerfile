# ---- Build stage: compile and assemble a fat jar ----
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.2_13_1.10.1_3.4.2 AS build
WORKDIR /app

# Cache dependencies first
COPY build.sbt ./
COPY project ./project
RUN sbt update

# Build the application
COPY src ./src
RUN sbt assembly

# ---- Runtime stage: small JRE image ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/scala-3.4.2/memoryforge.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
