# ── Build + Cache stage ──────────────────────────────────
# Maven has full access to source, compiled classes, and
# dependencies — so we run GraphCacheBuilder here directly.
FROM maven:3.9-eclipse-temurin-21 AS builder

RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Cache dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Download Sri Lanka OSM data
RUN mkdir -p /data \
    && wget -q -O /data/sri-lanka.osm.pbf \
       https://download.geofabrik.de/asia/sri-lanka-latest.osm.pbf

# Build the application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Pre-build the GraphHopper routing graph cache using Maven exec
# This runs GraphCacheBuilder with full classpath access (no fat-JAR issue)
RUN mvn exec:java \
    -Dexec.mainClass="com.decanode.routing.GraphCacheBuilder" \
    -Dexec.args="/data/sri-lanka.osm.pbf /data/graph-cache" \
    -Dexec.classpathScope="runtime" \
    && rm -f /data/sri-lanka.osm.pbf

# ── Runtime stage ────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /app/target/routing-engine-*.jar app.jar

# Copy the pre-built graph cache (no PBF needed at runtime)
COPY --from=builder /data/graph-cache /data/graph-cache

# JVM memory is set via JAVA_TOOL_OPTIONS in docker-compose.yml or Cloud Run
ENTRYPOINT ["java", "-jar", "app.jar"]
