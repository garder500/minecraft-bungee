package control

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/garder500/minecraft-bungee/internal/backup"
)

// MaxBackupBytes bounds an uploaded ZIP (the expanded size is bounded separately by backup.Limit).
const MaxBackupBytes = backup.Limit

// Job is a backup or restore running in the background; the panel polls it.
type Job struct {
	ID       string `json:"id"`
	Kind     string `json:"kind"` // backup | restore
	Server   string `json:"server"`
	File     string `json:"file,omitempty"`
	State    string `json:"state"` // running | done | failed
	Step     string `json:"step"`
	Error    string `json:"error,omitempty"`
	Warning  string `json:"warning,omitempty"`
	Started  string `json:"startedAt"`
	Finished string `json:"finishedAt,omitempty"`
}

type jobBoard struct {
	mu   sync.Mutex
	jobs map[string]*Job
	busy map[string]string // server -> job id
}

func (b *jobBoard) start(kind, server, file string) (Job, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.jobs == nil {
		b.jobs, b.busy = map[string]*Job{}, map[string]string{}
	}
	if id := b.busy[server]; id != "" {
		return Job{}, fmt.Errorf("another backup or restore of %s is running", server)
	}
	random := make([]byte, 8)
	rand.Read(random)
	j := &Job{ID: hex.EncodeToString(random), Kind: kind, Server: server, File: file, State: "running", Step: "starting", Started: time.Now().UTC().Format(time.RFC3339)}
	b.jobs[j.ID] = j
	b.busy[server] = j.ID
	// Keep the board small: forget the oldest finished jobs.
	if len(b.jobs) > 30 {
		var done []*Job
		for _, o := range b.jobs {
			if o.State != "running" {
				done = append(done, o)
			}
		}
		sort.Slice(done, func(i, k int) bool { return done[i].Started < done[k].Started })
		for _, o := range done[:min(len(done), len(b.jobs)-30)] {
			delete(b.jobs, o.ID)
		}
	}
	return *j, nil
}
func (b *jobBoard) step(id, step string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	if j := b.jobs[id]; j != nil {
		j.Step = step
	}
}
func (b *jobBoard) warn(id, text string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	if j := b.jobs[id]; j != nil {
		j.Warning = text
	}
}
func (b *jobBoard) finish(id string, err error, file string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	j := b.jobs[id]
	if j == nil {
		return
	}
	j.Finished = time.Now().UTC().Format(time.RFC3339)
	if err != nil {
		j.State, j.Error = "failed", err.Error()
	} else {
		j.State, j.Step = "done", "done"
	}
	if file != "" {
		j.File = file
	}
	delete(b.busy, j.Server)
}
func (b *jobBoard) get(id string) (Job, bool) {
	b.mu.Lock()
	defer b.mu.Unlock()
	if j := b.jobs[id]; j != nil {
		return *j, true
	}
	return Job{}, false
}
func (b *jobBoard) using(file string) bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	for _, j := range b.jobs {
		if j.State == "running" && j.File == file {
			return true
		}
	}
	return false
}
func (b *jobBoard) all() []Job {
	b.mu.Lock()
	defer b.mu.Unlock()
	out := []Job{}
	for _, j := range b.jobs {
		out = append(out, *j)
	}
	sort.Slice(out, func(i, k int) bool { return out[i].Started > out[k].Started })
	return out
}

func (s *Server) dataDir(id string) string { return filepath.Join(s.DataRoot, id) }

func (s *Server) rconAddr(id string) string { return id + ":25575" }

// freeze makes the world files stable without kicking anyone: autosave off, everything flushed. The
// returned func turns autosave back on. A stopped server needs neither.
func (s *Server) freeze(ctx context.Context, id string) (func(), error) {
	st, e := s.Docker.Status(ctx, id)
	if e != nil {
		return nil, errors.New("cannot read the server state")
	}
	if st.State != "running" {
		return func() {}, nil
	}
	if _, e = RCONWait(ctx, s.rconAddr(id), s.Passwords[id], "save-off", 10*time.Second); e != nil {
		return nil, errors.New("cannot pause autosave over RCON; the backup was not made")
	}
	thaw := func() {
		c, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()
		if _, e := RCONWait(c, s.rconAddr(id), s.Passwords[id], "save-on", 20*time.Second); e != nil {
			slog.Error("save-on failed after a backup: autosave may be off", "server", id, "error", e)
		}
	}
	if _, e = RCONWait(ctx, s.rconAddr(id), s.Passwords[id], "save-all flush", 5*time.Minute); e != nil {
		thaw()
		return nil, errors.New("the world flush did not finish; the backup was not made")
	}
	return thaw, nil
}

