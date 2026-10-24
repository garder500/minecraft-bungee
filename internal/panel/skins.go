package panel

import (
	"context"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"
)

// Player skins come from Mojang only: the session server names a player's skin texture, the texture
// server hosts it. The panel proxies and caches the PNG so the browser never talks to Mojang, and so
// a player's UUID is only ever sent to Mojang from the server side.
const (
	mojangSession   = "https://sessionserver.mojang.com"
	skinHost        = "textures.minecraft.net"
	maxSkinBytes    = 256 << 10
	skinHit         = 6 * time.Hour
	skinMiss        = 10 * time.Minute
	skinCacheLimit  = 512
)

var validSkinUUID = regexp.MustCompile(`^[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}$`)

type skinEntry struct {
	png  []byte
	slim bool
	at   time.Time
}

// SkinStore fetches and caches player skins. The zero value is not usable: set HTTP.
type SkinStore struct {
	HTTP    *http.Client
	Session string               // defaults to Mojang's session server
	Allowed func(host string) bool // texture hosts, defaults to textures.minecraft.net only
	mu      sync.Mutex
	cache   map[string]skinEntry
}

var errNoSkin = errors.New("no skin for this player")

// Get returns the skin PNG of a player (64×64 or 64×32) and whether it uses the slim arm model.
func (s *SkinStore) Get(ctx context.Context, uuid string) ([]byte, bool, error) {
	if s == nil || s.HTTP == nil {
		return nil, false, errNoSkin
	}
	if !validSkinUUID.MatchString(uuid) {
		return nil, false, errors.New("invalid player id")
	}
	id := strings.ToLower(strings.ReplaceAll(uuid, "-", ""))
	s.mu.Lock()
	if e, ok := s.cache[id]; ok {
		ttl := skinHit
		if e.png == nil {
			ttl = skinMiss
		}
		if time.Since(e.at) < ttl {
			s.mu.Unlock()
			if e.png == nil {
				return nil, false, errNoSkin
			}
			return e.png, e.slim, nil
		}
	}
	s.mu.Unlock()
	png, slim, e := s.fetch(ctx, id)
	s.mu.Lock()
	if s.cache == nil || len(s.cache) >= skinCacheLimit {
		s.cache = map[string]skinEntry{}
	}
	if e == nil {
		s.cache[id] = skinEntry{png: png, slim: slim, at: time.Now()}
	} else if errors.Is(e, errNoSkin) {
		s.cache[id] = skinEntry{at: time.Now()}
	}
	s.mu.Unlock()
	return png, slim, e
}

func (s *SkinStore) allowed(host string) bool {
	if s.Allowed != nil {
		return s.Allowed(host)
	}
	return host == skinHost
}

func (s *SkinStore) get(ctx context.Context, raw string, limit int64) ([]byte, int, error) {
	req, e := http.NewRequestWithContext(ctx, http.MethodGet, raw, nil)
	if e != nil {
		return nil, 0, e
	}
	res, e := s.HTTP.Do(req)
	if e != nil {
		return nil, 0, e
	}
	defer res.Body.Close()
	data, e := io.ReadAll(io.LimitReader(res.Body, limit+1))
	if e != nil || int64(len(data)) > limit {
		return nil, res.StatusCode, errors.New("skin response too large")
	}
	return data, res.StatusCode, nil
}

func (s *SkinStore) fetch(ctx context.Context, id string) ([]byte, bool, error) {
	base := s.Session
	if base == "" {
		base = mojangSession
	}
	body, status, e := s.get(ctx, base+"/session/minecraft/profile/"+id, 64<<10)
	if e != nil {
		return nil, false, e
	}
	if status == http.StatusNoContent || status == http.StatusNotFound {
		return nil, false, errNoSkin
	}
	if status != http.StatusOK {
		return nil, false, errors.New("skin service unavailable")
	}
	var profile struct {
		Properties []struct{ Name, Value string } `json:"properties"`
	}
	if json.Unmarshal(body, &profile) != nil {
		return nil, false, errNoSkin
	}
	for _, p := range profile.Properties {
		if p.Name != "textures" {
			continue
		}
		raw, e := base64.StdEncoding.DecodeString(p.Value)
		if e != nil {
			return nil, false, errNoSkin
		}
		var textures struct {
			Textures struct {
				Skin struct {
					URL      string `json:"url"`
					Metadata struct {
						Model string `json:"model"`
					} `json:"metadata"`
				} `json:"SKIN"`
			} `json:"textures"`
		}
		if json.Unmarshal(raw, &textures) != nil || textures.Textures.Skin.URL == "" {
			return nil, false, errNoSkin
		}
		u, e := url.Parse(textures.Textures.Skin.URL)
		if e != nil || !s.allowed(u.Hostname()) {
			return nil, false, errNoSkin
		}
		if u.Scheme == "http" && u.Hostname() == skinHost {
			u.Scheme = "https" // Mojang still hands out http:// texture links
		}
		png, status, e := s.get(ctx, u.String(), maxSkinBytes)
		if e != nil || status != http.StatusOK || !validSkin(png) {
			return nil, false, errNoSkin
		}
		return png, textures.Textures.Skin.Metadata.Model == "slim", nil
	}
	return nil, false, errNoSkin
}

// validSkin accepts only PNG files of the two skin sizes.
func validSkin(png []byte) bool {
	if len(png) < 33 || string(png[:8]) != "\x89PNG\r\n\x1a\n" || string(png[12:16]) != "IHDR" {
		return false
	}
	w, h := binary.BigEndian.Uint32(png[16:20]), binary.BigEndian.Uint32(png[20:24])
	return w == 64 && (h == 64 || h == 32)
}

func (s *Server) skinRoutes(mux *http.ServeMux, protected func(func(http.ResponseWriter, *http.Request, session)) http.HandlerFunc) {
	mux.HandleFunc("GET /api/v1/players/{uuid}/skin", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
		defer cancel()
		png, slim, e := s.Skins.Get(ctx, r.PathValue("uuid"))
		if e != nil {
			if errors.Is(e, errNoSkin) {
				fail(w, 404, "no skin for this player")
			} else {
				fail(w, 502, e.Error())
			}
			return
		}
		model := "classic"
		if slim {
			model = "slim"
		}
		w.Header().Set("Content-Type", "image/png")
		w.Header().Set("X-Skin-Model", model)
		w.Header().Set("Cache-Control", "private, max-age=3600")
		w.Write(png)
	}))
}
