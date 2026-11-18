package it.pingpro.app

import it.pingpro.app.data.DetailCodec
import it.pingpro.app.data.Export
import it.pingpro.app.data.TestSummary
import it.pingpro.app.ping.EventKind
import it.pingpro.app.ping.IcmpPacket
import it.pingpro.app.ping.LinkStatus
import it.pingpro.app.ping.NetworkResolver
import it.pingpro.app.ping.PingConfig
import it.pingpro.app.ping.PingMode
import it.pingpro.app.ping.PingSample
import it.pingpro.app.ping.PingStats
import it.pingpro.app.ping.ReplyStatus
import it.pingpro.app.ping.SessionTracker
import it.pingpro.app.ping.SystemPingPinger
import it.pingpro.app.ui.screens.downsample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PingLogicTest {

    private fun ok(seq: Int, rtt: Double, t: Long = seq * 1000L) = PingSample(seq, t, ReplyStatus.OK, rtt, 64, 32)
    private fun lost(seq: Int, t: Long = seq * 1000L) = PingSample(seq, t, ReplyStatus.TIMEOUT, message = "Richiesta scaduta.")

    @Test fun windowsPayloadMatchesPingExe() {
        assertEquals("abcdefghijklmnopqrstuvwabcdefghi", String(IcmpPacket.windowsPayload(32)))
    }

    @Test fun echoRequestHeaderAndChecksum() {
        val p = IcmpPacket.buildEchoRequest(false, 0x1234, 7, IcmpPacket.windowsPayload(32))
        assertEquals(40, p.size)
        assertEquals(8, p[0].toInt())
        assertEquals(0, p[1].toInt())
        assertEquals(7, p[7].toInt())
        // Il checksum di un pacchetto corretto, ricalcolato, vale 0.
        assertEquals(0, IcmpPacket.checksum(p))
    }

    @Test fun parsesEchoReplyAsReturnedByPingSocket() {
        // Datagramma reale (header ICMP + payload) come restituito dal kernel su un socket ICMP "ping".
        val req = IcmpPacket.buildEchoRequest(false, 0x06f6, 3, IcmpPacket.windowsPayload(32))
        req[0] = 0 // Echo Reply
        val r = IcmpPacket.parse(req, req.size)!!
        assertTrue(IcmpPacket.isEchoReply(r, false))
        assertEquals(3, r.seq)
        assertEquals(32, r.payloadSize)
        assertNull(IcmpPacket.parse(ByteArray(4), 4))
    }

    @Test fun statsLikeWindows() {
        val st = PingStats.of(listOf(ok(1, 1.0), ok(2, 3.0), lost(3), ok(4, 2.0)))
        assertEquals(4, st.sent); assertEquals(3, st.received); assertEquals(1, st.lost)
        assertEquals(25.0, st.lossPercent, 1e-9)
        assertEquals(1.0, st.minMs!!, 1e-9); assertEquals(3.0, st.maxMs!!, 1e-9); assertEquals(2.0, st.avgMs!!, 1e-9)
        // Jitter: |3-1| e |2-3| -> media 1,5
        assertEquals(1.5, st.jitterMs!!, 1e-9)
        assertNull(PingStats().avgMs)
    }

    @Test fun trackerDetectsOutageAndRecovery() {
        val t = SessionTracker(highLatencyMs = 100)
        assertEquals(SessionTracker.Alert.NONE, t.add(ok(1, 1.0)))
        assertEquals(LinkStatus.ONLINE, t.status)
        assertEquals(SessionTracker.Alert.NONE, t.add(lost(2)))
        assertEquals(LinkStatus.UNSTABLE, t.status)
        assertEquals(SessionTracker.Alert.DOWN, t.add(lost(3)))
        assertEquals(LinkStatus.DOWN, t.status)
        assertEquals(SessionTracker.Alert.NONE, t.add(lost(4)))
        assertEquals(SessionTracker.Alert.RECOVERED, t.add(ok(5, 1.2)))
        assertEquals(LinkStatus.UNSTABLE, t.status) // perdite recenti
        assertEquals(1, t.outages)
        val kinds = t.events.map { it.kind }
        assertEquals(listOf(EventKind.LOSS, EventKind.OUTAGE_START, EventKind.OUTAGE_END), kinds)
        repeat(SessionTracker.RECENT_WINDOW) { t.add(ok(6 + it, 1.0)) }
        assertEquals(LinkStatus.ONLINE, t.status)
        t.add(ok(100, 250.0))
        assertEquals(LinkStatus.SLOW, t.status)
        assertTrue(t.events.any { it.kind == EventKind.SPIKE })
    }

    @Test fun singleLossIsNotOutage() {
        val t = SessionTracker()
        t.add(ok(1, 1.0)); t.add(lost(2)); t.add(ok(3, 1.0))
        assertEquals(0, t.outages)
        assertFalse(t.events.any { it.kind == EventKind.OUTAGE_START })
    }

    @Test fun windowsCommandEquivalent() {
        assertEquals("ping 192.168.1.100", PingConfig("192.168.1.100").windowsCommand())
        assertEquals("ping -t 192.168.1.100", PingConfig("192.168.1.100", PingMode.CONTINUOUS).windowsCommand())
        assertEquals("ping -n 10 -l 64 10.0.0.1", PingConfig("10.0.0.1", count = 10, payloadSize = 64).windowsCommand())
    }

    @Test fun targetValidation() {
        assertTrue(NetworkResolver.isValidTarget("192.168.1.100"))
        assertTrue(NetworkResolver.isValidTarget("scheda-01.local"))
        assertTrue(NetworkResolver.isValidTarget("fe80::1"))
        assertFalse(NetworkResolver.isValidTarget("192.168.1.300"))
        assertFalse(NetworkResolver.isValidTarget("192.168.1"))
        assertFalse(NetworkResolver.isValidTarget(""))
        assertFalse(NetworkResolver.isValidTarget("192 168"))
    }

    @Test fun parsesSystemPingOutput() {
        val out = """
            PING 192.168.1.1 (192.168.1.1) 32(60) bytes of data.
            40 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time=2.31 ms
        """.trimIndent()
        val r = SystemPingPinger.parseOutput(out)
        assertEquals(ReplyStatus.OK, r.status)
        assertEquals(2.31, r.rttMs!!, 1e-9)
        assertEquals(64, r.ttl)
        assertEquals(32, r.bytes)
        assertEquals(ReplyStatus.TIMEOUT, SystemPingPinger.parseOutput("1 packets transmitted, 0 received, 100% packet loss").status)
        assertEquals(ReplyStatus.UNREACHABLE, SystemPingPinger.parseOutput("From 192.168.1.5 icmp_seq=1 Destination Host Unreachable").status)
    }

    private fun summary(samples: List<PingSample>): TestSummary {
        val st = PingStats.of(samples)
        return TestSummary("id", "192.168.1.100", "192.168.1.100", "Quadro 3", PingMode.SINGLE, 4, 32, 4000, 128,
            0, 4000, st.sent, st.received, st.minMs, st.avgMs, st.maxMs, st.jitterMs, 0, "Wi-Fi · 192.168.1.20/24", "Socket ICMP nativo")
    }

    @Test fun windowsStyleReport() {
        val samples = listOf(ok(1, 0.42), ok(2, 3.6), lost(3), ok(4, 1.2))
        val txt = Export.windowsStyle(summary(samples), samples)
        assertTrue(txt.contains("Esecuzione di Ping 192.168.1.100 con 32 byte di dati:"))
        assertTrue(txt.contains("Risposta da 192.168.1.100: byte=32 durata<1ms TTL=64"))
        assertTrue(txt.contains("Risposta da 192.168.1.100: byte=32 durata=4ms TTL=64"))
        assertTrue(txt.contains("Richiesta scaduta."))
        assertTrue(txt.contains("Pacchetti: Trasmessi = 4, Ricevuti = 3,"))
        assertTrue(txt.contains("Persi = 1 (25% persi),"))
        println(txt)
        val csv = Export.csv(summary(samples), samples)
        assertEquals(5, csv.trim().lines().size)
    }

    @Test fun detailCodecRoundTrip() {
        val samples = listOf(ok(1, 1.5), lost(2))
        val (s, e) = DetailCodec.decode(DetailCodec.encode(samples, emptyList()))
        assertEquals(samples, s)
        assertTrue(e.isEmpty())
    }

    @Test fun downsampleKeepsLosses() {
        val samples = (1..10_000).map { if (it == 5_000) lost(it) else ok(it, 1.0) }
        val d = downsample(samples, 600)
        assertTrue(d.size <= 600)
        assertTrue(d.any { it.status.isLost })
    }
}
