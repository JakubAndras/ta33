package com.example.ta33.data.location

import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeoPosition
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** CoreLocation based GPS acquisition wrapping CLLocationManager updates in a cold Flow. */
@OptIn(ExperimentalForeignApi::class)
class IosLocationProvider : LocationProvider {

    override fun positionUpdates(intervalMillis: Long): Flow<GeoPosition> = callbackFlow {
        val manager = CLLocationManager()
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                didUpdateLocations.filterIsInstance<CLLocation>().forEach { location ->
                    val (lat, lon) = location.coordinate.useContents { latitude to longitude }
                    trySend(
                        GeoPosition(
                            location = GeoPoint(lat, lon),
                            accuracyMeters = location.horizontalAccuracy,
                            timestampMillis = (location.timestamp.timeIntervalSince1970 * 1000).toLong(),
                        ),
                    )
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                // Location services disabled / permission missing: keep the flow open (no fixes).
            }
        }
        // CoreLocation delivers delegate callbacks on the run loop of the thread that starts updates.
        // The collecting coroutine may run on a background dispatcher (no run loop), so start/stop on
        // the main queue to guarantee callbacks arrive.
        dispatch_async(dispatch_get_main_queue()) {
            manager.desiredAccuracy = kCLLocationAccuracyBest
            manager.delegate = delegate
            manager.startUpdatingLocation()
        }
        awaitClose {
            dispatch_async(dispatch_get_main_queue()) {
                manager.stopUpdatingLocation()
                manager.delegate = null
            }
        }
    }
}
