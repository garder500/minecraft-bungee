package control

import (
	"bytes"
	"encoding/json"
	"net/http"
	"regexp"
	"strings"
	"unicode/utf8"
)

var staffName = regexp.MustCompile(`^[A-Za-z0-9_. -]{1,32}$`)

// chatRoutes carries the private staff<->player channel to the network plugin of one server. One fixed
// operation set (open, send, close, read); the plugin shows staff text as plain text and the controller
// only checks the shape of each request.
func (s *Server) chatRoutes(mux *http.ServeMux) {
	mux.HandleFunc("POST /servers/{id}/chat", func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		lock := s.serverLockSafe(id)
		if lock == nil {
			JSON(w, 400, map[string]string{"error": "Unknown Paper server"})
			return
		}
		var in struct {
			Action  string `json:"action"`
			UUID    string `json:"uuid"`
			Staff   string `json:"staff"`
			Message string `json:"message"`
			Since   int64  `json:"since"`
		}
		if json.NewDecoder(http.MaxBytesReader(w, r.Body, 4096)).Decode(&in) != nil || !gameUUID.MatchString(in.UUID) || in.UUID == "00000000-0000-0000-0000-000000000000" {
			JSON(w, 400, map[string]string{"error": "Invalid chat request"})
			return
		}
		switch in.Action {
		case "open", "send":
			if !staffName.MatchString(in.Staff) {
				JSON(w, 400, map[string]string{"error": "Invalid staff name"})
				return
			}
			if in.Action == "send" && (strings.TrimSpace(in.Message) == "" || utf8.RuneCountInString(in.Message) > 256) {
				JSON(w, 400, map[string]string{"error": "A message is 1 to 256 characters"})
				return
			}
		case "close", "read":
		default:
			JSON(w, 400, map[string]string{"error": "Unsupported chat operation"})
			return
		}
		if in.Since < 0 {
			in.Since = 0
		}
		body, _ := json.Marshal(in)
		if in.Action != "read" {
			lock.Lock()
			defer lock.Unlock()
		}
		s.gameProxy(w, r, id, "POST", "/chat", bytes.NewReader(body))
	})
}
