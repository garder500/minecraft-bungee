package control

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"
)

// fakeGoogle implements just enough of OAuth and Drive for the client: token endpoint, folders, files,
// resumable uploads (with an injectable failure) and delete.
type fakeGoogle struct {
	mu       sync.Mutex
	srv      *httptest.Server
	badRefr  bool
	failOnce bool
	files    map[string]*DriveFile
	bodies   map[string][]byte
	upload   []byte
	meta     map[string]any
	next     int
	tokens   int
	quota    bool
}

func newGoogle(t *testing.T) (*fakeGoogle, *Drive) {
	g := &fakeGoogle{files: map[string]*DriveFile{}, bodies: map[string][]byte{}}
	mux := http.NewServeMux()
	mux.HandleFunc("/token", func(w http.ResponseWriter, r *http.Request) {
		r.ParseForm()
		g.mu.Lock()
		defer g.mu.Unlock()
		g.tokens++
		w.Header().Set("Content-Type", "application/json")
		switch r.Form.Get("grant_type") {
		case "authorization_code":
			if r.Form.Get("code") != "good-code" || r.Form.Get("redirect_uri") != "https://panel.test/cb" || r.Form.Get("client_secret") != "shh" {
				w.WriteHeader(400)
				io.WriteString(w, `{"error":"invalid_grant"}`)
				return
			}
			io.WriteString(w, `{"access_token":"A1","refresh_token":"R1","expires_in":3600}`)
		case "refresh_token":
			if g.badRefr || r.Form.Get("refresh_token") != "R1" {
				w.WriteHeader(400)
				io.WriteString(w, `{"error":"invalid_grant","error_description":"Token has been expired or revoked."}`)
				return
			}
			io.WriteString(w, fmt.Sprintf(`{"access_token":"A%d","expires_in":3600}`, 10+g.tokens))
		}
	})
	mux.HandleFunc("/revoke", func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(200) })
	auth := func(w http.ResponseWriter, r *http.Request) bool {
		if !strings.HasPrefix(r.Header.Get("Authorization"), "Bearer A") {
			w.WriteHeader(401)
			return false
		}
		return true
	}
	mux.HandleFunc("/drive/files", func(w http.ResponseWriter, r *http.Request) {
		if !auth(w, r) {
			return
		}
		g.mu.Lock()
		defer g.mu.Unlock()
		if r.Method == "POST" {
			var in map[string]any
			json.NewDecoder(r.Body).Decode(&in)
			g.next++
			id := fmt.Sprintf("folder%d", g.next)
			g.files[id] = &DriveFile{ID: id, Name: in["name"].(string)}
			fmt.Fprintf(w, `{"id":%q}`, id)
			return
		}
		var out []DriveFile
		parent := strings.Split(strings.Split(r.URL.Query().Get("q"), "'")[1], "'")[0]
		for _, f := range g.files {
			if f.AppProperties != nil && f.AppProperties["parent"] == parent {
				out = append(out, *f)
			}
		}
		json.NewEncoder(w).Encode(map[string]any{"files": out})
	})
	mux.HandleFunc("/drive/files/", func(w http.ResponseWriter, r *http.Request) {
		if !auth(w, r) {
			return
		}
		g.mu.Lock()
		defer g.mu.Unlock()
		id := strings.TrimPrefix(r.URL.Path, "/drive/files/")
		f := g.files[id]
		if f == nil {
			w.WriteHeader(404)
			io.WriteString(w, `{"error":{"message":"File not found","errors":[{"reason":"notFound"}]}}`)
			return
		}
		if r.Method == "DELETE" {
			delete(g.files, id)
			w.WriteHeader(204)
			return
		}
		fmt.Fprintf(w, `{"id":%q,"trashed":false}`, id)
	})
	mux.HandleFunc("/upload/files", func(w http.ResponseWriter, r *http.Request) {
		if !auth(w, r) {
			return
		}
		g.mu.Lock()
		defer g.mu.Unlock()
		if g.quota {
			w.WriteHeader(403)
			io.WriteString(w, `{"error":{"message":"The user's Drive storage quota has been exceeded.","errors":[{"reason":"storageQuotaExceeded"}]}}`)
			return
		}
		json.NewDecoder(r.Body).Decode(&g.meta)
		g.upload = nil
		w.Header().Set("Location", g.srv.URL+"/session/1")
	})
	mux.HandleFunc("/session/1", func(w http.ResponseWriter, r *http.Request) {
		g.mu.Lock()
		defer g.mu.Unlock()
		cr := strings.TrimPrefix(r.Header.Get("Content-Range"), "bytes ")
		rng, total, _ := strings.Cut(cr, "/")
		body, _ := io.ReadAll(r.Body)
		if rng != "*" {
			if g.failOnce && len(g.upload) > 0 {
				g.failOnce = false
				w.WriteHeader(500)
				return
			}
			start, _ := strconv.Atoi(strings.Split(rng, "-")[0])
			if start != len(g.upload) {
				w.WriteHeader(400)
				return
			}
			g.upload = append(g.upload, body...)
		}
		n, _ := strconv.Atoi(total)
		if len(g.upload) >= n {
			g.next++
			id := fmt.Sprintf("file%d", g.next)
			props := map[string]string{"parent": g.meta["parents"].([]any)[0].(string)}
			for k, v := range g.meta["appProperties"].(map[string]any) {
				props[k] = v.(string)
			}
			g.files[id] = &DriveFile{ID: id, Name: g.meta["name"].(string), AppProperties: props}
			g.bodies[id] = append([]byte(nil), g.upload...)
			fmt.Fprintf(w, `{"id":%q}`, id)
			return
		}
		if len(g.upload) > 0 {
			w.Header().Set("Range", fmt.Sprintf("bytes=0-%d", len(g.upload)-1))
		}
		w.WriteHeader(308)
	})
	g.srv = httptest.NewServer(mux)
	t.Cleanup(g.srv.Close)
	d := NewDrive()
	d.AuthURL, d.TokenURL, d.RevokeURL = g.srv.URL+"/auth", g.srv.URL+"/token", g.srv.URL+"/revoke"
	d.APIURL, d.UploadURL = g.srv.URL+"/drive", g.srv.URL+"/upload"
	d.Chunk, d.Wait = 256<<10, time.Millisecond
	return g, d
}

