package com.example.ta33.presentation.navigation

class StartDestinationResolver {
    /** @return the initial destination, or null while still LOADING (native shows a splash). */
    fun resolve(readiness: AppReadiness, activeRunId: String?): Destination? = when (readiness) {
        AppReadiness.LOADING -> null
        AppReadiness.NOT_READY,
        AppReadiness.PREPARING -> Destination.Preparation
        AppReadiness.READY ->
            activeRunId?.let { Destination.RunActive(it) } ?: Destination.Main() // Main defaults to DENIK
    }
}
