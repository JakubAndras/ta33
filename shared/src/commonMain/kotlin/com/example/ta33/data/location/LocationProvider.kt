package com.example.ta33.data.location

import com.example.ta33.domain.model.GeoPosition
import kotlinx.coroutines.flow.Flow

/** Platform GPS acquisition. Cold: each collection starts updates; cancellation stops them.
 *  Consumers should use LocationStream (hot, shared) rather than collecting this directly. */
interface LocationProvider {
    /** @param intervalMillis desired update cadence hint (platforms may coalesce). */
    fun positionUpdates(intervalMillis: Long = 2_000): Flow<GeoPosition>
}
