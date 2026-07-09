package com.example.ta33.core

/** PURE. Elapsed derives from wall-clock time — never accumulated, so it cannot drift and
 *  is correct after app restart (start timestamp is durable in FR-02 RunSession). */
object Stopwatch {
    fun elapsed(nowMillis: Long, startedAtMillis: Long?, finishedAtMillis: Long?): Long = when {
        startedAtMillis == null      -> 0L
        finishedAtMillis != null     -> (finishedAtMillis - startedAtMillis).coerceAtLeast(0L) // frozen
        else                         -> (nowMillis - startedAtMillis).coerceAtLeast(0L)         // running
    }
}
