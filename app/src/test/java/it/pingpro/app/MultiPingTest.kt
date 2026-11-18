package it.pingpro.app

import it.pingpro.app.data.Export
import it.pingpro.app.data.MultiTarget
import it.pingpro.app.ping.LinkStatus
import it.pingpro.app.ping.MultiState
import it.pingpro.app.ping.PingMode
import it.pingpro.app.ping.PingSample
import it.pingpro.app.ping.PingStats
import it.pingpro.app.ping.ReplyStatus
import it.pingpro.app.ping.TargetParser
import it.pingpro.app.ping.TargetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiPingTest {
    @Test fun parsesListsAndRanges() {
        val r = TargetParser.parse("192.168.1.100\n192.168.1.105, 192.168.1.110-112; 10.0.0.1-10.0.0.3 scheda01.azienda.it")
        assertEquals(listOf("192.168.1.100", "192.168.1.105", "192.168.1.110", "192.168.1.111", "192.168.1.112",
            "10.0.0.1", "10.0.0.2", "10.0.0.3", "scheda01.azienda.it"), r.addresses)
        assertTrue(r.invalid.isEmpty())
        val bad = TargetParser.parse("192.168.1.300 192.168.1.20-10 ciao!")
        assertEquals(3, bad.invalid.size)
        assertTrue(bad.addresses.isEmpty())
        assertEquals(listOf("192.168.1.1"), TargetParser.parse("192.168.1.1 192.168.1.1").addresses) // duplicati
    }

    @Test fun addPreviewMessages() {
        val full = (1..100).map { "192.168.1.$it" }
        // Caso segnalato dall'utente: elenco pieno e indirizzo già presente.
        val dup = TargetParser.previewAdd(listOf("192.168.1.1"), full)
        assertEquals("192.168.1.1 è già nell'elenco.", dup.message); assertTrue(!dup.canAdd)
        val noRoom = TargetParser.previewAdd(listOf("192.168.1.200"), full)
        assertTrue(noRoom.message.startsWith("L'elenco è pieno")); assertTrue(!noRoom.canAdd)
        val ok = TargetParser.previewAdd(listOf("10.0.0.1", "10.0.0.2", "192.168.1.5"), listOf("192.168.1.5"))
        assertEquals("2 indirizzi da aggiungere · 1 indirizzo già nell'elenco.", ok.message); assertTrue(ok.canAdd)
        val one = TargetParser.previewAdd(listOf("10.0.0.1"), emptyList())
        assertEquals("1 indirizzo da aggiungere.", one.message)
        val partial = TargetParser.previewAdd((1..10).map { "10.0.0.$it" }, (1..95).map { "192.168.1.$it" })
        assertTrue(partial.message.contains("posto solo per altri 5")); assertTrue(partial.canAdd)
    }

    @Test fun rangesAndLimit() {
        assertEquals(21L, TargetParser.rangeSize("192.168.1.100", "192.168.1.120"))
        assertNull(TargetParser.rangeSize("192.168.1.120", "192.168.1.100"))
        assertEquals(listOf("192.168.1.254", "192.168.1.255", "192.168.2.0"), TargetParser.expandRange("192.168.1.254", "192.168.2.0"))
        val big = TargetParser.parse("10.0.0.1-10.0.1.255")
        assertEquals(TargetParser.MAX_TARGETS, big.addresses.size)
        assertTrue(big.truncated)
        val exact = TargetParser.parse("10.0.0.1-100")
        assertEquals(100, exact.addresses.size)
        assertTrue(!exact.truncated)
    }

    private fun ts(addr: String, name: String?, rtts: List<Double?>, finished: Boolean, status: LinkStatus = LinkStatus.ONLINE): TargetState {
        val samples = rtts.mapIndexed { i, r -> if (r == null) PingSample(i + 1, 0, ReplyStatus.TIMEOUT) else PingSample(i + 1, 0, ReplyStatus.OK, r) }
        return TargetState(MultiTarget(addr, name), name, addr, status, PingStats.of(samples), samples, finished = finished)
    }

    @Test fun healthAndCounts() {
        val m = MultiState(
            running = false, mode = PingMode.SINGLE, count = 4, groupName = "Quadro linea 3",
            targets = listOf(
                ts("192.168.1.100", "Scheda A", listOf(1.0, 1.2, 1.1, 1.3), true),
                ts("192.168.1.101", null, listOf(1.0, null, 1.1, 1.0), true),
                ts("192.168.1.102", "Scheda C", listOf(null, null, null, null), true),
                TargetState(MultiTarget("192.168.1.103"), null),
            ),
            startedAt = 0, endedAt = 4000,
        )
        assertEquals(1, m.okCount); assertEquals(1, m.warnCount); assertEquals(1, m.downCount); assertEquals(1, m.waitingCount)
        // Durante il test lo stato segue il tracker (rosso solo da 2 persi consecutivi).
        assertEquals(TargetState.Health.DOWN, ts("x", null, listOf(1.0, null, null), false, LinkStatus.DOWN).health)
        assertEquals(TargetState.Health.WARN, ts("x", null, listOf(1.0, null), false, LinkStatus.UNSTABLE).health)

        val report = Export.multiReport(m)
        println(report)
        assertTrue(report.contains("Rispondono: 1 · Con perdite: 1 · Non rispondono: 1"))
        assertTrue(report.contains("NON RISPONDE Scheda C (192.168.1.102)"))
        assertTrue(report.contains("Trasmessi 4, Ricevuti 3, Persi 1 (25%)"))
        assertEquals(5, Export.multiCsv(m).trim().lines().size)
    }
}