func TestConsentAndTokenExchange(t *testing.T) {
	g, d := newGoogle(t)
	u, _ := url.Parse(d.ConsentURL("cid", "https://panel.test/cb", "st4te"))
	q := u.Query()
	if q.Get("access_type") != "offline" || q.Get("prompt") != "consent" || q.Get("scope") != driveScope || q.Get("redirect_uri") != "https://panel.test/cb" || q.Get("state") != "st4te" {
		t.Fatal(q)
	}
	ctx := context.Background()
	if _, e := d.Exchange(ctx, "cid", "shh", "bad", "https://panel.test/cb"); !errors.Is(e, ErrReconnect) {
		t.Fatal("bad code:", e)
	}
	if _, e := d.Exchange(ctx, "cid", "shh", "good-code", "https://other/cb"); e == nil {
		t.Fatal("redirect_uri must match the consent request")
	}
	refresh, e := d.Exchange(ctx, "cid", "shh", "good-code", "https://panel.test/cb")
	if e != nil || refresh != "R1" {
		t.Fatal(refresh, e)
	}
	a1, e := d.Access(ctx, "cid", "shh", "R1")
	a2, _ := d.Access(ctx, "cid", "shh", "R1")
	if e != nil || a1 != a2 {
		t.Fatal("access token not cached", a1, a2, e)
	}
	d.Now = func() time.Time { return time.Now().Add(2 * time.Hour) }
	if a3, _ := d.Access(ctx, "cid", "shh", "R1"); a3 == a1 {
		t.Fatal("expired access token reused")
	}
	g.badRefr = true
	d.Now = time.Now
	d.mu.Lock()
	d.token = ""
	d.mu.Unlock()
	if _, e = d.Access(ctx, "cid", "shh", "R1"); !errors.Is(e, ErrReconnect) {
		t.Fatal("revoked grant must ask to reconnect:", e)
	}
}

func bigFile(t *testing.T, size int) (string, []byte) {
	data := make([]byte, size)
	for i := range data {
		data[i] = byte(i * 7)
	}
	p := filepath.Join(t.TempDir(), "x.zip")
	os.WriteFile(p, data, 0o600)
	return p, data
}

