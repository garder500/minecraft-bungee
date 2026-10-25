package control

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

// ErrReconnect means Google refused the stored grant (revoked, expired or the consent screen is still in
// Testing): the operator must connect Drive again. Anything else is a passing problem.
var ErrReconnect = errors.New("Google Drive access was revoked or expired: connect Drive again")

const driveScope = "https://www.googleapis.com/auth/drive.file"

// Drive talks to Google with the standard library only. The base URLs are fields so tests can point them
// at a fake. With the drive.file scope the app only ever sees files and folders it created itself.
type Drive struct {
	HTTP      *http.Client
	AuthURL   string
	TokenURL  string
	RevokeURL string
	APIURL    string
	UploadURL string
	Chunk     int           // upload chunk size, a multiple of 256 KiB
	Wait      time.Duration // pause before retrying a failed chunk
	Now       func() time.Time

	mu     sync.Mutex
	token  string
	expiry time.Time
	owner  string // refresh token the cached access token belongs to
}

func NewDrive() *Drive {
	return &Drive{
		HTTP:      &http.Client{Timeout: 5 * time.Minute},
		AuthURL:   "https://accounts.google.com/o/oauth2/v2/auth",
		TokenURL:  "https://oauth2.googleapis.com/token",
		RevokeURL: "https://oauth2.googleapis.com/revoke",
		APIURL:    "https://www.googleapis.com/drive/v3",
		UploadURL: "https://www.googleapis.com/upload/drive/v3",
		Chunk:     16 << 20,
		Wait:      5 * time.Second,
		Now:       time.Now,
	}
}

// ConsentURL is where the operator grants access. offline + consent make Google return a refresh token
// every time, not only on the first grant.
func (d *Drive) ConsentURL(clientID, redirectURI, state string) string {
	q := url.Values{
		"client_id": {clientID}, "redirect_uri": {redirectURI}, "response_type": {"code"}, "scope": {driveScope},
		"access_type": {"offline"}, "prompt": {"consent"}, "state": {state}, "include_granted_scopes": {"false"},
	}
	return d.AuthURL + "?" + q.Encode()
}

type tokenReply struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	ExpiresIn    int    `json:"expires_in"`
	Error        string `json:"error"`
	Description  string `json:"error_description"`
}

func (d *Drive) tokenRequest(ctx context.Context, form url.Values) (tokenReply, error) {
	var out tokenReply
	req, e := http.NewRequestWithContext(ctx, "POST", d.TokenURL, strings.NewReader(form.Encode()))
	if e != nil {
		return out, e
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	res, e := d.HTTP.Do(req)
	if e != nil {
		return out, errors.New("cannot reach Google")
	}
	defer res.Body.Close()
	json.NewDecoder(io.LimitReader(res.Body, 1<<20)).Decode(&out)
	if res.StatusCode >= 300 || out.Error != "" {
		if out.Error == "invalid_grant" {
			return out, ErrReconnect
		}
		msg := out.Error
		if out.Description != "" {
			msg += ": " + out.Description
		}
		return out, fmt.Errorf("Google refused the token request (%s)", strings.TrimPrefix(msg, ": "))
	}
	return out, nil
}

// Exchange turns the code from the consent screen into a refresh token. redirectURI must be exactly the
// one the consent URL used.
func (d *Drive) Exchange(ctx context.Context, clientID, secret, code, redirectURI string) (string, error) {
	r, e := d.tokenRequest(ctx, url.Values{"grant_type": {"authorization_code"}, "code": {code}, "client_id": {clientID}, "client_secret": {secret}, "redirect_uri": {redirectURI}})
	if e != nil {
		return "", e
	}
	if r.RefreshToken == "" {
		return "", errors.New("Google returned no refresh token; remove the app under myaccount.google.com/permissions and connect again")
	}
	return r.RefreshToken, nil
}

// Access returns a valid access token, refreshing it a minute before it expires.
func (d *Drive) Access(ctx context.Context, clientID, secret, refresh string) (string, error) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.token != "" && d.owner == refresh && d.Now().Before(d.expiry.Add(-time.Minute)) {
		return d.token, nil
	}
	r, e := d.tokenRequest(ctx, url.Values{"grant_type": {"refresh_token"}, "refresh_token": {refresh}, "client_id": {clientID}, "client_secret": {secret}})
	if e != nil {
		return "", e
	}
	d.token, d.owner, d.expiry = r.AccessToken, refresh, d.Now().Add(time.Duration(r.ExpiresIn)*time.Second)
	return d.token, nil
}

