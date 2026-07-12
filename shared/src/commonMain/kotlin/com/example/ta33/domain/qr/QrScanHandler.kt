package com.example.ta33.domain.qr

/**
 * Contract the native QR scanner (CameraX + ML Kit / AVFoundation + Vision - built in the UI phase)
 * uses to hand a decoded payload into shared logic. This logic-only phase defines the seam;
 * TimingViewModel implements it. The camera-backed producer is deferred.
 */
fun interface QrScanHandler {
    /** Called by the native scanner for each decoded QR string. Fire-and-forget from the caller. */
    fun onQrScanned(raw: String)
}
