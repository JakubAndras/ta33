package com.example.ta33.presentation.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationSerializationTest {

    private val json = Json

    private inline fun <reified T : Destination> roundTrip(value: T) {
        val encoded = json.encodeToString<Destination>(value)
        val decoded = json.decodeFromString<Destination>(encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun main_roundTrips() = roundTrip(Destination.Main(TopLevelDestination.MAPA))

    @Test
    fun routeDetail_roundTrips() = roundTrip(Destination.RouteDetail("x"))

    @Test
    fun runActive_roundTrips() = roundTrip(Destination.RunActive("r"))

    @Test
    fun controlCollected_roundTrips() = roundTrip(Destination.ControlCollected("r", "c"))

    @Test
    fun preparation_roundTrips() = roundTrip(Destination.Preparation)
}
