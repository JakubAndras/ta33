package com.example.ta33

import com.example.ta33.core.IdGenerator
import com.example.ta33.core.TimeProvider
import com.example.ta33.data.connectivity.ConnectivityMonitor
import com.example.ta33.data.file.FileStorage
import com.example.ta33.data.location.LocationPermissionController
import com.example.ta33.data.location.LocationProvider
import com.example.ta33.data.location.LocationStream
import com.example.ta33.data.location.LocationTrackingController
import com.example.ta33.domain.download.DownloadItemProgress
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPosition
import com.example.ta33.domain.model.LocationPermissionStatus
import com.example.ta33.domain.model.NetworkType
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RouteSummary
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.model.Trackpoint
import com.example.ta33.domain.repository.AppPreferencesRepository
import com.example.ta33.domain.repository.PreparationRepository
import com.example.ta33.domain.repository.RouteRepository
import com.example.ta33.domain.repository.RunRepository
import com.example.ta33.core.Ticker
import com.example.ta33.domain.repository.TrackpointRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

class MutableTimeProvider(var now: Long = 0L) : TimeProvider {
    override fun nowMillis(): Long = now
}

class SeqIdGenerator(private val prefix: String = "id-") : IdGenerator {
    private var counter = 0
    override fun newId(): String = "$prefix${counter++}"
}

class FakeRunRepository(private val ids: IdGenerator = SeqIdGenerator("run-")) : RunRepository {
    private val runFlow = MutableStateFlow<RunSession?>(null)
    private val collectedFlow = MutableStateFlow<List<CollectedControl>>(emptyList())

    /** Directly seed a run (bypasses createRun) for timing tests. */
    fun seedRun(run: RunSession) {
        runFlow.value = run
    }

    override suspend fun createRun(routeId: String, participantId: String): RunSession {
        val run = RunSession(ids.newId(), routeId, participantId)
        runFlow.value = run
        return run
    }

    override suspend fun getRun(runId: String): RunSession? =
        runFlow.value?.takeIf { it.id == runId }

    override fun observeRun(runId: String): Flow<RunSession?> =
        runFlow.map { it?.takeIf { r -> r.id == runId } }

    override fun observeActiveRun(): Flow<RunSession?> =
        runFlow.map { it?.takeIf { r -> r.finishedAtMillis == null } }

    override suspend fun getActiveRun(): RunSession? =
        runFlow.value?.takeIf { it.finishedAtMillis == null }

    override fun observeLatestRun(): Flow<RunSession?> = runFlow

    override suspend fun getLatestRun(): RunSession? = runFlow.value

    override suspend fun clearFinished(runId: String) {
        val current = runFlow.value
        if (current?.id == runId) runFlow.value = current.copy(finishedAtMillis = null)
    }

    override suspend fun clearAllRuns() {
        runFlow.value = null
        collectedFlow.value = emptyList()
    }

    override suspend fun setStarted(runId: String, startedAtMillis: Long) {
        val current = runFlow.value
        if (current?.id == runId) runFlow.value = current.copy(startedAtMillis = startedAtMillis)
    }

    override suspend fun setFinished(runId: String, finishedAtMillis: Long) {
        val current = runFlow.value
        if (current?.id == runId) runFlow.value = current.copy(finishedAtMillis = finishedAtMillis)
    }

    override suspend fun addCollected(collected: CollectedControl): Boolean {
        val exists = collectedFlow.value.any {
            it.runSessionId == collected.runSessionId && it.controlId == collected.controlId
        }
        if (exists) return false
        collectedFlow.value = collectedFlow.value + collected
        return true
    }

    override fun observeCollected(runId: String): Flow<List<CollectedControl>> =
        collectedFlow.map { list -> list.filter { it.runSessionId == runId } }
}

/** Deterministic tick source: tests drive cadence via [tick] without any real delay. */
class FakeTicker : Ticker {
    private val flow = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 64)

    suspend fun tick() = flow.emit(Unit)

    override fun ticks(periodMillis: Long): Flow<Unit> = flow
}

class FakeRouteRepository : RouteRepository {
    private val routes = MutableStateFlow<List<Route>>(emptyList())
    private val controls = MutableStateFlow<List<ControlPoint>>(emptyList())

    override fun observeRoutes(): Flow<List<Route>> = routes

