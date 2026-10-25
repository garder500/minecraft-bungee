package panel

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestConsoleSearchIsForwardedWithKnownParametersOnly(t *testing.T) {
	s, _ := setup(t)
	var got string
	controller := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasSuffix(r.URL.Path, "/download") {
			w.Header().Set("Content-Disposition", `attachment; filename="creative-console.log"`)
			io.WriteString(w, "line one\n")
			return
		}
		got = r.URL.RawQuery
		io.WriteString(w, `{"lines":[],"scanned":0,"matched":0,"truncated":false}`)
	}))
	defer controller.Close()
	s.Controller = controller.URL
	if w := request(t, s, "GET", "/api/v1/servers/creative/logs?q=x", "", nil, ""); w.Code != 401 {
		t.Fatal(w.Code)
	}
	cookie, _ := login(t, s)
	w := request(t, s, "GET", "/api/v1/servers/creative/logs?q=warn+me&level=WARN&regex=1&evil=1&tail=all", "", cookie, "")
	if w.Code != 200 || strings.Contains(got, "evil") || !strings.Contains(got, "level=WARN") || !strings.Contains(got, "q=warn+me") {
		t.Fatal(w.Code, got)
	}
	w = request(t, s, "GET", "/api/v1/servers/creative/logs/download", "", cookie, "")
	if w.Code != 200 || w.Body.String() != "line one\n" || !strings.Contains(w.Header().Get("Content-Disposition"), "creative-console.log") {
		t.Fatal(w.Code, w.Header(), w.Body.String())
	}
	if w := request(t, s, "GET", "/api/v1/servers/nope/logs", "", cookie, ""); w.Code != 404 {
		t.Fatal(w.Code)
	}
	var n int
	s.DB.QueryRow("SELECT COUNT(*) FROM audit WHERE action='console-download' AND result=200").Scan(&n)
	if n != 1 {
		t.Fatal("download not audited:", n)
	}
}

func TestChatUsesTheSessionNameAndAuditsWithoutTheText(t *testing.T) {
	s, _ := setup(t)
	var sent string
	controller := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		sent = string(b)
		io.WriteString(w, `{"open":true,"messages":[],"last":0}`)
	}))
	defer controller.Close()
	s.Controller = controller.URL
	uuid := "12345678-1234-4234-8234-123456789abc"
	if w := request(t, s, "POST", "/api/v1/servers/creative/chat", `{"action":"read","uuid":"`+uuid+`"}`, nil, ""); w.Code != 401 {
		t.Fatal(w.Code)
	}
	cookie, csrf := login(t, s)
	if w := request(t, s, "POST", "/api/v1/servers/creative/chat", `{"action":"read","uuid":"`+uuid+`"}`, cookie, ""); w.Code != 403 {
		t.Fatal("no CSRF:", w.Code)
	}
	w := request(t, s, "POST", "/api/v1/servers/creative/chat", `{"action":"send","uuid":"`+uuid+`","message":"secret words"}`, cookie, csrf)
	if w.Code != 200 || !strings.Contains(sent, `"staff":"admin"`) || !strings.Contains(sent, "secret words") {
		t.Fatal(w.Code, sent)
	}
	var actions string
	rows, _ := s.DB.Query("SELECT action FROM audit")
	for rows.Next() {
		var a string
		rows.Scan(&a)
		actions += a + ";"
	}
	if !strings.Contains(actions, "chat-send:"+uuid) || strings.Contains(actions, "secret words") {
		t.Fatal("audit:", actions)
	}
	if w := request(t, s, "POST", "/api/v1/servers/creative/chat", `{"action":"send","uuid":"`+uuid+`","staff":"root"}`, cookie, csrf); w.Code != 400 {
		t.Fatal("the staff name must come from the session:", w.Code)
	}
	for _, bad := range []string{`{"action":"op","uuid":"` + uuid + `"}`, `{"action":"read","uuid":"x"}`} {
		if w := request(t, s, "POST", "/api/v1/servers/creative/chat", bad, cookie, csrf); w.Code != 400 {
			t.Fatal(bad, w.Code)
		}
	}
	if w := request(t, s, "POST", "/api/v1/servers/velocity/chat", `{"action":"read","uuid":"`+uuid+`"}`, cookie, csrf); w.Code != 400 {
		t.Fatal(w.Code)
	}
}
