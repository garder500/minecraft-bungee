package control

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"syscall"
	"time"

	"github.com/garder500/minecraft-bungee/internal/backup"
)

func (s *Server) store() *planStore {
	s.planOnce.Do(func() {
		if s.StateDir != "" {
			s.plan.path = filepath.Join(s.StateDir, "backup-settings.json")
		}
	})
	return &s.plan
}

func (s *Server) now() time.Time {
	if s.Now != nil {
		return s.Now()
	}
	return time.Now()
}

func (s *Server) drive() *Drive {
	if s.Drive == nil {
		s.Drive = NewDrive()
	}
	return s.Drive
}

func (s *Server) planBusy() bool { return s.planOn.Load() }

func target(a AutoSettings, local time.Time) time.Time {
	var h, m int
	fmt.Sscanf(a.Time, "%d:%d", &h, &m)
	// time.Date settles a time that does not exist (spring forward) or occurs twice (autumn) on one
	// instant; with one run per local date that is all that is needed.
	return time.Date(local.Year(), local.Month(), local.Day(), h, m, 0, 0, local.Location())
}

// dueDate says whether today's run (in the schedule's own time zone) is owed and which local date it is for.
// A date already run is never due again, so a restart after the hour does not repeat the backup and one
// that was missed is made up the same day.
func dueDate(a AutoSettings, lastDate string, now time.Time) (string, bool) {
	loc, e := time.LoadLocation(a.Timezone)
	if e != nil || !timeRE.MatchString(a.Time) {
		return "", false
	}
	local := now.In(loc)
	date := local.Format("2006-01-02")
	if lastDate == date {
		return date, false
	}
	return date, !now.Before(target(a, local))
}

// nextRun is when the next automatic run starts: now if one is owed, else today's or tomorrow's hour.
func nextRun(a AutoSettings, lastDate string, now time.Time) (time.Time, bool) {
	loc, e := time.LoadLocation(a.Timezone)
	if e != nil || !timeRE.MatchString(a.Time) {
		return time.Time{}, false
	}
	local := now.In(loc)
	if _, due := dueDate(a, lastDate, now); due {
		return now, true
	}
	if lastDate != local.Format("2006-01-02") {
		return target(a, local), true
	}
	return target(a, local.AddDate(0, 0, 1)), true
}

// RunScheduler checks every half minute whether a daily run is owed. It returns when ctx ends.
func (s *Server) RunScheduler(ctx context.Context) {
	if s.BackupDir == "" || s.Version == "" {
		return
	}
	tick := time.NewTicker(30 * time.Second)
	defer tick.Stop()
	for {
		s.schedulerTick(ctx)
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		}
	}
}

func (s *Server) schedulerTick(ctx context.Context) {
	st := s.store().get()
	if !st.Auto.Enabled {
		return
	}
	if date, due := dueDate(st.Auto, st.LastRunDate, s.now()); due {
		s.runPlan(ctx, date, false)
	}
}

// runPlan backs up every selected server in turn. A scheduled run keeps its progress on disk and counts
// the date as done only once each server has been attempted; a server busy with a manual job is retried
// at the next tick. manual runs (the "run now" button) change neither the progress nor the done date.
func (s *Server) runPlan(ctx context.Context, date string, manual bool) {
	if !s.planOn.CompareAndSwap(false, true) {
		return
	}
	defer s.planOn.Store(false)
	st := s.store().get()
	done := map[string]ServerResult{}
	if !manual && st.Progress != nil && st.Progress.Date == date {
		done = st.Progress.Done
	}
	retry := false
	for _, id := range st.Auto.Servers {
		if _, ok := done[id]; ok {
			continue
		}
		res, again := s.autoOne(ctx, id)
		if again {
			retry = true
			continue
		}
		done[id] = res
		if !manual {
			snapshot := map[string]ServerResult{}
			for k, v := range done {
				snapshot[k] = v
			}
			s.store().update(func(x *Settings) error { x.Progress = &Progress{Date: date, Done: snapshot}; return nil })
		}
	}
	if retry && !manual {
		return
	}
	results := []ServerResult{}
	for _, id := range st.Auto.Servers {
		if r, ok := done[id]; ok {
			results = append(results, r)
		}
	}
	at := s.now().UTC().Format(time.RFC3339)
	s.store().update(func(x *Settings) error {
		x.LastRun, x.LastRunAt = results, at
		if !manual {
			x.LastRunDate, x.Progress = date, nil
		}
		return nil
	})
}

