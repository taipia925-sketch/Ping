package it.pingpro.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import it.pingpro.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Descrizione dell'ultima versione pubblicata (file release/latest.json del repository). */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String,
) {
    companion object {
        fun parse(json: String): UpdateInfo {
            val o = JSONObject(json)
            return UpdateInfo(
                versionCode = o.getInt("versionCode"),
                versionName = o.getString("versionName"),
                apkUrl = o.getString("apkUrl"),
                sha256 = o.getString("sha256").lowercase(),
                sizeBytes = o.optLong("sizeBytes", 0),
                notes = o.optString("notes", ""),
            )
        }
    }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/**
 * Aggiornamento dell'app senza passare dal Play Store: legge la versione pubblicata nel repository
 * GitHub, scarica l'APK, ne verifica l'impronta SHA-256 e apre l'installazione di Android.
 * Android installa l'aggiornamento solo se è firmato con la stessa chiave dell'app già installata.
 */
class Updater(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val lastCheck: Long get() = prefs.getLong("lastCheck", 0)

    /** Versione per cui l'utente ha già chiuso l'avviso (non la riproponiamo a ogni avvio). */
    var dismissedVersion: Int
        get() = prefs.getInt("dismissed", 0)
        set(v) = prefs.edit().putInt("dismissed", v).apply()

    /** Controllo automatico all'avvio, al massimo ogni 6 ore e senza disturbare se fallisce. */
    fun checkInBackground() {
        if (System.currentTimeMillis() - lastCheck < 6 * 3600_000L) {
            prefs.getString("cachedInfo", null)?.let { cached ->
                runCatching { UpdateInfo.parse(cached) }.getOrNull()?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
                    ?.let { _state.value = UpdateState.Available(it) }
            }
            return
        }
        check(silent = true)
    }

    fun check(silent: Boolean = false) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        scope.launch {
            try {
                val json = httpGetText(LATEST_URL)
                val info = UpdateInfo.parse(json)
                prefs.edit().putLong("lastCheck", System.currentTimeMillis()).putString("cachedInfo", json).apply()
                _state.value = if (info.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(info)
                else UpdateState.UpToDate(System.currentTimeMillis())
            } catch (e: Exception) {
                _state.value = if (silent) UpdateState.Idle
                else UpdateState.Failed("Impossibile controllare gli aggiornamenti: serve una connessione a Internet (Wi-Fi o dati mobili).")
            }
        }
    }

    fun download(info: UpdateInfo) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(info, 0f)
        scope.launch {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "PingPro-${info.versionName}.apk")
                val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
                }
                val digest = MessageDigest.getInstance("SHA-256")
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); digest.update(buf, 0, n); done += n
                            if (total > 0) _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (hash != info.sha256) {
                    file.delete()
                    _state.value = UpdateState.Failed("Il file scaricato non è integro (impronta diversa). Riprova più tardi.", info)
                    return@launch
                }
                _state.value = UpdateState.Ready(info, file)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("Download non riuscito: controlla la connessione a Internet e riprova.", info)
            }
        }
    }

    /** true se Android permette già a PingPro di installare aggiornamenti. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Apre l'impostazione "Installa app sconosciute" per PingPro (serve una volta sola). */
    fun openInstallPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun reset() { _state.value = UpdateState.Idle }

    private fun httpGetText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 10_000; useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    companion object {
        /** File pubblicato nel ramo predefinito del repository. */
        const val LATEST_URL = "https://raw.githubusercontent.com/taipia925-sketch/Ping/HEAD/release/latest.json"
    }
}
