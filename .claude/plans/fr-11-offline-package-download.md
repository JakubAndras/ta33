# FR-11 Offline Package Download — Networking & Orchestration (Logic Only)

> **Summary**: Build the shared, headless logic that downloads the full TA33 offline package (manifest + content JSON → routes/controls persisted via FR-02, plus map tiles into file storage for FR-06), tracks per-item and overall download progress with pause/resume, honours a Wi-Fi-vs-mobile-data preference via platform connectivity detection, and flips the app to "ready offline" (FR-01) on completion — with no UI built.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Before the event, a TA33 participant must pull everything the app needs to work with no signal on the trail: the route(s), the control points, and the offline map. The download can be large, so the app must show progress, let the user choose whether it may run on mobile data or Wi-Fi only, survive interruptions (resume rather than restart), and end in a durable "ready offline" state. Today none of this exists — Ktor is wired into the build but no HTTP client is constructed, there is no content source, no file storage, and no connectivity detection.

### 1.2 Solution Overview
Add a shared, Clean-Architecture download subsystem inside the existing `:shared` module: `@Serializable` DTOs + a Ktor-based remote data source (manifest + content JSON + streamed tile files from a **configurable** base URL), a DTO→domain mapper that persists routes/controls through the **existing FR-02 `RouteRepository.upsertRoute` seam**, an `expect/actual` `FileStorage` for tiles and `ConnectivityMonitor` for network-type detection, a **pure** progress reducer, an `OfflinePackageRepository` + `PrepareOfflinePackageUseCase` that orchestrates the whole run (idempotent + resumable), a persisted `Preparation` state that FR-01 reads to drive `PREPARING`/`READY`, and a headless `DownloadViewModel` exposing a `StateFlow`. No screens, no progress bars, no visuals.

### 1.3 Scope: What This IS
- **DTO layer** (`data/dto`): `@Serializable` `ManifestDto`, `ContentDto`, `RouteDto`, `ControlDto`, `TileSetDto` (+ mappers to FR-02 domain models).
- **Remote data source** (`data/remote`): Ktor `HttpClient` construction (engine injected per platform), `ContentRemoteDataSource` (fetch manifest, fetch content JSON, **stream** a tile file with byte-progress + HTTP `Range` resume), and a configurable `ContentConfig` (base URL + manifest path).
- **File storage** (`data/file`): `FileStorage` interface + Android (`Context.filesDir`) and iOS (`NSDocumentDirectory`) implementations, registered in `platformModule`. Append-capable (for resume) + size/exists/delete. FR-06 consumes the written tiles later.
- **Connectivity** (`data/connectivity`): `ConnectivityMonitor` interface + Android (`ConnectivityManager`/`NetworkCapabilities`) and iOS (`NWPathMonitor`) implementations; exposes current `NetworkType` and a `Flow<NetworkType>`.
- **Progress model + pure reducer** (`domain/download`): `DownloadStatus`, `DownloadItemProgress`, `OfflinePackageProgress`, and a pure `ProgressReducer` (per-item update + overall aggregation + fraction) — fully unit-testable with no I/O.
- **Persisted preparation state**: new SQLDelight `Preparation` (single row) + `DownloadedAsset` tables on the existing `Ta33Database`, behind a `PreparationRepository`.
- **Orchestration**: `OfflinePackageRepository` (domain contract + data impl) and `PrepareOfflinePackageUseCase` (manifest → content download+persist → tile downloads → mark ready), idempotent and resumable, network-preference-gated.
- **Headless `DownloadViewModel`** (`presentation`): `StateFlow<DownloadUiState>` + intents `start / pause / resume / retry / setNetworkPreference`. No UI consumes it here.
- **Koin registration** (`di`) + Swift accessor (`ViewModelProvider.downloadViewModel()`).
- **One coordinated additive seam on FR-01**: `AppStateReducer`/`AppViewModel` read `PreparationRepository.observePreparationState()` so readiness reflects the explicit persisted flag (drives `PREPARING`/`READY`).
- **Unit tests** (`commonTest`) with **Ktor `MockEngine`**: manifest parsing, DTO→domain mapping, progress reducer transitions, connectivity gating, orchestration happy-path + resume, ViewModel state.

### 1.4 Scope: What This IS NOT
- **No UI whatsoever** — no Compose/SwiftUI screens, no progress bars, no network-choice toggles, no "ready" screen. UI is a deliberately deferred later phase.
- **No map rendering / tile *consumption*** — FR-11 only downloads and stores tile files; FR-06 reads them.
- **No redefinition of FR-02** — domain models (`Route`, `ControlPoint`, …), `RouteRepository`, `SyncStatus`, `TimeProvider`, `IdGenerator`, `Ta33Database` are **referenced**, not duplicated. Content is persisted through the existing `upsertRoute` seam.
- **No server upload / sync** — Etapa 1 is download-only, read-only content (project-stack §10). `SyncStatus` stays `PENDING`.
- **No real production content** — the final GPS/route/tile data is not delivered yet; everything is driven off a **configurable URL** + a temporary dev host/file.
- **No background/foreground-service download** — the download runs in the ViewModel's coroutine scope while the app is foregrounded; OS background-download services are a later concern.
- **No new Gradle modules** — everything lives in `:shared` as package layers (project-stack §12).

---

## 2. SUCCESS CRITERIA

