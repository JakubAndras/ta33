# FR-06 — Offline Map Data & Aggregation (Shared Logic Only)

> **Summary**: Build the thin shared-core logic in `commonMain` that *feeds* a native MapLibre map — offline tile-source descriptor + validation, an aggregated map overlay/state model, a `MapViewModel` combining route/checkpoints/location, and pure unit-testable geometry helpers — without any MapLibre rendering or UI.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Participants of the TA33 march need a map that works without signal: a downloaded basemap plus the route, checkpoints, their live GPS position and the trail they have walked. The actual drawing is done by MapLibre Native inside the native UI (Compose / SwiftUI) in a later phase. What is missing today is the **shared brain** that tells that native map *what* to draw and *whether the offline map is even available*.

### 1.2 Solution Overview
Add a small shared-logic layer in `:shared/commonMain` that (a) describes and validates the offline tile source stored by FR-11, (b) aggregates the route polyline, checkpoint markers (with FR-04 state), live position and breadcrumb into one `MapOverlay`, and (c) exposes a single `MapViewModel` → `StateFlow<MapUiState>` that the native map view consumes. All decision logic (tile readiness, bounding box, marker selection) is pure and unit-tested; the MapLibre view itself is a documented native seam.

### 1.3 Scope: What This IS
- A tile-source descriptor (`MapTileSource`) + availability state (`MapTileSourceState`) + a `TileStore` accessor that resolves the on-disk MBTiles path from FR-11's `FileStorage` and validates completeness/readiness.
- A `MapOverlay` model + thin `CheckpointMarker` projection aggregating route polyline, checkpoint markers (colored by `ControlPointState`), live position and breadcrumb polyline — all built from existing FR-02/03/04/05 models.
- A `MapViewModel` exposing `StateFlow<MapUiState>` combining FR-03 (route), FR-04 (log/control states), FR-05 (live position + track), and the tile-source state, plus initial camera and marker selection.
- Pure, `commonTest`-covered logic: route bounding box, initial-camera calculation, overlay mapping, nearest-marker selection.
- A clearly documented **platform seam** describing how native MapLibre Native (Android + iOS) will consume `MapUiState`.

### 1.4 Scope: What This IS NOT
- **No** MapLibre rendering, style JSON, `MapView`/`MLNMapView`, layers, or any map SDK dependency in shared code (that is the later native/UI phase).
- **No** Compose or SwiftUI code.
- **No** mapy.cz deep-link (that is FR-07).
- **No** new coordinate/route/control/location domain models — reuse `GeoPoint`, `ControlPoint`, `RouteDetail`, `ControlPointState`, `GeoPosition`, `Trackpoint` from FR-02/03/04/05.
- **No** tile downloading, manifest parsing, or connectivity logic (that is FR-11) — FR-06 only *reads* what FR-11 produced.
- **No** server/network in Etapa 1 — tiles come only from local `FileStorage`.
- **No** real route-path polyline (FR-02 stores no route geometry — see §7 / §12).

---

## 2. SUCCESS CRITERIA

Implementation is COMPLETE when ALL criteria are met:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | `MapTileSource`, `MapTileSourceState`, `MapOverlay`, `CheckpointMarker`, `MapCamera`, `GeoBoundingBox`, `MapUiState` compile in `commonMain` reusing existing FR-02/03/04/05 types (no duplicated coordinate/control/route models) | `./gradlew :shared:compileKotlinMetadata` / `./gradlew build` |
| 2 | `TileStore` returns `MapTileSourceState.Ready` only when FR-11 preparation is `READY`, the tile asset status is `DONE`, **and** `FileStorage.exists(...)` is true; otherwise `NotDownloaded` / `Preparing` / `Error` ("mapa nestažena") | Unit tests on `TileSourceSelector`; integration test with a fake `FileStorage` + fake `PreparationRepository` |
| 3 | `MapViewModel.state: StateFlow<MapUiState>` emits a combined state from route + run-log + live-location + tile-source flows; markers carry the correct `ControlPointState` and `isNext` flag | Unit test driving fake use-cases; assert emitted `MapUiState` |
| 4 | Route bounding box, initial camera and nearest-marker selection are pure functions with ≥ 90% branch coverage including empty/single-point/tie cases | `./gradlew :shared:allTests` (tests in `commonTest`) |
| 5 | Overlay works with **no active run** (runId null): markers derived via `ControlLogDeriver.deriveLog(controls, emptyList(), null)`, empty breadcrumb, no crash | Unit test with runId = null |
| 6 | Koin resolves `TileStore` (single) and `MapViewModel` (factory); Swift accessor `ViewModelProvider.mapViewModel()` exists | `./gradlew build`; inspect `di/AppModule.kt` + `di/Koin.kt` |
| 7 | Zero MapLibre / Compose / SwiftUI imports anywhere under `shared/` | `grep -rE "maplibre|androidx.compose|SwiftUI" shared/src` returns nothing |
| 8 | The native MapLibre consumption seam is documented (per platform, one SDK) in this plan and referenced from code KDoc | Review §3.4 + KDoc on `MapUiState` |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
        FR-11 (files/prep)         FR-03 (route)      FR-04 (log)        FR-05 (location)
        ┌──────────────┐        ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
        │ FileStorage  │        │ ObserveRoute │  │ ObserveRunLog│  │ LocationStream   │
        │ Preparation  │        │ DetailUseCase│  │ UseCase      │  │ ObserveTrack     │
        │ Repository   │        └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘
        └──────┬───────┘               │                 │                   │
               │                       │                 │                   │
        ┌──────▼───────┐               │                 │                   │
        │  TileStore   │  observe      │ RouteDetail     │ LogUiState        │ GeoPosition? / List<Trackpoint>
        │ (FR-06 data) │  TileSource   │                 │                   │
        └──────┬───────┘               │                 │                   │
               │ MapTileSourceState    │                 │                   │
               └───────────────┬───────┴─────────────────┴───────────────────┘
                               │ combine(...)  (FR-06 presentation)
                        ┌──────▼──────────┐        pure helpers (FR-06 domain, commonTest):
                        │  MapViewModel   │◀──────  OverlayMapper, MapCameraCalculator,
                        │  StateFlow      │         RouteBounds, MarkerSelector,
                        │  <MapUiState>   │         TileSourceSelector
                        └──────┬──────────┘
                               │ MapUiState (basemap state + overlay + camera + selection)
        ══════════════════════▼══════════════════════  ← PLATFORM SEAM (later UI phase)
             Native MapLibre Native SDK  (Android: Compose AndroidView + MapView,
                                          iOS: SwiftUI UIViewRepresentable + MLNMapView)
             builds style from tile path, draws polyline/markers/puck/breadcrumb, fits camera
