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
