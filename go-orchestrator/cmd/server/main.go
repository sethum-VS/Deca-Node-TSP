package main

import (
	"log"
	"net/http"
	"os"

	"github.com/sethum-VS/Deca-Node-TSP/go-orchestrator/internal/infrastructure/handlers"
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	mux := http.NewServeMux()

	// ── Static files ───────────────────────────────────────────────
	staticDir := "internal/infrastructure/static"
	mux.Handle("/static/", http.StripPrefix("/static/", http.FileServer(http.Dir(staticDir))))

	// ── Page routes ────────────────────────────────────────────────
	mux.HandleFunc("/", handlers.IndexHandler)

	// ── HTMX API routes ────────────────────────────────────────────
	mux.HandleFunc("/route", handlers.RouteHandler)

	log.Printf("🚀 Deca-Node Go orchestrator listening on :%s", port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		log.Fatalf("server failed: %v", err)
	}
}
