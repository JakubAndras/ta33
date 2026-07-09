package com.example.ta33.data.dto

import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.Route

fun RouteDto.toDomain(): Pair<Route, List<ControlPoint>> {
    val mappedControls = controls.map { c ->
        ControlPoint(
            id = c.id,
            routeId = id,
            ordinal = c.ordinal,
            name = c.name,
            location = GeoPoint(c.lat, c.lon),
            radiusMeters = c.radiusMeters,
        )
    }
    return Route(id = id, name = name, distanceKm = distanceKm, controls = mappedControls) to mappedControls
}
