package com.example.ta33.domain.model

/** The control currently in range and offered for collection (FR-08). Null when nothing to offer. */
data class CollectionCandidate(
    val control: ControlPoint,        // FR-02/FR-03 model (has location + radiusMeters)
    val distanceMeters: Double,       // distance from the producing fix to the control
    val accuracyMeters: Double,       // accuracy of the producing fix (for UI trust hints, later)
    val atLocation: GeoPoint,         // the fix location that produced this offer (fed to confirm())
)
