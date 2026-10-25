package panel

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"sync"
	"testing"
	"time"
)

// callbackRequest is what the browser sends when Google redirects back: no cookie, no CSRF header.
func callbackRequest(s *Server, query string) *httptest.ResponseRecorder {
	r := httptest.NewRequest("GET", "/api/v1/drive/callback?"+query, nil)
	r.RemoteAddr = "127.0.0.1:1234"
	w := httptest.NewRecorder()
	s.Handler().ServeHTTP(w, r)
	return w
}

func TestDriveConnectAndCallbackWithoutACookie(t *testing.T) {
	s, _ := setup(t)
	var mu sync.Mutex
	var gotExchange map[string]string
	var authBody map[string]string
	controller := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		defer mu.Unlock()
		b, _ := io.ReadAll(r.Body)
		switch r.URL.Path {
		case "/drive/auth-url":
			json.Unmarshal(b, &authBody)
			io.WriteString(w, `{"url":"https://accounts.google.test/auth"}`)
		case "/drive/exchange":
			json.Unmarshal(b, &gotExchange)
			io.WriteString(w, `{}`)
		}
	}))
	defer controller.Close()
	s.Controller = controller.URL
	cookie, csrf := login(t, s)

	if w := request(t, s, "POST", "/api/v1/drive/connect", "", nil, ""); w.Code != 401 {
		t.Fatal("connect without a session:", w.Code)
	}
	if w := request(t, s, "POST", "/api/v1/drive/connect", "", cookie, ""); w.Code != 403 {
		t.Fatal("connect without CSRF:", w.Code)
	}
	w := request(t, s, "POST", "/api/v1/drive/connect", "", cookie, csrf)
	if w.Code != 200 || !strings.Contains(w.Body.String(), "accounts.google.test") {
		t.Fatal(w.Code, w.Body.String())
	}
	state := authBody["state"]
	if len(state) < 32 || authBody["redirectUri"] != "https://panel.test/api/v1/drive/callback" {
		t.Fatal(authBody)
	}

	// Google's redirect carries no cookie: the state alone authorises it, once.
	w = callbackRequest(s, url.Values{"code": {"abc"}, "state": {state}}.Encode())
	if w.Code != 303 || w.Header().Get("Location") != "/?drive=connected#backups" {
		t.Fatal(w.Code, w.Header())
	}
	mu.Lock()
	if gotExchange["code"] != "abc" || gotExchange["redirectUri"] != authBody["redirectUri"] {
		t.Fatal("exchange did not get the code and the same redirect URI:", gotExchange)
	}
	mu.Unlock()
	var who string
	s.DB.QueryRow("SELECT username FROM audit WHERE action='drive-connect' AND result=200").Scan(&who)
	if who != "admin" {
		t.Fatal("the connection was not audited to the administrator who asked:", who)
	}
	if w = callbackRequest(s, url.Values{"code": {"abc"}, "state": {state}}.Encode()); w.Header().Get("Location") != "/?drive=expired#backups" {
		t.Fatal("a state worked twice:", w.Header())
	}
	if w = callbackRequest(s, url.Values{"code": {"abc"}, "state": {strings.Repeat("a", 48)}}.Encode()); w.Header().Get("Location") != "/?drive=expired#backups" {
		t.Fatal("unknown state accepted")
	}
	if w = callbackRequest(s, "code=abc"); w.Header().Get("Location") != "/?drive=expired#backups" {
		t.Fatal("missing state accepted")
	}

	// Expired state.
	request(t, s, "POST", "/api/v1/drive/connect", "", cookie, csrf)
	s.DB.Exec("UPDATE drive_states SET expires=?", time.Now().Add(-time.Minute).Unix())
	var old string
	s.DB.QueryRow("SELECT state FROM drive_states").Scan(&old)
	if w = callbackRequest(s, url.Values{"code": {"abc"}, "state": {old}}.Encode()); w.Header().Get("Location") != "/?drive=expired#backups" {
		t.Fatal("expired state accepted")
	}

	// The user said no at Google.
	request(t, s, "POST", "/api/v1/drive/connect", "", cookie, csrf)
	s.DB.QueryRow("SELECT state FROM drive_states").Scan(&old)
	if w = callbackRequest(s, url.Values{"error": {"access_denied"}, "state": {old}}.Encode()); w.Header().Get("Location") != "/?drive=denied#backups" {
		t.Fatal(w.Header())
	}
}

func TestBackupSettingsRoutesAreProtectedAndNeverAuditTheBody(t *testing.T) {
	s, _ := setup(t)
	var seen string
	controller := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		seen = string(b)
		io.WriteString(w, `{"ok":true}`)
	}))
	defer controller.Close()
	s.Controller = controller.URL
	for _, r := range []struct{ method, path string }{{"GET", "/api/v1/backup-settings"}, {"PUT", "/api/v1/backup-settings"}, {"POST", "/api/v1/backup-settings/run"}, {"POST", "/api/v1/drive/disconnect"}, {"GET", "/api/v1/drive/files"}, {"POST", "/api/v1/backups/x.zip/drive"}} {
		if w := request(t, s, r.method, r.path, "{}", nil, ""); w.Code != 401 {
			t.Fatal(r, w.Code)
		}
	}
	cookie, csrf := login(t, s)
	if w := request(t, s, "PUT", "/api/v1/backup-settings", `{"drive":{"clientSecret":"TOPSECRET"}}`, cookie, ""); w.Code != 403 {
		t.Fatal("settings saved without CSRF:", w.Code)
	}
	if w := request(t, s, "PUT", "/api/v1/backup-settings", `{"drive":{"clientSecret":"TOPSECRET"}}`, cookie, csrf); w.Code != 200 || !strings.Contains(seen, "TOPSECRET") {
		t.Fatal(w.Code, seen)
	}
	var leaked int
	s.DB.QueryRow("SELECT COUNT(*) FROM audit WHERE action LIKE '%TOPSECRET%' OR server LIKE '%TOPSECRET%'").Scan(&leaked)
	if leaked != 0 {
		t.Fatal("the secret reached the audit log")
	}
	if w := request(t, s, "POST", "/api/v1/backups/..%2Fx.zip/drive", "", cookie, csrf); w.Code != 400 && w.Code != 404 {
		t.Fatal("bad name:", w.Code)
	}
}
