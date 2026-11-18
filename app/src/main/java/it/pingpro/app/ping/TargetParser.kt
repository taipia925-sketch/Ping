package it.pingpro.app.ping

/**
 * Interpreta un elenco di indirizzi scritto o incollato dall'utente.
 * Accetta indirizzi separati da a capo, virgole, punti e virgola o spazi, e intervalli:
 * "192.168.1.10-20" oppure "192.168.1.10-192.168.1.20".
 */
object TargetParser {
    const val MAX_TARGETS = 100

    data class Result(val addresses: List<String>, val invalid: List<String>, val truncated: Boolean)

    private fun toLong(ip: String): Long? {
        if (!NetworkResolver.isIpv4Literal(ip)) return null
        return ip.trim().split('.').fold(0L) { acc, p -> (acc shl 8) or p.toLong() }
    }

    private fun fromLong(v: Long) = "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"

    /** Espande un intervallo di indirizzi IPv4 (estremi inclusi). Restituisce null se non valido. */
    fun expandRange(from: String, to: String, max: Int = MAX_TARGETS): List<String>? {
        val a = toLong(from) ?: return null
        val b = toLong(to) ?: return null
        if (b < a) return null
        val n = (b - a + 1).coerceAtMost(max.toLong())
        return (0 until n).map { fromLong(a + it) }
    }

    /** Numero di indirizzi in un intervallo, o null se non valido. */
    fun rangeSize(from: String, to: String): Long? {
        val a = toLong(from) ?: return null
        val b = toLong(to) ?: return null
        return if (b < a) null else b - a + 1
    }

    fun parse(text: String, max: Int = MAX_TARGETS): Result {
        val out = LinkedHashSet<String>()
        val invalid = ArrayList<String>()
        var truncated = false
        val tokens = text.split(Regex("[\\s,;]+")).map { it.trim() }.filter { it.isNotEmpty() }
        for (tok in tokens) {
            val dash = tok.indexOf('-')
            val dotsBefore = if (dash > 0) tok.substring(0, dash).count { it == '.' } else 0
            val list: List<String>? = if (dash > 0 && dotsBefore == 3) {
                val left = tok.substring(0, dash); val right = tok.substring(dash + 1)
                val end = when {
                    right.all(Char::isDigit) && right.isNotEmpty() -> left.substringBeforeLast('.') + "." + right
                    else -> right
                }
                val size = rangeSize(left, end)
                if (size == null) null else expandRange(left, end, (max - out.size).coerceAtLeast(0) + 1)
            } else if (NetworkResolver.isValidTarget(tok)) listOf(tok) else null
            if (list == null) { invalid.add(tok); continue }
            for (a in list) {
                if (out.size >= max) { truncated = true; break }
                out.add(a)
            }
            if (truncated) break
        }
        return Result(out.toList(), invalid, truncated)
    }
}
