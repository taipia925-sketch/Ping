package it.pingpro.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import it.pingpro.app.data.AppSettings
import it.pingpro.app.data.SavedDevice
import it.pingpro.app.data.TestSummary
import it.pingpro.app.ping.LinkStatus
import it.pingpro.app.ping.NetworkChoice
import it.pingpro.app.ping.PingConfig
import it.pingpro.app.ping.PingMode
import it.pingpro.app.ping.PingSample
import it.pingpro.app.ping.ReplyStatus
import it.pingpro.app.ping.SessionState
import it.pingpro.app.ping.SessionTracker
import it.pingpro.app.ping.NetworkResolver
import it.pingpro.app.ui.TestUi
import it.pingpro.app.ui.screens.DevicesScreen
import it.pingpro.app.ui.screens.GuideScreen
import it.pingpro.app.ui.screens.HistoryScreen
import it.pingpro.app.ui.screens.MultiScreen
import it.pingpro.app.ui.screens.MultiEditScreen
import it.pingpro.app.ping.MultiState
import it.pingpro.app.ping.TargetState
import it.pingpro.app.data.MultiTarget
import it.pingpro.app.data.TargetGroup
import it.pingpro.app.ui.screens.QuizScreen
import it.pingpro.app.ui.screens.DiagnosisScreen
import it.pingpro.app.ui.screens.LabContent
import it.pingpro.app.ui.screens.LabScenario
import it.pingpro.app.ui.components.LocalTour
import it.pingpro.app.ui.components.TourController
import it.pingpro.app.ui.components.TourOverlay
import androidx.compose.runtime.CompositionLocalProvider
import it.pingpro.app.ui.screens.NetworkScreen
import it.pingpro.app.ping.NetInterfaceInfo
import it.pingpro.app.ui.screens.OnboardingScreen
import it.pingpro.app.ui.screens.SettingsScreen
import it.pingpro.app.ui.screens.TestScreen
import it.pingpro.app.ui.theme.Palette
import it.pingpro.app.ui.theme.PingProTheme
import org.junit.Rule
import org.junit.Test
import kotlin.math.sin

class FakeUi(t: String, m: PingMode, private val devices: List<SavedDevice>) : TestUi {
    override var target by mutableStateOf(t)
    override var mode by mutableStateOf(m)
    override val targetValid get() = NetworkResolver.isValidTarget(target)
    override fun buildConfig() = PingConfig(target, mode)
    override fun start() {}
    override fun stop() {}
    override fun deviceFor(address: String) = devices.firstOrNull { it.address == address }
}

class ScreensSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6_PRO.copy(screenHeight = 3120, nightMode = NightMode.NIGHT, softButtons = false),
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 1.0,
    )

    private val devices = listOf(
        SavedDevice("1", "Quadro linea 3", "192.168.1.100", "Armadio elettrico reparto B", lastUsedAt = 3),
        SavedDevice("2", "Inverter A", "192.168.1.120", "", lastUsedAt = 2),
        SavedDevice("3", "PLC ingresso", "192.168.1.15", "Sopra la porta carraia"),
    )
    private val wifi = NetInterfaceInfo("Wi-Fi", "wlan0", false, false, "192.168.1.23", 24, "192.168.1.1",
        ipv6 = listOf("fe80::1c2b:3aff:fe4d:5e6f"), dns = listOf("192.168.1.1"), dhcpServer = "192.168.1.1",
        wifiRssi = -58, linkSpeedMbps = 866, frequencyMHz = 5180, mtu = 1500)
    private val cell = NetInterfaceInfo("Dati mobili", "rmnet_data0", true, true, "10.142.33.7", 30, null)
    private val net = NetworkChoice(null, "Wi-Fi", "wlan0", "192.168.1.23", 24, true)

    private fun session(n: Int, lossAt: Set<Int>, status: LinkStatus? = null, running: Boolean = true, mode: PingMode = PingMode.CONTINUOUS): SessionState {
        val tr = SessionTracker()
        val start = System.currentTimeMillis() - n * 1000L
        for (i in 1..n) {
            val t = start + i * 1000L
            val s = if (i in lossAt) PingSample(i, t, ReplyStatus.TIMEOUT, message = "Richiesta scaduta.")
            else PingSample(i, t, ReplyStatus.OK, 2.2 + 1.4 * sin(i / 3.0) + (if (i % 17 == 0) 9.0 else 0.0) + (i % 5) * 0.3, 64, 32)
            tr.add(s)
        }
        return SessionState(
            running = running, config = PingConfig("192.168.1.100", mode), deviceName = "Quadro linea 3", resolvedIp = "192.168.1.100",
            network = net, engine = "Socket ICMP nativo", samples = tr.samples.toList(), events = tr.events.toList(), stats = tr.stats,
            status = status ?: tr.status, consecutiveLost = tr.consecutiveLost, outages = tr.outages, startedAt = start,
            endedAt = if (running) start + n * 1000L else start + n * 1000L, lastRecordId = if (running) null else "x",
        )
    }

    private fun shot(content: @Composable () -> Unit) = paparazzi.snapshot {
        PingProTheme { Box(Modifier.fillMaxSize().background(Palette.Bg)) { content() } }
    }

    /** Rende una schermata lunga in più "pagine" spostando il contenuto verso l'alto. */
    private fun pages(n: Int, content: @Composable () -> Unit) {
        for (i in 0 until n) paparazzi.snapshot("p$i") {
            PingProTheme {
                Box(Modifier.fillMaxSize().background(Palette.Bg).clipToBounds()) {
                    Box(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).requiredHeight(4000.dp).offset(y = (-820 * i).dp)) { content() }
                }
            }
        }
    }

    private fun test(s: SessionState, mode: PingMode = PingMode.CONTINUOUS, history: List<TestSummary> = emptyList()) = pages(4) {
        TestScreen(FakeUi("192.168.1.100", mode, devices), s, devices, history, AppSettings(), {}, {}, {}, {}, lan = wifi)
    }

    @Test fun test_idle() = test(SessionState())
    @Test fun test_running_online() = test(session(48, setOf(21, 22, 23)))
    @Test fun test_down() = test(session(30, setOf(26, 27, 28, 29, 30)))
    @Test fun test_finished_single() = test(session(4, emptySet(), LinkStatus.FINISHED, running = false, mode = PingMode.SINGLE), PingMode.SINGLE)

    private val hist = (0 until 6).map { i ->
        TestSummary("h$i", listOf("192.168.1.100", "192.168.1.120", "192.168.1.15")[i % 3], "192.168.1.100",
            listOf("Quadro linea 3", "Inverter A", null)[i % 3], if (i % 2 == 0) PingMode.SINGLE else PingMode.CONTINUOUS,
            4, 32, 4000, 128, 1_700_000_000_000L - i * 7_200_000L, 1_700_000_000_000L - i * 7_200_000L + 4000L * (i + 1),
            4 * (i + 1), if (i == 4) 0 else 4 * (i + 1) - (i % 3), 0.8, 2.4 + i, 9.0, 1.1, 0, "Wi-Fi", "Socket ICMP nativo")
    }

    @Test fun network() = pages(2) { NetworkScreen(listOf(wifi, cell), false, {}, {}, {}, {}) }
    @Test fun network_none() = shot { NetworkScreen(listOf(cell), false, {}, {}, {}, {}) }

    @Test fun guide_tools() = pages(2) { GuideScreen({}, {}) }
    @Test fun quiz() = shot { QuizScreen {} }
    @Test fun diagnosis() = pages(2) { DiagnosisScreen(wifi, SessionState(), "192.168.2.50", { true }, {}, {}, {}, {}) }
    @Test fun lab() = pages(2) {
        val st = session(40, setOf(30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40))
        LabContent(st, LabScenario.DROPOUTS, true, false, AppSettings(), {}, {}, {}, {}, {})
    }
    @Test fun tour() = shot {
        val tour = TourController()
        CompositionLocalProvider(LocalTour provides tour) {
            Box(Modifier.fillMaxSize()) {
                TestScreen(FakeUi("192.168.1.100", PingMode.SINGLE, devices), SessionState(), devices, emptyList(), AppSettings(), {}, {}, {}, {}, lan = wifi)
                tour.bounds["mode"] = androidx.compose.ui.geometry.Rect(64f, 1130f, 1376f, 1290f)
                tour.steps = listOf(it.pingpro.app.ui.testScreenTour[0], it.pingpro.app.ui.testScreenTour[5], it.pingpro.app.ui.testScreenTour[6])
                tour.index = 1; tour.active = true
                TourOverlay(tour)
            }
        }
    }

    private fun multiState(running: Boolean): MultiState {
        val names = listOf("Alimentatore", "CPU principale", "I/O rack 1", "I/O rack 2", "Inverter A", "Inverter B", "HMI", "Modulo misure")
        val t = names.mapIndexed { i, n ->
            val tr = SessionTracker()
            for (k in 1..30) {
                val lost = (i == 4 && k > 26) || (i == 2 && k == 12)
                tr.add(if (lost) PingSample(k, k * 1000L, ReplyStatus.TIMEOUT) else PingSample(k, k * 1000L, ReplyStatus.OK, 1.8 + i * 0.7 + (k % 4) * 0.4))
            }
            TargetState(MultiTarget("192.168.1.${100 + i}", n), n, "192.168.1.${100 + i}", tr.status, tr.stats, tr.samples.takeLast(60),
                tr.consecutiveLost, tr.outages, tr.events, net, finished = !running)
        }
        return MultiState(running, PingMode.CONTINUOUS, 4, "Quadro linea 3", t, System.currentTimeMillis() - 30_000, if (running) null else System.currentTimeMillis(), "b")
    }
    private val multiTargets = (0 until 8).map { MultiTarget("192.168.1.${100 + it}", listOf("Alimentatore", "CPU principale", "I/O rack 1", "I/O rack 2", "Inverter A", "Inverter B", "HMI", "Modulo misure")[it]) }
    private val groupsList = listOf(TargetGroup("g1", "Quadro linea 3", multiTargets), TargetGroup("g2", "Linea 4", multiTargets.take(5)))

    @Test fun multi_running() = pages(3) {
        MultiScreen(multiState(true), multiTargets, "Quadro linea 3", groupsList, PingMode.CONTINUOUS, AppSettings(), false,
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
    @Test fun multi_idle() = shot {
        MultiScreen(MultiState(), multiTargets, "Quadro linea 3", groupsList, PingMode.CONTINUOUS, AppSettings(), false,
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
    @Test fun multi_edit() = pages(2) {
        MultiEditScreen(multiTargets.take(3), "Quadro linea 3", devices, groupsList, wifi, { _, _ -> }, {}, {}, {}, {}, {})
    }
    @Test fun history_batch() = shot {
        val batch = (0 until 4).map { i ->
            TestSummary("b$i", "192.168.1.${100 + i}", "192.168.1.${100 + i}", listOf("Alimentatore", "CPU", "I/O rack", "Inverter A")[i], PingMode.CONTINUOUS,
                4, 32, 4000, 128, 1_700_000_000_000L, 1_700_000_300_000L, 300, if (i == 3) 0 else 300 - i, 0.9, 2.0 + i, 8.0, 1.0, 0, "Wi-Fi", "Più schede",
                batchId = "B", batchName = "Quadro linea 3")
        }
        HistoryScreen(batch + hist, {}, { _, _ -> }, {})
    }

    @Test fun test_running_dark() {
        it.pingpro.app.ui.theme.Palette.dark = true
        try { shot { TestScreen(FakeUi("192.168.1.100", PingMode.CONTINUOUS, devices), session(48, setOf(21, 22, 23)), devices, emptyList(), AppSettings(), {}, {}, {}, {}, lan = wifi) } }
        finally { it.pingpro.app.ui.theme.Palette.dark = false }
    }

    @Test fun multi_edit_full() = shot {
        MultiEditScreen((1..100).map { MultiTarget("192.168.1.$it") }, null, devices, groupsList, wifi, { _, _ -> }, {}, {}, {}, {}, {})
    }

    @Test fun history() = shot { HistoryScreen(hist, {}, { _, _ -> }, {}) }
    @Test fun devices() = shot { DevicesScreen(devices, hist, false, {}, {}, {}) }
    @Test fun guide() = pages(4) { GuideScreen({}, {}) }
    @Test fun onboarding() = shot { OnboardingScreen(onDone = {}) }
    @Test fun settings() = shot { SettingsScreen(AppSettings(), {}, {}, {}) }
}
