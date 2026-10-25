package panel

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestBackupRoutesRequireSessionCSRFAndValidNames(t *testing.T) {
	s, _ := setup(t)
	routes := []struct{ method, path string }{
		{"GET", "/api/v1/backups"}, {"GET", "/api/v1/backups/x.zip"}, {"PUT", "/api/v1/backups/x.zip"}, {"DELETE", "/api/v1/backups/x.zip"},
		{"POST", "/api/v1/servers/creative/backups"}, {"POST", "/api/v1/servers/creative/restore/x.zip"}, {"GET", "/api/v1/jobs/0123456789abcdef"},
	}
	for _, r := range routes {
		if w := request(t, s, r.method, r.path, "", nil, ""); w.Code != 401 {
			t.Fatal(r, w.Code)
		}
	}
	cookie, csrf := login(t, s)
	for _, r := range routes {
		if r.method != "GET" {
			if w := request(t, s, r.method, r.path, "x", cookie, ""); w.Code != 403 {
				t.Fatal("no CSRF:", r, w.Code)
			}
		}
	}
	for _, r := range []struct{ method, path string }{
		{"GET", "/api/v1/backups/.hidden.zip"}, {"PUT", "/api/v1/backups/a.jar"}, {"DELETE", "/api/v1/backups/x.zip.sha256"},
		{"POST", "/api/v1/servers/creative/restore/..%2Fx.zip"}, {"POST", "/api/v1/servers/velocity/backups"}, {"GET", "/api/v1/jobs/..%2F..%2Fx"},
	} {
		if w := request(t, s, r.method, r.path, "x", cookie, csrf); w.Code != 400 && w.Code != 404 {
			t.Fatal(r, w.Code, w.Body.String())
		}
	}
}

func TestBackupDownloadAndUploadStreamThroughTheController(t *testing.T) {
	s, _ := setup(t)
	var uploaded string
	controller := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer "+s.Token {
			w.WriteHeader(401)
			return
		}
		switch r.Method {
		case "GET":
			w.Header().Set("Content-Type", "application/zip")
			w.Header().Set("Content-Disposition", `attachment; filename="x.zip"`)
			io.WriteString(w, "ZIPBYTES")
		case "PUT":
			b, _ := io.ReadAll(r.Body)
			uploaded = string(b)
			w.WriteHeader(201)
			io.WriteString(w, `{"name":"x.zip"}`)
		}
	}))
	defer controller.Close()
	s.Controller = controller.URL
	cookie, csrf := login(t, s)
	w := request(t, s, "GET", "/api/v1/backups/x.zip", "", cookie, "")
	if w.Code != 200 || w.Body.String() != "ZIPBYTES" || w.Header().Get("Content-Type") != "application/zip" || !strings.Contains(w.Header().Get("Content-Disposition"), "x.zip") {
		t.Fatal(w.Code, w.Header(), w.Body.String())
	}
	w = request(t, s, "PUT", "/api/v1/backups/x.zip", "PAYLOAD", cookie, csrf)
	if w.Code != 201 || uploaded != "PAYLOAD" {
		t.Fatal(w.Code, uploaded, w.Body.String())
	}
	var n int
	s.DB.QueryRow("SELECT COUNT(*) FROM audit WHERE action IN ('backup-download:x.zip','backup-upload:x.zip') AND result IN (200,201)").Scan(&n)
	if n != 2 {
		t.Fatal("audit rows:", n)
	}
}
