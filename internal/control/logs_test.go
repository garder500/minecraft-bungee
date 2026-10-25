package control

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"net/http"
	"strings"
	"testing"
)

func frame(stream byte, text string) []byte {
	h := make([]byte, 8)
	h[0] = stream
	binary.BigEndian.PutUint32(h[4:], uint32(len(text)))
	return append(h, text...)
}

// logServer answers Docker with a multiplexed log stream of the given lines.
func logServer(t *testing.T, lines ...string) (*Server, *int) {
	t.Helper()
	var body bytes.Buffer
	for i, l := range lines {
		body.Write(frame(byte(1+i%2), "2026-10-25T10:00:0"+string(rune('0'+i%10))+".000000000Z "+l+"\n"))
	}
	var tails int
	docker := fakeDocker(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/v1.44/containers/json" {
			w.Write([]byte(`[{"Id":"c"}]`))
			return
		}
		if strings.Contains(r.URL.Path, "/logs") {
			if r.URL.Query().Get("tail") == "all" {
				tails++
			}
			w.Write(body.Bytes())
		}
	})
	return &Server{Docker: docker, Token: strings.Repeat("t", 64), Passwords: map[string]string{"creative": strings.Repeat("p", 64)}}, &tails
}

func TestLevelOfEveryLogFormat(t *testing.T) {
	for line, want := range map[string]string{
		"2026-10-25T10:00:00.1Z [12:30:01] [Server thread/WARN]: Can't keep up!": "WARN",
		"[12:30:01] [Paper Async Task Handler Thread - 1/INFO]: hello":           "INFO",
		"[12:30:01 ERROR]: Velocity failed":                                      "ERROR",
		"[12:30:01] [main/FATAL]: boom":                                          "ERROR",
		"[12:30:01] [main/DEBUG]: x":                                             "DEBUG",
		"\tat java.base/java.lang.Thread.run(Thread.java:1583)":                  "",
		"2026-10-25T10:00:00.1Z [init] Starting":                                 "",
		"Caused by: java.lang.IllegalStateException: no":                         "",
	} {
		if got := Level(line); got != want {
			t.Errorf("%q: %q, want %q", line, got, want)
		}
	}
}

func TestStackTraceKeepsTheLevelOfItsHeadline(t *testing.T) {
	s, _ := logServer(t, "[10:00:00] [main/INFO]: starting", "[10:00:01] [main/ERROR]: crash", "\tat a.b.C.d(C.java:1)", "\tat a.b.C.e(C.java:2)", "[10:00:02] [main/INFO]: done")
	res, e := s.queryLogs(t.Context(), "creative", LogQuery{Tail: 100, Max: 100, Levels: map[string]bool{"ERROR": true}})
	if e != nil || res.Matched != 3 || !strings.Contains(res.Lines[2], "C.e(") {
		t.Fatal(res, e)
	}
}

func TestSearchRedactsBeforeMatchingAndKeepsTheNewestMatches(t *testing.T) {
	secret := strings.Repeat("p", 64)
	s, _ := logServer(t, "[10:00:00] [main/INFO]: rcon password "+secret, "[10:00:01] [main/INFO]: foo 1", "[10:00:02] [main/INFO]: foo 2", "[10:00:03] [main/INFO]: foo 3", "[10:00:04] [main/INFO]: bar")
	if res, _ := s.queryLogs(t.Context(), "creative", LogQuery{Search: "ppppp", Max: 10}); res.Matched != 0 {
		t.Fatal("a search matched the redacted secret:", res)
	}
	if res, _ := s.queryLogs(t.Context(), "creative", LogQuery{Search: "^.*password pppp", Regex: true, Max: 10}); res.Matched != 0 {
		t.Fatal("a regex matched the redacted secret:", res)
	}
	res, e := s.queryLogs(t.Context(), "creative", LogQuery{Search: "FOO", Max: 2})
	if e != nil || res.Matched != 3 || len(res.Lines) != 2 || !strings.Contains(res.Lines[0], "foo 2") || !strings.Contains(res.Lines[1], "foo 3") {
		t.Fatal(res, e)
	}
	if res, _ = s.queryLogs(t.Context(), "creative", LogQuery{Search: "FOO", Case: true, Max: 5}); res.Matched != 0 {
		t.Fatal("case-sensitive search ignored case")
	}
	res, _ = s.queryLogs(t.Context(), "creative", LogQuery{Search: `foo \d$`, Regex: true, Max: 5})
	if res.Matched != 3 {
		t.Fatal(res)
	}
	if _, e := s.queryLogs(t.Context(), "creative", LogQuery{Search: "(", Regex: true}); e == nil {
		t.Fatal("bad regex accepted")
	}
	if _, e := s.queryLogs(t.Context(), "creative", LogQuery{Search: strings.Repeat("a", 300)}); e == nil {
		t.Fatal("huge pattern accepted")
	}
}

func TestLogLinesAreCleanedOfColourCodes(t *testing.T) {
	s, _ := logServer(t, "\x1b[39m[mc-image-helper] \x1b[0;39mdone")
	res, _ := s.queryLogs(t.Context(), "creative", LogQuery{Tail: 5, Max: 5})
	if len(res.Lines) != 1 || strings.Contains(res.Lines[0], "\x1b") {
		t.Fatal(res.Lines)
	}
}

func TestLogRoutes(t *testing.T) {
	s, tails := logServer(t, "[10:00:00] [main/INFO]: hello", "[10:00:01] [main/WARN]: careful")
	var plain []string
	w := controllerRequest(s, "GET", "/servers/creative/logs", nil)
	if w.Code != 200 || json.Unmarshal(w.Body.Bytes(), &plain) != nil || len(plain) != 2 {
		t.Fatal("the plain snapshot changed shape:", w.Code, w.Body.String())
	}
	var res LogResult
	w = controllerRequest(s, "GET", "/servers/creative/logs?q=care&level=WARN&tail=all", nil)
	if w.Code != 200 || json.Unmarshal(w.Body.Bytes(), &res) != nil || res.Matched != 1 || *tails != 1 {
		t.Fatal(w.Code, w.Body.String(), *tails)
	}
	for _, bad := range []string{"tail=0", "tail=99999999", "level=LOUD", "max=0", "q=(&regex=1"} {
		if w := controllerRequest(s, "GET", "/servers/creative/logs?"+bad, nil); w.Code != 400 {
			t.Errorf("%s: %d", bad, w.Code)
		}
	}
	w = controllerRequest(s, "GET", "/servers/creative/logs/download", nil)
	if w.Code != 200 || !strings.Contains(w.Body.String(), "careful") || !strings.Contains(w.Header().Get("Content-Disposition"), "creative-console.log") {
		t.Fatal(w.Code, w.Header(), w.Body.String())
	}
	if w := controllerRequest(s, "GET", "/servers/nope/logs", nil); w.Code != 404 {
		t.Fatal(w.Code)
	}
}
