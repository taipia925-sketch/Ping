package it.pingpro.app.ping

/**
 * Costruzione e analisi dei pacchetti ICMP Echo. Il contenuto (payload) è lo stesso usato da
 * ping.exe di Windows: la sequenza "abcdefghijklmnopqrstuvwabcdefghi" ripetuta.
 */
object IcmpPacket {
    const val HEADER_SIZE = 8
    const val ICMP_ECHO_REQUEST = 8
    const val ICMP_ECHO_REPLY = 0
    const val ICMP6_ECHO_REQUEST = 128
    const val ICMP6_ECHO_REPLY = 129

    private const val WINDOWS_PATTERN = "abcdefghijklmnopqrstuvw"

    fun windowsPayload(size: Int): ByteArray =
        ByteArray(size) { i -> WINDOWS_PATTERN[i % WINDOWS_PATTERN.length].code.toByte() }

    fun buildEchoRequest(ipv6: Boolean, identifier: Int, seq: Int, payload: ByteArray): ByteArray {
        val p = ByteArray(HEADER_SIZE + payload.size)
        p[0] = (if (ipv6) ICMP6_ECHO_REQUEST else ICMP_ECHO_REQUEST).toByte()
        p[1] = 0
        p[4] = (identifier shr 8).toByte()
        p[5] = identifier.toByte()
        p[6] = (seq shr 8).toByte()
        p[7] = seq.toByte()
        payload.copyInto(p, HEADER_SIZE)
        if (!ipv6) {
            // Per ICMPv6 il checksum è calcolato dal kernel (include lo pseudo-header IPv6).
            val c = checksum(p)
            p[2] = (c shr 8).toByte()
            p[3] = c.toByte()
        }
        return p
    }

    /** Checksum Internet (RFC 1071). */
    fun checksum(data: ByteArray, length: Int = data.size): Int {
        var sum = 0L
        var i = 0
        while (i + 1 < length) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < length) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    data class Reply(val type: Int, val code: Int, val identifier: Int, val seq: Int, val payloadSize: Int)

    /** Analizza un datagramma ricevuto su un socket ICMP "ping" (senza header IP). */
    fun parse(data: ByteArray, length: Int): Reply? {
        if (length < HEADER_SIZE) return null
        return Reply(
            type = data[0].toInt() and 0xFF,
            code = data[1].toInt() and 0xFF,
            identifier = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF),
            seq = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF),
            payloadSize = length - HEADER_SIZE,
        )
    }

    fun isEchoReply(reply: Reply, ipv6: Boolean): Boolean =
        reply.type == (if (ipv6) ICMP6_ECHO_REPLY else ICMP_ECHO_REPLY) && reply.code == 0
}
