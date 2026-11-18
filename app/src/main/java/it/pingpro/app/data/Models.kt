package it.pingpro.app.data

import it.pingpro.app.ping.EventKind
import it.pingpro.app.ping.PingEvent
import it.pingpro.app.ping.PingMode
import it.pingpro.app.ping.PingSample
import it.pingpro.app.ping.ReplyStatus
import org.json.JSONArray
import org.json.JSONObject

data class SavedDevice(
    val id: String,
    val name: String,
    val address: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("address", address).put("note", note)
        .put("createdAt", createdAt).put("lastUsedAt", lastUsedAt ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = SavedDevice(
            id = o.getString("id"), name = o.getString("name"), address = o.getString("address"),
            note = o.optString("note", ""), createdAt = o.optLong("createdAt"),
            lastUsedAt = if (o.isNull("lastUsedAt")) null else o.optLong("lastUsedAt"),
        )
    }
}

/** Riepilogo di un test concluso, salvato nello storico. */
data class TestSummary(
    val id: String,
    val target: String,
    val resolvedIp: String,
    val deviceName: String?,
    val mode: PingMode,
    val count: Int,
    val payloadSize: Int,
    val timeoutMs: Int,
    val ttl: Int,
    val startedAt: Long,
    val endedAt: Long,
    val sent: Int,
    val received: Int,
    val minMs: Double?,
    val avgMs: Double?,
    val maxMs: Double?,
    val jitterMs: Double?,
    val outages: Int,
    val network: String?,
    val engine: String?,
    /** Test eseguiti insieme (più schede) condividono lo stesso batchId. */
    val batchId: String? = null,
    val batchName: String? = null,
) {
    val lost: Int get() = sent - received
    val lossPercent: Double get() = if (sent > 0) lost * 100.0 / sent else 0.0
    val durationMs: Long get() = endedAt - startedAt
    val displayName: String get() = deviceName?.takeIf { it.isNotBlank() } ?: target

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("target", target).put("resolvedIp", resolvedIp)
        .put("deviceName", deviceName ?: JSONObject.NULL).put("mode", mode.name)
        .put("count", count).put("payloadSize", payloadSize).put("timeoutMs", timeoutMs).put("ttl", ttl)
        .put("startedAt", startedAt).put("endedAt", endedAt).put("sent", sent).put("received", received)
        .put("minMs", minMs ?: JSONObject.NULL).put("avgMs", avgMs ?: JSONObject.NULL)
        .put("maxMs", maxMs ?: JSONObject.NULL).put("jitterMs", jitterMs ?: JSONObject.NULL)
        .put("outages", outages).put("network", network ?: JSONObject.NULL).put("engine", engine ?: JSONObject.NULL)
        .put("batchId", batchId ?: JSONObject.NULL).put("batchName", batchName ?: JSONObject.NULL)

    companion object {
        private fun JSONObject.optDoubleOrNull(k: String): Double? = if (isNull(k) || !has(k)) null else getDouble(k)
        private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)

        fun fromJson(o: JSONObject) = TestSummary(
            id = o.getString("id"), target = o.getString("target"), resolvedIp = o.optString("resolvedIp"),
            deviceName = o.optStringOrNull("deviceName"),
            mode = runCatching { PingMode.valueOf(o.getString("mode")) }.getOrDefault(PingMode.SINGLE),
            count = o.optInt("count", 4), payloadSize = o.optInt("payloadSize", 32),
            timeoutMs = o.optInt("timeoutMs", 4000), ttl = o.optInt("ttl", 128),
            startedAt = o.getLong("startedAt"), endedAt = o.getLong("endedAt"),
            sent = o.getInt("sent"), received = o.getInt("received"),
            minMs = o.optDoubleOrNull("minMs"), avgMs = o.optDoubleOrNull("avgMs"),
            maxMs = o.optDoubleOrNull("maxMs"), jitterMs = o.optDoubleOrNull("jitterMs"),
            outages = o.optInt("outages"), network = o.optStringOrNull("network"), engine = o.optStringOrNull("engine"),
            batchId = o.optStringOrNull("batchId"), batchName = o.optStringOrNull("batchName"),
        )
    }
}