    override fun observeRouteSummaries(): Flow<List<RouteSummary>> =
        combine(routes, controls) { routeList, controlList ->
            routeList
                .map { route ->
                    RouteSummary(
                        routeId = route.id,
                        name = route.name,
                        distanceKm = route.distanceKm,
                        controlCount = controlList.count { it.routeId == route.id },
                    )
                }
                .sortedBy { it.name }
        }

    override fun observeRouteWithControls(routeId: String): Flow<Route?> =
        combine(routes, controls) { routeList, controlList ->
            routeList.firstOrNull { it.id == routeId }
                ?.copy(controls = controlList.filter { it.routeId == routeId })
        }

    override suspend fun getRouteWithControls(routeId: String): Route? =
        routes.value.firstOrNull { it.id == routeId }
            ?.copy(controls = controls.value.filter { it.routeId == routeId })

    override suspend fun getControl(controlId: String): ControlPoint? =
        controls.value.firstOrNull { it.id == controlId }

    override suspend fun upsertRoute(route: Route, controls: List<ControlPoint>) {
        routes.value = routes.value.filterNot { it.id == route.id } + route.copy(controls = emptyList())
        this.controls.value = this.controls.value.filterNot { it.routeId == route.id } + controls
    }

    override suspend fun clearAll() {
        routes.value = emptyList()
        controls.value = emptyList()
    }
}

class FakeAppPreferencesRepository(
    initial: String? = null,
    initialNotificationsEnabled: Boolean = true,
) : AppPreferencesRepository {
    private val selected = MutableStateFlow(initial)
    private val notificationsEnabled = MutableStateFlow(initialNotificationsEnabled)

    override fun observeSelectedRouteId(): Flow<String?> = selected.asStateFlow()

    override suspend fun getSelectedRouteId(): String? = selected.value

    override suspend fun setSelectedRouteId(routeId: String?) {
        selected.value = routeId
    }

    override fun observeNotificationsEnabled(): Flow<Boolean> = notificationsEnabled.asStateFlow()

    override suspend fun getNotificationsEnabled(): Boolean = notificationsEnabled.value

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled.value = enabled
    }
}

class FakePreparationRepository(initial: PreparationState = PreparationState()) : PreparationRepository {
    private val stateFlow = MutableStateFlow(initial)
    private val assets = mutableMapOf<String, Pair<DownloadItemProgress, String?>>()

    var readyCalls = 0
        private set
    var errorCalls = 0
        private set
    var lastReadyVersion: Int? = null
        private set

    fun set(state: PreparationState) {
        stateFlow.value = state
    }

    fun seedAsset(item: DownloadItemProgress, relativePath: String? = null) {
        assets[item.id] = item to relativePath
    }

    override fun observePreparationState(): Flow<PreparationState> = stateFlow.asStateFlow()

    override suspend fun getPreparationState(): PreparationState = stateFlow.value

    override suspend fun setPreparing(manifestVersion: Int) {
        stateFlow.value = PreparationState(PreparationStatus.PREPARING, manifestVersion)
    }

    override suspend fun markReady(manifestVersion: Int) {
        readyCalls++
        lastReadyVersion = manifestVersion
        stateFlow.value = PreparationState(PreparationStatus.READY, manifestVersion, 0L)
    }

    override suspend fun markError() {
        errorCalls++
        stateFlow.value = stateFlow.value.copy(status = PreparationStatus.ERROR)
    }

    override suspend fun loadAssets(): List<DownloadItemProgress> = assets.values.map { it.first }

    override suspend fun saveAsset(item: DownloadItemProgress, relativePath: String?) {
        assets[item.id] = item to relativePath
    }

    override suspend fun clearAssets() {
        assets.clear()
    }

    override suspend fun reset() {
        stateFlow.value = PreparationState(PreparationStatus.NOT_STARTED)
        assets.clear()
    }
}

class FakeFileStorage : FileStorage {
    val files = mutableMapOf<String, ByteArray>()
    val deletedPaths = mutableListOf<String>()

    fun seed(relativePath: String, bytes: ByteArray) {
        files[relativePath] = bytes
    }

    override fun baseDir(): String = "/fake"

    override fun exists(relativePath: String): Boolean = files.containsKey(relativePath)

    override suspend fun size(relativePath: String): Long = files[relativePath]?.size?.toLong() ?: 0L

