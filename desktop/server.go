package main

import (
	"crypto/rand"
	"embed"
	"encoding/hex"
	"encoding/json"
	"io/fs"
	"net"
	"net/http"
	"strconv"
	"strings"
	"sync/atomic"
	"time"
)

//go:embed web
var webFS embed.FS

type Server struct {
	app      *App
	token    string
	port     int
	lastSeen atomic.Int64
	clients  atomic.Int32
}

func newToken() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

func (s *Server) json(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	_ = json.NewEncoder(w).Encode(v)
}

func (s *Server) fail(w http.ResponseWriter, code int, msg string) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(map[string]string{"error": msg})
}

// guard: solo richieste dal PC stesso, verso l'host giusto e con la chiave della sessione.
func (s *Server) guard(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		host, _, _ := net.SplitHostPort(r.Host)
		if host != "127.0.0.1" && host != "localhost" {
			http.Error(w, "host non valido", http.StatusForbidden)
			return
		}
		if r.Header.Get("X-PingPro-Token") != s.token && r.URL.Query().Get("token") != s.token {
			http.Error(w, "non autorizzato", http.StatusForbidden)
			return
		}
		s.lastSeen.Store(time.Now().UnixMilli())
		next(w, r)
	}
}

func decode(r *http.Request, v any) error {
	defer r.Body.Close()
	return json.NewDecoder(http.MaxBytesReader(nil, r.Body, 1<<20)).Decode(v)
}

