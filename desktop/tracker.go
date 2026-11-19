package main

import (
	"fmt"
	"math"
	"sort"
)

// Sample è il risultato di una richiesta, come una riga di ping.exe.
type Sample struct {
	Seq    int      `json:"seq"`
	T      int64    `json:"t"`
	Status string   `json:"status"` // ok | timeout | unreachable | error
	RTT    *float64 `json:"rtt,omitempty"`
	WinRTT int      `json:"winRtt,omitempty"`
	TTL    int      `json:"ttl,omitempty"`
	Bytes  int      `json:"bytes,omitempty"`
	Msg    string   `json:"msg,omitempty"`
}

func (s Sample) Lost() bool { return s.Status != "ok" }

type Event struct {
	T      int64  `json:"t"`
	Kind   string `json:"kind"` // start info loss outage_start outage_end spike stop error
	Title  string `json:"title"`
	Detail string `json:"detail,omitempty"`
	Seq    int    `json:"seq,omitempty"`
}

// Stats: calcolate come Windows (min/max/medio sulle risposte, perdita = persi/inviati) più il jitter.
type Stats struct {
	Sent     int      `json:"sent"`
	Received int      `json:"received"`
	Lost     int      `json:"lost"`
	LossPct  float64  `json:"lossPct"`
	Min      *float64 `json:"min,omitempty"`
	Max      *float64 `json:"max,omitempty"`
	Avg      *float64 `json:"avg,omitempty"`
	Last     *float64 `json:"last,omitempty"`
	Jitter   *float64 `json:"jitter,omitempty"`
	sum      float64
	jsum     float64
	jcount   int
	prev     *float64
}

func f64(v float64) *float64 { return &v }

func (st *Stats) Add(s Sample) {
	st.Sent++
	if s.Lost() || s.RTT == nil {
		st.Last = nil
	} else {
		r := *s.RTT
		st.Received++
		st.sum += r
		if st.Min == nil || r < *st.Min {
			st.Min = f64(r)
		}
		if st.Max == nil || r > *st.Max {
			st.Max = f64(r)
		}
		st.Avg = f64(st.sum / float64(st.Received))
		if st.prev != nil {
			st.jsum += math.Abs(r - *st.prev)
			st.jcount++
			st.Jitter = f64(st.jsum / float64(st.jcount))
		}
		st.prev = f64(r)
		st.Last = f64(r)
	}
	st.Lost = st.Sent - st.Received
	if st.Sent > 0 {
		st.LossPct = float64(st.Lost) * 100 / float64(st.Sent)
	}
}

const (
	outageThreshold = 2  // richieste consecutive senza risposta per "NON RISPONDE"
	recentWindow    = 10 // finestra per lo stato "INSTABILE"
)

type Alert int

const (
	AlertNone Alert = iota
	AlertDown
	AlertRecovered
)

// Tracker aggiorna statistiche, stato ed eventi pacchetto dopo pacchetto (stessa logica dell'app Android).
type Tracker struct {
	Samples         []Sample
	Events          []Event
	Stats           Stats
	Status          string
	ConsecutiveLost int
	Outages         int
	HighLatency     float64
	outageStart     int64
	outageStartSeq  int
}

func NewTracker(highLatency float64) *Tracker {
	return &Tracker{Status: "starting", HighLatency: highLatency}
}

func (t *Tracker) AddEvent(e Event) { t.Events = append(t.Events, e) }

func (t *Tracker) Add(s Sample) Alert {
	t.Samples = append(t.Samples, s)
	t.Stats.Add(s)
	alert := AlertNone
	if s.Lost() {
		t.ConsecutiveLost++
		if t.ConsecutiveLost == 1 {
			t.outageStart = s.T
			t.outageStartSeq = s.Seq
			msg := s.Msg
			if msg == "" {
				msg = "Nessuna risposta entro il timeout"
			}
			t.AddEvent(Event{T: s.T, Kind: "loss", Title: fmt.Sprintf("Pacchetto #%d perso", s.Seq), Detail: msg, Seq: s.Seq})
		}
		if t.ConsecutiveLost == outageThreshold {
			t.Outages++
			alert = AlertDown
			t.AddEvent(Event{T: s.T, Kind: "outage_start", Title: "Interruzione: la scheda non risponde",
				Detail: fmt.Sprintf("Nessuna risposta da %d richieste consecutive (dal pacchetto #%d)", outageThreshold, t.outageStartSeq), Seq: s.Seq})
		}
	} else {
		if t.ConsecutiveLost >= outageThreshold {
			secs := math.Round(float64(s.T-t.outageStart) / 1000)
			t.AddEvent(Event{T: s.T, Kind: "outage_end", Title: "Connessione ripristinata",
				Detail: fmt.Sprintf("Interruzione di circa %.0f s, %d pacchetti persi", secs, t.ConsecutiveLost), Seq: s.Seq})
			alert = AlertRecovered
		}
		t.ConsecutiveLost = 0
		t.detectSpike(s)
	}
	t.Status = t.computeStatus()
	return alert
}

func (t *Tracker) detectSpike(s Sample) {
	if s.RTT == nil {
		return
	}
	var recent []float64
	for i := len(t.Samples) - 2; i >= 0 && len(recent) < 20; i-- {
		if t.Samples[i].RTT != nil {
			recent = append(recent, *t.Samples[i].RTT)
		}
	}
	if len(recent) < 5 {
		return
	}
	sort.Float64s(recent)
	median := recent[len(recent)/2]
	if *s.RTT > median*4 && *s.RTT-median > 20 {
		t.AddEvent(Event{T: s.T, Kind: "spike", Title: "Picco di latenza: " + fmtMs(*s.RTT) + " ms",
			Detail: "Valore tipico in quel momento: " + fmtMs(median) + " ms", Seq: s.Seq})
	}
}

func (t *Tracker) computeStatus() string {
	if t.ConsecutiveLost >= outageThreshold {
		return "down"
	}
	if t.ConsecutiveLost > 0 {
		return "unstable"
	}
	from := len(t.Samples) - recentWindow
	if from < 0 {
		from = 0
	}
	for _, s := range t.Samples[from:] {
		if s.Lost() {
			return "unstable"
		}
	}
	if n := len(t.Samples); n > 0 && t.Samples[n-1].RTT != nil && *t.Samples[n-1].RTT > t.HighLatency {
		return "slow"
	}
	return "online"
}
