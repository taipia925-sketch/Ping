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
import it.pingpro.app.ping.LinkStatus
import it.pingpro.app.ui.screens.CompareScreen
import it.pingpro.app.ui.screens.DeviceDraft
import it.pingpro.app.ui.screens.DeviceEditorDialog
import it.pingpro.app.ui.screens.DevicesScreen
import it.pingpro.app.ui.screens.GuideScreen
import it.pingpro.app.ui.screens.HistoryDetailScreen
import it.pingpro.app.ui.screens.HistoryScreen
import it.pingpro.app.ui.screens.OnboardingScreen
import it.pingpro.app.ui.screens.SettingsScreen
import it.pingpro.app.ui.screens.TestScreen
import it.pingpro.app.ui.theme.Palette

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("test", "Test", Icons.Outlined.NetworkPing),
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
    val context = LocalContext.current
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    var showTutorial by remember { mutableStateOf(!settings.onboardingDone) }
    var draft by remember { mutableStateOf<DeviceDraft?>(null) }
    var deleting by remember { mutableStateOf<it.pingpro.app.data.SavedDevice?>(null) }

    if (showTutorial) {
        OnboardingScreen(onDone = {
            vm.updateSettings { it.copy(onboardingDone = true) }
            showTutorial = false
        })
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

    Scaffold(
        containerColor = Palette.Bg,
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar(containerColor = Palette.Surface, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = currentRoute == t.route,
                            onClick = { goTab(t.route) },
                            icon = {
                                if (t.route == "test" && session.running && currentRoute != "test") {
                                    BadgedBox(badge = { Badge(containerColor = if (session.status == LinkStatus.DOWN) Palette.Red else Palette.Green) }) {
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
                    )
                }
                composable("devices") {
                    DevicesScreen(
                        devices = devices, history = history, running = session.running,
                        onPing = { d -> vm.target = d.address; startTest(); vm.start(); goTab("test") },
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
                    GuideScreen(onStartTutorial = { showTutorial = true }, onTryNow = { goTab("test") })
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

