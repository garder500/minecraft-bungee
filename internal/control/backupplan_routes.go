package control

import (
	"context"
	"encoding/json"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"time"

	"github.com/garder500/minecraft-bungee/internal/backup"
)

var stateRE = regexp.MustCompile(`^[0-9a-f]{16,128}$`)

func decodeBody(w http.ResponseWriter, r *http.Request, v any) bool {
	if e := json.NewDecoder(http.MaxBytesReader(w, r.Body, 64<<10)).Decode(v); e != nil {
		JSON(w, 400, map[string]string{"error": "Invalid request"})
		return false
	}
	return true
}

func (s *Server) planRoutes(mux *http.ServeMux) {
	enabled := func(w http.ResponseWriter) bool {
		if s.BackupDir == "" || s.Version == "" {
			JSON(w, 503, map[string]string{"error": "Backups are not configured on this controller"})
			return false
		}
		return true
	}
	mux.HandleFunc("GET /backup-settings", func(w http.ResponseWriter, r *http.Request) {
		if enabled(w) {
			JSON(w, 200, s.view(s.store().get(), s.now()))
		}
	})
	mux.HandleFunc("PUT /backup-settings", func(w http.ResponseWriter, r *http.Request) {
		if !enabled(w) {
			return
		}
		var in struct {
			Auto  AutoSettings `json:"auto"`
			Drive struct {
				ClientID     string `json:"clientId"`
				ClientSecret string `json:"clientSecret"`
				Folder       string `json:"folder"`
				Upload       bool   `json:"upload"`
				KeepDrive    int    `json:"keepDrive"`
			} `json:"drive"`
		}
		if !decodeBody(w, r, &in) {
			return
		}
		st, e := s.store().update(func(x *Settings) error {
			next := x.Drive
			if in.Drive.ClientID != x.Drive.ClientID {
				// Another Google client means another grant: forget the old one.
				next.RefreshToken, next.FolderID, next.Problem, next.ClientSecret = "", "", "", ""
			}
			next.ClientID = in.Drive.ClientID
			if in.Drive.ClientSecret != "" {
				next.ClientSecret = in.Drive.ClientSecret
			}
			if in.Drive.Folder != x.Drive.Folder {
				next.FolderID = ""
			}
			next.Folder, next.Upload, next.KeepDrive = in.Drive.Folder, in.Drive.Upload, in.Drive.KeepDrive
			if e := validateAuto(in.Auto); e != nil {
				return e
			}
			if e := validateDrive(next); e != nil {
				return e
			}
			// Turning the schedule on, or moving its hour, after today's hour has passed must not fire a backup
			// at once: catching up is for a controller that was down, not for a settings change.
			changed := !x.Auto.Enabled || x.Auto.Time != in.Auto.Time || x.Auto.Timezone != in.Auto.Timezone
			x.Auto, x.Drive = in.Auto, next
			if changed && in.Auto.Enabled {
				if date, due := dueDate(in.Auto, "", s.now()); due {
					x.LastRunDate, x.Progress = date, nil
				}
			}
			return nil
		})
		if e != nil {
			JSON(w, 400, map[string]string{"error": e.Error()})
			return
		}
		JSON(w, 200, s.view(st, s.now()))
	})
	mux.HandleFunc("POST /drive/auth-url", func(w http.ResponseWriter, r *http.Request) {
		if !enabled(w) {
			return
		}
		var in struct{ RedirectURI, State string }
		if !decodeBody(w, r, &in) {
			return
		}
		u, e := url.Parse(in.RedirectURI)
		st := s.store().get()
		if e != nil || (u.Scheme != "https" && u.Scheme != "http") || u.Host == "" || !stateRE.MatchString(in.State) {
			JSON(w, 400, map[string]string{"error": "Invalid redirect address or state"})
			return
		}
		if st.Drive.ClientID == "" || st.Drive.ClientSecret == "" {
			JSON(w, 400, map[string]string{"error": "Save the Google client ID and secret first"})
			return
		}
		JSON(w, 200, map[string]string{"url": s.drive().ConsentURL(st.Drive.ClientID, in.RedirectURI, in.State)})
	})
	mux.HandleFunc("POST /drive/exchange", func(w http.ResponseWriter, r *http.Request) {
		if !enabled(w) {
			return
		}
		var in struct{ Code, RedirectURI string }
		if !decodeBody(w, r, &in) || in.Code == "" || len(in.Code) > 2048 {
			if in.Code == "" {
				JSON(w, 400, map[string]string{"error": "Invalid request"})
			}
			return
		}
		st := s.store().get()
		ctx, cancel := context.WithTimeout(r.Context(), 60*time.Second)
		defer cancel()
		refresh, e := s.drive().Exchange(ctx, st.Drive.ClientID, st.Drive.ClientSecret, in.Code, in.RedirectURI)
		if e != nil {
			JSON(w, 502, map[string]string{"error": e.Error()})
			return
		}
		st, _ = s.store().update(func(x *Settings) error {
			x.Drive.RefreshToken, x.Drive.FolderID, x.Drive.Problem = refresh, "", ""
			return nil
		})
		// Make the folder now: it proves the grant works with the drive.file scope.
		if _, _, _, e = s.driveAccess(ctx); e != nil {
			JSON(w, 502, map[string]string{"error": "Connected, but Drive refused the folder: " + e.Error()})
			return
		}
		JSON(w, 200, s.view(s.store().get(), s.now()))
	})
	mux.HandleFunc("POST /drive/disconnect", func(w http.ResponseWriter, r *http.Request) {
		if !enabled(w) {
			return
		}
		st := s.store().get()
		if st.Drive.RefreshToken != "" {
			ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
			s.drive().Revoke(ctx, st.Drive.RefreshToken) // best effort: the local copy goes either way
			cancel()
		}
		st, _ = s.store().update(func(x *Settings) error {
			x.Drive.RefreshToken, x.Drive.FolderID, x.Drive.Problem = "", "", ""
			return nil
		})
		JSON(w, 200, s.view(st, s.now()))
	})
	mux.HandleFunc("GET /drive/files", func(w http.ResponseWriter, r *http.Request) {
		if !enabled(w) {
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), 60*time.Second)
		defer cancel()
		token, folder, _, e := s.driveAccess(ctx)
		if e != nil {
			JSON(w, 502, map[string]string{"error": e.Error()})
			return
		}
		files, e := s.drive().List(ctx, token, folder)
		if e != nil {
			JSON(w, 502, map[string]string{"error": e.Error()})
			return
		}
		sort.Slice(files, func(i, j int) bool { return files[i].Name > files[j].Name })
		JSON(w, 200, files)
	})
	mux.HandleFunc("POST /backups/{name}/drive", func(w http.ResponseWriter, r *http.Request) {
		name := r.PathValue("name")
		if !enabled(w) {
			return
		}
		if !backup.NameRE.MatchString(name) {
			JSON(w, 400, map[string]string{"error": "Invalid backup name"})
			return
		}
		if st, e := os.Lstat(filepath.Join(s.BackupDir, name)); e != nil || !st.Mode().IsRegular() {
			JSON(w, 404, map[string]string{"error": "Backup not found"})
			return
		}
		if s.store().get().Drive.RefreshToken == "" {
			JSON(w, 400, map[string]string{"error": "Google Drive is not connected"})
			return
		}
		job, e := s.jobs.start("upload", "drive", name)
		if e != nil {
			JSON(w, 409, map[string]string{"error": "Another upload to Drive is running"})
			return
		}
		server, kind := "", "manual"
		if list, e := backup.List(s.BackupDir); e == nil {
			for _, b := range list {
				if b.Name == name {
					server = b.Server
					if b.Kind == "auto" {
						kind = "auto"
					}
				}
			}
		}
		go func() {
			s.jobs.step(job.ID, "uploading to Drive")
			e := s.uploadToDrive(context.Background(), name, server, kind, func(sent, total int64) {
				if total > 0 {
					s.jobs.step(job.ID, "uploading to Drive: "+strconv.FormatInt(sent*100/total, 10)+"%")
				}
			})
			s.jobs.finish(job.ID, e, name)
		}()
		JSON(w, 202, job)
	})
	mux.HandleFunc("POST /backup-settings/run", func(w http.ResponseWriter, r *http.Request) {
		if !enabled(w) {
			return
		}
		if s.planBusy() {
			JSON(w, 409, map[string]string{"error": "An automatic run is already in progress"})
			return
		}
		st := s.store().get()
		if len(st.Auto.Servers) == 0 {
			JSON(w, 400, map[string]string{"error": "Choose at least one server first"})
			return
		}
		go s.runPlan(context.Background(), time.Now().Format("2006-01-02"), true)
		JSON(w, 202, map[string]bool{"started": true})
	})
}
