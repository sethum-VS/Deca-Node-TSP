package com.decanode.routing.domain;

import jakarta.validation.constraints.NotNull;

/**
 * Represents a geographic coordinate with an optional text label.
 * The label preserves the original address text after geocoding.
 */
public class Coordinate {

    @NotNull(message = "Latitude is required")
    private Double lat;

    @NotNull(message = "Longitude is required")
    private Double lng;

    /** Optional label — holds the original text address or stop name */
    private String label;

    public Coordinate() {}

    public Coordinate(Double lat, Double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public Coordinate(Double lat, Double lng, String label) {
        this.lat = lat;
        this.lng = lng;
        this.label = label;
    }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    @Override
    public String toString() {
        if (label != null && !label.isEmpty()) {
            return String.format("%s (%.6f, %.6f)", label, lat, lng);
        }
        return String.format("(%.6f, %.6f)", lat, lng);
    }
}
