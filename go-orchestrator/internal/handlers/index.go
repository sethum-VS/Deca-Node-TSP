package handlers

import (
	"html/template"
	"log"
	"net/http"
	"path/filepath"
)

// IndexHandler serves the main page with the coordinate input form.
func IndexHandler(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/" {
		http.NotFound(w, r)
		return
	}

	tmplPath := filepath.Join("internal", "templates", "index.html")
	tmpl, err := template.ParseFiles(tmplPath)
	if err != nil {
		log.Printf("template parse error: %v", err)
		http.Error(w, "Internal Server Error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	if err := tmpl.Execute(w, nil); err != nil {
		log.Printf("template execute error: %v", err)
	}
}
