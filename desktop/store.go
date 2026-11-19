package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sort"
	"sync"
)

type Device struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Address string `json:"address"`
	Note    string `json:"note"`
}

// Summary è il riepilogo di un test salvato nello storico.
type Summary struct {
	ID        string   `json:"id"`
	Target    string   `json:"target"`
	IP        string   `json:"ip"`
	Name      string   `json:"name,omitempty"`
	Mode      string   `json:"mode"`
	Count     int      `json:"count"`
	Size      int      `json:"size"`
	TimeoutMs int      `json:"timeoutMs"`
	TTL       int      `json:"ttl"`
	StartedAt int64    `json:"startedAt"`
	EndedAt   int64    `json:"endedAt"`
	Sent      int      `json:"sent"`
	Received  int      `json:"received"`
	Min       *float64 `json:"min,omitempty"`
	Avg       *float64 `json:"avg,omitempty"`
	Max       *float64 `json:"max,omitempty"`
	Jitter    *float64 `json:"jitter,omitempty"`
	Outages   int      `json:"outages"`
	Network   string   `json:"network,omitempty"`
	BatchID   string   `json:"batchId,omitempty"`
	BatchName string   `json:"batchName,omitempty"`
}

type Detail struct {
	Samples []Sample `json:"samples"`
	Events  []Event  `json:"events"`
}

// Store salva schede, storico ed elenco di più schede nella cartella dati dell'utente
// (su Windows: %APPDATA%\PingPro).
type Store struct {
	mu      sync.Mutex
	dir     string
	Devices []Device  `json:"-"`
	History []Summary `json:"-"`
	Multi   []Device  `json:"-"`
}

const maxHistory = 1000

func dataDir() string {
	if d := os.Getenv("PINGPRO_DATA"); d != "" {
		return d
	}
	base, err := os.UserConfigDir()
	if err != nil {
		base = "."
	}
	return filepath.Join(base, "PingPro")
}

func OpenStore(dir string) *Store {
	s := &Store{dir: dir}
	_ = os.MkdirAll(filepath.Join(dir, "history"), 0o755)
	readJSON(filepath.Join(dir, "devices.json"), &s.Devices)
	readJSON(filepath.Join(dir, "history", "index.json"), &s.History)
	readJSON(filepath.Join(dir, "multi.json"), &s.Multi)
	return s
}

func readJSON(path string, v any) {
	if b, err := os.ReadFile(path); err == nil {
		_ = json.Unmarshal(b, v)
	}
}

func writeJSON(path string, v any) error {
	b, err := json.MarshalIndent(v, "", " ")
	if err != nil {
		return err
	}
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, b, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

func (s *Store) SaveDevices() error {
	sort.Slice(s.Devices, func(i, j int) bool { return s.Devices[i].Name < s.Devices[j].Name })
	return writeJSON(filepath.Join(s.dir, "devices.json"), s.Devices)
}

func (s *Store) SaveMulti() error { return writeJSON(filepath.Join(s.dir, "multi.json"), s.Multi) }

func (s *Store) DeviceName(addr string) string {
	for _, d := range s.Devices {
		if d.Address == addr {
			return d.Name
		}
	}
	return ""
}

func (s *Store) AddHistory(sum Summary, det Detail) {
	s.mu.Lock()
	defer s.mu.Unlock()
	_ = writeJSON(filepath.Join(s.dir, "history", sum.ID+".json"), det)
	s.History = append([]Summary{sum}, s.History...)
	if len(s.History) > maxHistory {
		for _, old := range s.History[maxHistory:] {
			_ = os.Remove(filepath.Join(s.dir, "history", old.ID+".json"))
		}
		s.History = s.History[:maxHistory]
	}
	_ = writeJSON(filepath.Join(s.dir, "history", "index.json"), s.History)
}

func (s *Store) HistoryDetail(id string) (*Summary, *Detail) {
	s.mu.Lock()
	defer s.mu.Unlock()
	for i := range s.History {
		if s.History[i].ID == id {
			var d Detail
			readJSON(filepath.Join(s.dir, "history", id+".json"), &d)
			sum := s.History[i]
			return &sum, &d
		}
	}
	return nil, nil
}

func (s *Store) DeleteHistory(id string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := s.History[:0]
	for _, h := range s.History {
		if h.ID != id {
			out = append(out, h)
		}
	}
	s.History = out
	_ = os.Remove(filepath.Join(s.dir, "history", id+".json"))
	_ = writeJSON(filepath.Join(s.dir, "history", "index.json"), s.History)
}

func (s *Store) ClearHistory() {
	s.mu.Lock()
	defer s.mu.Unlock()
	for _, h := range s.History {
		_ = os.Remove(filepath.Join(s.dir, "history", h.ID+".json"))
	}
	s.History = nil
	_ = writeJSON(filepath.Join(s.dir, "history", "index.json"), s.History)
}
