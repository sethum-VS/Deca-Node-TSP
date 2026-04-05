package main

import (
	"log"
	"net/http"
	"os"

	"github.com/sethum-VS/Deca-Node-TSP/go-orchestrator/internal/handlers"
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	mux := http.NewServeMux()

	// ── Page routes ────────────────────────────────────────────────
	mux.HandleFunc("/", handlers.IndexHandler)

	// ── Static assets ──────────────────────────────────────────────
	mux.Handle("/css/", http.StripPrefix("/css/", http.FileServer(http.Dir("public/css"))))

	// ── HTMX API routes ────────────────────────────────────────────
	mux.HandleFunc("/route", handlers.RouteHandler)

	log.Printf("🚀 Deca-Node Go orchestrator listening on :%s", port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		log.Fatalf("server failed: %v", err)
	}
}
