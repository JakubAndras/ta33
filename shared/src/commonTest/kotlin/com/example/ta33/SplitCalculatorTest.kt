package com.example.ta33

import com.example.ta33.core.SplitCalculator
import com.example.ta33.domain.model.CollectedControl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitCalculatorTest {

    private fun collected(controlId: String, at: Long) =
        CollectedControl(id = "cc-$controlId", runSessionId = "run-1", controlId = controlId, collectedAtMillis = at)

    @Test
    fun notStarted_isEmpty() {
        assertTrue(SplitCalculator.splits(startedAtMillis = null, collected = listOf(collected("c1", 2_000L))).isEmpty())
    }

    @Test
    fun sortedByCollectedAt_withDifferenceFromStart() {
        val start = 1_000L
        val unsorted = listOf(
            collected("c3", 4_000L),
            collected("c1", 2_000L),
            collected("c2", 3_000L),
        )
        val splits = SplitCalculator.splits(start, unsorted)
        assertEquals(listOf("c1", "c2", "c3"), splits.map { it.controlId })
        assertEquals(listOf(1_000L, 2_000L, 3_000L), splits.map { it.splitMillis })
        assertEquals(listOf(2_000L, 3_000L, 4_000L), splits.map { it.collectedAtMillis })
    }

    @Test
    fun clockSkew_coercedToZero() {
        val splits = SplitCalculator.splits(startedAtMillis = 5_000L, collected = listOf(collected("c1", 1_000L)))
        assertEquals(0L, splits.single().splitMillis)
    }
}
