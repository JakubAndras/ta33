package com.example.ta33.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.ta33.domain.model.LocationPermissionStatus
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reads Android runtime location permissions. Requesting is UI-phase (needs an Activity result API);
 *  request* are documented stubs for now. Call [refresh] on resume to re-read the status. */
class AndroidLocationPermissionController(private val context: Context) : LocationPermissionController {

    private val _status = MutableStateFlow(read())

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun read(): LocationPermissionStatus {
        val fineOrCoarse = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fineOrCoarse) return LocationPermissionStatus.DENIED
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        return if (background) {
            LocationPermissionStatus.GRANTED_ALWAYS
        } else {
            LocationPermissionStatus.GRANTED_WHEN_IN_USE
        }
    }

    /** Re-read current permission state (UI should call this on resume after a system dialog). */
    fun refresh() {
        _status.value = read()
    }

    override fun status(): LocationPermissionStatus = read()

    override fun observeStatus(): Flow<LocationPermissionStatus> = _status.asStateFlow()

    override suspend fun requestWhenInUse() {
        // UI-phase: real request needs ActivityResultContracts.RequestMultiplePermissions.
        Napier.d("requestWhenInUse: deferred to UI phase (Activity result API)", tag = "LocationPermission")
    }

    override suspend fun requestBackground() {
        // UI-phase: background permission must be requested after when-in-use is granted.
        Napier.d("requestBackground: deferred to UI phase (Activity result API)", tag = "LocationPermission")
    }
}
