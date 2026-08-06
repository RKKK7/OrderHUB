# ============================================
# STAGE 1 — BUILD
# Built with REPO ROOT as context (docker-compose sets context: .)
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY order-service/pom.xml .
RUN mvn dependency:go-offline -B

COPY order-service/src ./src
# Tests run during build — broken order flow never reaches the image
RUN mvn clean package -B

# ============================================
# STAGE 2 — RUN
# ============================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
