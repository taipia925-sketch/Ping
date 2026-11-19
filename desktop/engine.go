package main

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"net"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

func newID() string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

func nowMs() int64 { return time.Now().UnixMilli() }

// Config: valori predefiniti identici a ping.exe (-n 4, -l 32, -w 4000, -i 128, circa 1 s tra le richieste).
type Config struct {
	Target     string `json:"target"`
	Continuous bool   `json:"continuous"`
	Count      int    `json:"count"`
	Size       int    `json:"size"`
	TimeoutMs  int    `json:"timeoutMs"`
	TTL        int    `json:"ttl"`
}

func (c *Config) defaults() {
	if c.Count <= 0 {
		c.Count = 4
	}
	if c.Size < 0 || c.Size > 65500 {
		c.Size = 32
	}
	if c.Size == 0 {
		c.Size = 32
	}
	if c.TimeoutMs <= 0 {
		c.TimeoutMs = 4000
	}
	if c.TTL <= 0 || c.TTL > 255 {
		c.TTL = 128
	}
}

func (c Config) WindowsCommand() string {
	b := "ping"
	if c.Continuous {
		b += " -t"
	} else if c.Count != 4 {
		b += fmt.Sprintf(" -n %d", c.Count)
	}
	if c.Size != 32 {
		b += fmt.Sprintf(" -l %d", c.Size)
	}
	if c.TimeoutMs != 4000 {
		b += fmt.Sprintf(" -w %d", c.TimeoutMs)
	}
	if c.TTL != 128 {
		b += fmt.Sprintf(" -i %d", c.TTL)
	}
	return b + " " + c.Target
}

// Session è un test verso una scheda (usato sia per il test singolo sia per ogni scheda del test multiplo).
type Session struct {
	mu        sync.Mutex
	ID        string
	Cfg       Config
	Name      string
	IP        string
	Network   string
	SameNet   bool
	Running   bool
	Finished  bool
	StartedAt int64
	EndedAt   int64
	Error     string
	Tr        *Tracker
	cancel    atomic.Bool
}

type SessionView struct {
	ID              string   `json:"id"`
	Target          string   `json:"target"`
	Name            string   `json:"name,omitempty"`
	IP              string   `json:"ip,omitempty"`
	Network         string   `json:"network,omitempty"`
	SameNet         bool     `json:"sameNet"`
	Continuous      bool     `json:"continuous"`
	Count           int      `json:"count"`
	Command         string   `json:"command"`
	Running         bool     `json:"running"`
	Finished        bool     `json:"finished"`
	StartedAt       int64    `json:"startedAt"`
	EndedAt         int64    `json:"endedAt,omitempty"`
	Error           string   `json:"error,omitempty"`
	Status          string   `json:"status"`
	Stats           Stats    `json:"stats"`
	ConsecutiveLost int      `json:"consecutiveLost"`
	Outages         int      `json:"outages"`
	Samples         []Sample `json:"samples"`
	Events          []Event  `json:"events"`
	RecordID        string   `json:"recordId,omitempty"`
}

func (s *Session) View(maxSamples int, withEvents bool) SessionView {
	s.mu.Lock()
	defer s.mu.Unlock()
	v := SessionView{ID: s.ID, Target: s.Cfg.Target, Name: s.Name, IP: s.IP, Network: s.Network, SameNet: s.SameNet,
		Continuous: s.Cfg.Continuous, Count: s.Cfg.Count, Command: s.Cfg.WindowsCommand(), Running: s.Running,
		Finished: s.Finished, StartedAt: s.StartedAt, EndedAt: s.EndedAt, Error: s.Error,
		Status: s.Tr.Status, Stats: s.Tr.Stats, ConsecutiveLost: s.Tr.ConsecutiveLost, Outages: s.Tr.Outages}
	if s.Error != "" {
		v.Status = "error"
	} else if !s.Running && s.Finished {
		v.Status = "finished"
	}
	n := len(s.Tr.Samples)
	from := 0
	if maxSamples > 0 && n > maxSamples {
		from = n - maxSamples
	}
	v.Samples = append([]Sample{}, s.Tr.Samples[from:]...)
	if withEvents {
		v.Events = append([]Event{}, s.Tr.Events...)
	}
	return v
}

