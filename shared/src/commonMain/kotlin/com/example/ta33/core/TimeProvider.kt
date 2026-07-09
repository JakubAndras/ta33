package com.example.ta33.core

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun interface TimeProvider {
    fun nowMillis(): Long
}

@OptIn(ExperimentalTime::class)
class SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
