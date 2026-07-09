package com.example.ta33

import com.example.ta33.domain.geo.BreadcrumbThrottle
import com.example.ta33.domain.model.BreadcrumbConfig
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeoPosition
import com.example.ta33.domain.model.Trackpoint
import com.example.ta33.domain.usecase.RecordBreadcrumbUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RecordBreadcrumbUseCaseTest {

    private val config = BreadcrumbConfig(minDistanceMeters = 10.0, minTimeMillis = 5_000, maxAccuracyMeters = 50.0)

    private fun pos(latDelta: Double = 0.0, accuracy: Double = 5.0, t: Long) =
        GeoPosition(GeoPoint(50.0 + latDelta, 16.0), accuracy, t)

    @Test
    fun keepsOnlyThrottledPoints_withGeneratedIds() = runTest {
        val stream = FakeLocationStream(
            listOf(
                pos(t = 0L),                                  // keep (first good fix) -> tp-0
                pos(latDelta = 0.0001, t = 1_000L),           // drop TOO_SOON
                pos(t = 6_000L),                              // drop TOO_CLOSE (same spot)
                pos(latDelta = 0.0001, accuracy = 100.0, t = 12_000L), // drop POOR_ACCURACY
                pos(latDelta = 0.0001, t = 13_000L),          // keep (~11 m, 13 s) -> tp-1
            ),
        )
        val repo = FakeTrackpointRepository()
        val useCase = RecordBreadcrumbUseCase(stream, repo, BreadcrumbThrottle(config), SeqIdGenerator("tp-"))

        val kept = mutableListOf<Trackpoint>()
        useCase.record("run-1").collect { kept += it }

        assertEquals(listOf("tp-0", "tp-1"), kept.map { it.id })
        val track = repo.getTrack("run-1")
        assertEquals(2, track.size)
        assertEquals(0L, track[0].timestampMillis)
        assertEquals(13_000L, track[1].timestampMillis)
    }

    @Test
    fun resume_seedsLastPoint_soNearbyFixIsDropped() = runTest {
        val stream = FakeLocationStream(
            listOf(pos(t = 6_000L)), // same spot as the seeded last point -> TOO_CLOSE despite the time gap
        )
        val repo = FakeTrackpointRepository()
        repo.seed(
            Trackpoint(
                id = "seed",
                runSessionId = "run-1",
                location = GeoPoint(50.0, 16.0),
                accuracyMeters = 5.0,
                timestampMillis = 0L,
            ),
        )
        val useCase = RecordBreadcrumbUseCase(stream, repo, BreadcrumbThrottle(config), SeqIdGenerator("tp-"))

        val kept = mutableListOf<Trackpoint>()
        useCase.record("run-1").collect { kept += it }

        assertEquals(emptyList(), kept.map { it.id }) // the nearby fix was dropped
        val track = repo.getTrack("run-1")
        assertEquals(1, track.size)
        assertEquals("seed", track[0].id)
    }
}
