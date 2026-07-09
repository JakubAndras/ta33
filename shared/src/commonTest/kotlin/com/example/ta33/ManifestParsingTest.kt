package com.example.ta33

import com.example.ta33.data.remote.ContentConfig
import com.example.ta33.data.remote.ContentRemoteDataSource
import com.example.ta33.data.remote.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ManifestParsingTest {

    private val manifestJson = """
        {
          "version": 7,
          "content": { "url": "content.json", "bytes": 123 },
          "tiles": [
            { "id": "tile-a", "url": "tiles/tile-a.mbtiles", "bytes": 10, "extraUnknown": "x" }
          ],
          "extraTopLevelField": true
        }
    """.trimIndent()

    @Test
    fun fetchManifest_parsesFields_andToleratesUnknownKeys() = runTest {
        val engine = MockEngine {
            respond(
                content = manifestJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val ds = ContentRemoteDataSource(createHttpClient(engine), ContentConfig(baseUrl = "https://host/"))

        val manifest = ds.fetchManifest()

        assertEquals(7, manifest.version)
        assertEquals("content.json", manifest.content.url)
        assertEquals(123L, manifest.content.bytes)
        assertEquals(1, manifest.tiles.size)
        assertEquals("tile-a", manifest.tiles[0].id)
        assertEquals("tiles/tile-a.mbtiles", manifest.tiles[0].url)
        assertEquals(10L, manifest.tiles[0].bytes)
        assertEquals("mbtiles", manifest.tiles[0].format) // default
    }

    @Test
    fun fetchManifest_requestsConfiguredManifestUrl() = runTest {
        var requestedUrl: String? = null
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = manifestJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val ds = ContentRemoteDataSource(
            createHttpClient(engine),
            ContentConfig(baseUrl = "https://host/ta33/", manifestPath = "manifest.json"),
        )

        ds.fetchManifest()

        assertEquals("https://host/ta33/manifest.json", requestedUrl)
    }
}
