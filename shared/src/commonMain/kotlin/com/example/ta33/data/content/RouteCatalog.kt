package com.example.ta33.data.content

import com.example.ta33.domain.model.ElevationProfile
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.RouteItinerary
import com.example.ta33.domain.model.RouteWaypoint
import com.example.ta33.domain.model.TrailMark
import com.example.ta33.domain.model.TurnDirection
import com.example.ta33.domain.model.WaypointKind

/**
 * Static, in-memory route content (mock) faithfully ported from the design source
 * `ui_kits/ta33-app/TrasaData.jsx` (TA33 + TA50 propozice: full itinerary rows with trail marks,
 * turn directions, km positions, and the elevation profile).
 *
 * This is display content for the redesigned screens; it is intentionally separate from the
 * run/geofence [com.example.ta33.domain.model.Route]/[com.example.ta33.domain.model.ControlPoint]
 * data. CONTROL waypoints carry approximate GeoPoints (Adršpach/Teplice region) so the DevSeed can
 * derive ControlPoints for FR-08. Real coords + a real FR-11 content pipeline are follow-ups.
 */
object RouteCatalog {

    const val TA33_ROUTE_ID = "dev-ta33"
    const val TA50_ROUTE_ID = "dev-ta50"

    val itineraries: List<RouteItinerary> by lazy { listOf(ta33, ta50) }

    fun byId(routeId: String): RouteItinerary? = itineraries.firstOrNull { it.routeId == routeId }

    // --- TA33 -----------------------------------------------------------------------------------

    private val ta33: RouteItinerary by lazy {
        RouteItinerary(
            routeId = TA33_ROUTE_ID,
            letter = "A",
            name = "Teplicko-Adršpašská 33",
            shortId = "TA33",
            distanceKm = 33.2,
            ascentMeters = 740,
            descentMeters = 740,
            controlsCount = 5,
            elevation = ElevationProfile(
                pointsNormalized = listOf(
                    0.05, 0.12, 0.35, 0.62, 0.9, 0.78, 0.55, 0.42, 0.6, 0.48, 0.3, 0.38, 0.22, 0.15, 0.08,
                ),
                lowMeters = 462,
                highMeters = 727,
                ascentMeters = 740,
                descentMeters = 740,
                kmTotal = 33.2,
                tickKm = listOf(8, 16, 24),
            ),
            waypoints = numberControls(
                listOf(
                    wp("Koupaliště", 0.0, WaypointKind.START, TurnDirection.UP, TrailMark.MODRA),
                    wp("Teplice – Nádržní ul.", 1.9, WaypointKind.WAYPOINT, TurnDirection.RIGHT, TrailMark.ZLUTA),
                    wp("Zámek Bischofstein", 5.9, WaypointKind.CONTROL, TurnDirection.UP, TrailMark.ZLUTA,
                        location = GeoPoint(50.6015, 16.1490)),
                    wp("Pod zvětralým vrchem", 9.3, WaypointKind.WAYPOINT, TurnDirection.RIGHT, TrailMark.ZELENA),
                    wp("Záboř – bývalá osada", 9.7, WaypointKind.WAYPOINT, TurnDirection.UP, TrailMark.MODRA),
                    wp("Vlčí rokle – Pod 7 sch.", 12.4, WaypointKind.CONTROL, TurnDirection.LEFT, TrailMark.ZLUTA,
                        location = GeoPoint(50.6120, 16.1330)),
                    wp("Adrš. skály – Trpaslík", 14.7, WaypointKind.WAYPOINT, TurnDirection.LEFT, TrailMark.ZELENA),
                    wp("Adrš. skály – Ozvěna", 15.7, WaypointKind.WAYPOINT, TurnDirection.LEFT, TrailMark.VLASTNI),
                    wp("Adršpach – Tošovák", 16.6, WaypointKind.WAYPOINT, TurnDirection.RIGHT, TrailMark.CERVENA),
                    wp("Adršpach – Zámek", 16.9, WaypointKind.CONTROL, TurnDirection.LEFT_UP, TrailMark.CERVENA,
                        location = GeoPoint(50.6180, 16.1120)),
                    wp("Adršpach parkoviště", 17.7, WaypointKind.WAYPOINT, TurnDirection.LEFT, TrailMark.ZLUTA),
                    wp("Zdoňov – zastávka bus", 20.3, WaypointKind.CONTROL, TurnDirection.LEFT, TrailMark.VLASTNI,
                        location = GeoPoint(50.6250, 16.1000)),
                    wp("Zdoňov – horní zast.", 21.5, WaypointKind.WAYPOINT, TurnDirection.LEFT, TrailMark.MODRA),
                    wp("Zdoňov křižovatka", 21.6, WaypointKind.WAYPOINT, TurnDirection.UP, TrailMark.CYKLO,
                        markNumber = "4036"),
                    wp("Buková hora", 26.7, WaypointKind.WAYPOINT, TurnDirection.RIGHT, TrailMark.CYKLO,
                        markNumber = "4036"),
                    wp("Na kraji lesa", 28.0, WaypointKind.WAYPOINT, TurnDirection.LEFT, TrailMark.VLASTNI),
                    wp("U Nového dvora", 30.5, WaypointKind.CONTROL, TurnDirection.RIGHT, TrailMark.CERVENA,
                        location = GeoPoint(50.6000, 16.1550)),
                    wp("Teplice náměstí", 32.7, WaypointKind.WAYPOINT, TurnDirection.RIGHT, TrailMark.MODRA),
                    wp("Koupaliště", 33.2, WaypointKind.FINISH, TurnDirection.UP, TrailMark.MODRA),
                ),
            ),
        )
    }