Implementation is COMPLETE when ALL criteria are met:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | Project compiles with the new DTOs, remote source, file storage, connectivity, progress model, repositories, use-case and ViewModel | `./gradlew build` succeeds; SQLDelight regenerates with `Preparation`/`DownloadedAsset` tables |
| 2 | A manifest JSON is fetched and parsed into `ManifestDto` from a configurable URL | `ManifestParsingTest` with `MockEngine` |
| 3 | Content JSON maps to FR-02 `Route` + `List<ControlPoint>` and is persisted via `RouteRepository.upsertRoute` | `ContentMappingTest` + orchestration test asserts fake repo received routes/controls |
| 4 | Tiles are streamed to `FileStorage`; a partially downloaded file resumes via HTTP `Range` and does not restart from 0 | `PrepareOfflinePackageUseCaseTest` (resume case) + fake `FileStorage` asserts append offset |
| 5 | Progress reducer computes per-item + overall status and fraction with correct precedence (any ERROR→ERROR, all DONE→DONE, etc.) | `ProgressReducerTest` covering all transitions incl. unknown totals |
| 6 | Download is blocked when preference is Wi-Fi-only and the active network is cellular; allowed on Wi-Fi or when preference permits cellular | `ConnectivityGatingTest` |
| 7 | Completing all items persists `Preparation.status = READY` (+ manifest version, timestamp) | Orchestration test asserts `PreparationRepository.markReady` and final progress `DONE` |
| 8 | Re-running after full completion is a safe no-op (idempotent); re-running after partial completion skips finished items | Orchestration test: second `prepare()` call adds no duplicate routes / re-downloads nothing done |
| 9 | FR-01 readiness reflects the persisted flag: `PREPARING` during download, `READY` after | `AppStateReducer`/`AppViewModel` test with a fake `PreparationRepository` (coordinated seam) |
| 10 | `DownloadViewModel` exposes `StateFlow<DownloadUiState>` and reacts to `start/pause/resume/retry/setNetworkPreference` | `DownloadViewModelTest` (`runTest` + `Dispatchers.setMain`) |
| 11 | All new components resolvable via Koin (engine/storage/connectivity per platform, rest in `appModule`) | Koin resolution test / app launch; iOS `xcodebuild` compiles the actuals |
| 12 | `./gradlew :shared:allTests` green | Run tests |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
              NATIVE UI (later phase — NOT built here)
              reads DownloadUiState, sends intents (start/pause/…)
        ══════════════════ SHARED CORE (:shared, commonMain) ══════════════════
                          presentation/
                     ┌───────────────────────────┐
                     │      DownloadViewModel     │  StateFlow<DownloadUiState>
                     │      (headless, no UI)      │  intents: start/pause/resume/retry/setPref
                     └─────────────┬──────────────┘
                                   │ observes progress Flow, calls use-case
                     domain/usecase/
                     ┌───────────────────────────┐
                     │ PrepareOfflinePackageUseCase│  manifest→content→tiles→markReady
                     └─────┬───────────────┬──────┘
             domain/download/ (PURE)       │ calls
             ┌────────────────────┐        │
             │   ProgressReducer  │        │
             │ OfflinePackageProg.│        │
             └────────────────────┘        │
                     domain/repository/     ▼
        ┌──────────────────────────┬──────────────────────────┬────────────────────┐
        │ OfflinePackageRepository │ PreparationRepository     │ RouteRepository     │
        │ (fetch/download/stream)  │ (status + resume state)   │ (FR-02 upsertRoute) │
        └───────────┬──────────────┴───────────┬──────────────┴─────────┬──────────┘
     data/remote/   │            data/repository (SQLDelight)            │ (FR-02, referenced)
   ┌────────────────┴───────┐   Preparation.sq / DownloadedAsset.sq      │
   │ ContentRemoteDataSource │            Ta33Database ◀──────────────────┘
   │  (Ktor HttpClient)      │
   │  + ContentConfig(url)   │        data/file/          data/connectivity/
   └────────┬────────────────┘   ┌──────────────┐    ┌──────────────────────┐
            │ injected engine     │  FileStorage │    │  ConnectivityMonitor │
            │ (platformModule)    │  (expect via │    │  (expect via         │
   ┌────────┴──────────┐          │  platformMod)│    │  platformModule)     │
 Android OkHttp  iOS Darwin       └──────┬───────┘    └──────────┬───────────┘
                              Android filesDir / iOS   Android ConnectivityManager /
                              NSDocumentDirectory       iOS NWPathMonitor
```

**Data flow (prepare):** ViewModel `start(pref)` → `PrepareOfflinePackageUseCase` checks `ConnectivityMonitor.current()` against `pref` → fetch manifest (`ContentRemoteDataSource`) → set `Preparation.status = PREPARING` → for the **content** item: download JSON, map DTO→domain, `RouteRepository.upsertRoute(...)`, mark asset done → for **each tileset**: stream file to `FileStorage` (resume from existing size via `Range`), mark asset done → all done → `PreparationRepository.markReady(version, now)` → progress `DONE`. Every step emits an `OfflinePackageProgress` via a `Flow` the ViewModel collects. FR-01's `AppStateReducer` observes `Preparation.status` and maps `PREPARING`/`READY` onto `AppReadiness`.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Where code lives | Existing `:shared` module, new **package layers** (`data/dto`, `data/remote`, `data/file`, `data/connectivity`, `domain/download`, `domain/repository`, `domain/usecase`, `presentation`) | project-stack §12 "layers as packages, not Gradle modules yet"; no duplication |
| Content persistence | Reuse **FR-02 `RouteRepository.upsertRoute(route, controls)`** (FR-02 §12.3 anticipates exactly this) | Zero schema duplication; `INSERT OR REPLACE` makes re-runs idempotent |
| Ktor engine injection | Provide **`HttpClientEngine` in `platformModule`** (Android `OkHttp`, iOS `Darwin`); build the configured `HttpClient` once in `appModule` | Mirrors the existing `SqlDriver` platform-provision pattern; keeps `ContentNegotiation`/JSON config in `commonMain`; lets tests inject `MockEngine` |
| Configurable content source | `ContentConfig(baseUrl, manifestPath)` as a Koin `single` with a dev-placeholder default; manifest lists content + tile URLs (relative URLs resolved against base) | project-stack §10: final data not ready → static JSON at a swappable URL; no code change to repoint |
| File storage | **`interface FileStorage`** in common + platform impls in `platformModule` (Android `Context.filesDir`, iOS `NSDocumentDirectory`) | Same DI pattern as SQLDelight; avoids `expect class` constructor-with-Context friction; append + size enable resume |
| Connectivity | **`interface ConnectivityMonitor`** + platform impls (`ConnectivityManager`/`NWPathMonitor`) exposing `NetworkType` + `Flow<NetworkType>` | Wi-Fi vs mobile-data is a platform capability; interface keeps the gating logic pure/testable |
| Progress aggregation | **Pure `ProgressReducer`** (no coroutines/I/O) over an immutable `OfflinePackageProgress` | Deterministic unit tests; ViewModel/use-case are thin wiring over it |
| Resume strategy | Per-item: content JSON re-fetched whole (small); tile files resumed via **HTTP `Range: bytes=<size>-`**, falling back to full re-download if server returns `200` instead of `206` | Handles multi-hundred-MB tiles over flaky connections; dev static host may not support Range → graceful fallback |
| Persisted preparation | New single-row **`Preparation`** table (status/version/readyAt) + **`DownloadedAsset`** table (per-item bytes/status) on `Ta33Database` | Durable "ready offline" flag (FR-01) + resume bookkeeping survive app kill |
| Readiness source of truth | FR-01 reads **persisted `Preparation.status`** (this plan's coordinated seam), superseding FR-01's interim "routes-present" proxy | Fulfils FR-01 §12.2 open question; `PREPARING` finally has a driver |
| Download concurrency | **Sequential** items (content, then each tileset) | Clean, monotonic progress; simpler cancel/resume; parallelism deferred (§12.1) |
| Pause semantics | Pause = **cancel the download coroutine**; partial file + `DownloadedAsset` rows persist; resume relaunches the use-case which skips done items and Range-resumes the in-flight one | No special "paused connection" state to hold; robust against process death |

---

## 4. IMPLEMENTATION STEPS

> Execute in order. Do not skip. Paths under `shared/src/commonMain/kotlin/com/example/ta33/` unless stated. Base package `com.example.ta33`.
> **Dependency ordering**: assumes **FR-02 implemented** (models, `RouteRepository.upsertRoute`, `Ta33Database`, `TimeProvider`, `IdGenerator`, `appModule`, `kotlinx-coroutines-test`). FR-01 may be implemented in parallel; Step 12 is the coordinated seam.

### Step 1: Add test + serialization dependencies
**Goal**: Ktor `MockEngine` for tests; ensure coroutines-test present.
**Files**: `gradle/libs.versions.toml`, `shared/build.gradle.kts`

`libs.versions.toml` `[libraries]`:
```toml
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
# (FR-02 already adds kotlinx-coroutines-test; add here only if FR-02 not merged)
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
```
`shared/build.gradle.kts` `commonTest.dependencies`:
```kotlin
implementation(libs.kotlin.test)
implementation(libs.kotlinx.coroutines.test)
implementation(libs.ktor.client.mock)
```
**Done when**: `./gradlew :shared:compileTestKotlin*` resolves the new deps. (Ktor client + serialization-json are **already** in `commonMain`; no runtime deps to add.)

---

### Step 2: Add configurable content config + Ktor `HttpClient` provision
**Goal**: A single, configured `HttpClient` with a per-platform engine and a swappable base URL.
**Files**: `data/remote/ContentConfig.kt`, `data/remote/HttpClientFactory.kt`, `di/PlatformModule.kt` (+ `.android.kt`, `.ios.kt`)

```kotlin
// data/remote/ContentConfig.kt
package com.example.ta33.data.remote