func TestResumableUploadSurvivesAMidUploadFailure(t *testing.T) {
	g, d := newGoogle(t)
	ctx := context.Background()
	folder, e := d.Folder(ctx, "A1", "", "Vampyuyu backups")
	if e != nil {
		t.Fatal(e)
	}
	if same, _ := d.Folder(ctx, "A1", folder, "x"); same != folder {
		t.Fatal("existing folder not reused")
	}
	if other, _ := d.Folder(ctx, "A1", "gone", "Vampyuyu backups"); other == "gone" || other == "" {
		t.Fatal("missing folder not recreated")
	}
	path, data := bigFile(t, 700<<10)
	g.failOnce = true
	var last int64
	id, e := d.Upload(ctx, "A1", folder, path, "x.zip", map[string]string{"server": "creative", "kind": "auto"}, func(sent, total int64) { last = sent })
	if e != nil || id == "" {
		t.Fatal(id, e)
	}
	if !bytes.Equal(g.bodies[id], data) || last != int64(len(data)) {
		t.Fatal("uploaded bytes differ", len(g.bodies[id]), last)
	}
	files, e := d.List(ctx, "A1", folder)
	if e != nil || len(files) != 1 || files[0].AppProperties["server"] != "creative" {
		t.Fatal(files, e)
	}
	if e = d.Delete(ctx, "A1", id); e != nil {
		t.Fatal(e)
	}
	if files, _ = d.List(ctx, "A1", folder); len(files) != 0 {
		t.Fatal("not deleted")
	}
	g.quota = true
	if _, e = d.Upload(ctx, "A1", folder, path, "y.zip", nil, nil); e == nil || !strings.Contains(e.Error(), "storageQuotaExceeded") {
		t.Fatal("Google's quota error must be shown as is:", e)
	}
}

func TestDueDateOncePerLocalDayAcrossDST(t *testing.T) {
	paris, _ := time.LoadLocation("Europe/Paris")
	a := AutoSettings{Enabled: true, Time: "02:30", Timezone: "Europe/Paris", KeepLocal: 3, Servers: []string{"creative"}}
	at := func(y int, m time.Month, d, h, min int) time.Time { return time.Date(y, m, d, h, min, 0, 0, paris) }
	// Before the hour: not due. After it: due once, then never again that day.
	if _, due := dueDate(a, "", at(2026, 3, 1, 2, 29)); due {
		t.Fatal("due before the hour")
	}
	if date, due := dueDate(a, "", at(2026, 3, 1, 2, 31)); !due || date != "2026-03-01" {
		t.Fatal(date, due)
	}
	if _, due := dueDate(a, "2026-03-01", at(2026, 3, 1, 23, 0)); due {
		t.Fatal("ran twice on one day")
	}
	// Spring forward 2026-03-29: 02:30 does not exist. The run still happens that day, once.
	runs := 0
	last := ""
	for m := 0; m < 24*60; m += 5 {
		now := time.Date(2026, 3, 29, 0, 0, 0, 0, paris).Add(time.Duration(m) * time.Minute)
		if date, due := dueDate(a, last, now); due {
			runs++
			last = date
		}
	}
	if runs != 1 {
		t.Fatal("spring forward day ran", runs)
	}
	// Autumn back 2026-10-25: 02:30 happens twice. Once.
	runs, last = 0, ""
	for m := 0; m < 25*60; m += 5 {
		now := time.Date(2026, 10, 25, 0, 0, 0, 0, paris).Add(time.Duration(m) * time.Minute)
		if now.In(paris).Day() != 25 {
			break
		}
		if date, due := dueDate(a, last, now); due {
			runs++
			last = date
		}
	}
	if runs != 1 {
		t.Fatal("autumn day ran", runs)
	}
	if next, ok := nextRun(a, "2026-03-01", at(2026, 3, 1, 10, 0)); !ok || !next.Equal(at(2026, 3, 2, 2, 30)) {
		t.Fatal(next)
	}
	if _, due := dueDate(AutoSettings{Time: "25:00", Timezone: "Europe/Paris"}, "", time.Now()); due {
		t.Fatal("invalid time must never be due")
	}
}

