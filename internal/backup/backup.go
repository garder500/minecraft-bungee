// Package backup writes, checks and restores the portable server ZIPs of scripts/world_archive.py
// ("vampyuyu-server-zip-v1": payload/ files plus a manifest with a SHA-256 and size per file), so a
// ZIP made in the panel restores with `make restore` and the other way round. The caller stops (or
// freezes) the writer of the data directory; this package only touches files.
package backup

import (
	"archive/zip"
	"bufio"
	"bytes"
	"compress/flate"
	"compress/gzip"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"math"
	"os"
	"path"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"syscall"
	"time"
)

const (
	Format   = "vampyuyu-server-zip-v1"
	Limit    = int64(40 << 30)
	MaxFiles = 200000
)

// Servers that carry worlds; the panel database has its own export.
var Servers = []string{"lobby", "creative", "survival"}

var rootFiles = []string{"ops.json", "whitelist.json", "banned-players.json", "banned-ips.json", "usercache.json", "permissions.yml", ".vampyuyu-palace"}
var managedJars = map[string]bool{"lobby-selector.jar": true, "network-display.jar": true, "nexus-arena-engine.jar": true}
var reserved = map[string]bool{"plugins": true, "config": true, "logs": true, "cache": true, "versions": true, "libraries": true, "server.properties": true, "bukkit.yml": true, "spigot.yml": true, "server.jar": true, "paper.jar": true}
var worldRE = regexp.MustCompile(`^[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}$`)
var versionRE = regexp.MustCompile(`^(1\.\d+(\.\d+)?|26\.\d+(\.\d+)?)$`)
var sha256RE = regexp.MustCompile(`^[0-9a-f]{64}$`)

