package handlers

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

// ── DTOs matching the Java service ──────────────────────────────────

// StopInput supports two modes: coordinate (lat+lng) or address (text).
type StopInput struct {
	Lat     *float64 `json:"lat,omitempty"`
	Lng     *float64 `json:"lng,omitempty"`
	Address string   `json:"address,omitempty"`
}

// OptimizeRequest is the JSON payload sent to the Java service.
type OptimizeRequest struct {
	Stops []StopInput `json:"stops"`
}

// Coordinate in the Java response (with optional label).
type Coordinate struct {
	Lat   float64 `json:"lat"`
	Lng   float64 `json:"lng"`
	Label string  `json:"label,omitempty"`
}

// OptimizeResponse is the JSON response from the Java service.
type OptimizeResponse struct {
	Status          string       `json:"status"`
	Message         string       `json:"message"`
	OptimizedRoute  []Coordinate `json:"optimizedRoute"`
	TotalStops      int          `json:"totalStops"`
	TotalDistanceKm float64      `json:"totalDistanceKm"`
	EstimatedTimeMin float64     `json:"estimatedTimeMin"`
}

// ErrorResponse for Java error responses.
type ErrorResponse struct {
	Status  string `json:"status"`
	Message string `json:"message"`
}

// javaClient is a reusable HTTP client with a generous timeout
// (GraphHopper routing + jsprit can take several seconds).
var javaClient = &http.Client{
	Timeout: 60 * time.Second,
}

// getJavaServiceURL returns the base URL of the Java routing engine.
func getJavaServiceURL() string {
	url := os.Getenv("JAVA_SERVICE_URL")
	if url == "" {
		url = "http://localhost:8081"
	}
	return url
}

// RouteHandler receives stop inputs via HTMX POST, performs smart parsing,
// calls the Java routing engine, and returns an HTML fragment.
func RouteHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		return
	}

	if err := r.ParseForm(); err != nil {
		http.Error(w, "Bad Request", http.StatusBadRequest)
		return
	}

	// ── Smart parsing: read stop_1 .. stop_10 fields ────────
	var stops []StopInput
	var originalInputs []string

	for i := 1; i <= 10; i++ {
		raw := strings.TrimSpace(r.FormValue(fmt.Sprintf("stop_%d", i)))
		if raw == "" {
			continue
		}

		originalInputs = append(originalInputs, raw)
		stop := parseStopInput(raw)
		stops = append(stops, stop)
	}

	if len(stops) < 2 {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprint(w, `<div class="result-card result-error">
			<p>⚠️ Please provide at least <strong>2 valid stops</strong> to calculate a route.</p>
			<p class="hint">Enter coordinates (e.g. 6.9271, 79.8612) or an address (e.g. Lotus Tower, Colombo).</p>
		</div>`)
		return
	}

	log.Printf("Received %d stops, calling Java routing engine...", len(stops))

	// ── Call the Java routing engine ────────────────────────
	optimized, errResp, err := callJavaService(stops)
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
	if errResp != nil {
		log.Printf("Java service returned error: %s", errResp.Message)
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprintf(w, `<div class="result-card result-error">
			<h3>⚠️ Routing Error</h3>
			<p>%s</p>
		</div>`, errResp.Message)
		return
	}

	// ── Build HTML response fragment ───────────────────────
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, buildResultHTML(optimized, originalInputs))
}

// parseStopInput applies smart parsing:
//   - If the string looks like "lat, lng" numbers → coordinate mode
//   - Otherwise → address mode
func parseStopInput(raw string) StopInput {
	parts := strings.SplitN(raw, ",", 2)
	if len(parts) == 2 {
		latStr := strings.TrimSpace(parts[0])
		lngStr := strings.TrimSpace(parts[1])

		lat, errLat := strconv.ParseFloat(latStr, 64)
		lng, errLng := strconv.ParseFloat(lngStr, 64)

		if errLat == nil && errLng == nil {
			return StopInput{Lat: &lat, Lng: &lng}
		}
	}

	// Not a coordinate pair → treat as address
	return StopInput{Address: raw}
}

// callJavaService sends stops to the Java /api/optimize endpoint
// and returns the parsed response.
func callJavaService(stops []StopInput) (*OptimizeResponse, *ErrorResponse, error) {
	reqBody := OptimizeRequest{Stops: stops}
	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	url := getJavaServiceURL() + "/api/optimize"
	resp, err := javaClient.Post(url, "application/json", bytes.NewReader(jsonData))
	if err != nil {
		return nil, nil, fmt.Errorf("failed to reach Java service at %s: %w", url, err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to read Java response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		var errResp ErrorResponse
		if jsonErr := json.Unmarshal(body, &errResp); jsonErr == nil && errResp.Message != "" {
			return nil, &errResp, nil
		}
		return nil, nil, fmt.Errorf("Java service returned status %d: %s", resp.StatusCode, string(body))
	}

	var optimized OptimizeResponse
	if err := json.Unmarshal(body, &optimized); err != nil {
		return nil, nil, fmt.Errorf("failed to parse Java response: %w", err)
	}

	return &optimized, nil, nil
}

// buildResultHTML constructs the HTMX HTML fragment for the result area.
func buildResultHTML(resp *OptimizeResponse, originalInputs []string) string {
	var sb strings.Builder

	sb.WriteString(`<div class="result-card result-success">`)
	sb.WriteString(fmt.Sprintf(`<h3>✅ %s</h3>`, resp.Message))

	// ── Summary metrics ───────────────────────────────────
	sb.WriteString(`<div class="metrics-row">`)
	sb.WriteString(fmt.Sprintf(`<div class="metric"><span class="metric-value">%d</span><span class="metric-label">Stops</span></div>`, resp.TotalStops))
	sb.WriteString(fmt.Sprintf(`<div class="metric"><span class="metric-value">%.1f km</span><span class="metric-label">Total Distance</span></div>`, resp.TotalDistanceKm))
	sb.WriteString(fmt.Sprintf(`<div class="metric"><span class="metric-value">~%.0f min</span><span class="metric-label">Est. Travel Time</span></div>`, resp.EstimatedTimeMin))
	sb.WriteString(`</div>`)

	// ── Original order ────────────────────────────────────
	sb.WriteString(`<h4 style="margin-top:1.5rem; color: var(--text-muted);">📋 Original Input</h4>`)
	sb.WriteString(`<table class="coord-table">`)
	sb.WriteString(`<thead><tr><th>Stop</th><th>Input</th></tr></thead><tbody>`)
	for i, input := range originalInputs {
		sb.WriteString(fmt.Sprintf(`<tr><td>%d</td><td>%s</td></tr>`, i+1, input))
	}
	sb.WriteString(`</tbody></table>`)

	// ── Optimized route ───────────────────────────────────
	sb.WriteString(`<h4 style="margin-top:1.5rem; color: var(--accent);">🚀 Optimized Route</h4>`)
	sb.WriteString(`<table class="coord-table">`)
	sb.WriteString(`<thead><tr><th>Order</th><th>Label</th><th>Latitude</th><th>Longitude</th></tr></thead><tbody>`)
	for i, c := range resp.OptimizedRoute {
		label := c.Label
		if label == "" {
			label = fmt.Sprintf("Stop %d", i+1)
		}
		sb.WriteString(fmt.Sprintf(`<tr><td>%d</td><td>%s</td><td>%.6f</td><td>%.6f</td></tr>`,
			i+1, label, c.Lat, c.Lng))
	}
	sb.WriteString(`</tbody></table>`)

	sb.WriteString(`</div>`)
	return sb.String()
}
