package it.pingpro.app.ping

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * Legge la configurazione IP del telefono (come `ipconfig`) e la tiene aggiornata quando
 * il telefono cambia rete. Nessun permesso di localizzazione richiesto.
 */
class NetworkInfoProvider(private val context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _interfaces = MutableStateFlow<List<NetInterfaceInfo>>(emptyList())
    val interfaces: StateFlow<List<NetInterfaceInfo>> = _interfaces.asStateFlow()

    init {
        refresh()
        runCatching {
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh()
                override fun onLost(network: Network) = refresh()
                override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = refresh()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
            }
            cm.registerNetworkCallback(NetworkRequest.Builder().build(), cb)
        }
    }

    /** Gateway predefinito della rete locale (Wi-Fi/Ethernet) principale, se presente. */
    val primaryLan: NetInterfaceInfo? get() = _interfaces.value.firstOrNull { it.isLan && it.ipv4 != null }

    @Suppress("DEPRECATION")
    fun refresh() {
        val list = runCatching {
            val active = cm.activeNetwork
            cm.allNetworks.mapNotNull { n -> read(n, n == active) }
                .distinctBy { it.interfaceName to it.ipv4 }
                .sortedWith(compareByDescending<NetInterfaceInfo> { it.isLan }.thenByDescending { it.gateway != null }.thenByDescending { it.isDefault })
        }.getOrDefault(emptyList())
        _interfaces.value = list
    }

    @Suppress("DEPRECATION")
    private fun read(n: Network, isDefault: Boolean): NetInterfaceInfo? {
        val lp = cm.getLinkProperties(n) ?: return null
        val caps = cm.getNetworkCapabilities(n)
        val type = when {
            caps == null -> "Rete"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dati mobili"
            else -> "Rete"
        }
        val v4 = lp.linkAddresses.firstOrNull { it.address is Inet4Address }
        val gw = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address && !it.gateway!!.isAnyLocalAddress }?.gateway?.hostAddress
            ?: lp.routes.firstOrNull { it.isDefaultRoute && it.gateway != null && !it.gateway!!.isAnyLocalAddress }?.gateway?.hostAddress?.substringBefore('%')
        var rssi: Int? = null; var speed: Int? = null; var freq: Int? = null
        if (type == "Wi-Fi") {
            val wi: WifiInfo? = if (Build.VERSION.SDK_INT >= 29) caps?.transportInfo as? WifiInfo
            else runCatching { context.applicationContext.getSystemService(WifiManager::class.java).connectionInfo }.getOrNull()
            wi?.let {
                rssi = it.rssi.takeIf { r -> r in -127..0 }
                speed = it.linkSpeed.takeIf { s -> s > 0 }
                freq = it.frequency.takeIf { f -> f > 0 }
            }
        }
        return NetInterfaceInfo(
            type = type,
            interfaceName = lp.interfaceName,
            isDefault = isDefault,
            validatedInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            ipv4 = v4?.address?.hostAddress,
            prefixLength = v4?.prefixLength,
            gateway = gw,
            ipv6 = lp.linkAddresses.filter { it.address is Inet6Address }.mapNotNull { it.address.hostAddress?.substringBefore('%') },
            dns = lp.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') },
            domain = lp.domains,
            dhcpServer = if (Build.VERSION.SDK_INT >= 30) lp.dhcpServerAddress?.hostAddress else null,
            wifiRssi = rssi, linkSpeedMbps = speed, frequencyMHz = freq,
            mtu = if (Build.VERSION.SDK_INT >= 29) lp.mtu.takeIf { it > 0 } else null,
        )
    }
}
