package com.example.ta33.ui.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** Epoch millis → lokální "HH:mm" (např. 08:14). */
fun formatClock(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    CLOCK.format(Instant.ofEpochMilli(millis).atZone(zone))
