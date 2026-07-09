package com.example.ta33.domain.qr

/** Outcome of parsing a scanned QR string (FR-09). routeId is present only when route-scoped. */
sealed interface QrParseResult {
    data class StartQr(val routeId: String?) : QrParseResult
    data class FinishQr(val routeId: String?) : QrParseResult
    data class Unrecognized(val raw: String) : QrParseResult   // foreign / malformed / empty
}
