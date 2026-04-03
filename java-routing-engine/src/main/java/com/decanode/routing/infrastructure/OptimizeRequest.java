package com.decanode.routing.infrastructure;

import com.decanode.routing.domain.Coordinate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for the /api/optimize endpoint.
 * Accepts a list of up to 10 coordinate pairs.
 */
public class OptimizeRequest {

    @NotEmpty(message = "At least 2 coordinates are required")
    @Size(min = 2, max = 10, message = "Must provide between 2 and 10 coordinates")
    @Valid
    private List<Coordinate> coordinates;

    public OptimizeRequest() {}

    public OptimizeRequest(List<Coordinate> coordinates) {
        this.coordinates = coordinates;
    }

    public List<Coordinate> getCoordinates() { return coordinates; }
    public void setCoordinates(List<Coordinate> coordinates) { this.coordinates = coordinates; }
}