// App tiene lo stato di tutti i test.
type App struct {
	mu          sync.Mutex
	store       *Store
	single      *Session
	singleRec   string
	multi       []*Session
	multiName   string
	multiBatch  string
	multiRun    bool
	multiStart  int64
	multiEnd    int64
	scan        ScanState
	scanCancel  atomic.Bool
	version     atomic.Int64
	highLatency float64
	onAlert     func(Alert)
	lastAlert   atomic.Int64
}

func NewApp(store *Store) *App {
	return &App{store: store, highLatency: 100}
}

func (a *App) changed() { a.version.Add(1) }

func (a *App) Busy() bool {
	a.mu.Lock()
	defer a.mu.Unlock()
	return (a.single != nil && a.single.Running) || a.multiRun || a.scan.Running
}

// resolve: indirizzo IPv4 (anche da nome) e scheda di rete del PC che lo raggiunge direttamente.
func resolve(target string) (net.IP, error) {
	if ip := net.ParseIP(strings.TrimSpace(target)); ip != nil && ip.To4() != nil {
		return ip.To4(), nil
	}
	ra, err := net.ResolveIPAddr("ip4", strings.TrimSpace(target))
	if err != nil {
		return nil, err
	}
	return ra.IP.To4(), nil
}

func networkLabel(ip string) (string, bool) {
	ads := readAdapters()
	if a := localNetworkFor(ip, ads); a != nil {
		return fmt.Sprintf("%s · %s/%d", a.Name, a.IPv4, a.Prefix), true
	}
	for _, a := range ads {
		if a.Up && a.Gateway != "" {
			return fmt.Sprintf("%s · tramite gateway %s", a.Name, a.Gateway), false
		}
	}
	return "", false
}

// run esegue il ciclo delle richieste con la cadenza di Windows:
// circa 1 s tra una richiesta e l'altra; se la risposta non arriva si attende il timeout e si prosegue.
func (a *App) run(s *Session, onSample func(Alert)) {
	ip, err := resolve(s.Cfg.Target)
	if err != nil || ip == nil {
		s.mu.Lock()
		s.Error = fmt.Sprintf("Impossibile trovare l'host %s. Verificare il nome e riprovare.", s.Cfg.Target)
		s.Running, s.Finished, s.EndedAt = false, true, nowMs()
		s.Tr.AddEvent(Event{T: nowMs(), Kind: "error", Title: "Errore", Detail: s.Error})
		s.mu.Unlock()
		a.changed()
		return
	}
	label, same := networkLabel(ip.String())
	p, err := newPinger()
	if err != nil {
		s.mu.Lock()
		s.Error = "Impossibile avviare il ping: " + err.Error()
		s.Running, s.Finished, s.EndedAt = false, true, nowMs()
		s.mu.Unlock()
		a.changed()
		return
	}
	defer p.Close()
	s.mu.Lock()
	s.IP, s.Network, s.SameNet = ip.String(), label, same
	s.Tr.AddEvent(Event{T: nowMs(), Kind: "start", Title: "Test avviato verso " + ip.String(),
		Detail: fmt.Sprintf("Rete: %s · %d byte di dati", label, s.Cfg.Size)})
	if !same && ip.IsPrivate() {
		s.Tr.AddEvent(Event{T: nowMs(), Kind: "info", Title: "Indirizzo fuori dalla rete del PC",
			Detail: "Il PC non è collegato alla sottorete di " + ip.String() + ": verificare cavo o Wi-Fi."})
	}
	s.mu.Unlock()
	a.changed()

	seq := 0
	for !s.cancel.Load() {
		seq++
		t0 := time.Now()
		r := p.Ping(ip, seq, s.Cfg.TimeoutMs, s.Cfg.Size, s.Cfg.TTL)
		if s.cancel.Load() && !r.OK {
			break
		}
		smp := Sample{Seq: seq, T: t0.UnixMilli(), Status: r.Status, TTL: r.TTL, Bytes: r.Bytes, Msg: r.Message}
		if r.OK {
			smp.RTT = f64(r.RTT)
			smp.WinRTT = r.WinRTT
		}
		s.mu.Lock()
		alert := s.Tr.Add(smp)
		s.mu.Unlock()
		if onSample != nil {
			onSample(alert)
		}
		a.changed()
		if !s.Cfg.Continuous && seq >= s.Cfg.Count {
			break
		}
		next := t0.Add(time.Second)
		for !s.cancel.Load() && time.Now().Before(next) {
			time.Sleep(50 * time.Millisecond)
		}
	}
	s.mu.Lock()
	end := nowMs()
	title := "Test completato"
	if s.cancel.Load() {
		title = "Test interrotto dall'utente"
	}
	st := s.Tr.Stats
	s.Tr.AddEvent(Event{T: end, Kind: "stop", Title: title, Detail: fmt.Sprintf("%d inviati, %d ricevuti, %d persi", st.Sent, st.Received, st.Lost)})
	s.Running, s.Finished, s.EndedAt = false, true, end
	s.mu.Unlock()
	a.changed()
}