func (s *Server) routes() http.Handler {
	mux := http.NewServeMux()
	sub, _ := fs.Sub(webFS, "web")
	static := http.FileServer(http.FS(sub))
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		host, _, _ := net.SplitHostPort(r.Host)
		if host != "127.0.0.1" && host != "localhost" {
			http.Error(w, "host non valido", http.StatusForbidden)
			return
		}
		if r.URL.Path == "/" || r.URL.Path == "/index.html" {
			if r.URL.Query().Get("token") != s.token {
				http.Error(w, "Apri PingPro dal suo collegamento.", http.StatusForbidden)
				return
			}
			b, _ := webFS.ReadFile("web/index.html")
			html := strings.Replace(string(b), "__TOKEN__", s.token, 1)
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			w.Header().Set("Cache-Control", "no-store")
			w.Header().Set("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'")
			_, _ = w.Write([]byte(html))
			return
		}
		static.ServeHTTP(w, r)
	})

	// Stato in tempo reale (Server-Sent Events), aggiornato al massimo 5 volte al secondo.
	mux.HandleFunc("/api/events", s.guard(func(w http.ResponseWriter, r *http.Request) {
		fl, ok := w.(http.Flusher)
		if !ok {
			s.fail(w, 500, "streaming non supportato")
			return
		}
		w.Header().Set("Content-Type", "text/event-stream")
		w.Header().Set("Cache-Control", "no-store")
		s.clients.Add(1)
		defer s.clients.Add(-1)
		last := int64(-1)
		tick := time.NewTicker(200 * time.Millisecond)
		defer tick.Stop()
		keep := time.NewTicker(10 * time.Second)
		defer keep.Stop()
		for {
			select {
			case <-r.Context().Done():
				return
			case <-keep.C:
				_, _ = w.Write([]byte(": ok\n\n"))
				fl.Flush()
				s.lastSeen.Store(time.Now().UnixMilli())
			case <-tick.C:
				v := s.app.version.Load()
				if v == last {
					continue
				}
				last = v
				b, _ := json.Marshal(s.app.State())
				_, _ = w.Write([]byte("data: "))
				_, _ = w.Write(b)
				_, _ = w.Write([]byte("\n\n"))
				fl.Flush()
				s.lastSeen.Store(time.Now().UnixMilli())
			}
		}
	}))

	mux.HandleFunc("POST /api/test/start", s.guard(func(w http.ResponseWriter, r *http.Request) {
		var c Config
		if err := decode(r, &c); err != nil {
			s.fail(w, 400, "richiesta non valida")
			return
		}
		if err := s.app.StartSingle(c); err != nil {
			s.fail(w, 409, err.Error())
			return
		}
		s.json(w, map[string]bool{"ok": true})
	}))
	mux.HandleFunc("POST /api/test/stop", s.guard(func(w http.ResponseWriter, r *http.Request) {
		s.app.StopSingle()
		s.json(w, map[string]bool{"ok": true})
	}))
	mux.HandleFunc("POST /api/multi/start", s.guard(func(w http.ResponseWriter, r *http.Request) {
		var req struct {
			Targets []Device `json:"targets"`
			Name    string   `json:"name"`
			Config  Config   `json:"config"`
		}
		if err := decode(r, &req); err != nil {
			s.fail(w, 400, "richiesta non valida")
			return
		}
		if err := s.app.StartMulti(req.Targets, req.Config, req.Name); err != nil {
			s.fail(w, 409, err.Error())
			return
		}
		s.json(w, map[string]bool{"ok": true})
	}))
	mux.HandleFunc("POST /api/multi/stop", s.guard(func(w http.ResponseWriter, r *http.Request) {
		s.app.StopMulti()
		s.json(w, map[string]bool{"ok": true})
	}))
	mux.HandleFunc("GET /api/multi/list", s.guard(func(w http.ResponseWriter, r *http.Request) {
		s.json(w, append([]Device{}, s.app.store.Multi...))
	}))
	mux.HandleFunc("PUT /api/multi/list", s.guard(func(w http.ResponseWriter, r *http.Request) {
		var list []Device
		if err := decode(r, &list); err != nil {
			s.fail(w, 400, "richiesta non valida")
			return
		}
		if len(list) > maxMulti {
			list = list[:maxMulti]
		}
		s.app.store.Multi = list
		_ = s.app.store.SaveMulti()
		s.json(w, list)
	}))
	mux.HandleFunc("GET /api/multi/report", s.guard(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		_, _ = w.Write([]byte(multiReport(s.app.State().Multi)))
	}))
	mux.HandleFunc("POST /api/scan/start", s.guard(func(w http.ResponseWriter, r *http.Request) {
		var req struct{ From, To string }
		if err := decode(r, &req); err != nil {
			s.fail(w, 400, "richiesta non valida")
			return
		}
		if err := s.app.StartScan(req.From, req.To); err != nil {
			s.fail(w, 409, err.Error())
			return
		}
		s.json(w, map[string]bool{"ok": true})
	}))
	mux.HandleFunc("POST /api/scan/stop", s.guard(func(w http.ResponseWriter, r *http.Request) {
		s.app.StopScan()
		s.json(w, map[string]bool{"ok": true})
	}))
	mux.HandleFunc("GET /api/ipconfig", s.guard(func(w http.ResponseWriter, r *http.Request) {
		list := readAdapters()
		s.json(w, map[string]any{"adapters": list, "text": ipconfigText(list, false), "textAll": ipconfigText(list, true)})
	}))
	mux.HandleFunc("GET /api/devices", s.guard(func(w http.ResponseWriter, r *http.Request) {
		s.json(w, append([]Device{}, s.app.store.Devices...))
	}))
	mux.HandleFunc("POST /api/devices", s.guard(func(w http.ResponseWriter, r *http.Request) {
		var d Device
		if err := decode(r, &d); err != nil || strings.TrimSpace(d.Name) == "" || net.ParseIP(strings.TrimSpace(d.Address)) == nil && strings.TrimSpace(d.Address) == "" {
			s.fail(w, 400, "nome e indirizzo sono obbligatori")
			return
		}
		d.Name, d.Address, d.Note = strings.TrimSpace(d.Name), strings.TrimSpace(d.Address), strings.TrimSpace(d.Note)
		st := s.app.store
		replaced := false
		for i := range st.Devices {
			if (d.ID != "" && st.Devices[i].ID == d.ID) || st.Devices[i].Address == d.Address {
				d.ID = st.Devices[i].ID
				st.Devices[i] = d
				replaced = true
				break
			}
		}
		if !replaced {
			d.ID = newID()
			st.Devices = append(st.Devices, d)
		}
		_ = st.SaveDevices()
		s.json(w, d)
	}))
	mux.HandleFunc("DELETE /api/devices/{id}", s.guard(func(w http.ResponseWriter, r *http.Request) {
		st := s.app.store
		id := r.PathValue("id")
		out := st.Devices[:0]
		for _, d := range st.Devices {
			if d.ID != id {
				out = append(out, d)
			}
		}
		st.Devices = out
		_ = st.SaveDevices()
		s.json(w, map[string]bool{"ok": true})
	}))
	mux.HandleFunc("GET /api/history", s.guard(func(w http.ResponseWriter, r *http.Request) {
		s.app.store.mu.Lock()
		h := append([]Summary{}, s.app.store.History...)
		s.app.store.mu.Unlock()
		s.json(w, h)
	}))
	mux.HandleFunc("DELETE /api/history", s.guard(func(w http.ResponseWriter, r *http.Request) {
		s.app.store.ClearHistory()
		s.json(w, map[string]bool{"ok": true})
	}))
	mux.HandleFunc("GET /api/history/{id}", s.guard(func(w http.ResponseWriter, r *http.Request) {
		sum, det := s.app.store.HistoryDetail(r.PathValue("id"))
		if sum == nil {
			s.fail(w, 404, "test non trovato")
			return
		}
		s.json(w, map[string]any{"summary": sum, "detail": det})
	}))
	mux.HandleFunc("DELETE /api/history/{id}", s.guard(func(w http.ResponseWriter, r *http.Request) {
		s.app.store.DeleteHistory(r.PathValue("id"))
		s.json(w, map[string]bool{"ok": true})
	}))
	mux.HandleFunc("GET /api/history/{id}/export", s.guard(func(w http.ResponseWriter, r *http.Request) {
		sum, det := s.app.store.HistoryDetail(r.PathValue("id"))
		if sum == nil {
			s.fail(w, 404, "test non trovato")
			return
		}
		name := strings.Map(func(c rune) rune {
			if c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '.' || c == '-' {
				return c
			}
			return '_'
		}, sum.IP) + "_" + time.UnixMilli(sum.StartedAt).Format("20060102_150405")
		if r.URL.Query().Get("format") == "csv" {
			w.Header().Set("Content-Type", "text/csv; charset=utf-8")
			w.Header().Set("Content-Disposition", "attachment; filename=\"ping_"+name+".csv\"")
			_, _ = w.Write([]byte("\ufeff" + csvReport(*det)))
			return
		}
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		if r.URL.Query().Get("download") == "1" {
			w.Header().Set("Content-Disposition", "attachment; filename=\"ping_"+name+".txt\"")
		}
		_, _ = w.Write([]byte(windowsReport(*sum, *det)))
	}))
	mux.HandleFunc("GET /api/info", s.guard(func(w http.ResponseWriter, r *http.Request) {
		s.json(w, map[string]string{"version": appVersion, "platform": platformName, "data": dataDir()})
	}))
	return mux
}

func (s *Server) URL() string {
	return "http://127.0.0.1:" + strconv.Itoa(s.port) + "/?token=" + s.token
}
