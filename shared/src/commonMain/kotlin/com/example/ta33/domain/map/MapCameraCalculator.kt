package com.example.ta33.domain.map

import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeoPosition
import com.example.ta33.domain.model.MapCamera

object MapCameraCalculator {
    fun initialCamera(
        routePoints: List<GeoPoint>,
        live: GeoPosition?,
        fallbackFocus: GeoPoint,
    ): MapCamera {
        val bounds = RouteBounds.boundingBox(routePoints)
        val focus = live?.location ?: bounds?.center ?: fallbackFocus
        return MapCamera(bounds = bounds, focus = focus)
    }
}
