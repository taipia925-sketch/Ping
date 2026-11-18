package it.pingpro.app.ping

import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Motore di riserva: usa il comando `ping` di sistema presente in Android (toybox/iputils).
 * Viene utilizzato solo se il dispositivo non consente l'apertura del socket ICMP nativo.
 * Anche in questo caso le richieste ICMP Echo partono realmente dal telefono.
 */
class SystemPingPinger(
    private val address: InetAddress,
    private val ttl: Int,
    private val payloadSize: Int,
) : Pinger {

    override val engineName: String = "Comando ping di sistema"

    override fun ping(seq: Int, timeoutMs: Int, isCancelled: () -> Boolean): EchoResult? {
        val binary = if (address is Inet6Address) "ping6" else "ping"
        val waitSec = ((timeoutMs + 999) / 1000).coerceAtLeast(1)
        val cmd = listOf(
            "/system/bin/$binary", "-n", "-c", "1", "-W", waitSec.toString(),
            "-s", payloadSize.toString(), "-t", ttl.toString(), address.hostAddress ?: return null,
        )
        val process = try {
            ProcessBuilder(cmd).redirectErrorStream(true).start()
        } catch (e: Exception) {
            return EchoResult(ReplyStatus.ERROR, message = "Ping di sistema non disponibile.")
        }
        val deadline = System.currentTimeMillis() + timeoutMs + 1500
        while (true) {
            if (isCancelled()) {
                process.destroy(); return null
            }
            if (process.waitFor(100, TimeUnit.MILLISECONDS)) break
            if (System.currentTimeMillis() > deadline) {
                process.destroy()
                return EchoResult(ReplyStatus.TIMEOUT, message = "Richiesta scaduta.")
            }
        }
        val output = process.inputStream.bufferedReader().readText()
        return parseOutput(output)
    }

    override fun close() = Unit

    companion object {
        private val TIME_RE = Regex("""time[=<]([0-9.]+)\s*ms""")
        private val TTL_RE = Regex("""ttl=(\d+)""", RegexOption.IGNORE_CASE)
        private val BYTES_RE = Regex("""(\d+) bytes from""")

        fun parseOutput(output: String): EchoResult {
            val time = TIME_RE.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
            if (time != null) {
                val bytes = BYTES_RE.find(output)?.groupValues?.get(1)?.toIntOrNull()?.minus(8) ?: 0
                return EchoResult(
                    ReplyStatus.OK, rttMs = time,
                    ttl = TTL_RE.find(output)?.groupValues?.get(1)?.toIntOrNull(),
                    bytes = bytes.coerceAtLeast(0),
                )
            }
            return when {
                output.contains("Unreachable", ignoreCase = true) ->
                    EchoResult(ReplyStatus.UNREACHABLE, message = "Host di destinazione non raggiungibile.")
                output.contains("Network is unreachable", ignoreCase = true) ->
                    EchoResult(ReplyStatus.UNREACHABLE, message = "Rete di destinazione non raggiungibile.")
                else -> EchoResult(ReplyStatus.TIMEOUT, message = "Richiesta scaduta.")
            }
        }
    }
}
