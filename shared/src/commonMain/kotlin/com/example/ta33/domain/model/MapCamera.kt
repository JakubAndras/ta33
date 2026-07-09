package com.example.ta33.domain.model

data class GeoBoundingBox(val southWest: GeoPoint, val northEast: GeoPoint) {
    val center: GeoPoint get() = GeoPoint(
        latitude = (southWest.latitude + northEast.latitude) / 2.0,
        longitude = (southWest.longitude + northEast.longitude) / 2.0,
    )
}

/** Initial camera target. Native fits `bounds` on first load, then may follow `focus`. */
data class MapCamera(
    val bounds: GeoBoundingBox?, // null when route has < 1 point
    val focus: GeoPoint, // live position if available, else bounds center, else fallback
)
