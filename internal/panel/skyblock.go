package panel

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/url"
	"strings"

	"github.com/garder500/minecraft-bungee/internal/control"
)

// skyblockReadParams are the only query parameters a read forwards (the controller filters them again).
var skyblockReadParams = []string{"q", "origin", "offset", "limit", "table", "key", "actor"}

func shortKey(key string) string {
	if len(key) > 80 {
		return key[:80]
	}
	return key
}

// skyblockRoutes: staff editing of the Skyblock content database (survival plugin, through the controller).
// The actor of a change is always the session user, never a request field. Reads are open to any signed-in
// administrator like the other pages; every change is written to the panel's audit table (what and where, the
// row's before/after lives in the plugin's own data_audit).
func (s *Server) skyblockRoutes(mux *http.ServeMux, protected func(func(http.ResponseWriter, *http.Request, session)) http.HandlerFunc) {
	const base = "/api/v1/servers/{id}/skyblock/data"
	server := func(w http.ResponseWriter, r *http.Request) (string, bool) {
		id := r.PathValue("id")
		if !control.Valid(id) || id == "velocity" {
			fail(w, 400, "Paper server required")
			return "", false
		}
		return id, true
	}
	names := func(w http.ResponseWriter, r *http.Request) (string, string, bool) {
		d, t := r.PathValue("domain"), r.PathValue("table")
		if !control.DataName.MatchString(d) || !control.DataName.MatchString(t) {
			fail(w, 400, "invalid data set or table")
			return "", "", false
		}
		return d, t, true
	}
	// read relays one GET: suffix is the panel route after base, tail the same route on the controller after the table.
	read := func(suffix string, hasTable bool, tail string, needsKey bool) {
		mux.HandleFunc("GET "+base+suffix, protected(func(w http.ResponseWriter, r *http.Request, v session) {
			id, ok := server(w, r)
			if !ok {
				return
			}
			path := "/servers/" + id + "/skyblock/data"
			if hasTable {
				d, t, ok := names(w, r)
				if !ok {
					return
				}
				path += "/" + d + "/" + t
			}
			path += tail
			q := url.Values{}
			for _, name := range skyblockReadParams {
				if x := r.URL.Query().Get(name); x != "" && len(x) <= 256 {
					q.Set(name, x)
				}
			}
			if needsKey && !control.ValidDataKey(q.Get("key")) {
				fail(w, 400, "invalid row key")
				return
			}
			if len(q) > 0 {
				path += "?" + q.Encode()
			}
			s.relay(w, r, "GET", path, nil)
		}))
	}
	read("", false, "", false)
	read("/audit", false, "/audit", false)
	read("/{domain}/{table}", true, "", false)
	read("/{domain}/{table}/rows", true, "/rows", false)
	read("/{domain}/{table}/row", true, "/row", true)

	write := func(method, verb string) {
		mux.HandleFunc(method+" "+base+"/{domain}/{table}/"+verb, protected(func(w http.ResponseWriter, r *http.Request, v session) {
			id, ok := server(w, r)
			if !ok {
				return
			}
			d, t, ok := names(w, r)
			if !ok {
				return
			}
			key := r.URL.Query().Get("key")
			if !control.ValidDataKey(key) {
				fail(w, 400, "invalid row key")
				return
			}
			route := "/servers/" + id + "/skyblock/data/" + d + "/" + t + "/" + verb + "?" + url.Values{"key": {key}, "actor": {v.User}}.Encode()
			var body io.Reader
			action := "skyblock-" + strings.ToLower(map[string]string{"row": method, "reset": "reset"}[verb]) + ":" + d + "." + t + ":" + shortKey(key)
			if method == "PUT" {
				var in struct {
					Row      json.RawMessage `json:"row"`
					Children json.RawMessage `json:"children"`
				}
				if !decodeSized(w, r, &in, 256<<10) {
					return
				}
				raw, _ := json.Marshal(in)
				body = bytes.NewReader(raw)
			}
			s.auditRelay(w, r, v, id, action, method, route, body)
		}))
	}
	write("PUT", "row")
	write("DELETE", "row")
	write("POST", "reset")
}
