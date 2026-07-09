package com.example.ta33.data.location

import com.example.ta33.domain.model.GeoPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/** Hot, shared location source: a single upstream LocationProvider subscription,
 *  fanned out to breadcrumb recording (FR-05) and geofencing (FR-08). */
interface LocationStream {
    fun positions(): Flow<GeoPosition>
}

/** Shares one [LocationProvider] subscription across all consumers. */
class SharedLocationStream(
    private val provider: LocationProvider,
    private val appScope: CoroutineScope,
    intervalMillis: Long = 2_000,
) : LocationStream {
    private val shared: SharedFlow<GeoPosition> =
        provider.positionUpdates(intervalMillis)
            .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    override fun positions(): Flow<GeoPosition> = shared
}