func (a *App) summaryOf(s *Session, batchID, batchName string) (Summary, Detail) {
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.Tr.Stats
	mode := "single"
	if s.Cfg.Continuous {
		mode = "continuous"
	}
	ip := s.IP
	if ip == "" {
		ip = s.Cfg.Target
	}
	return Summary{ID: newID(), Target: s.Cfg.Target, IP: ip, Name: s.Name, Mode: mode, Count: s.Cfg.Count, Size: s.Cfg.Size,
			TimeoutMs: s.Cfg.TimeoutMs, TTL: s.Cfg.TTL, StartedAt: s.StartedAt, EndedAt: s.EndedAt, Sent: st.Sent,
			Received: st.Received, Min: st.Min, Avg: st.Avg, Max: st.Max, Jitter: st.Jitter, Outages: s.Tr.Outages,
			Network: s.Network, BatchID: batchID, BatchName: batchName},
		Detail{Samples: append([]Sample{}, s.Tr.Samples...), Events: append([]Event{}, s.Tr.Events...)}
}

func (a *App) alert(al Alert) {
	if al == AlertNone || a.onAlert == nil {
		return
	}
	now := nowMs()
	if now-a.lastAlert.Load() < 3000 {
		return
	}
	a.lastAlert.Store(now)
	a.onAlert(al)
}

func (a *App) StartSingle(cfg Config) error {
	cfg.defaults()
	if strings.TrimSpace(cfg.Target) == "" {
		return fmt.Errorf("indirizzo mancante")
	}
	if a.Busy() {
		return fmt.Errorf("c'è già un test in corso")
	}
	s := &Session{ID: newID(), Cfg: cfg, Name: a.store.DeviceName(cfg.Target), Running: true, StartedAt: nowMs(), Tr: NewTracker(a.highLatency)}
	a.mu.Lock()
	a.single, a.singleRec = s, ""
	a.mu.Unlock()
	a.changed()
	go func() {
		a.run(s, a.alert)
		if s.Tr.Stats.Sent > 0 {
			sum, det := a.summaryOf(s, "", "")
			a.store.AddHistory(sum, det)
			a.mu.Lock()
			a.singleRec = sum.ID
			a.mu.Unlock()
		}
		a.changed()
	}()
	return nil
}

func (a *App) StopSingle() {
	a.mu.Lock()
	s := a.single
	a.mu.Unlock()
	if s != nil {
		s.cancel.Store(true)
	}
}

const maxMulti = 100

