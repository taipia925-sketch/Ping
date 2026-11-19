//go:build windows

package main

import (
	"fmt"
	"strings"
	"unsafe"

	"golang.org/x/sys/windows"
)

func readAdapters() []Adapter {
	size := uint32(16 * 1024)
	var buf []byte
	for i := 0; i < 4; i++ {
		buf = make([]byte, size)
		err := windows.GetAdaptersAddresses(windows.AF_UNSPEC,
			windows.GAA_FLAG_INCLUDE_GATEWAYS|windows.GAA_FLAG_INCLUDE_PREFIX,
			0, (*windows.IpAdapterAddresses)(unsafe.Pointer(&buf[0])), &size)
		if err == nil {
			break
		}
		if err != windows.ERROR_BUFFER_OVERFLOW {
			return nil
		}
	}
	var out []Adapter
	for aa := (*windows.IpAdapterAddresses)(unsafe.Pointer(&buf[0])); aa != nil; aa = aa.Next {
		if aa.IfType == windows.IF_TYPE_SOFTWARE_LOOPBACK || aa.IfType == windows.IF_TYPE_TUNNEL {
			continue
		}
		a := Adapter{
			Name:        windows.UTF16PtrToString(aa.FriendlyName),
			Description: windows.UTF16PtrToString(aa.Description),
			Up:          aa.OperStatus == windows.IfOperStatusUp,
			DnsSuffix:   windows.UTF16PtrToString(aa.DnsSuffix),
			SpeedMbps:   int64(aa.ReceiveLinkSpeed / 1_000_000),
		}
		switch aa.IfType {
		case windows.IF_TYPE_ETHERNET_CSMACD:
			a.Type = "Ethernet"
		case windows.IF_TYPE_IEEE80211:
			a.Type = "Wi-Fi"
		default:
			a.Type = "Altro"
		}
		if aa.PhysicalAddressLength > 0 {
			var parts []string
			for i := uint32(0); i < aa.PhysicalAddressLength && i < uint32(len(aa.PhysicalAddress)); i++ {
				parts = append(parts, fmt.Sprintf("%02X", aa.PhysicalAddress[i]))
			}
			a.MAC = strings.Join(parts, "-")
		}
		for u := aa.FirstUnicastAddress; u != nil; u = u.Next {
			ip := u.Address.IP()
			if ip == nil {
				continue
			}
			if v4 := ip.To4(); v4 != nil {
				if a.IPv4 == "" && !(v4[0] == 169 && v4[1] == 254) {
					a.IPv4 = v4.String()
					a.Prefix = int(u.OnLinkPrefixLength)
				}
			} else {
				a.IPv6 = append(a.IPv6, ip.String())
			}
		}
		for g := aa.FirstGatewayAddress; g != nil; g = g.Next {
			if ip := g.Address.IP(); ip != nil && ip.To4() != nil && !ip.IsUnspecified() {
				a.Gateway = ip.String()
				break
			}
		}
		for d := aa.FirstDnsServerAddress; d != nil; d = d.Next {
			if ip := d.Address.IP(); ip != nil {
				a.DNS = append(a.DNS, ip.String())
			}
		}
		if aa.Dhcpv4Server.Sockaddr != nil {
			if ip := aa.Dhcpv4Server.IP(); ip != nil && !ip.IsUnspecified() {
				a.DHCP = ip.String()
			}
		}
		a.fill()
		out = append(out, a)
	}
	sortAdapters(out)
	return out
}