    override suspend fun append(relativePath: String, bytes: ByteArray) {
        val existing = files[relativePath] ?: ByteArray(0)
        files[relativePath] = existing + bytes
    }

    override suspend fun delete(relativePath: String) {
        deletedPaths += relativePath
        files.remove(relativePath)
    }
}

class FakeConnectivityMonitor(initial: NetworkType = NetworkType.WIFI) : ConnectivityMonitor {
    private val flow = MutableStateFlow(initial)

    fun set(type: NetworkType) {
        flow.value = type
    }

    override fun current(): NetworkType = flow.value

    override fun observe(): Flow<NetworkType> = flow.asStateFlow()
}

/** Hot fake provider: emissions are pushed via [emit]; tracks the net number of active upstream
 *  subscriptions so LocationStream fan-out (one subscription for many consumers) can be asserted. */
class FakeLocationProvider : LocationProvider {
    private val upstream = MutableSharedFlow<GeoPosition>(replay = 1, extraBufferCapacity = 64)

    var activeSubscriptions = 0
        private set
    var maxSubscriptions = 0
        private set

    suspend fun emit(position: GeoPosition) = upstream.emit(position)

    override fun positionUpdates(intervalMillis: Long): Flow<GeoPosition> =
        upstream
            .onStart {
                activeSubscriptions++
                if (activeSubscriptions > maxSubscriptions) maxSubscriptions = activeSubscriptions
            }
            .onCompletion { activeSubscriptions-- }
}

/** Deterministic LocationStream for use-case / ViewModel tests: replays a scripted, finite flow. */
class FakeLocationStream(private val positions: List<GeoPosition> = emptyList()) : LocationStream {
    override fun positions(): Flow<GeoPosition> = positions.asFlow()
}

/** Hot LocationStream for reactive tests: emit fixes interleaved with repo changes. */
class MutableLocationStream : LocationStream {
    private val flow = MutableSharedFlow<GeoPosition>(replay = 1, extraBufferCapacity = 16)

    suspend fun emit(position: GeoPosition) = flow.emit(position)

    override fun positions(): Flow<GeoPosition> = flow
}

/** In-memory TrackpointRepository backed by a MutableStateFlow so observeTrack re-emits on append. */
class FakeTrackpointRepository : TrackpointRepository {
    private val points = MutableStateFlow<List<Trackpoint>>(emptyList())

    fun seed(vararg trackpoints: Trackpoint) {
        points.value = points.value + trackpoints
    }

    override suspend fun append(trackpoint: Trackpoint) {
        points.value = points.value + trackpoint
    }

    override fun observeTrack(runSessionId: String): Flow<List<Trackpoint>> =
        points.map { list -> list.filter { it.runSessionId == runSessionId }.sortedBy { it.timestampMillis } }

    override suspend fun getTrack(runSessionId: String): List<Trackpoint> =
        points.value.filter { it.runSessionId == runSessionId }.sortedBy { it.timestampMillis }

    override suspend fun getLastTrackpoint(runSessionId: String): Trackpoint? =
        points.value.filter { it.runSessionId == runSessionId }.maxByOrNull { it.timestampMillis }

    override suspend fun clearTrack(runSessionId: String) {
        points.value = points.value.filterNot { it.runSessionId == runSessionId }
    }
}

class FakePermissionController(
    initial: LocationPermissionStatus = LocationPermissionStatus.GRANTED_WHEN_IN_USE,
) : LocationPermissionController {
    private val statuses = MutableStateFlow(initial)
    var whenInUseRequests = 0
        private set
    var backgroundRequests = 0
        private set

    fun set(status: LocationPermissionStatus) {
        statuses.value = status
    }

    override fun status(): LocationPermissionStatus = statuses.value

    override fun observeStatus(): Flow<LocationPermissionStatus> = statuses.asStateFlow()

    override suspend fun requestWhenInUse() {
        whenInUseRequests++
    }

    override suspend fun requestBackground() {
        backgroundRequests++
    }
}

class FakeTrackingController : LocationTrackingController {
    var startCalls = 0
        private set
    var stopCalls = 0
        private set
    private var tracking = false

    override fun start() {
        startCalls++
        tracking = true
    }

    override fun stop() {
        stopCalls++
        tracking = false
    }

    override val isTracking: Boolean get() = tracking
}
