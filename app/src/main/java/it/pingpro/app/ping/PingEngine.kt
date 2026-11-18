package it.pingpro.app.ping

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.system.ErrnoException
import android.util.Log
import java.net.InetAddress
import java.net.UnknownHostException

/** Operazioni comuni al test singolo e al test di più schede: risoluzione, scelta della rete, apertura del motore. */
object PingEngine {

    class Resolved(val address: InetAddress, val ip: String, val network: NetworkChoice, val isLiteral: Boolean) {
        /** true se è un indirizzo privato che però non appartiene alla rete del telefono. */
        val outsidePhoneNetwork: Boolean
            get() = isLiteral && !network.sameSubnet && (address.isSiteLocalAddress || address.isLinkLocalAddress)
    }

    /** @throws UnknownHostException se il nome non può essere risolto. */
    fun resolve(context: Context, target: String): Resolved {
        val literal = NetworkResolver.isIpv4Literal(target) || target.contains(':')
        val preliminary = if (literal) runCatching { InetAddress.getByName(target) }.getOrNull() else null
        var choice = NetworkResolver.choose(context, preliminary)
        val resolved = preliminary ?: NetworkResolver.resolve(target, choice.network)
        if (preliminary == null) choice = NetworkResolver.choose(context, resolved)
        return Resolved(resolved, resolved.hostAddress ?: target, choice, preliminary != null)
    }

    /** Apre il socket ICMP nativo, con ripiego sul comando ping di sistema. */
    fun openPinger(r: Resolved, ttl: Int, payloadSize: Int): Pinger = try {
        IcmpSocketPinger(r.address, r.network.network, ttl, payloadSize)
    } catch (e: Throwable) {
        Log.w("PingEngine", "Socket ICMP non disponibile, uso ping di sistema", e)
        if (e is ErrnoException || e is SecurityException || e is java.io.IOException) SystemPingPinger(r.address, ttl, payloadSize)
        else throw e
    }

    fun unknownHostMessage(target: String) = "Impossibile trovare l'host $target. Verificare il nome e riprovare."

    /** Disattiva il risparmio energetico del Wi-Fi durante il test per misure di latenza più stabili. */
    @Suppress("DEPRECATION")
    fun acquireWifiLock(context: Context): WifiManager.WifiLock? = runCatching {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wm.createWifiLock(mode, "PingPro:test").apply { setReferenceCounted(false); acquire() }
    }.getOrNull()
}
