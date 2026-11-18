package it.pingpro.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    private val IT = Locale.ITALY

    /** Millisecondi con precisione adeguata: 0,42 · 3,8 · 27. */
    fun ms(v: Double): String = when {
        v < 1 -> String.format(IT, "%.2f", v)
        v < 10 -> String.format(IT, "%.1f", v)
        else -> String.format(IT, "%.0f", v)
    }

    fun msOrDash(v: Double?): String = v?.let { ms(it) } ?: "—"

    fun percent(v: Double): String = if (v == 0.0 || v == 100.0) String.format(IT, "%.0f%%", v) else String.format(IT, "%.1f%%", v)

    fun time(ts: Long): String = SimpleDateFormat("HH:mm:ss", IT).format(Date(ts))
    fun dateTime(ts: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", IT).format(Date(ts))
    fun dateTimeFull(ts: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", IT).format(Date(ts))
    fun fileStamp(ts: Long): String = SimpleDateFormat("yyyyMMdd_HHmmss", IT).format(Date(ts))

    fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return when {
            h > 0 -> "${h} h ${m} min"
            m > 0 -> "${m} min ${sec} s"
            else -> "${sec} s"
        }
    }

    fun clock(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format(IT, "%d:%02d:%02d", h, m, sec) else String.format(IT, "%02d:%02d", m, sec)
    }
}
