package panel

import (
	"context"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"sync/atomic"
	"testing"
)

func fakeSkin(w, h uint32) []byte {
	png := []byte("\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR")
	dim := make([]byte, 8)
	binary.BigEndian.PutUint32(dim[:4], w)
	binary.BigEndian.PutUint32(dim[4:], h)
	return append(append(png, dim...), make([]byte, 17)...)
}

// mojang fakes the session server and the texture server on one test server.
func mojang(t *testing.T, model string, skin []byte, hits *atomic.Int32) *httptest.Server {
	t.Helper()
	var srv *httptest.Server
	srv = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case strings.HasPrefix(r.URL.Path, "/session/minecraft/profile/"):
			hits.Add(1)
			if strings.HasSuffix(r.URL.Path, "00000000000000000000000000000404") {
				w.WriteHeader(http.StatusNoContent)
				return
			}
			meta := map[string]string{}
			if model != "" {
				meta["model"] = model
			}
			value, _ := json.Marshal(map[string]any{"textures": map[string]any{"SKIN": map[string]any{"url": srv.URL + "/texture/abc", "metadata": meta}}})
			json.NewEncoder(w).Encode(map[string]any{"properties": []map[string]string{{"name": "textures", "value": base64.StdEncoding.EncodeToString(value)}}})
		case r.URL.Path == "/texture/abc":
			w.Write(skin)
		default:
			http.NotFound(w, r)
		}
	}))
	t.Cleanup(srv.Close)
	return srv
}

func store(srv *httptest.Server) *SkinStore {
	u, _ := url.Parse(srv.URL)
	return &SkinStore{HTTP: srv.Client(), Session: srv.URL, Allowed: func(host string) bool { return host == u.Hostname() }}
}

func TestSkinStoreFetchesCachesAndReportsTheModel(t *testing.T) {
	var hits atomic.Int32
	srv := mojang(t, "slim", fakeSkin(64, 64), &hits)
	s := store(srv)
	id := "069a79f4-44e9-4726-a5be-fca90e38aaf5"
	for i := 0; i < 3; i++ {
		png, slim, e := s.Get(context.Background(), id)
		if e != nil || !slim || len(png) == 0 {
			t.Fatalf("skin: %v slim=%v", e, slim)
		}
	}
	if hits.Load() != 1 {
		t.Fatalf("the profile should be fetched once, was %d", hits.Load())
	}
	if _, slim, _ := store(mojang(t, "", fakeSkin(64, 32), &hits)).Get(context.Background(), id); slim {
		t.Fatal("no model metadata means the classic arms")
	}
}

func TestSkinStoreRefusesWrongInputs(t *testing.T) {
	var hits atomic.Int32
	srv := mojang(t, "", fakeSkin(64, 64), &hits)
	s := store(srv)
	if _, _, e := s.Get(context.Background(), "../../etc/passwd"); e == nil || hits.Load() != 0 {
		t.Fatal("a malformed id must never reach Mojang")
	}
	if _, _, e := s.Get(context.Background(), "00000000-0000-0000-0000-000000000404"); e != errNoSkin {
		t.Fatalf("a player without a profile has no skin, got %v", e)
	}
	// A texture that is not a skin-sized PNG, or on another host, is not served.
	bad := store(mojang(t, "", fakeSkin(128, 128), &hits))
	if _, _, e := bad.Get(context.Background(), "069a79f4-44e9-4726-a5be-fca90e38aaf5"); e != errNoSkin {
		t.Fatalf("oversized skin: %v", e)
	}
	other := store(srv)
	other.Allowed = func(string) bool { return false }
	if _, _, e := other.Get(context.Background(), "069a79f4-44e9-4726-a5be-fca90e38aaf5"); e != errNoSkin {
		t.Fatalf("foreign texture host: %v", e)
	}
	if _, _, e := (*SkinStore)(nil).Get(context.Background(), "069a79f4-44e9-4726-a5be-fca90e38aaf5"); e != errNoSkin {
		t.Fatal("a missing store means no skin")
	}
}
