package com.example.ta33.ui.format

import kotlin.math.roundToInt

/** Vzdálenost v km s českou desetinnou čárkou (locale-independent). Celé číslo bez desetin. */
fun formatKm(km: Double): String =
    if (km % 1.0 == 0.0) km.toInt().toString() else km.toString().replace('.', ',')

/** Krátká vzdálenost (<1 km) v celých metrech, např. „34 m" (FR-08 nabídka sběru). */
fun formatMeters(meters: Double): String = "${meters.roundToInt()} m"
