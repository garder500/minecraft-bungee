package panel

import (
	"bytes"
	"encoding/json"
	"net/http"
	"regexp"

	"github.com/garder500/minecraft-bungee/internal/control"
)

var chatUUID = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)

// chatRoutes: the private channel between the signed-in administrator and one player. The staff name
// always comes from the session, never from the request. Messages are not written to the audit log, only
// that a channel was opened, written to or closed, and with whom.
func (s *Server) chatRoutes(mux *http.ServeMux, protected func(func(http.ResponseWriter, *http.Request, session)) http.HandlerFunc) {
	mux.HandleFunc("POST /api/v1/servers/{id}/chat", protected(func(w http.ResponseWriter, r *http.Request, v session) {
		id := r.PathValue("id")
		if !control.Valid(id) || id == "velocity" {
			fail(w, 400, "Paper server required")
			return
		}
		var in struct {
			Action  string `json:"action"`
			UUID    string `json:"uuid"`
			Message string `json:"message"`
			Since   int64  `json:"since"`
		}
		if !decode(w, r, &in) {
			return
		}
		if !chatUUID.MatchString(in.UUID) || (in.Action != "open" && in.Action != "send" && in.Action != "close" && in.Action != "read") {
			fail(w, 400, "invalid chat request")
			return
		}
		body, _ := json.Marshal(map[string]any{"action": in.Action, "uuid": in.UUID, "staff": v.User, "message": in.Message, "since": in.Since})
		if in.Action == "read" {
			s.relay(w, r, "POST", "/servers/"+id+"/chat", bytes.NewReader(body))
			return
		}
		s.auditRelay(w, r, v, id, "chat-"+in.Action+":"+in.UUID, "POST", "/servers/"+id+"/chat", bytes.NewReader(body))
	}))
}
