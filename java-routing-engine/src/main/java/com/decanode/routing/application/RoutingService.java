package com.decanode.routing.application;

import com.decanode.routing.domain.Coordinate;
import com.decanode.routing.infrastructure.OptimizeResponse;
import com.decanode.routing.infrastructure.StopInput;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.SchrimpfFactory;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Core routing service — implements the 5-step optimization pipeline:
 *   A) Geocode text addresses via Photon API
 *   B) Snap all coordinates to nearest road via GraphHopper LocationIndex
 *   C) Build travel-time distance matrix via GraphHopper routing
 *   D) Solve TSP via jsprit VRP
 *   E) Assemble response
 */
@Component
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);
    private static final String PHOTON_BASE_URL = "https://photon.komoot.io/api/";
    private static final int SNAP_RADIUS_METERS = 5000; // 5 km snap radius

    private final GraphHopper hopper;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RoutingService(GraphHopper hopper) {
        this.hopper = hopper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Execute the full optimization pipeline.
     * @param stops list of 2-10 stop inputs (coords or addresses)
     * @return optimized route response
     */
    public OptimizeResponse optimize(List<StopInput> stops) {
        log.info("Starting optimization for {} stops", stops.size());

        // ── Step A: Geocode addresses ─────────────────────────────
        List<Coordinate> resolved = resolveAllStops(stops);
        log.info("Step A complete: {} stops resolved", resolved.size());

        // ── Step B: Snap to road network ─────────────────────────
        List<Coordinate> snapped = snapAllToRoad(resolved);
        log.info("Step B complete: {} stops snapped", snapped.size());

        // ── Step C: Distance matrix ──────────────────────────────
        int n = snapped.size();
        double[][] distMatrix = new double[n][n]; // meters
        double[][] timeMatrix = new double[n][n]; // seconds
        buildDistanceMatrix(snapped, distMatrix, timeMatrix);
        log.info("Step C complete: {}x{} distance matrix built", n, n);

        // ── Step D: TSP via jsprit ───────────────────────────────
        List<Integer> optimalOrder = solveTSP(n, distMatrix, timeMatrix);
        log.info("Step D complete: optimal order = {}", optimalOrder);

        // ── Step E: Build response ───────────────────────────────
        return buildResponse(snapped, optimalOrder, distMatrix, timeMatrix);
    }

    // ════════════════════════════════════════════════════════════════
    // Step A: Geocode text addresses via Photon
    // ════════════════════════════════════════════════════════════════

    private List<Coordinate> resolveAllStops(List<StopInput> stops) {
        List<Coordinate> resolved = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            StopInput stop = stops.get(i);
            if (stop.hasCoordinates()) {
                String label = String.format("Stop %d", i + 1);
                resolved.add(new Coordinate(stop.getLat(), stop.getLng(), label));
            } else if (stop.hasAddress()) {
                Coordinate geocoded = geocode(stop.getAddress(), i);
                resolved.add(geocoded);
            } else {
                throw new IllegalArgumentException(
                        String.format("Stop %d has neither coordinates nor address", i + 1));
            }
        }
        return resolved;
    }

    private Coordinate geocode(String address, int index) {
        try {
            String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String url = PHOTON_BASE_URL + "?q=" + encoded + "&limit=1";
            log.info("Geocoding stop {}: '{}' via Photon", index + 1, address);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalArgumentException(
                        String.format("Geocoding failed for stop %d ('%s'): HTTP %d",
                                index + 1, address, response.statusCode()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode features = root.get("features");
            if (features == null || features.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("Geocoding returned no results for stop %d: '%s'",
                                index + 1, address));
            }

            // GeoJSON: coordinates = [longitude, latitude]
            JsonNode coords = features.get(0).get("geometry").get("coordinates");
            double lon = coords.get(0).asDouble();
            double lat = coords.get(1).asDouble();

            log.info("  → Resolved '{}' to ({}, {})", address, lat, lon);
            return new Coordinate(lat, lon, address);
        } catch (IllegalArgumentException e) {
            throw e; // re-throw validation errors
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("Geocoding error for stop %d ('%s'): %s",
                            index + 1, address, e.getMessage()), e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Step B: Snap to nearest road node
    // ════════════════════════════════════════════════════════════════

    private List<Coordinate> snapAllToRoad(List<Coordinate> coords) {
        LocationIndex index = hopper.getLocationIndex();
        List<Coordinate> snapped = new ArrayList<>();

        for (int i = 0; i < coords.size(); i++) {
            Coordinate c = coords.get(i);
            Snap snap = index.findClosest(c.getLat(), c.getLng(), EdgeFilter.ALL_EDGES);

            if (!snap.isValid()) {
                throw new IllegalArgumentException(
                        String.format("Stop %d (%s) could not be snapped to a road. " +
                                        "Ensure the coordinate is within Sri Lanka's road network.",
                                i + 1, c));
            }

            double snappedLat = snap.getSnappedPoint().getLat();
            double snappedLng = snap.getSnappedPoint().getLon();

            log.debug("  Snap {}: ({}, {}) → ({}, {})",
                    i + 1, c.getLat(), c.getLng(), snappedLat, snappedLng);

            snapped.add(new Coordinate(snappedLat, snappedLng, c.getLabel()));
        }
        return snapped;
    }

    // ════════════════════════════════════════════════════════════════
    // Step C: Build NxN distance/time matrix via GraphHopper
    // ════════════════════════════════════════════════════════════════

    private void buildDistanceMatrix(List<Coordinate> stops,
                                     double[][] distMatrix, double[][] timeMatrix) {
        int n = stops.size();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    distMatrix[i][j] = 0;
                    timeMatrix[i][j] = 0;
                    continue;
                }
                Coordinate from = stops.get(i);
                Coordinate to = stops.get(j);

                GHRequest req = new GHRequest(
                        from.getLat(), from.getLng(),
                        to.getLat(), to.getLng()
                ).setProfile("car");

                GHResponse rsp = hopper.route(req);
                if (rsp.hasErrors()) {
                    log.warn("Routing error {}->{}: {}", i + 1, j + 1, rsp.getErrors());
                    // Use large penalty so jsprit avoids this pair
                    distMatrix[i][j] = 999_999;
                    timeMatrix[i][j] = 999_999;
                } else {
                    ResponsePath path = rsp.getBest();
                    distMatrix[i][j] = path.getDistance();          // meters
                    timeMatrix[i][j] = path.getTime() / 1000.0;    // seconds
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Step D: Solve TSP using jsprit
    // ════════════════════════════════════════════════════════════════

    private List<Integer> solveTSP(int n, double[][] distMatrix, double[][] timeMatrix) {
        // Build jsprit transport cost matrix using string-indexed locations
        VehicleRoutingTransportCostsMatrix.Builder costBuilder =
                VehicleRoutingTransportCostsMatrix.Builder.newInstance(false); // asymmetric

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    costBuilder.addTransportDistance(
                            String.valueOf(i), String.valueOf(j), distMatrix[i][j]);
                    costBuilder.addTransportTime(
                            String.valueOf(i), String.valueOf(j), timeMatrix[i][j]);
                }
            }
        }

        VehicleRoutingTransportCostsMatrix costMatrix = costBuilder.build();

        // Vehicle type with infinite capacity (we just visit stops, no load)
        VehicleType vehicleType = VehicleTypeImpl.Builder
                .newInstance("delivery-type")
                .addCapacityDimension(0, Integer.MAX_VALUE)
                .build();

        // Single vehicle starting at first stop, open-ended (no return to depot)
        VehicleImpl vehicle = VehicleImpl.Builder.newInstance("delivery-vehicle")
                .setStartLocation(Location.newInstance("0"))
                .setReturnToDepot(false)
                .setType(vehicleType)
                .build();

        // Each remaining stop is a "Service" job to visit
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);
        vrpBuilder.setRoutingCost(costMatrix);
        vrpBuilder.addVehicle(vehicle);

        for (int i = 1; i < n; i++) {
            Service service = Service.Builder.newInstance("stop-" + i)
                    .setLocation(Location.newInstance(String.valueOf(i)))
                    .build();
            vrpBuilder.addJob(service);
        }

        VehicleRoutingProblem problem = vrpBuilder.build();

        // Run the meta-heuristic solver
        VehicleRoutingAlgorithm algorithm = new SchrimpfFactory().createAlgorithm(problem);
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        VehicleRoutingProblemSolution best = Solutions.bestOf(solutions);

        // Extract optimal visit order
        List<Integer> order = new ArrayList<>();
        order.add(0); // start at first stop

        if (!best.getRoutes().isEmpty()) {
            VehicleRoute route = best.getRoutes().iterator().next();
            for (TourActivity activity : route.getActivities()) {
                String locId = activity.getLocation().getId();
                order.add(Integer.parseInt(locId));
            }
        }

        return order;
    }

    // ════════════════════════════════════════════════════════════════
    // Step E: Build the response
    // ════════════════════════════════════════════════════════════════

    private OptimizeResponse buildResponse(List<Coordinate> snapped,
                                           List<Integer> order,
                                           double[][] distMatrix,
                                           double[][] timeMatrix) {
        List<Coordinate> orderedRoute = new ArrayList<>();
        double totalDistMeters = 0;
        double totalTimeSec = 0;

        for (int k = 0; k < order.size(); k++) {
            int idx = order.get(k);
            orderedRoute.add(snapped.get(idx));

            if (k > 0) {
                int prevIdx = order.get(k - 1);
                totalDistMeters += distMatrix[prevIdx][idx];
                totalTimeSec += timeMatrix[prevIdx][idx];
            }
        }

        double totalDistKm = Math.round(totalDistMeters / 100.0) / 10.0; // 1 decimal
        double totalTimeMin = Math.round(totalTimeSec / 6.0) / 10.0;     // 1 decimal

        return new OptimizeResponse(
                "success",
                String.format("Route optimized: %d stops, %.1f km, ~%.0f min",
                        orderedRoute.size(), totalDistKm, totalTimeMin),
                orderedRoute,
                totalDistKm,
                totalTimeMin
        );
    }
}
