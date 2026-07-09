package com.example.ta33.data.repository

import com.example.ta33.data.dto.ContentDto
import com.example.ta33.data.dto.ManifestDto
import com.example.ta33.data.dto.toDomain
import com.example.ta33.data.file.FileStorage
import com.example.ta33.data.remote.ContentRemoteDataSource
import com.example.ta33.domain.repository.OfflinePackageRepository
import com.example.ta33.domain.repository.RouteRepository

class OfflinePackageRepositoryImpl(
    private val remote: ContentRemoteDataSource,
    private val routeRepository: RouteRepository,
    private val fileStorage: FileStorage,
) : OfflinePackageRepository {

    override suspend fun fetchManifest(): ManifestDto = remote.fetchManifest()

    override suspend fun downloadAndPersistContent(url: String, onBytes: (Long, Long?) -> Unit) {
        val content = remote.fetchContent(url)
        validate(content)
        content.routes.forEach { routeDto ->
            val (route, controls) = routeDto.toDomain()
            routeRepository.upsertRoute(route, controls)
        }
        // Content is atomic and small; report an unknown total so overall progress uses the
        // per-item average (a fixed "1 byte" total is negligible against multi-MB tiles).
        onBytes(1L, null)
    }

    override suspend fun downloadTileset(
        tilesetId: String,
        url: String,
        expectedBytes: Long?,
        onBytes: (Long, Long?) -> Unit,
    ): String {
        val clean = url.substringBefore('?')
        val ext = clean.substringAfterLast('.', "mbtiles")
        val relativePath = "tiles/$tilesetId.$ext"
        val offset = fileStorage.size(relativePath)
        var downloaded = offset
        remote.downloadStream(
            url = url,
            offset = offset,
            onStart = { _, resumed ->
                // Server ignored Range (returned 200 instead of 206): discard partial + restart from 0.
                if (offset > 0 && !resumed) {
                    fileStorage.delete(relativePath)
                    downloaded = 0
                }
            },
            onBytes = { chunk ->
                fileStorage.append(relativePath, chunk)
                downloaded += chunk.size
                onBytes(downloaded, expectedBytes)
            },
        )
        return relativePath
    }

    private fun validate(content: ContentDto) {
        content.routes.forEach { route ->
            require(route.id.isNotBlank()) { "route id must not be blank" }
            require(route.distanceKm >= 0) { "distanceKm must be >= 0" }
            route.controls.forEach { control ->
                require(control.id.isNotBlank()) { "control id must not be blank" }
                require(control.lat in -90.0..90.0) { "lat out of range" }
                require(control.lon in -180.0..180.0) { "lon out of range" }
                require(control.radiusMeters > 0) { "radiusMeters must be > 0" }
            }
        }
    }
}
