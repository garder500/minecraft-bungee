package control

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/url"
	"regexp"
)

// DataName is a Skyblock data domain or table name as the plugin spells it.
var DataName = regexp.MustCompile(`^[a-z0-9_]{1,64}$`)

// ValidDataKey: a row key is free text (ids, patterns with a slash or a star) but bounded and without control characters.
func ValidDataKey(key string) bool {
	if key == "" || len(key) > 256 {
		return false
	}
	for _, c := range key {
		if c < 0x20 || c == 0x7f {
			return false
		}
	}
	return true
}

// skyblockReadParams are the only query parameters a read forwards to the plugin.
var skyblockReadParams = []string{"q", "origin", "offset", "limit", "table", "key", "actor"}

// skyblockRoutes carries the staff editing of the Skyblock content database to the survival plugin of one
// server (its own private port, see gameProxy). The plugin enforces every table name against its domain list;
// here only the shape of the request is checked. A change needs the actor (the panel's session user) and takes
// the server lock like the other writes.
func (s *Server) skyblockRoutes(mux *http.ServeMux) {
	const base = "/servers/{id}/skyblock/data"
	read := func(path func(r *http.Request) (string, bool)) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			p, ok := path(r)
			if !ok {
				JSON(w, 400, map[string]string{"error": "Invalid data request"})
				return
			}
			q := url.Values{}
			for _, name := range skyblockReadParams {
				if v := r.URL.Query().Get(name); v != "" && len(v) <= 256 {
					q.Set(name, v)
				}
			}
			if k := q.Get("key"); k != "" && !ValidDataKey(k) {
				JSON(w, 400, map[string]string{"error": "Invalid row key"})
				return
			}
			if len(q) > 0 {
				p += "?" + q.Encode()
			}
			s.gameProxy(w, r, r.PathValue("id"), "GET", p, nil)
		}
	}
	names := func(r *http.Request) (string, string, bool) {
		d, t := r.PathValue("domain"), r.PathValue("table")
		return d, t, DataName.MatchString(d) && DataName.MatchString(t)
	}
	mux.HandleFunc("GET "+base, read(func(r *http.Request) (string, bool) { return "/skyblock/data", true }))
	mux.HandleFunc("GET "+base+"/audit", read(func(r *http.Request) (string, bool) { return "/skyblock/data/audit", true }))
	mux.HandleFunc("GET "+base+"/{domain}/{table}", read(func(r *http.Request) (string, bool) {
		d, t, ok := names(r)
		return "/skyblock/data/" + d + "/" + t, ok
	}))
	mux.HandleFunc("GET "+base+"/{domain}/{table}/rows", read(func(r *http.Request) (string, bool) {
		d, t, ok := names(r)
		return "/skyblock/data/" + d + "/" + t + "/rows", ok
	}))
	mux.HandleFunc("GET "+base+"/{domain}/{table}/row", read(func(r *http.Request) (string, bool) {
		d, t, ok := names(r)
		return "/skyblock/data/" + d + "/" + t + "/row", ok && r.URL.Query().Get("key") != ""
	}))
	write := func(method, verb string) {
		mux.HandleFunc(method+" "+base+"/{domain}/{table}/"+verb, func(w http.ResponseWriter, r *http.Request) {
			d, t, ok := names(r)
			key, actor := r.URL.Query().Get("key"), r.URL.Query().Get("actor")
			if !ok || !ValidDataKey(key) || !staffName.MatchString(actor) {
				JSON(w, 400, map[string]string{"error": "Invalid data change"})
				return
			}
			var body io.Reader
			if method == "PUT" {
				raw, e := io.ReadAll(http.MaxBytesReader(w, r.Body, 256<<10))
				var probe map[string]json.RawMessage
				if e != nil || json.Unmarshal(raw, &probe) != nil {
					JSON(w, 400, map[string]string{"error": "Invalid JSON body"})
					return
				}
				body = bytes.NewReader(raw)
			}
			id := r.PathValue("id")
			lock := s.serverLockSafe(id)
			if lock == nil {
				JSON(w, 400, map[string]string{"error": "Unknown Paper server"})
				return
			}
			lock.Lock()
			defer lock.Unlock()
			q := url.Values{"key": {key}, "actor": {actor}}
			s.gameProxy(w, r, id, method, "/skyblock/data/"+d+"/"+t+"/"+verb+"?"+q.Encode(), body)
		})
	}
	write("PUT", "row")
	write("DELETE", "row")
	write("POST", "reset")
}
