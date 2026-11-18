package it.pingpro.app.ping

import android.net.Network
import android.os.Build
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructMsghdr
import android.system.StructPollfd
import java.io.FileDescriptor
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Motore PING principale: invia vere richieste ICMP Echo direttamente dal telefono.
 *
 * Android non permette alle app di aprire socket "raw" (riservati a root), ma il kernel Linux
 * di Android mette a disposizione i socket ICMP di tipo "ping" (SOCK_DGRAM + IPPROTO_ICMP),
 * pensati proprio per questo scopo. Il pacchetto che parte dalla scheda di rete è lo stesso
 * ICMP Echo Request (tipo 8) inviato da ping.exe di Windows, e la risposta è un Echo Reply (tipo 0).
 *
 * Il socket viene inoltre legato alla rete (Wi-Fi/Ethernet) che contiene l'indirizzo di
 * destinazione, così il traffico non finisce sui dati mobili quando il Wi-Fi è senza Internet.
 */
class IcmpSocketPinger(
    private val address: InetAddress,
    network: Network?,
    ttl: Int,
    payloadSize: Int,
) : Pinger {

    private val ipv6 = address is Inet6Address
    private val fd: FileDescriptor
    private val payload = IcmpPacket.windowsPayload(payloadSize)
    private val recvBuffer = ByteArray(65536)
    private var ttlFromControl = Build.VERSION.SDK_INT >= 33
    private var identifier = (SystemClock.elapsedRealtimeNanos() and 0xFFFF).toInt()

    override val engineName: String = if (ipv6) "Socket ICMPv6 nativo" else "Socket ICMP nativo"

    init {
        fd = if (ipv6) Os.socket(OsConstants.AF_INET6, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMPV6)
        else Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP)
        try {
            network?.bindSocket(fd)
            if (ipv6) {
                Os.setsockoptInt(fd, OsConstants.IPPROTO_IPV6, OsConstants.IPV6_UNICAST_HOPS, ttl)
            } else {
                Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, OsConstants.IP_TTL, ttl)
            }
            if (ttlFromControl) {
                try {
                    if (ipv6) Os.setsockoptInt(fd, OsConstants.IPPROTO_IPV6, OsConstants.IPV6_RECVHOPLIMIT, 1)
                    else Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, IP_RECVTTL, 1)
                } catch (_: Throwable) {
                    ttlFromControl = false
                }
            }
        } catch (t: Throwable) {
            runCatching { Os.close(fd) }
            throw t
        }
    }

    override fun ping(seq: Int, timeoutMs: Int, isCancelled: () -> Boolean): EchoResult? {
        val seq16 = seq and 0xFFFF
        drainStale()
        val packet = IcmpPacket.buildEchoRequest(ipv6, identifier, seq16, payload)
        val start = System.nanoTime()
        try {
            Os.sendto(fd, packet, 0, packet.size, 0, address, 0)
        } catch (e: ErrnoException) {
            return errnoResult(e)
        }
        val deadline = start + timeoutMs * 1_000_000L
        val pfd = StructPollfd().apply { fd = this@IcmpSocketPinger.fd; events = OsConstants.POLLIN.toShort() }
        while (true) {
            if (isCancelled()) return null
            val now = System.nanoTime()
            val remainingMs = ((deadline - now) / 1_000_000L).toInt()
            if (remainingMs <= 0) return EchoResult(ReplyStatus.TIMEOUT, message = "Richiesta scaduta.")
            // Attesa a fette di 100 ms per poter interrompere il test subito.
            val ready = try {
                pfd.revents = 0
                Os.poll(arrayOf(pfd), minOf(remainingMs, 100))
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EINTR) 0 else return errnoResult(e)
            }
            if (ready <= 0) continue
            val received = try {
                receive()
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EAGAIN) continue
                return errnoResult(e)
            }
            val end = System.nanoTime()
            val reply = IcmpPacket.parse(recvBuffer, received.length) ?: continue
            // Con i socket "ping" il kernel sostituisce l'identificatore: si confronta la sequenza.
            if (!IcmpPacket.isEchoReply(reply, ipv6) || reply.seq != seq16) continue
            return EchoResult(
                status = ReplyStatus.OK,
                rttMs = (end - start) / 1_000_000.0,
                ttl = received.ttl,
                bytes = reply.payloadSize,
            )
        }
    }

    private class Received(val length: Int, val ttl: Int?)

    private fun receive(): Received {
        if (ttlFromControl) {
            try {
                val buf = ByteBuffer.wrap(recvBuffer)
                val msg = StructMsghdr(InetSocketAddress(0), arrayOf(buf), null, 0)
                val n = Os.recvmsg(fd, msg, MSG_DONTWAIT)
                var ttl: Int? = null
                msg.msg_control?.forEach { c ->
                    val isTtl = if (ipv6) c.cmsg_level == OsConstants.IPPROTO_IPV6 && c.cmsg_type == IPV6_HOPLIMIT
                    else c.cmsg_level == OsConstants.IPPROTO_IP && c.cmsg_type == OsConstants.IP_TTL
                    if (isTtl && c.cmsg_data.size >= 4) {
                        ttl = ByteBuffer.wrap(c.cmsg_data).order(ByteOrder.nativeOrder()).int
                    } else if (isTtl && c.cmsg_data.isNotEmpty()) {
                        ttl = c.cmsg_data[0].toInt() and 0xFF
                    }
                }
                return Received(n, ttl)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EAGAIN) throw e
                ttlFromControl = false
            } catch (_: Throwable) {
                // Implementazione recvmsg non disponibile su questo dispositivo: si prosegue senza TTL.
                ttlFromControl = false
            }
        }
        val n = Os.recvfrom(fd, recvBuffer, 0, recvBuffer.size, MSG_DONTWAIT, null)
        return Received(n, null)
    }

    /** Scarta eventuali risposte arrivate in ritardo dopo il timeout di una richiesta precedente. */
    private fun drainStale() {
        repeat(64) {
            try {
                Os.recvfrom(fd, recvBuffer, 0, recvBuffer.size, MSG_DONTWAIT, null)
            } catch (_: Throwable) {
                return
            }
        }
    }

    private fun errnoResult(e: ErrnoException): EchoResult = when (e.errno) {
        OsConstants.EHOSTUNREACH -> EchoResult(ReplyStatus.UNREACHABLE, message = "Host di destinazione non raggiungibile.")
        OsConstants.ENETUNREACH -> EchoResult(ReplyStatus.UNREACHABLE, message = "Rete di destinazione non raggiungibile.")
        else -> EchoResult(ReplyStatus.ERROR, message = "Errore di trasmissione (${OsConstants.errnoName(e.errno) ?: e.errno}).")
    }

    override fun close() {
        runCatching { Os.close(fd) }
    }

    private companion object {
        /** Valori Linux: IP_RECVTTL = 12, IPV6_HOPLIMIT = 52. */
        const val IP_RECVTTL = 12
        const val IPV6_HOPLIMIT = 52
        /** Valore Linux di MSG_DONTWAIT (lettura non bloccante). */
        const val MSG_DONTWAIT = 0x40
    }
}