// Revoke tells Google to forget the grant; failures are not fatal, the token is deleted locally anyway.
func (d *Drive) Revoke(ctx context.Context, token string) error {
	req, e := http.NewRequestWithContext(ctx, "POST", d.RevokeURL, strings.NewReader(url.Values{"token": {token}}.Encode()))
	if e != nil {
		return e
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	res, e := d.HTTP.Do(req)
	if e != nil {
		return e
	}
	res.Body.Close()
	d.mu.Lock()
	d.token = ""
	d.mu.Unlock()
	return nil
}

// api is a JSON call to the Drive API. Google's own error text (storageQuotaExceeded...) is passed on.
func (d *Drive) api(ctx context.Context, token, method, path string, query url.Values, body any, out any) error {
	var rd io.Reader
	if body != nil {
		b, _ := json.Marshal(body)
		rd = bytes.NewReader(b)
	}
	u := d.APIURL + path
	if len(query) > 0 {
		u += "?" + query.Encode()
	}
	req, e := http.NewRequestWithContext(ctx, method, u, rd)
	if e != nil {
		return e
	}
	req.Header.Set("Authorization", "Bearer "+token)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	res, e := d.HTTP.Do(req)
	if e != nil {
		return errors.New("cannot reach Google Drive")
	}
	defer res.Body.Close()
	raw, _ := io.ReadAll(io.LimitReader(res.Body, 4<<20))
	if res.StatusCode == 401 {
		return ErrReconnect
	}
	if res.StatusCode >= 300 {
		return driveError(res.StatusCode, raw)
	}
	if out != nil && len(raw) > 0 {
		return json.Unmarshal(raw, out)
	}
	return nil
}

func driveError(status int, raw []byte) error {
	var v struct {
		Error struct {
			Message string `json:"message"`
			Errors  []struct {
				Reason string `json:"reason"`
			} `json:"errors"`
		} `json:"error"`
	}
	json.Unmarshal(raw, &v)
	reason := ""
	if len(v.Error.Errors) > 0 {
		reason = v.Error.Errors[0].Reason
	}
	if v.Error.Message == "" {
		return fmt.Errorf("Google Drive answered %d", status)
	}
	if reason != "" {
		return fmt.Errorf("Google Drive: %s (%s)", v.Error.Message, reason)
	}
	return fmt.Errorf("Google Drive: %s", v.Error.Message)
}

type DriveFile struct {
	ID            string            `json:"id"`
	Name          string            `json:"name"`
	Size          int64             `json:"size,string"`
	CreatedTime   string            `json:"createdTime"`
	AppProperties map[string]string `json:"appProperties"`
}

// Folder returns the id of an existing, untrashed folder, or makes the named one.
func (d *Drive) Folder(ctx context.Context, token, id, name string) (string, error) {
	if id != "" {
		var f struct {
			ID      string `json:"id"`
			Trashed bool   `json:"trashed"`
		}
		e := d.api(ctx, token, "GET", "/files/"+url.PathEscape(id), url.Values{"fields": {"id,trashed"}}, nil, &f)
		if e == nil && !f.Trashed {
			return f.ID, nil
		}
		if e != nil && !strings.Contains(e.Error(), "notFound") && !strings.Contains(e.Error(), "not found") {
			return "", e
		}
	}
	var created struct {
		ID string `json:"id"`
	}
	e := d.api(ctx, token, "POST", "/files", url.Values{"fields": {"id"}}, map[string]any{"name": name, "mimeType": "application/vnd.google-apps.folder"}, &created)
	if e != nil {
		return "", e
	}
	return created.ID, nil
}

// List returns the files in a folder (this app's own files only, by scope).
func (d *Drive) List(ctx context.Context, token, folder string) ([]DriveFile, error) {
	var all []DriveFile
	page := ""
	for i := 0; i < 50; i++ {
		q := url.Values{"q": {"'" + folder + "' in parents and trashed=false"}, "pageSize": {"200"}, "fields": {"nextPageToken,files(id,name,size,createdTime,appProperties)"}}
		if page != "" {
			q.Set("pageToken", page)
		}
		var out struct {
			Files []DriveFile `json:"files"`
			Next  string      `json:"nextPageToken"`
		}
		if e := d.api(ctx, token, "GET", "/files", q, nil, &out); e != nil {
			return nil, e
		}
		all = append(all, out.Files...)
		if out.Next == "" {
			return all, nil
		}
		page = out.Next
	}
	return all, nil
}

func (d *Drive) Delete(ctx context.Context, token, id string) error {
	return d.api(ctx, token, "DELETE", "/files/"+url.PathEscape(id), nil, nil, nil)
}

// Upload sends a file with a resumable session: chunks of d.Chunk bytes, resumed from what Google reports
// after a failure. The session URL carries its own authorisation, so a long upload outlives the one-hour
// access token. progress gets the byte count after each chunk.
func (d *Drive) Upload(ctx context.Context, token, folder, path, name string, props map[string]string, progress func(sent, total int64)) (string, error) {
	f, e := os.Open(path)
	if e != nil {
		return "", e
	}
	defer f.Close()
	st, e := f.Stat()
	if e != nil {
		return "", e
	}
	total := st.Size()
	meta, _ := json.Marshal(map[string]any{"name": name, "parents": []string{folder}, "appProperties": props})
	req, e := http.NewRequestWithContext(ctx, "POST", d.UploadURL+"/files?uploadType=resumable&fields=id", bytes.NewReader(meta))
	if e != nil {
		return "", e
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json; charset=UTF-8")
	req.Header.Set("X-Upload-Content-Type", "application/zip")
	req.Header.Set("X-Upload-Content-Length", strconv.FormatInt(total, 10))
	res, e := d.HTTP.Do(req)
	if e != nil {
		return "", errors.New("cannot reach Google Drive")
	}
	raw, _ := io.ReadAll(io.LimitReader(res.Body, 1<<20))
	res.Body.Close()
	if res.StatusCode == 401 {
		return "", ErrReconnect
	}
	session := res.Header.Get("Location")
	if res.StatusCode >= 300 || session == "" {
		return "", driveError(res.StatusCode, raw)
	}
	chunk := d.Chunk
	if chunk <= 0 || chunk%(256<<10) != 0 {
		chunk = 8 << 20
	}
	var sent int64
	failures := 0
	buf := make([]byte, chunk)
	for {
		if ctx.Err() != nil {
			return "", ctx.Err()
		}
		n, _ := f.ReadAt(buf, sent)
		end := sent + int64(n) - 1
		put, e := http.NewRequestWithContext(ctx, "PUT", session, bytes.NewReader(buf[:n]))
		if e != nil {
			return "", e
		}
		if total == 0 {
			put.Header.Set("Content-Range", "bytes */0")
		} else {
			put.Header.Set("Content-Range", fmt.Sprintf("bytes %d-%d/%d", sent, end, total))
		}
		res, e = d.HTTP.Do(put)
		var status int
		var body []byte
		var rng string
		if e == nil {
			status = res.StatusCode
			rng = res.Header.Get("Range")
			body, _ = io.ReadAll(io.LimitReader(res.Body, 1<<20))
			res.Body.Close()
		}
		switch {
		case e == nil && (status == 200 || status == 201):
			var done struct {
				ID string `json:"id"`
			}
			json.Unmarshal(body, &done)
			if done.ID == "" {
				return "", errors.New("Google Drive did not return the file id")
			}
			if progress != nil {
				progress(total, total)
			}
			return done.ID, nil
		case e == nil && status == 308:
			failures = 0
			sent = resumeAt(rng)
			if progress != nil {
				progress(sent, total)
			}
		case e == nil && (status == 400 || status == 401 || status == 403 || status == 404):
			return "", driveError(status, body)
		default:
			failures++
			if failures > 6 {
				if e != nil {
					return "", errors.New("upload interrupted: " + e.Error())
				}
				return "", driveError(status, body)
			}
			select {
			case <-time.After(d.Wait):
			case <-ctx.Done():
				return "", ctx.Err()
			}
			// Ask Google what it has before sending anything again.
			probe, _ := http.NewRequestWithContext(ctx, "PUT", session, nil)
			probe.Header.Set("Content-Range", fmt.Sprintf("bytes */%d", total))
			if r2, e2 := d.HTTP.Do(probe); e2 == nil {
				b2, _ := io.ReadAll(io.LimitReader(r2.Body, 1<<20))
				r2.Body.Close()
				if r2.StatusCode == 200 || r2.StatusCode == 201 {
					var done struct {
						ID string `json:"id"`
					}
					json.Unmarshal(b2, &done)
					return done.ID, nil
				}
				if r2.StatusCode == 308 {
					sent = resumeAt(r2.Header.Get("Range"))
				}
			}
		}
	}
}

// resumeAt reads "bytes=0-12345" and returns the next byte to send; no header means nothing arrived yet.
func resumeAt(rng string) int64 {
	if i := strings.LastIndex(rng, "-"); i >= 0 {
		if v, e := strconv.ParseInt(rng[i+1:], 10, 64); e == nil {
			return v + 1
		}
	}
	return 0
}