/** Configurable content source. Final data not delivered yet (project-stack §10) — repoint by changing baseUrl only. */
data class ContentConfig(
    val baseUrl: String = DEV_PLACEHOLDER_BASE_URL,
    val manifestPath: String = "manifest.json",
) {
    fun manifestUrl(): String = resolve(manifestPath)
    /** Resolve a possibly-relative URL from the manifest against baseUrl. */
    fun resolve(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http")) pathOrUrl else baseUrl.trimEnd('/') + "/" + pathOrUrl.trimStart('/')

    companion object {
        // TODO(content): replace with the organizer's real host when data is delivered.
        const val DEV_PLACEHOLDER_BASE_URL = "https://example.invalid/ta33/"
    }
}
```
```kotlin
// data/remote/HttpClientFactory.kt
package com.example.ta33.data.remote
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createHttpClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    }
```
Add engine to `platformModule` (alongside the existing `SqlDriver`):
```kotlin
// androidMain … PlatformModule.android.kt
single<HttpClientEngine> { OkHttp.create() }
// iosMain … PlatformModule.ios.kt
single<HttpClientEngine> { Darwin.create() }
```
**Done when**: `createHttpClient` compiles in common; each `platformModule` provides an `HttpClientEngine`.

---

### Step 3: Add `@Serializable` DTOs + DTO→domain mapper
**Goal**: Wire format for manifest + content, mapped onto FR-02 models.
**Files**: `data/dto/ManifestDto.kt`, `data/dto/ContentDto.kt`, `data/dto/ContentMappers.kt`

```kotlin
// data/dto/ManifestDto.kt
package com.example.ta33.data.dto
import kotlinx.serialization.Serializable

@Serializable data class ManifestDto(
    val version: Int,
    val content: ContentRefDto,
    val tiles: List<TileSetDto> = emptyList(),
)
@Serializable data class ContentRefDto(val url: String, val bytes: Long? = null, val sha256: String? = null)
@Serializable data class TileSetDto(
    val id: String,            // stable, e.g. "adrspach-teplice"
    val url: String,
    val bytes: Long? = null,   // expected size (nullable if host omits it)
    val format: String = "mbtiles",
    val sha256: String? = null,
)
```
```kotlin
// data/dto/ContentDto.kt
package com.example.ta33.data.dto
import kotlinx.serialization.Serializable

@Serializable data class ContentDto(val routes: List<RouteDto>)
@Serializable data class RouteDto(
    val id: String, val name: String, val distanceKm: Double,
    val controls: List<ControlDto> = emptyList(),
)
@Serializable data class ControlDto(
    val id: String, val ordinal: Int, val name: String,
    val lat: Double, val lon: Double, val radiusMeters: Double = 50.0,
)
```
```kotlin
// data/dto/ContentMappers.kt  → FR-02 domain models
package com.example.ta33.data.dto
import com.example.ta33.domain.model.*

fun RouteDto.toDomain(): Pair<Route, List<ControlPoint>> {
    val controls = controls.map { c ->
        ControlPoint(
            id = c.id, routeId = id, ordinal = c.ordinal, name = c.name,
            location = GeoPoint(c.lat, c.lon), radiusMeters = c.radiusMeters,
        )
    }
    return Route(id = id, name = name, distanceKm = distanceKm, controls = controls) to controls
}
```
**Done when**: DTOs + mapper compile against FR-02 `Route`/`ControlPoint`/`GeoPoint`.

---

### Step 4: Add the pure progress model + `ProgressReducer`
**Goal**: Deterministic, I/O-free progress state.
**Files**: `domain/download/DownloadProgress.kt`, `domain/download/ProgressReducer.kt`

```kotlin
// domain/download/DownloadProgress.kt
package com.example.ta33.domain.download

enum class DownloadStatus { IDLE, DOWNLOADING, PAUSED, DONE, ERROR }

data class DownloadItemProgress(
    val id: String,               // "content" | "tiles:<id>"
    val label: String,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long? = null, // null when host omits content-length
    val error: String? = null,
) {
    val fraction: Double
        get() = when {
            status == DownloadStatus.DONE -> 1.0
            totalBytes == null || totalBytes == 0L -> if (status == DownloadStatus.DOWNLOADING) 0.0 else 0.0
            else -> (bytesDownloaded.toDouble() / totalBytes).coerceIn(0.0, 1.0)
        }
}

data class OfflinePackageProgress(
    val items: List<DownloadItemProgress> = emptyList(),
    val overallStatus: DownloadStatus = DownloadStatus.IDLE,
    val overallFraction: Double = 0.0,
)
```
```kotlin
// domain/download/ProgressReducer.kt
package com.example.ta33.domain.download

object ProgressReducer {
    fun replaceItem(state: OfflinePackageProgress, item: DownloadItemProgress): OfflinePackageProgress {
        val items = state.items.map { if (it.id == item.id) item else it }
            .let { if (it.any { i -> i.id == item.id }) it else it + item }
        return aggregate(state.copy(items = items))
    }

