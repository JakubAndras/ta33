package com.example.ta33.data.location

import com.example.ta33.domain.model.LocationPermissionStatus
import kotlinx.coroutines.flow.Flow

interface LocationPermissionController {
    fun status(): LocationPermissionStatus
    fun observeStatus(): Flow<LocationPermissionStatus>

    /** Trigger the system permission flow. UI reaction/dialogs are UI-phase; impl may be a
     *  documented stub now (Android needs an Activity result API wired later). */
    suspend fun requestWhenInUse()
    suspend fun requestBackground()
}
