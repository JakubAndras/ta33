package com.example.ta33.presentation.navigation

data class AppUiState(
    val readiness: AppReadiness = AppReadiness.LOADING,
    val contentAvailability: ContentAvailability = ContentAvailability.UNKNOWN,
    val activeRouteId: String? = null,
    val activeRunId: String? = null,
    /** Initial destination/gate. `null` while LOADING → native shows a splash and does not navigate. */
    val startDestination: Destination? = null,
)
