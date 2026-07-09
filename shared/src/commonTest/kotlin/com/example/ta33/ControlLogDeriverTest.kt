package com.example.ta33

import com.example.ta33.domain.log.ControlLogDeriver
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.ControlPointState
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.RunSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControlLogDeriverTest {

    private val routeId = "r1"
    private val runId = "run-1"

    private fun control(n: Int) = ControlPoint(
        id = "c$n", routeId = routeId, ordinal = n, name = "K$n", location = GeoPoint(50.0 + n, 16.0),
    )

    private fun run(startedAt: Long? = 1_000L, finishedAt: Long? = null) =
        RunSession(id = runId, routeId = routeId, participantId = "p1", startedAtMillis = startedAt, finishedAtMillis = finishedAt)

    private fun collected(controlId: String, at: Long) =
        CollectedControl(id = "cc-$controlId", runSessionId = runId, controlId = controlId, collectedAtMillis = at)

    @Test
    fun nothingCollected_firstActiveRestLocked() {
        val controls = (1..5).map { control(it) }
        val state = ControlLogDeriver.deriveLog(controls, emptyList(), run())

        assertEquals(
            listOf(
                ControlPointState.ACTIVE,
                ControlPointState.LOCKED,
                ControlPointState.LOCKED,
                ControlPointState.LOCKED,
                ControlPointState.LOCKED,
            ),
            state.entries.map { it.state },
        )
        assertEquals(0, state.collectedCount)
        assertEquals(5, state.totalCount)
        assertEquals("0 z 5", state.progressLabel)
        assertEquals("c1", state.nextControl?.id)
        assertFalse(state.isComplete)
        assertEquals(ControlPointState.LOCKED, state.finishState)
    }

    @Test
    fun partial_twoOfFive_states() {
        val controls = (1..5).map { control(it) }
        val col = listOf(collected("c1", 1_300L), collected("c2", 1_600L))
        val state = ControlLogDeriver.deriveLog(controls, col, run())

        assertEquals(
            listOf(
                ControlPointState.DONE,
                ControlPointState.DONE,
                ControlPointState.ACTIVE,
                ControlPointState.LOCKED,
                ControlPointState.LOCKED,
            ),
            state.entries.map { it.state },
        )
        assertEquals(2, state.collectedCount)
        assertEquals("2 z 5", state.progressLabel)
        assertEquals("c3", state.nextControl?.id)
        assertFalse(state.isComplete)
        assertEquals(ControlPointState.LOCKED, state.finishState)
    }

    @Test
    fun allCollected_runNotFinished_finishActive() {
        val controls = (1..5).map { control(it) }
        val col = (1..5).map { collected("c$it", 1_000L + it * 100) }
        val state = ControlLogDeriver.deriveLog(controls, col, run(finishedAt = null))

        assertTrue(state.entries.all { it.state == ControlPointState.DONE })
        assertEquals("5 z 5", state.progressLabel)
        assertNull(state.nextControl)
        assertTrue(state.isComplete)
        assertFalse(state.isRunFinished)
        assertEquals(ControlPointState.ACTIVE, state.finishState)
    }

    @Test
    fun allCollected_runFinished_finishTerminal() {
        val controls = (1..5).map { control(it) }
        val col = (1..5).map { collected("c$it", 1_000L + it * 100) }
        val state = ControlLogDeriver.deriveLog(controls, col, run(finishedAt = 9_000L))

        assertTrue(state.entries.all { it.state == ControlPointState.DONE })
        assertTrue(state.isComplete)
        assertTrue(state.isRunFinished)
        assertEquals(ControlPointState.FINISH, state.finishState)
    }

    @Test
    fun outOfOrder_c2CollectedC1Not_c1StaysActive() {
        val controls = (1..3).map { control(it) }
        val col = listOf(collected("c2", 1_500L))
        val state = ControlLogDeriver.deriveLog(controls, col, run())

        assertEquals(
            listOf(
                ControlPointState.ACTIVE, // c1 still next
                ControlPointState.DONE, // c2 collected
                ControlPointState.LOCKED,
            ),
            state.entries.map { it.state },
        )
        assertEquals("c1", state.nextControl?.id)
        assertEquals(1, state.collectedCount)
    }

    @Test
    fun emptyRoute_noCrash() {
        val state = ControlLogDeriver.deriveLog(emptyList(), emptyList(), run())

        assertEquals(emptyList(), state.entries)
        assertEquals("0 z 0", state.progressLabel)
        assertNull(state.nextControl)
        assertFalse(state.isComplete)
        assertEquals(ControlPointState.LOCKED, state.finishState)
    }

    @Test
    fun splitTime_computedWhenRunStarted_nullWhenNoRun() {
        val controls = listOf(control(1))
        val col = listOf(collected("c1", 1_000L + 120_000L))

        val withRun = ControlLogDeriver.deriveLog(controls, col, run(startedAt = 1_000L))
        assertEquals(120_000L, withRun.entries[0].splitMillis)

        val noRun = ControlLogDeriver.deriveLog(controls, col, null)
        assertNull(noRun.entries[0].splitMillis)
        assertEquals(1_000L + 120_000L, noRun.entries[0].collectedAtMillis)
    }

    @Test
    fun unsortedInput_sortedByOrdinal() {
        val controls = listOf(control(3), control(1), control(2))
        val state = ControlLogDeriver.deriveLog(controls, emptyList(), run())

        assertEquals(listOf("c1", "c2", "c3"), state.entries.map { it.control.id })
        assertEquals("c1", state.nextControl?.id)
        assertEquals(ControlPointState.ACTIVE, state.entries[0].state)
    }

    @Test
    fun duplicateCollectedRows_countedOnce() {
        val controls = (1..3).map { control(it) }
        val col = listOf(collected("c1", 1_100L), collected("c1", 1_200L))
        val state = ControlLogDeriver.deriveLog(controls, col, run())

        assertEquals(1, state.collectedCount)
        assertEquals(ControlPointState.DONE, state.entries[0].state)
        assertEquals(ControlPointState.ACTIVE, state.entries[1].state)
    }
}
