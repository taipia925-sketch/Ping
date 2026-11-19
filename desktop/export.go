package main

import (
	"fmt"
	"math"
	"strings"
)

func winTime(s Sample) string {
	if s.RTT == nil {
		return ""
	}
	if *s.RTT < 1 {
		return "durata<1ms"
	}
	return fmt.Sprintf("durata=%dms", int(math.Round(*s.RTT)))
}

// windowsReport: stesso formato dell'output di ping.exe (Windows in italiano), con i valori precisi in più.
func windowsReport(s Summary, d Detail) string {
	var b strings.Builder
	name := s.IP
	if s.Name != "" {
		name = s.Name + " (" + s.IP + ")"
	}
	fmt.Fprintf(&b, "PingPro – %s   %s\n", name, fmtDateTime(s.StartedAt))
	cmd := "ping"
	if s.Mode == "continuous" {
		cmd += " -t"
	} else if s.Count != 4 {
		cmd += fmt.Sprintf(" -n %d", s.Count)
	}
	fmt.Fprintf(&b, "Comando equivalente: %s %s\n", cmd, s.Target)
	if s.Network != "" {
		fmt.Fprintf(&b, "Rete del PC: %s\n", s.Network)
	}
	b.WriteString("\n")
	host := s.IP
	if s.Target != s.IP {
		host = s.Target + " [" + s.IP + "]"
	}
	fmt.Fprintf(&b, "Esecuzione di Ping %s con %d byte di dati:\n", host, s.Size)
	for _, p := range d.Samples {
		if p.Status == "ok" {
			ttl := ""
			if p.TTL > 0 {
				ttl = fmt.Sprintf(" TTL=%d", p.TTL)
			}
			fmt.Fprintf(&b, "Risposta da %s: byte=%d %s%s   (%s ms)\n", s.IP, p.Bytes, winTime(p), ttl, fmtMs(*p.RTT))
		} else if p.Msg != "" {
			b.WriteString(p.Msg + "\n")
		} else {
			b.WriteString("Richiesta scaduta.\n")
		}
	}
	lost := s.Sent - s.Received
	pct := 0
	if s.Sent > 0 {
		pct = int(math.Round(float64(lost) * 100 / float64(s.Sent)))
	}
	fmt.Fprintf(&b, "\nStatistiche Ping per %s:\n    Pacchetti: Trasmessi = %d, Ricevuti = %d,\n    Persi = %d (%d%% persi),\n", s.IP, s.Sent, s.Received, lost, pct)
	if s.Received > 0 && s.Min != nil {
		b.WriteString("Tempo approssimativo percorsi andata/ritorno in millisecondi:\n")
		fmt.Fprintf(&b, "    Minimo = %dms, Massimo = %dms, Medio = %dms\n", int(math.Round(*s.Min)), int(math.Round(*s.Max)), int(math.Round(*s.Avg)))
		jit := "—"
		if s.Jitter != nil {
			jit = fmtMs(*s.Jitter)
		}
		fmt.Fprintf(&b, "Valori precisi: minimo %s ms, massimo %s ms, medio %s ms, jitter %s ms\n", fmtMs(*s.Min), fmtMs(*s.Max), fmtMs(*s.Avg), jit)
	}
	fmt.Fprintf(&b, "Durata del test: %s", fmtDuration(s.EndedAt-s.StartedAt))
	if s.Outages > 0 {
		fmt.Fprintf(&b, " · Interruzioni: %d", s.Outages)
	}
	b.WriteString("\n")
	return b.String()
}

func csvReport(d Detail) string {
	var b strings.Builder
	b.WriteString("seq;data_ora;esito;tempo_ms;ttl;byte;messaggio\r\n")
	for _, p := range d.Samples {
		esito := map[string]string{"ok": "risposta", "timeout": "scaduta", "unreachable": "non_raggiungibile", "error": "errore"}[p.Status]
		rtt := ""
		if p.RTT != nil {
			rtt = strings.Replace(fmt.Sprintf("%.3f", *p.RTT), ".", ",", 1)
		}
		ttl, by := "", ""
		if p.TTL > 0 {
			ttl = fmt.Sprint(p.TTL)
		}
		if p.Status == "ok" {
			by = fmt.Sprint(p.Bytes)
		}
		fmt.Fprintf(&b, "%d;%s;%s;%s;%s;%s;%s\r\n", p.Seq, fmtDateTime(p.T), esito, rtt, ttl, by, strings.ReplaceAll(p.Msg, ";", ","))
	}
	return b.String()
}

// multiReport: riepilogo del test di più schede, una riga per scheda.
func multiReport(m MultiView) string {
	var b strings.Builder
	ok, warn, down := 0, 0, 0
	for _, t := range m.Targets {
		switch verdict(t) {
		case "OK":
			ok++
		case "con perdite":
			warn++
		case "NON RISPONDE", "ERRORE":
			down++
		}
	}
	fmt.Fprintf(&b, "PingPro – %s   %s\n", m.Name, fmtDateTime(m.StartedAt))
	end := m.EndedAt
	if end == 0 {
		end = nowMs()
	}
	fmt.Fprintf(&b, "%d schede · durata %s\nRispondono: %d · Con perdite: %d · Non rispondono: %d\n\n", len(m.Targets), fmtDuration(end-m.StartedAt), ok, warn, down)
	for _, t := range m.Targets {
		label := t.Target
		if t.Name != "" {
			label = t.Name + " (" + t.Target + ")"
		}
		fmt.Fprintf(&b, "%-13s%s\n", verdict(t), label)
		if t.Error != "" {
			fmt.Fprintf(&b, "             %s\n", t.Error)
			continue
		}
		st := t.Stats
		line := fmt.Sprintf("             Trasmessi %d, Ricevuti %d, Persi %d (%d%%)", st.Sent, st.Received, st.Lost, int(math.Round(st.LossPct)))
		if st.Min != nil {
			line += fmt.Sprintf(" · Min %s / Medio %s / Max %s ms", fmtMs(*st.Min), fmtMs(*st.Avg), fmtMs(*st.Max))
		}
		b.WriteString(line + "\n")
	}
	return b.String()
}

func verdict(t SessionView) string {
	switch {
	case t.Error != "":
		return "ERRORE"
	case t.Stats.Sent == 0:
		return "in attesa"
	case t.Running && t.Status == "down":
		return "NON RISPONDE"
	case t.Stats.Received == 0:
		return "NON RISPONDE"
	case t.Stats.Lost > 0:
		return "con perdite"
	}
	return "OK"
}
