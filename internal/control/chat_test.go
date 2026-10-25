package control

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestChatChannelRoutesValidateAndForward(t *testing.T) {
	var got []string
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		got = append(got, r.URL.Path+" "+string(b))
		io.WriteString(w, `{"open":true,"messages":[],"last":0}`)
	}))
	defer upstream.Close()
	docker := fakeDocker(t, func(w http.ResponseWriter, r *http.Request) {
		if strings.Contains(r.URL.Path, "/containers/json") {
			io.WriteString(w, `[{"Id":"managed"}]`)
		} else {
			io.WriteString(w, `{"NetworkSettings":{"Networks":{"game":{"IPAddress":"127.0.0.1"}}}}`)
		}
	})
	s := &Server{Docker: docker, Token: strings.Repeat("c", 64), GameToken: strings.Repeat("g", 64), GameHTTP: &http.Client{Transport: rewriteTransport{base: upstream.URL}}}
	uuid := "12345678-1234-4234-8234-123456789abc"
	post := func(server, body string) *httptest.ResponseRecorder {
		return controllerRequest(s, "POST", "/servers/"+server+"/chat", []byte(body))
	}
	for _, ok := range []string{
		`{"action":"open","uuid":"` + uuid + `","staff":"admin"}`,
		`{"action":"send","uuid":"` + uuid + `","staff":"Jeremy_27","message":"hello"}`,
		`{"action":"read","uuid":"` + uuid + `","since":4}`,
		`{"action":"close","uuid":"` + uuid + `"}`,
	} {
		if w := post("creative", ok); w.Code != 200 {
			t.Fatal(ok, w.Code, w.Body.String())
		}
	}
	if len(got) != 4 || !strings.HasPrefix(got[0], "/chat ") || !strings.Contains(got[2], `"since":4`) {
		t.Fatal(got)
	}
	before := len(got)
	for name, bad := range map[string]string{
		"unknown action": `{"action":"op","uuid":"` + uuid + `"}`,
		"bad uuid":       `{"action":"open","uuid":"nope","staff":"admin"}`,
		"nil uuid":       `{"action":"read","uuid":"00000000-0000-0000-0000-000000000000"}`,
		"markup in name": `{"action":"open","uuid":"` + uuid + `","staff":"<red>x"}`,
		"empty message":  `{"action":"send","uuid":"` + uuid + `","staff":"a","message":"  "}`,
		"long message":   `{"action":"send","uuid":"` + uuid + `","staff":"a","message":"` + strings.Repeat("x", 257) + `"}`,
		"not json":       `nope`,
	} {
		if w := post("creative", bad); w.Code != 400 {
			t.Errorf("%s: %d", name, w.Code)
		}
	}
	if w := post("velocity", `{"action":"read","uuid":"`+uuid+`"}`); w.Code != 400 {
		t.Error("proxy accepted:", w.Code)
	}
	if len(got) != before {
		t.Fatal("an invalid request reached the game server")
	}
	var out map[string]any
	json.Unmarshal(post("creative", `{"action":"read","uuid":"`+uuid+`"}`).Body.Bytes(), &out)
	if out["open"] != true {
		t.Fatal(out)
	}
}
