package panel

import (
	"crypto/sha1"
	"encoding/hex"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func digestOf(b []byte) string {
	sum := sha1.Sum(b)
	return hex.EncodeToString(sum[:])
}

// A rebuilt pack must be served by its new hash without a panel restart; a vanished file keeps the last good copy.
func TestPackCacheReloadsAChangedFile(t *testing.T) {
	path := filepath.Join(t.TempDir(), "pack.zip")
	var c packCache

	if data, digest := c.load(path); data != nil || digest != "" {
		t.Fatalf("missing file should give nothing, got %q", digest)
	}

	first := []byte("first pack")
	if err := os.WriteFile(path, first, 0o644); err != nil {
		t.Fatal(err)
	}
	if _, digest := c.load(path); digest != digestOf(first) {
		t.Fatalf("first read: got %s", digest)
	}

	second := []byte("second pack, longer")
	if err := os.WriteFile(path, second, 0o644); err != nil {
		t.Fatal(err)
	}
	later := time.Now().Add(2 * time.Second)
	if err := os.Chtimes(path, later, later); err != nil {
		t.Fatal(err)
	}
	if _, digest := c.load(path); digest != digestOf(second) {
		t.Fatalf("changed file not reloaded: got %s", digest)
	}

	if err := os.Remove(path); err != nil {
		t.Fatal(err)
	}
	if _, digest := c.load(path); digest != digestOf(second) {
		t.Fatalf("vanished file should keep the last good copy, got %s", digest)
	}
}
