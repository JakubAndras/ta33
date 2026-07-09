package com.example.ta33

import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.Trackpoint
import com.example.ta33.domain.usecase.ObserveTrackUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveTrackUseCaseTest {

    private fun tp(id: String, t: Long) =
        Trackpoint(id = id, runSessionId = "run-1", location = GeoPoint(50.0, 16.0), accuracyMeters = 5.0, timestampMillis = t)

    @Test
    fun emitsTrackOrderedByTime() = runTest {
        val repo = FakeTrackpointRepository()
        repo.seed(tp("b", 2_000L), tp("a", 1_000L))
        val observe = ObserveTrackUseCase(repo)

        val track = observe("run-1").first()
        assertEquals(listOf("a", "b"), track.map { it.id })
    }

    @Test
    fun filtersByRun() = runTest {
        val repo = FakeTrackpointRepository()
        repo.seed(tp("a", 1_000L))
        repo.append(Trackpoint("x", "run-2", GeoPoint(50.0, 16.0), 5.0, 500L))
        val observe = ObserveTrackUseCase(repo)

        assertEquals(listOf("a"), observe("run-1").first().map { it.id })
    }
}
