package handlers

import (
	"fmt"
	"log"
	"net/http"
	"strings"
)

// RouteHandler receives coordinate form data via HTMX POST and returns
// an HTML fragment. Currently returns a dummy response to prove the
// HTMX swap works. Will call the Java routing engine in later sprints.
func RouteHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		return
	}

	if err := r.ParseForm(); err != nil {
		http.Error(w, "Bad Request", http.StatusBadRequest)
		return
	}

	// Collect non-empty coordinate pairs from form fields
	type Coord struct {
		Lat string
		Lng string
	}

	var coords []Coord
	for i := 1; i <= 10; i++ {
		lat := strings.TrimSpace(r.FormValue(fmt.Sprintf("lat_%d", i)))
		lng := strings.TrimSpace(r.FormValue(fmt.Sprintf("lng_%d", i)))
		if lat != "" && lng != "" {
			coords = append(coords, Coord{Lat: lat, Lng: lng})
		}
	}

	if len(coords) < 2 {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprint(w, `<div class="result-card result-error">
			<p>⚠️ Please provide at least <strong>2 coordinates</strong> to calculate a route.</p>
		</div>`)
		return
	}

	log.Printf("Received %d coordinates for route optimization", len(coords))

	// ── Build dummy response HTML fragment ──────────────────────────
	var sb strings.Builder
	sb.WriteString(`<div class="result-card result-success">`)
	sb.WriteString(`<h3>✅ Route Received — HTMX Swap Works!</h3>`)
	sb.WriteString(fmt.Sprintf(`<p><strong>%d</strong> stops submitted. Optimization pending (Java engine not yet connected).</p>`, len(coords)))
	sb.WriteString(`<table class="coord-table">`)
	sb.WriteString(`<thead><tr><th>Stop</th><th>Latitude</th><th>Longitude</th></tr></thead><tbody>`)
	for i, c := range coords {
		sb.WriteString(fmt.Sprintf(`<tr><td>%d</td><td>%s</td><td>%s</td></tr>`, i+1, c.Lat, c.Lng))
	}
	sb.WriteString(`</tbody></table>`)
	sb.WriteString(`<p class="hint">🔜 In Sprint 2, this will return the optimized sequence from the Java routing engine.</p>`)
	sb.WriteString(`</div>`)

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, sb.String())
}
