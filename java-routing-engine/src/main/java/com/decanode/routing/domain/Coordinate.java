package com.decanode.routing.domain;

import jakarta.validation.constraints.NotNull;

/**
 * Represents a single geographic coordinate (latitude/longitude).
 */
public class Coordinate {

    @NotNull(message = "Latitude is required")
    private Double lat;

    @NotNull(message = "Longitude is required")
    private Double lng;

    public Coordinate() {}

    public Coordinate(Double lat, Double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    @Override
    public String toString() {
        return String.format("(%f, %f)", lat, lng);
    }
}