    // --- TA50 -----------------------------------------------------------------------------------

    private val ta50: RouteItinerary by lazy {
        RouteItinerary(
            routeId = TA50_ROUTE_ID,
            letter = "B",
            name = "Teplicko-Adršpašská 50",
            shortId = "TA50",
            distanceKm = 49.6,
            ascentMeters = 1085,
            descentMeters = 1085,
            controlsCount = 6,
            elevation = ElevationProfile(
                pointsNormalized = listOf(
                    0.05, 0.1, 0.3, 0.55, 0.85, 0.72, 0.5, 0.66, 0.58, 0.4, 0.6, 0.75, 0.62, 0.5, 0.7, 0.55,
                    0.35, 0.2, 0.1,
                ),
                lowMeters = 452,
                highMeters = 727,
                ascentMeters = 1085,
                descentMeters = 1085,
                kmTotal = 49.6,
                tickKm = listOf(15, 30, 45),
            ),
            waypoints = numberControls(
                listOf(
                    wp("Koupaliště", 0.0, WaypointKind.START, TurnDirection.UP, TrailMark.MODRA),
                    wp("Teplice – Nádržní ul.", 1.9, WaypointKind.WAYPOINT, TurnDirection.RIGHT, TrailMark.ZLUTA),
                    wp("Zámek Bischofstein", 5.9, WaypointKind.CONTROL, TurnDirection.UP, TrailMark.ZLUTA,
                        location = GeoPoint(50.6015, 16.1490)),
                    wp("Pod zvětralým vrchem", 9.3, WaypointKind.WAYPOINT, TurnDirection.RIGHT, TrailMark.ZELENA),
                    wp("Záboř – bývalá osada", 9.7, WaypointKind.WAYPOINT, TurnDirection.UP, TrailMark.MODRA),
                    wp("Vlčí rokle – Pod 7 sch.", 12.4, WaypointKind.CONTROL, TurnDirection.LEFT, TrailMark.ZLUTA,
                        location = GeoPoint(50.6120, 16.1330)),
                    wp("Adrš. skály – Trpaslík", 14.7, WaypointKind.WAYPOINT, TurnDirection.LEFT, TrailMark.ZELENA),
                    wp("Adrš. skály – Ozvěna", 15.7, WaypointKind.WAYPOINT, TurnDirection.LEFT, TrailMark.VLASTNI),
                    wp("Adršpach – Tošovák", 16.6, WaypointKind.WAYPOINT, TurnDirection.LEFT, TrailMark.CYKLO,
                        markNumber = "4036"),
                    wp("H. Adršpach – aut. zast.", 18.6, WaypointKind.CONTROL, TurnDirection.LEFT, TrailMark.CYKLO,
                        markNumber = "4036", location = GeoPoint(50.6220, 16.1050)),
                    wp("Libná rybník, Smír. kř.", 22.8, WaypointKind.WAYPOINT, TurnDirection.RIGHT, TrailMark.MODRA),
                    wp("Zdoňov – křiž. za mostem", 25.8, WaypointKind.CONTROL, TurnDirection.STRAIGHT, TrailMark.CYKLO,
                        markNumber = "4020", location = GeoPoint(50.6300, 16.1150)),
                    wp("Státní hranice", 27.3, WaypointKind.WAYPOINT, TurnDirection.UP, TrailMark.CYKLO,
                        markNumber = "4020"),
                    wp("Přístřešek pod lesem", 28.0, WaypointKind.WAYPOINT, TurnDirection.RIGHT, TrailMark.CYKLO,
                        markNumber = "Z.M."),
                    wp("Odbočka u kravína", 36.0, WaypointKind.CONTROL, TurnDirection.RIGHT, TrailMark.VLASTNI,
                        location = GeoPoint(50.6100, 16.0900)),
                    wp("Po hranici", 38.0, WaypointKind.WAYPOINT, TurnDirection.UP, TrailMark.VLASTNI),
                    wp("Buková hora", 39.0, WaypointKind.WAYPOINT, TurnDirection.LEFT, TrailMark.ZLUTA),
                    wp("Vernéřovická studánka", 41.0, WaypointKind.WAYPOINT, TurnDirection.LEFT, TrailMark.CERVENA),
                    wp("Vernéřovice škola", 43.4, WaypointKind.WAYPOINT, TurnDirection.LEFT, TrailMark.MODRA),
                    wp("Kř. za farmou Bošina", 43.8, WaypointKind.WAYPOINT, TurnDirection.RIGHT, TrailMark.VLASTNI),
                    wp("Křižovatka v lese", 46.3, WaypointKind.WAYPOINT, TurnDirection.LEFT_RIGHT, TrailMark.CERVENA),
                    wp("Nové Dvory – odpočiv.", 46.9, WaypointKind.CONTROL, TurnDirection.UP, TrailMark.CERVENA,
                        location = GeoPoint(50.6000, 16.1500)),
                    wp("Teplice – kino", 49.1, WaypointKind.WAYPOINT, TurnDirection.RIGHT, TrailMark.MODRA),
                    wp("Koupaliště", 49.6, WaypointKind.FINISH, TurnDirection.UP, TrailMark.MODRA),
                ),
            ),
        )
    }

    // --- helpers --------------------------------------------------------------------------------

    /** Builds a waypoint with index = -1; [numberControls] assigns index + controlOrdinal. */
    private fun wp(
        name: String,
        km: Double,
        kind: WaypointKind,
        direction: TurnDirection,
        mark: TrailMark,
        markNumber: String? = null,
        location: GeoPoint? = null,
    ): RouteWaypoint = RouteWaypoint(
        index = -1,
        name = name,
        km = km,
        kind = kind,
        direction = direction,
        mark = mark,
        markNumber = markNumber,
        location = location,
    )

    /** Assigns 0-based index to every waypoint and 1..N controlOrdinal to CONTROL waypoints. */
    private fun numberControls(rows: List<RouteWaypoint>): List<RouteWaypoint> {
        var control = 0
        return rows.mapIndexed { index, row ->
            val ordinal = if (row.kind == WaypointKind.CONTROL) ++control else null
            row.copy(index = index, controlOrdinal = ordinal)
        }
    }
}
