package control

import (
	"bytes"
	"compress/gzip"
	"encoding/binary"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func nbtLevel(version string) []byte {
	var b bytes.Buffer
	str := func(s string) { binary.Write(&b, binary.BigEndian, uint16(len(s))); b.WriteString(s) }
	b.WriteByte(10)
	str("")
	b.WriteByte(10)
	str("Data")
	b.WriteByte(10)
	str("Version")
	b.WriteByte(8)
	str("Name")
	str(version)
	b.Write([]byte{0, 0, 0})
	var z bytes.Buffer
	g := gzip.NewWriter(&z)
	g.Write(b.Bytes())
	g.Close()
	return z.Bytes()
}

func backupServer(t *testing.T) (*Server, http.Handler, string) {
	t.Helper()
	data, backups := t.TempDir(), t.TempDir()
	for _, f := range []struct {
		path string
		body []byte
	}{{"creative/world/level.dat", nbtLevel("26.3")}, {"creative/world/region/r.0.0.mca", []byte("chunks")}, {"creative/plugins/a.yml", []byte("a")}} {
		p := filepath.Join(data, f.path)
		os.MkdirAll(filepath.Dir(p), 0o750)
		os.WriteFile(p, f.body, 0o640)
	}
	// A stopped container: no RCON freeze, restore does not need to restart anything.
	docker := fakeDocker(t, func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.URL.Path == "/v1.44/containers/json":
			io.WriteString(w, `[{"Id":"c"}]`)
		case strings.HasSuffix(r.URL.Path, "/json"):
			io.WriteString(w, `{"State":{"Status":"exited"}}`)
		default:
			w.WriteHeader(204)
		}
	})
	s := &Server{DataRoot: data, Docker: docker, Token: strings.Repeat("t", 40), BackupDir: backups, Version: "26.3"}
	return s, s.Handler(), data
}

func call(t *testing.T, h http.Handler, method, url string, body io.Reader) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(method, url, body)
	req.Header.Set("Authorization", "Bearer "+strings.Repeat("t", 40))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func waitJob(t *testing.T, h http.Handler, id string) Job {
	t.Helper()
	for i := 0; i < 100; i++ {
		var j Job
		json.Unmarshal(call(t, h, "GET", "/jobs/"+id, nil).Body.Bytes(), &j)
		if j.State != "running" {
			return j
		}
		time.Sleep(50 * time.Millisecond)
	}
	t.Fatal("job did not finish")
	return Job{}
}

func TestBackupDownloadUploadRestoreDelete(t *testing.T) {
	_, h, data := backupServer(t)
	rec := call(t, h, "POST", "/servers/creative/backups", nil)
	var job Job
	json.Unmarshal(rec.Body.Bytes(), &job)
	if rec.Code != 202 || job.ID == "" {
		t.Fatal(rec.Code, rec.Body.String())
	}
	if done := waitJob(t, h, job.ID); done.State != "done" || done.File == "" {
		t.Fatal(done)
	}
	var list struct {
		Backups []struct{ Name, Server, Kind string }
	}
	json.Unmarshal(call(t, h, "GET", "/backups", nil).Body.Bytes(), &list)
	if len(list.Backups) != 1 || list.Backups[0].Server != "creative" || list.Backups[0].Kind != "export" {
		t.Fatal(list)
	}
	name := list.Backups[0].Name
	zipBytes := call(t, h, "GET", "/backups/"+name, nil)
	if zipBytes.Code != 200 || zipBytes.Header().Get("Content-Type") != "application/zip" || zipBytes.Body.Len() < 100 {
		t.Fatal(zipBytes.Code, zipBytes.Header())
	}

	// Upload the same bytes under a new name, change the server, restore from the upload.
	if r := call(t, h, "PUT", "/backups/uploaded.zip", bytes.NewReader(zipBytes.Body.Bytes())); r.Code != 201 {
		t.Fatal(r.Code, r.Body.String())
	}
	if r := call(t, h, "PUT", "/backups/uploaded.zip", bytes.NewReader(zipBytes.Body.Bytes())); r.Code != 409 {
		t.Fatal("overwrite allowed:", r.Code)
	}
	if r := call(t, h, "PUT", "/backups/junk.zip", strings.NewReader("not a zip")); r.Code != 400 {
		t.Fatal("junk accepted:", r.Code)
	}
	if r := call(t, h, "PUT", "/backups/..%2Fevil.zip", strings.NewReader("x")); r.Code == 201 {
		t.Fatal("traversal name accepted")
	}
	os.WriteFile(filepath.Join(data, "creative/world/region/r.0.0.mca"), []byte("newer"), 0o640)
	rec = call(t, h, "POST", "/servers/creative/restore/uploaded.zip", nil)
	json.Unmarshal(rec.Body.Bytes(), &job)
	if rec.Code != 202 {
		t.Fatal(rec.Code, rec.Body.String())
	}
	if done := waitJob(t, h, job.ID); done.State != "done" {
		t.Fatal(done)
	}
	got, _ := os.ReadFile(filepath.Join(data, "creative/world/region/r.0.0.mca"))
	if string(got) != "chunks" {
		t.Fatal("not restored:", string(got))
	}
	// The restore kept the state it replaced.
	json.Unmarshal(call(t, h, "GET", "/backups", nil).Body.Bytes(), &list)
	kinds := map[string]bool{}
	for _, b := range list.Backups {
		kinds[b.Kind] = true
	}
	if !kinds["before-restore"] || !kinds["uploaded"] && !kinds["export"] {
		t.Fatal(kinds)
	}
	if r := call(t, h, "DELETE", "/backups/uploaded.zip", nil); r.Code != 200 {
		t.Fatal(r.Code)
	}
	if r := call(t, h, "GET", "/backups/uploaded.zip", nil); r.Code != 404 {
		t.Fatal(r.Code)
	}
	if r := call(t, h, "POST", "/servers/velocity/backups", nil); r.Code != 400 {
		t.Fatal("proxy backed up:", r.Code)
	}
	if r := call(t, h, "POST", "/servers/creative/restore/missing.zip", nil); r.Code != 404 {
		t.Fatal(r.Code)
	}
}
