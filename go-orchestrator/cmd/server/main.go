package main

import (
	"log"
	"net/http"
	"os"

	"github.com/sethum-VS/Deca-Node-TSP/go-orchestrator/internal/handlers"
)

// withCSP wraps any http.Handler and injects a Content-Security-Policy header
// into every response. This is required because:
//   - HTMX needs 'unsafe-eval' to parse trigger expressions at runtime.
//   - Our inline onclick toggle scripts require 'unsafe-inline'.
//   - Leaflet tile images are served from tile.openstreetmap.org.
//   - Fonts/scripts are loaded from Google Fonts and unpkg CDNs.
func withCSP(next http.Handler) http.Handler {
	const csp = "" +
		"default-src 'self'; " +
		"script-src 'self' 'unsafe-inline' 'unsafe-eval' https://unpkg.com; " +
		"style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://unpkg.com; " +
		"font-src 'self' https://fonts.gstatic.com https://fonts.googleapis.com; " +
		"img-src 'self' data: https://*.tile.openstreetmap.org https://unpkg.com; " +
		"connect-src 'self' https://photon.komoot.io https://unpkg.com; " +
		"frame-ancestors 'none';"

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Security-Policy", csp)
		next.ServeHTTP(w, r)
	})
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	mux := http.NewServeMux()

	// ── Page routes ────────────────────────────────────────────────
	mux.HandleFunc("/", handlers.IndexHandler)

	// ── Static assets ──────────────────────────────────────────────
	mux.Handle("/css/", http.StripPrefix("/css/", http.FileServer(http.Dir("./public/css"))))

	// ── HTMX API routes ────────────────────────────────────────────
	mux.HandleFunc("/route", handlers.RouteHandler)

	log.Printf("🚀 Deca-Node Go orchestrator listening on :%s", port)
	// Wrap the entire mux so every route (pages, static, API) gets the CSP header.
	if err := http.ListenAndServe(":"+port, withCSP(mux)); err != nil {
		log.Fatalf("server failed: %v", err)
	}
}

