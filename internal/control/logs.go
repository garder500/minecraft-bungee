package control

import (
	"bufio"
	"bytes"
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// Log lines come from Docker (its json-file driver keeps about 30 MB per server), each prefixed with a
// UTC timestamp. Everything the panel sees is redacted first and stripped of terminal colour codes.
var ansiRE = regexp.MustCompile("\x1b\\[[0-9;?]*[ -/]*[@-~]")
var dockerStamp = regexp.MustCompile(`^\d{4}-\d\d-\d\dT[\d:.]+Z `)

// Paper: "[12:30:01] [Server thread/WARN]: ..."; Velocity: "[12:30:01 WARN]: ...".
var levelRE = regexp.MustCompile(`^\[[\d:]{8}(?: |\] \[[^\]]*/)(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\]`)

// Level returns INFO, WARN, ERROR or DEBUG for a line, or "" when it has no header of its own (a stack
// trace line, a continuation): the caller then reuses the level of the line above.
func Level(line string) string {
	body := dockerStamp.ReplaceAllString(line, "")
	m := levelRE.FindStringSubmatch(body)
	if m == nil {
		return ""
	}
	switch m[1] {
	case "FATAL":
		return "ERROR"
	case "TRACE":
		return "DEBUG"
	}
	return m[1]
}

const (
	maxLogLine    = 4096
	maxScanBytes  = 96 << 20
	maxReplyBytes = 1536 << 10
	maxLogTail    = 20000
	maxSearchLen  = 200
)

// LogQuery describes what the panel wants: the last Tail lines, or the last Max lines that match.
type LogQuery struct {
	Tail   int // lines read from the end; 0 reads the whole history
	Max    int // matches kept (the newest ones)
	Search string
	Regex  bool
	Case   bool
	Levels map[string]bool
}

type LogResult struct {
	Lines     []string `json:"lines"`
	Scanned   int      `json:"scanned"`
	Matched   int      `json:"matched"`
	Truncated bool     `json:"truncated"`
}

// matcher compiles the search once. Go's regexp is RE2: linear time, no catastrophic backtracking.
func (q LogQuery) matcher() (func(string) bool, error) {
	if q.Search == "" {
		return func(string) bool { return true }, nil
	}
	if len(q.Search) > maxSearchLen {
		return nil, errors.New("search text is too long")
	}
	if q.Regex {
		pattern := q.Search
		if !q.Case {
			pattern = "(?i)" + pattern
		}
		re, e := regexp.Compile(pattern)
		if e != nil {
			return nil, errors.New("invalid regular expression")
		}
		return re.MatchString, nil
	}
	if q.Case {
		return func(s string) bool { return strings.Contains(s, q.Search) }, nil
	}
	needle := strings.ToLower(q.Search)
	return func(s string) bool { return strings.Contains(strings.ToLower(s), needle) }, nil
}

// walkLogs reads a container's log stream line by line (Docker multiplexes stdout and stderr in frames
// with an eight-byte header) and hands each cleaned line to fn until fn returns false or limit bytes
// were read. Nothing is kept in memory but one line.
func (d *Docker) walkLogs(ctx context.Context, id string, tail int, limit int64, fn func(line string) bool) (truncated bool, err error) {
	cid, e := d.container(ctx, id)
	if e != nil {
		return false, e
	}
	t := "all"
	if tail > 0 {
		t = strconv.Itoa(tail)
	}
	r, e := d.request(ctx, "GET", "/containers/"+cid+"/logs?stdout=true&stderr=true&timestamps=true&tail="+t)
	if e != nil {
		return false, e
	}
	defer r.Body.Close()
	body := bufio.NewReaderSize(io.LimitReader(r.Body, limit), 64<<10)
	var pending [2][]byte // a line can span frames, one buffer per stream
	emit := func(raw []byte) bool {
		line := ansiRE.ReplaceAllString(strings.TrimRight(string(raw), "\r"), "")
		if len(line) > maxLogLine {
			line = line[:maxLogLine] + "…"
		}
		return fn(line)
	}
	for {
		h, e := body.Peek(8)
		if e == io.EOF || (e != nil && len(h) == 0) {
			break
		}
		if e != nil {
			return false, e
		}
		if h[0] > 2 || h[1] != 0 || h[2] != 0 || h[3] != 0 {
			return false, errors.New("expected non-TTY Docker logs")
		}
		n := int(binary.BigEndian.Uint32(h[4:]))
		if n > 1<<20 {
			return false, errors.New("log frame too large")
		}
		stream := 0
		if h[0] == 2 {
			stream = 1
		}
		body.Discard(8)
		frame := make([]byte, n)
		if _, e = io.ReadFull(body, frame); e != nil {
			truncated = true
			break
		}
		buf := append(pending[stream], frame...)
		for {
			i := bytes.IndexByte(buf, '\n')
			if i < 0 {
				break
			}
			if !emit(buf[:i]) {
				return false, nil
			}
			buf = buf[i+1:]
		}
		pending[stream] = append(pending[stream][:0], buf...)
		if len(pending[stream]) > 1<<20 {
			pending[stream] = pending[stream][:0]
		}
	}
	for _, p := range pending {
		if len(p) > 0 && !emit(p) {
			break
		}
	}
	return truncated, nil
}

// Query returns the matching lines, newest last. Redaction happens before matching, so a search can
// never be used to discover a secret one character at a time.
func (s *Server) queryLogs(ctx context.Context, id string, q LogQuery) (LogResult, error) {
	match, e := q.matcher()
	if e != nil {
		return LogResult{}, e
	}
	max := q.Max
	if max < 1 || max > 5000 {
		max = 200
	}
	ring := make([]string, 0, max)
	var res LogResult
	level, size := "INFO", 0
	truncated, e := s.Docker.walkLogs(ctx, id, q.Tail, maxScanBytes, func(line string) bool {
		line = s.redact(line)
		res.Scanned++
		if l := Level(line); l != "" {
			level = l
		}
		if len(q.Levels) > 0 && !q.Levels[level] {
			return true
		}
		if q.Search != "" && !match(dockerStamp.ReplaceAllString(line, "")) {
			return true
		}
		res.Matched++
		if len(ring) == max {
			size -= len(ring[0])
			ring = ring[1:]
		}
		ring = append(ring, line)
		size += len(line)
		// Keep the reply small enough for the panel: drop the oldest lines first.
		for size > maxReplyBytes && len(ring) > 1 {
			size -= len(ring[0])
			ring = ring[1:]
			res.Truncated = true
		}
		return true
	})
	if e != nil {
		return LogResult{}, e
	}
	res.Truncated = res.Truncated || truncated
	res.Lines = ring
	return res, nil
}

func parseLogQuery(r *http.Request) (LogQuery, bool, error) {
	v := r.URL.Query()
	q := LogQuery{Tail: 200, Max: 200, Search: v.Get("q"), Regex: v.Get("regex") == "1", Case: v.Get("case") == "1"}
	filtering := false
	if t := v.Get("tail"); t != "" {
		if t == "all" {
			q.Tail = 0
		} else {
			n, e := strconv.Atoi(t)
			if e != nil || n < 1 || n > maxLogTail {
				return q, false, fmt.Errorf("tail must be between 1 and %d, or all", maxLogTail)
			}
			q.Tail = n
		}
		q.Max = min(max(q.Tail, 200), 5000)
		if q.Tail == 0 {
			q.Max = 5000
		}
	}
	if m := v.Get("max"); m != "" {
		n, e := strconv.Atoi(m)
		if e != nil || n < 1 || n > 5000 {
			return q, false, errors.New("max must be between 1 and 5000")
		}
		q.Max = n
		filtering = true
	}
	if l := v.Get("level"); l != "" {
		q.Levels = map[string]bool{}
		for _, one := range strings.Split(l, ",") {
			one = strings.ToUpper(strings.TrimSpace(one))
			if one != "INFO" && one != "WARN" && one != "ERROR" && one != "DEBUG" {
				return q, false, errors.New("level must be INFO, WARN, ERROR or DEBUG")
			}
			q.Levels[one] = true
		}
		filtering = true
	}
	if q.Search != "" {
		filtering = true
	}
	return q, filtering, nil
}

func (s *Server) logRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /servers/{id}/logs", func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		if !Valid(id) {
			JSON(w, 404, map[string]string{"error": "unknown server"})
			return
		}
		q, filtering, e := parseLogQuery(r)
		if e != nil {
			JSON(w, 400, map[string]string{"error": e.Error()})
			return
		}
		res, e := s.queryLogs(r.Context(), id, q)
		if e != nil {
			if strings.Contains(e.Error(), "regular expression") || strings.Contains(e.Error(), "too long") {
				JSON(w, 400, map[string]string{"error": e.Error()})
				return
			}
			JSON(w, 503, map[string]string{"error": e.Error()})
			return
		}
		if !filtering {
			// The stream and older clients: a plain array of the last lines.
			if res.Lines == nil {
				res.Lines = []string{}
			}
			JSON(w, 200, res.Lines)
			return
		}
		if res.Lines == nil {
			res.Lines = []string{}
		}
		JSON(w, 200, res)
	})
	mux.HandleFunc("GET /servers/{id}/logs/download", func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		if !Valid(id) {
			JSON(w, 404, map[string]string{"error": "unknown server"})
			return
		}
		cid, e := s.Docker.container(r.Context(), id)
		if e != nil || cid == "" {
			JSON(w, 503, map[string]string{"error": "console unavailable"})
			return
		}
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		w.Header().Set("Content-Disposition", `attachment; filename="`+id+`-console.log"`)
		http.NewResponseController(w).SetWriteDeadline(time.Time{})
		out := bufio.NewWriter(w)
		defer out.Flush()
		s.Docker.walkLogs(r.Context(), id, 0, maxScanBytes, func(line string) bool {
			_, e := out.WriteString(s.redact(line) + "\n")
			return e == nil
		})
	})
}
