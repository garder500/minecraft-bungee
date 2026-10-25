package panel

import (
	"io"
	"net/http"
	"net/url"
	"regexp"
	"time"

	"github.com/garder500/minecraft-bungee/internal/backup"
	"github.com/garder500/minecraft-bungee/internal/control"
)

var jobID = regexp.MustCompile(`^[0-9a-f]{16}$`)

// audited records a backup action in the audit table and returns the func that stores its HTTP status.
func (s *Server) audited(w http.ResponseWriter, v session, server, action string) (func(int), bool) {
	row, e := s.DB.Exec("INSERT INTO audit(at,username,server,action,result) VALUES(?,?,?,?,?)", time.Now().UTC().Format(time.RFC3339), v.User, server, action, 0)
	if e != nil {
		fail(w, 500, "audit unavailable")
		return nil, false
	}
	id, _ := row.LastInsertId()
	return func(status int) { s.DB.Exec("UPDATE audit SET result=? WHERE id=?", status, id) }, true
}

// transfer streams a request body or response body of any size to or from the controller. The
// ordinary controller client has a 70 s timeout, which would cut a multi-gigabyte ZIP.
func (s *Server) transfer(r *http.Request, method, endpoint string, body io.Reader, length int64) (*http.Response, error) {
	req, e := http.NewRequestWithContext(r.Context(), method, s.Controller+endpoint, body)
	if e != nil {
		return nil, e
	}
	if body != nil {
		req.ContentLength = length
	}
	req.Header.Set("Authorization", "Bearer "+s.Token)
	if rng := r.Header.Get("Range"); rng != "" && method == "GET" {
		req.Header.Set("Range", rng)
	}
	return (&http.Client{Transport: s.HTTP.Transport}).Do(req)
}

func (s *Server) backupRoutes(mux *http.ServeMux, protected func(func(http.ResponseWriter, *http.Request, session)) http.HandlerFunc) {
	validName := func(w http.ResponseWriter, name string) bool {
		if !backup.NameRE.MatchString(name) {
			fail(w, 400, "invalid backup name")
			return false
		}
		return true
	}
	mux.HandleFunc("GET /api/v1/backups", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		s.relay(w, r, "GET", "/backups", nil)
	}))
	mux.HandleFunc("GET /api/v1/jobs/{id}", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		if !jobID.MatchString(r.PathValue("id")) {
			fail(w, 400, "invalid job")
			return
		}
		s.relay(w, r, "GET", "/jobs/"+r.PathValue("id"), nil)
	}))
	mux.HandleFunc("POST /api/v1/servers/{id}/backups", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		id := r.PathValue("id")
		if !control.Valid(id) || id == "velocity" {
			fail(w, 400, "Paper server required")
			return
		}
		s.auditRelay(w, r, v, id, "backup-create", "POST", "/servers/"+id+"/backups", nil)
	}))
	mux.HandleFunc("POST /api/v1/servers/{id}/restore/{name}", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		id, name := r.PathValue("id"), r.PathValue("name")
		if !control.Valid(id) || id == "velocity" {
			fail(w, 400, "Paper server required")
			return
		}
		if !validName(w, name) {
			return
		}
		s.auditRelay(w, r, v, id, "backup-restore:"+name, "POST", "/servers/"+id+"/restore/"+url.PathEscape(name), nil)
	}))
	mux.HandleFunc("DELETE /api/v1/backups/{name}", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		name := r.PathValue("name")
		if !validName(w, name) {
			return
		}
		s.auditRelay(w, r, v, "", "backup-delete:"+name, "DELETE", "/backups/"+url.PathEscape(name), nil)
	}))
	mux.HandleFunc("GET /api/v1/backups/{name}", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		name := r.PathValue("name")
		if !validName(w, name) {
			return
		}
		done, ok := s.audited(w, v, "", "backup-download:"+name)
		if !ok {
			return
		}
		res, e := s.transfer(r, "GET", "/backups/"+url.PathEscape(name), nil, 0)
		if e != nil {
			done(503)
			fail(w, 503, "controller unavailable")
			return
		}
		defer res.Body.Close()
		for _, h := range []string{"Content-Type", "Content-Length", "Content-Range", "Content-Disposition", "Accept-Ranges", "Last-Modified"} {
			if value := res.Header.Get(h); value != "" {
				w.Header().Set(h, value)
			}
		}
		w.Header().Set("Content-Encoding", "identity") // already compressed: keep the reverse proxy from re-encoding it
		w.Header().Set("Cache-Control", "no-store")
		http.NewResponseController(w).SetWriteDeadline(time.Time{})
		w.WriteHeader(res.StatusCode)
		io.Copy(w, res.Body)
		done(res.StatusCode)
	}))
	mux.HandleFunc("PUT /api/v1/backups/{name}", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		name := r.PathValue("name")
		if !validName(w, name) {
			return
		}
		if r.ContentLength > control.MaxBackupBytes {
			fail(w, 413, "backup is larger than 40 GiB")
			return
		}
		done, ok := s.audited(w, v, "", "backup-upload:"+name)
		if !ok {
			return
		}
		// Hours of upload are fine: only the idle time between reads should be bounded, not the whole body.
		http.NewResponseController(w).SetReadDeadline(time.Time{})
		res, e := s.transfer(r, "PUT", "/backups/"+url.PathEscape(name), http.MaxBytesReader(w, r.Body, control.MaxBackupBytes), r.ContentLength)
		if e != nil {
			done(503)
			fail(w, 503, "controller unavailable or upload interrupted")
			return
		}
		defer res.Body.Close()
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(res.StatusCode)
		io.Copy(w, io.LimitReader(res.Body, 64<<10))
		done(res.StatusCode)
	}))
}
