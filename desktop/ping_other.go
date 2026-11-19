//go:build !windows

package main

import (
	"errors"
	"math"
	"net"
	"os"
	"time"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv4"
)

// Motore per Linux/macOS (sviluppo e prove): socket ICMP "ping" non privilegiato.
type sockPinger struct {
	conn *icmp.PacketConn
	id   int
}

func newPinger() (Pinger, error) {
	c, err := icmp.ListenPacket("udp4", "0.0.0.0")
	if err != nil {
		return nil, err
	}
	_ = c.IPv4PacketConn().SetControlMessage(ipv4.FlagTTL, true)
	return &sockPinger{conn: c, id: os.Getpid() & 0xffff}, nil
}

func (p *sockPinger) Ping(dst net.IP, seq int, timeoutMs, size, ttl int) EchoResult {
	_ = p.conn.IPv4PacketConn().SetTTL(ttl)
	msg := icmp.Message{Type: ipv4.ICMPTypeEcho, Code: 0, Body: &icmp.Echo{ID: p.id, Seq: seq & 0xffff, Data: windowsPayload(size)}}
	b, _ := msg.Marshal(nil)
	start := time.Now()
	if _, err := p.conn.WriteTo(b, &net.UDPAddr{IP: dst}); err != nil {
		var ne *net.OpError
		if errors.As(err, &ne) {
			return EchoResult{Status: "unreachable", Message: "Rete di destinazione non raggiungibile."}
		}
		return EchoResult{Status: "error", Message: "Errore di trasmissione."}
	}
	deadline := start.Add(time.Duration(timeoutMs) * time.Millisecond)
	buf := make([]byte, 65536)
	for {
		_ = p.conn.SetReadDeadline(deadline)
		n, cm, _, err := p.conn.IPv4PacketConn().ReadFrom(buf)
		if err != nil {
			return EchoResult{Status: "timeout", Message: "Richiesta scaduta."}
		}
		rm, err := icmp.ParseMessage(1, buf[:n])
		if err != nil || rm.Type != ipv4.ICMPTypeEchoReply {
			continue
		}
		echo, ok := rm.Body.(*icmp.Echo)
		if !ok || echo.Seq != seq&0xffff {
			continue
		}
		rtt := float64(time.Since(start).Microseconds()) / 1000.0
		t := 0
		if cm != nil {
			t = cm.TTL
		}
		return EchoResult{OK: true, Status: "ok", RTT: rtt, WinRTT: int(math.Round(rtt)), TTL: t, Bytes: len(echo.Data)}
	}
}

func (p *sockPinger) Close() { _ = p.conn.Close() }
