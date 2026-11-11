# UNVERIFIED: written without a running Docker daemon. See docs/SPRING_BOOT_GUIDE.md.
# Builds the core, then the Spring Boot API, and runs it on a slim JRE.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests install
COPY spring-api/pom.xml spring-api/pom.xml
COPY spring-api/src spring-api/src
RUN mvn -q -f spring-api/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/spring-api/target/triage-spring-api-1.0.0.jar app.jar
ENV PORT=8080
EXPOSE 8080
USER 1000
ENTRYPOINT ["java", "-jar", "app.jar"]
