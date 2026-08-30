package com.artt.minibrowser.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.temporal.WeekFields
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val TIME_INPUT_FORMAT = DateTimeFormatterBuilder()
    .appendPattern("HH:mm")
    .optionalStart().appendPattern(":ss")
    .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
    .optionalEnd().optionalEnd()
    .toFormatter(Locale.ROOT)

internal fun formatDateValue(date: LocalDate): String = date.format(DATE_FORMAT)

internal fun formatTimeValue(time: LocalTime): String = time.format(TIME_FORMAT)

internal fun formatDateTimeLocal(date: LocalDate, time: LocalTime): String =
    "${formatDateValue(date)}T${formatTimeValue(time)}"

internal fun formatMonthValue(date: LocalDate): String =
    "%04d-%02d".format(Locale.ROOT, date.year, date.monthValue)

internal fun formatIsoWeekValue(date: LocalDate): String {
    val fields = WeekFields.ISO
    return "%04d-W%02d".format(
        Locale.ROOT,
        date.get(fields.weekBasedYear()),
        date.get(fields.weekOfWeekBasedYear()),
    )
}

internal fun parseDateValue(value: String?): LocalDate? = runCatching { LocalDate.parse(value ?: "") }.getOrNull()

internal fun parseTimeValue(value: String?): LocalTime? = runCatching {
    LocalTime.parse(value ?: "", TIME_INPUT_FORMAT)
}.getOrNull()

internal fun parseDateTimeLocalValue(value: String?): LocalDateTime? = runCatching {
    LocalDateTime.parse(value ?: "")
}.getOrNull()

internal fun parseMonthValue(value: String?): LocalDate? = runCatching {
    YearMonth.parse(value ?: "").atDay(1)
}.getOrNull()

internal fun parseIsoWeekValue(value: String?): LocalDate? = runCatching {
    val match = Regex("^(\\d{4})-W(\\d{2})$").matchEntire(value ?: "") ?: return null
    val year = match.groupValues[1].toInt()
    val week = match.groupValues[2].toInt()
    if (week !in 1..53) return null
    val date = LocalDate.of(year, 1, 4)
        .with(WeekFields.ISO.weekBasedYear(), year.toLong())
        .with(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong())
        .with(WeekFields.ISO.dayOfWeek(), 1)
    if (formatIsoWeekValue(date) == value) date else null
}.getOrNull()