func TestValidation(t *testing.T) {
	ok := AutoSettings{Enabled: true, Time: "04:00", Timezone: "UTC", Servers: []string{"creative"}, KeepLocal: 3}
	if e := validateAuto(ok); e != nil {
		t.Fatal(e)
	}
	for name, mutate := range map[string]func(*AutoSettings){
		"time":     func(a *AutoSettings) { a.Time = "4:00" },
		"zone":     func(a *AutoSettings) { a.Timezone = "Mars/Base" },
		"local":    func(a *AutoSettings) { a.Timezone = "Local" },
		"server":   func(a *AutoSettings) { a.Servers = []string{"velocity"} },
		"dup":      func(a *AutoSettings) { a.Servers = []string{"lobby", "lobby"} },
		"none":     func(a *AutoSettings) { a.Servers = nil },
		"keep0":    func(a *AutoSettings) { a.KeepLocal = 0 },
		"keep1000": func(a *AutoSettings) { a.KeepLocal = 1000 },
	} {
		bad := ok
		mutate(&bad)
		if validateAuto(bad) == nil {
			t.Fatal(name, "accepted")
		}
	}
	if validateDrive(DriveSettings{Folder: "x", KeepDrive: 0}) == nil || validateDrive(DriveSettings{Folder: "", KeepDrive: 3}) == nil {
		t.Fatal("bad drive settings accepted")
	}
}

func planServer(t *testing.T) (*Server, http.Handler, string, *fakeGoogle) {
	s, h, data := backupServer(t)
	g, d := newGoogle(t)
	s.Drive = d
	s.StateDir = t.TempDir()
	return s, h, data, g
}

func put(t *testing.T, h http.Handler, body string) *httptest.ResponseRecorder {
	return call(t, h, "PUT", "/backup-settings", strings.NewReader(body))
}

func TestSettingsNeverExposeSecretsAndSecretIsWriteOnly(t *testing.T) {
	s, h, _, _ := planServer(t)
	body := `{"auto":{"enabled":true,"time":"05:15","timezone":"Europe/Paris","servers":["creative"],"keepLocal":2},"drive":{"clientId":"cid","clientSecret":"shh","folder":"Mine","upload":true,"keepDrive":4}}`
	if r := put(t, h, body); r.Code != 200 {
		t.Fatal(r.Code, r.Body.String())
	}
	// Connect through the exchange route, then read everything back.
	if r := call(t, h, "POST", "/drive/exchange", strings.NewReader(`{"code":"good-code","redirectUri":"https://panel.test/cb"}`)); r.Code != 200 {
		t.Fatal(r.Code, r.Body.String())
	}
	for _, path := range []string{"/backup-settings"} {
		out := call(t, h, "GET", path, nil).Body.String()
		for _, secret := range []string{"shh", "R1", "A1", "refreshToken", "clientSecret"} {
			if strings.Contains(out, secret) {
				t.Fatalf("%s leaks %q: %s", path, secret, out)
			}
		}
		if !strings.Contains(out, `"connected":true`) || !strings.Contains(out, `"hasClientSecret":true`) || !strings.Contains(out, `"next"`) {
			t.Fatal(out)
		}
	}
	// Saving again without a secret keeps the grant and the secret.
	if r := put(t, h, strings.Replace(body, `"clientSecret":"shh"`, `"clientSecret":""`, 1)); r.Code != 200 {
		t.Fatal(r.Body.String())
	}
	if st := s.store().get(); st.Drive.ClientSecret != "shh" || st.Drive.RefreshToken != "R1" {
		t.Fatal("an empty secret field wiped the stored values")
	}
	// Another Google client means a new grant.
	put(t, h, strings.Replace(strings.Replace(body, `"clientId":"cid"`, `"clientId":"other"`, 1), `"clientSecret":"shh"`, `"clientSecret":""`, 1))
	if st := s.store().get(); st.Drive.RefreshToken != "" || st.Drive.ClientSecret != "" {
		t.Fatal("changing the client kept the old grant")
	}
	if r := put(t, h, `{"auto":{"enabled":true,"time":"99:99","timezone":"UTC","servers":["creative"],"keepLocal":2},"drive":{"folder":"x","keepDrive":1}}`); r.Code != 400 {
		t.Fatal("invalid time accepted:", r.Code)
	}
	info, e := os.Stat(filepath.Join(s.StateDir, "backup-settings.json"))
	if e != nil || info.Mode().Perm() != 0o600 {
		t.Fatal("settings file must be 0600:", e, info.Mode())
	}
}

