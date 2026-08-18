# =========================================================
# Spring Boot Backend Dockerfile for Free Cloud Hosting
# (Render.com, Koyeb.com, Railway.app, Fly.io)
# =========================================================

# Stage 1: Build the Spring Boot Application
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run with lightweight Eclipse Temurin JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

ENV PORT=8091
EXPOSE 8091

ENTRYPOINT ["java", "-Dserver.port=${PORT}", "-jar", "app.jar"]
