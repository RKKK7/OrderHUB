# ============================================
# STAGE 1 — BUILD
# Built with REPO ROOT as context (docker-compose sets context: .)
# so all COPY paths are relative to the repo root.
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY eureka-server/pom.xml .
RUN mvn dependency:go-offline -B

COPY eureka-server/src ./src
RUN mvn clean package -DskipTests -B

# ============================================
# STAGE 2 — RUN
# ============================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]
