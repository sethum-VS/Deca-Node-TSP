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
	Status           string       `json:"status"`
	Message          string       `json:"message"`
	OptimizedRoute   []Coordinate `json:"optimizedRoute"`
	TotalStops       int          `json:"totalStops"`
	TotalDistanceKm  float64      `json:"totalDistanceKm"`
	EstimatedTimeMin float64      `json:"estimatedTimeMin"`
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

// RouteHandler receives stop inputs via HTMX POST (hidden inputs named "stop"),
// performs smart parsing, calls the Java routing engine, and returns an HTML
// fragment + a <script> tag to draw the optimized polyline on the Leaflet map.
func RouteHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		return
	}

	if err := r.ParseForm(); err != nil {
		http.Error(w, "Bad Request", http.StatusBadRequest)
		return
	}

	// ── Read all "stop" values (multiple hidden inputs with same name) ──
	rawStops := r.Form["stop"]
	var stops []StopInput
	var originalInputs []string

	for _, raw := range rawStops {
		raw = strings.TrimSpace(raw)
		if raw == "" {
			continue
		}
		originalInputs = append(originalInputs, raw)
		stops = append(stops, parseStopInput(raw))
	}

	if len(stops) < 2 {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprint(w, `
			<div class="flex items-start gap-3 p-4 bg-error-container/30 border border-error/20 rounded-lg">
				<span class="material-symbols-outlined text-error text-xl mt-0.5">warning</span>
				<div>
					<p class="text-sm font-semibold text-error">Not enough stops</p>
					<p class="text-xs text-on-surface-variant mt-1">Drop at least <strong>2 pins</strong> on the map to calculate a route.</p>
				</div>
			</div>`)
		return
	}

	log.Printf("Received %d stops, calling Java routing engine...", len(stops))

	// ── Call the Java routing engine ────────────────────────
	optimized, errResp, err := callJavaService(stops)
	if err != nil {
		log.Printf("Java service error: %v", err)
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprintf(w, `
			<div class="flex items-start gap-3 p-4 bg-error-container/30 border border-error/20 rounded-lg">
				<span class="material-symbols-outlined text-error text-xl mt-0.5">error</span>
				<div>
					<p class="text-sm font-semibold text-error">Routing Engine Error</p>
					<p class="text-xs text-on-surface-variant mt-1">%s</p>
					<p class="text-xs text-on-surface-variant mt-1">Make sure both services are running via <code>docker-compose up</code>.</p>
				</div>
			</div>`, err.Error())
		return
	}
	if errResp != nil {
		log.Printf("Java service returned error: %s", errResp.Message)
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprintf(w, `
			<div class="flex items-start gap-3 p-4 bg-error-container/30 border border-error/20 rounded-lg">
				<span class="material-symbols-outlined text-error text-xl mt-0.5">warning</span>
				<div>
					<p class="text-sm font-semibold text-error">Routing Error</p>
					<p class="text-xs text-on-surface-variant mt-1">%s</p>
				</div>
			</div>`, errResp.Message)
		return
	}

	// ── Build HTML response fragment + route drawing script ──
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, buildResultHTML(optimized))
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
// Includes the metrics cards, the optimized stop list, and a <script>
// tag that calls drawOptimizedRoute() to render the polyline on the map.
func buildResultHTML(resp *OptimizeResponse) string {
	var sb strings.Builder

	// ── Metrics cards ─────────────────────────────────────────
	sb.WriteString(`<div class="space-y-4">`)

	// Success header
	sb.WriteString(`<div class="flex items-center gap-2 text-sm font-semibold text-primary">`)
	sb.WriteString(`<span class="material-symbols-outlined text-lg" style="font-variation-settings: 'FILL' 1;">check_circle</span>`)
	sb.WriteString(`<span>Route Optimized</span>`)
	sb.WriteString(`</div>`)

	// Metric cards
	sb.WriteString(`<div class="grid grid-cols-3 gap-3">`)

	sb.WriteString(fmt.Sprintf(`
		<div class="flex flex-col items-center p-3 bg-primary-fixed/30 rounded-xl">
			<span class="text-lg font-bold text-primary">%d</span>
			<span class="text-[10px] uppercase tracking-wider text-on-surface-variant font-bold">Stops</span>
		</div>`, resp.TotalStops))

	sb.WriteString(fmt.Sprintf(`
		<div class="flex flex-col items-center p-3 bg-primary-fixed/30 rounded-xl">
			<span class="text-lg font-bold text-primary">%.1f</span>
			<span class="text-[10px] uppercase tracking-wider text-on-surface-variant font-bold">KM</span>
		</div>`, resp.TotalDistanceKm))

	sb.WriteString(fmt.Sprintf(`
		<div class="flex flex-col items-center p-3 bg-primary-fixed/30 rounded-xl">
			<span class="text-lg font-bold text-primary">~%.0f</span>
			<span class="text-[10px] uppercase tracking-wider text-on-surface-variant font-bold">MIN</span>
		</div>`, resp.EstimatedTimeMin))

	sb.WriteString(`</div>`)

	// ── Optimized stop list ───────────────────────────────────
	sb.WriteString(`<div class="space-y-2">`)
	sb.WriteString(`<p class="text-xs font-bold uppercase tracking-widest text-on-surface-variant flex items-center gap-1.5">`)
	sb.WriteString(`<span class="material-symbols-outlined text-sm">route</span> Optimized Sequence</p>`)

	for i, c := range resp.OptimizedRoute {
		label := c.Label
		if label == "" {
			label = fmt.Sprintf("Stop %d", i+1)
		}
		sb.WriteString(fmt.Sprintf(`
			<div class="flex items-center gap-3 py-2 px-3 bg-surface-container-low rounded-lg border border-outline-variant/20">
				<div class="flex items-center justify-center w-6 h-6 rounded-full text-white text-[11px] font-bold shrink-0" style="background: linear-gradient(135deg, #0f9d58, #34a853);">%d</div>
				<div class="flex-1 min-w-0">
					<p class="text-sm font-medium text-on-surface truncate">%s</p>
					<p class="text-[11px] text-on-surface-variant">%.6f, %.6f</p>
				</div>
			</div>`, i+1, label, c.Lat, c.Lng))
	}

	sb.WriteString(`</div>`)
	sb.WriteString(`</div>`)

	// ── Script tag to draw the polyline on the Leaflet map ───
	routeJSON, _ := json.Marshal(resp.OptimizedRoute)
	sb.WriteString(fmt.Sprintf(`<script>drawOptimizedRoute(%s);</script>`, string(routeJSON)))

	return sb.String()
}
