package backup

import (
	"archive/zip"
	"bytes"
	"compress/gzip"
	"encoding/binary"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// levelDat is a minimal gzip NBT: {"": {Data: {Version: {Name: version}}}}.
func levelDat(version string) []byte {
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

func write(t *testing.T, p string, data []byte) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(p), 0o750); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(p, data, 0o640); err != nil {
		t.Fatal(err)
	}
}

func server(t *testing.T, version string) string {
	dir := t.TempDir()
	write(t, filepath.Join(dir, "world/level.dat"), levelDat(version))
	write(t, filepath.Join(dir, "world/region/r.0.0.mca"), []byte("chunks"))
	write(t, filepath.Join(dir, "world_nether/level.dat"), levelDat(version))
	write(t, filepath.Join(dir, "plugins/claims/data.db"), []byte("claims"))
	write(t, filepath.Join(dir, "plugins/network-display.jar"), []byte("managed"))
	write(t, filepath.Join(dir, "plugins/.paper-remapped/x.jar"), []byte("cache"))
	write(t, filepath.Join(dir, "ops.json"), []byte("[]"))
	write(t, filepath.Join(dir, "server.properties"), []byte("not portable"))
	write(t, filepath.Join(dir, "logs/latest.log"), []byte("not portable"))
	return dir
}

func TestWorldVersion(t *testing.T) {
	if v, e := WorldVersion(levelDat("26.3")); e != nil || v != "26.3" {
		t.Fatal(v, e)
	}
	if _, e := WorldVersion(levelDat("snapshot")); e == nil {
		t.Fatal("unknown version accepted")
	}
	if _, e := WorldVersion([]byte("junk")); e == nil {
		t.Fatal("junk accepted")
	}
}

func TestRoundTrip(t *testing.T) {
	data, out := server(t, "26.3"), t.TempDir()
	info, e := Create(data, "creative", "26.3", out, "export")
	if e != nil || info.Files != 5 || !generatedRE.MatchString(info.Name) || info.SHA256 == "" {
		t.Fatal(info, e)
	}
	plan, e := Inspect(filepath.Join(out, info.Name), "creative", "26.3")
	if e != nil {
		t.Fatal(e)
	}
	if strings.Join(plan.Roots, ",") != "ops.json,plugins,world,world_nether" {
		t.Fatal(plan.Roots)
	}
	// The server moves on: edited world, an extra world the snapshot lacks, a new plugin file.
	write(t, filepath.Join(data, "world/region/r.0.0.mca"), []byte("newer"))
	write(t, filepath.Join(data, "world_extra/level.dat"), levelDat("26.3"))
	write(t, filepath.Join(data, "plugins/new.yml"), []byte("x"))
	if _, e = plan.Restore(data); e != nil {
		t.Fatal(e)
	}
	got, _ := os.ReadFile(filepath.Join(data, "world/region/r.0.0.mca"))
	if string(got) != "chunks" {
		t.Fatal("world not restored:", string(got))
	}
	for _, gone := range []string{"world_extra", "plugins/new.yml"} {
		if _, e := os.Stat(filepath.Join(data, gone)); e == nil {
			t.Fatal(gone, "survived the restore")
		}
	}
	if b, _ := os.ReadFile(filepath.Join(data, "server.properties")); string(b) != "not portable" {
		t.Fatal("network configuration touched")
	}
	if _, e := os.Stat(filepath.Join(data, "plugins/.paper-remapped")); e == nil {
		t.Fatal("remapped cache was exported")
	}
	entries, _ := os.ReadDir(data)
	for _, entry := range entries {
		if strings.HasPrefix(entry.Name(), ".archive-") {
			t.Fatal("staging folder left behind")
		}
	}
	list, e := List(out)
	if e != nil || len(list) != 1 || list[0].Server != "creative" || list[0].Kind != "export" || list[0].Files != 5 {
		t.Fatal(list, e)
	}
}

func TestRefusals(t *testing.T) {
	out := t.TempDir()
	if _, e := Create(server(t, "1.21.9"), "creative", "26.3", out, "export"); e == nil {
		t.Fatal("old world exported as 26.3")
	}
	if _, e := Create(t.TempDir(), "creative", "26.3", out, "export"); e == nil {
		t.Fatal("empty server exported")
	}
	info, e := Create(server(t, "26.3"), "creative", "26.3", out, "export")
	if e != nil {
		t.Fatal(e)
	}
	zipPath := filepath.Join(out, info.Name)
	if _, e = Inspect(zipPath, "survival", "26.3"); e == nil {
		t.Fatal("restored on the wrong server")
	}
	if _, e = Inspect(zipPath, "creative", "26.4"); e == nil {
		t.Fatal("restored on another Minecraft version")
	}
	// A flipped byte must trip the sidecar checksum before anything else.
	raw, _ := os.ReadFile(zipPath)
	raw[len(raw)/2] ^= 0xff
	os.WriteFile(zipPath, raw, 0o600)
	if _, e = Inspect(zipPath, "creative", "26.3"); e == nil {
		t.Fatal("corrupted ZIP accepted")
	}
}

func TestUnsafeZIP(t *testing.T) {
	for name, entry := range map[string]string{"traversal": "payload/../evil", "absolute": "/etc/passwd", "backslash": "payload\\evil"} {
		p := filepath.Join(t.TempDir(), "x.zip")
		var b bytes.Buffer
		w := zip.NewWriter(&b)
		f, _ := w.Create(entry)
		f.Write([]byte("x"))
		w.Close()
		os.WriteFile(p, b.Bytes(), 0o600)
		if _, e := Inspect(p, "creative", "26.3"); e == nil {
			t.Fatal(name, "accepted")
		}
	}
	plain := filepath.Join(t.TempDir(), "world.zip")
	var b bytes.Buffer
	w := zip.NewWriter(&b)
	f, _ := w.Create("world/level.dat")
	f.Write(levelDat("26.3"))
	w.Close()
	os.WriteFile(plain, b.Bytes(), 0o600)
	if _, e := Inspect(plain, "creative", "26.3"); e == nil || !strings.Contains(e.Error(), "manifest") {
		t.Fatal("plain world ZIP not refused with a clear message:", e)
	}
}

func TestNames(t *testing.T) {
	for _, ok := range []string{"creative-export-20261025T142341123456Z.zip", "my backup.zip"[:2] + ".zip", "a_b-c.1.zip"} {
		if !NameRE.MatchString(ok) {
			t.Fatal(ok)
		}
	}
	for _, bad := range []string{"../x.zip", "a/b.zip", ".hidden.zip", "x.zip.sha256", "x.jar", strings.Repeat("a", 130) + ".zip", "a b.zip"} {
		if NameRE.MatchString(bad) {
			t.Fatal(bad, "accepted")
		}
	}
}
