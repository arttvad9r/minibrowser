package com.artt.minibrowser.engine

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

internal fun formatDateValue(date: LocalDate): String = date.format(DATE_FORMAT)

internal fun formatTimeValue(time: LocalTime): String = time.format(TIME_FORMAT)

internal fun formatDateTimeLocal(date: LocalDate, time: LocalTime): String =
    "${formatDateValue(date)}T${formatTimeValue(time)}"

internal fun formatMonthValue(date: LocalDate): String = "%04d-%02d".format(date.year, date.monthValue)

internal fun formatIsoWeekValue(date: LocalDate): String {
    val fields = WeekFields.ISO
    return "%04d-W%02d".format(date.get(fields.weekBasedYear()), date.get(fields.weekOfWeekBasedYear()))
}
