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
	mu     sync.Mutex
	data   []byte
	digest string
}

// loadPack reads the menu pack on first success; a missing file is retried so the panel can start before the ZIP exists.
func (s *Server) loadPack() ([]byte, string) { return s.pack.load(s.PackFile) }

// load reads one pack file into the cache; it stays empty (and is retried) while the file is missing or too big.
func (c *packCache) load(path string) ([]byte, string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.data != nil || path == "" {
		return c.data, c.digest
	}
	info, e := os.Stat(path)
	if e != nil || !info.Mode().IsRegular() || info.Size() > maxPackBytes {
		return nil, ""
	}
	b, e := os.ReadFile(path)
	if e != nil {
		return nil, ""
	}
	sum := sha1.Sum(b)
	c.data, c.digest = b, hex.EncodeToString(sum[:])
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
