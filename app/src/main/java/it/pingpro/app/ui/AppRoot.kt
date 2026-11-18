package it.pingpro.app.ui

import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import it.pingpro.app.data.Export
import it.pingpro.app.ping.LinkStatus
import it.pingpro.app.ping.PingMode
import it.pingpro.app.ui.screens.CompareScreen
import it.pingpro.app.ui.screens.DeviceDraft
import it.pingpro.app.ui.screens.DeviceEditorDialog
import it.pingpro.app.ui.screens.DevicesScreen
import it.pingpro.app.ui.screens.GuideScreen
import it.pingpro.app.ui.screens.HistoryDetailScreen
import it.pingpro.app.ui.screens.HistoryScreen
import it.pingpro.app.ui.screens.MultiScreen
import it.pingpro.app.ui.screens.MultiEditScreen
import it.pingpro.app.ui.screens.LabScreen
import it.pingpro.app.ui.screens.QuizScreen
import it.pingpro.app.ui.screens.DiagnosisScreen
import it.pingpro.app.ui.components.LocalTour
import it.pingpro.app.ui.components.TourController
import it.pingpro.app.ui.components.TourOverlay
import it.pingpro.app.ui.components.tourTarget
import it.pingpro.app.ui.screens.NetworkScreen
import it.pingpro.app.ui.screens.OnboardingScreen
import it.pingpro.app.ui.screens.SettingsScreen
import it.pingpro.app.ui.screens.TestScreen
import it.pingpro.app.ui.theme.Palette

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("test", "Test", Icons.Outlined.NetworkPing),
    Tab("network", "Rete", Icons.Outlined.Lan),
    Tab("devices", "Schede", Icons.Outlined.DeveloperBoard),
    Tab("history", "Storico", Icons.Outlined.History),
    Tab("guide", "Guida", Icons.AutoMirrored.Outlined.MenuBook),
)

