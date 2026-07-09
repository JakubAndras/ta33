package com.example.ta33.ui.format

/** Vzdálenost v km s českou desetinnou čárkou (locale-independent). Celé číslo bez desetin. */
fun formatKm(km: Double): String =
    if (km % 1.0 == 0.0) km.toInt().toString() else km.toString().replace('.', ',')