func (s *Server) runBackup(job Job) {
	ctx := context.Background()
	s.jobs.step(job.ID, "saving the world")
	thaw, e := s.freeze(ctx, job.Server)
	if e != nil {
		s.jobs.finish(job.ID, e, "")
		return
	}
	s.jobs.step(job.ID, "writing the ZIP")
	info, e := backup.Create(s.dataDir(job.Server), job.Server, s.Version, s.BackupDir, "export")
	thaw()
	if e != nil {
		slog.Warn("backup failed", "server", job.Server, "error", e)
	}
	s.jobs.finish(job.ID, e, info.Name)
}

func (s *Server) running(ctx context.Context, id string) bool {
	st, e := s.Docker.Status(ctx, id)
	return e == nil && st.State == "running"
}

func (s *Server) runRestore(job Job) {
	ctx := context.Background()
	zipPath := filepath.Join(s.BackupDir, job.File)
	s.jobs.step(job.ID, "checking the ZIP")
	plan, e := backup.Inspect(zipPath, job.Server, s.Version)
	if e != nil {
		s.jobs.finish(job.ID, e, "")
		return
	}
	was := s.running(ctx, job.Server)
	if was {
		s.jobs.step(job.ID, "stopping the server")
		if e = s.Docker.Action(ctx, job.Server, "stop"); e != nil {
			s.jobs.finish(job.ID, errors.New("cannot stop the server: "+e.Error()), "")
			return
		}
		for i := 0; i < 90 && s.running(ctx, job.Server); i++ {
			time.Sleep(time.Second)
		}
		if s.running(ctx, job.Server) {
			s.jobs.finish(job.ID, errors.New("the server did not stop"), "")
			return
		}
	}
	resume := func() {
		if was {
			if e := s.Docker.Action(ctx, job.Server, "start"); e != nil {
				slog.Error("server did not restart after a restore", "server", job.Server, "error", e)
			}
		}
	}
	s.jobs.step(job.ID, "saving the current state")
	if _, e = backup.Create(s.dataDir(job.Server), job.Server, s.Version, s.BackupDir, "before-restore"); e != nil {
		resume()
		s.jobs.finish(job.ID, errors.New("cannot save the current state first, nothing was changed: "+e.Error()), "")
		return
	}
	s.jobs.step(job.ID, "restoring")
	leftover, e := plan.Restore(s.dataDir(job.Server))
	if e != nil {
		slog.Error("restore failed", "server", job.Server, "error", e)
		if strings.Contains(e.Error(), "recovery files kept") {
			s.jobs.finish(job.ID, fmt.Errorf("%w; the server stays stopped", e), "")
			return
		}
		resume()
		s.jobs.finish(job.ID, e, "")
		return
	}
	if leftover != "" {
		s.jobs.warn(job.ID, "The restore is complete, but the folder "+leftover+" in the server's data could not be deleted (root-owned files). Remove it by hand to free the disk space.")
	}
	if was {
		s.jobs.step(job.ID, "starting the server")
	}
	resume()
	s.jobs.finish(job.ID, nil, "")
}

func paperServer(id string) bool { return Valid(id) && id != "velocity" }

