package main

import (
	"net"
	"os"
	"strings"
	"testing"
	"time"
)

func ok(seq int, rtt float64) Sample {
	return Sample{Seq: seq, T: int64(seq) * 1000, Status: "ok", RTT: f64(rtt), TTL: 64, Bytes: 32}
}
func lost(seq int) Sample {
	return Sample{Seq: seq, T: int64(seq) * 1000, Status: "timeout", Msg: "Richiesta scaduta."}
}

func TestStatsLikeWindows(t *testing.T) {
	var st Stats
	for _, s := range []Sample{ok(1, 1), ok(2, 3), lost(3), ok(4, 2)} {
		st.Add(s)
	}
	if st.Sent != 4 || st.Received != 3 || st.Lost != 1 || st.LossPct != 25 {
		t.Fatalf("conteggi errati: %+v", st)
	}
	if *st.Min != 1 || *st.Max != 3 || *st.Avg != 2 || *st.Jitter != 1.5 {
		t.Fatalf("statistiche errate: min %v max %v avg %v jit %v", *st.Min, *st.Max, *st.Avg, *st.Jitter)
	}
}

func TestTrackerOutageAndRecovery(t *testing.T) {
	tr := NewTracker(100)
	if tr.Add(ok(1, 1)) != AlertNone || tr.Status != "online" {
		t.Fatal("online")
	}
	tr.Add(lost(2))
	if tr.Status != "unstable" {
		t.Fatal("instabile dopo 1 perso")
	}
	if tr.Add(lost(3)) != AlertDown || tr.Status != "down" {
		t.Fatal("non risponde dopo 2 persi")
	}
	if tr.Add(ok(4, 1)) != AlertRecovered {
		t.Fatal("ripristino")
	}
	kinds := []string{}
	for _, e := range tr.Events {
		kinds = append(kinds, e.Kind)
	}
	if strings.Join(kinds, ",") != "loss,outage_start,outage_end" {
		t.Fatal("eventi:", kinds)
	}
}

func TestWindowsCommandAndPayload(t *testing.T) {
	c := Config{Target: "192.168.1.100"}
	c.defaults()
	if c.WindowsCommand() != "ping 192.168.1.100" {
		t.Fatal(c.WindowsCommand())
	}
	c.Continuous = true
	if c.WindowsCommand() != "ping -t 192.168.1.100" {
		t.Fatal(c.WindowsCommand())
	}
	if string(windowsPayload(32)) != "abcdefghijklmnopqrstuvwabcdefghi" {
		t.Fatal("payload diverso da ping.exe")
	}
}

func TestSubnets(t *testing.T) {
	a, b := hostRange("192.168.1.104", 24)
	if a != "192.168.1.1" || b != "192.168.1.254" || prefixToMask(24) != "255.255.255.0" {
		t.Fatal(a, b)
	}
	if !sameSubnet("192.168.1.104", "192.168.1.254", 24) || sameSubnet("192.168.1.4", "192.168.2.4", 24) {
		t.Fatal("sameSubnet")
	}
	l, err := expandRange("192.168.1.254", "192.168.2.1", 10)
	if err != nil || strings.Join(l, " ") != "192.168.1.254 192.168.1.255 192.168.2.0 192.168.2.1" {
		t.Fatal(l, err)
	}
	if _, err := expandRange("10.0.0.5", "10.0.0.1", 10); err == nil {
		t.Fatal("intervallo invertito accettato")
	}
}

func TestReports(t *testing.T) {
	sum := Summary{IP: "192.168.1.100", Target: "192.168.1.100", Mode: "single", Count: 4, Size: 32, Sent: 4, Received: 3,
		Min: f64(0.42), Avg: f64(1.7), Max: f64(3.6), Jitter: f64(2.8), StartedAt: 0, EndedAt: 4000}
	txt := windowsReport(sum, Detail{Samples: []Sample{ok(1, 0.42), ok(2, 3.6), lost(3), ok(4, 1.2)}})
	for _, want := range []string{"Esecuzione di Ping 192.168.1.100 con 32 byte di dati:", "byte=32 durata<1ms TTL=64", "byte=32 durata=4ms TTL=64",
		"Richiesta scaduta.", "Pacchetti: Trasmessi = 4, Ricevuti = 3,", "Persi = 1 (25% persi),"} {
		if !strings.Contains(txt, want) {
			t.Fatalf("manca %q in:\n%s", want, txt)
		}
	}
	list := []Adapter{{Name: "Ethernet", Type: "Ethernet", Up: true, IPv4: "192.168.1.35", Prefix: 24, Mask: "255.255.255.0", Gateway: "192.168.1.254", IPv6: []string{"fe80::1", "2001:db8::5"}}}
	ip := ipconfigText(list, false)
	for _, want := range []string{"Scheda Ethernet Ethernet:", "   Indirizzo IPv4. . . . . . . . . . . . : 192.168.1.35", "   Gateway predefinito . . . . . . . . . : 192.168.1.254",
		"locale rispetto al collegamento . : fe80::1", "   Indirizzo IPv6. . . . . . . . . . . . : 2001:db8::5"} {
		if !strings.Contains(ip, want) {
			t.Fatalf("ipconfig: manca %q", want)
		}
	}
}

