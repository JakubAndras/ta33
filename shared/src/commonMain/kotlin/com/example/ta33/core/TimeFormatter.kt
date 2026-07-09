package com.example.ta33.core

/** PURE duration formatter. "MM:SS" under an hour, "H:MM:SS" at/over an hour. Locale-neutral. */
object TimeFormatter {
    fun format(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        fun two(v: Long) = v.toString().padStart(2, '0')
        return if (hours > 0L) "$hours:${two(minutes)}:${two(seconds)}" else "${two(minutes)}:${two(seconds)}"
    }
}
