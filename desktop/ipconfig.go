package main

import (
	"fmt"
	"net"
	"strings"
)

// Adapter è una scheda di rete del PC, come una sezione di ipconfig.
type Adapter struct {
	Name        string   `json:"name"`
	Description string   `json:"description"`
	Type        string   `json:"type"` // Ethernet | Wi-Fi | Altro
	Up          bool     `json:"up"`
	MAC         string   `json:"mac"`
	IPv4        string   `json:"ipv4"`
	Prefix      int      `json:"prefix"`
	Mask        string   `json:"mask"`
	Gateway     string   `json:"gateway"`
	DNS         []string `json:"dns"`
	DHCP        string   `json:"dhcp"`
	IPv6        []string `json:"ipv6"`
	DnsSuffix   string   `json:"dnsSuffix"`
	SpeedMbps   int64    `json:"speedMbps"`
	RangeFrom   string   `json:"rangeFrom"`
	RangeTo     string   `json:"rangeTo"`
}

func prefixToMask(p int) string {
	if p <= 0 || p > 32 {
		return ""
	}
	m := net.CIDRMask(p, 32)
	return net.IP(m).String()
}

func ipToU32(ip net.IP) uint32 {
	v := ip.To4()
	if v == nil {
		return 0
	}
	return uint32(v[0])<<24 | uint32(v[1])<<16 | uint32(v[2])<<8 | uint32(v[3])
}

func u32ToIP(v uint32) string {
	return fmt.Sprintf("%d.%d.%d.%d", v>>24&255, v>>16&255, v>>8&255, v&255)
}

// hostRange: primo e ultimo indirizzo utilizzabile della sottorete.
func hostRange(ip string, prefix int) (string, string) {
	p := net.ParseIP(ip)
	if p == nil || prefix <= 0 || prefix > 32 {
		return "", ""
	}
	v := ipToU32(p)
	mask := uint32(0xFFFFFFFF) << (32 - uint(prefix))
	netw := v & mask
	bcast := netw | ^mask
	if prefix >= 31 {
		return u32ToIP(v), u32ToIP(v)
	}
	return u32ToIP(netw + 1), u32ToIP(bcast - 1)
}

func sameSubnet(a, b string, prefix int) bool {
	pa, pb := net.ParseIP(a), net.ParseIP(b)
	if pa == nil || pb == nil || prefix <= 0 {
		return false
	}
	mask := uint32(0xFFFFFFFF) << (32 - uint(prefix))
	return ipToU32(pa)&mask == ipToU32(pb)&mask
}

func (a *Adapter) fill() {
	a.Mask = prefixToMask(a.Prefix)
	if a.IPv4 != "" && a.Prefix > 0 {
		from, to := hostRange(a.IPv4, a.Prefix)
		// Reti molto grandi: si propone solo la /24 attorno al PC.
		if a.Prefix < 22 {
			base := a.IPv4[:strings.LastIndex(a.IPv4, ".")]
			from, to = base+".1", base+".254"
		}
		a.RangeFrom, a.RangeTo = from, to
	}
}

// localNetworkFor restituisce la scheda del PC che contiene l'indirizzo (stessa sottorete).
func localNetworkFor(target string, adapters []Adapter) *Adapter {
	for i := range adapters {
		a := &adapters[i]
		if a.Up && a.IPv4 != "" && sameSubnet(a.IPv4, target, a.Prefix) {
			return a
		}
	}
	return nil
}

func isLinkLocalV6(s string) bool {
	l := strings.ToLower(s)
	return strings.HasPrefix(l, "fe8") || strings.HasPrefix(l, "fe9") || strings.HasPrefix(l, "fea") || strings.HasPrefix(l, "feb")
}

// ipconfigText: testo nello stesso formato di ipconfig di Windows in italiano.
func ipconfigText(list []Adapter, all bool) string {
	var b strings.Builder
	b.WriteString("Configurazione IP di Windows\n\n")
	for _, a := range list {
		kind := "Ethernet"
		if a.Type == "Wi-Fi" {
			kind = "LAN wireless"
		} else if a.Type == "Altro" {
			kind = "sconosciuta"
		}
		fmt.Fprintf(&b, "Scheda %s %s:\n\n", kind, a.Name)
		if !a.Up {
			b.WriteString("   Stato supporto. . . . . . . . . . . . : Supporto disconnesso\n")
			fmt.Fprintf(&b, "   Suffisso DNS specifico per connessione: %s\n", a.DnsSuffix)
			if all {
				fmt.Fprintf(&b, "   Descrizione . . . . . . . . . . . . . : %s\n   Indirizzo fisico. . . . . . . . . . . : %s\n", a.Description, a.MAC)
			}
			b.WriteString("\n")
			continue
		}
		fmt.Fprintf(&b, "   Suffisso DNS specifico per connessione: %s\n", a.DnsSuffix)
		if all {
			fmt.Fprintf(&b, "   Descrizione . . . . . . . . . . . . . : %s\n   Indirizzo fisico. . . . . . . . . . . : %s\n", a.Description, a.MAC)
		}
		for _, v6 := range a.IPv6 {
			if isLinkLocalV6(v6) {
				fmt.Fprintf(&b, "   Indirizzo IPv6 locale rispetto al collegamento . : %s\n", v6)
			} else {
				fmt.Fprintf(&b, "   Indirizzo IPv6. . . . . . . . . . . . : %s\n", v6)
			}
		}
		if a.IPv4 != "" {
			fmt.Fprintf(&b, "   Indirizzo IPv4. . . . . . . . . . . . : %s\n", a.IPv4)
			fmt.Fprintf(&b, "   Subnet mask . . . . . . . . . . . . . : %s\n", a.Mask)
		}
		fmt.Fprintf(&b, "   Gateway predefinito . . . . . . . . . : %s\n", a.Gateway)
		if all {
			if a.DHCP != "" {
				fmt.Fprintf(&b, "   Server DHCP . . . . . . . . . . . . . : %s\n", a.DHCP)
			}
			for i, d := range a.DNS {
				if i == 0 {
					fmt.Fprintf(&b, "   Server DNS. . . . . . . . . . . . . . : %s\n", d)
				} else {
					fmt.Fprintf(&b, "                                           %s\n", d)
				}
			}
		}
		b.WriteString("\n")
	}
	return b.String()
}

// Prima le schede collegate con un gateway, poi le altre.
func sortAdapters(list []Adapter) {
	score := func(a Adapter) int {
		s := 0
		if a.Up {
			s += 4
		}
		if a.IPv4 != "" {
			s += 2
		}
		if a.Gateway != "" {
			s++
		}
		return s
	}
	for i := 1; i < len(list); i++ {
		for j := i; j > 0 && score(list[j]) > score(list[j-1]); j-- {
			list[j], list[j-1] = list[j-1], list[j]
		}
	}
}