```

**Data flow**: `TileStore` turns FR-11 preparation + file existence into a `MapTileSourceState`. `MapViewModel.bind(routeId, runId?)` combines that with the route/log/location flows, runs pure mappers to build `MapOverlay` + `MapCamera`, and publishes one `MapUiState`. Native code observes `MapUiState` and renders. Nothing in shared touches MapLibre.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Where tile discovery lives | New FR-06 `TileStore` (interface in `domain/map`, impl in `data/map`) over `FileStorage` + `PreparationRepository` | FR-11 plan explicitly defers a clean tile accessor to FR-06; keeps FR-06 self-contained and FR-11 unchanged |
| How to find the tile file path | Reconstruct from FR-11's deterministic convention `tiles/<tilesetId>.<format>` + validate with `FileStorage.exists()` | `DownloadItemProgress` (what `loadAssets()` returns) carries the id (`tiles:<id>`) and `DONE` status but **not** `relativePath`; the naming convention is fixed by FR-11 so reconstruction is safe. See §12.2 open question |
| Tile readiness gate | `PreparationStatus.READY` **and** asset `DownloadStatus.DONE` **and** file exists | Belt-and-suspenders: DB says done + bytes physically present; a DONE row with a missing file → `NotDownloaded` (needs re-download) |
| Marker model | Thin `CheckpointMarker` projection (control fields + `ControlPointState` + `isNext`) | A map-layer view of `RunLogEntry`; keeps native code from reaching into FR-04's log model and computing "next" itself. Not a duplicate *coordinate/control* model — it references `GeoPoint`/`ControlPointState` |
| Markers before a run starts | Use FR-04's pure `ControlLogDeriver.deriveLog(controls, emptyList(), null)` when `runId == null` | Single consistent state source (first control ACTIVE, rest LOCKED) whether or not a run is active; no new logic |
| Route polyline geometry | Straight segments between ordered `ControlPoint.location` | FR-02/03 store **no** route path geometry — only control points. Real path needs a new geometry field upstream (out of scope, see §12) |
| VM composition | `MapViewModel` depends on **use-cases/streams**, not on FR-04/FR-05 ViewModels | Avoids fragile ViewModel-of-ViewModels binding/lifecycle; FR-05 plan explicitly allows injecting `LocationStream` + `ObserveTrackUseCase` directly |
| Pure vs impure split | Pure mappers/selectors in `domain/map`; only I/O (file existence) + flow wiring in `data`/`presentation` | Requirement #4 — everything decision-shaped is unit-testable in `commonTest` |
| MapLibre integration | Entirely in native UI modules (`androidApp`, `iosApp`), no shared dependency, no `expect/actual` for rendering | Alza-style: shared core is UI-agnostic; one MapLibre Native SDK per platform consumes `MapUiState` |

### 3.3 New Types (all `com.example.ta33`, `:shared/commonMain`)

```kotlin
// domain/model/MapTileSource.kt
enum class TileFormat(val ext: String) {
    MBTILES("mbtiles"),
    UNKNOWN("");
    companion object {
        fun fromRaw(raw: String): TileFormat =
            entries.firstOrNull { it.ext.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

/** Describes a downloaded, on-disk offline tile source handed to the native MapLibre layer. */
data class MapTileSource(
    val tilesetId: String,      // e.g. "adrspach-teplice"
    val absolutePath: String,   // full filesystem path: FileStorage.baseDir()/tiles/<id>.<ext>
    val format: TileFormat,
)

/** Availability of the offline basemap. `NotDownloaded` == UI shows "mapa nestažena". */
sealed interface MapTileSourceState {
    data object NotDownloaded : MapTileSourceState
    data object Preparing : MapTileSourceState
    data class Ready(val source: MapTileSource) : MapTileSourceState
    data class Error(val message: String?) : MapTileSourceState
}
```

```kotlin
// domain/model/MapCamera.kt
data class GeoBoundingBox(val southWest: GeoPoint, val northEast: GeoPoint) {
    val center: GeoPoint get() = GeoPoint(
        latitude = (southWest.latitude + northEast.latitude) / 2.0,
        longitude = (southWest.longitude + northEast.longitude) / 2.0,
    )
}

/** Initial camera target. Native fits `bounds` on first load, then may follow `focus`. */
data class MapCamera(
    val bounds: GeoBoundingBox?,   // null when route has < 1 point
    val focus: GeoPoint,           // live position if available, else bounds center, else fallback
)
```

```kotlin
// domain/model/MapOverlay.kt
/** A single checkpoint as the native marker layer needs it (projection of RunLogEntry). */
data class CheckpointMarker(
    val controlId: String,
    val ordinal: Int,
    val name: String,
    val location: GeoPoint,
    val radiusMeters: Double,
    val state: ControlPointState,   // FR-04 → drives native marker color/style
    val isNext: Boolean,            // matches LogUiState.nextControl → native highlight
)

/** Everything the native map draws on top of the basemap. */
data class MapOverlay(
    val routePolyline: List<GeoPoint> = emptyList(),   // ordered control locations (see §7)
    val checkpointMarkers: List<CheckpointMarker> = emptyList(),
    val livePosition: GeoPosition? = null,             // FR-05 live dot + accuracy radius
    val breadcrumb: List<GeoPoint> = emptyList(),      // FR-05 track.map { it.location }
)
```

```kotlin
// presentation/MapUiState.kt
/** Complete description of what the native MapLibre view must render. */
data class MapUiState(
    val tileSource: MapTileSourceState = MapTileSourceState.NotDownloaded,
    val overlay: MapOverlay = MapOverlay(),
    val camera: MapCamera? = null,          // initial camera; null until route loaded
    val isRouteLoaded: Boolean = false,
    val selectedMarkerId: String? = null,   // from onMarkerTapped
    val loading: Boolean = true,
)
```

```kotlin
// domain/map/MapTileConfig.kt
/** Small config for the basemap tileset. Etapa 1 = single Adršpach/Teplice basemap. */
data class MapTileConfig(
    val preferredBasemapTilesetId: String? = null,  // null → pick the single DONE tiles asset
    val tilesSubDir: String = "tiles",
    val defaultFormat: TileFormat = TileFormat.MBTILES,
    /** Fallback camera focus when no route/live position (Adršpach rock town centre). */
    val fallbackFocus: GeoPoint = GeoPoint(50.6156, 16.1122),
)
```

### 3.4 Platform Seam (later native/UI phase — NOT implemented here)

The native map view (per platform, one MapLibre Native SDK) observes `MapUiState` and does all rendering. Contract for the future UI phase:

| `MapUiState` field | Native MapLibre action |
|--------------------|------------------------|
| `tileSource: Ready(source)` | Build a MapLibre style referencing the local file `source.absolutePath` (e.g. `mbtiles://` / file source per `source.format`); add as the base raster/vector source. Style JSON construction is **native** |
| `tileSource: NotDownloaded` | Show "mapa nestažena" empty state (offer FR-11 download); do not create map source |
| `tileSource: Preparing` | Show loading state |
| `tileSource: Error(msg)` | Show error/offer retry |
| `overlay.routePolyline` | `LineLayer` from a GeoJSON `LineString` built out of the points |
| `overlay.checkpointMarkers` | `SymbolLayer`/`CircleLayer`; color/icon by `state` (LOCKED/ACTIVE/DONE/FINISH), emphasize `isNext`; optional accuracy/geofence circle from `radiusMeters` |
| `overlay.livePosition` | Location puck at `location`, accuracy circle from `accuracyMeters` |
| `overlay.breadcrumb` | Second `LineLayer` (trail behind the participant) |
| `camera` | On first load fit `bounds`; if null, center on `focus` at a default zoom; then follow live position |
| `onMarkerTapped(GeoPoint)` | Native tap handler converts the map coordinate to `GeoPoint` and calls the VM; `selectedMarkerId` then drives the native highlight/callout |

Coordinate adaptation (`GeoPoint(latitude, longitude)` → `LatLng` on Android / `CLLocationCoordinate2D` on iOS) happens **only** in the native modules. The MapLibre dependency is added to `androidApp` and `iosApp` only — never to `:shared`.

---

## 4. IMPLEMENTATION STEPS

> Execute steps in order. Do not skip. Prerequisite: FR-02/03/04/05/11 contracts exist in code (this plan targets their planned shapes; if a signature drifted, adapt the call site, not the model).

### Step 1: Add tile-source domain models
**Goal**: Descriptor + availability state + config for the offline basemap.
**Files**:
- `shared/src/commonMain/kotlin/com/example/ta33/domain/model/MapTileSource.kt`
- `shared/src/commonMain/kotlin/com/example/ta33/domain/map/MapTileConfig.kt`

Create `TileFormat`, `MapTileSource`, `MapTileSourceState` and `MapTileConfig` exactly as in §3.3.

**Done when**: Files compile; no MapLibre/UI imports.

---

### Step 2: Add the pure `TileSourceSelector`
**Goal**: Decide the tile state from preparation status + asset list **without** touching the filesystem (I/O deferred to the impl in Step 3).
**Files**: `shared/src/commonMain/kotlin/com/example/ta33/domain/map/TileSourceSelector.kt`

```kotlin
package com.example.ta33.domain.map

import com.example.ta33.domain.download.DownloadItemProgress
import com.example.ta33.domain.download.DownloadStatus
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.model.TileFormat

/** Pure selection: which tileset (if any) should be used, and what state applies. */
object TileSourceSelector {

    /** Result carries only the *relative* path + id; the impl resolves absolute path + file existence. */
    sealed interface Selection {
        data object NotDownloaded : Selection
        data object Preparing : Selection
        data class Candidate(
            val tilesetId: String,
            val relativePath: String,   // "tiles/<id>.<ext>"
            val format: TileFormat,
        ) : Selection
        data class Error(val message: String?) : Selection
    }

    fun select(
        preparation: PreparationState,
        assets: List<DownloadItemProgress>,
        config: com.example.ta33.domain.map.MapTileConfig,
    ): Selection {
        val tileAssets = assets.filter { it.id.startsWith("tiles:") }
        val doneTiles = tileAssets.filter { it.status == DownloadStatus.DONE }

        return when (preparation.status) {
            PreparationStatus.ERROR -> Selection.Error(preparation.let { null })
            PreparationStatus.NOT_STARTED -> Selection.NotDownloaded
            PreparationStatus.PREPARING ->
                pickCandidate(doneTiles, config) ?: Selection.Preparing
            PreparationStatus.READY ->
                pickCandidate(doneTiles, config) ?: Selection.NotDownloaded
        }
    }

    private fun pickCandidate(
        doneTiles: List<DownloadItemProgress>,
        config: com.example.ta33.domain.map.MapTileConfig,
    ): Selection.Candidate? {
        val chosen = when (val preferred = config.preferredBasemapTilesetId) {
            null -> doneTiles.singleOrNull() ?: doneTiles.firstOrNull()
            else -> doneTiles.firstOrNull { it.id == "tiles:$preferred" }
        } ?: return null
        val tilesetId = chosen.id.removePrefix("tiles:")
        val ext = config.defaultFormat.ext
        return Selection.Candidate(
            tilesetId = tilesetId,
            relativePath = "${config.tilesSubDir}/$tilesetId.$ext",
            format = config.defaultFormat,
        )
    }
}
```

**Done when**: Compiles; ready for unit tests in Step 9.

---

### Step 3: Add `TileStore` interface + impl (file existence validation)
**Goal**: Turn the selection into an observable `MapTileSourceState`, performing the only impure check (`FileStorage.exists`) and building the absolute path.
**Files**:
- `shared/src/commonMain/kotlin/com/example/ta33/domain/map/TileStore.kt`
- `shared/src/commonMain/kotlin/com/example/ta33/data/map/TileStoreImpl.kt`

```kotlin
// domain/map/TileStore.kt
interface TileStore {
    fun observeTileSource(): Flow<MapTileSourceState>
    suspend fun resolveTileSource(): MapTileSourceState
}
```

```kotlin
// data/map/TileStoreImpl.kt
class TileStoreImpl(
    private val fileStorage: FileStorage,                 // FR-11
    private val preparationRepository: PreparationRepository, // FR-11
    private val config: MapTileConfig,
) : TileStore {

    override fun observeTileSource(): Flow<MapTileSourceState> =
        preparationRepository.observePreparationState().map { prep -> resolveFor(prep) }

    override suspend fun resolveTileSource(): MapTileSourceState =
        resolveFor(preparationRepository.getPreparationState())

    private suspend fun resolveFor(prep: PreparationState): MapTileSourceState {
        val assets = preparationRepository.loadAssets()
        return when (val sel = TileSourceSelector.select(prep, assets, config)) {
            TileSourceSelector.Selection.NotDownloaded -> MapTileSourceState.NotDownloaded
            TileSourceSelector.Selection.Preparing -> MapTileSourceState.Preparing
            is TileSourceSelector.Selection.Error -> MapTileSourceState.Error(sel.message)
            is TileSourceSelector.Selection.Candidate -> {
                // DB says DONE — confirm the bytes are physically present.
                if (fileStorage.exists(sel.relativePath)) {
                    val abs = fileStorage.baseDir().trimEnd('/') + "/" + sel.relativePath
                    MapTileSourceState.Ready(MapTileSource(sel.tilesetId, abs, sel.format))
                } else {
                    // DONE row but missing file → treat as needs (re)download.
                    MapTileSourceState.NotDownloaded
                }
            }
        }
    }
}
```

**Done when**: Compiles; `observeTileSource()` re-emits whenever preparation state changes.

---

### Step 4: Add pure geometry helpers — `RouteBounds` + `MapCameraCalculator`
**Goal**: Bounding box + initial camera from ordered route points (requirement #4).
**Files**:
- `shared/src/commonMain/kotlin/com/example/ta33/domain/map/RouteBounds.kt`
- `shared/src/commonMain/kotlin/com/example/ta33/domain/map/MapCameraCalculator.kt`

```kotlin
// RouteBounds.kt
object RouteBounds {
    /** null for empty input; degenerate (SW==NE) box for a single point. */
    fun boundingBox(points: List<GeoPoint>): GeoBoundingBox? {
        if (points.isEmpty()) return null
        var minLat = points.first().latitude; var maxLat = minLat
        var minLon = points.first().longitude; var maxLon = minLon
        for (p in points) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }
        return GeoBoundingBox(GeoPoint(minLat, minLon), GeoPoint(maxLat, maxLon))
    }
}

// MapCameraCalculator.kt
object MapCameraCalculator {
    fun initialCamera(
        routePoints: List<GeoPoint>,
        live: GeoPosition?,
        fallbackFocus: GeoPoint,
    ): MapCamera {
        val bounds = RouteBounds.boundingBox(routePoints)
        val focus = live?.location ?: bounds?.center ?: fallbackFocus
        return MapCamera(bounds = bounds, focus = focus)
    }
}
```

**Done when**: Compiles. (Antimeridian not handled — Czech Republic, see §7.)

---

### Step 5: Add pure `OverlayMapper`
**Goal**: Map FR-04 `LogUiState` + FR-05 data into `MapOverlay` pieces (requirement #4).
**Files**: `shared/src/commonMain/kotlin/com/example/ta33/domain/map/OverlayMapper.kt`

```kotlin
object OverlayMapper {
    fun toCheckpointMarkers(log: LogUiState): List<CheckpointMarker> =
        log.entries.map { entry ->
            CheckpointMarker(
                controlId = entry.control.id,
                ordinal = entry.control.ordinal,
                name = entry.control.name,
                location = entry.control.location,
                radiusMeters = entry.control.radiusMeters,
                state = entry.state,
                isNext = entry.control.id == log.nextControl?.id,
            )
        }

    /** Ordered control locations. NOTE: straight segments, not a real trail path (see plan §7). */
    fun toRoutePolyline(log: LogUiState): List<GeoPoint> =
        log.entries.sortedBy { it.control.ordinal }.map { it.control.location }

    fun toBreadcrumb(track: List<Trackpoint>): List<GeoPoint> = track.map { it.location }
}
```

**Done when**: Compiles.

---

### Step 6: Add `MapUiState` + `MapViewModel`
**Goal**: One ViewModel combining tile-source + route/log + live position + track + selection into `MapUiState`.
**Files**:
- `shared/src/commonMain/kotlin/com/example/ta33/presentation/MapUiState.kt`
- `shared/src/commonMain/kotlin/com/example/ta33/presentation/MapViewModel.kt`

```kotlin
class MapViewModel(
    private val tileStore: TileStore,
    private val observeRouteDetail: ObserveRouteDetailUseCase, // FR-03
    private val observeRunLog: ObserveRunLogUseCase,           // FR-04
    private val observeTrack: ObserveTrackUseCase,             // FR-05
    private val locationStream: LocationStream,                // FR-05
    private val config: MapTileConfig,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private val selectedMarkerId = MutableStateFlow<String?>(null)
    private var job: Job? = null

    /** @param runId null while browsing a route before a run has started. */
    fun bind(routeId: String, runId: String?) {
        job?.cancel()

        // Log source: real run log if a run exists, else derive states from the route's controls.
        val logFlow: Flow<LogUiState> =
            if (runId != null) observeRunLog(runId, routeId)
            else observeRouteDetail(routeId).map { detail ->
                val controls = detail?.controls ?: emptyList()
                ControlLogDeriver.deriveLog(controls, emptyList(), null)
            }

        val trackFlow: Flow<List<Trackpoint>> =
            if (runId != null) observeTrack(runId) else flowOf(emptyList())

        // Live position: start with null so the combined state isn't blocked on the first GPS fix.
        val positionFlow: Flow<GeoPosition?> =
            locationStream.positions().map<GeoPosition, GeoPosition?> { it }.onStart { emit(null) }

        job = combine(
            tileStore.observeTileSource(),
            logFlow,
            positionFlow,
            trackFlow,
            selectedMarkerId,
        ) { tile, log, position, track, selected ->
            val polyline = OverlayMapper.toRoutePolyline(log)
            MapUiState(
                tileSource = tile,
                overlay = MapOverlay(
                    routePolyline = polyline,
                    checkpointMarkers = OverlayMapper.toCheckpointMarkers(log),
                    livePosition = position,
                    breadcrumb = OverlayMapper.toBreadcrumb(track),
                ),
                camera = MapCameraCalculator.initialCamera(polyline, position, config.fallbackFocus),
                isRouteLoaded = polyline.isNotEmpty(),
                selectedMarkerId = selected,
                loading = false,
            )
        }.onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    fun onMarkerTapped(tap: GeoPoint, maxMeters: Double = 60.0) {
        val marker = MarkerSelector.selectNearest(_state.value.overlay.checkpointMarkers, tap, maxMeters)
        selectedMarkerId.value = marker?.controlId
    }

    fun clearSelection() { selectedMarkerId.value = null }
}
```

> Adjust `observeRunLog(...)` / `observeRouteDetail(...)` / `observeTrack(...)` to their real invoke signatures (FR-03/04/05 use `operator fun invoke`). If FR-03 uses `RouteRepository.observeRouteWithControls`, swap the source but keep the mapping.

**Done when**: Compiles; `bind` can be called repeatedly (cancels the previous job).

---

### Step 7: Add pure `MarkerSelector`
**Goal**: Nearest-marker-to-tap selection (requirement #4).
**Files**: `shared/src/commonMain/kotlin/com/example/ta33/domain/map/MarkerSelector.kt`

```kotlin
object MarkerSelector {
    /** Nearest marker within `maxMeters`, or null. Uses FR-02 GeoUtils Haversine. */
    fun selectNearest(markers: List<CheckpointMarker>, tap: GeoPoint, maxMeters: Double): CheckpointMarker? =
        markers
            .map { it to GeoUtils.distanceMeters(it.location, tap) }
            .filter { it.second <= maxMeters }
            .minByOrNull { it.second }
            ?.first
}
```

**Done when**: Compiles (depends on FR-02 `GeoUtils.distanceMeters`).

---

### Step 8: Register in Koin + Swift accessor
**Goal**: DI wiring following existing conventions.
**Files**:
- `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt`
- `shared/src/commonMain/kotlin/com/example/ta33/di/Koin.kt`

```kotlin
// AppModule.kt (add)
single { MapTileConfig() }
single<TileStore> { TileStoreImpl(get(), get(), get()) }   // FileStorage, PreparationRepository, MapTileConfig
factory { MapViewModel(get(), get(), get(), get(), get(), get()) }
```

```kotlin
// Koin.kt → object ViewModelProvider (add)
fun mapViewModel(): MapViewModel = KoinPlatform.getKoin().get()
```

**Done when**: `./gradlew build` succeeds; `TileStore` + `MapViewModel` resolve. Depends on FR-11 registering `FileStorage` + `PreparationRepository` and FR-03/04/05 registering their use-cases + `LocationStream`.

---

### Step 9: Unit tests in `commonTest`
**Goal**: Cover all pure logic + the tile state machine (requirements #2, #3, #4, #5).
**Files** (`shared/src/commonTest/kotlin/com/example/ta33/map/`):
- `TileSourceSelectorTest.kt` — READY+DONE→Candidate; READY+no DONE→NotDownloaded; PREPARING (no done)→Preparing; PREPARING+DONE→Candidate; NOT_STARTED→NotDownloaded; ERROR→Error; preferred id match/miss; multiple done tiles.
- `TileStoreImplTest.kt` — fake `FileStorage` (exists true/false) + fake `PreparationRepository`: Candidate+exists→Ready(absolutePath correct); Candidate+missing file→NotDownloaded; `observeTileSource` re-emits on preparation change.
- `RouteBoundsTest.kt` — empty→null; single→degenerate; multi→min/max; center.
- `MapCameraCalculatorTest.kt` — focus = live when present; = bounds center when no live; = fallback when no route & no live.
- `OverlayMapperTest.kt` — marker state/`isNext` mapping; polyline ordered by ordinal; breadcrumb from track.
- `MarkerSelectorTest.kt` — nearest within radius; none beyond radius; tie → deterministic; empty list→null.
- `MapViewModelTest.kt` — fakes for all use-cases/streams; assert combined `MapUiState` (runId set → real states; runId null → derived states + empty breadcrumb; `onMarkerTapped` sets `selectedMarkerId`). Use `kotlinx-coroutines-test` `runTest` + a test dispatcher for `viewModelScope`.

**Done when**: `./gradlew :shared:allTests` passes; branch coverage of `domain/map` ≥ 90%.

---

### Step 10: KDoc the native seam + verify no forbidden imports
**Goal**: Make the platform boundary explicit in code.
**Files**: KDoc on `MapUiState` and `MapTileSource` pointing to §3.4 of this plan.

**Done when**:
- `grep -rE "maplibre|org.maplibre|androidx.compose|import SwiftUI" shared/src` → no matches.
- `./gradlew build` and `./gradlew :shared:allTests` green.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| No tiles ever downloaded | `MapTileSourceState.NotDownloaded` → native "mapa nestažena" | `TileSourceSelector` returns `NotDownloaded` for NOT_STARTED / READY-without-DONE |
| Preparation still running, no tile yet | `Preparing` | `PREPARING` + no DONE tile |
| Preparation running but tile already DONE | `Ready` early | `pickCandidate` succeeds even in PREPARING |
| Asset row `DONE` but file deleted/missing | `NotDownloaded` (prompt re-download) | `FileStorage.exists()` false in `TileStoreImpl` |
| Multiple downloaded tilesets | Pick preferred id, else the single/first DONE tile | `pickCandidate`; document that Etapa 1 has one basemap |
| Route with 0 controls | Empty polyline, `camera.bounds == null`, `focus = fallbackFocus`, `isRouteLoaded=false` | `RouteBounds` null-safe; `MapCameraCalculator` fallback |
| Route with 1 control | Degenerate bbox (SW==NE); native uses default zoom on `focus` | `boundingBox` returns SW==NE |
| No active run (runId null) | Markers derived (first ACTIVE, rest LOCKED), empty breadcrumb | `ControlLogDeriver.deriveLog(controls, [], null)` + `flowOf(emptyList())` |
| Location permission denied / no GPS fix | `livePosition == null`, no puck; breadcrumb from persisted track still shows | `positionFlow` starts with `null` via `onStart` |
| GPS fix arrives after route loaded | Camera focus recomputed to live position on next emission | Combined flow recomputes `initialCamera` each emit (native still owns continuous follow) |
| `bind` called again (route/run switch) | Previous combine cancelled, fresh state | `job?.cancel()` at start of `bind` |
| Marker tap far from any control | `selectedMarkerId = null` | `MarkerSelector` radius filter |
| `PreparationRepository.loadAssets()` throws | Surface as `Error`; do not crash the map | Wrap `resolveFor` body in try/catch → `Error(e.message)` (add in impl) |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: Tile file paths are built only from FR-11's fixed convention (`tiles/<tilesetId>.<ext>`) and a config-supplied subdir — no user-supplied path segments, so no path-traversal surface. `tilesetId` originates from FR-11's manifest/DB (trusted content channel).
- **Auth/Access control**: None — Etapa 1 is anonymous, all data is local.
- **Sensitive data**: Live GPS position + breadcrumb are personal location data but stay in memory / local DB (FR-05) and are only surfaced to the native map layer; FR-06 neither persists nor transmits them. No new storage.
- **Logging**: Log tile-state transitions and file-missing warnings without logging full absolute paths at info level (paths in debug only). Never log GPS coordinates.

---

## 7. ASSUMPTIONS

> User opted out of clarification ("skip questions" / "proceed directly"). Accepted defaults logged below.

1. **FR-02/03/04/05/11 land as their plans describe** — this plan targets planned contracts; none are in code yet (repo is the Greeting skeleton). Impact if a signature drifts: adjust the call site/mapper, not the FR-06 models.
2. **Tile file naming is `tiles/<tilesetId>.<format>` with default `mbtiles`** (from FR-11) — `TileSourceSelector` reconstructs the relative path from this. Impact if wrong: `FileStorage.exists()` fails → false `NotDownloaded`; fix by exposing `relativePath` (see §12.2).
3. **`DownloadItemProgress` (what `loadAssets()` returns) carries `id` + `status` but not `relativePath`/`format`** — hence reconstruction by convention. If FR-11 later exposes `relativePath`, prefer it.
4. **Single basemap tileset for Etapa 1** (Adršpach/Teplice) — `preferredBasemapTilesetId` may stay null; selector picks the one DONE tile.
5. **No route path geometry exists** — polyline = straight segments between ordered `ControlPoint.location`. A true trail path is out of scope (needs a new geometry field upstream).
6. **No antimeridian / pole handling** — event area is a small region in the Czech Republic; simple min/max bbox is correct there.
7. **MapLibre style JSON is a native concern** — shared only provides the tile file path + format; native builds the style.
8. **`MapViewModel` is bound with a `routeId`** resolved by the caller from `AppUiState.activeRouteId` (FR-01) / `ObserveSelectedRouteUseCase` (FR-03); FR-06 does not re-implement active-route resolution.

---

## 8. QUICK REFERENCE

### Files to Modify
- `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt` — register `MapTileConfig`, `TileStore`, `MapViewModel`
- `shared/src/commonMain/kotlin/com/example/ta33/di/Koin.kt` — add `ViewModelProvider.mapViewModel()`

### Files to Create
- `domain/model/MapTileSource.kt` — `TileFormat`, `MapTileSource`, `MapTileSourceState`
- `domain/model/MapCamera.kt` — `GeoBoundingBox`, `MapCamera`
- `domain/model/MapOverlay.kt` — `CheckpointMarker`, `MapOverlay`
- `domain/map/MapTileConfig.kt` — config
- `domain/map/TileSourceSelector.kt` — pure tile selection
- `domain/map/TileStore.kt` — interface
- `domain/map/RouteBounds.kt` — pure bbox
- `domain/map/MapCameraCalculator.kt` — pure camera
- `domain/map/OverlayMapper.kt` — pure overlay mapping
- `domain/map/MarkerSelector.kt` — pure marker selection
- `data/map/TileStoreImpl.kt` — file-validating impl
- `presentation/MapUiState.kt` — aggregated state
- `presentation/MapViewModel.kt` — aggregating ViewModel
- `commonTest/.../map/*Test.kt` — 7 test files (Step 9)

### Dependencies
- No new libraries in `:shared`. Reuses existing `kotlinx-coroutines`, `androidx.lifecycle` ViewModel, Koin, `kotlinx-coroutines-test` (test).
- MapLibre Native SDK: added **later**, in `androidApp` + `iosApp` only.

### Commands
```bash
# Build shared
./gradlew build
# Run shared unit tests
./gradlew :shared:allTests
# Verify no map/UI leakage into shared
grep -rE "maplibre|org.maplibre|androidx.compose|import SwiftUI" shared/src || echo "clean"
```

---

## 11. CHANGELOG

| Date | Change |
|------|--------|
| 2026-07-09 | Initial plan created |

---

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES

### 12.1 Alternative Approaches Considered

| Approach | Pros | Cons | Selected? |
|----------|------|------|-----------|
| **A. `MapViewModel` aggregates underlying use-cases + streams + pure mappers; `TileStore` over FileStorage/PreparationRepository** | Testable pure core; no ViewModel-of-ViewModels; one shared source of truth for the native map; self-contained tile discovery | New `TileStore` + several small types; must track FR-03/04/05 use-case signatures | ✅ |
| **B. `MapViewModel` consumes sibling FR-04/FR-05 *ViewModels* directly** | Reuses their already-combined `UiState` | Fragile VM composition (double `bind`, lifecycle/scope ownership unclear); harder to unit test; couples to VM shapes | — |
| **C. No shared aggregation — native code reads repositories/use-cases and builds the overlay per platform** | Zero new shared types | Aggregation logic duplicated in Kotlin + Swift; bounding box / selection untestable in `commonTest`; violates Alza-style shared-core principle | — |
| **D. Overlay carries raw `List<RunLogEntry>` instead of `CheckpointMarker`** | One fewer type; strictly "no new model" | Native must reach into FR-04's log model and compute "next" itself; leaks FR-04 shape into rendering | — |

**Why the selected approach won**: Approach A keeps every decision (tile readiness, camera, selection) pure and unit-tested in `commonMain` per the project's testing goal, gives the native MapLibre layer a single flat `MapUiState` to render, and honors FR-11's explicit deferral of a tile accessor to FR-06 — without duplicating any coordinate/route/control models.

### 12.2 Open Questions

- [ ] **Should FR-11 expose `DownloadedAsset.relativePath` (and `format`) through a read seam instead of FR-06 reconstructing `tiles/<id>.<ext>`?** — Proposed direction: ship FR-06 now with convention-based reconstruction (robust because the convention is fixed); if FR-11 later adds `relativePath` to its progress/asset read API, switch `TileSourceSelector` to consume it and drop the reconstruction.
- [ ] **Does MapLibre need a separate downloaded style JSON asset, or does native build the style around the mbtiles?** — Proposed direction: Etapa 1 assumes native constructs a minimal style referencing the local mbtiles (no style asset in the FR-11 manifest). Revisit if a vector style file is added to the manifest — then extend `MapTileSource` with an optional `styleAbsolutePath`.
- [ ] **Which route id does the map screen bind to when there is no active run and multiple routes exist?** — Proposed direction: caller passes `AppUiState.activeRouteId` (FR-01 run-aware resolution) / `ObserveSelectedRouteUseCase`; FR-06 stays agnostic. Confirm during the map UI phase.

### 12.3 Suggestions & Follow-ups

- **Real route path polyline**: to draw the actual trail (not straight control-to-control lines), add a `geometry: List<GeoPoint>` field to content ingestion (FR-11), the SQLDelight `Route` schema, the `Route`/`RouteDetail` models, then feed `MapOverlay.routePolyline` from it. Out of scope here; noted for a future FR / content-model revision.
- **Camera follow & zoom policy** (recenter button, zoom-to-fit-route vs follow-me) is a native UI-phase concern that reads `MapUiState.camera`; keep it out of shared.
- **Accuracy/geofence circle**: `CheckpointMarker.radiusMeters` + `GeoPosition.accuracyMeters` are already carried so the native layer can draw the ~50 m geofence (FR-08) and GPS accuracy halo without new shared work.
- **`MapUiState` throttling**: live position emits ~every 2 s (FR-05 `LocationStream`); if recomposition cost is high on the native side, consider `distinctUntilChanged` or a coarser sampling in the native observer, not in shared.
- **Heading/bearing**: FR-05 has no heading field; if a directional puck is wanted later, add `headingDegrees` to FR-05's `GeoPosition` first, then surface via `MapOverlay`.
