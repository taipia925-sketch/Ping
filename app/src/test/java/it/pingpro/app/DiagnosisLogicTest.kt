package it.pingpro.app

import it.pingpro.app.ping.LinkStatus
import it.pingpro.app.ping.NetInterfaceInfo
import it.pingpro.app.ping.PingSample
import it.pingpro.app.ping.ReplyStatus
import it.pingpro.app.ping.SessionTracker
import it.pingpro.app.ui.screens.CheckResult
import it.pingpro.app.ui.screens.LabScenario
import it.pingpro.app.ui.screens.Verdict
import it.pingpro.app.ui.screens.checkAddress
import it.pingpro.app.ui.screens.diagnosisConclusion
import it.pingpro.app.ui.screens.simulateRtt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiagnosisLogicTest {
    private val lan = NetInterfaceInfo("Wi-Fi", "wlan0", true, false, "192.168.1.23", 24, "192.168.1.1")

    @Test fun addressChecks() {
        assertEquals(Verdict.OK, checkAddress("192.168.1.100", lan).verdict)
        assertEquals(true, checkAddress("192.168.1.100", lan).sameNetwork)
        val other = checkAddress("192.168.2.50", lan)
        assertEquals(Verdict.WARN, other.verdict); assertEquals(false, other.sameNetwork)
        assertEquals(Verdict.FAIL, checkAddress("192.168.1.23", lan).verdict)   // indirizzo del telefono
        assertEquals(Verdict.FAIL, checkAddress("192.168.1.255", lan).verdict)  // broadcast
        assertEquals(Verdict.FAIL, checkAddress("192.168.1.0", lan).verdict)    // indirizzo di rete
        assertEquals(Verdict.FAIL, checkAddress("192.168.1.300", lan).verdict)
        assertEquals(Verdict.WARN, checkAddress("scheda.local", lan).verdict)
        assertEquals(Verdict.FAIL, checkAddress("10.0.0.5", lan.copy(gateway = null)).verdict)
        assertEquals(Verdict.WARN, checkAddress("192.168.1.100", null).verdict)
    }

    @Test fun conclusions() {
        val same = checkAddress("192.168.1.100", lan)
        val other = checkAddress("192.168.2.50", lan)
        val ok = CheckResult(Verdict.OK, 4, 4, 3.0)
        val fail = CheckResult(Verdict.FAIL, 4, 0)
        val warn = CheckResult(Verdict.WARN, 4, 3, 5.0)
        assertEquals(Verdict.OK, diagnosisConclusion(same, ok, ok).first)
        assertTrue(diagnosisConclusion(same, ok, fail).second.contains("Il telefono è collegato bene"))
        assertTrue(diagnosisConclusion(other, ok, fail).second.contains("non è nella stessa rete"))
        assertTrue(diagnosisConclusion(same, fail, fail).second.contains("collegamento del telefono"))
        assertTrue(diagnosisConclusion(same, ok, warn).second.contains("vicino alla scheda"))
        assertTrue(diagnosisConclusion(same, warn, warn).second.contains("Wi-Fi del telefono"))
        assertTrue(diagnosisConclusion(same, CheckResult(Verdict.SKIPPED), fail).second.contains("non c'è il risultato del gateway"))
        assertEquals(Verdict.TODO, diagnosisConclusion(same, ok, CheckResult(Verdict.TODO)).first)
    }

    /** Gli scenari del Laboratorio producono gli stati che la guida descrive. */
    @Test fun labScenariosMatchDescriptions() {
        fun run(s: LabScenario, n: Int = 60): SessionTracker {
            val t = SessionTracker(); val rnd = Random(1)
            for (i in 1..n) {
                val r = simulateRtt(s, i, rnd)
                t.add(if (r == null) PingSample(i, i * 1000L, ReplyStatus.TIMEOUT) else PingSample(i, i * 1000L, ReplyStatus.OK, r))
            }
            return t
        }
        assertEquals(0, run(LabScenario.NORMAL).stats.lost)
        assertEquals(LinkStatus.ONLINE, run(LabScenario.NORMAL).status)
        assertEquals(0, run(LabScenario.SPIKES).stats.lost)
        assertTrue(run(LabScenario.SPIKES).stats.maxMs!! > 30)
        assertTrue(run(LabScenario.DROPOUTS).outages >= 2)
        assertEquals(LinkStatus.DOWN, run(LabScenario.OFF, 5).status)
        val weak = run(LabScenario.WEAK_WIFI, 200)
        assertTrue(weak.stats.lost in 1..40)
        assertTrue(weak.stats.jitterMs!! > 10)
    }
}
