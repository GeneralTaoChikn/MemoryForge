# ---- Build stage: compile and assemble a fat jar ----
# NOTE: the sbt/Scala versions baked into this image don't matter — the project
# pins sbt 1.10.1 (project/build.properties) and Scala 3.4.2 (build.sbt), which
# the sbt launcher fetches automatically.
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.8_9_1.12.11_3.3.7 AS build
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
