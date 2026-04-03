# 📦 Deca-Node — Delivery Route Optimizer

A microservices-based delivery route optimization tool using **Go** (orchestrator), **Java/Spring Boot** (routing engine with GraphHopper + jsprit), and **HTMX** for interactive frontend.

## Architecture

```
Browser (HTMX) ─→ Go Orchestrator (:8080) ─→ Java Routing Engine (:8081)
                   │  Smart parsing              │  Geocoding (Photon)
                   │  HTML fragments              │  Snap-to-Road (GraphHopper)
                   └──────────────────            │  Distance Matrix (GraphHopper)
                                                  │  TSP Solver (jsprit)
                                                  └──────────────────────────
```

## Quick Start

### 1. Clone & Download OSM Data

```bash
git clone https://github.com/sethum-VS/Deca-Node-TSP.git
cd Deca-Node-TSP

# Download Sri Lanka OSM data (~150MB)
./init-data.sh
```

### 2. Build & Run

```bash
docker-compose up --build
```

> **Note:** On first startup, GraphHopper builds a routing graph from the PBF file. This takes ~30-60 seconds. Subsequent startups are instant.

### 3. Open the App

Navigate to [http://localhost:8080](http://localhost:8080)

Enter delivery stops as:
- **Coordinates:** `6.9271, 79.8612`
- **Addresses:** `Lotus Tower, Colombo`
- **Mix of both!**

## Tech Stack

| Component | Technology |
|---|---|
| Frontend | HTML + HTMX + vanilla CSS |
| Orchestrator | Go `net/http` + `html/template` |
| Routing Engine | Java 21, Spring Boot 3.4 |
| Routing Graph | GraphHopper 11.0 + Sri Lanka OSM |
| TSP Solver | jsprit 1.7.2 |
| Geocoding | Photon API (komoot) |
| Infrastructure | Docker Compose |

## Development

```bash
# Feature branch workflow
git checkout -b feature/my-feature

# Run services individually (without Docker)
cd go-orchestrator && go run cmd/server/main.go
cd java-routing-engine && mvn spring-boot:run
```

## License

MIT
