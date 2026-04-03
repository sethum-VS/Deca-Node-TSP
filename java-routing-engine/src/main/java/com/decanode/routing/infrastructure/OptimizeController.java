package com.decanode.routing.infrastructure;

import com.decanode.routing.domain.Coordinate;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * REST Controller for route optimization.
 * Sprint 2: Returns dummy response (reversed coordinates).
 * Future sprints will integrate GraphHopper + jsprit.
 */
@RestController
@RequestMapping("/api")
public class OptimizeController {

    private static final Logger log = LoggerFactory.getLogger(OptimizeController.class);

    @PostMapping("/optimize")
    public ResponseEntity<OptimizeResponse> optimize(@Valid @RequestBody OptimizeRequest request) {
        List<Coordinate> coordinates = request.getCoordinates();
        log.info("Received {} coordinates for optimization", coordinates.size());

        // ── Dummy optimization: reverse the order ──────────────────
        // In future sprints, this will use GraphHopper distance matrix
        // fed into jsprit TSP solver for actual optimization.
        List<Coordinate> reversed = new ArrayList<>(coordinates);
        Collections.reverse(reversed);

        OptimizeResponse response = new OptimizeResponse(
                "success",
                "Route optimized (dummy: reversed order). TSP solver pending.",
                reversed
        );

        return ResponseEntity.ok(response);
    }
}
