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
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.CHINESE)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEE", Locale.CHINESE)

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
        val formatter = DateTimeFormatter.ofPattern("yyyy年 M月")
        return formatter.format(date)
    }
}
