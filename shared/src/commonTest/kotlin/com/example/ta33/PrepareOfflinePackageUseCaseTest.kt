package com.example.ta33

import com.example.ta33.data.remote.ContentConfig
import com.example.ta33.data.remote.ContentRemoteDataSource
import com.example.ta33.data.remote.createHttpClient
import com.example.ta33.data.repository.OfflinePackageRepositoryImpl
import com.example.ta33.domain.download.DownloadItemProgress
import com.example.ta33.domain.download.DownloadStatus
import com.example.ta33.domain.model.NetworkPreference
import com.example.ta33.domain.model.NetworkType
import com.example.ta33.domain.usecase.PrepareOfflinePackageUseCase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrepareOfflinePackageUseCaseTest {

    private val manifestJson =
        """{"version":7,"content":{"url":"content.json"},"tiles":[{"id":"tile-a","url":"tiles/tile-a.mbtiles","bytes":10}]}"""
    private val contentJson =
        """{"routes":[{"id":"r1","name":"A","distanceKm":33.0,"controls":[
           {"id":"c1","ordinal":1,"name":"Start","lat":50.6,"lon":16.1},
           {"id":"c2","ordinal":2,"name":"Cil","lat":50.7,"lon":16.2}]}]}"""
    private val tile = ByteArray(10) { it.toByte() }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun offlineRepo(engine: MockEngine, routeRepo: FakeRouteRepository, fileStorage: FakeFileStorage) =
        OfflinePackageRepositoryImpl(
            ContentRemoteDataSource(createHttpClient(engine), ContentConfig(baseUrl = "https://host/")),
            routeRepo,
            fileStorage,
        )

    @Test
    fun happyPath_persistsRoutes_writesTile_marksReady_done() = runTest {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("manifest.json") -> respond(manifestJson, HttpStatusCode.OK, jsonHeaders)
                path.endsWith("content.json") -> respond(contentJson, HttpStatusCode.OK, jsonHeaders)
                path.endsWith("tile-a.mbtiles") ->
                    respond(tile, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "10"))
                else -> respond("nope", HttpStatusCode.NotFound)
            }
        }
        val routeRepo = FakeRouteRepository()
        val fileStorage = FakeFileStorage()
        val prepRepo = FakePreparationRepository()
        val useCase = PrepareOfflinePackageUseCase(
            offlineRepo(engine, routeRepo, fileStorage),
            prepRepo,
            FakeConnectivityMonitor(NetworkType.WIFI),
        )

        val emissions = useCase.run(NetworkPreference.WIFI_AND_CELLULAR).toList()

        assertEquals(DownloadStatus.DONE, emissions.last().overallStatus)
        val routes = routeRepo.observeRoutes().first()
        assertEquals(1, routes.size)
        assertEquals("r1", routes.first().id)
        assertEquals(2, routeRepo.getRouteWithControls("r1")!!.controls.size)
        assertContentEquals(tile, fileStorage.files["tiles/tile-a.mbtiles"])
        assertEquals(1, prepRepo.readyCalls)
        assertEquals(7, prepRepo.lastReadyVersion)
    }

    @Test
    fun resume_skipsDoneContent_rangeResumesTile_noRestart() = runTest {
        var tileHadRange = false
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("manifest.json") -> respond(manifestJson, HttpStatusCode.OK, jsonHeaders)
                path.endsWith("content.json") -> respond(contentJson, HttpStatusCode.OK, jsonHeaders)
                path.endsWith("tile-a.mbtiles") -> {
                    if (request.headers[HttpHeaders.Range] != null) {
                        tileHadRange = true
                        respond(
                            tile.copyOfRange(5, 10),
                            HttpStatusCode.PartialContent,
                            headersOf(HttpHeaders.ContentLength, "5"),
                        )
                    } else {
                        respond(tile, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "10"))
                    }
                }
                else -> respond("nope", HttpStatusCode.NotFound)
            }
        }
        val routeRepo = FakeRouteRepository()
        val fileStorage = FakeFileStorage().apply { seed("tiles/tile-a.mbtiles", tile.copyOfRange(0, 5)) }
        val prepRepo = FakePreparationRepository().apply {
            seedAsset(DownloadItemProgress(id = "content", label = "content", status = DownloadStatus.DONE))
        }
        val useCase = PrepareOfflinePackageUseCase(
            offlineRepo(engine, routeRepo, fileStorage),
            prepRepo,
            FakeConnectivityMonitor(NetworkType.WIFI),
        )

        val emissions = useCase.run(NetworkPreference.WIFI_ONLY).toList()

        assertEquals(DownloadStatus.DONE, emissions.last().overallStatus)
        assertTrue(tileHadRange, "tile request should carry a Range header on resume")
        assertTrue(routeRepo.observeRoutes().first().isEmpty(), "content was DONE → must not re-persist")
        assertContentEquals(tile, fileStorage.files["tiles/tile-a.mbtiles"])
        assertTrue(fileStorage.deletedPaths.isEmpty(), "206 resume must append, not restart")
    }

    @Test
    fun blocked_wifiOnlyOnCellular_emitsPaused_noNetworkCalls() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("x", HttpStatusCode.OK)
        }
        val prepRepo = FakePreparationRepository()
        val useCase = PrepareOfflinePackageUseCase(
            offlineRepo(engine, FakeRouteRepository(), FakeFileStorage()),
            prepRepo,
            FakeConnectivityMonitor(NetworkType.CELLULAR),
        )

        val emissions = useCase.run(NetworkPreference.WIFI_ONLY).toList()

        assertEquals(DownloadStatus.PAUSED, emissions.first().overallStatus)
        assertEquals(0, calls)
        assertEquals(0, prepRepo.readyCalls)
    }

    @Test
    fun error_tile500_marksError_contentStillPersisted() = runTest {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("manifest.json") -> respond(manifestJson, HttpStatusCode.OK, jsonHeaders)
                path.endsWith("content.json") -> respond(contentJson, HttpStatusCode.OK, jsonHeaders)
                path.endsWith("tile-a.mbtiles") -> respond("boom", HttpStatusCode.InternalServerError)
                else -> respond("nope", HttpStatusCode.NotFound)
            }
        }
        val routeRepo = FakeRouteRepository()
        val prepRepo = FakePreparationRepository()
        val useCase = PrepareOfflinePackageUseCase(
            offlineRepo(engine, routeRepo, FakeFileStorage()),
            prepRepo,
            FakeConnectivityMonitor(NetworkType.WIFI),
        )

        val emissions = useCase.run(NetworkPreference.WIFI_AND_CELLULAR).toList()
        val last = emissions.last()

        assertEquals(DownloadStatus.ERROR, last.overallStatus)
        assertEquals(DownloadStatus.DONE, last.items.first { it.id == "content" }.status)
        assertEquals(DownloadStatus.ERROR, last.items.first { it.id == "tiles:tile-a" }.status)
        assertTrue(prepRepo.errorCalls >= 1)
        assertEquals(1, routeRepo.observeRoutes().first().size)
    }
}
