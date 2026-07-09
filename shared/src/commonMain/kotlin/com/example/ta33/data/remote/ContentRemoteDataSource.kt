package com.example.ta33.data.remote

import com.example.ta33.data.dto.ContentDto
import com.example.ta33.data.dto.ManifestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable

class ContentRemoteDataSource(
    private val client: HttpClient,
    private val config: ContentConfig,
) {
    suspend fun fetchManifest(): ManifestDto = client.get(config.manifestUrl()).body()

    suspend fun fetchContent(url: String): ContentDto = client.get(config.resolve(url)).body()

    /**
     * Streams [url] to [onBytes], resuming from [offset] via HTTP Range.
     * [onStart] receives (contentLength, resumed); resumed=true only when the server honoured Range (206).
     */
    suspend fun downloadStream(
        url: String,
        offset: Long,
        onStart: suspend (contentLength: Long?, resumed: Boolean) -> Unit,
        onBytes: suspend (chunk: ByteArray) -> Unit,
    ) {
        client.prepareGet(config.resolve(url)) {
            if (offset > 0) header(HttpHeaders.Range, "bytes=$offset-")
        }.execute { response ->
            if (response.status == HttpStatusCode.RequestedRangeNotSatisfiable) {
                // Range starts at/after the full length → the file is already complete on disk; nothing to fetch.
                return@execute
            }
            check(response.status.isSuccess()) { "Tile download failed: HTTP ${response.status}" }
            val resumed = response.status == HttpStatusCode.PartialContent
            onStart(response.contentLength(), resumed)
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(DEFAULT_CHUNK_SIZE)
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read > 0) onBytes(buffer.copyOf(read))
            }
        }
    }

    private companion object {
        const val DEFAULT_CHUNK_SIZE = 64 * 1024
    }
}
