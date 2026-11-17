package com.alaslente.app

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val ALAS_HOURS = mapOf(
    1 to "Alas-Una",
    2 to "Alas-Dos",
    3 to "Alas-Tres",
    4 to "Alas-Kwatro",
    5 to "Alas-Singko",
    6 to "Alas-Sais",
    7 to "Alas-Syete",
    8 to "Alas-Otso",
    9 to "Alas-Nuwebe",
    10 to "Alas-Diyes",
    11 to "Alas-Onse",
    12 to "Alas-Dose"
)

data class TimeMetadata(
    val alasLabel: String,
    val formatted12h: String,
    val period: String
)

fun getTimeMetadata(time24: String): TimeMetadata {
    val parts = time24.ifBlank { "00:00" }.split(":")
    val hour24 = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    val ampm = if (hour24 >= 12) "PM" else "AM"

    val (period, suffix) = when {
        hour24 in 0..5 -> "madaling-araw" to "ng Madaling Araw"
        hour24 in 6..11 -> "umaga" to "ng Umaga"
        hour24 in 12..17 -> "hapon" to (if (hour24 == 12) "ng Tanghali" else "ng Hapon")
        else -> "gabi" to "ng Gabi"
    }

    val minStr = minute.toString().padStart(2, '0')
    return TimeMetadata(
        alasLabel = "${ALAS_HOURS[hour12]} $suffix",
        formatted12h = "$hour12:$minStr $ampm",
        period = period
    )
}

fun getManilaNow24(): String {
    val now = ZonedDateTime.now(ZoneId.of("Asia/Manila"))
    return now.format(DateTimeFormatter.ofPattern("HH:mm"))
}

fun getManilaTodayMMDDYYYY(): String {
    val now = ZonedDateTime.now(ZoneId.of("Asia/Manila"))
    return now.format(DateTimeFormatter.ofPattern("MM-dd-yyyy"))
}

fun getLiveManilaClockText(): String {
    val time24 = getManilaNow24()
    val meta = getTimeMetadata(time24)
    return "${meta.alasLabel.uppercase()} • ${meta.formatted12h} PHT"
}