func TestAuthURLNeedsAClientAndAValidRedirect(t *testing.T) {
	_, h, _, _ := planServer(t)
	ask := func() int {
		return call(t, h, "POST", "/drive/auth-url", strings.NewReader(`{"redirectUri":"https://panel.test/cb","state":"0123456789abcdef0123"}`)).Code
	}
	if ask() != 400 {
		t.Fatal("auth URL given without a client")
	}
	put(t, h, `{"auto":{"time":"04:00","timezone":"UTC","servers":["creative"],"keepLocal":1},"drive":{"clientId":"cid","clientSecret":"shh","folder":"x","keepDrive":1}}`)
	if ask() != 200 {
		t.Fatal("auth URL refused")
	}
	for _, bad := range []string{`{"redirectUri":"javascript:alert(1)","state":"0123456789abcdef0123"}`, `{"redirectUri":"https://panel.test/cb","state":"nope"}`} {
		if call(t, h, "POST", "/drive/auth-url", strings.NewReader(bad)).Code != 400 {
			t.Fatal("accepted", bad)
		}
	}
}

func TestScheduledRunBacksUpUploadsAndPrunes(t *testing.T) {
	s, h, data, g := planServer(t)
	s.Now = func() time.Time { return time.Date(2026, 5, 9, 12, 0, 0, 0, time.UTC) }
	put(t, h, `{"auto":{"enabled":true,"time":"04:00","timezone":"UTC","servers":["creative"],"keepLocal":2},"drive":{"clientId":"cid","clientSecret":"shh","folder":"Mine","upload":true,"keepDrive":2}}`)
	call(t, h, "POST", "/drive/exchange", strings.NewReader(`{"code":"good-code","redirectUri":"https://panel.test/cb"}`))
	// Seed: a manual backup that retention must never touch.
	manual := call(t, h, "POST", "/servers/creative/backups", nil)
	var mj Job
	json.Unmarshal(manual.Body.Bytes(), &mj)
	waitJob(t, h, mj.ID)

	clock := time.Date(2026, 5, 10, 3, 59, 0, 0, time.UTC)
	s.Now = func() time.Time { return clock }
	for day := 0; day < 4; day++ {
		clock = time.Date(2026, 5, 10+day, 4, 1, 0, 0, time.UTC)
		os.WriteFile(filepath.Join(data, "creative/world/region/r.0.0.mca"), []byte(fmt.Sprintf("day%d", day)), 0o640)
		s.schedulerTick(context.Background())
		s.schedulerTick(context.Background()) // a second tick the same day must do nothing
	}
	st := s.store().get()
	if st.LastRunDate != "2026-05-13" || len(st.LastRun) != 1 || st.LastRun[0].Error != "" || !st.LastRun[0].Uploaded {
		t.Fatalf("%+v", st)
	}
	auto, other := 0, 0
	entries, _ := os.ReadDir(s.BackupDir)
	for _, e := range entries {
		switch {
		case strings.HasSuffix(e.Name(), ".sha256"):
		case strings.Contains(e.Name(), "-auto-"):
			auto++
		case strings.HasSuffix(e.Name(), ".zip"):
			other++
		}
	}
	if auto != 2 || other != 1 {
		t.Fatal("local retention: auto", auto, "others", other)
	}
	if n := len(g.files) - 1; n != 2 { // minus the folder
		t.Fatal("Drive retention kept", n)
	}
	for _, f := range g.files {
		if f.AppProperties != nil && f.AppProperties["kind"] != "auto" {
			t.Fatal("a non-automatic file was uploaded by the plan")
		}
	}
	// A restart in the middle of a day must not repeat finished work.
	n := len(entries)
	s2 := &Server{DataRoot: s.DataRoot, Docker: s.Docker, BackupDir: s.BackupDir, Version: "26.3", StateDir: s.StateDir, Drive: s.Drive, Now: s.Now}
	s2.schedulerTick(context.Background())
	if again, _ := os.ReadDir(s.BackupDir); len(again) != n {
		t.Fatal("restart repeated the day's backup")
	}
}