    fun aggregate(state: OfflinePackageProgress): OfflinePackageProgress {
        val items = state.items
        val overall = when {
            items.isEmpty() -> DownloadStatus.IDLE
            items.any { it.status == DownloadStatus.ERROR } -> DownloadStatus.ERROR
            items.all { it.status == DownloadStatus.DONE } -> DownloadStatus.DONE
            items.any { it.status == DownloadStatus.DOWNLOADING } -> DownloadStatus.DOWNLOADING
            items.any { it.status == DownloadStatus.PAUSED } -> DownloadStatus.PAUSED
            else -> DownloadStatus.IDLE
        }
        // Byte-weighted when every item knows its total; else item-count average of per-item fractions.
        val fraction = if (items.isNotEmpty() && items.all { it.totalBytes != null }) {
            val total = items.sumOf { it.totalBytes ?: 0L }
            if (total == 0L) items.count { it.status == DownloadStatus.DONE }.toDouble() / items.size
            else items.sumOf { it.bytesDownloaded }.toDouble() / total
        } else if (items.isNotEmpty()) {
            items.sumOf { it.fraction } / items.size
        } else 0.0
        return state.copy(overallStatus = overall, overallFraction = fraction.coerceIn(0.0, 1.0))
    }
}
```
**Done when**: Compiles; covered by `ProgressReducerTest` (Step 13).

---

### Step 5: Add domain models for network preference/type + preparation state
**Goal**: Small shared enums/models used by gating, VM, and persistence.
**Files**: `domain/model/NetworkPreference.kt`, `domain/model/NetworkType.kt`, `domain/model/PreparationState.kt`

```kotlin
// domain/model/NetworkPreference.kt
package com.example.ta33.domain.model
/** User's Wi-Fi vs mobile-data choice (FR-11). */
enum class NetworkPreference { WIFI_ONLY, WIFI_AND_CELLULAR }
```
```kotlin
// domain/model/NetworkType.kt
package com.example.ta33.domain.model
enum class NetworkType { WIFI, CELLULAR, OTHER, NONE } // OTHER = ethernet/unknown metered-unknown
```
```kotlin
// domain/model/PreparationState.kt
package com.example.ta33.domain.model
enum class PreparationStatus { NOT_STARTED, PREPARING, READY, ERROR }
data class PreparationState(
    val status: PreparationStatus = PreparationStatus.NOT_STARTED,
    val manifestVersion: Int? = null,
    val readyAtMillis: Long? = null,
)
```
**Done when**: Compiles.

---

### Step 6: Add `FileStorage` interface + platform impls
**Goal**: Append-capable file storage for tiles (resume-friendly). FR-06 reads these later.
**Files**: `data/file/FileStorage.kt` (common), `data/file/FileStorage.android.kt` (androidMain), `data/file/FileStorage.ios.kt` (iosMain), `di/PlatformModule*`

```kotlin
// commonMain data/file/FileStorage.kt
package com.example.ta33.data.file
interface FileStorage {
    /** Absolute base dir for downloaded assets (e.g. <files>/offline). */
    fun baseDir(): String
    fun exists(relativePath: String): Boolean
    suspend fun size(relativePath: String): Long          // 0 if absent
    suspend fun append(relativePath: String, bytes: ByteArray) // creates parent dirs; appends for resume
    suspend fun delete(relativePath: String)
}
```
- **Android** (`AndroidFileStorage(private val context: Context)`): base = `File(context.filesDir, "offline")`; `append` via `FileOutputStream(file, /*append=*/true)` on `Dispatchers.Default`; make parent dirs.
- **iOS** (`IosFileStorage`): base = `NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first()` + `/offline`; `append` via `NSFileHandle(forWritingAtPath:)` + `seekToEndOfFile` / create file if missing with `NSFileManager`.
- Register in `platformModule`: Android `single<FileStorage> { AndroidFileStorage(androidContext()) }`; iOS `single<FileStorage> { IosFileStorage() }`.

**Done when**: Interface compiles in common; both actuals compile (`./gradlew build` + iOS `xcodebuild`).

---

### Step 7: Add `ConnectivityMonitor` interface + platform impls
**Goal**: Detect Wi-Fi vs cellular for the network-preference gate.
**Files**: `data/connectivity/ConnectivityMonitor.kt` (common), `.android.kt`, `.ios.kt`, `di/PlatformModule*`

```kotlin
// commonMain data/connectivity/ConnectivityMonitor.kt
package com.example.ta33.data.connectivity
import com.example.ta33.domain.model.NetworkType
import kotlinx.coroutines.flow.Flow
interface ConnectivityMonitor {
    fun current(): NetworkType
    fun observe(): Flow<NetworkType>
}
```
- **Android** (`AndroidConnectivityMonitor(context)`): `getSystemService(ConnectivityManager)`; `current()` reads `activeNetwork` + `getNetworkCapabilities` → `hasTransport(TRANSPORT_WIFI)`→WIFI, `TRANSPORT_CELLULAR`→CELLULAR, else OTHER/NONE; `observe()` = `callbackFlow` over `registerDefaultNetworkCallback` (`onCapabilitiesChanged`/`onLost`) with `awaitClose { unregister }`. **Requires `ACCESS_NETWORK_STATE` permission** (Step 11).
- **iOS** (`IosConnectivityMonitor`): `NWPathMonitor`; `current()` from last path (`usesInterfaceType(.wifi)`→WIFI, `.cellular`→CELLULAR); `observe()` = `callbackFlow` over `pathUpdateHandler` started on a `DispatchQueue`, `awaitClose { monitor.cancel() }`.
- Register both in `platformModule`.

**Done when**: Interface compiles in common; both actuals compile.

---

### Step 8: Extend SQLDelight schema — `Preparation` + `DownloadedAsset`
**Goal**: Durable readiness flag + resume bookkeeping on the existing `Ta33Database`.
**Files**: `shared/src/commonMain/sqldelight/com/example/ta33/data/db/Preparation.sq`, `DownloadedAsset.sq`

```sql
-- Preparation.sq  (single row, id = 1)
CREATE TABLE Preparation (
    id INTEGER NOT NULL PRIMARY KEY DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'NOT_STARTED',
    manifestVersion INTEGER,
    readyAtMillis INTEGER
);
INSERT OR IGNORE INTO Preparation(id, status) VALUES (1, 'NOT_STARTED');
selectPreparation: SELECT * FROM Preparation WHERE id = 1;
upsertPreparation:
INSERT OR REPLACE INTO Preparation(id, status, manifestVersion, readyAtMillis) VALUES (1, ?, ?, ?);
```
```sql
-- DownloadedAsset.sq  (one row per download item)
CREATE TABLE DownloadedAsset (
    itemId TEXT NOT NULL PRIMARY KEY,   -- "content" | "tiles:<id>"
    relativePath TEXT,                  -- null for the non-file content item
    status TEXT NOT NULL DEFAULT 'IDLE',
    bytesDownloaded INTEGER NOT NULL DEFAULT 0,
    totalBytes INTEGER,
    sha256 TEXT,
    updatedAtMillis INTEGER NOT NULL
);
selectAllAssets: SELECT * FROM DownloadedAsset;
selectAsset: SELECT * FROM DownloadedAsset WHERE itemId = ?;
upsertAsset:
INSERT OR REPLACE INTO DownloadedAsset(itemId, relativePath, status, bytesDownloaded, totalBytes, sha256, updatedAtMillis)
VALUES (?, ?, ?, ?, ?, ?, ?);
deleteAllAssets: DELETE FROM DownloadedAsset;
```
> The seed `INSERT OR IGNORE` guarantees a row exists; enums stored as TEXT and parsed in Kotlin (matches FR-02's `SyncStatus.fromDb` convention — add `PreparationStatus.fromDb`/`DownloadStatus.fromDb`).

**Done when**: `./gradlew build` regenerates `PreparationQueries` + `DownloadedAssetQueries`.

---

### Step 9: Add repository contracts + implementations
**Goal**: The download source, the preparation store, and the orchestration seam.
**Files**: `domain/repository/OfflinePackageRepository.kt`, `domain/repository/PreparationRepository.kt`, `data/remote/ContentRemoteDataSource.kt`, `data/repository/OfflinePackageRepositoryImpl.kt`, `data/repository/PreparationRepositoryImpl.kt`

Contracts:
```kotlin
// domain/repository/PreparationRepository.kt
package com.example.ta33.domain.repository
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.download.DownloadItemProgress
import kotlinx.coroutines.flow.Flow
interface PreparationRepository {
    fun observePreparationState(): Flow<PreparationState>
    suspend fun getPreparationState(): PreparationState
    suspend fun setPreparing(manifestVersion: Int)
    suspend fun markReady(manifestVersion: Int)
    suspend fun markError()
    // resume bookkeeping
    suspend fun loadAssets(): List<DownloadItemProgress>
    suspend fun saveAsset(item: DownloadItemProgress, relativePath: String?)
    suspend fun clearAssets()
}
```
```kotlin
// domain/repository/OfflinePackageRepository.kt
package com.example.ta33.domain.repository
import com.example.ta33.data.dto.ManifestDto
interface OfflinePackageRepository {
    suspend fun fetchManifest(): ManifestDto
    /** Downloads content JSON and persists routes/controls via RouteRepository.upsertRoute. */
    suspend fun downloadAndPersistContent(url: String, onBytes: (downloaded: Long, total: Long?) -> Unit)
    /** Streams a tile file to FileStorage; resumes from existing size via HTTP Range. Returns relativePath. */
    suspend fun downloadTileset(
        tilesetId: String, url: String, expectedBytes: Long?,
        onBytes: (downloaded: Long, total: Long?) -> Unit,
    ): String
}
```
`ContentRemoteDataSource` (Ktor): `fetchManifest`/`fetchContent` via `client.get(url).body()`; `downloadStream` via `client.prepareGet(url){ if (offset>0) header(HttpHeaders.Range,"bytes=$offset-") }.execute { resp -> … read resp.bodyAsChannel() in chunks, call onBytes }`. Detect `resp.status == PartialContent (206)` vs `OK (200)` to decide append-vs-truncate on resume.

`OfflinePackageRepositoryImpl(remote, routeRepository /*FR-02*/, fileStorage, prep)`:
- `downloadAndPersistContent`: fetch `ContentDto`; for each `RouteDto` → `toDomain()` → `routeRepository.upsertRoute(route, controls)` (idempotent).
- `downloadTileset`: `relativePath = "tiles/$tilesetId.$ext"`; `offset = fileStorage.size(relativePath)`; stream with `Range`; if server returned 200 (no range), delete + restart from 0; append chunks via `fileStorage.append`; report bytes.

`PreparationRepositoryImpl(db, time /*FR-02 TimeProvider*/)`: wraps `PreparationQueries`/`DownloadedAssetQueries`; `observePreparationState()` via `.asFlow().mapToOneOrNull(Dispatchers.Default)` + parse; writes on `Dispatchers.Default`.

**Done when**: All compile; use exact generated query-accessor names (verify after first build).

---

### Step 10: Add `PrepareOfflinePackageUseCase` + `ObservePreparationStateUseCase`
**Goal**: Orchestrate the full run; idempotent, resumable, network-gated; expose a progress `Flow`.
**Files**: `domain/usecase/PrepareOfflinePackageUseCase.kt`, `domain/usecase/ObservePreparationStateUseCase.kt`

```kotlin
package com.example.ta33.domain.usecase
// … imports
sealed interface PrepareResult {
    data object Success : PrepareResult
    data object BlockedByNetwork : PrepareResult
    data class Failed(val itemId: String, val cause: Throwable) : PrepareResult
}

