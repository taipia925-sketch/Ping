package it.pingpro.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import it.pingpro.app.MainActivity
import it.pingpro.app.PingApp
import it.pingpro.app.R
import it.pingpro.app.ping.LinkStatus
import it.pingpro.app.ping.MultiState
import it.pingpro.app.ping.SessionState
import it.pingpro.app.ping.TargetState
import it.pingpro.app.ping.PingMode
import it.pingpro.app.ui.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Servizio in primo piano: mantiene attivo il test anche con lo schermo spento o con l'app
 * in background, mostrando lo stato in una notifica con il pulsante "Interrompi".
 */
class PingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var collector: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as PingApp
        val controller = app.controller
        val multi = app.multi
        if (intent?.action == ACTION_STOP) {
            controller.stop()
            multi.stop()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIF_ID, build("Avvio del test…", ""), type) }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PingPro:test").apply { setReferenceCounted(false); acquire(24 * 3600_000L) }
        }
        collector?.cancel()
        collector = scope.launch {
            controller.state.combine(multi.state) { a, b -> a to b }.collectLatest { (s, m) ->
                if (!s.running && !m.running) {
                    stopSelf()
                    return@collectLatest
                }
                val (title, text) = if (m.running) multiText(m) else singleText(s) ?: return@collectLatest
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(title, text))
            }
        }
        return START_NOT_STICKY
    }

    private fun singleText(s: SessionState): Pair<String, String>? {
        val cfg = s.config ?: return null
        val title = when (s.status) {
            LinkStatus.DOWN -> "⚠ ${s.deviceName ?: cfg.target} NON RISPONDE"
            LinkStatus.STARTING -> "Avvio test ${cfg.target}…"
            else -> "${s.deviceName ?: cfg.target} · " + (s.stats.lastMs?.let { Format.ms(it) + " ms" } ?: "pacchetto perso")
        }
        val st = s.stats
        val text = "Inviati ${st.sent} · Ricevuti ${st.received} · Persi ${st.lost} (${Format.percent(st.lossPercent)})" +
            (st.avgMs?.let { " · Media ${Format.ms(it)} ms" } ?: "") +
            if (cfg.mode == PingMode.CONTINUOUS) "" else " · ${st.sent}/${cfg.count}"
        return title to text
    }

    private fun multiText(m: MultiState): Pair<String, String> {
        val down = m.targets.filter { it.health == TargetState.Health.DOWN }
        val title = if (down.isNotEmpty()) "⚠ ${down.size} di ${m.targets.size} schede NON RISPONDONO"
        else "${m.groupName ?: "Più schede"} · ${m.targets.size} schede"
        val text = "Rispondono ${m.okCount} · Instabili ${m.warnCount} · Non rispondono ${m.downCount}" +
            (if (down.isNotEmpty()) "\nNon rispondono: " + down.take(6).joinToString(", ") { it.label } + (if (down.size > 6) "…" else "") else "")
        return title to text
    }

    private fun build(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, PingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ping)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Interrompi", stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Test in corso", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Mostra lo stato del test PING in esecuzione"
            setShowBadge(false)
        })
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "ping_running"
        private const val NOTIF_ID = 42
        private const val ACTION_STOP = "it.pingpro.app.STOP"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, PingService::class.java)) }
        }
    }
}