// Prove reali con la rete (loopback): richiedono il permesso dei socket ICMP "ping".
func canPing(t *testing.T) {
	p, err := newPinger()
	if err != nil {
		t.Skip("socket ICMP non disponibile:", err)
	}
	p.Close()
}

func TestRealPingLoopbackAndTimeout(t *testing.T) {
	canPing(t)
	p, _ := newPinger()
	defer p.Close()
	r := p.Ping(net.ParseIP("127.0.0.1"), 1, 1000, 32, 128)
	if !r.OK || r.Bytes != 32 || r.RTT <= 0 || r.RTT > 50 {
		t.Fatalf("ping 127.0.0.1: %+v", r)
	}
	start := time.Now()
	r = p.Ping(net.ParseIP("192.0.2.123"), 2, 800, 32, 128) // TEST-NET-1: nessuno risponde
	if r.OK || r.Status == "ok" || time.Since(start) < 700*time.Millisecond && r.Status == "timeout" {
		t.Fatalf("atteso timeout, ottenuto %+v in %v", r, time.Since(start))
	}
}

func waitFor(t *testing.T, d time.Duration, f func() bool) {
	end := time.Now().Add(d)
	for time.Now().Before(end) {
		if f() {
			return
		}
		time.Sleep(50 * time.Millisecond)
	}
	t.Fatal("tempo scaduto")
}

func TestEngineEndToEnd(t *testing.T) {
	canPing(t)
	dir := t.TempDir()
	os.Setenv("PINGPRO_DATA", dir)
	app := NewApp(OpenStore(dir))

	// Test singolo: 4 richieste con circa 1 s tra l'una e l'altra, come ping.exe.
	start := time.Now()
	if err := app.StartSingle(Config{Target: "127.0.0.1"}); err != nil {
		t.Fatal(err)
	}
	waitFor(t, 8*time.Second, func() bool { s := app.State().Single; return s != nil && !s.Running && s.RecordID != "" })
	el := time.Since(start)
	s := app.State().Single
	if s.Stats.Sent != 4 || s.Stats.Received != 4 || el < 2900*time.Millisecond || el > 5*time.Second {
		t.Fatalf("singolo: %+v in %v", s.Stats, el)
	}
	if len(app.store.History) != 1 {
		t.Fatal("non salvato nello storico")
	}

	// Test continuo verso un indirizzo che non risponde: stato "down" e interruzione da Interrompi.
	if err := app.StartSingle(Config{Target: "192.0.2.77", Continuous: true, TimeoutMs: 400}); err != nil {
		t.Fatal(err)
	}
	waitFor(t, 5*time.Second, func() bool { return app.State().Single.Status == "down" })
	app.StopSingle()
	waitFor(t, 3*time.Second, func() bool { return !app.State().Single.Running })

	// Più schede: tre indirizzi di loopback e uno che non risponde.
	targets := []Device{{Address: "127.0.0.1", Name: "A"}, {Address: "127.0.0.2"}, {Address: "127.0.0.3"}, {Address: "192.0.2.9", Name: "Spenta"}}
	if err := app.StartMulti(targets, Config{TimeoutMs: 400}, "Prova"); err != nil {
		t.Fatal(err)
	}
	waitFor(t, 10*time.Second, func() bool { m := app.State().Multi; return !m.Running && len(m.Targets) == 4 })
	m := app.State().Multi
	rep := multiReport(m)
	if !strings.Contains(rep, "Rispondono: 3 · Con perdite: 0 · Non rispondono: 1") || !strings.Contains(rep, "NON RISPONDE Spenta (192.0.2.9)") {
		t.Fatal("riepilogo:\n" + rep)
	}

	// Cerca dispositivi su 127.0.0.1–127.0.0.20: rispondono tutti (loopback).
	if err := app.StartScan("127.0.0.1", "127.0.0.20"); err != nil {
		t.Fatal(err)
	}
	waitFor(t, 15*time.Second, func() bool { sc := app.State().Scan; return !sc.Running && sc.EndedAt > 0 })
	sc := app.State().Scan
	if len(sc.Found) != 20 || sc.Found[0].Address != "127.0.0.1" || sc.Found[19].Address != "127.0.0.20" {
		t.Fatalf("ricerca: %d trovati %+v", len(sc.Found), sc.Found)
	}
	if _, err := expandRange("10.0.0.0", "10.0.255.255", scanMax); err == nil {
		t.Fatal("limite della ricerca non applicato")
	}
}
