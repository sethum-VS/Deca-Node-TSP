package handlers

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"
)

// ── DTOs matching the Java service ──────────────────────────────────

// Coordinate represents a lat/lng pair for the Java API.
type Coordinate struct {
	Lat float64 `json:"lat"`
	Lng float64 `json:"lng"`
}

// OptimizeRequest is the JSON payload sent to the Java service.
type OptimizeRequest struct {
	Coordinates []Coordinate `json:"coordinates"`
}

// OptimizeResponse is the JSON response from the Java service.
type OptimizeResponse struct {
	Status         string       `json:"status"`
	Message        string       `json:"message"`
	OptimizedRoute []Coordinate `json:"optimizedRoute"`
	TotalStops     int          `json:"totalStops"`
}

// javaClient is a reusable HTTP client with a reasonable timeout.
var javaClient = &http.Client{
	Timeout: 10 * time.Second,
}

// getJavaServiceURL returns the base URL of the Java routing engine.
func getJavaServiceURL() string {
	url := os.Getenv("JAVA_SERVICE_URL")
	if url == "" {
		url = "http://localhost:8081"
	}
	return url
}

// RouteHandler receives coordinate form data via HTMX POST, calls the
// Java routing engine for optimization, and returns an HTML fragment.
func RouteHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		return
	}

	if err := r.ParseForm(); err != nil {
		http.Error(w, "Bad Request", http.StatusBadRequest)
		return
	}

	// ── Collect non-empty coordinate pairs from form fields ─────
	var coords []Coordinate
	for i := 1; i <= 10; i++ {
		latStr := strings.TrimSpace(r.FormValue(fmt.Sprintf("lat_%d", i)))
		lngStr := strings.TrimSpace(r.FormValue(fmt.Sprintf("lng_%d", i)))
		if latStr != "" && lngStr != "" {
			var lat, lng float64
			if _, err := fmt.Sscanf(latStr, "%f", &lat); err != nil {
				continue
			}
			if _, err := fmt.Sscanf(lngStr, "%f", &lng); err != nil {
				continue
			}
			coords = append(coords, Coordinate{Lat: lat, Lng: lng})
		}
	}

	if len(coords) < 2 {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprint(w, `<div class="result-card result-error">
			<p>⚠️ Please provide at least <strong>2 valid coordinates</strong> to calculate a route.</p>
		</div>`)
		return
	}

	log.Printf("Received %d coordinates, calling Java routing engine...", len(coords))

	// ── Call the Java routing engine ────────────────────────────
	optimized, err := callJavaService(coords)
	if err != nil {
		log.Printf("Java service error: %v", err)
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprintf(w, `<div class="result-card result-error">
			<h3>❌ Routing Engine Error</h3>
			<p>%s</p>
			<p class="hint">Make sure both services are running via <code>docker-compose up</code>.</p>
		</div>`, err.Error())
		return
	}

	// ── Build HTML response fragment ───────────────────────────
	var sb strings.Builder
	sb.WriteString(`<div class="result-card result-success">`)
	sb.WriteString(fmt.Sprintf(`<h3>✅ %s</h3>`, optimized.Message))
	sb.WriteString(fmt.Sprintf(`<p><strong>%d</strong> stops optimized by the Java routing engine.</p>`, optimized.TotalStops))

	// Original order
	sb.WriteString(`<h4 style="margin-top:1rem; color: var(--text-muted);">📋 Original Order</h4>`)
	sb.WriteString(`<table class="coord-table">`)
	sb.WriteString(`<thead><tr><th>Stop</th><th>Latitude</th><th>Longitude</th></tr></thead><tbody>`)
	for i, c := range coords {
		sb.WriteString(fmt.Sprintf(`<tr><td>%d</td><td>%.6f</td><td>%.6f</td></tr>`, i+1, c.Lat, c.Lng))
	}
	sb.WriteString(`</tbody></table>`)

	// Optimized order
	sb.WriteString(`<h4 style="margin-top:1rem; color: var(--accent);">🚀 Optimized Order</h4>`)
	sb.WriteString(`<table class="coord-table">`)
	sb.WriteString(`<thead><tr><th>Stop</th><th>Latitude</th><th>Longitude</th></tr></thead><tbody>`)
	for i, c := range optimized.OptimizedRoute {
		sb.WriteString(fmt.Sprintf(`<tr><td>%d</td><td>%.6f</td><td>%.6f</td></tr>`, i+1, c.Lat, c.Lng))
	}
	sb.WriteString(`</tbody></table>`)

	sb.WriteString(`<p class="hint">🔜 In Sprint 3+, the Java engine will use GraphHopper + jsprit for real TSP optimization.</p>`)
	sb.WriteString(`</div>`)

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, sb.String())
}

// callJavaService sends coordinates to the Java /api/optimize endpoint
// and returns the parsed response.
func callJavaService(coords []Coordinate) (*OptimizeResponse, error) {
	reqBody := OptimizeRequest{Coordinates: coords}
	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	url := getJavaServiceURL() + "/api/optimize"
	resp, err := javaClient.Post(url, "application/json", bytes.NewReader(jsonData))
	if err != nil {
		return nil, fmt.Errorf("failed to reach Java service at %s: %w", url, err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read Java response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("Java service returned status %d: %s", resp.StatusCode, string(body))
	}

	var optimized OptimizeResponse
	if err := json.Unmarshal(body, &optimized); err != nil {
		return nil, fmt.Errorf("failed to parse Java response: %w", err)
	}

	return &optimized, nil
}