class PrepareOfflinePackageUseCase(
    private val offline: OfflinePackageRepository,
    private val prep: PreparationRepository,
    private val connectivity: ConnectivityMonitor,
) {
    /** Emits progress as it runs; terminal emission carries overallStatus DONE/ERROR. */
    fun run(preference: NetworkPreference): Flow<OfflinePackageProgress> = flow {
        if (!networkAllows(preference, connectivity.current())) {
            emit(OfflinePackageProgress(overallStatus = DownloadStatus.PAUSED)); return@flow
        }
        val manifest = offline.fetchManifest()
        prep.setPreparing(manifest.version)

        // Build item list = content + tilesets; seed from persisted assets for resume.
        var state = seedState(manifest, prep.loadAssets())
        emit(state)

        // content item (skip if already DONE)
        state = downloadItem(state, id = "content", skipIfDone = true) { onBytes ->
            offline.downloadAndPersistContent(manifest.content.url, onBytes); null
        }.also { emit(it) }

        // tilesets
        for (t in manifest.tiles) {
            state = downloadItem(state, id = "tiles:${t.id}", skipIfDone = true) { onBytes ->
                offline.downloadTileset(t.id, t.url, t.bytes, onBytes)
            }.also { emit(it) }
            if (state.overallStatus == DownloadStatus.ERROR) break
        }

        if (state.overallStatus == DownloadStatus.DONE) prep.markReady(manifest.version)
        else prep.markError()
        emit(state)
    }
    // helpers: networkAllows(pref, type), seedState(...), downloadItem(...) update via ProgressReducer,
    // persist each item to prep.saveAsset(...) after status changes; wrap the body in try/catch → item ERROR.
    companion object {
        fun networkAllows(pref: NetworkPreference, type: NetworkType): Boolean = when (pref) {
            NetworkPreference.WIFI_ONLY -> type == NetworkType.WIFI
            NetworkPreference.WIFI_AND_CELLULAR -> type == NetworkType.WIFI || type == NetworkType.CELLULAR
        }
    }
}
```
> `networkAllows` is a pure `companion` function → directly unit-tested (Step 13). Cancellation (pause) propagates through the `flow` coroutine; persisted `DownloadedAsset` rows enable the next `run(...)` to resume/skip.

`ObservePreparationStateUseCase(prep) = prep.observePreparationState()` — thin, consumed by FR-01 (Step 12) and the ViewModel.

**Done when**: Compiles; behaviour locked by Step 13 tests.

---

### Step 11: Add Android manifest permissions + Koin registration
**Goal**: Grant network/state access; make everything resolvable.
**Files**: `androidApp/src/main/AndroidManifest.xml`, `di/AppModule.kt`, `di/Koin.kt`

AndroidManifest (`<manifest>` level):
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```
`appModule` (mirrors FR-02 style):
```kotlin
single { ContentConfig() }                                   // dev default; repoint via baseUrl
single { createHttpClient(get()) }                           // engine from platformModule
single { ContentRemoteDataSource(get(), get()) }             // HttpClient, ContentConfig
single<PreparationRepository> { PreparationRepositoryImpl(get(), get()) } // db, TimeProvider
single<OfflinePackageRepository> { OfflinePackageRepositoryImpl(get(), get(), get(), get()) } // remote, RouteRepository, FileStorage, prep
factory { PrepareOfflinePackageUseCase(get(), get(), get()) }
factory { ObservePreparationStateUseCase(get()) }
factory { DownloadViewModel(get(), get(), get()) }
```
`Koin.kt` `ViewModelProvider`: `fun downloadViewModel(): DownloadViewModel = KoinPlatform.getKoin().get()`.
(iOS Info.plist needs no special key — standard HTTPS is allowed under ATS; document only if a plaintext dev host is used.)

