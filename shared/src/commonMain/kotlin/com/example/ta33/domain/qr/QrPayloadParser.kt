package com.example.ta33.domain.qr

import com.example.ta33.domain.model.QrTimingConfig

/** PURE, deterministic. No coroutines / I/O / platform. Format comes from QrTimingConfig. */
class QrPayloadParser {

    fun parse(raw: String, config: QrTimingConfig): QrParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return QrParseResult.Unrecognized(raw)

        val parts = trimmed.split(config.separator)
        if (parts.size < 2) return QrParseResult.Unrecognized(raw)

        fun eq(a: String, b: String) =
            if (config.caseSensitive) a == b else a.equals(b, ignoreCase = true)

        if (!eq(parts[0], config.scheme)) return QrParseResult.Unrecognized(raw)

        val keyword = parts[1]
        val routeId = if (config.routeScoped) parts.getOrNull(2)?.takeIf { it.isNotBlank() } else null

        return when {
            eq(keyword, config.startKeyword)  -> QrParseResult.StartQr(routeId)
            eq(keyword, config.finishKeyword) -> QrParseResult.FinishQr(routeId)
            else                              -> QrParseResult.Unrecognized(raw)
        }
    }
}
