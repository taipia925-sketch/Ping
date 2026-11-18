package it.pingpro.app

import it.pingpro.app.ping.IpConfig
import it.pingpro.app.ping.NetInterfaceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpConfigTest {
    @Test fun masks() {
        assertEquals("255.255.255.0", IpConfig.prefixToMask(24))
        assertEquals("255.255.0.0", IpConfig.prefixToMask(16))
        assertEquals("255.255.255.252", IpConfig.prefixToMask(30))
        assertEquals("255.255.254.0", IpConfig.prefixToMask(23))
    }

    @Test fun ranges() {
        assertEquals("192.168.1.0", IpConfig.networkAddress("192.168.1.23", 24))
        assertEquals("192.168.1.1" to "192.168.1.254", IpConfig.hostRange("192.168.1.23", 24))
        assertEquals("10.0.0.1" to "10.0.1.254", IpConfig.hostRange("10.0.1.7", 23))
        assertTrue(IpConfig.sameSubnet("192.168.1.23", "192.168.1.100", 24))
        assertFalse(IpConfig.sameSubnet("192.168.1.23", "192.168.2.100", 24))
    }

    @Test fun windowsIpconfigText() {
        val i = NetInterfaceInfo("Wi-Fi", "wlan0", true, false, "192.168.1.23", 24, "192.168.1.1",
            ipv6 = listOf("fe80::1c2b:3aff:fe4d:5e6f"), dns = listOf("192.168.1.1"), dhcpServer = "192.168.1.1")
        val t = IpConfig.windowsText(listOf(i))
        println(t)
        assertTrue(t.contains("Scheda LAN wireless Wi-Fi:"))
        assertTrue(t.contains("   Indirizzo IPv4. . . . . . . . . . . . : 192.168.1.23"))
        assertTrue(t.contains("   Subnet mask . . . . . . . . . . . . . : 255.255.255.0"))
        assertTrue(t.contains("   Gateway predefinito . . . . . . . . . : 192.168.1.1"))
        assertTrue(IpConfig.windowsText(listOf(i), all = true).contains("Server DHCP"))
        // Caso reale: rete mobile con IPv6 pubblico → "Indirizzo IPv6", non "locale rispetto al collegamento".
        val cell = NetInterfaceInfo("Dati mobili", "rmnet", false, true, null, null, "fe80::b", ipv6 = listOf("2001:b04:a8:e412::364:4772"))
        val tc = IpConfig.windowsText(listOf(cell))
        assertTrue(tc.contains("   Indirizzo IPv6. . . . . . . . . . . . : 2001:b04:a8:e412::364:4772"))
        assertTrue(!tc.contains("locale rispetto al collegamento . : 2001"))
    }
}
