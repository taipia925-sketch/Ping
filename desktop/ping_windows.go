//go:build windows

package main

import (
	"net"
	"sync"
	"syscall"
	"time"
	"unsafe"
)

// Motore PING per Windows: usa IcmpSendEcho di iphlpapi.dll, la stessa funzione usata da ping.exe.
// Non richiede privilegi di amministratore e non apre porte nel firewall.

var (
	iphlpapi         = syscall.NewLazyDLL("iphlpapi.dll")
	procIcmpCreate   = iphlpapi.NewProc("IcmpCreateFile")
	procIcmpClose    = iphlpapi.NewProc("IcmpCloseHandle")
	procIcmpSendEcho = iphlpapi.NewProc("IcmpSendEcho")
)

type ipOptionInformation struct {
	Ttl         uint8
	Tos         uint8
	Flags       uint8
	OptionsSize uint8
	OptionsData uintptr
}

type icmpEchoReply struct {
	Address       uint32
	Status        uint32
	RoundTripTime uint32
	DataSize      uint16
	Reserved      uint16
	Data          uintptr
	Options       ipOptionInformation
}

const (
	ipSuccess             = 0
	ipBufTooSmall         = 11001
	ipDestNetUnreachable  = 11002
	ipDestHostUnreachable = 11003
	ipDestProtUnreachable = 11004
	ipDestPortUnreachable = 11005
	ipHwError             = 11008
	ipReqTimedOut         = 11010
	ipTTLExpiredTransit   = 11013
	ipGeneralFailure      = 11050
	errorInvalidParameter = 87
	errorNotEnoughMemory  = 8
)

type winPinger struct {
	mu     sync.Mutex
	handle uintptr
}

func newPinger() (Pinger, error) {
	h, _, err := procIcmpCreate.Call()
	if h == 0 || h == ^uintptr(0) {
		return nil, err
	}
	return &winPinger{handle: h}, nil
}

func statusMessage(code uint32) (string, string) {
	switch code {
	case ipReqTimedOut:
		return "timeout", "Richiesta scaduta."
	case ipDestHostUnreachable:
		return "unreachable", "Host di destinazione non raggiungibile."
	case ipDestNetUnreachable:
		return "unreachable", "Rete di destinazione non raggiungibile."
	case ipDestProtUnreachable, ipDestPortUnreachable:
		return "unreachable", "Destinazione non raggiungibile."
	case ipTTLExpiredTransit:
		return "unreachable", "TTL scaduto durante il transito."
	case ipHwError:
		return "error", "Errore hardware."
	case ipGeneralFailure:
		return "error", "Errore generale."
	}
	return "error", "Errore di trasmissione."
}

func (p *winPinger) Ping(dst net.IP, seq int, timeoutMs, size, ttl int) EchoResult {
	p.mu.Lock()
	defer p.mu.Unlock()
	ip4 := dst.To4()
	if ip4 == nil {
		return EchoResult{Status: "error", Message: "Indirizzo non IPv4."}
	}
	addr := uint32(ip4[0]) | uint32(ip4[1])<<8 | uint32(ip4[2])<<16 | uint32(ip4[3])<<24
	data := windowsPayload(size)
	var dataPtr uintptr
	if len(data) > 0 {
		dataPtr = uintptr(unsafe.Pointer(&data[0]))
	}
	opts := ipOptionInformation{Ttl: uint8(ttl)}
	replySize := int(unsafe.Sizeof(icmpEchoReply{})) + size + 8 + 64
	reply := make([]byte, replySize)
	start := time.Now()
	n, _, callErr := procIcmpSendEcho.Call(p.handle, uintptr(addr), dataPtr, uintptr(len(data)),
		uintptr(unsafe.Pointer(&opts)), uintptr(unsafe.Pointer(&reply[0])), uintptr(replySize), uintptr(timeoutMs))
	elapsed := float64(time.Since(start).Microseconds()) / 1000.0
	if n == 0 {
		code := uint32(ipReqTimedOut)
		if errno, ok := callErr.(syscall.Errno); ok && errno != 0 {
			code = uint32(errno)
		}
		st, msg := statusMessage(code)
		return EchoResult{Status: st, Message: msg}
	}
	r := (*icmpEchoReply)(unsafe.Pointer(&reply[0]))
	if r.Status != ipSuccess {
		st, msg := statusMessage(r.Status)
		return EchoResult{Status: st, Message: msg}
	}
	rtt := elapsed
	// La misura con l'orologio ad alta risoluzione non può essere inferiore a quella di Windows meno 1 ms.
	if float64(r.RoundTripTime) > rtt+1 {
		rtt = float64(r.RoundTripTime)
	}
	return EchoResult{OK: true, Status: "ok", RTT: rtt, WinRTT: int(r.RoundTripTime), TTL: int(r.Options.Ttl), Bytes: int(r.DataSize)}
}

func (p *winPinger) Close() {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.handle != 0 {
		procIcmpClose.Call(p.handle)
		p.handle = 0
	}
}
