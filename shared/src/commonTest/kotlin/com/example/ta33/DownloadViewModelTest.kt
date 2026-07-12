package com.example.ta33

import com.example.ta33.data.remote.ContentConfig
import com.example.ta33.data.remote.ContentRemoteDataSource
import com.example.ta33.data.remote.createHttpClient
import com.example.ta33.data.repository.OfflinePackageRepositoryImpl
import com.example.ta33.domain.download.DownloadStatus
import com.example.ta33.domain.model.NetworkPreference
import com.example.ta33.domain.model.NetworkType
import com.example.ta33.domain.usecase.ObserveNotificationsEnabledUseCase
import com.example.ta33.domain.usecase.ObservePreparationStateUseCase
import com.example.ta33.domain.usecase.PrepareOfflinePackageUseCase
import com.example.ta33.presentation.DownloadViewModel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelTest {

    private val manifestJson =
        """{"version":7,"content":{"url":"content.json"},"tiles":[{"id":"tile-a","url":"tiles/tile-a.mbtiles","bytes":10}]}"""
    private val contentJson = """{"routes":[{"id":"r1","name":"A","distanceKm":33.0,"controls":[]}]}"""
    private val tile = ByteArray(10) { it.toByte() }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun happyEngine() = MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path.endsWith("manifest.json") -> respond(manifestJson, HttpStatusCode.OK, jsonHeaders)
            path.endsWith("content.json") -> respond(contentJson, HttpStatusCode.OK, jsonHeaders)
            path.endsWith("tile-a.mbtiles") ->
                respond(tile, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "10"))
            else -> respond("nope", HttpStatusCode.NotFound)
        }
    }

    private fun viewModel(connectivity: FakeConnectivityMonitor): DownloadViewModel {
        val prepRepo = FakePreparationRepository()
        val offline = OfflinePackageRepositoryImpl(
            ContentRemoteDataSource(createHttpClient(happyEngine()), ContentConfig(baseUrl = "https://host/")),
            FakeRouteRepository(),
            FakeFileStorage(),
        )
        val prepare = PrepareOfflinePackageUseCase(offline, prepRepo, connectivity)
        return DownloadViewModel(
            prepare,
            ObservePreparationStateUseCase(prepRepo),
            connectivity,
            FakeNotifier(),
            ObserveNotificationsEnabledUseCase(FakeAppPreferencesRepository()),
        )
    }

    @Test
    fun start_drivesProgressToDone() = runTest {
        val vm = viewModel(FakeConnectivityMonitor(NetworkType.WIFI))

        vm.start()
        // Suspend the test body on the state flow until the run reaches a terminal DONE,
        // keeping runTest pumping while the (real-async) MockEngine download completes.
        val done = vm.state.first { it.progress.overallStatus == DownloadStatus.DONE }

        assertEquals(DownloadStatus.DONE, done.progress.overallStatus)
    }

    @Test
    fun setNetworkPreference_reflectsBlockedState() = runTest {
        val vm = viewModel(FakeConnectivityMonitor(NetworkType.CELLULAR))
        advanceUntilIdle()

        // default preference WIFI_ONLY on cellular → blocked
        assertTrue(vm.state.value.blockedByNetwork)

        vm.setNetworkPreference(NetworkPreference.WIFI_AND_CELLULAR)
        assertFalse(vm.state.value.blockedByNetwork)
        assertEquals(NetworkPreference.WIFI_AND_CELLULAR, vm.state.value.networkPreference)
    }

    @Test
    fun connectivityDropsToCellular_blocksAndStartRefusesToRun() = runTest {
        val connectivity = FakeConnectivityMonitor(NetworkType.WIFI)
        val vm = viewModel(connectivity)
        advanceUntilIdle()
        assertFalse(vm.state.value.blockedByNetwork)

        connectivity.set(NetworkType.CELLULAR)
        advanceUntilIdle()
        assertEquals(NetworkType.CELLULAR, vm.state.value.currentNetworkType)
        assertTrue(vm.state.value.blockedByNetwork)

        // Default preference is WIFI_ONLY → starting on cellular must not download; it emits PAUSED.
        vm.start()
        advanceUntilIdle()
        assertEquals(DownloadStatus.PAUSED, vm.state.value.progress.overallStatus)
    }
}
