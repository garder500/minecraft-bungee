package control

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"sync"
	"time"
)

// AutoSettings is the daily schedule. Retention applies to automatic backups only: manual ones,
// "before restore" copies and uploads are never deleted by the scheduler.
type AutoSettings struct {
	Enabled   bool     `json:"enabled"`
	Time      string   `json:"time"`
	Timezone  string   `json:"timezone"`
	Servers   []string `json:"servers"`
	KeepLocal int      `json:"keepLocal"`
}

type DriveSettings struct {
	ClientID     string `json:"clientId"`
	ClientSecret string `json:"clientSecret"`
	RefreshToken string `json:"refreshToken"`
	FolderID     string `json:"folderId"`
	Folder       string `json:"folder"`
	Upload       bool   `json:"upload"`
	KeepDrive    int    `json:"keepDrive"`
	Problem      string `json:"problem,omitempty"`
}

type ServerResult struct {
	Server   string `json:"server"`
	Backup   string `json:"backup,omitempty"`
	Uploaded bool   `json:"uploaded"`
	Error    string `json:"error,omitempty"`
	At       string `json:"at"`
}

// Progress is what a day's run has already done, kept on disk so a controller restart cannot repeat it.
type Progress struct {
	Date string                  `json:"date"`
	Done map[string]ServerResult `json:"done"`
}

type Settings struct {
	Auto        AutoSettings   `json:"auto"`
	Drive       DriveSettings  `json:"drive"`
	LastRunDate string         `json:"lastRunDate"`
	LastRunAt   string         `json:"lastRunAt,omitempty"`
	LastRun     []ServerResult `json:"lastRun,omitempty"`
	Progress    *Progress      `json:"progress,omitempty"`
}

func defaultSettings() Settings {
	return Settings{
		Auto:  AutoSettings{Time: "04:00", Timezone: "UTC", Servers: []string{"lobby", "creative", "survival"}, KeepLocal: 3},
		Drive: DriveSettings{Folder: "Vampyuyu backups", Upload: true, KeepDrive: 3},
	}
}

// planStore keeps the settings in one 0600 file, replaced atomically.
type planStore struct {
	mu     sync.Mutex
	path   string
	loaded bool
	cur    Settings
}

func (p *planStore) load() {
	if p.loaded {
		return
	}
	p.cur = defaultSettings()
	if raw, e := os.ReadFile(p.path); e == nil {
		var s Settings
		if json.Unmarshal(raw, &s) == nil {
			def := defaultSettings()
			if s.Auto.Time == "" {
				s.Auto.Time = def.Auto.Time
			}
			if s.Auto.Timezone == "" {
				s.Auto.Timezone = def.Auto.Timezone
			}
			if s.Auto.KeepLocal < 1 {
				s.Auto.KeepLocal = def.Auto.KeepLocal
			}
			if s.Drive.KeepDrive < 1 {
				s.Drive.KeepDrive = def.Drive.KeepDrive
			}
			if s.Drive.Folder == "" {
				s.Drive.Folder = def.Drive.Folder
			}
			p.cur = s
		}
	}
	p.loaded = true
}

func (p *planStore) get() Settings {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.load()
	return clone(p.cur)
}

func clone(s Settings) Settings {
	s.Auto.Servers = append([]string(nil), s.Auto.Servers...)
	s.LastRun = append([]ServerResult(nil), s.LastRun...)
	if s.Progress != nil {
		pr := *s.Progress
		pr.Done = map[string]ServerResult{}
		for k, v := range s.Progress.Done {
			pr.Done[k] = v
		}
		s.Progress = &pr
	}
	return s
}

// update applies fn and writes the file; the in-memory copy changes only if the write worked.
func (p *planStore) update(fn func(*Settings) error) (Settings, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.load()
	next := clone(p.cur)
	if e := fn(&next); e != nil {
		return clone(p.cur), e
	}
	raw, _ := json.MarshalIndent(next, "", "  ")
	if p.path != "" {
		if e := os.MkdirAll(filepath.Dir(p.path), 0o700); e != nil {
			return clone(p.cur), errors.New("settings folder unavailable")
		}
		tmp := p.path + ".tmp"
		if e := os.WriteFile(tmp, raw, 0o600); e != nil {
			return clone(p.cur), errors.New("cannot save the settings")
		}
		if e := os.Rename(tmp, p.path); e != nil {
			return clone(p.cur), errors.New("cannot save the settings")
		}
	}
	p.cur = next
	return clone(next), nil
}

