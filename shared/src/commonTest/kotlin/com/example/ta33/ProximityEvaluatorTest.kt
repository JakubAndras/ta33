package com.example.ta33

import com.example.ta33.domain.geo.ProximityEvaluator
import com.example.ta33.domain.geo.ProximityEvaluation
import com.example.ta33.domain.geo.ProximityState
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeofenceConfig
import com.example.ta33.domain.model.GeoPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProximityEvaluatorTest {

    // Reference point (Adršpach area); ~111_320 m per degree latitude.
    private val base = GeoPoint(50.6100, 16.1100)

    private fun control(id: String, lat: Double = 50.6100, lon: Double = 16.1100, radius: Double = 50.0) =
        ControlPoint(id = id, routeId = "r1", ordinal = 1, name = id, location = GeoPoint(lat, lon), radius)

    // latDelta -> metres north: delta * 111_320.
    private fun pos(latDelta: Double = 0.0, accuracy: Double = 5.0) =
        GeoPosition(GeoPoint(50.6100 + latDelta, 16.1100), accuracyMeters = accuracy, timestampMillis = 0L)

    private fun ProximityEvaluator.foldFixes(
        count: Int,
        position: GeoPosition,
        controls: List<ControlPoint>,
        collected: Set<String> = emptySet(),
        start: ProximityState = ProximityState(),
    ): ProximityEvaluation {
        var state = start
        var last = ProximityEvaluation(state, null)
        repeat(count) {
            last = evaluate(state, position, controls, collected)
            state = last.state
        }
        return last
    }

    @Test
    fun inRange_isDebouncedBeforeFirstOffer() {
        val evaluator = ProximityEvaluator(GeofenceConfig(minConsecutiveFixes = 2))
        val controls = listOf(control("c1"))

        val first = evaluator.evaluate(ProximityState(), pos(latDelta = 0.00027), controls, emptySet())
        assertNull(first.candidate)
        assertEquals(1, first.state.pendingStreak)
        assertEquals("c1", first.state.pendingControlId)

        val second = evaluator.evaluate(first.state, pos(latDelta = 0.00027), controls, emptySet())
        assertEquals("c1", second.candidate?.control?.id)
        assertEquals("c1", second.state.offeredControlId)
    }

    @Test
    fun outOfRange_yieldsNoCandidate() {
        val evaluator = ProximityEvaluator()
        val result = evaluator.foldFixes(2, pos(latDelta = 0.00135), listOf(control("c1")))
        assertNull(result.candidate)
        assertEquals(ProximityState(), result.state)
    }

    @Test
    fun poorAccuracy_doesNotPromoteNewCandidate() {
        val evaluator = ProximityEvaluator(GeofenceConfig(maxAccuracyMeters = 50.0))
        // Inside the radius but the fix is untrustworthy; with no prior offer nothing is promoted.
        val result = evaluator.foldFixes(3, pos(latDelta = 0.00027, accuracy = 100.0), listOf(control("c1")))
        assertNull(result.candidate)
    }

    @Test
    fun poorAccuracy_holdsAnAlreadyOfferedCandidate() {
        val evaluator = ProximityEvaluator(GeofenceConfig(maxAccuracyMeters = 50.0, exitMarginMeters = 20.0))
        val controls = listOf(control("c1"))
        // Held offer; fix ~60 m out (within radius+exitMargin=70) with poor accuracy still keeps offering.
        val result = evaluator.evaluate(
            previous = ProximityState(offeredControlId = "c1"),
            position = pos(latDelta = 0.00054, accuracy = 100.0),
            controls = controls,
            collectedIds = emptySet(),
        )
        assertEquals("c1", result.candidate?.control?.id)
    }

    @Test
    fun exitHysteresis_staysOfferedWithinMarginDropsBeyond() {
        val evaluator = ProximityEvaluator(GeofenceConfig(exitMarginMeters = 20.0))
        val controls = listOf(control("c1"))

        // ~60 m out: within radius (50) + exitMargin (20) => still offered.
        val within = evaluator.evaluate(ProximityState(offeredControlId = "c1"), pos(latDelta = 0.00054), controls, emptySet())
        assertEquals("c1", within.candidate?.control?.id)

        // ~80 m out: beyond radius + exitMargin => dropped.
        val beyond = evaluator.evaluate(ProximityState(offeredControlId = "c1"), pos(latDelta = 0.00072), controls, emptySet())
        assertNull(beyond.candidate)
    }

    @Test
    fun boundaryJitter_doesNotFlicker() {
        val evaluator = ProximityEvaluator(GeofenceConfig(exitMarginMeters = 20.0))
        val controls = listOf(control("c1"))
        var state = ProximityState(offeredControlId = "c1")
        // Oscillate a few metres across the radius while held; must stay the same offer.
        listOf(0.0004, 0.0005, 0.00045, 0.00054).forEach { d ->
            val e = evaluator.evaluate(state, pos(latDelta = d), controls, emptySet())
            assertEquals("c1", e.candidate?.control?.id, "flickered at delta $d")
            state = e.state
        }
    }

    @Test
    fun anyOrder_offersReachedControlRegardlessOfOrdinal() {
        val evaluator = ProximityEvaluator(GeofenceConfig(minConsecutiveFixes = 1))
        val controls = listOf(
            control("c1", lat = 50.6100),                 // ordinal-next, far from the fix
            control("c2", lat = 50.6200),                 // far
            control("c3", lat = 50.6000),                 // the one we physically reach
        )
        // Fix ~30 m north of c3.
        val position = GeoPosition(GeoPoint(50.6000 + 0.00027, 16.1100), 5.0, 0L)
        val result = evaluator.evaluate(ProximityState(), position, controls, emptySet())
        assertEquals("c3", result.candidate?.control?.id)
    }

    @Test
    fun alreadyCollected_isNeverOffered() {
        val evaluator = ProximityEvaluator(GeofenceConfig(minConsecutiveFixes = 1))
        val controls = listOf(control("c1"))
        val result = evaluator.evaluate(ProximityState(), pos(latDelta = 0.00027), controls, setOf("c1"))
        assertNull(result.candidate)
    }

    @Test
    fun allCollected_yieldsNoCandidate() {
        val evaluator = ProximityEvaluator(GeofenceConfig(minConsecutiveFixes = 1))
        val controls = listOf(control("c1"), control("c2", lat = 50.6000))
        val result = evaluator.foldFixes(3, pos(latDelta = 0.00027), controls, collected = setOf("c1", "c2"))
        assertNull(result.candidate)
    }

    @Test
    fun overlappingRadii_nearestWins() {
        val evaluator = ProximityEvaluator(GeofenceConfig(minConsecutiveFixes = 1))
        val controls = listOf(
            control("c1", lat = 50.6100),           // base
            control("c2", lat = 50.6100 + 0.0002),  // ~22 m north
        )
        // Fix ~5.6 m north of c1 => closer to c1 than c2, both within radius.
        val result = evaluator.evaluate(ProximityState(), pos(latDelta = 0.00005), controls, emptySet())
        assertEquals("c1", result.candidate?.control?.id)
    }
}
