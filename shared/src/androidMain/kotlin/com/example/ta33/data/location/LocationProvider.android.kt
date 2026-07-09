package com.example.ta33.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeoPosition
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Fused Location Provider based GPS acquisition. LocationManager is a documented fallback (Fused is primary). */
class AndroidLocationProvider(private val context: Context) : LocationProvider {

    @SuppressLint("MissingPermission")
    override fun positionUpdates(intervalMillis: Long): Flow<GeoPosition> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    trySend(
                        GeoPosition(
                            location = GeoPoint(location.latitude, location.longitude),
                            accuracyMeters = location.accuracy.toDouble(),
                            timestampMillis = location.time,
                        ),
                    )
                }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Permission missing: complete the flow empty (not exceptionally) so the shared
            // upstream can restart once permission is later granted, rather than crashing.
            close()
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