// autoOne: disk check, backup, upload, then prune (local, then Drive only after a good upload).
func (s *Server) autoOne(ctx context.Context, id string) (ServerResult, bool) {
	res := ServerResult{Server: id, At: s.now().UTC().Format(time.RFC3339)}
	st := s.store().get()
	if e := s.diskOK(id); e != nil {
		res.Error = e.Error()
		return res, false
	}
	job, e := s.jobs.start("backup", id, "")
	if e != nil {
		return res, true
	}
	s.runBackup(job, "auto")
	done, _ := s.jobs.get(job.ID)
	if done.State != "done" {
		res.Error = done.Error
		return res, false
	}
	res.Backup = done.File
	if st.Drive.Upload && st.Drive.RefreshToken != "" {
		if e := s.uploadToDrive(ctx, done.File, id, "auto", nil); e != nil {
			res.Error = "Drive: " + e.Error()
		} else {
			res.Uploaded = true
		}
	}
	s.pruneLocal(id, st.Auto.KeepLocal)
	if res.Uploaded {
		if e := s.pruneDrive(ctx, id, st.Drive.KeepDrive); e != nil {
			res.Error = "Drive cleanup: " + e.Error()
		}
	}
	return res, false
}

// diskOK refuses a backup that could fill the disk the servers share: free space must exceed the last
// backup of this server plus a margin (2 GiB when there is none yet).
func (s *Server) diskOK(id string) error {
	need := int64(2 << 30)
	if list, e := backup.List(s.BackupDir); e == nil {
		for _, b := range list { // newest first
			if b.Server == id && (b.Kind == "auto" || b.Kind == "export") {
				need = b.Bytes + max(int64(1<<30), b.Bytes/10)
				break
			}
		}
	}
	var st syscall.Statfs_t
	if e := syscall.Statfs(s.BackupDir, &st); e != nil {
		return nil // cannot tell: do not block the backup on a failed check
	}
	if free := int64(st.Bavail) * int64(st.Bsize); free < need {
		return fmt.Errorf("not enough free disk space: %d MiB free, %d MiB needed", free>>20, need>>20)
	}
	return nil
}

// pruneLocal keeps the newest keep automatic backups of a server; the stamp in the name orders them.
func (s *Server) pruneLocal(id string, keep int) {
	list, e := backup.List(s.BackupDir)
	if e != nil || keep < 1 {
		return
	}
	var names []string
	for _, b := range list {
		if b.Server == id && b.Kind == "auto" {
			names = append(names, b.Name)
		}
	}
	sort.Sort(sort.Reverse(sort.StringSlice(names)))
	for i, name := range names {
		if i < keep || s.jobs.using(name) {
			continue
		}
		os.Remove(filepath.Join(s.BackupDir, name))
		os.Remove(filepath.Join(s.BackupDir, name+".sha256"))
	}
}

// driveAccess returns a token, the folder to use and the settings read; ErrReconnect is remembered so the
// page can say "connect again" instead of showing a generic failure.
func (s *Server) driveAccess(ctx context.Context) (string, string, Settings, error) {
	st := s.store().get()
	if st.Drive.RefreshToken == "" || st.Drive.ClientID == "" {
		return "", "", st, errors.New("Google Drive is not connected")
	}
	d := s.drive()
	token, e := d.Access(ctx, st.Drive.ClientID, st.Drive.ClientSecret, st.Drive.RefreshToken)
	if e == nil {
		var folder string
		if folder, e = d.Folder(ctx, token, st.Drive.FolderID, st.Drive.Folder); e == nil {
			if folder != st.Drive.FolderID || st.Drive.Problem != "" {
				st, _ = s.store().update(func(x *Settings) error { x.Drive.FolderID, x.Drive.Problem = folder, ""; return nil })
			}
			return token, folder, st, nil
		}
	}
	if errors.Is(e, ErrReconnect) {
		s.store().update(func(x *Settings) error { x.Drive.Problem = "reconnect"; return nil })
	}
	return "", "", st, e
}

func (s *Server) uploadToDrive(ctx context.Context, name, server, kind string, progress func(sent, total int64)) error {
	token, folder, _, e := s.driveAccess(ctx)
	if e != nil {
		return e
	}
	_, e = s.drive().Upload(ctx, token, folder, filepath.Join(s.BackupDir, name), name, map[string]string{"server": server, "kind": kind, "app": "vampyuyu"}, progress)
	if errors.Is(e, ErrReconnect) {
		s.store().update(func(x *Settings) error { x.Drive.Problem = "reconnect"; return nil })
	}
	return e
}

// pruneDrive keeps the newest keep files this app tagged as automatic backups of the server.
func (s *Server) pruneDrive(ctx context.Context, id string, keep int) error {
	token, folder, _, e := s.driveAccess(ctx)
	if e != nil {
		return e
	}
	files, e := s.drive().List(ctx, token, folder)
	if e != nil {
		return e
	}
	var mine []DriveFile
	for _, f := range files {
		if f.AppProperties["kind"] == "auto" && f.AppProperties["server"] == id && strings.HasSuffix(f.Name, ".zip") {
			mine = append(mine, f)
		}
	}
	sort.Slice(mine, func(i, j int) bool { return mine[i].Name > mine[j].Name })
	for i, f := range mine {
		if i >= keep {
			if e = s.drive().Delete(ctx, token, f.ID); e != nil {
				return e
			}
		}
	}
	return nil
}
