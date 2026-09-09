package com.ferhat.commutetracker.ui

import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/** e.g. "1h 07m 03s" while running, "1h 07m" once finished. */
fun formatDuration(millis: Long, withSeconds: Boolean = false): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) append("${hours}h ")
        append(String.format("%02dm", minutes))
        if (withSeconds) append(String.format(" %02ds", seconds))
    }.trim()
}

fun formatDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

fun formatTime(epochMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))

fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

/** "1.2 km" / "740 m" */
fun formatDistance(meters: Double): String =
    if (meters >= 1000) String.format("%.1f km", meters / 1000.0)
    else "${meters.toInt()} m"

/** "2 h ago", "5 min ago", "just now" */
fun formatRelative(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diff = (nowMillis - epochMillis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours h ago"
        days < 7 -> "$days d ago"
        else -> formatDate(epochMillis)
    }
}