data class TestDetail(val summary: TestSummary, val samples: List<PingSample>, val events: List<PingEvent>)

object DetailCodec {
    fun encode(samples: List<PingSample>, events: List<PingEvent>): String {
        val s = JSONArray()
        samples.forEach { p ->
            s.put(JSONArray().put(p.seq).put(p.timestamp).put(p.status.ordinal)
                .put(p.rttMs ?: JSONObject.NULL).put(p.ttl ?: JSONObject.NULL).put(p.bytes).put(p.message ?: JSONObject.NULL))
        }
        val e = JSONArray()
        events.forEach { ev ->
            e.put(JSONObject().put("t", ev.timestamp).put("k", ev.kind.name).put("title", ev.title)
                .put("detail", ev.detail ?: JSONObject.NULL).put("seq", ev.seq ?: JSONObject.NULL))
        }
        return JSONObject().put("samples", s).put("events", e).toString()
    }

    fun decode(json: String): Pair<List<PingSample>, List<PingEvent>> {
        val o = JSONObject(json)
        val sa = o.optJSONArray("samples") ?: JSONArray()
        val samples = (0 until sa.length()).map { i ->
            val a = sa.getJSONArray(i)
            PingSample(
                seq = a.getInt(0), timestamp = a.getLong(1),
                status = ReplyStatus.entries.getOrElse(a.getInt(2)) { ReplyStatus.TIMEOUT },
                rttMs = if (a.isNull(3)) null else a.getDouble(3),
                ttl = if (a.isNull(4)) null else a.getInt(4),
                bytes = a.optInt(5), message = if (a.isNull(6)) null else a.getString(6),
            )
        }
        val ea = o.optJSONArray("events") ?: JSONArray()
        val events = (0 until ea.length()).map { i ->
            val e = ea.getJSONObject(i)
            PingEvent(
                timestamp = e.getLong("t"),
                kind = runCatching { EventKind.valueOf(e.getString("k")) }.getOrDefault(EventKind.INFO),
                title = e.getString("title"),
                detail = if (e.isNull("detail")) null else e.getString("detail"),
                seq = if (e.isNull("seq")) null else e.getInt("seq"),
            )
        }
        return samples to events
    }
}

data class AppSettings(
    val count: Int = 4,
    val payloadSize: Int = 32,
    val timeoutMs: Int = 4000,
    val ttl: Int = 128,
    val highLatencyMs: Int = 100,
    val vibrateOnAlert: Boolean = true,
    val soundOnAlert: Boolean = false,
    val keepScreenOn: Boolean = true,
    val onboardingDone: Boolean = false,
    /** "light" (predefinito), "dark" oppure "system" (segue il telefono). */
    val theme: String = "light",
)

/** Una scheda da controllare nel test di più schede. */
data class MultiTarget(val address: String, val name: String? = null) {
    fun toJson(): JSONObject = JSONObject().put("a", address).put("n", name ?: JSONObject.NULL)
    companion object {
        fun fromJson(o: JSONObject) = MultiTarget(o.getString("a"), if (o.isNull("n")) null else o.optString("n"))
    }
}

/** Gruppo salvato di schede, ad esempio tutte le schede di un apparato. */
data class TargetGroup(val id: String, val name: String, val targets: List<MultiTarget>) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name)
        .put("targets", JSONArray().apply { targets.forEach { put(it.toJson()) } })
    companion object {
        fun fromJson(o: JSONObject): TargetGroup {
            val a = o.optJSONArray("targets") ?: JSONArray()
            return TargetGroup(o.getString("id"), o.getString("name"), (0 until a.length()).map { MultiTarget.fromJson(a.getJSONObject(it)) })
        }
    }
}
