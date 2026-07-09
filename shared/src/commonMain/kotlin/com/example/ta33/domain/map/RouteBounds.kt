package com.example.ta33.domain.map

import com.example.ta33.domain.model.GeoBoundingBox
import com.example.ta33.domain.model.GeoPoint

object RouteBounds {
    /** null for empty input; degenerate (SW==NE) box for a single point. */
    fun boundingBox(points: List<GeoPoint>): GeoBoundingBox? {
        if (points.isEmpty()) return null
        var minLat = points.first().latitude
        var maxLat = minLat
        var minLon = points.first().longitude
        var maxLon = minLon
        for (p in points) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }
        return GeoBoundingBox(GeoPoint(minLat, minLon), GeoPoint(maxLat, maxLon))
    }
}