**Done when**: `./gradlew build` passes; Koin resolves (Step 13 / app launch); iOS builds.

---

### Step 12: Coordinated FR-01 seam — readiness from persisted `Preparation`
**Goal**: Make the persisted flag the readiness source of truth (drives `PREPARING`/`READY`).
**Files**: `presentation/navigation/AppStateReducer.kt` (FR-01), `presentation/AppViewModel.kt` (FR-01)

- `AppViewModel` adds `PreparationRepository.observePreparationState()` to its `combine(...)`.
- `AppStateReducer.reduce(routes, activeRun, prep, resolver)` maps:
  - `prep.status == READY` → `AppReadiness.READY`, `ContentAvailability.PRESENT`
  - `prep.status == PREPARING` → `AppReadiness.PREPARING`
  - `prep.status == ERROR` or `NOT_STARTED` → fall back to routes-present proxy (`READY` if routes non-empty else `NOT_READY`) — keeps a sane state if content was side-loaded.
> Additive, single-site change (mirrors how FR-01 added `observeActiveRun` to FR-02). If FR-01 is unimplemented, fold this into FR-01 instead. Resolves FR-01 §12.2 open question ("where does FR-11 persist the ready flag").

**Done when**: FR-01 tests updated to feed a fake `PreparationRepository`; `PREPARING`/`READY` assertions pass.

---

### Step 13: Add headless `DownloadViewModel`
**Goal**: Expose download state + intents as a `StateFlow`; no UI consumes it here.
**Files**: `presentation/DownloadViewModel.kt`

```kotlin
data class DownloadUiState(
    val progress: OfflinePackageProgress = OfflinePackageProgress(),
    val networkPreference: NetworkPreference = NetworkPreference.WIFI_ONLY,
    val currentNetworkType: NetworkType = NetworkType.NONE,
    val blockedByNetwork: Boolean = false,
    val preparation: PreparationState = PreparationState(),
)

class DownloadViewModel(
    private val prepare: PrepareOfflinePackageUseCase,
    private val observePreparation: ObservePreparationStateUseCase,
    private val connectivity: ConnectivityMonitor,
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        connectivity.observe().onEach { type ->
            _state.update { it.copy(currentNetworkType = type,
                blockedByNetwork = !PrepareOfflinePackageUseCase.networkAllows(it.networkPreference, type)) }
            // auto-pause if preference no longer satisfied mid-download
            if (_state.value.blockedByNetwork) pause()
        }.launchIn(viewModelScope)
        observePreparation().onEach { p -> _state.update { it.copy(preparation = p) } }.launchIn(viewModelScope)
    }

    fun setNetworkPreference(pref: NetworkPreference) { _state.update { it.copy(networkPreference = pref,
        blockedByNetwork = !PrepareOfflinePackageUseCase.networkAllows(pref, it.currentNetworkType)) } }
    fun start() { launchDownload() }
    fun resume() { launchDownload() }        // resume = relaunch; use-case skips done items
    fun retry() { launchDownload() }
    fun pause() { job?.cancel(); job = null }

    private fun launchDownload() {
        if (job?.isActive == true) return
        job = prepare.run(_state.value.networkPreference)
            .onEach { p -> _state.update { it.copy(progress = p) } }
            .launchIn(viewModelScope)
    }
}
```
> Mirrors FR-02 `RunLogViewModel` / FR-01 `AppViewModel` patterns. Pause cancels the coroutine; persisted assets make resume cheap.

**Done when**: Compiles.

---

### Step 14: Unit tests (`commonTest`, Ktor `MockEngine`)
**Goal**: Lock parsing, mapping, progress, gating, orchestration, VM. Prefer fakes over real DB/engine.
**Files**: under `shared/src/commonTest/kotlin/com/example/ta33/`

- **`ManifestParsingTest`** — `MockEngine` returns a manifest JSON; assert `ManifestDto` fields (version, content.url, tiles) and that `ignoreUnknownKeys` tolerates extra fields.
- **`ContentMappingTest`** — `RouteDto.toDomain()`: ordinal/name/`GeoPoint(lat,lon)`/`radiusMeters` default 50.0; `routeId` back-filled onto controls.
- **`ProgressReducerTest`** — precedence (any ERROR→ERROR; all DONE→DONE; any DOWNLOADING→DOWNLOADING; PAUSED; IDLE); byte-weighted fraction when all totals known; item-average fallback when a total is null; empty list → IDLE/0.0.
- **`ConnectivityGatingTest`** — `networkAllows(WIFI_ONLY, CELLULAR)=false`, `(WIFI_ONLY, WIFI)=true`, `(WIFI_AND_CELLULAR, CELLULAR)=true`, `(*, NONE)=false`.
- **`PrepareOfflinePackageUseCaseTest`** — with `MockEngine` (manifest+content JSON, a small tile body), a **fake `FileStorage`** (in-memory map), a **fake `RouteRepository`** (records `upsertRoute`), a **fake `PreparationRepository`**, and a fake `ConnectivityMonitor(WIFI)`:
  - happy path → routes persisted, tile bytes written, `markReady(version)` called, terminal progress `DONE`;
  - **resume** → pre-seed content asset `DONE` + a tile at half size → content not re-persisted, tile Range-resumes (fake asserts append offset), ends `DONE`;
  - **blocked** → `ConnectivityMonitor(CELLULAR)` + `WIFI_ONLY` → emits `PAUSED`, no manifest fetch, no `markReady`;
  - **error** → `MockEngine` 500 on a tile → that item `ERROR`, overall `ERROR`, `markError()`, other items untouched.