func (a *App) StartMulti(targets []Device, cfg Config, name string) error {
	cfg.defaults()
	if len(targets) == 0 {
		return fmt.Errorf("elenco vuoto")
	}
	if len(targets) > maxMulti {
		targets = targets[:maxMulti]
	}
	if a.Busy() {
		return fmt.Errorf("c'è già un test in corso")
	}
	if name == "" {
		name = fmt.Sprintf("%d schede", len(targets))
	}
	batch := newID()
	var sessions []*Session
	for _, t := range targets {
		c := cfg
		c.Target = t.Address
		n := t.Name
		if n == "" {
			n = a.store.DeviceName(t.Address)
		}
		sessions = append(sessions, &Session{ID: newID(), Cfg: c, Name: n, Running: true, StartedAt: nowMs(), Tr: NewTracker(a.highLatency)})
	}
	a.mu.Lock()
	a.multi, a.multiName, a.multiBatch, a.multiRun, a.multiStart, a.multiEnd = sessions, name, batch, true, nowMs(), 0
	a.mu.Unlock()
	a.changed()
	go func() {
		var wg sync.WaitGroup
		n := len(sessions)
		for i, s := range sessions {
			wg.Add(1)
			go func(i int, s *Session) {
				defer wg.Done()
				// Partenze sfalsate nel secondo, per non inviare tutto nello stesso istante.
				time.Sleep(time.Duration(i) * time.Second / time.Duration(n))
				if !s.cancel.Load() {
					a.run(s, a.alert)
				} else {
					s.mu.Lock()
					s.Running, s.Finished, s.EndedAt = false, true, nowMs()
					s.mu.Unlock()
				}
			}(i, s)
		}
		wg.Wait()
		for _, s := range sessions {
			if s.Tr.Stats.Sent > 0 {
				sum, det := a.summaryOf(s, batch, name)
				a.store.AddHistory(sum, det)
			}
		}
		a.mu.Lock()
		a.multiRun, a.multiEnd = false, nowMs()
		a.mu.Unlock()
		a.changed()
	}()
	return nil
}

func (a *App) StopMulti() {
	a.mu.Lock()
	defer a.mu.Unlock()
	for _, s := range a.multi {
		s.cancel.Store(true)
	}
}

// ---------- Cerca dispositivi ----------

type Found struct {
	Address   string  `json:"address"`
	RTT       float64 `json:"rtt"`
	TTL       int     `json:"ttl,omitempty"`
	Name      string  `json:"name,omitempty"`
	IsGateway bool    `json:"isGateway,omitempty"`
	IsSelf    bool    `json:"isSelf,omitempty"`
}

type ScanState struct {
	Running   bool    `json:"running"`
	From      string  `json:"from"`
	To        string  `json:"to"`
	Total     int     `json:"total"`
	Done      int     `json:"done"`
	Found     []Found `json:"found"`
	StartedAt int64   `json:"startedAt,omitempty"`
	EndedAt   int64   `json:"endedAt,omitempty"`
	Cancelled bool    `json:"cancelled,omitempty"`
}

const (
	scanMax         = 1024
	scanConcurrency = 32
	scanAttempts    = 2
	scanTimeoutMs   = 1000
)

func expandRange(from, to string, max int) ([]string, error) {
	a, b := net.ParseIP(from), net.ParseIP(to)
	if a == nil || b == nil || a.To4() == nil || b.To4() == nil {
		return nil, fmt.Errorf("indirizzi non validi")
	}
	x, y := ipToU32(a), ipToU32(b)
	if y < x {
		return nil, fmt.Errorf("il secondo indirizzo deve essere uguale o maggiore del primo")
	}
	if int(y-x)+1 > max {
		return nil, fmt.Errorf("troppi indirizzi (%d): al massimo %d", y-x+1, max)
	}
	var out []string
	for v := x; v <= y; v++ {
		out = append(out, u32ToIP(v))
		if v == 0xFFFFFFFF {
			break
		}
	}
	return out, nil
}

