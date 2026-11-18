package it.pingpro.app.ping

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

/** Rete del telefono attraverso la quale viene eseguito il test. */
data class NetworkChoice(
    val network: Network?,
    val type: String,
    val interfaceName: String?,
    val localAddress: String?,
    val prefixLength: Int?,
    /** true se la destinazione appartiene alla sottorete locale (stessa rete della scheda). */
    val sameSubnet: Boolean,
) {
    val label: String
        get() = buildString {
            append(type)
            if (localAddress != null) append(" · ").append(localAddress)
            if (prefixLength != null) append('/').append(prefixLength)
        }
}

object NetworkResolver {

    private fun typeOf(caps: NetworkCapabilities?): String = when {
        caps == null -> "Rete"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dati mobili"
        else -> "Rete"
    }

    private fun priority(caps: NetworkCapabilities?): Int = when {
        caps == null -> 0
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 4
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 3
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> 2
        else -> 1
    }

    /**
     * Sceglie la rete corretta per raggiungere [target].
     *
     * Su Android, se il Wi-Fi a cui è collegata la scheda non ha accesso a Internet, il sistema può
     * instradare il traffico sui dati mobili. Per evitarlo si cerca la rete (Wi-Fi o Ethernet) che
     * ha una route diretta verso la sottorete della destinazione e si lega il socket a quella rete.
     */
    @Suppress("DEPRECATION")
    fun choose(context: Context, target: InetAddress?): NetworkChoice {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val networks = runCatching { cm.allNetworks.toList() }.getOrDefault(emptyList())

        if (target != null) {
            val local = networks.mapNotNull { n ->
                val lp = cm.getLinkProperties(n) ?: return@mapNotNull null
                val caps = cm.getNetworkCapabilities(n)
                val route = lp.routes.firstOrNull { r ->
                    !r.isDefaultRoute && r.destination.prefixLength > 0 && r.matches(target)
                } ?: return@mapNotNull null
                Triple(n, lp, caps) to route.destination.prefixLength
            }.sortedWith(compareByDescending<Pair<Triple<Network, LinkProperties, NetworkCapabilities?>, Int>> { it.second }
                .thenByDescending { priority(it.first.third) })
                .firstOrNull()
            if (local != null) {
                val (n, lp, caps) = local.first
                return build(n, lp, caps, target, sameSubnet = true)
            }
        }

        // Indirizzo privato su un'altra sottorete (raggiunto tramite router): si preferisce comunque
        // il Wi-Fi/Ethernet locale rispetto ai dati mobili.
        if (target != null && (target.isSiteLocalAddress || target.isLinkLocalAddress)) {
            val lan = networks.mapNotNull { n ->
                val caps = cm.getNetworkCapabilities(n) ?: return@mapNotNull null
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return@mapNotNull null
                Triple(n, cm.getLinkProperties(n), caps)
            }.maxByOrNull { priority(it.third) }
            if (lan != null) return build(lan.first, lan.second, lan.third, target, sameSubnet = false)
        }

        val active = cm.activeNetwork
        if (active != null) {
            return build(active, cm.getLinkProperties(active), cm.getNetworkCapabilities(active), target, sameSubnet = false)
        }
        return NetworkChoice(null, "Nessuna rete", null, null, null, false)
    }

    private fun build(n: Network, lp: LinkProperties?, caps: NetworkCapabilities?, target: InetAddress?, sameSubnet: Boolean): NetworkChoice {
        val wantV4 = target == null || target is Inet4Address
        val la = lp?.linkAddresses?.firstOrNull { (it.address is Inet4Address) == wantV4 && !it.address.isLinkLocalAddress }
            ?: lp?.linkAddresses?.firstOrNull()
        return NetworkChoice(
            network = n,
            type = typeOf(caps),
            interfaceName = lp?.interfaceName,
            localAddress = la?.address?.hostAddress,
            prefixLength = la?.prefixLength,
            sameSubnet = sameSubnet,
        )
    }

    /** Risoluzione del nome (se l'utente inserisce un nome host invece di un IP), preferendo IPv4. */
    fun resolve(target: String, network: Network?): InetAddress {
        val all = network?.getAllByName(target) ?: InetAddress.getAllByName(target)
        return all.firstOrNull { it is Inet4Address } ?: all.first()
    }

    /** Verifica sintattica di un indirizzo IPv4 senza effettuare risoluzioni DNS. */
    fun isIpv4Literal(s: String): Boolean {
        val parts = s.trim().split('.')
        if (parts.size != 4) return false
        return parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() in 0..255 }
    }

    fun isValidTarget(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty() || t.contains(' ')) return false
        if (isIpv4Literal(t)) return true
        if (t.contains(':')) return t.all { it.isLetterOrDigit() || it == ':' || it == '.' || it == '%' }
        // Un nome che sembra un IP ma non lo è (es. 192.168.1.300) viene rifiutato.
        if (t.all { it.isDigit() || it == '.' }) return false
        return t.all { it.isLetterOrDigit() || it == '-' || it == '.' || it == '_' }
    }
}
