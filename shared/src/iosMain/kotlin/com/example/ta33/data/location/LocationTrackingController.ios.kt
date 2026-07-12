package com.example.ta33.data.location

import platform.CoreLocation.CLLocationManager

/** Enables CoreLocation background updates (requires UIBackgroundModes = [location] in Info.plist).
 *  ⚠️ FIELD-TEST REQUIRED ("ověřit v terénu, baterie") - validate on real devices over a long route. */
class IosLocationTrackingController : LocationTrackingController {

    private val manager = CLLocationManager()
    private var tracking = false

    override fun start() {
        manager.allowsBackgroundLocationUpdates = true
        manager.pausesLocationUpdatesAutomatically = false
        tracking = true
    }

    override fun stop() {
        manager.allowsBackgroundLocationUpdates = false
        tracking = false
    }

    override val isTracking: Boolean get() = tracking
}