func (a *App) StartScan(from, to string) error {
	list, err := expandRange(from, to, scanMax)
	if err != nil {
		return err
	}
	if a.Busy() {
		return fmt.Errorf("c'è già un test in corso")
	}
	gateways, selfs := map[string]bool{}, map[string]bool{}
	for _, ad := range readAdapters() {
		if ad.Gateway != "" {
			gateways[ad.Gateway] = true
		}
		if ad.IPv4 != "" {
			selfs[ad.IPv4] = true
		}
	}
	a.scanCancel.Store(false)
	a.mu.Lock()
	a.scan = ScanState{Running: true, From: from, To: to, Total: len(list), StartedAt: nowMs()}
	a.mu.Unlock()
	a.changed()
	go func() {
		jobs := make(chan string)
		var wg sync.WaitGroup
		var fmu sync.Mutex
		var found []Found
		var done atomic.Int64
		for w := 0; w < scanConcurrency; w++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				p, err := newPinger()
				if err != nil {
					for range jobs {
						done.Add(1)
					}
					return
				}
				defer p.Close()
				for addr := range jobs {
					if !a.scanCancel.Load() {
						ip := net.ParseIP(addr).To4()
						for att := 1; att <= scanAttempts; att++ {
							r := p.Ping(ip, att, scanTimeoutMs, 32, 128)
							if r.OK {
								fmu.Lock()
								found = append(found, Found{Address: addr, RTT: r.RTT, TTL: r.TTL, IsGateway: gateways[addr], IsSelf: selfs[addr]})
								fmu.Unlock()
								break
							}
							if a.scanCancel.Load() {
								break
							}
						}
					}
					done.Add(1)
					a.mu.Lock()
					a.scan.Done = int(done.Load())
					fmu.Lock()
					a.scan.Found = sortedFound(found)
					fmu.Unlock()
					a.mu.Unlock()
					a.changed()
				}
			}()
		}
		for _, addr := range list {
			if a.scanCancel.Load() {
				break
			}
			jobs <- addr
		}
		close(jobs)
		wg.Wait()
		// Nomi dei dispositivi (DNS inverso / nomi Windows), in parallelo e con un tempo massimo.
		var nwg sync.WaitGroup
		for i := range found {
			nwg.Add(1)
			go func(i int) {
				defer nwg.Done()
				ch := make(chan string, 1)
				go func() {
					names, err := net.LookupAddr(found[i].Address)
					if err == nil && len(names) > 0 {
						ch <- strings.TrimSuffix(names[0], ".")
					} else {
						ch <- ""
					}
				}()
				select {
				case n := <-ch:
					found[i].Name = n
				case <-time.After(3 * time.Second):
				}
			}(i)
		}
		nwg.Wait()
		a.mu.Lock()
		a.scan.Found = sortedFound(found)
		a.scan.Done = int(done.Load())
		a.scan.Running, a.scan.EndedAt, a.scan.Cancelled = false, nowMs(), a.scanCancel.Load()
		a.mu.Unlock()
		a.changed()
	}()
	return nil
}

func sortedFound(f []Found) []Found {
	out := append([]Found{}, f...)
	sort.Slice(out, func(i, j int) bool {
		return ipToU32(net.ParseIP(out[i].Address)) < ipToU32(net.ParseIP(out[j].Address))
	})
	return out
}

func (a *App) StopScan() { a.scanCancel.Store(true) }

// ---------- Stato complessivo per l'interfaccia ----------

type MultiView struct {
	Running   bool          `json:"running"`
	Name      string        `json:"name"`
	StartedAt int64         `json:"startedAt"`
	EndedAt   int64         `json:"endedAt,omitempty"`
	Targets   []SessionView `json:"targets"`
	BatchID   string        `json:"batchId,omitempty"`
}

type StateView struct {
	Version  int64        `json:"version"`
	Single   *SessionView `json:"single"`
	Multi    MultiView    `json:"multi"`
	Scan     ScanState    `json:"scan"`
	Busy     bool         `json:"busy"`
	Platform string       `json:"platform"`
}

func (a *App) State() StateView {
	a.mu.Lock()
	single, rec := a.single, a.singleRec
	multi := append([]*Session(nil), a.multi...)
	mv := MultiView{Running: a.multiRun, Name: a.multiName, StartedAt: a.multiStart, EndedAt: a.multiEnd, BatchID: a.multiBatch}
	scan := a.scan
	scan.Found = append([]Found{}, a.scan.Found...)
	a.mu.Unlock()
	st := StateView{Version: a.version.Load(), Multi: mv, Scan: scan, Platform: platformName}
	if single != nil {
		v := single.View(300, true)
		v.RecordID = rec
		st.Single = &v
	}
	for _, s := range multi {
		st.Multi.Targets = append(st.Multi.Targets, s.View(40, false))
	}
	st.Busy = (single != nil && single.Running) || mv.Running || scan.Running
	return st
}
