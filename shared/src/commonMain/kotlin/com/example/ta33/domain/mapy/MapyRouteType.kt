package com.example.ta33.domain.mapy

/** mapy.cz `routeType` values (developer.mapy.com). Default for TA33 is on-foot hiking. */
enum class MapyRouteType(val apiValue: String) {
    FOOT_HIKING("foot_hiking"),
    FOOT_FAST("foot_fast"),
    BIKE_MOUNTAIN("bike_mountain"),
    BIKE_ROAD("bike_road"),
    CAR_FAST("car_fast"),
    CAR_FAST_TRAFFIC("car_fast_traffic"),
    CAR_SHORT("car_short"),
}
