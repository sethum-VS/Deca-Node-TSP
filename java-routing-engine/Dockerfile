# ── Build stage ──────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Cache dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build the application
COPY src ./src
RUN mvn package -DskipTests -B

# ── Runtime stage ────────────────────────────────────────
# Using debian-based JRE (not Alpine) for compatibility with
# GraphHopper's native memory-mapped file operations.
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /app/target/routing-engine-*.jar app.jar

# /data is mounted via docker-compose (contains OSM PBF + graph cache)
VOLUME ["/data"]

EXPOSE 8081

# JVM memory is set via JAVA_TOOL_OPTIONS in docker-compose.yml
ENTRYPOINT ["java", "-jar", "app.jar"]
