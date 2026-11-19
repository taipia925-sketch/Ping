package main

import "net"

// Esito di una singola richiesta ICMP Echo.
type EchoResult struct {
	OK      bool
	RTT     float64 // millisecondi (con decimali)
	WinRTT  int     // tempo riportato da Windows in ms interi (come ping.exe)
	TTL     int     // 0 = non disponibile
	Bytes   int
	Message string // "Richiesta scaduta.", "Host di destinazione non raggiungibile.", ...
	Status  string // ok | timeout | unreachable | error
}

// Pinger invia richieste ICMP Echo verso un indirizzo.
type Pinger interface {
	Ping(dst net.IP, seq int, timeoutMs, size, ttl int) EchoResult
	Close()
}

// Contenuto dei dati identico a ping.exe di Windows.
func windowsPayload(size int) []byte {
	const pattern = "abcdefghijklmnopqrstuvw"
	b := make([]byte, size)
	for i := range b {
		b[i] = pattern[i%len(pattern)]
	}
	return b
}