- **`DownloadViewModelTest`** — `runTest` + `Dispatchers.setMain(StandardTestDispatcher())`: `start()` drives state to `DONE`; `setNetworkPreference(WIFI_ONLY)` on cellular sets `blockedByNetwork`; a connectivity emission to cellular mid-run auto-pauses (job cancelled).

**Done when**: `./gradlew :shared:allTests` green.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| Wi-Fi-only preference, device on cellular | Download does not start / auto-pauses | `networkAllows` false → emit `PAUSED`; VM sets `blockedByNetwork`; observer auto-pauses mid-run |
| Network drops mid-tile | Item pauses; partial file kept | Coroutine cancels on drop or user pause; `DownloadedAsset.bytesDownloaded` persisted; next `run` resumes via `Range` |
| Server ignores `Range` (returns 200) | Restart that file from 0 (no corruption) | Detect `status != 206` → `fileStorage.delete(path)` then write from offset 0 |
| Host omits `Content-Length` | Progress shows indeterminate for that item; overall uses item-average | `totalBytes = null`; reducer falls back to per-item-average fraction |
| App killed mid-download | Resume on next launch | State fully in `Preparation`/`DownloadedAsset`; `run` seeds from persisted assets |
| Re-run after full completion | No-op | All assets `DONE` → each item skipped; `upsertRoute` idempotent; `markReady` re-asserts READY |
| Manifest fetch fails (offline/404/bad URL) | Preparation not marked; surfaced as error | `fetchManifest` throws → caught → `markError`, progress `ERROR`; user retries |
| Corrupt/incomplete content JSON | Parse fails; content item ERROR, no partial routes | `ignoreUnknownKeys` tolerates extra fields but missing required → exception → item `ERROR`; upsert only after full parse |
| Tile file half-written then checksum mismatch (if `sha256` present) | Discard + re-download | After write, if `sha256` provided and mismatched → delete + mark item `ERROR` for retry (optional; see §12.2) |
| Disk full while writing tiles | Item ERROR, overall ERROR | `FileStorage.append` throws IO → caught → item `ERROR`; user frees space + retries |
| Manifest version newer than persisted READY | Re-download changed items | `Preparation.manifestVersion` differs → clear assets / re-run (see §12.2 for granular diffing) |
| Placeholder dev URL still in place | Manifest fetch fails fast | `example.invalid` is intentionally unresolvable; log a clear TODO; real URL set via `ContentConfig` |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: Content is **read-only, remote-sourced** — validate before persisting: require non-blank `route.id`/`control.id`, finite `lat`/`lon` (`-90..90`, `-180..180`), `radiusMeters > 0`, `distanceKm >= 0`; reject the content item on violation rather than writing bad rows. Use `Json { ignoreUnknownKeys = true }` (tolerate additive fields) but treat missing required fields as errors.
- **Transport**: Use **HTTPS** for the content host. If a plaintext dev host is temporarily needed, scope it to debug builds (Android `usesCleartextTraffic` on a debug manifest; iOS ATS exception) — never ship cleartext. Optionally verify `sha256` from the manifest for downloaded files (integrity).
- **Auth/Access control**: None in Etapa 1 — content is public/read-only, participant anonymous. No tokens stored (Etapa 2, Keystore/Keychain per stack §4).
- **Sensitive data**: Downloaded routes/coordinates + tiles stay **on-device** (app-private `filesDir`/Documents). No upload. No PII collected during download.
- **Logging**: Napier at debug for URLs/sizes/progress; **do not** log full response bodies or coordinate payloads at info level; never log to persistent files. Redact the base URL host only if it later carries a token (it does not in Etapa 1).

---

## 7. ASSUMPTIONS

> User opted out of clarification (`skip questions` / `proceed directly`). Accepted defaults recorded here.

1. **FR-02 is implemented first** — its models, `RouteRepository.upsertRoute`, `Ta33Database`, `TimeProvider`, `IdGenerator`, and `appModule` exist and are referenced (not duplicated). If wrong: FR-11 cannot compile; do FR-02 first.
2. **Manifest-based content layout** — a small `manifest.json` (version + content URL + tile list) is fetched first, then content JSON, then tiles. If the organizer ships a single combined JSON instead, drop the manifest step and read tiles from a fixed list (localized change in the use-case).
3. **Content persists via `upsertRoute`** (FR-02 §12.3 seam), so re-runs are idempotent. If FR-02's signature differs, adapt the call — do not redefine the repo.
4. **Enums stored as TEXT, parsed in Kotlin** (matches FR-02 `SyncStatus.fromDb`); no column adapters. Impact: swapping to adapters later is localized.
5. **`FileStorage`/`ConnectivityMonitor`/`HttpClientEngine` provided via `platformModule`** (same pattern as the SQLDelight `SqlDriver`), not `expect class`. Impact if wrong: minor DI reshuffle.
6. **Sequential downloads** (content then tiles). If tiles must parallelize for speed, see §12.1 — reducer already supports concurrent per-item updates.
7. **Persisted `Preparation` becomes FR-01's readiness source** (Step 12 additive seam). Resolves FR-01 §12.2. If FR-01 is unimplemented, fold Step 12 into FR-01.
8. **Configurable dev URL placeholder** (`example.invalid`) until the organizer delivers the real host/data (project-stack §10). No code change to repoint — only `ContentConfig.baseUrl`.
9. **Foreground-only download** in Etapa 1 (runs in `viewModelScope`); OS background-download services are out of scope.
10. **HTTP `Range` resume with graceful 200 fallback** is sufficient for Etapa 1; no multi-connection/segmented downloading.
11. **Only Android + iOS targets**, so `Dispatchers.Default` + OkHttp/Darwin engines + native/android file & connectivity APIs suffice.

> Open questions live in Section 12.

---

## 8. QUICK REFERENCE