// NameRE is the only shape of backup file name the panel accepts, for downloads, uploads and deletes.
var NameRE = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,119}\.zip$`)
var generatedRE = regexp.MustCompile(`^(lobby|creative|survival)-([a-z-]+)-\d{8}T\d+Z\.zip$`)

type FileMeta struct {
	SHA256 string `json:"sha256"`
	Size   int64  `json:"size"`
}
type Manifest struct {
	Format    string              `json:"format"`
	Server    string              `json:"server"`
	Minecraft string              `json:"minecraft"`
	CreatedAt string              `json:"createdAt"`
	Roots     []string            `json:"roots"`
	Files     map[string]FileMeta `json:"files"`
}

// Info is one ZIP in the backup folder.
type Info struct {
	Name      string `json:"name"`
	Server    string `json:"server,omitempty"`
	Kind      string `json:"kind"`
	Minecraft string `json:"minecraft,omitempty"`
	Bytes     int64  `json:"bytes"`
	CreatedAt string `json:"createdAt"`
	Files     int    `json:"files"`
	SHA256    string `json:"sha256,omitempty"`
	Problem   string `json:"problem,omitempty"`
}

func worldName(name string) bool { return worldRE.MatchString(name) && !reserved[name] }

func isRootFile(name string) bool {
	for _, n := range rootFiles {
		if n == name {
			return true
		}
	}
	return false
}

// WorldVersion reads Data.Version.Name from a level.dat (gzip NBT), never from a file name.
func WorldVersion(raw []byte) (string, error) {
	g, e := gzip.NewReader(bytes.NewReader(raw))
	if e != nil {
		return "", errors.New("level.dat is not gzip data")
	}
	defer g.Close()
	data, e := io.ReadAll(io.LimitReader(g, 4<<20+1))
	if e != nil || len(data) > 4<<20 {
		return "", errors.New("level.dat metadata too large or unreadable")
	}
	pos := 0
	take := func(n int) ([]byte, error) {
		if n < 0 || n > len(data)-pos {
			return nil, errors.New("invalid level.dat")
		}
		b := data[pos : pos+n]
		pos += n
		return b, nil
	}
	u8 := func() (int, error) {
		b, e := take(1)
		if e != nil {
			return 0, e
		}
		return int(b[0]), nil
	}
	u16 := func() (int, error) {
		b, e := take(2)
		if e != nil {
			return 0, e
		}
		return int(binary.BigEndian.Uint16(b)), nil
	}
	i32 := func() (int, error) {
		b, e := take(4)
		if e != nil {
			return 0, e
		}
		return int(int32(binary.BigEndian.Uint32(b))), nil
	}
	text := func() (string, error) {
		n, e := u16()
		if e != nil {
			return "", e
		}
		b, e := take(n)
		return string(b), e
	}
	var value func(kind, depth int) (any, error)
	value = func(kind, depth int) (any, error) {
		if depth > 64 {
			return nil, errors.New("NBT nesting limit exceeded")
		}
		switch kind {
		case 1, 2, 3, 4, 5, 6:
			_, e := take(map[int]int{1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}[kind])
			return nil, e
		case 8:
			return text()
		case 7, 11, 12:
			n, e := i32()
			if e != nil {
				return nil, e
			}
			size := map[int]int{7: 1, 11: 4, 12: 8}[kind]
			if n < 0 || n > math.MaxInt32/size {
				return nil, errors.New("invalid NBT array")
			}
			_, e = take(n * size)
			return nil, e
		case 9:
			sub, e := u8()
			if e != nil {
				return nil, e
			}
			n, e := i32()
			if e != nil || n < 0 || n > 1000000 {
				return nil, errors.New("invalid NBT list")
			}
			for i := 0; i < n; i++ {
				if _, e = value(sub, depth+1); e != nil {
					return nil, e
				}
			}
			return nil, nil
		case 10:
			out := map[string]any{}
			for {
				sub, e := u8()
				if e != nil {
					return nil, e
				}
				if sub == 0 {
					return out, nil
				}
				name, e := text()
				if e != nil {
					return nil, e
				}
				v, e := value(sub, depth+1)
				if e != nil {
					return nil, e
				}
				out[name] = v
			}
		}
		return nil, errors.New("unsupported NBT tag")
	}
	if k, e := u8(); e != nil || k != 10 {
		return "", errors.New("level.dat must be an NBT compound")
	}
	if _, e = text(); e != nil {
		return "", e
	}
	root, e := value(10, 0)
	if e != nil {
		return "", e
	}
	tag, _ := root.(map[string]any)
	d, _ := tag["Data"].(map[string]any)
	v, _ := d["Version"].(map[string]any)
	name, _ := v["Name"].(string)
	if !versionRE.MatchString(name) {
		return "", errors.New("world version cannot be confirmed; inspect this archive manually")
	}
	return name, nil
}

// roots lists what a server ZIP carries: worlds (folders with a level.dat), plugins, root files.
func roots(dataDir string) ([]string, error) {
	entries, e := os.ReadDir(dataDir)
	if e != nil {
		return nil, e
	}
	var out []string
	for _, entry := range entries {
		name := entry.Name()
		switch {
		case strings.HasPrefix(name, ".archive-"):
		case isRootFile(name):
			out = append(out, name)
		case name == "plugins" && entry.Type().IsDir():
			out = append(out, name)
		case entry.Type().IsDir() && worldName(name):
			if st, e := os.Lstat(filepath.Join(dataDir, name, "level.dat")); e == nil && st.Mode().IsRegular() {
				out = append(out, name)
			}
		}
	}
	sort.Strings(out)
	return out, nil
}

func fileSHA(p string) (string, error) {
	f, e := os.Open(p)
	if e != nil {
		return "", e
	}
	defer f.Close()
	h := sha256.New()
	if _, e = io.Copy(h, f); e != nil {
		return "", e
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

// Create writes <outDir>/<server>-<kind>-<UTC stamp>.zip and its .sha256. The data must not change
// underneath it (server stopped, or autosave off and flushed).
func Create(dataDir, server, version, outDir, kind string) (Info, error) {
	if !contains(Servers, server) || !regexp.MustCompile(`^[a-z]+(-[a-z]+)*$`).MatchString(kind) {
		return Info{}, errors.New("invalid server or backup kind")
	}
	selected, e := roots(dataDir)
	if e != nil || len(selected) == 0 {
		return Info{}, errors.New("no saved data to export")
	}
	worlds := 0
	for _, root := range selected {
		raw, e := os.ReadFile(filepath.Join(dataDir, root, "level.dat"))
		if e != nil {
			continue
		}
		worlds++
		if v, e := WorldVersion(raw); e != nil || v != version {
			return Info{}, fmt.Errorf("world %s: saved version differs from configured version %s", root, version)
		}
	}
	if worlds == 0 {
		return Info{}, errors.New("no saved worlds to export")
	}
	if e = os.MkdirAll(outDir, 0o700); e != nil {
		return Info{}, errors.New("backup folder unavailable")
	}
	now := time.Now().UTC()
	name := fmt.Sprintf("%s-%s-%s%06dZ.zip", server, kind, now.Format("20060102T150405"), now.Nanosecond()/1000)
	final := filepath.Join(outDir, name)
	partial := final + ".partial"
	out, e := os.OpenFile(partial, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if e != nil {
		return Info{}, errors.New("cannot create backup file")
	}
	ok := false
	defer func() {
		out.Close()
		if !ok {
			os.Remove(partial)
		}
	}()
	zw := zip.NewWriter(out)
	zw.RegisterCompressor(zip.Deflate, func(w io.Writer) (io.WriteCloser, error) { return flate.NewWriter(w, 1) })
	files := map[string]FileMeta{}
	var expanded int64
	for _, root := range selected {
		base := filepath.Join(dataDir, root)
		e = filepath.WalkDir(base, func(p string, d fs.DirEntry, err error) error {
			if err != nil {
				return err
			}
			rel, _ := filepath.Rel(dataDir, p)
			rel = filepath.ToSlash(rel)
			if d.Type()&fs.ModeSymlink != 0 {
				return fmt.Errorf("symlinks cannot be exported: %s", rel)
			}
			if d.IsDir() {
				if rel == "plugins/.paper-remapped" {
					return filepath.SkipDir
				}
				return nil
			}
			if !d.Type().IsRegular() {
				return errors.New("only regular files can be exported")
			}
			if strings.HasPrefix(rel, "plugins/") && managedJars[path.Base(rel)] {
				return nil
			}
			if len(files) >= MaxFiles-1 {
				return errors.New("export exceeds supported ZIP limits")
			}
			src, err := os.Open(p)
			if err != nil {
				return err
			}
			defer src.Close()
			header := &zip.FileHeader{Name: "payload/" + rel, Method: zip.Deflate, Modified: time.Now()}
			header.SetMode(0o600)
			dst, err := zw.CreateHeader(header)
			if err != nil {
				return err
			}
			h := sha256.New()
			n, err := io.Copy(io.MultiWriter(dst, h), src)
			if err != nil {
				return err
			}
			expanded += n
			if expanded > Limit {
				return errors.New("export exceeds supported ZIP limits")
			}
			files[rel] = FileMeta{SHA256: hex.EncodeToString(h.Sum(nil)), Size: n}
			return nil
		})
		if e != nil {
			return Info{}, e
		}
	}
	manifest := Manifest{Format: Format, Server: server, Minecraft: version, CreatedAt: now.Format(time.RFC3339), Roots: selected, Files: files}
	mw, e := zw.Create("manifest.json")
	if e != nil {
		return Info{}, e
	}
	enc := json.NewEncoder(mw)
	enc.SetIndent("", "  ")
	if e = enc.Encode(manifest); e != nil {
		return Info{}, e
	}
	if e = zw.Close(); e != nil {
		return Info{}, e
	}
	if e = out.Sync(); e != nil {
		return Info{}, e
	}
	if e = os.Link(partial, final); e != nil {
		return Info{}, errors.New("backup file already exists")
	}
	os.Remove(partial)
	ok = true
	sum, e := fileSHA(final)
	if e != nil {
		return Info{}, e
	}
	if e = os.WriteFile(final+".sha256", []byte(sum+"  "+name+"\n"), 0o600); e != nil {
		return Info{}, e
	}
	st, _ := os.Stat(final)
	return Info{Name: name, Server: server, Kind: kind, Minecraft: version, Bytes: st.Size(), CreatedAt: manifest.CreatedAt, Files: len(files), SHA256: sum}, nil
}

func contains(list []string, v string) bool {
	for _, s := range list {
		if s == v {
			return true
		}
	}
	return false
}

// Plan is a validated ZIP, ready to stage.
type Plan struct {
	Zip      string
	Server   string
	Version  string
	Roots    []string
	Files    map[string]string // zip entry -> declared SHA-256
	Rel      map[string]string // zip entry -> path under the server data directory
	Expanded int64
	Created  string
}

// Inspect validates the ZIP without extracting anything: entry names, sizes, the manifest, the sidecar
// checksum, the destination server and the Minecraft version of every world inside.
func Inspect(zipPath, server, version string) (*Plan, error) {
	if !contains(Servers, server) {
		return nil, errors.New("invalid server")
	}
	if sum, e := os.ReadFile(zipPath + ".sha256"); e == nil {
		fields := strings.Fields(string(sum))
		actual, e := fileSHA(zipPath)
		if e != nil || len(fields) == 0 || fields[0] != actual {
			return nil, errors.New("archive SHA-256 mismatch")
		}
	}
	z, e := zip.OpenReader(zipPath)
	if e != nil {
		return nil, errors.New("not a readable ZIP file")
	}
	defer z.Close()
	if len(z.File) > MaxFiles {
		return nil, errors.New("too many ZIP entries")
	}
	seen := map[string]bool{}
	entries := map[string]*zip.File{}
	var total int64
	for _, f := range z.File {
		name := strings.TrimSuffix(f.Name, "/")
		if name == "" || strings.ContainsAny(f.Name, "\\\x00:") || strings.HasPrefix(name, "/") || path.Clean(name) != name || name == ".." || strings.HasPrefix(name, "../") {
			return nil, fmt.Errorf("unsafe ZIP path: %q", f.Name)
		}
		if f.Mode()&os.ModeSymlink != 0 || (!f.FileInfo().IsDir() && !f.Mode().IsRegular()) {
			return nil, errors.New("ZIP links and special files are forbidden")
		}
		if seen[strings.ToLower(name)] {
			return nil, errors.New("duplicate ZIP entry")
		}
		seen[strings.ToLower(name)] = true
		if f.Flags&1 != 0 {
			return nil, errors.New("encrypted ZIPs are unsupported")
		}
		total += int64(f.UncompressedSize64)
		if total > Limit || total < 0 {
			return nil, errors.New("ZIP expansion limit exceeded")
		}
		if !f.FileInfo().IsDir() {
			entries[name] = f
		}
	}
	mf := entries["manifest.json"]
	if mf == nil {
		return nil, errors.New("not a server backup (manifest.json missing); only ZIPs made by the panel or `make zip` can be restored here")
	}
	if mf.UncompressedSize64 > 32<<20 {
		return nil, errors.New("manifest too large")
	}
	rc, e := mf.Open()
	if e != nil {
		return nil, e
	}
	var m Manifest
	e = json.NewDecoder(io.LimitReader(rc, 32<<20)).Decode(&m)
	rc.Close()
	if e != nil {
		return nil, errors.New("invalid manifest")
	}
	if m.Format != Format || m.Server != server {
		return nil, fmt.Errorf("this ZIP is for %q, not %q", m.Server, server)
	}
	if m.Minecraft != version {
		return nil, fmt.Errorf("Minecraft version mismatch (ZIP %s, server %s); upgrades and downgrades need a reviewed migration", m.Minecraft, version)
	}
	if len(m.Roots) == 0 || len(m.Files) == 0 {
		return nil, errors.New("invalid manifest")
	}
	rootSet := map[string]bool{}
	var worlds []string
	for _, r := range m.Roots {
		if rootSet[r] || r == "" || strings.Contains(r, "/") || r == "." || r == ".." {
			return nil, errors.New("invalid restore root")
		}
		rootSet[r] = true
		if r != "plugins" && !isRootFile(r) {
			if !worldName(r) {
				return nil, errors.New("invalid world folder name")
			}
			worlds = append(worlds, r)
		}
	}
	if len(worlds) == 0 {
		return nil, errors.New("server ZIP has no world")
	}
	plan := &Plan{Zip: zipPath, Server: server, Version: version, Roots: append([]string(nil), m.Roots...), Files: map[string]string{}, Rel: map[string]string{}, Created: m.CreatedAt}
	for rel, meta := range m.Files {
		if !rootSet[strings.Split(rel, "/")[0]] || strings.Contains(rel, "..") {
			return nil, errors.New("payload outside declared roots")
		}
		if meta.Size < 0 || !sha256RE.MatchString(meta.SHA256) {
			return nil, errors.New("invalid file metadata")
		}
		key := "payload/" + rel
		if f := entries[key]; f == nil || int64(f.UncompressedSize64) != meta.Size {
			return nil, errors.New("missing or truncated payload")
		}
		plan.Files[key] = meta.SHA256
		plan.Rel[key] = rel
		plan.Expanded += meta.Size
	}
	if len(entries) != len(plan.Files)+1 {
		return nil, errors.New("unexpected files in the ZIP")
	}
	for _, r := range m.Roots {
		if isRootFile(r) {
			if _, ok := m.Files[r]; !ok {
				return nil, errors.New("expected a root file")
			}
		}
	}
	for _, w := range worlds {
		f := entries["payload/"+w+"/level.dat"]
		if f == nil || f.UncompressedSize64 > 4<<20 {
			return nil, errors.New("world is missing level.dat")
		}
		rc, e := f.Open()
		if e != nil {
			return nil, e
		}
		raw, e := io.ReadAll(io.LimitReader(rc, 4<<20))
		rc.Close()
		if v, e2 := WorldVersion(raw); e != nil || e2 != nil || v != version {
			return nil, fmt.Errorf("world %s: saved version does not match %s", w, version)
		}
	}
	return plan, nil
}

// Restore stages the ZIP under dataDir (same filesystem), checks every hash, then swaps the roots in
// with renames, rolling back if one fails. The roots the server has now that the ZIP lacks are removed
// as well (a ZIP is a complete snapshot). The previous state must have been exported by the caller.
// leftover names a staging folder that could not be deleted (files owned by another user, as the
// image's root-run start script creates): the restore itself is complete, only that folder needs
// removing by hand.
func (p *Plan) Restore(dataDir string) (leftover string, err error) {
	var st syscall.Statfs_t
	if syscall.Statfs(dataDir, &st) == nil && uint64(st.Bavail)*uint64(st.Bsize) < uint64(p.Expanded)+64<<20 {
		return "", errors.New("insufficient free space for staging")
	}
	random := make([]byte, 6)
	rand.Read(random)
	stage := filepath.Join(dataDir, ".archive-staging-"+hex.EncodeToString(random))
	if e := os.Mkdir(stage, 0o700); e != nil {
		return "", e
	}
	z, e := zip.OpenReader(p.Zip)
	if e != nil {
		os.RemoveAll(stage)
		return "", e
	}
	defer z.Close()
	byName := map[string]*zip.File{}
	for _, f := range z.File {
		byName[f.Name] = f
	}
	keys := make([]string, 0, len(p.Files))
	for k := range p.Files {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	var written int64
	for _, key := range keys {
		target := filepath.Join(stage, filepath.FromSlash(p.Rel[key]))
		if e = os.MkdirAll(filepath.Dir(target), 0o750); e != nil {
			os.RemoveAll(stage)
			return "", e
		}
		src, e := byName[key].Open()
		if e != nil {
			os.RemoveAll(stage)
			return "", e
		}
		dst, e := os.OpenFile(target, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o640)
		if e != nil {
			src.Close()
			os.RemoveAll(stage)
			return "", e
		}
		h := sha256.New()
		n, e := io.Copy(io.MultiWriter(dst, h), io.LimitReader(src, p.Expanded-written+1))
		src.Close()
		dst.Close()
		written += n
		if e != nil || written > p.Expanded || hex.EncodeToString(h.Sum(nil)) != p.Files[key] {
			os.RemoveAll(stage)
			return "", fmt.Errorf("ZIP checksum mismatch: %s", p.Rel[key])
		}
	}
	for _, root := range p.Roots {
		if _, e := os.Lstat(filepath.Join(stage, root)); errors.Is(e, fs.ErrNotExist) {
			if e = os.Mkdir(filepath.Join(stage, root), 0o750); e != nil {
				os.RemoveAll(stage)
				return "", e
			}
		}
		if raw, e := os.ReadFile(filepath.Join(stage, root, "level.dat")); e == nil {
			if v, e := WorldVersion(raw); e != nil || v != p.Version {
				os.RemoveAll(stage)
				return "", errors.New("staged world version mismatch")
			}
		}
	}
	previous, _ := roots(dataDir)
	all := map[string]bool{}
	for _, r := range append(previous, p.Roots...) {
		all[r] = true
	}
	names := make([]string, 0, len(all))
	for r := range all {
		names = append(names, r)
	}
	sort.Strings(names)
	e = swap(dataDir, stage, names)
	if e != nil {
		// Rollback files stay in the staging folder for inspection; nothing half-installed remains.
		return "", fmt.Errorf("%w (recovery files kept in %s)", e, filepath.Base(stage))
	}
	if os.RemoveAll(stage) != nil {
		return filepath.Base(stage), nil
	}
	return "", nil
}

func swap(dataDir, stage string, names []string) error {
	old := filepath.Join(stage, ".rollback")
	if e := os.Mkdir(old, 0o700); e != nil {
		return e
	}
	var moved, installed []string
	undo := func() {
		for i := len(installed) - 1; i >= 0; i-- {
			os.Rename(filepath.Join(dataDir, installed[i]), filepath.Join(stage, installed[i]))
		}
		for i := len(moved) - 1; i >= 0; i-- {
			os.Rename(filepath.Join(old, moved[i]), filepath.Join(dataDir, moved[i]))
		}
	}
	for _, name := range names {
		cur := filepath.Join(dataDir, name)
		if st, e := os.Lstat(cur); e == nil {
			if st.Mode()&os.ModeSymlink != 0 {
				undo()
				return errors.New("refusing symlink restore destination")
			}
			if e = os.Rename(cur, filepath.Join(old, name)); e != nil {
				undo()
				return e
			}
			moved = append(moved, name)
		}
		if _, e := os.Lstat(filepath.Join(stage, name)); e == nil {
			if e = os.Rename(filepath.Join(stage, name), cur); e != nil {
				undo()
				return e
			}
			installed = append(installed, name)
		}
	}
	return nil
}

// List describes every ZIP in dir, newest first. The manifest of a ZIP is read once per file version.
func List(dir string) ([]Info, error) {
	entries, e := os.ReadDir(dir)
	if errors.Is(e, fs.ErrNotExist) {
		return []Info{}, nil
	}
	if e != nil {
		return nil, e
	}
	out := []Info{}
	for _, entry := range entries {
		name := entry.Name()
		if !entry.Type().IsRegular() || !NameRE.MatchString(name) {
			continue
		}
		st, e := entry.Info()
		if e != nil {
			continue
		}
		info := Info{Name: name, Kind: "uploaded", Bytes: st.Size(), CreatedAt: st.ModTime().UTC().Format(time.RFC3339)}
		if m := generatedRE.FindStringSubmatch(name); m != nil {
			info.Server, info.Kind = m[1], m[2]
		}
		if sum, e := os.ReadFile(filepath.Join(dir, name+".sha256")); e == nil {
			if f := strings.Fields(string(sum)); len(f) > 0 {
				info.SHA256 = f[0]
			}
		}
		if m, e := readManifest(filepath.Join(dir, name)); e == nil {
			info.Server, info.Minecraft, info.Files = m.Server, m.Minecraft, len(m.Files)
			if m.CreatedAt != "" {
				info.CreatedAt = m.CreatedAt
			}
		} else {
			info.Problem = "not a server backup"
		}
		out = append(out, info)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].CreatedAt > out[j].CreatedAt })
	return out, nil
}

// readManifest opens only the ZIP directory and manifest.json, whatever the ZIP's size.
func readManifest(zipPath string) (Manifest, error) {
	var m Manifest
	z, e := zip.OpenReader(zipPath)
	if e != nil {
		return m, e
	}
	defer z.Close()
	for _, f := range z.File {
		if f.Name != "manifest.json" {
			continue
		}
		if f.UncompressedSize64 > 32<<20 {
			return m, errors.New("manifest too large")
		}
		rc, e := f.Open()
		if e != nil {
			return m, e
		}
		defer rc.Close()
		if e = json.NewDecoder(bufio.NewReader(io.LimitReader(rc, 32<<20))).Decode(&m); e != nil {
			return m, e
		}
		if m.Format != Format {
			return m, errors.New("unknown format")
		}
		return m, nil
	}
	return m, errors.New("manifest missing")
}

// Validate is the cheap check an upload gets before it is kept: a readable server ZIP of this format.
func Validate(zipPath string) (Manifest, error) { return readManifest(zipPath) }
