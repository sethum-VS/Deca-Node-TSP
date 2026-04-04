# ── Build stage ──────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Cache dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build the application
COPY src ./src
RUN mvn package -DskipTests -B

# ── Cache-builder stage ──────────────────────────────────
# Downloads the OSM PBF and pre-builds the GraphHopper
# routing graph so it is baked into the final image.
FROM eclipse-temurin:21-jre-jammy AS cache-builder

WORKDIR /app

COPY --from=builder /app/target/routing-engine-*.jar app.jar

# Download Sri Lanka OSM data
RUN mkdir -p /data \
    && apt-get update && apt-get install -y --no-install-recommends wget \
    && wget -q -O /data/sri-lanka.osm.pbf \
       https://download.geofabrik.de/asia/sri-lanka-latest.osm.pbf \
    && apt-get purge -y wget && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

# Build the graph cache using the standalone CLI utility
RUN mkdir -p /data/graph-cache \
    && java -Xmx1G -cp app.jar \
       com.decanode.routing.GraphCacheBuilder \
       /data/sri-lanka.osm.pbf /data/graph-cache \
    && rm -f /data/sri-lanka.osm.pbf

# ── Runtime stage ────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /app/target/routing-engine-*.jar app.jar

# Copy the pre-built graph cache (no PBF needed at runtime)
COPY --from=cache-builder /data/graph-cache /data/graph-cache

EXPOSE 8081

# JVM memory is set via JAVA_TOOL_OPTIONS in docker-compose.yml or Cloud Run
ENTRYPOINT ["java", "-jar", "app.jar"]
