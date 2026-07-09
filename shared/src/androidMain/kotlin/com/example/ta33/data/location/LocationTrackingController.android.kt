package com.example.ta33.data.location

import android.content.Context
import android.content.Intent
import android.os.Build

/** Starts/stops [LocationForegroundService] so recording survives screen-off / background.
 *  ⚠️ FIELD-TEST REQUIRED ("ověřit v terénu, baterie") — OEM battery savers may still interrupt. */
class AndroidLocationTrackingController(private val context: Context) : LocationTrackingController {

    private var tracking = false

    override fun start() {
        val intent = Intent(context, LocationForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        tracking = true
    }

    override fun stop() {
        context.stopService(Intent(context, LocationForegroundService::class.java))
        tracking = false
    }

    override val isTracking: Boolean get() = tracking
}
