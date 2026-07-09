package com.example.ta33.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StartDestinationResolverTest {

    private val resolver = StartDestinationResolver()

    @Test
    fun loading_returnsNull() {
        assertNull(resolver.resolve(AppReadiness.LOADING, activeRunId = null))
        assertNull(resolver.resolve(AppReadiness.LOADING, activeRunId = "r1"))
    }

    @Test
    fun notReady_returnsPreparation() {
        assertEquals(Destination.Preparation, resolver.resolve(AppReadiness.NOT_READY, activeRunId = null))
    }

    @Test
    fun preparing_returnsPreparation() {
        assertEquals(Destination.Preparation, resolver.resolve(AppReadiness.PREPARING, activeRunId = null))
    }

    @Test
    fun ready_noActiveRun_returnsMainDenik() {
        assertEquals(
            Destination.Main(TopLevelDestination.DENIK),
            resolver.resolve(AppReadiness.READY, activeRunId = null),
        )
    }

    @Test
    fun ready_activeRun_returnsRunActive() {
        assertEquals(
            Destination.RunActive("r1"),
            resolver.resolve(AppReadiness.READY, activeRunId = "r1"),
        )
    }
}
