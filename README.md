# Deca-Node: TSP Optimization Engine

## Introduction

The Traveling Salesperson Problem (TSP) is a classic algorithmic challenge aiming to find the shortest possible route that visits a set of given locations exactly once and returns to the origin. In the context of modern logistics, delivery route optimization translates this theoretical problem into tangible operational efficiency. The Deca-Node TSP Optimization Engine addresses this challenge by employing a split-microservice architecture to provide real-time, real-world route optimization tailored for delivery operations.

## System Architecture & Tech Stack

This project leverages a modernized, containerized microservices architecture, distinctly separating the presentation layer, API orchestration, and computationally intensive routing logic.

| Component | Technology | Purpose |
| --- | --- | --- |
| **Frontend** | HTMX, Tailwind CSS, Leaflet.js | Dynamic UI, Map Visualization |
| **Orchestrator** | Go (1.26.1) | API Gateway, HTMX Rendering, Traffic Management |
| **Routing Engine** | Java 21, Spring Boot | Heuristics Processing, Algorithmic Execution |
| **Containerization**| Docker, Docker Compose | Multi-stage Builds, Environment Isolation |

### Frontend
The presentation layer relies on **Leaflet.js** for interactive geographic mapping, paired with OpenStreetMap (OSM) tiles. Leaflet provides an open-source, lightweight interface for real-time visualization of optimized routes without relying on proprietary, restrictive mapping APIs.

### API Gateway / Orchestrator (Go)
The API layer is built using **Go** alongside **HTMX**. This combination was selected to maximize development speed, reduce client-side payload complexity, and leverage robust concurrency. By utilizing HTMX, the frontend adopts a 'CDN-first', server-rendered fragment approach. Go evaluates the incoming HTMX requests, parses geographic inputs (smart parsing), marshals the microservice requests, and optimally renders modular HTML responses, stripping out the massive overhead often associated with JavaScript Single Page Applications (SPAs).

### Routing Engine (Java/Spring Boot)
The algorithmic heavy lifting is delegated to a dedicated **Java/Spring Boot** microservice. Java is specifically chosen due to its extensive ecosystem of mature, highly optimized enterprise libraries—principally, **GraphHopper** and **Jsprit**—which require the JVM's advanced memory management characteristics (e.g., memory-mapped files) to handle large-scale geographic graphs quickly and efficiently.

## The 'Heavy Math' Logic (The Core)

### GraphHopper
GraphHopper serves as the foundational routing engine, directly consuming raw OpenStreetMap (`.pbf`) data. Before processing a route, GraphHopper translates geographic points into network nodes through 'Snap-to-Road' calculations. It efficiently maps arbitrary user coordinates or semantic addresses to the nearest valid, navigable road segments on the localized map graph, subsequently generating the required travel-time and distance algorithms.

### Jsprit
Once GraphHopper computes the highly accurate NxN distance/time matrix, the mathematical problem is passed to **Jsprit**, a heavily featured Java-based local search heuristic and meta-heuristic framework. Jsprit acts as the primary Vehicle Routing Problem (VRP) solver, navigating the intense computational complexity of TSP to deduce the most optimal sequence of stops based purely on physical road distance and logical constraints.

## Comparison: OSM/GraphHopper vs. Google Maps API

While commercial APIs like Google Maps offer simplistic integration, the custom OSM and GraphHopper stack was meticulously engineered to prioritize:

1. **Data Sovereignty:** Maintaining complete ownership of the geographic network graph and eliminating third-party latency.
2. **Cost-Efficiency for Intensive Matrices:** TSP algorithms demand highly iterative NxN matrix calculations. Generating extensive travel matrices via the Google Maps API incurs substantial financial overhead (and rate limits). Deploying a local GraphHopper instance enables practically infinite matrix calculations at zero incremental cost.
3. **Academic Value & Advanced Control:** Managing a localized Geographic Information System (GIS) empowers complete, low-level control over edge weights, vehicle profiles, and discrete routing nuances without risking vendor lock-in.

## Internal Data Flow (API Chain)

The pipeline of typical optimization request follows a strict, synchronous lifecycle:

1. **User Search → Photon (Komoot) API:** The user inputs an address string via the UI. The frontend fetches raw geographic coordinates via the Photon Geocoding API. Alternatively, they directly plot a coordinate set via Leaflet map clicks. 
2. **Go Backend → Java REST API:** The HTMX form submits the complete payload of coordinates. The Go Orchestrator resolves coordinate types and acts as an internal HTTP client, sending a standardized JSON request to the Java microservice over an internal Docker network overlay.
3. **Java → GraphHopper → Jsprit:** The Spring Boot API parses the node points. GraphHopper computes Snap-to-Road matrices to derive distances between all permutations of nodes. Jsprit iteratively tests sequences against the matrix to optimize for minimal travel time, concluding with a fully optimized pathway logic.
4. **Return → Leaflet.js:** The Java JVM node returns an optimization object containing coordinate arrays to the Go Orchestrator. The Go application formats this JSON into an HTMX out-of-band fragment update (containing script execution triggers) to dynamically render polyline visualizations directly onto the client's Leaflet.js canvas without full-page reloads.

## Development Roadmap

| Sprint | Objective & Achievements |
| --- | --- |
| **Sprint 1** | **Repository Initialization & DDD Skeleton** <br> Initialized Git flow. Scaffolded the Domain-Driven Design (DDD) directory structure for both the `go-orchestrator` and `java-routing-engine`. Setup baseline HTTP multiplexers in Go. |
| **Sprint 2** | **Java Microservice & Docker Networking** <br> Integrated the Spring Boot framework. Exposed initial REST endpoints for matrix operations via a mocked algorithmic proxy. Established the discrete Docker bridge network to permit seamless inter-container Go-to-Java communication scenarios. |
| **Sprint 3** | **GraphHopper, Jsprit & Routing Pipelines** <br> Ingested the Sri Lanka OSM dataset natively into GraphHopper memory constraints. Executed the 5-step heuristic pipeline: Geocode → Snap → Matrix → Solve → Respond. Implemented semantic text parsing via external Photon REST APIs. |
| **Sprint 4** | **Leaflet Map Integration & Dynamic UI** <br> Sunset the static geographic image for comprehensive Leaflet.js interactivity. Migrated design language to Tailwind CSS elements. Appended map-based geographic pin drops, sequential polyline projections, and dynamic HTMX-driven structural data synchronization. |

## Deployment

In production, the platform microservices are governed via a multi-stage **Docker-Compose** configuration. 
- Build infrastructure is separated natively via Dockerfile multi-stage directives (e.g., stripping Go binaries vs allocating Java environments).
- Configuration explicitly injects customized JVM environmental constraints (`JAVA_TOOL_OPTIONS="-Xmx1G"`) to guarantee the GraphHopper engine maps localized memory graphs efficiently, preventing unallocated `OutOfMemory` container crash conditions when encountering aggressive heuristics loads.
- Geographic constraints and datasets are bootstrapped iteratively via an OS-level pre-init shell script (`init-data.sh`).
