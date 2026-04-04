package com.decanode.routing.infrastructure.web;

import com.decanode.routing.domain.Coordinate;

import java.util.List;

/**
 * Response DTO for the /api/optimize endpoint.
 * Returns the optimized route with distance and time metrics.
 */
public class OptimizeResponse {

    private String status;
    private String message;
    private List<Coordinate> optimizedRoute;
    private int totalStops;
    private double totalDistanceKm;
    private double estimatedTimeMin;

    public OptimizeResponse() {}

    public OptimizeResponse(String status, String message, List<Coordinate> optimizedRoute,
                            double totalDistanceKm, double estimatedTimeMin) {
        this.status = status;
        this.message = message;
        this.optimizedRoute = optimizedRoute;
        this.totalStops = optimizedRoute != null ? optimizedRoute.size() : 0;
        this.totalDistanceKm = totalDistanceKm;
        this.estimatedTimeMin = estimatedTimeMin;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<Coordinate> getOptimizedRoute() { return optimizedRoute; }
    public void setOptimizedRoute(List<Coordinate> optimizedRoute) {
        this.optimizedRoute = optimizedRoute;
        this.totalStops = optimizedRoute != null ? optimizedRoute.size() : 0;
    }

    public int getTotalStops() { return totalStops; }
    public void setTotalStops(int totalStops) { this.totalStops = totalStops; }

    public double getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(double totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }

    public double getEstimatedTimeMin() { return estimatedTimeMin; }
    public void setEstimatedTimeMin(double estimatedTimeMin) { this.estimatedTimeMin = estimatedTimeMin; }
}