@Composable
fun AppRoot(onFirstTestStarting: () -> Unit) {
    val vm: MainViewModel = viewModel()
    val session by vm.session.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val network by vm.network.collectAsStateWithLifecycle()
    val multi by vm.multi.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    var showTutorial by remember { mutableStateOf(!settings.onboardingDone) }
    var draft by remember { mutableStateOf<DeviceDraft?>(null) }
    var deleting by remember { mutableStateOf<it.pingpro.app.data.SavedDevice?>(null) }

    val tour = remember { TourController() }
    var pendingTour by remember { mutableStateOf(false) }

    if (showTutorial) {
        OnboardingScreen(
            onDone = {
                vm.updateSettings { it.copy(onboardingDone = true) }
                showTutorial = false
            },
            onTour = {
                vm.updateSettings { it.copy(onboardingDone = true) }
                showTutorial = false
                pendingTour = true
            },
        )
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    fun goTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val startTest: () -> Unit = { onFirstTestStarting() }

    // Il tour parte quando la schermata Test è visibile e i suoi elementi sono stati misurati.
    androidx.compose.runtime.LaunchedEffect(pendingTour, currentRoute) {
        if (pendingTour && currentRoute == "test") {
            kotlinx.coroutines.delay(450)
            tour.start(testScreenTour)
            pendingTour = false
        }
    }
    fun startTour() { pendingTour = true; goTab("test") }

    androidx.compose.runtime.CompositionLocalProvider(LocalTour provides tour) {
    Box(Modifier.fillMaxSize()) {

    Scaffold(
        containerColor = Palette.Bg,
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar(modifier = Modifier.tourTarget("nav"), containerColor = Palette.Surface, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = currentRoute == t.route,
                            onClick = { goTab(t.route) },
                            icon = {
                                if (t.route == "test" && (session.running || multi.running) && currentRoute != "test") {
                                    val bad = session.status == LinkStatus.DOWN || (multi.running && multi.downCount > 0)
                                    BadgedBox(badge = { Badge(containerColor = if (bad) Palette.Red else Palette.Green) }) {
                                        Icon(t.icon, t.label)
                                    }
                                } else Icon(t.icon, t.label)
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.AccentSoft, selectedTextColor = Palette.Text,
                                indicatorColor = Palette.Accent.copy(alpha = 0.16f),
                                unselectedIconColor = Palette.TextDim, unselectedTextColor = Palette.TextDim,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(Palette.Bg).padding(bottom = padding.calculateBottomPadding())) {
            NavHost(nav, startDestination = "test",
                enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) {
                composable("test") {
                    if (vm.multiMode) {
                        MultiScreen(
                            state = multi, targets = vm.multiTargets, listName = vm.multiName, groups = groups,
                            runMode = vm.multiRunMode, settings = settings, singleRunning = session.running,
                            onRunMode = { vm.multiRunMode = it },
                            onSwitchSingle = { vm.multiMode = false },
                            onEdit = { nav.navigate("multi/edit") },
                            onLoadGroup = { vm.loadGroup(it) },
                            onStart = { startTest(); vm.startMulti() },
                            onStop = { vm.stopMulti() },
                            onOpenSettings = { nav.navigate("settings") },
                            onPingSingle = { addr -> vm.multiMode = false; vm.target = addr },
                            onCopy = { Export.copy(context, "Risultati PING", Export.multiReport(multi)); toast("Riepilogo copiato negli appunti") },
                            onShare = { csv -> Export.shareMulti(context, multi, csv) },
                        )
                    } else {
                    TestScreen(
                        vm = vm, session = session, devices = devices, history = history, settings = settings,
                        onOpenSettings = { nav.navigate("settings") },
                        onOpenRecord = { nav.navigate("record/$it") },
                        onSaveDevice = { addr ->
                            val existing = vm.deviceFor(addr)
                            draft = DeviceDraft(existing?.id, existing?.name ?: "", addr, existing?.note ?: "")
                        },
                        toast = toast,
                        onStartRequested = startTest,
                        lan = network.firstOrNull { it.isLan && it.ipv4 != null },
                        onOpenNetwork = { goTab("network") },
                        onSwitchMulti = { vm.multiMode = true },
                        multiRunning = multi.running,
                    )
                    }
                }
                composable("network") {
                    // Aggiorna periodicamente (segnale Wi-Fi) mentre la schermata è visibile.
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        while (true) { vm.refreshNetwork(); kotlinx.coroutines.delay(3000) }
                    }
                    NetworkScreen(
                        interfaces = network, running = session.running,
                        onPing = { addr -> if (!vm.anyRunning) vm.multiMode = false; startTest(); vm.pingNow(addr); goTab("test") },
                        onUse = { addr -> if (!vm.anyRunning) { vm.multiMode = false; vm.target = addr }; goTab("test") },
                        onRefresh = { vm.refreshNetwork(); toast("Configurazione aggiornata") },
                        onCopy = { txt -> Export.copy(context, "ipconfig", txt); toast("Configurazione copiata negli appunti") },
                    )
                }
                composable("devices") {
                    DevicesScreen(
                        devices = devices, history = history, running = session.running,
                        onPing = { d -> vm.multiMode = false; vm.target = d.address; startTest(); vm.start(); goTab("test") },
                        onEdit = { draft = it },
                        onDelete = { deleting = it },
                    )
                }
                composable("history") {
                    HistoryScreen(
                        items = history,
                        onOpen = { nav.navigate("record/$it") },
                        onCompare = { a, b -> nav.navigate("compare/$a/$b") },
                        onClear = { vm.clearHistory() },
                    )
                }
                composable("guide") {
                    GuideScreen(
                        onStartTutorial = { showTutorial = true },
                        onTryNow = { goTab("test") },
                        onStartTour = { startTour() },
                        onOpenLab = { nav.navigate("lab") },
                        onOpenQuiz = { nav.navigate("quiz") },
                        onOpenDiagnosis = { nav.navigate("diagnosis") },
                    )
                }
                composable("multi/edit") {
                    MultiEditScreen(
                        targets = vm.multiTargets, listName = vm.multiName, devices = devices, groups = groups,
                        lan = network.firstOrNull { it.isLan && it.ipv4 != null },
                        onChange = { list, name -> vm.setMultiList(list, name) },
                        onSaveGroup = { vm.saveGroup(it) },
                        onLoadGroup = { vm.loadGroup(it) },
                        onDeleteGroup = { vm.deleteGroup(it.id) },
                        onBack = { nav.popBackStack() },
                        toast = toast,
                    )
                }
                composable("lab") { LabScreen(onBack = { nav.popBackStack() }) }
                composable("quiz") { QuizScreen(onBack = { nav.popBackStack() }) }
                composable("diagnosis") {
                    DiagnosisScreen(
                        lan = network.firstOrNull { it.isLan && it.ipv4 != null },
                        session = session,
                        initialAddress = vm.target,
                        runCheck = { addr -> startTest(); vm.runCheck(addr) },
                        onRefreshNetwork = { vm.refreshNetwork() },
                        onContinuous = { addr ->
                            if (session.running) toast("C'è già un test in corso") else {
                                vm.target = addr; vm.mode = PingMode.CONTINUOUS
                                startTest(); vm.start(); goTab("test")
                            }
                        },
                        onBack = { nav.popBackStack() },
                        toast = toast,
                    )
                }
                composable("record/{id}") { e ->
                    HistoryDetailScreen(vm, e.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() },
                        onRepeat = { s ->
                            if (!session.running) { startTest(); vm.repeat(s); goTab("test") } else toast("Un test è già in corso")
                        }, toast = toast)
                }
                composable("compare/{a}/{b}") { e ->
                    CompareScreen(vm, e.arguments?.getString("a") ?: "", e.arguments?.getString("b") ?: "", onBack = { nav.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(settings, vm::updateSettings, onBack = { nav.popBackStack() }, onTutorial = { showTutorial = true })
                }
            }
        }
    }
    TourOverlay(tour)
    }
    }

    draft?.let { d ->
        DeviceEditorDialog(d, onDismiss = { draft = null }, onSave = { r ->
            vm.saveDevice(r.id, r.name, r.address, r.note)
            toast("Scheda \"${r.name.trim()}\" salvata")
            draft = null
        })
    }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Palette.Surface2,
            title = { Text("Eliminare \"${d.name}\"?") },
            text = { Text("La scheda verrà rimossa dall'elenco. I test già eseguiti restano nello storico.") },
            confirmButton = { TextButton(onClick = { vm.deleteDevice(d.id); deleting = null }) { Text("Elimina", color = Palette.Red) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annulla") } },
        )
    }
}

