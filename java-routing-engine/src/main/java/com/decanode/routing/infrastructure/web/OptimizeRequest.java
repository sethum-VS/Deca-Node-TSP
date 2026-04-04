package com.decanode.routing.infrastructure.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for the /api/optimize endpoint.
 * Accepts a list of 2-10 stops, each being either coordinates or an address.
 */
public class OptimizeRequest {

    @NotEmpty(message = "At least 2 stops are required")
    @Size(min = 2, max = 10, message = "Must provide between 2 and 10 stops")
    private List<StopInput> stops;

    public OptimizeRequest() {}

    public OptimizeRequest(List<StopInput> stops) {
        this.stops = stops;
    }

    public List<StopInput> getStops() { return stops; }
    public void setStops(List<StopInput> stops) { this.stops = stops; }
}