var timeRE = regexp.MustCompile(`^([01]\d|2[0-3]):[0-5]\d$`)

func validateAuto(a AutoSettings) error {
	if !timeRE.MatchString(a.Time) {
		return errors.New("the time must look like 04:30")
	}
	if a.Timezone == "" || a.Timezone == "Local" {
		return errors.New("choose a time zone")
	}
	if _, e := time.LoadLocation(a.Timezone); e != nil {
		return errors.New("unknown time zone")
	}
	seen := map[string]bool{}
	for _, s := range a.Servers {
		if !paperServer(s) || seen[s] {
			return errors.New("servers must be a subset of lobby, creative and survival")
		}
		seen[s] = true
	}
	if a.Enabled && len(a.Servers) == 0 {
		return errors.New("choose at least one server to back up")
	}
	if a.KeepLocal < 1 || a.KeepLocal > 60 {
		return errors.New("keep between 1 and 60 local backups per server")
	}
	return nil
}

func validateDrive(d DriveSettings) error {
	if d.KeepDrive < 1 || d.KeepDrive > 60 {
		return errors.New("keep between 1 and 60 Drive backups per server")
	}
	if d.Folder == "" || len(d.Folder) > 100 {
		return errors.New("the Drive folder needs a name of up to 100 characters")
	}
	if len(d.ClientID) > 200 || len(d.ClientSecret) > 200 {
		return errors.New("the Google client values are too long")
	}
	return nil
}

// View is what the panel may see: the secret and the tokens never leave the controller.
type View struct {
	Auto struct {
		AutoSettings
		Next string `json:"next,omitempty"`
	} `json:"auto"`
	Drive struct {
		ClientID        string `json:"clientId"`
		HasClientSecret bool   `json:"hasClientSecret"`
		Connected       bool   `json:"connected"`
		Folder          string `json:"folder"`
		Upload          bool   `json:"upload"`
		KeepDrive       int    `json:"keepDrive"`
		Problem         string `json:"problem,omitempty"`
	} `json:"drive"`
	LastRunAt string         `json:"lastRunAt,omitempty"`
	LastRun   []ServerResult `json:"lastRun,omitempty"`
	Running   bool           `json:"running"`
	Timezones []string       `json:"timezones"`
}

var zones = []string{"UTC", "Europe/Paris", "Europe/London", "Europe/Berlin", "Europe/Madrid", "Europe/Brussels", "America/New_York", "America/Chicago", "America/Los_Angeles", "America/Montreal", "Asia/Tokyo", "Australia/Sydney"}

func (s *Server) view(st Settings, now time.Time) View {
	var v View
	v.Auto.AutoSettings = st.Auto
	if st.Auto.Enabled {
		if next, ok := nextRun(st.Auto, st.LastRunDate, now); ok {
			v.Auto.Next = next.Format(time.RFC3339)
		}
	}
	v.Drive.ClientID = st.Drive.ClientID
	v.Drive.HasClientSecret = st.Drive.ClientSecret != ""
	v.Drive.Connected = st.Drive.RefreshToken != ""
	v.Drive.Folder, v.Drive.Upload, v.Drive.KeepDrive, v.Drive.Problem = st.Drive.Folder, st.Drive.Upload, st.Drive.KeepDrive, st.Drive.Problem
	v.LastRunAt, v.LastRun = st.LastRunAt, st.LastRun
	v.Running = s.planBusy()
	v.Timezones = append([]string(nil), zones...)
	known := false
	for _, z := range zones {
		known = known || z == st.Auto.Timezone
	}
	if !known {
		v.Timezones = append(v.Timezones, st.Auto.Timezone)
		sort.Strings(v.Timezones)
	}
	return v
}
