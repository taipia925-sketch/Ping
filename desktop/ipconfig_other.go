//go:build !windows

package main

import (
	"bufio"
	"encoding/hex"
	"net"
	"os"
	"strings"
)

// Versione per Linux/macOS (sviluppo): interfacce di sistema e gateway da /proc/net/route.
func readAdapters() []Adapter {
	gateways := map[string]string{}
	if f, err := os.Open("/proc/net/route"); err == nil {
		sc := bufio.NewScanner(f)
		for sc.Scan() {
			fs := strings.Fields(sc.Text())
			if len(fs) > 2 && fs[1] == "00000000" {
				if b, err := hex.DecodeString(fs[2]); err == nil && len(b) == 4 {
					gw := net.IPv4(b[3], b[2], b[1], b[0])
					gateways[fs[0]] = gw.String()
				}
			}
		}
		f.Close()
	}
	ifs, _ := net.Interfaces()
	var out []Adapter
	for _, itf := range ifs {
		if itf.Flags&net.FlagLoopback != 0 {
			continue
		}
		a := Adapter{Name: itf.Name, Description: itf.Name, Type: "Ethernet", Up: itf.Flags&net.FlagUp != 0, MAC: strings.ToUpper(strings.ReplaceAll(itf.HardwareAddr.String(), ":", "-"))}
		if strings.HasPrefix(itf.Name, "wl") {
			a.Type = "Wi-Fi"
		}
		addrs, _ := itf.Addrs()
		for _, ad := range addrs {
			if ipn, ok := ad.(*net.IPNet); ok {
				if v4 := ipn.IP.To4(); v4 != nil && a.IPv4 == "" {
					a.IPv4 = v4.String()
					a.Prefix, _ = ipn.Mask.Size()
				} else if ipn.IP.To4() == nil {
					a.IPv6 = append(a.IPv6, ipn.IP.String())
				}
			}
		}
		a.Gateway = gateways[itf.Name]
		a.fill()
		out = append(out, a)
	}
	sortAdapters(out)
	return out
}
