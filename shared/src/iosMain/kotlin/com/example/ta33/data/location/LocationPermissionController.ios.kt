package com.example.ta33.data.location

import com.example.ta33.domain.model.LocationPermissionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.darwin.NSObject

/** Reads and observes CoreLocation authorization. requestWhenInUse/requestBackground are callable now;
 *  the UI reaction (explanations, settings deep-link) is deferred to the UI phase. */
class IosLocationPermissionController : LocationPermissionController {

    private val manager = CLLocationManager()
    private val statuses = MutableStateFlow(map(manager.authorizationStatus))

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            statuses.value = map(manager.authorizationStatus)
        }
    }

    init {
        manager.delegate = delegate
    }

    private fun map(raw: CLAuthorizationStatus): LocationPermissionStatus = when (raw) {
        kCLAuthorizationStatusNotDetermined -> LocationPermissionStatus.NOT_DETERMINED
        kCLAuthorizationStatusRestricted -> LocationPermissionStatus.RESTRICTED
        kCLAuthorizationStatusDenied -> LocationPermissionStatus.DENIED
        kCLAuthorizationStatusAuthorizedWhenInUse -> LocationPermissionStatus.GRANTED_WHEN_IN_USE
        kCLAuthorizationStatusAuthorizedAlways -> LocationPermissionStatus.GRANTED_ALWAYS
        else -> LocationPermissionStatus.NOT_DETERMINED
    }

    override fun status(): LocationPermissionStatus = statuses.value

    override fun observeStatus(): Flow<LocationPermissionStatus> = statuses.asStateFlow()

    override suspend fun requestWhenInUse() {
        manager.requestWhenInUseAuthorization()
    }

    override suspend fun requestBackground() {
        manager.requestAlwaysAuthorization()
    }
}
