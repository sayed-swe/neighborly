package com.neighborly.app.core.util

import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Ports of src/lib/format.js. */
object Format {

    fun parseInstant(value: String?): Instant? = value?.let { s ->
        runCatching { OffsetDateTime.parse(s).toInstant() }
            .recoverCatching { Instant.parse(s) }
            .recoverCatching { LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant() }
            .recoverCatching { LocalDate.parse(s).atStartOfDay(ZoneId.systemDefault()).toInstant() }
            .getOrNull()
    }

    /** "Just now", "5m", "3h", "2d", or a short date. */
    fun timeAgo(value: String?, now: Instant = Instant.now()): String {
        val instant = parseInstant(value) ?: return ""
        val s = Duration.between(instant, now).seconds
        if (s < 45) return "Just now"
        val m = s / 60
        if (m < 60) return "${m.coerceAtLeast(1)}m"
        val h = m / 60
        if (h < 24) return "${h}h"
        val d = h / 24
        if (d < 7) return "${d}d"
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val pattern = if (date.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun date(value: String?): String {
        val instant = parseInstant(value) ?: return ""
        return instant.atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }

    fun dateTime(value: String?): String {
        val instant = parseInstant(value) ?: return ""
        return instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE, d MMM, h:mm a", Locale.getDefault()))
    }

    fun time(value: String?): String {
        val instant = parseInstant(value) ?: return ""
        return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

    /** "৳1,25,000" style (en-IN grouping), or a label for free / exchange / wanted. */
    fun price(price: Double?, type: String?): String {
        if (type == "free") return "Free"
        if (price == null) return when (type) {
            "exchange" -> "Exchange"
            "buy" -> "Wanted"
            else -> "Ask price"
        }
        return "৳" + indianGrouping(price)
    }

    /** 1234567.5 → "12,34,567.5" — same as toLocaleString('en-IN'), independent of the JVM's locale data. */
    fun indianGrouping(value: Double): String {
        val negative = value < 0
        val rounded = Math.round(kotlin.math.abs(value) * 100) / 100.0
        val whole = rounded.toLong()
        val fraction = Math.round((rounded - whole) * 100).toInt()
        val digits = whole.toString()
        val grouped = if (digits.length <= 3) digits else {
            val head = digits.dropLast(3)
            val tail = digits.takeLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
        }
        val frac = when {
            fraction == 0 -> ""
            fraction % 10 == 0 -> "." + (fraction / 10)
            else -> "." + fraction.toString().padStart(2, '0')
        }
        return (if (negative) "-" else "") + grouped + frac
    }

    fun compact(n: Int?): String {
        if (n == null) return "0"
        if (n < 1000) return n.toString()
        if (n < 1_000_000) {
            val v = n / 1000.0
            return (if (n < 10_000) "%.1f".format(Locale.US, v) else "%.0f".format(Locale.US, v)).removeSuffix(".0") + "K"
        }
        return "%.1f".format(Locale.US, n / 1_000_000.0).removeSuffix(".0") + "M"
    }

    fun initials(name: String?): String =
        name.orEmpty().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

    fun labelize(s: String?): String =
        s.orEmpty().replace('_', ' ').split(' ').joinToString(" ") { w ->
            w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

    fun firstName(name: String?): String = name.orEmpty().trim().split(" ").firstOrNull().orEmpty()

    /** Same as clean() in api.js: strip characters that break PostgREST or() filters. */
    fun cleanSearch(s: String?): String = s.orEmpty().replace(Regex("[,()%*\\\\]"), " ").trim()
}
