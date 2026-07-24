package com.journal.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DateFormatter {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy EEE", Locale.ENGLISH)
    private val englishDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    private val englishShortDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    fun formatTime(epochMillis: Long): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        val zdt = instant.atZone(ZoneId.systemDefault())
        return timeFormatter.format(zdt)
    }

    fun formatDate(date: LocalDate): String = dateFormatter.format(date)

    fun formatFullDate(date: LocalDate): String = fullDateFormatter.format(date)

    fun formatWeekday(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE)

    fun formatMonthYear(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        return formatter.format(date)
    }

    /** English long date, e.g. "Jul 24, 2026" — used by the Echoes UI. */
    fun formatEnglishDate(date: LocalDate): String = englishDateFormatter.format(date)

    /** English short date, e.g. "Jul 24" — used by the Full Journal date rail. */
    fun formatEnglishShortDate(date: LocalDate): String = englishShortDateFormatter.format(date)

    /** Coarse "time ago" label, e.g. "now", "15m ago", "2h ago", "3d ago". */
    fun formatRelative(epochMillis: Long): String {
        val diff = System.currentTimeMillis() - epochMillis
        if (diff < 0) return "now"
        val minutes = diff / 60_000
        val hours = diff / 3_600_000
        val days = diff / 86_400_000
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> {
                val date = Instant.ofEpochMilli(epochMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                englishShortDateFormatter.format(date)
            }
        }
    }

    /** Playback-style duration, e.g. 45_000L -> "0:45", 83_000L -> "1:23". */
    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
