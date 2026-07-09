package com.example.ta33.core

import com.example.ta33.domain.model.CollectedControl   // FR-02 model
import com.example.ta33.domain.model.Split

/** PURE. Splits = collectedAt - start for each collected control, sorted by collectedAt.
 *  Empty until the run has started. Mirrors FR-04's splitMillis formula (kept decoupled from
 *  the deník deriver; the UI can join control names via FR-04 later). */
object SplitCalculator {
    fun splits(startedAtMillis: Long?, collected: List<CollectedControl>): List<Split> {
        if (startedAtMillis == null) return emptyList()
        return collected
            .sortedBy { it.collectedAtMillis }
            .map { c ->
                Split(
                    controlId = c.controlId,
                    collectedAtMillis = c.collectedAtMillis,
                    splitMillis = (c.collectedAtMillis - startedAtMillis).coerceAtLeast(0L),
                )
            }
    }
}
