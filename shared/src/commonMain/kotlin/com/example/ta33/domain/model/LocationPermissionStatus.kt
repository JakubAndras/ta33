package com.example.ta33.domain.model

/** Contract-level permission status (maps to Android runtime perms + iOS CLAuthorizationStatus). */
enum class LocationPermissionStatus {
    NOT_DETERMINED, DENIED, RESTRICTED, GRANTED_WHEN_IN_USE, GRANTED_ALWAYS;

    val isUsable: Boolean get() = this == GRANTED_WHEN_IN_USE || this == GRANTED_ALWAYS
    val allowsBackground: Boolean get() = this == GRANTED_ALWAYS
}
