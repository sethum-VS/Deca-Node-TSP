package com.decanode.routing.infrastructure.web;

import com.decanode.routing.application.service.RoutingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for the /api/optimize endpoint.
 * Delegates to RoutingService for the full optimization pipeline.
 */
@RestController
@RequestMapping("/api")
public class OptimizeController {

    private static final Logger log = LoggerFactory.getLogger(OptimizeController.class);

    private final RoutingService routingService;

    public OptimizeController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @PostMapping("/optimize")
    public ResponseEntity<?> optimize(@Valid @RequestBody OptimizeRequest request) {
        log.info("POST /api/optimize — {} stops received", request.getStops().size());

        try {
            OptimizeResponse response = routingService.optimize(request.getStops());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Validation/routing error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during optimization", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("status", "error", "message", "Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "up", "service", "routing-engine"));
    }
}
