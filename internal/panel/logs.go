package panel

import (
	"io"
	"net/http"
	"net/url"
	"time"

	"github.com/garder500/minecraft-bungee/internal/control"
)

// logRoutes searches and downloads a server's console history. The controller redacts every line before
// matching it, cleans terminal colour codes and validates the query; the panel passes on known parameters only.
func (s *Server) logRoutes(mux *http.ServeMux, protected func(func(http.ResponseWriter, *http.Request, session)) http.HandlerFunc) {
	mux.HandleFunc("GET /api/v1/servers/{id}/logs", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		id := r.PathValue("id")
		if !control.Valid(id) {
			fail(w, 404, "unknown server")
			return
		}
		q := url.Values{}
		for _, key := range []string{"tail", "max", "q", "regex", "case", "level"} {
			if value := r.URL.Query().Get(key); value != "" {
				q.Set(key, value)
			}
		}
		s.relay(w, r, "GET", "/servers/"+id+"/logs?"+q.Encode(), nil)
	}))
	mux.HandleFunc("GET /api/v1/servers/{id}/logs/download", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		id := r.PathValue("id")
		if !control.Valid(id) {
			fail(w, 404, "unknown server")
			return
		}
		done, ok := s.audited(w, v, id, "console-download")
		if !ok {
			return
		}
		res, e := s.transfer(r, "GET", "/servers/"+id+"/logs/download", nil, 0)
		if e != nil {
			done(503)
			fail(w, 503, "controller unavailable")
			return
		}
		defer res.Body.Close()
		if res.StatusCode != 200 {
			done(res.StatusCode)
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(res.StatusCode)
			io.Copy(w, io.LimitReader(res.Body, 64<<10))
			return
		}
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		w.Header().Set("Content-Disposition", res.Header.Get("Content-Disposition"))
		w.Header().Set("Cache-Control", "no-store")
		http.NewResponseController(w).SetWriteDeadline(time.Time{})
		w.WriteHeader(200)
		io.Copy(w, res.Body)
		done(200)
	}))
}
