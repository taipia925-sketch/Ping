package it.pingpro.app.ping

/** Configurazione IP di una scheda di rete del telefono (l'equivalente di una sezione di `ipconfig`). */
data class NetInterfaceInfo(
    val type: String,
    val interfaceName: String?,
    val isDefault: Boolean,
    val validatedInternet: Boolean,
    val ipv4: String?,
    val prefixLength: Int?,
    val gateway: String?,
    val ipv6: List<String> = emptyList(),
    val dns: List<String> = emptyList(),
    val domain: String? = null,
    val dhcpServer: String? = null,
    val wifiRssi: Int? = null,
    val linkSpeedMbps: Int? = null,
    val frequencyMHz: Int? = null,
    val mtu: Int? = null,
) {
    val isLan: Boolean get() = type == "Wi-Fi" || type == "Ethernet"
    val subnetMask: String? get() = prefixLength?.let { IpConfig.prefixToMask(it) }
    val networkAddress: String? get() = if (ipv4 != null && prefixLength != null) IpConfig.networkAddress(ipv4, prefixLength) else null
    val hostRange: Pair<String, String>? get() = if (ipv4 != null && prefixLength != null) IpConfig.hostRange(ipv4, prefixLength) else null
}

object IpConfig {

    private fun toInt(ip: String): Long? {
        val p = ip.split('.')
        if (p.size != 4) return null
        var v = 0L
        for (s in p) { val n = s.toIntOrNull() ?: return null; if (n !in 0..255) return null; v = (v shl 8) or n.toLong() }
        return v
    }

    private fun fromInt(v: Long): String = "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"

    private fun maskBits(prefix: Int): Long = if (prefix <= 0) 0L else (0xFFFFFFFFL shl (32 - prefix.coerceAtMost(32))) and 0xFFFFFFFFL

    fun prefixToMask(prefix: Int): String = fromInt(maskBits(prefix))

    fun networkAddress(ip: String, prefix: Int): String? = toInt(ip)?.let { fromInt(it and maskBits(prefix)) }

    /** Primo e ultimo indirizzo utilizzabile della sottorete (es. 192.168.1.1 – 192.168.1.254). */
    fun hostRange(ip: String, prefix: Int): Pair<String, String>? {
        val v = toInt(ip) ?: return null
        if (prefix >= 31) return fromInt(v) to fromInt(v)
        val net = v and maskBits(prefix)
        val bcast = net or (maskBits(prefix).inv() and 0xFFFFFFFFL)
        return fromInt(net + 1) to fromInt(bcast - 1)
    }

    fun sameSubnet(a: String, b: String, prefix: Int): Boolean {
        val x = toInt(a) ?: return false; val y = toInt(b) ?: return false
        return (x and maskBits(prefix)) == (y and maskBits(prefix))
    }

    fun signalLabel(rssi: Int): String = when {
        rssi >= -55 -> "Ottimo"
        rssi >= -67 -> "Buono"
        rssi >= -75 -> "Discreto"
        rssi >= -85 -> "Debole"
        else -> "Molto debole"
    }

    private fun section(i: NetInterfaceInfo): String = when (i.type) {
        "Wi-Fi" -> "Scheda LAN wireless Wi-Fi"
        "Ethernet" -> "Scheda Ethernet Ethernet"
        "Dati mobili" -> "Scheda rete mobile Dati mobili"
        "VPN" -> "Scheda sconosciuta VPN"
        else -> "Scheda ${i.type}"
    }

    /** Testo nello stesso formato di `ipconfig` di Windows in italiano (con qualche voce di `ipconfig /all`). */
    fun windowsText(list: List<NetInterfaceInfo>, all: Boolean = false): String = buildString {
        appendLine("Configurazione IP del telefono")
        appendLine()
        if (list.isEmpty()) appendLine("Nessuna scheda di rete connessa.")
        list.forEach { i ->
            appendLine("${section(i)}:")
            appendLine()
            appendLine("   Suffisso DNS specifico per connessione: ${i.domain ?: ""}")
            if (all && i.interfaceName != null) appendLine("   Interfaccia . . . . . . . . . . . . . : ${i.interfaceName}")
            i.ipv6.forEach { appendLine("   Indirizzo IPv6 locale rispetto al collegamento . : $it") }
            appendLine("   Indirizzo IPv4. . . . . . . . . . . . : ${i.ipv4 ?: "-"}")
            appendLine("   Subnet mask . . . . . . . . . . . . . : ${i.subnetMask ?: "-"}")
            appendLine("   Gateway predefinito . . . . . . . . . : ${i.gateway ?: ""}")
            if (all) {
                i.dhcpServer?.let { appendLine("   Server DHCP . . . . . . . . . . . . . : $it") }
                if (i.dns.isNotEmpty()) {
                    appendLine("   Server DNS. . . . . . . . . . . . . . : ${i.dns.first()}")
                    i.dns.drop(1).forEach { appendLine("                                           $it") }
                }
            }
            appendLine()
        }
    }
}
