package com.example.ta33

import com.example.ta33.core.UrlOpener
import com.example.ta33.domain.mapy.MapyCzUrlBuilder
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.RouteDetail
import com.example.ta33.domain.usecase.OpenRouteInMapyCzUseCase
import com.example.ta33.domain.usecase.OpenRouteResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeUrlOpener(private val result: Boolean) : UrlOpener {
    var lastUrl: String? = null
    var callCount = 0
    override fun open(url: String): Boolean {
        lastUrl = url
        callCount++
        return result
    }
}

class OpenRouteInMapyCzUseCaseTest {

    private fun control(ordinal: Int, lat: Double, lon: Double): ControlPoint =
        ControlPoint(
            id = "c$ordinal",
            routeId = "r1",
            ordinal = ordinal,
            name = "Control $ordinal",
            location = GeoPoint(latitude = lat, longitude = lon),
        )

    private fun routeDetail(controls: List<ControlPoint>): RouteDetail =
        RouteDetail(routeId = "r1", name = "Route", distanceKm = 1.0, controls = controls)

    @Test
    fun openerSucceeds_returnsOpenedWithBuiltUrl() {
        val fake = FakeUrlOpener(result = true)
        val useCase = OpenRouteInMapyCzUseCase(MapyCzUrlBuilder(), fake)
        val detail = routeDetail(
            listOf(
                control(1, lat = 50.0, lon = 14.0),
                control(2, lat = 50.2, lon = 14.2),
            ),
        )

        val result = useCase(detail)

        assertTrue(result is OpenRouteResult.Opened)
        assertEquals(fake.lastUrl, result.url)
        assertEquals(
            "https://mapy.com/fnc/v1/route?start=14.0,50.0&end=14.2,50.2&routeType=foot_hiking",
            result.url,
        )
        assertEquals(1, fake.callCount)
    }

    @Test
    fun openerFails_returnsNoAppAvailable() {
        val fake = FakeUrlOpener(result = false)
        val useCase = OpenRouteInMapyCzUseCase(MapyCzUrlBuilder(), fake)
        val detail = routeDetail(listOf(control(1, lat = 50.0, lon = 14.0)))

        val result = useCase(detail)

        assertTrue(result is OpenRouteResult.NoAppAvailable)
        assertEquals(fake.lastUrl, result.url)
        assertEquals(1, fake.callCount)
    }

    @Test
    fun emptyControls_returnsNoRoute_openerNeverCalled() {
        val fake = FakeUrlOpener(result = true)
        val useCase = OpenRouteInMapyCzUseCase(MapyCzUrlBuilder(), fake)
        val detail = routeDetail(emptyList())

        val result = useCase(detail)

        assertEquals(OpenRouteResult.NoRoute, result)
        assertEquals(0, fake.callCount)
    }
}
