package panel

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/garder500/minecraft-bungee/internal/backup"
)

const driveStateTTL = 10 * time.Minute

func (s *Server) callbackURL() string {
	return strings.TrimRight(s.Origin, "/") + "/api/v1/drive/callback"
}

// takeState consumes a connect state: it works once, only before it expires, and returns who asked.
func (s *Server) takeState(state string) (string, bool) {
	if len(state) < 16 || len(state) > 128 {
		return "", false
	}
	tx, e := s.DB.Begin()
	if e != nil {
		return "", false
	}
	defer tx.Rollback()
	var user string
	var expires int64
	if tx.QueryRow("SELECT username,expires FROM drive_states WHERE state=?", state).Scan(&user, &expires) != nil {
		return "", false
	}
	if _, e = tx.Exec("DELETE FROM drive_states WHERE state=?", state); e != nil || tx.Commit() != nil {
		return "", false
	}
	return user, time.Now().Unix() < expires
}

func (s *Server) driveRoutes(mux *http.ServeMux, protected func(func(http.ResponseWriter, *http.Request, session)) http.HandlerFunc) {
	s.DB.Exec("CREATE TABLE IF NOT EXISTS drive_states(state TEXT PRIMARY KEY,username TEXT NOT NULL,expires INTEGER NOT NULL)")
	mux.HandleFunc("GET /api/v1/backup-settings", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		s.relay(w, r, "GET", "/backup-settings", nil)
	}))
	mux.HandleFunc("PUT /api/v1/backup-settings", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		// The body can hold the Google client secret: it goes to the controller and nowhere else, never to the audit log.
		s.auditRelay(w, r, v, "", "backup-settings-update", "PUT", "/backup-settings", http.MaxBytesReader(w, r.Body, 64<<10))
	}))
	mux.HandleFunc("POST /api/v1/backup-settings/run", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		s.auditRelay(w, r, v, "", "backup-plan-run", "POST", "/backup-settings/run", nil)
	}))
	mux.HandleFunc("POST /api/v1/drive/disconnect", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		s.auditRelay(w, r, v, "", "drive-disconnect", "POST", "/drive/disconnect", nil)
	}))
	mux.HandleFunc("GET /api/v1/drive/files", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		s.relay(w, r, "GET", "/drive/files", nil)
	}))
	mux.HandleFunc("POST /api/v1/backups/{name}/drive", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		name := r.PathValue("name")
		if !backup.NameRE.MatchString(name) {
			fail(w, 400, "invalid backup name")
			return
		}
		s.auditRelay(w, r, v, "", "backup-to-drive:"+name, "POST", "/backups/"+url.PathEscape(name)+"/drive", nil)
	}))
	// The consent URL is built by the controller (it knows the client id). The state ties the later callback
	// to this administrator: the callback itself arrives from Google without a session cookie.
	mux.HandleFunc("POST /api/v1/drive/connect", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		raw := make([]byte, 24)
		rand.Read(raw)
		state := hex.EncodeToString(raw)
		s.DB.Exec("DELETE FROM drive_states WHERE expires<?", time.Now().Unix())
		if _, e := s.DB.Exec("INSERT INTO drive_states VALUES(?,?,?)", state, v.User, time.Now().Add(driveStateTTL).Unix()); e != nil {
			fail(w, 500, "cannot start the connection")
			return
		}
		body, _ := json.Marshal(map[string]string{"redirectUri": s.callbackURL(), "state": state})
		res, e := s.upstream(r.Context(), "POST", "/drive/auth-url", bytes.NewReader(body))
		if e != nil {
			fail(w, 503, "controller unavailable")
			return
		}
		defer res.Body.Close()
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(res.StatusCode)
		io.Copy(w, io.LimitReader(res.Body, 64<<10))
	}))
	mux.HandleFunc("GET /api/v1/drive/callback", func(w http.ResponseWriter, r *http.Request) {
		land := func(result string) { http.Redirect(w, r, "/?drive="+result+"#backups", http.StatusSeeOther) }
		q := r.URL.Query()
		user, ok := s.takeState(q.Get("state"))
		if !ok {
			land("expired")
			return
		}
		audit := func(status int) {
			s.DB.Exec("INSERT INTO audit(at,username,server,action,result) VALUES(?,?,?,?,?)", time.Now().UTC().Format(time.RFC3339), user, "", "drive-connect", status)
		}
		if q.Get("error") != "" || q.Get("code") == "" {
			audit(403)
			land("denied")
			return
		}
		body, _ := json.Marshal(map[string]string{"code": q.Get("code"), "redirectUri": s.callbackURL()})
		res, e := s.upstream(r.Context(), "POST", "/drive/exchange", bytes.NewReader(body))
		if e != nil {
			audit(503)
			land("error")
			return
		}
		res.Body.Close()
		audit(res.StatusCode)
		if res.StatusCode != 200 {
			slog.Warn("Google Drive connection refused", "status", res.StatusCode)
			land("error")
			return
		}
		land("connected")
	})
}
