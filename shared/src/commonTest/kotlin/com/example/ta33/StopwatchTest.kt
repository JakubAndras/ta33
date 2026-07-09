package com.example.ta33

import com.example.ta33.core.Stopwatch
import kotlin.test.Test
import kotlin.test.assertEquals

class StopwatchTest {

    @Test
    fun beforeStart_isZero() {
        assertEquals(0L, Stopwatch.elapsed(nowMillis = 5_000L, startedAtMillis = null, finishedAtMillis = null))
    }

    @Test
    fun running_isNowMinusStart() {
        assertEquals(3_000L, Stopwatch.elapsed(nowMillis = 4_000L, startedAtMillis = 1_000L, finishedAtMillis = null))
    }

    @Test
    fun finished_isFrozen_regardlessOfNow() {
        assertEquals(4_000L, Stopwatch.elapsed(nowMillis = 99_000L, startedAtMillis = 1_000L, finishedAtMillis = 5_000L))
    }

    @Test
    fun nowBeforeStart_coercedToZero() {
        assertEquals(0L, Stopwatch.elapsed(nowMillis = 500L, startedAtMillis = 1_000L, finishedAtMillis = null))
    }

    @Test
    fun finishBeforeStart_coercedToZero() {
        assertEquals(0L, Stopwatch.elapsed(nowMillis = 9_000L, startedAtMillis = 1_000L, finishedAtMillis = 500L))
    }
}
