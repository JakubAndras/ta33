package com.example.ta33.data.location

/** Keeps location recording alive with the screen off / app backgrounded.
 *  ⚠️ FIELD-TEST REQUIRED ("ověřit v terénu, baterie"): OEM battery-savers (esp. Android)
 *  can still kill this - validate on real devices over a multi-hour route. */
interface LocationTrackingController {
    fun start()   // Android: start foreground service; iOS: enable background location updates
    fun stop()
    val isTracking: Boolean
}