func (s *Server) backupRoutes(mux *http.ServeMux) {
	enabled := func(w http.ResponseWriter) bool {
		if s.BackupDir == "" || s.Version == "" {
			JSON(w, 503, map[string]string{"error": "Backups are not configured on this controller"})
			return false
		}
		return true
	}
	mux.HandleFunc("GET /backups", func(w http.ResponseWriter, r *http.Request) {
		if !enabled(w) {
			return
		}
		list, e := backup.List(s.BackupDir)
		if e != nil {
			JSON(w, 503, map[string]string{"error": "Backup folder unavailable"})
			return
		}
		JSON(w, 200, map[string]any{"version": s.Version, "backups": list, "jobs": s.jobs.all()})
	})
	mux.HandleFunc("GET /jobs/{id}", func(w http.ResponseWriter, r *http.Request) {
		if j, ok := s.jobs.get(r.PathValue("id")); ok {
			JSON(w, 200, j)
			return
		}
		JSON(w, 404, map[string]string{"error": "unknown job"})
	})
	mux.HandleFunc("POST /servers/{id}/backups", func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		if !enabled(w) {
			return
		}
		if !paperServer(id) {
			JSON(w, 400, map[string]string{"error": "Paper server required"})
			return
		}
		job, e := s.jobs.start("backup", id, "")
		if e != nil {
			JSON(w, 409, map[string]string{"error": e.Error()})
			return
		}
		go s.runBackup(job)
		JSON(w, 202, job)
	})
	mux.HandleFunc("POST /servers/{id}/restore/{name}", func(w http.ResponseWriter, r *http.Request) {
		id, name := r.PathValue("id"), r.PathValue("name")
		if !enabled(w) {
			return
		}
		if !paperServer(id) || !backup.NameRE.MatchString(name) {
			JSON(w, 400, map[string]string{"error": "Invalid server or backup name"})
			return
		}
		if st, e := os.Lstat(filepath.Join(s.BackupDir, name)); e != nil || !st.Mode().IsRegular() {
			JSON(w, 404, map[string]string{"error": "Backup not found"})
			return
		}
		job, e := s.jobs.start("restore", id, name)
		if e != nil {
			JSON(w, 409, map[string]string{"error": e.Error()})
			return
		}
		go s.runRestore(job)
		JSON(w, 202, job)
	})
	mux.HandleFunc("GET /backups/{name}", func(w http.ResponseWriter, r *http.Request) {
		name := r.PathValue("name")
		if !enabled(w) {
			return
		}
		if !backup.NameRE.MatchString(name) {
			JSON(w, 400, map[string]string{"error": "Invalid backup name"})
			return
		}
		f, e := os.Open(filepath.Join(s.BackupDir, name))
		if e != nil {
			JSON(w, 404, map[string]string{"error": "Backup not found"})
			return
		}
		defer f.Close()
		st, e := f.Stat()
		if e != nil || !st.Mode().IsRegular() {
			JSON(w, 404, map[string]string{"error": "Backup not found"})
			return
		}
		w.Header().Set("Content-Type", "application/zip")
		w.Header().Set("Content-Disposition", `attachment; filename="`+name+`"`)
		// Gigabytes: lift the server-wide read/write limits for this one transfer.
		rc := http.NewResponseController(w)
		rc.SetWriteDeadline(time.Time{})
		http.ServeContent(w, r, name, st.ModTime(), f)
	})
	mux.HandleFunc("PUT /backups/{name}", func(w http.ResponseWriter, r *http.Request) {
		name := r.PathValue("name")
		if !enabled(w) {
			return
		}
		if !backup.NameRE.MatchString(name) {
			JSON(w, 400, map[string]string{"error": "Backup names use letters, digits, dot, dash and underscore, and end in .zip"})
			return
		}
		final := filepath.Join(s.BackupDir, name)
		if _, e := os.Lstat(final); e == nil {
			JSON(w, 409, map[string]string{"error": "A backup with this name already exists"})
			return
		}
		if e := os.MkdirAll(s.BackupDir, 0o700); e != nil {
			JSON(w, 503, map[string]string{"error": "Backup folder unavailable"})
			return
		}
		rc := http.NewResponseController(w)
		rc.SetReadDeadline(time.Time{})
		random := make([]byte, 8)
		rand.Read(random)
		partial := filepath.Join(s.BackupDir, ".upload-"+hex.EncodeToString(random)+".partial")
		f, e := os.OpenFile(partial, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
		if e != nil {
			JSON(w, 503, map[string]string{"error": "Cannot stage the upload"})
			return
		}
		defer os.Remove(partial)
		_, e = io.Copy(f, http.MaxBytesReader(w, r.Body, MaxBackupBytes))
		if ce := f.Close(); e == nil {
			e = ce
		}
		if e != nil {
			JSON(w, 413, map[string]string{"error": "Upload failed or is larger than 40 GiB"})
			return
		}
		if _, e = backup.Validate(partial); e != nil {
			JSON(w, 400, map[string]string{"error": "Not a server backup ZIP (manifest.json missing or unreadable)"})
			return
		}
		if e = os.Link(partial, final); e != nil {
			JSON(w, 409, map[string]string{"error": "A backup with this name already exists"})
			return
		}
		if sum, e := fileSum(final); e == nil {
			os.WriteFile(final+".sha256", []byte(sum+"  "+name+"\n"), 0o600)
		}
		JSON(w, 201, map[string]string{"name": name})
	})
	mux.HandleFunc("DELETE /backups/{name}", func(w http.ResponseWriter, r *http.Request) {
		name := r.PathValue("name")
		if !enabled(w) {
			return
		}
		if !backup.NameRE.MatchString(name) {
			JSON(w, 400, map[string]string{"error": "Invalid backup name"})
			return
		}
		if s.jobs.using(name) {
			JSON(w, 409, map[string]string{"error": "This backup is being restored"})
			return
		}
		if e := os.Remove(filepath.Join(s.BackupDir, name)); e != nil {
			JSON(w, 404, map[string]string{"error": "Backup not found"})
			return
		}
		os.Remove(filepath.Join(s.BackupDir, name+".sha256"))
		JSON(w, 200, map[string]bool{"ok": true})
	})
}

func fileSum(path string) (string, error) {
	f, e := os.Open(path)
	if e != nil {
		return "", e
	}
	defer f.Close()
	h := sha256.New()
	if _, e = io.Copy(h, f); e != nil {
		return "", e
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}
