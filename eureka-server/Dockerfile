# ============================================
# STAGE 1 — BUILD  (Java 17 / Maven)
# Built with REPO ROOT as context (docker-compose sets context: .)
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom first (layer caching), then build.
# NOTE: we intentionally do NOT run 'mvn dependency:go-offline'. That goal tries to
# pre-fetch every optional/transitive artifact (including a ~58MB rocksdbjni that this
# app never uses) and aborts entirely if any single download is interrupted. A plain
# 'mvn package' downloads ONLY what's actually needed and is far more resilient on a
# slow or unstable connection. The Wagon settings below add retries per download.
COPY eureka-server/pom.xml .
COPY eureka-server/src ./src
RUN mvn clean package -DskipTests -B       -Dmaven.wagon.http.retryHandler.count=5       -Dmaven.wagon.httpconnectionManager.ttlSeconds=120       -Dmaven.wagon.rto=120000

# ============================================
# STAGE 2 — RUN  (Java 17 JRE, small alpine image)
# ============================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]
