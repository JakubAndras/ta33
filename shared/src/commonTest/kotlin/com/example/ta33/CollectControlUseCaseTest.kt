package com.example.ta33

import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.usecase.CollectControlUseCase
import com.example.ta33.domain.usecase.CollectResult
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CollectControlUseCaseTest {

    private lateinit var runRepo: FakeRunRepository
    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var time: MutableTimeProvider
    private lateinit var useCase: CollectControlUseCase

    private val controlLocation = GeoPoint(50.6100, 16.1100)
    private val runId = "run-1"
    private val controlId = "c1"

    @BeforeTest
    fun setup() = runTest {
        runRepo = FakeRunRepository()
        routeRepo = FakeRouteRepository()
        time = MutableTimeProvider(now = 1_000L)
        useCase = CollectControlUseCase(runRepo, routeRepo, time, SeqIdGenerator("cc-"))
        routeRepo.upsertRoute(
            route = com.example.ta33.domain.model.Route(id = "r1", name = "A", distanceKm = 33.0),
            controls = listOf(
                ControlPoint(id = controlId, routeId = "r1", ordinal = 1, name = "K1", location = controlLocation),
            ),
        )
    }

    @Test
    fun firstCollect_returnsCollected_withTimestamp() = runTest {
        time.now = 1_500L
        val result = useCase(runId, controlId)
        val collected = assertIs<CollectResult.Collected>(result)
        assertEquals(1_500L, collected.control.collectedAtMillis)
    }

    @Test
    fun secondCollect_isNoOp_returnsAlreadyCollected() = runTest {
        useCase(runId, controlId)
        val second = useCase(runId, controlId)
        assertEquals(CollectResult.AlreadyCollected, second)
    }

    @Test
    fun outOfRangeLocation_isNotPersisted() = runTest {
        val far = GeoPoint(50.6200, 16.1300) // well beyond 50 m
        val result = useCase(runId, controlId, far)
        assertEquals(CollectResult.OutOfRange, result)
        // no persistence: a subsequent in-range collect succeeds as the first row
        val second = useCase(runId, controlId)
        assertTrue(second is CollectResult.Collected)
    }

    @Test
    fun unknownControl_returnsUnknownControl() = runTest {
        val result = useCase(runId, "nope")
        assertEquals(CollectResult.UnknownControl, result)
    }
}
