package panel

import (
	"bytes"
	"crypto/sha1"
	"encoding/hex"
	"net/http"
	"os"
	"sync"
	"time"
)

const maxPackBytes = 32 << 20

type packCache struct {
	mu      sync.Mutex
	data    []byte
	digest  string
	modTime time.Time
	size    int64
}

// loadPack reads the menu pack; a missing file is retried so the panel can start before the ZIP exists.
func (s *Server) loadPack() ([]byte, string) { return s.pack.load(s.PackFile) }

// load returns the pack file's bytes and SHA-1. The file is read again when its size or modification time changes, so
// a rebuilt pack (make pack, make pack-valoblock) is served by its new hash without restarting the panel. While the
// file is missing, unreadable or too big, the last good copy keeps being served (nil before the first good read).
func (c *packCache) load(path string) ([]byte, string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if path == "" {
		return nil, ""
	}
	info, e := os.Stat(path)
	if e != nil || !info.Mode().IsRegular() || info.Size() > maxPackBytes {
		return c.data, c.digest
	}
	if c.data != nil && info.Size() == c.size && info.ModTime().Equal(c.modTime) {
		return c.data, c.digest
	}
	b, e := os.ReadFile(path)
	if e != nil || int64(len(b)) > maxPackBytes {
		return c.data, c.digest
	}
	sum := sha1.Sum(b)
	c.data, c.digest, c.modTime, c.size = b, hex.EncodeToString(sum[:]), info.ModTime(), info.Size()
	return c.data, c.digest
}

// servePack is public and unauthenticated: the Minecraft client downloads it directly.
// Two packs are served, the menu pack (PackFile) and the ValoBlock pack (ValoBlockPackFile), each only by its exact
// content hash so a stale announcement can never fetch the wrong pack.
func (s *Server) servePack(w http.ResponseWriter, r *http.Request) {
	file := r.PathValue("file")
	for _, p := range []struct {
		cache *packCache
		path  string
		name  string
	}{{&s.pack, s.PackFile, "vampyuyu-menus.zip"}, {&s.valoPack, s.ValoBlockPackFile, "vampyuyu-valoblock.zip"}} {
		data, digest := p.cache.load(p.path)
		if data == nil || file != digest+".zip" {
			continue
		}
		w.Header().Set("Content-Type", "application/zip")
		w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
		w.Header().Set("ETag", `"`+digest+`"`)
		http.ServeContent(w, r, p.name, time.Time{}, bytes.NewReader(data))
		return
	}
	http.NotFound(w, r)
}
