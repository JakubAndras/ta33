package com.example.ta33.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits Unit every periodMillis, first tick immediately (so elapsed updates at once). */
fun interface Ticker {
    fun ticks(periodMillis: Long): Flow<Unit>
}

class DefaultTicker : Ticker {
    override fun ticks(periodMillis: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(periodMillis)
        }
    }
}
