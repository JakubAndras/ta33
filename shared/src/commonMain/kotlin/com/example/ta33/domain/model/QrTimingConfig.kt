package com.example.ta33.domain.model

/**
 * QR format for start/finish timing (FR-09). Dev default recognises "TA33:START" / "TA33:FINISH".
 * The real format is finalised by the organizer; all knobs are field-tunable, no code change.
 */
data class QrTimingConfig(
    val scheme: String = "TA33",         // first segment must equal this
    val separator: String = ":",         // segment delimiter
    val startKeyword: String = "START",  // second segment for a start QR
    val finishKeyword: String = "FINISH",// second segment for a finish QR
    val routeScoped: Boolean = false,    // when true, an optional 3rd segment carries a routeId
    val caseSensitive: Boolean = false,  // keyword/scheme comparison
)
