package com.example.ta33

import com.example.ta33.domain.model.QrTimingConfig
import com.example.ta33.domain.qr.QrParseResult
import com.example.ta33.domain.qr.QrPayloadParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QrPayloadParserTest {

    private val parser = QrPayloadParser()
    private val default = QrTimingConfig()

    @Test
    fun validStart_isStartQr() {
        assertIs<QrParseResult.StartQr>(parser.parse("TA33:START", default))
    }

    @Test
    fun validFinish_isFinishQr() {
        assertIs<QrParseResult.FinishQr>(parser.parse("TA33:FINISH", default))
    }

    @Test
    fun lowercase_isRecognized_whenCaseInsensitive() {
        assertIs<QrParseResult.StartQr>(parser.parse("ta33:start", default))
    }

    @Test
    fun routeScoped_carriesRouteId() {
        val config = QrTimingConfig(routeScoped = true)
        val result = assertIs<QrParseResult.StartQr>(parser.parse("TA33:START:routeA", config))
        assertEquals("routeA", result.routeId)
    }

    @Test
    fun routeId_isNull_whenNotRouteScoped() {
        val result = assertIs<QrParseResult.StartQr>(parser.parse("TA33:START:routeA", default))
        assertEquals(null, result.routeId)
    }

    @Test
    fun whitespace_isTrimmed() {
        assertIs<QrParseResult.StartQr>(parser.parse("  TA33:START  ", default))
    }

    @Test
    fun empty_isUnrecognized() {
        assertIs<QrParseResult.Unrecognized>(parser.parse("", default))
        assertIs<QrParseResult.Unrecognized>(parser.parse("   ", default))
    }

    @Test
    fun foreignAndMalformed_areUnrecognized() {
        assertIs<QrParseResult.Unrecognized>(parser.parse("HELLO", default))
        assertIs<QrParseResult.Unrecognized>(parser.parse("TA33", default))
        assertIs<QrParseResult.Unrecognized>(parser.parse("TA33:FOO", default))
        assertIs<QrParseResult.Unrecognized>(parser.parse("OTHER:START", default))
        assertIs<QrParseResult.Unrecognized>(parser.parse("https://example.com/qr?x=1", default))
    }

    @Test
    fun caseSensitive_rejectsWrongCase() {
        val config = QrTimingConfig(caseSensitive = true)
        assertIs<QrParseResult.Unrecognized>(parser.parse("ta33:start", config))
        assertIs<QrParseResult.StartQr>(parser.parse("TA33:START", config))
    }
}