func TestScheduledRunWaitsForABusyServerAndKeepsProgress(t *testing.T) {
	s, h, _, _ := planServer(t)
	s.Now = func() time.Time { return time.Date(2026, 5, 10, 3, 0, 0, 0, time.UTC) }
	put(t, h, `{"auto":{"enabled":true,"time":"04:00","timezone":"UTC","servers":["creative"],"keepLocal":2},"drive":{"folder":"x","upload":false,"keepDrive":1}}`)
	s.Now = func() time.Time { return time.Date(2026, 5, 10, 5, 0, 0, 0, time.UTC) }
	busy, e := s.jobs.start("restore", "creative", "x.zip")
	if e != nil {
		t.Fatal(e)
	}
	s.schedulerTick(context.Background())
	if st := s.store().get(); st.LastRunDate != "" {
		t.Fatal("a skipped server must not mark the day as done")
	}
	s.jobs.finish(busy.ID, nil, "")
	s.schedulerTick(context.Background())
	if st := s.store().get(); st.LastRunDate != "2026-05-10" || st.LastRun[0].Backup == "" {
		t.Fatalf("%+v", st)
	}
}

func TestExpiredGrantIsReportedAsReconnect(t *testing.T) {
	s, h, _, g := planServer(t)
	put(t, h, `{"auto":{"time":"04:00","timezone":"UTC","servers":["creative"],"keepLocal":1},"drive":{"clientId":"cid","clientSecret":"shh","folder":"x","upload":true,"keepDrive":1}}`)
	call(t, h, "POST", "/drive/exchange", strings.NewReader(`{"code":"good-code","redirectUri":"https://panel.test/cb"}`))
	g.badRefr = true
	s.drive().mu.Lock()
	s.drive().token = ""
	s.drive().mu.Unlock()
	if r := call(t, h, "GET", "/drive/files", nil); r.Code != 502 {
		t.Fatal(r.Code)
	}
	out := call(t, h, "GET", "/backup-settings", nil).Body.String()
	if !strings.Contains(out, `"problem":"reconnect"`) {
		t.Fatal(out)
	}
	if r := call(t, h, "POST", "/drive/disconnect", nil); r.Code != 200 || strings.Contains(r.Body.String(), `"connected":true`) {
		t.Fatal(r.Body.String())
	}
}

func TestDiskGuardSkipsABackupThatWouldNotFit(t *testing.T) {
	s, _, _, _ := planServer(t)
	// A 3 GiB sparse file is the "last backup": with that much needed plus margin, the check must compare to free space.
	big := filepath.Join(s.BackupDir, "creative-export-20260101T000000000000Z.zip")
	f, _ := os.Create(big)
	f.Truncate(8 << 40) // 8 TiB: more than any CI disk has free
	f.Close()
	if e := s.diskOK("creative"); e == nil || !strings.Contains(e.Error(), "not enough free disk space") {
		t.Fatal("disk guard did not trigger:", e)
	}
	if e := s.diskOK("survival"); e != nil && !strings.Contains(e.Error(), "not enough free disk space") {
		t.Fatal(e)
	}
}

func TestEnablingTheScheduleAfterTheHourDoesNotRunAtOnce(t *testing.T) {
	s, h, _, _ := planServer(t)
	s.Now = func() time.Time { return time.Date(2026, 5, 10, 20, 0, 0, 0, time.UTC) }
	body := func(hour string) string {
		return `{"auto":{"enabled":true,"time":"` + hour + `","timezone":"UTC","servers":["creative"],"keepLocal":2},"drive":{"folder":"x","upload":false,"keepDrive":1}}`
	}
	put(t, h, body("04:00"))
	s.schedulerTick(context.Background())
	if entries, _ := os.ReadDir(s.BackupDir); len(entries) != 0 {
		t.Fatal("enabling after the hour started a backup")
	}
	// The next local day it runs at the hour.
	s.Now = func() time.Time { return time.Date(2026, 5, 11, 4, 0, 30, 0, time.UTC) }
	s.schedulerTick(context.Background())
	if st := s.store().get(); st.LastRunDate != "2026-05-11" || len(st.LastRun) != 1 {
		t.Fatalf("%+v", st)
	}
	// Moving the hour to one later today is still honoured.
	s.Now = func() time.Time { return time.Date(2026, 5, 12, 9, 0, 0, 0, time.UTC) }
	put(t, h, body("23:00"))
	s.Now = func() time.Time { return time.Date(2026, 5, 12, 23, 1, 0, 0, time.UTC) }
	s.schedulerTick(context.Background())
	if st := s.store().get(); st.LastRunDate != "2026-05-12" {
		t.Fatalf("%+v", st)
	}
}
