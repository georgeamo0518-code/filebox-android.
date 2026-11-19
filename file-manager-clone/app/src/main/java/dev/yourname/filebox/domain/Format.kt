package dev.yourname.filebox.domain

import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object Format {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun bytes(b: Long): String {
        if (b < 1024) return "$b B"
        val group = (log10(b.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.size - 1)
        val v = b / 1024.0.pow(group)
        return if (v >= 100) "%.0f %s".format(v, units[group]) else "%.1f %s".format(v, units[group])
    }

    fun count(n: Int): String = NumberFormat.getIntegerInstance().format(n)

    fun items(n: Int) = if (n == 1) "1 item" else "${count(n)} items"

    /** Short date for row meta: "3 Nov" this year, "3 Nov 2024" otherwise. */
    fun shortDate(ms: Long): String {
        val d = localDay(ms)
        val pattern = if (d.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"
        return d.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun dateTime(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

    /** Section headers on S05: Today, Yesterday, or a full date. */
    fun dayHeader(day: LocalDate): String {
        val today = LocalDate.now()
        return when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        }
    }

    fun localDay(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
}