### Files to Modify
- `gradle/libs.versions.toml` — add `ktor-client-mock` (and `kotlinx-coroutines-test` if FR-02 unmerged).
- `shared/build.gradle.kts` — add test deps to `commonTest`.
- `shared/src/commonMain/kotlin/com/example/ta33/di/PlatformModule.kt` (+ `.android.kt`, `.ios.kt`) — provide `HttpClientEngine`, `FileStorage`, `ConnectivityMonitor`.
- `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt` — register config, HttpClient, remote source, repos, use-cases, ViewModel.
- `shared/src/commonMain/kotlin/com/example/ta33/di/Koin.kt` — add `ViewModelProvider.downloadViewModel()`.
- `androidApp/src/main/AndroidManifest.xml` — `INTERNET` + `ACCESS_NETWORK_STATE`.
- **(FR-01 seam)** `presentation/navigation/AppStateReducer.kt` + `presentation/AppViewModel.kt` — read `observePreparationState()`.

### Files to Create
- `data/remote/ContentConfig.kt`, `HttpClientFactory.kt`, `ContentRemoteDataSource.kt`
- `data/dto/ManifestDto.kt`, `ContentDto.kt`, `ContentMappers.kt`
- `data/file/FileStorage.kt` (+ `.android.kt`, `.ios.kt`)
- `data/connectivity/ConnectivityMonitor.kt` (+ `.android.kt`, `.ios.kt`)
- `data/repository/OfflinePackageRepositoryImpl.kt`, `PreparationRepositoryImpl.kt`
- `domain/download/DownloadProgress.kt`, `ProgressReducer.kt`
- `domain/model/NetworkPreference.kt`, `NetworkType.kt`, `PreparationState.kt`
- `domain/repository/OfflinePackageRepository.kt`, `PreparationRepository.kt`
- `domain/usecase/PrepareOfflinePackageUseCase.kt`, `ObservePreparationStateUseCase.kt`
- `presentation/DownloadViewModel.kt`
- SQLDelight: `Preparation.sq`, `DownloadedAsset.sq`
- `commonTest/…` — `ManifestParsingTest`, `ContentMappingTest`, `ProgressReducerTest`, `ConnectivityGatingTest`, `PrepareOfflinePackageUseCaseTest`, `DownloadViewModelTest` (+ fakes: `FakeFileStorage`, `FakeConnectivityMonitor`, `FakeRouteRepository`, `FakePreparationRepository`).

### Dependencies
- `io.ktor:ktor-client-mock` @ `ktor` (3.5.0) — test-only, `MockEngine`.
- `org.jetbrains.kotlinx:kotlinx-coroutines-test` @ `coroutines` (1.10.2) — `runTest` (via FR-02 if merged).
- (Already present: ktor-client-core/content-negotiation/serialization-json, okhttp (android), darwin (ios), kotlinx-serialization-json, SQLDelight 2.1.0 + coroutines-ext + drivers, Koin 4.1.0, lifecycle-viewmodel.)

### Commands
```bash
./gradlew build                      # compile + regenerate SQLDelight (Preparation/DownloadedAsset)
./gradlew :shared:allTests           # shared unit tests (MockEngine)
./gradlew :androidApp:assembleDebug  # Android sanity
# iOS headless (compiles FileStorage/Connectivity/engine actuals + ViewModelProvider):
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'id=<simulator-id>' CODE_SIGNING_ALLOWED=NO build
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
| **A. Manifest + streamed items via Ktor into `FileStorage`/`RouteRepository`, pure reducer, persisted `Preparation`, sequential, Range-resume** | Clean layering; idempotent + resumable; reuses FR-02 seam; testable with `MockEngine`; swappable URL (§10) | Sequential is slower than parallel for many tiles; Range-resume needs server support (has fallback) | ✅ |
| B. **Platform-native download managers** (Android `DownloadManager`/`WorkManager`, iOS `URLSession` background) | True OS background downloads survive app kill; battery-friendly | Two divergent native impls, hard to unit-test in `commonTest`, more surface for store review; overkill for Etapa 1 foreground prep | — |
| C. **Bundle content/tiles in the app binary** (no download) | Simplest, always offline | Fails project-stack §10 (data not ready, must be swappable without a release); bloats binary; can't update content per event | — |
| D. **Parallel tile downloads** (fan-out with a concurrency limit) | Faster on fat Wi-Fi | More complex cancel/resume + progress; marginal benefit for a handful of tilesets; reducer already supports it later | — (deferred) |

**Why the selected approach won**: It keeps the whole download subsystem in shared, unit-testable Kotlin (per stack goals), reuses the FR-02 persistence seam with zero duplication, satisfies the "swappable external JSON" mandate (§10), and delivers resumable, network-gated preparation without the cost/complexity of native background frameworks — which can be layered in later (Approach B) if real-world tile sizes demand it.

### 12.2 Open Questions

- [ ] **Exact manifest schema + whether content is one JSON or per-route files** — Proposed direction: single `manifest.json` (version + content URL + tile list) as designed; confirm with the organizer when data is delivered; DTOs are additive-tolerant (`ignoreUnknownKeys`).
- [ ] **Tile format/granularity (one big MBTiles vs many vector tiles)** — Proposed direction: treat each manifest `tiles[]` entry as one downloadable file; FR-06 defines how MapLibre consumes them. Revisit `relativePath`/`format` when tile generation (stack §11) lands.
- [ ] **Integrity checking** — Proposed direction: honour optional `sha256` per asset (verify after write, delete+retry on mismatch); make it required once the host provides hashes.
- [ ] **Content update / re-download policy when `manifestVersion` changes** — Proposed direction: for Etapa 1, if version differs → `clearAssets()` + full re-run; granular per-item diffing is a later optimization.
- [ ] **Should download continue in the background / be re-triggerable outside the prep screen?** — Proposed direction: foreground-only in `viewModelScope` for Etapa 1; evaluate `WorkManager`/`URLSession` background (Approach B) only if field tests show it's needed.

### 12.3 Suggestions & Follow-ups

- Add a **JVM SQLite driver** in `androidHostTest` for a real DB integration test of `PreparationRepositoryImpl`/`DownloadedAsset` (beyond fakes) — good coverage, out of scope here.
- Add a Koin **`checkModules()`** test to catch DI-graph breakage across FR-01/FR-02/FR-11.
- When **FR-06** lands, expose a `FileStorage.baseDir() + "/tiles"` accessor (or a small `TileStore`) so MapLibre points at the downloaded files without knowing FR-11 internals.
- Consider **Turbine** for `StateFlow`/`Flow` assertions in `DownloadViewModelTest`/orchestration tests (optional test-only dep).
- Wire the **prepared-URL into a debug settings screen** later so QA can point at staging vs the organizer's host without a rebuild (UI phase).
- When **Etapa 2 sync** arrives, the same `ContentRemoteDataSource`/HttpClient config is reused for uploads — keep the client construction in one place (`HttpClientFactory`).

> Section 9 (Design Reference) omitted: logic-only, no UI/visual spec. Section 10 (Corrections From Current State) omitted: greenfield FR-11 with no prior implementation — the FR-01 readiness change (Step 12) is an additive coordinated seam, not a correction, and is covered in Steps/Assumptions.
