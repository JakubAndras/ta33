package com.example.ta33.presentation.navigation

/** Offline-readiness state. PREPARING is reserved for FR-11 to drive (download in progress). */
enum class AppReadiness { LOADING, NOT_READY, PREPARING, READY }

/** Whether offline content (routes/controls) is present locally. Derived from RouteRepository. */
enum class ContentAvailability { UNKNOWN, ABSENT, PRESENT }
