package it.pingpro.app

import it.pingpro.app.ping.DnsPtr
import it.pingpro.app.ping.FoundDevice
import it.pingpro.app.ping.NetInterfaceInfo
import it.pingpro.app.ping.NetworkScanner
import it.pingpro.app.ping.ScanState
import it.pingpro.app.ui.screens.scanReport
import it.pingpro.app.ui.screens.title
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTest {
    @Test fun defaultRangeFromPhoneNetwork() {
        // Rete reale dell'utente: telefono 192.168.1.104/24, gateway .254
        val lan = NetInterfaceInfo("Wi-Fi", "wlan0", true, true, "192.168.1.104", 24, "192.168.1.254")
        assertEquals("192.168.1.1" to "192.168.1.254", NetworkScanner.defaultRange(lan))
        assertEquals("10.0.0.1" to "10.0.3.254", NetworkScanner.defaultRange(lan.copy(ipv4 = "10.0.1.7", prefixLength = 22)))
        // Reti molto grandi: si propone solo la /24 attorno al telefono.
        assertEquals("10.20.30.1" to "10.20.30.254", NetworkScanner.defaultRange(lan.copy(ipv4 = "10.20.30.40", prefixLength = 16)))
        assertNull(NetworkScanner.defaultRange(null))
        assertEquals(16, NetworkScanner.estimateSeconds(254))
    }

    @Test fun ptrQueryName() {
        assertEquals("1.1.168.192.in-addr.arpa", DnsPtr.queryName("192.168.1.1"))
        assertNull(DnsPtr.queryName("192.168.1"))
    }

    @Test fun parsesPtrAnswerWithCompression() {
        // Risposta DNS reale (semplificata): domanda 1.1.168.192.in-addr.arpa PTR, risposta "ripetitore.lan".
        val q = byteArrayOf(1, '1'.code.toByte(), 1, '1'.code.toByte(), 3, '1'.code.toByte(), '6'.code.toByte(), '8'.code.toByte(),
            3, '1'.code.toByte(), '9'.code.toByte(), '2'.code.toByte(), 7) + "in-addr".toByteArray() + byteArrayOf(4) + "arpa".toByteArray() + byteArrayOf(0)
        val header = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0)
        val question = q + byteArrayOf(0, 12, 0, 1)
        val rdata = byteArrayOf(10) + "ripetitore".toByteArray() + byteArrayOf(3) + "lan".toByteArray() + byteArrayOf(0)
        val answer = byteArrayOf(0xC0.toByte(), 12, 0, 12, 0, 1, 0, 0, 0, 60, 0, rdata.size.toByte()) + rdata
        assertEquals("ripetitore.lan", DnsPtr.parsePtr(header + question + answer))
        // Nessuna risposta
        assertNull(DnsPtr.parsePtr(byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x83.toByte(), 0, 1, 0, 0, 0, 0, 0, 0) + question))
    }

    @Test fun titlesAndReport() {
        val gw = FoundDevice("192.168.1.254", 3.7, 64, isGateway = true)
        val ap = FoundDevice("192.168.1.1", 3.9, 64, name = "ripetitore.lan")
        val me = FoundDevice("192.168.1.104", 0.1, 64, isPhone = true)
        assertEquals("Router / gateway", gw.title(null))
        assertEquals("ripetitore.lan", ap.title(null))
        assertEquals("Questo telefono", me.title(null))
        assertEquals("Quadro 3", ap.title("Quadro 3"))
        val rep = scanReport(ScanState(false, "192.168.1.1", "192.168.1.254", 254, 254, listOf(ap, me, gw), 0, 16000), { null })
        println(rep)
        assertTrue(rep.contains("3 dispositivi hanno risposto su 254 indirizzi"))
        assertTrue(rep.contains("192.168.1.254"))
        // «Pinga tutti»: tutti tranne il telefono; con una selezione, solo i selezionati
        assertEquals(listOf("192.168.1.1", "192.168.1.254"), it.pingpro.app.ui.screens.pingTargets(listOf(ap, me, gw), emptySet()).map { d -> d.address })
        assertEquals(listOf("192.168.1.254"), it.pingpro.app.ui.screens.pingTargets(listOf(ap, me, gw), setOf("192.168.1.254", "192.168.1.104")).map { d -> d.address })
        // «Copia»: solo indirizzi IP, uno per riga, in ordine numerico (non alfabetico: .104 dopo .1 e prima di .254)
        assertEquals("192.168.1.1\n192.168.1.104\n192.168.1.254", it.pingpro.app.ui.screens.ipList(listOf(gw, ap, me)))
        // e si incollano puliti nell'elenco di Più schede
        assertEquals(listOf("192.168.1.1", "192.168.1.104", "192.168.1.254"),
            it.pingpro.app.ping.TargetParser.parse(it.pingpro.app.ui.screens.ipList(listOf(gw, ap, me))).addresses)
    }
}
