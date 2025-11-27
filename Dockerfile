# Build stage
FROM gradle:8.12-jdk21 AS builder

WORKDIR /app

# Copy gradle files first (for caching)
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Download dependencies (build step triggers dependency download anyway)
RUN gradle build -x test --no-daemon || true

# Copy source
COPY src ./src

# Build shadow jar
RUN gradle shadowJar --no-daemon

# Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/build/libs/*-all.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
