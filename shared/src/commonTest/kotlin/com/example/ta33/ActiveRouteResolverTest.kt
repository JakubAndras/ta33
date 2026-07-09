package com.example.ta33

import com.example.ta33.domain.route.ActiveRouteResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveRouteResolverTest {

    @Test
    fun run_wins_overSelectionAndRoutes() {
        val result = ActiveRouteResolver.resolve(
            activeRunRouteId = "run-route",
            selectedRouteId = "sel",
            availableRouteIds = listOf("run-route", "sel", "other"),
        )
        assertEquals("run-route", result)
    }

    @Test
    fun selection_usedWhenAvailable() {
        val result = ActiveRouteResolver.resolve(
            activeRunRouteId = null,
            selectedRouteId = "r2",
            availableRouteIds = listOf("r1", "r2"),
        )
        assertEquals("r2", result)
    }

    @Test
    fun staleSelection_ignored_fallsBackToSoleRoute() {
        val result = ActiveRouteResolver.resolve(
            activeRunRouteId = null,
            selectedRouteId = "gone",
            availableRouteIds = listOf("only"),
        )
        assertEquals("only", result)
    }

    @Test
    fun staleSelection_ignored_ambiguousNull() {
        val result = ActiveRouteResolver.resolve(
            activeRunRouteId = null,
            selectedRouteId = "gone",
            availableRouteIds = listOf("r1", "r2"),
        )
        assertNull(result)
    }

    @Test
    fun singleRoute_autoSelected_whenNoRunNoSelection() {
        val result = ActiveRouteResolver.resolve(
            activeRunRouteId = null,
            selectedRouteId = null,
            availableRouteIds = listOf("only"),
        )
        assertEquals("only", result)
    }

    @Test
    fun twoRoutes_noRunNoSelection_null() {
        val result = ActiveRouteResolver.resolve(
            activeRunRouteId = null,
            selectedRouteId = null,
            availableRouteIds = listOf("r1", "r2"),
        )
        assertNull(result)
    }
}
