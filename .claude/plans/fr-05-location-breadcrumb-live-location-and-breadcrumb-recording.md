# FR-05 Live Location & Breadcrumb Recording - Location Stream, Throttle & Trackpoint Persistence (Logic Only)

> **Summary**: Build the shared, headless logic for the participant's live GPS position and their walked breadcrumb - a platform `LocationProvider` (`Flow<GeoPosition>`) shared across features, a pure throttle/downsample filter, an additive `Trackpoint` table + repository bound to FR-02's `RunSession`, a `RecordBreadcrumbUseCase`, a headless `LiveLocationViewModel`, and a location-permission + background-tracking platform seam - with no UI and no map rendering (FR-06).

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
On the TA33 trail a participant needs to see their live GPS position and the track they have already walked (breadcrumb), and that track must be recorded reliably over several hours with no signal, surviving battery-saver and OS background limits - the riskiest field part of the whole app. Today none of this exists: there is no location acquisition, no place to store a track, and no throttling to keep the track compact and clean despite GPS noise in the rock canyons of Adršpach/Teplice.

### 1.2 Solution Overview
Add a shared, Clean-Architecture location subsystem inside the existing `:shared` module: a `LocationProvider` interface (platform impls via `platformModule`, exactly like FR-11's `FileStorage`/`ConnectivityMonitor`) exposing `Flow<GeoPosition>`, wrapped by a single hot `LocationStream` that fans one GPS subscription out to all consumers (breadcrumb **and** FR-08 geofencing). A **pure** `BreadcrumbThrottle` (min-distance / min-time / accuracy filter, reusing FR-02 `GeoUtils`) decides which fixes to keep; a `RecordBreadcrumbUseCase` persists kept points into a new **additive** `Trackpoint` table bound to FR-02's `RunSession`; a headless `LiveLocationViewModel` exposes current position + accumulated track as a `StateFlow`. A `LocationPermissionController` models permission **status** as a contract, and a `LocationTrackingController` models the foreground-service / background-modes hook as a **platform seam only** (flagged for field testing). No screens, no map, no map SDK.

### 1.3 Scope: What This IS
- **Location model** (`domain/model`): `GeoPosition` = FR-02 `GeoPoint` + `accuracyMeters` + `timestampMillis`; `LocationPermissionStatus` enum; `BreadcrumbConfig` (thresholds); `Trackpoint` domain model.
- **Platform location seam** (`data/location`): `LocationProvider` interface + Android (Fused Location / `LocationManager`) and iOS (CoreLocation) impls, registered in `platformModule` (same pattern as FR-11).
- **Shared hot stream** (`data/location`): `LocationStream` - one upstream `LocationProvider` flow `shareIn`'d on an app scope so breadcrumb (FR-05) and geofencing (FR-08) share a single GPS subscription.
- **Permission contract** (`data/location`): `LocationPermissionController` interface + platform impls exposing current status + a status `Flow`; a `request*` trigger declared but its **UI dialog wiring deferred** to the UI phase.
- **Background seam** (`data/location`): `LocationTrackingController` interface (`start`/`stop`) + platform impls (Android foreground service, iOS background location modes) - **high-level seam only, flagged "ověřit v terénu, baterie"**.
- **Pure throttle** (`domain/geo`): `BreadcrumbThrottle` - stateless keep/drop decision reusing `GeoUtils.distanceMeters`; fully unit-testable, no I/O.
- **Persistence**: new SQLDelight `Trackpoint` table on the existing `Ta33Database` (**additive seam over FR-02**) + `TrackpointRepository` (domain interface + SQLDelight-backed impl) + mapper, reusing FR-02 `IdGenerator`/`SyncStatus` conventions.
- **Use-cases** (`domain/usecase`): `RecordBreadcrumbUseCase` (collect → throttle → persist, bound to a `runId`), `ObserveTrackUseCase`.
- **Headless `LiveLocationViewModel`** (`presentation`): `StateFlow<LiveLocationUiState>` (permission, current position, track, tracking flag). No UI consumes it here.
- **Koin registration** (`di`) + Swift accessor (`ViewModelProvider.liveLocationViewModel()`).
- **Unit tests** (`commonTest`) with a **fake location Flow**: pure throttle transitions, `RecordBreadcrumbUseCase`, `LiveLocationViewModel` state.

### 1.4 Scope: What This IS NOT
- **No UI whatsoever** - no Compose/SwiftUI, no live-dot rendering, no permission dialogs, no map. UI is a deliberately deferred later phase.
- **No map rendering / no map SDK (MapLibre)** - drawing the position and track on a map is **FR-06**. FR-05 only produces the data (current `GeoPosition` + `List<Trackpoint>`).
- **No geofencing / checkpoint collection** - that is **FR-08**; FR-05 only provides the shared `LocationProvider`/`LocationStream` it will reuse.
- **No deep background implementation** - `LocationTrackingController` is a seam with documented platform requirements; the real foreground service / background-mode tuning is field work, not built here.
- **No permission dialog UX** - only the permission **status contract** is modeled; requesting/explaining is UI phase.
- **No redefinition of FR-02** - `GeoPoint`, `GeoUtils`, `RunSession`, `IdGenerator`, `TimeProvider`, `SyncStatus`, `Ta33Database`, `appModule`, `platformModule` are **referenced**, not duplicated. `Trackpoint` is a new additive table.
- **No server upload / sync** - Etapa 1 is on-device only; `syncStatus` stays `PENDING` (project-stack §10).
- **No new Gradle modules** - everything lives in `:shared` as package layers (project-stack §12).

---

## 2. SUCCESS CRITERIA

Implementation is COMPLETE when ALL criteria are met:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | Project compiles with the new models, `LocationProvider`/`LocationStream`/permission/tracking seams, `Trackpoint` schema, repository, use-cases and ViewModel | `./gradlew build` succeeds; SQLDelight regenerates with a `Trackpoint` table |
| 2 | `LocationProvider` is an interface in `commonMain` with Android + iOS impls registered in `platformModule` (NOT `expect class`), exposing `Flow<GeoPosition>` | Code review + `./gradlew build` (Android) + `xcodebuild` (iOS actuals compile) |
| 3 | `GeoPosition` reuses FR-02 `GeoPoint` and adds `accuracyMeters` + `timestampMillis` | Type compiles; `GeoPositionTest` |
| 4 | `BreadcrumbThrottle` is pure (no coroutines/I/O): drops poor-accuracy fixes, keeps the first good fix, and keeps subsequent fixes only when min-distance AND min-time thresholds are met | `BreadcrumbThrottleTest` covering keep/drop reasons across thresholds, reusing `GeoUtils` |
| 5 | `RecordBreadcrumbUseCase` collects the location stream, applies the throttle, and persists only kept points to `TrackpointRepository`, bound to a `runId` | `RecordBreadcrumbUseCaseTest` with fake `LocationProvider`/`LocationStream` + fake repo |
| 6 | A `Trackpoint` row stores `runSessionId`, lat/lon, accuracy, timestamp, `syncStatus='PENDING'`; track reads back ordered by time | `TrackpointRepository` test (fake) + generated `TrackpointQueries`; ordering asserted |
| 7 | Resuming a run seeds the throttle from the last persisted trackpoint so no duplicate/adjacent point is recorded after an app kill | `RecordBreadcrumbUseCaseTest` (resume case): pre-seed a last point, feed a nearby fix → dropped |
| 8 | `LocationStream` shares one upstream subscription across multiple collectors (FR-05 + FR-08 seam) | `LocationStreamTest`: two collectors → underlying fake provider subscribed once |
| 9 | `LocationPermissionController` exposes current status + a status `Flow`; `LiveLocationViewModel` surfaces it | `LiveLocationViewModelTest` with fake permission controller |
| 10 | `LiveLocationViewModel` exposes `StateFlow<LiveLocationUiState>` with current position + accumulated track + permission + tracking flag | `LiveLocationViewModelTest` (`runTest` + `Dispatchers.setMain`) |
| 11 | All new components resolvable via Koin (provider/permission/tracking per platform in `platformModule`, rest in `appModule`) | Koin resolution/`checkModules` test or app launch; iOS build |
| 12 | `:shared` has **no** map-SDK dependency; Android location dependency added only to `androidMain` | Inspect `shared/build.gradle.kts`; no MapLibre import |
| 13 | `./gradlew :shared:allTests` green | Run tests |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
              NATIVE UI (later phase - NOT built here; map render = FR-06)
              reads LiveLocationUiState (current pos + track), shows permission state
        ══════════════════ SHARED CORE (:shared, commonMain) ══════════════════
                          presentation/
                     ┌────────────────────────────┐
                     │     LiveLocationViewModel    │  StateFlow<LiveLocationUiState>
                     │     (headless, no UI)        │  (permission, currentPosition, track, tracking)
                     └───────┬───────────┬─────────┘
              observes live  │           │ starts recording + observes track
              position (raw) │           │
                     domain/usecase/     │
                     ┌───────────────────┴────────────┐
                     │ RecordBreadcrumbUseCase          │  collect → throttle → persist (runId)
                     │ ObserveTrackUseCase              │
                     └───────┬───────────────┬─────────┘
             domain/geo/ (PURE)              │ persists
             ┌───────────────────┐          │
             │  BreadcrumbThrottle│──uses──▶ GeoUtils.distanceMeters (FR-02, reused)
             │  (keep/drop)       │          │
             └───────────────────┘          ▼
                                   domain/repository/
                     ┌───────────────────────────────┬──────────────────────────┐
                     │      LocationStream (hot)       │   TrackpointRepository     │
                     │  shareIn(appScope) over one     │   (SQLDelight-backed)      │
                     │  LocationProvider subscription  │   Trackpoint.sq            │
                     └───────────────┬─────────────────┴────────────┬─────────────┘
                     data/location/  │  (expect via platformModule)  │  data/repository + data/mapper
        ┌────────────────────────────┼───────────────┐              ▼
        │ LocationProvider  │ LocationPermission-      │        Ta33Database (FR-02)
        │  Flow<GeoPosition>│ Controller (status/Flow) │
        │ LocationTracking- │ (request = UI-phase)     │
        │  Controller (seam)│                          │
        └────────┬──────────┴──────────┬───────────────┘
        Android FusedLocation/          iOS CLLocationManager (CoreLocation)
        LocationManager;                background location modes;
        foreground service (seam)       UIBackgroundModes=location (seam)

        ── FR-08 (geofencing, later) reuses the SAME LocationStream ──▶ its own use-case
```

**Data flow (record breadcrumb for an active run):** `LiveLocationViewModel.bind(runId)` → starts `RecordBreadcrumbUseCase.record(runId)`: subscribes to `LocationStream.positions()` (one shared GPS subscription) → for each `GeoPosition`, `BreadcrumbThrottle.decide(lastKept, position)` → on `Keep`, build a `Trackpoint(id = IdGenerator.newId(), runSessionId = runId, …)`, `TrackpointRepository.append(...)`, update `lastKept` → the repo's `observeTrack(runId)` Flow re-emits → VM updates `track`. In parallel the VM observes `LocationStream.positions()` directly for the **raw** `currentPosition` (smooth live dot for FR-06) and `LocationPermissionController.observeStatus()` for `permissionStatus`. `LocationTrackingController.start()` requests the foreground service / background mode so recording survives screen-off (field-tested).

**Cross-feature seam (FR-08):** FR-08 injects the **same** `LocationStream` and adds its own geofence use-case that maps each `GeoPosition` through `GeoUtils.isWithinRadius(control, position.location)` (~50 m). No second GPS listener; no change to `LocationProvider`.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Where code lives | Existing `:shared`, new **package layers** (`data/location`, plus `domain/model`, `domain/geo` reuse, `domain/repository`, `domain/usecase`, `data/repository`, `data/mapper`, `presentation`) | project-stack §12 "layers as packages, not Gradle modules yet"; no duplication |
| Location acquisition seam | **`interface LocationProvider`** in common + platform impls in `platformModule` (Android Fused/`LocationManager`, iOS CoreLocation) | Mirrors FR-11 `FileStorage`/`ConnectivityMonitor`; avoids `expect class` constructor-with-Context friction; injectable fake for tests |
| Location value type | **`GeoPosition`** = FR-02 `GeoPoint` + `accuracyMeters` + `timestampMillis` | Reuses FR-02 geo model; accuracy needed for the canyon filter; timestamp from the fix (not device clock) drives throttle time delta |
| One stream for many consumers | **`LocationStream`** wraps `LocationProvider` with `shareIn(appScope, WhileSubscribed(5s), replay = 1)` | A single GPS subscription shared by breadcrumb (FR-05) **and** geofencing (FR-08) - battery-critical; `replay=1` gives late subscribers the last fix |
| Throttle purity | **Pure `BreadcrumbThrottle`** (stateless `decide(last, candidate)`), state (`lastKept`) held by the use-case | Deterministic unit tests with a fake Flow; no coroutines/I/O; reuses `GeoUtils.distanceMeters` (no re-implemented math) |
| Keep rule | Drop if `accuracy > maxAccuracyMeters`; keep first good fix; else keep iff `distance >= minDistanceMeters` **and** `elapsed >= minTimeMillis` | Distance filter = compact spatial track + no points while standing still; time floor = debounces jitter / caps density; accuracy filter = rejects canyon-reflection noise |
| Breadcrumb persistence | **New additive `Trackpoint` table** on `Ta33Database`, one row per kept point, FK-style `runSessionId`, enums as TEXT (`SyncStatus.fromDb`) | **Additive seam over FR-02** (clearly labelled); mirrors FR-02 schema/mapper/repo conventions; offline-first (persist immediately, survive crash) |
| Live dot vs track | VM reads **raw** stream for `currentPosition`; **persisted downsampled** track for `track` | Smooth live position for FR-06's dot; clean, storage-light breadcrumb polyline |
| Permission | **`LocationPermissionController`**: status + status `Flow` implementable now; `request*` declared, **dialog/UX deferred** to UI phase | Status query is platform-readable today; requesting needs an Activity/UI reaction (deferred). Keeps the contract stable for the UI phase |
| Background execution | **`LocationTrackingController` seam only** (`start`/`stop`) + documented manifest/Info.plist requirements, flagged "ověřit v terénu, baterie" | Riskiest field part (zadani); deep impl needs real devices + battery testing; model the hook now, tune later |
| App scope for `shareIn` | Provide an application `CoroutineScope` (`SupervisorJob() + Dispatchers.Default`) as a Koin `single` | `shareIn` needs a long-lived scope decoupled from any single ViewModel; shared by FR-05/FR-08 |
| Android location lib | **`play-services-location` (Fused)** primary, `LocationManager` fallback - in `androidMain` only | Stack §4 names both; Fused is battery-smart; keep the dependency off `commonMain`/iOS |

---

## 4. IMPLEMENTATION STEPS

> Execute in order. Do not skip. Paths under `shared/src/commonMain/kotlin/com/example/ta33/` unless stated. Base package `com.example.ta33`.
> **Dependency ordering**: assumes **FR-02 implemented** (`GeoPoint`, `GeoUtils`, `RunSession`, `IdGenerator`, `SyncStatus`, `Ta33Database`, `appModule`, `platformModule`, `kotlinx-coroutines-test`). FR-01 optional. FR-08 will build on Steps 3-4 later.

### Step 1: Add the Android location dependency
**Goal**: Fused Location on Android; iOS CoreLocation is a system framework (no dep).
**Files**: `gradle/libs.versions.toml`, `shared/build.gradle.kts`

`libs.versions.toml`:
```toml
[versions]
playServicesLocation = "21.3.0"   # verify latest at implementation time (project-stack §9 "ověřuj verze")
[libraries]
play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "playServicesLocation" }
```
`shared/build.gradle.kts` - **androidMain only** (never commonMain/iosMain):
```kotlin
androidMain.dependencies {
    implementation(libs.play.services.location)
}
```
> `kotlinx-coroutines-test` for tests already added by FR-02 Step 1; if FR-02 unmerged, add it to `commonTest` as FR-02 specifies.

**Done when**: `./gradlew :shared:compileDebugKotlinAndroid` resolves the dependency; `commonMain`/iOS remain free of it.

---

### Step 2: Add domain models - `GeoPosition`, `LocationPermissionStatus`, `BreadcrumbConfig`, `Trackpoint`
**Goal**: The shared value types the whole feature (and FR-08) reference.
**Files**: `domain/model/GeoPosition.kt`, `LocationPermissionStatus.kt`, `BreadcrumbConfig.kt`, `Trackpoint.kt`

```kotlin
// domain/model/GeoPosition.kt
package com.example.ta33.domain.model

/** A single GPS fix. Reuses FR-02 GeoPoint; adds accuracy + fix timestamp. Shared by FR-05 breadcrumb and FR-08 geofencing. */
data class GeoPosition(
    val location: GeoPoint,        // FR-02 model (latitude, longitude)
    val accuracyMeters: Double,    // horizontal accuracy radius; larger = worse (canyon reflections)
    val timestampMillis: Long,     // time of the fix, from the platform provider
)
```
```kotlin
// domain/model/LocationPermissionStatus.kt
package com.example.ta33.domain.model

/** Contract-level permission status (maps to Android runtime perms + iOS CLAuthorizationStatus). */
enum class LocationPermissionStatus {
    NOT_DETERMINED, DENIED, RESTRICTED, GRANTED_WHEN_IN_USE, GRANTED_ALWAYS;
    val isUsable: Boolean get() = this == GRANTED_WHEN_IN_USE || this == GRANTED_ALWAYS
    val allowsBackground: Boolean get() = this == GRANTED_ALWAYS
}
```
```kotlin
// domain/model/BreadcrumbConfig.kt
package com.example.ta33.domain.model

/** Downsample thresholds. Defaults are field-tunable (see §12.2). */
data class BreadcrumbConfig(
    val minDistanceMeters: Double = 10.0, // keep a point after ~10 m of movement
    val minTimeMillis: Long = 5_000,      // …but no more often than every 5 s
    val maxAccuracyMeters: Double = 50.0, // drop fixes worse than this (canyon noise)
)
```
```kotlin
// domain/model/Trackpoint.kt
package com.example.ta33.domain.model

/** One persisted breadcrumb point, bound to an FR-02 RunSession. */
data class Trackpoint(
    val id: String,
    val runSessionId: String,
    val location: GeoPoint,
    val accuracyMeters: Double,
    val timestampMillis: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING, // FR-02 convention; never uploaded in Etapa 1
)
```
**Done when**: All model files compile against FR-02 `GeoPoint`/`SyncStatus`.

---

### Step 3: Add the location platform seam - `LocationProvider` (+ Android/iOS impls)
**Goal**: A shared interface (NOT `expect class`) emitting `Flow<GeoPosition>`; the seam FR-08 reuses.
**Files**: `data/location/LocationProvider.kt` (common), `data/location/LocationProvider.android.kt` (androidMain), `data/location/LocationProvider.ios.kt` (iosMain)

```kotlin
// commonMain data/location/LocationProvider.kt
package com.example.ta33.data.location
import com.example.ta33.domain.model.GeoPosition
import kotlinx.coroutines.flow.Flow

/** Platform GPS acquisition. Cold: each collection starts updates; cancellation stops them.
 *  Consumers should use LocationStream (hot, shared) rather than collecting this directly. */
interface LocationProvider {
    /** @param intervalMillis desired update cadence hint (platforms may coalesce). */
    fun positionUpdates(intervalMillis: Long = 2_000): Flow<GeoPosition>
}
```
- **Android** (`AndroidLocationProvider(private val context: Context)`): `positionUpdates` = `callbackFlow`:
  - Prefer **Fused** - `LocationServices.getFusedLocationProviderClient(context)`, `LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()`, `requestLocationUpdates(request, callback, Looper)`; in `onLocationResult` map each `Location` → `GeoPosition(GeoPoint(lat, lon), accuracy.toDouble(), time)`; `awaitClose { removeLocationUpdates(callback) }`.
  - Fallback `LocationManager.requestLocationUpdates(GPS_PROVIDER, …)` if Play Services unavailable (note only; Fused is primary).
  - Guard `SecurityException` when permission missing → close the flow (empty) rather than crash.
- **iOS** (`IosLocationProvider`): `positionUpdates` = `callbackFlow` wrapping a `CLLocationManager` with a delegate:
  - `desiredAccuracy = kCLLocationAccuracyBest`, `startUpdatingLocation()`; delegate `didUpdateLocations` maps `CLLocation` (`coordinate.latitude/longitude`, `horizontalAccuracy`, `timestamp` → epoch millis) → `GeoPosition`; `awaitClose { stopUpdatingLocation() }`.
- Both impls run callbacks on `Dispatchers.Default`-friendly flows; no permission **requesting** here (that is `LocationPermissionController`).

**Done when**: Interface compiles in common; both actuals compile (`./gradlew build` + iOS `xcodebuild`).

---

### Step 4: Add the shared hot `LocationStream`
**Goal**: One GPS subscription fanned out to all consumers (FR-05 breadcrumb + FR-08 geofence).
**Files**: `data/location/LocationStream.kt` (common)

```kotlin
package com.example.ta33.data.location
import com.example.ta33.domain.model.GeoPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/** Hot, shared location source: a single upstream LocationProvider subscription,
 *  fanned out to breadcrumb recording (FR-05) and geofencing (FR-08). */
class LocationStream(
    private val provider: LocationProvider,
    private val appScope: CoroutineScope,
    intervalMillis: Long = 2_000,
) {
    private val shared: SharedFlow<GeoPosition> =
        provider.positionUpdates(intervalMillis)
            .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    fun positions(): Flow<GeoPosition> = shared
}
```
> `WhileSubscribed(5_000)` keeps GPS alive briefly between consumer swaps (screen changes) without leaking when nobody listens; `replay = 1` gives a late subscriber (e.g. FR-08 joining after FR-05) the last fix immediately. Foreground/background survival is the `LocationTrackingController`'s job (Step 6), not this scope.

**Done when**: Compiles; covered by `LocationStreamTest` (Step 11).

---

### Step 5: Add the permission contract - `LocationPermissionController` (+ impls)
**Goal**: Read permission status now; declare a request trigger whose UI wiring is deferred.
**Files**: `data/location/LocationPermissionController.kt` (common), `.android.kt`, `.ios.kt`

```kotlin
// commonMain data/location/LocationPermissionController.kt
package com.example.ta33.data.location
import com.example.ta33.domain.model.LocationPermissionStatus
import kotlinx.coroutines.flow.Flow

interface LocationPermissionController {
    fun status(): LocationPermissionStatus
    fun observeStatus(): Flow<LocationPermissionStatus>
    /** Trigger the system permission flow. UI reaction/dialogs are UI-phase; impl may be a
     *  documented stub now (Android needs an Activity result API wired later). */
    suspend fun requestWhenInUse()
    suspend fun requestBackground()
}
```
- **Android** (`AndroidLocationPermissionController(context)`): `status()` = `ContextCompat.checkSelfPermission(ACCESS_FINE_LOCATION/ACCESS_COARSE_LOCATION)` → map to `GRANTED_WHEN_IN_USE`; `ACCESS_BACKGROUND_LOCATION` granted → `GRANTED_ALWAYS`; none → `DENIED`/`NOT_DETERMINED` (Android cannot distinguish "not asked" from "denied" without an Activity - default `NOT_DETERMINED` until first request, document limitation). `observeStatus()` = `MutableStateFlow` re-read on `refresh()` (a `refresh()` the UI calls on resume) - for this phase emit current status once + on demand. `requestWhenInUse/Background()` = **TODO stub** (logs; real request needs `ActivityResultContracts.RequestMultiplePermissions` in the UI phase).
- **iOS** (`IosLocationPermissionController`): `status()` maps `CLLocationManager.authorizationStatus` (`notDetermined`/`restricted`/`denied`/`authorizedWhenInUse`/`authorizedAlways`); `observeStatus()` = `callbackFlow` over the delegate `locationManagerDidChangeAuthorization`; `requestWhenInUse()` = `requestWhenInUseAuthorization()`, `requestBackground()` = `requestAlwaysAuthorization()` (these are callable now; the UI **reaction** is deferred).
- Register both in `platformModule`.

**Done when**: Interface compiles in common; both actuals compile; `status()`/`observeStatus()` are functional, `request*` documented as UI-phase.

---

### Step 6: Add the background tracking seam - `LocationTrackingController` (+ impls)
**Goal**: A start/stop hook for multi-hour recording; **seam only, flagged for field testing**.
**Files**: `data/location/LocationTrackingController.kt` (common), `.android.kt`, `.ios.kt`; `androidApp/src/main/AndroidManifest.xml`; iOS `Info.plist`

```kotlin
// commonMain data/location/LocationTrackingController.kt
package com.example.ta33.data.location

/** Keeps location recording alive with the screen off / app backgrounded.
 *  ⚠️ FIELD-TEST REQUIRED ("ověřit v terénu, baterie"): OEM battery-savers (esp. Android)
 *  can still kill this - validate on real devices over a multi-hour route. */
interface LocationTrackingController {
    fun start()   // Android: start foreground service; iOS: enable background location updates
    fun stop()
    val isTracking: Boolean
}
```
- **Android** (`AndroidLocationTrackingController(context)`): `start()` = `ContextCompat.startForegroundService(...)` for a `LocationForegroundService` (a minimal `Service` posting an ongoing notification, `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_LOCATION)`). **This phase**: define the service class + start/stop plumbing at a high level; the notification content/UX and OEM battery-exemption prompts are UI/field work. `stop()` = `stopService`.
- **iOS** (`IosLocationTrackingController`): `start()` = set `CLLocationManager.allowsBackgroundLocationUpdates = true` + `pausesLocationUpdatesAutomatically = false` (requires `UIBackgroundModes = [location]`); `stop()` = revert.
- **AndroidManifest.xml** (`<manifest>` / `<application>` level) - document + add:
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<!-- <service android:name=".location.LocationForegroundService"
        android:foregroundServiceType="location" android:exported="false" /> -->
```
- **iOS Info.plist** - document + add: `NSLocationWhenInUseUsageDescription`, `NSLocationAlwaysAndWhenInUseUsageDescription` (participant-facing rationale - feeds **store review**, zadani), and `UIBackgroundModes` array containing `location`.
- Register both in `platformModule`.

**Done when**: Interfaces compile; both actuals compile; manifest/Info.plist entries added; the service is a documented high-level stub (no deep tuning).

---

### Step 7: Add the pure `BreadcrumbThrottle`
**Goal**: Deterministic keep/drop decision; reuse `GeoUtils`; no I/O.
**Files**: `domain/geo/BreadcrumbThrottle.kt`

```kotlin
package com.example.ta33.domain.geo
import com.example.ta33.domain.model.BreadcrumbConfig
import com.example.ta33.domain.model.GeoPosition

sealed interface BreadcrumbDecision {
    data class Keep(val position: GeoPosition) : BreadcrumbDecision
    enum class DropReason { POOR_ACCURACY, TOO_CLOSE, TOO_SOON }
    data class Drop(val reason: DropReason) : BreadcrumbDecision
}

/** Pure, stateless downsample. Caller holds `lastKept`. Reuses FR-02 GeoUtils (no re-implemented math). */
class BreadcrumbThrottle(private val config: BreadcrumbConfig = BreadcrumbConfig()) {
    fun decide(lastKept: GeoPosition?, candidate: GeoPosition): BreadcrumbDecision {
        if (candidate.accuracyMeters > config.maxAccuracyMeters)
            return BreadcrumbDecision.Drop(BreadcrumbDecision.DropReason.POOR_ACCURACY)
        if (lastKept == null) return BreadcrumbDecision.Keep(candidate)   // first good fix
        val elapsed = candidate.timestampMillis - lastKept.timestampMillis
        if (elapsed < config.minTimeMillis)
            return BreadcrumbDecision.Drop(BreadcrumbDecision.DropReason.TOO_SOON)
        val moved = GeoUtils.distanceMeters(lastKept.location, candidate.location)
        if (moved < config.minDistanceMeters)
            return BreadcrumbDecision.Drop(BreadcrumbDecision.DropReason.TOO_CLOSE)
        return BreadcrumbDecision.Keep(candidate)
    }
}
```
**Done when**: Compiles; behaviour locked by `BreadcrumbThrottleTest` (Step 11).

---

### Step 8: Extend SQLDelight - `Trackpoint` table (additive seam over FR-02)
**Goal**: Persist kept breadcrumb points on the existing `Ta33Database`.
**Files**: `shared/src/commonMain/sqldelight/com/example/ta33/data/db/Trackpoint.sq`

```sql
-- Trackpoint.sq  - ADDITIVE over FR-02 (one row per kept breadcrumb point).
CREATE TABLE Trackpoint (
    id TEXT NOT NULL PRIMARY KEY,
    runSessionId TEXT NOT NULL,        -- FK-style link to FR-02 RunSession.id
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    accuracyMeters REAL NOT NULL,
    timestampMillis INTEGER NOT NULL,
    syncStatus TEXT NOT NULL DEFAULT 'PENDING'
);
CREATE INDEX trackpoint_run ON Trackpoint(runSessionId);

selectTrackForRun: SELECT * FROM Trackpoint WHERE runSessionId = ? ORDER BY timestampMillis;
selectLastTrackpoint: SELECT * FROM Trackpoint WHERE runSessionId = ? ORDER BY timestampMillis DESC LIMIT 1;
countForRun: SELECT COUNT(*) FROM Trackpoint WHERE runSessionId = ?;
insertTrackpoint:
INSERT INTO Trackpoint(id, runSessionId, latitude, longitude, accuracyMeters, timestampMillis, syncStatus)
VALUES (?, ?, ?, ?, ?, ?, ?);
deleteForRun: DELETE FROM Trackpoint WHERE runSessionId = ?;
```
> Same conventions as FR-02: enums as TEXT parsed in Kotlin (`SyncStatus.fromDb`), lat/lon as REAL columns mapped into `GeoPoint`, `Long` epoch-millis. No column adapters, no migration framework (first version).

**Done when**: `./gradlew build` regenerates `TrackpointQueries`.

---

### Step 9: Add `TrackpointRepository` (interface + impl + mapper)
**Goal**: Persist/read the track behind a clean contract (FR-02 repo style).
**Files**: `domain/repository/TrackpointRepository.kt`, `data/repository/TrackpointRepositoryImpl.kt`, extend `data/mapper/Mappers.kt`

```kotlin
// domain/repository/TrackpointRepository.kt
package com.example.ta33.domain.repository
import com.example.ta33.domain.model.Trackpoint
import kotlinx.coroutines.flow.Flow

interface TrackpointRepository {
    suspend fun append(trackpoint: Trackpoint)
    fun observeTrack(runSessionId: String): Flow<List<Trackpoint>>
    suspend fun getTrack(runSessionId: String): List<Trackpoint>
    suspend fun getLastTrackpoint(runSessionId: String): Trackpoint?  // for resume-seed
    suspend fun clearTrack(runSessionId: String)
}
```
```kotlin
// data/mapper/Mappers.kt (add)
import com.example.ta33.data.db.Trackpoint as TrackpointRow
fun TrackpointRow.toDomain() = Trackpoint(
    id = id, runSessionId = runSessionId,
    location = GeoPoint(latitude, longitude),
    accuracyMeters = accuracyMeters, timestampMillis = timestampMillis,
    syncStatus = SyncStatus.fromDb(syncStatus),
)
```
```kotlin
// data/repository/TrackpointRepositoryImpl.kt (essentials)
class TrackpointRepositoryImpl(private val db: Ta33Database) : TrackpointRepository {
    private val q get() = db.trackpointQueries   // verify exact accessor name after first build
    override suspend fun append(tp: Trackpoint) = withContext(Dispatchers.Default) {
        q.insertTrackpoint(tp.id, tp.runSessionId, tp.location.latitude, tp.location.longitude,
            tp.accuracyMeters, tp.timestampMillis, tp.syncStatus.name)
    }
    override fun observeTrack(runId: String): Flow<List<Trackpoint>> =
        q.selectTrackForRun(runId).asFlow().mapToList(Dispatchers.Default).map { it.map { r -> r.toDomain() } }
    override suspend fun getLastTrackpoint(runId: String): Trackpoint? = withContext(Dispatchers.Default) {
        q.selectLastTrackpoint(runId).executeAsOneOrNull()?.toDomain()
    }
    // getTrack / clearTrack analogous
}
```
> `IdGenerator` is used by the **use-case** (Step 10) when constructing a `Trackpoint`, keeping the repo a thin persistence layer (matches FR-02).

**Done when**: Compiles; use the exact generated query-accessor name (verify after first build).

---

### Step 10: Add use-cases - `RecordBreadcrumbUseCase`, `ObserveTrackUseCase`
**Goal**: Orchestrate collect → throttle → persist (bound to a run); expose the track.
**Files**: `domain/usecase/RecordBreadcrumbUseCase.kt`, `domain/usecase/ObserveTrackUseCase.kt`

```kotlin
// domain/usecase/RecordBreadcrumbUseCase.kt
package com.example.ta33.domain.usecase
// … imports
class RecordBreadcrumbUseCase(
    private val locationStream: LocationStream,
    private val trackpoints: TrackpointRepository,
    private val throttle: BreadcrumbThrottle,
    private val ids: IdGenerator,   // FR-02
) {
    /** Records the breadcrumb for [runId] until cancelled. Emits each kept Trackpoint (for logging/tests). */
    fun record(runId: String): Flow<Trackpoint> = flow {
        // Resume-seed: don't duplicate a point right after an app kill.
        var lastKept: GeoPosition? = trackpoints.getLastTrackpoint(runId)?.let {
            GeoPosition(it.location, it.accuracyMeters, it.timestampMillis)
        }
        locationStream.positions().collect { position ->
            when (val d = throttle.decide(lastKept, position)) {
                is BreadcrumbDecision.Keep -> {
                    val tp = Trackpoint(
                        id = ids.newId(), runSessionId = runId,
                        location = position.location, accuracyMeters = position.accuracyMeters,
                        timestampMillis = position.timestampMillis,
                    )
                    trackpoints.append(tp)
                    lastKept = position
                    emit(tp)
                }
                is BreadcrumbDecision.Drop -> { /* ignore; d.reason available for debug logging */ }
            }
        }
    }
}
```
```kotlin
// domain/usecase/ObserveTrackUseCase.kt
class ObserveTrackUseCase(private val trackpoints: TrackpointRepository) {
    operator fun invoke(runId: String): Flow<List<Trackpoint>> = trackpoints.observeTrack(runId)
}
```
**Done when**: Compiles; behaviour locked by Step 11 tests.

---

### Step 11: Add the headless `LiveLocationViewModel`
**Goal**: Expose permission + current position + accumulated track as a `StateFlow`; no UI here.
**Files**: `presentation/LiveLocationViewModel.kt`

```kotlin
package com.example.ta33.presentation
// … imports
data class LiveLocationUiState(
    val permissionStatus: LocationPermissionStatus = LocationPermissionStatus.NOT_DETERMINED,
    val currentPosition: GeoPosition? = null,   // raw/live (for FR-06 dot)
    val track: List<Trackpoint> = emptyList(),  // persisted, downsampled (for FR-06 polyline)
    val isTracking: Boolean = false,
)

class LiveLocationViewModel(
    private val locationStream: LocationStream,
    private val permission: LocationPermissionController,
    private val recordBreadcrumb: RecordBreadcrumbUseCase,
    private val observeTrack: ObserveTrackUseCase,
    private val tracking: LocationTrackingController,
) : ViewModel() {
    private val _state = MutableStateFlow(LiveLocationUiState())
    val state: StateFlow<LiveLocationUiState> = _state.asStateFlow()
    private var recordJob: Job? = null

    fun bind(runId: String) {
        permission.observeStatus()
            .onEach { s -> _state.update { it.copy(permissionStatus = s) } }.launchIn(viewModelScope)
        locationStream.positions()
            .onEach { p -> _state.update { it.copy(currentPosition = p) } }.launchIn(viewModelScope)
        observeTrack(runId)
            .onEach { t -> _state.update { it.copy(track = t) } }.launchIn(viewModelScope)
        startRecording(runId)
    }

    fun startRecording(runId: String) {
        if (recordJob?.isActive == true) return
        tracking.start()                                   // platform seam (foreground/background)
        _state.update { it.copy(isTracking = true) }
        recordJob = recordBreadcrumb.record(runId).launchIn(viewModelScope)
    }

    fun stopRecording() {
        recordJob?.cancel(); recordJob = null
        tracking.stop()
        _state.update { it.copy(isTracking = false) }
    }
}
```
> Mirrors FR-02 `RunLogViewModel` / FR-01 `AppViewModel` (`MutableStateFlow` + `asStateFlow`, `viewModelScope`). Current position comes straight from the stream (smooth); track comes from persisted downsampled points.

**Done when**: Compiles.

---

### Step 12: Register in Koin + Swift accessor
**Goal**: Make everything resolvable (platform seams in `platformModule`, rest in `appModule`).
**Files**: `di/PlatformModule.kt` (+ `.android.kt`, `.ios.kt`), `di/AppModule.kt`, `di/Koin.kt`

`platformModule` - Android:
```kotlin
single<LocationProvider> { AndroidLocationProvider(androidContext()) }
single<LocationPermissionController> { AndroidLocationPermissionController(androidContext()) }
single<LocationTrackingController> { AndroidLocationTrackingController(androidContext()) }
```
`platformModule` - iOS:
```kotlin
single<LocationProvider> { IosLocationProvider() }
single<LocationPermissionController> { IosLocationPermissionController() }
single<LocationTrackingController> { IosLocationTrackingController() }
```
`appModule`:
```kotlin
single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }      // app scope for shareIn
single { BreadcrumbConfig() }                                         // field-tunable thresholds
single { BreadcrumbThrottle(get()) }
single { LocationStream(get(), get()) }                               // provider, appScope
single<TrackpointRepository> { TrackpointRepositoryImpl(get()) }      // db
factory { RecordBreadcrumbUseCase(get(), get(), get(), get()) }       // stream, repo, throttle, IdGenerator
factory { ObserveTrackUseCase(get()) }
factory { LiveLocationViewModel(get(), get(), get(), get(), get()) }
```
`Koin.kt` `ViewModelProvider`: `fun liveLocationViewModel(): LiveLocationViewModel = KoinPlatform.getKoin().get()`.

**Done when**: `./gradlew build` passes; Koin resolves (Step 13 / app launch); iOS builds.

---

### Step 13: Unit tests (`commonTest`, fake location Flow)
**Goal**: Lock throttle, orchestration, sharing, and VM state. Prefer fakes over a real DB.
**Files**: under `shared/src/commonTest/kotlin/com/example/ta33/`

- **`BreadcrumbThrottleTest`** - first good fix → `Keep`; accuracy `> max` → `Drop(POOR_ACCURACY)`; within `minTime` → `Drop(TOO_SOON)`; enough time but `< minDistance` → `Drop(TOO_CLOSE)`; enough time + distance → `Keep`. Use small lat/lon deltas so `GeoUtils.distanceMeters` crosses the 10 m boundary.
- **`LocationStreamTest`** - a `FakeLocationProvider` counting subscriptions; two simultaneous collectors of `LocationStream.positions()` → underlying provider subscribed **once**; late subscriber gets the replayed last fix. (`runTest` + `StandardTestDispatcher` as the app scope.)
- **`RecordBreadcrumbUseCaseTest`** - `FakeLocationStream` emitting a scripted list of `GeoPosition` (a mix of close/far/poor-accuracy), a `FakeTrackpointRepository` (in-memory list): assert only the kept points are appended, with expected ids (fixed `IdGenerator`) and coordinates; **resume case** - pre-seed `getLastTrackpoint` and feed a nearby fix → dropped (no duplicate).
- **`ObserveTrackUseCaseTest`** - fake repo `MutableStateFlow<List<Trackpoint>>` → emissions pass through ordered.
- **`LiveLocationViewModelTest`** - `runTest` + `Dispatchers.setMain(StandardTestDispatcher())`: `bind(runId)` sets `permissionStatus` from a `FakePermissionController`, `currentPosition` from a `FakeLocationStream`, `track` from the fake repo; `startRecording` sets `isTracking = true` and calls `FakeTrackingController.start()`; `stopRecording` cancels + `stop()`.

Fakes: `FakeLocationProvider`, `FakeLocationStream` (or reuse real `LocationStream` over a fake provider), `FakeTrackpointRepository`, `FakePermissionController`, `FakeTrackingController`.

**Done when**: `./gradlew :shared:allTests` green.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| Permission not granted / denied | No fixes; state shows status; nothing recorded | `LocationProvider` closes the flow on `SecurityException`; VM surfaces `permissionStatus`; recording is a no-op with no data |
| Poor-accuracy fix (canyon reflection) | Not recorded | `BreadcrumbThrottle` `Drop(POOR_ACCURACY)` when `accuracyMeters > maxAccuracyMeters` |
| Standing still | No new trackpoints | Distance filter `Drop(TOO_CLOSE)`; track stays compact, storage-light |
| Rapid dense fixes while moving fast | Capped density | Time floor `Drop(TOO_SOON)` below `minTimeMillis` |
| GPS jitter while stationary (fix hops a few m) | Mostly suppressed | Accuracy filter + distance threshold; residual noise accepted for Etapa 1 (note §12) |
| App killed mid-run, relaunched | Resume without duplicate/adjacent point | Use-case seeds `lastKept` from `getLastTrackpoint(runId)`; foreground service should keep recording (field-test) |
| Two consumers (FR-05 + FR-08) | One GPS subscription | `LocationStream.shareIn(WhileSubscribed, replay=1)`; no second listener |
| No active run bound | No recording | VM `bind(runId)` requires a run id (from FR-02 active run / FR-01); if absent, caller does not bind |
| OS location services disabled | Empty/erroring stream handled gracefully | Provider closes flow; VM `currentPosition` stays null; surface via permission/status where possible |
| Screen off for hours (battery saver) | Recording should continue | `LocationTrackingController` foreground service / background modes - **field-test required**, OEM killers may still interrupt (documented risk) |
| Clock/timestamp anomalies from provider | Ordering by `timestampMillis` may be imperfect | Use the fix timestamp as-is (as FR-02 accepts for wall-clock); note monotonic source for Etapa 2 |
| Very long track (thousands of points) | Bounded by downsample; reads stay ordered | Distance/time downsample keeps counts reasonable; `observeTrack` ordered by time, indexed by `runSessionId` |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: GPS coordinates/accuracy are numeric from the OS; clamp/validate before persisting (`accuracyMeters >= 0`, finite lat/lon). Ids are app-generated UUIDs (FR-02 `IdGenerator`), not user input.
- **Auth/Access control**: None in Etapa 1 - participant anonymous, data local. No token stored (Etapa 2, Keystore/Keychain per stack §4).
- **Sensitive data**: A location track is **personal data**. In Etapa 1 it stays **on-device only** (no upload); `syncStatus` stays `PENDING`. Before any coordinate leaves the device (Etapa 2 sync) run a privacy review. This directly affects **store approval** - provide clear, honest location-permission rationale strings (zadani: "zdůvodnění přístupu k poloze, prohlášení o soukromí"), especially for background location.
- **Least privilege**: Request **when-in-use** first; escalate to **background/always** only because multi-hour trail recording genuinely needs it - document this justification for both stores.
- **Logging**: Napier at debug only; **never** log the full coordinate stream or precise positions at info level (mirror FR-02 §6). Keep GPS/permission logs coarse; never log to persistent files.

---

## 7. ASSUMPTIONS

> User opted out of clarification (`skip questions` / `proceed directly`). Accepted defaults recorded here.

1. **FR-02 is implemented first** - its `GeoPoint`, `GeoUtils`, `RunSession`, `IdGenerator`, `SyncStatus`, `Ta33Database`, `appModule`, `platformModule` exist and are referenced (not duplicated). If wrong: FR-05 cannot compile; do FR-02 first.
2. **`Trackpoint` is a new additive table on `Ta33Database`** (clearly labelled), bound to `RunSession.id`, mirroring FR-02 conventions (enums as TEXT, lat/lon REAL, `Long` millis, no adapters/migrations). If FR-02's DB construction differs, adapt the accessor names - do not restructure FR-02.
3. **`LocationProvider`/`LocationPermissionController`/`LocationTrackingController` are provided via `platformModule`** (same pattern as FR-11's `FileStorage`/`ConnectivityMonitor`), not `expect class`. Impact if wrong: minor DI reshuffle.
4. **The same `LocationProvider`/`LocationStream` will be reused by FR-08** (geofencing). FR-05 must not make it breadcrumb-specific. FR-08 adds its own geofence use-case consuming `LocationStream.positions()` + FR-02 `GeoUtils.isWithinRadius`.
5. **Throttle defaults** (10 m / 5 s / 50 m accuracy) are reasonable starting points and **field-tunable** via `BreadcrumbConfig`. Impact if wrong: change config values only; logic unchanged.
6. **Keep rule is `distance >= min AND elapsed >= min`** (plus accuracy gate). If field data favours an OR policy, that is a localized change in `BreadcrumbThrottle` (§12.2).
7. **Permission status is readable now; requesting/dialogs are UI-phase.** `request*` impls may be documented stubs (Android especially needs an Activity result API). Impact if wrong: none on data logic.
8. **Background execution is a seam only** (`LocationTrackingController`); real foreground-service/battery tuning is field work on physical devices (zadani). Impact if under-scoped now: recording may pause when backgrounded until the seam is tuned - acceptable for a logic-only phase.
9. **Android uses Fused Location** (`play-services-location`) primary with `LocationManager` fallback; dependency stays in `androidMain`. iOS CoreLocation is a system framework. Verify library versions at implementation time (stack §9).
10. **App `CoroutineScope` for `shareIn`** is a DI `single`; it lives for the app's process. Impact if wrong: swap `SharingStarted`/scope - localized.
11. **Only Android + iOS targets**, so `Dispatchers.Default` + Fused/CoreLocation suffice.
12. **Ties to FR-01 are optional** - permission status is a runtime map-screen concern, not a startup gate. FR-01's `AppUiState` is untouched here (see §12.3 for an optional future flag).

> Open questions live in Section 12.

---

## 8. QUICK REFERENCE

### Files to Modify
- `gradle/libs.versions.toml` - add `play-services-location` (+ version).
- `shared/build.gradle.kts` - add `play.services.location` to **androidMain** only.
- `shared/src/commonMain/kotlin/com/example/ta33/di/PlatformModule.kt` (+ `.android.kt`, `.ios.kt`) - provide `LocationProvider`, `LocationPermissionController`, `LocationTrackingController`.
- `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt` - register app scope, config, throttle, `LocationStream`, `TrackpointRepository`, use-cases, ViewModel.
- `shared/src/commonMain/kotlin/com/example/ta33/di/Koin.kt` - add `ViewModelProvider.liveLocationViewModel()`.
- `shared/src/commonMain/kotlin/com/example/ta33/data/mapper/Mappers.kt` - add `Trackpoint` row→domain mapper.
- `androidApp/src/main/AndroidManifest.xml` - location + foreground-service + background permissions (+ service entry, commented until built).
- iOS `Info.plist` - `NSLocationWhenInUseUsageDescription`, `NSLocationAlwaysAndWhenInUseUsageDescription`, `UIBackgroundModes = [location]`.

### Files to Create
- `domain/model/GeoPosition.kt`, `LocationPermissionStatus.kt`, `BreadcrumbConfig.kt`, `Trackpoint.kt`
- `domain/geo/BreadcrumbThrottle.kt`
- `data/location/LocationProvider.kt` (+ `.android.kt`, `.ios.kt`)
- `data/location/LocationStream.kt`
- `data/location/LocationPermissionController.kt` (+ `.android.kt`, `.ios.kt`)
- `data/location/LocationTrackingController.kt` (+ `.android.kt`, `.ios.kt`) [Android: a `LocationForegroundService` stub]
- `domain/repository/TrackpointRepository.kt`
- `data/repository/TrackpointRepositoryImpl.kt`
- `domain/usecase/RecordBreadcrumbUseCase.kt`, `ObserveTrackUseCase.kt`
- `presentation/LiveLocationViewModel.kt`
- SQLDelight: `Trackpoint.sq`
- `commonTest/…` - `BreadcrumbThrottleTest`, `LocationStreamTest`, `RecordBreadcrumbUseCaseTest`, `ObserveTrackUseCaseTest`, `LiveLocationViewModelTest` (+ fakes: `FakeLocationProvider`, `FakeTrackpointRepository`, `FakePermissionController`, `FakeTrackingController`).

### Dependencies
- `com.google.android.gms:play-services-location` @ ~`21.3.0` - **androidMain only**; Fused Location. (Fallback `LocationManager` needs no dep.)
- iOS **CoreLocation** - system framework, no dependency.
- (Already present: coroutines-core + `kotlinx-coroutines-test` (FR-02), SQLDelight 2.1.0 + coroutines-ext + drivers, Koin 4.1.0, lifecycle-viewmodel, Napier.)
- **No map SDK** - MapLibre is FR-06.

### Commands
```bash
./gradlew build                      # compile + regenerate SQLDelight (Trackpoint)
./gradlew :shared:allTests           # shared unit tests (fake location Flow)
./gradlew :androidApp:assembleDebug  # Android sanity (Fused + manifest perms)
# iOS headless (compiles LocationProvider/Permission/Tracking actuals + ViewModelProvider):
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
| **A. `LocationProvider` seam + hot `LocationStream` + pure throttle + additive `Trackpoint` table + `RecordBreadcrumbUseCase`** | Clean layering; one shared GPS subscription (battery) reusable by FR-08; pure testable throttle; offline-first persistence; reuses FR-02 `GeoUtils`/`GeoPoint`/`IdGenerator` | Two seams (provider + stream) and a new table to maintain | ✅ |
| B. **Raw stream straight into DB, downsample in SQL** (e.g. distance in a query/trigger) | Fewer Kotlin layers | Downsample logic not pure/unit-testable; DB churn (every fix written); SQL geo math awkward; can't share cleanly with FR-08 | - |
| C. **In-memory track only, persist once at run finish** | Simplest write path | Loses the track on crash/kill mid-run - violates offline-first durability (zadani); no resume | - |
| D. **Cold `Flow` per consumer** (no `LocationStream`) | Less code | FR-05 + FR-08 spin up two GPS listeners → double battery drain over a multi-hour route; the exact cost to avoid on this trail | - |

**Why the selected approach won**: It keeps the whole subsystem in shared, unit-testable Kotlin (stack goals), persists durably for offline-first, reuses FR-02 primitives with zero duplication, and - critically - provides a single shared location stream FR-08 reuses directly, avoiding a second battery-hungry GPS subscription.

### 12.2 Open Questions

- [ ] **Final throttle thresholds (distance/time/accuracy) and AND-vs-OR keep policy** - Proposed direction: ship the defaults (10 m / 5 s / 50 m, AND policy) and tune from real field logs on the Adršpach/Teplice trail; all localized to `BreadcrumbConfig`/`BreadcrumbThrottle`.
- [ ] **How resilient must background recording be, and via what mechanism?** - Proposed direction: Android foreground service with `foregroundServiceType=location` + battery-exemption prompt; iOS background location updates. Validate on real devices across OEM battery savers (the biggest field risk) before committing UX.
- [ ] **Where does `runId` come from when binding the map/live-location screen?** - Proposed direction: from FR-02's active run (`observeActiveRun`, exposed by FR-01); bind only when a run is active. Confirm when FR-06/UI lands.
- [ ] **Should FR-01 `AppUiState` surface a coarse "location permission granted" flag?** - Proposed direction: not now (runtime map concern); add an additive flag later if a startup/permission gate is wanted.
- [ ] **Track simplification for very long runs (e.g. Douglas-Peucker) before FR-06 renders it** - Proposed direction: not needed for Etapa 1 given downsampling; revisit if the polyline gets heavy for MapLibre.

### 12.3 Suggestions & Follow-ups

- **FR-08 seam**: FR-08 injects the same `LocationStream` and adds a `GeofenceUseCase` mapping `positions()` through `GeoUtils.isWithinRadius(control, pos.location)` (~50 m) - no change to `LocationProvider`. Keep the stream feature-agnostic.
- **FR-06 seam**: FR-06 (map render) consumes `LiveLocationUiState.currentPosition` (live dot) + `.track` (polyline); no new data contract needed. Expose the track as `List<GeoPoint>` there if MapLibre prefers it.
- Add a **JVM SQLite driver** in `androidHostTest` for a real DB integration test of `TrackpointRepositoryImpl` (beyond fakes) - out of scope here.
- Add a Koin **`checkModules()`** test spanning FR-01/FR-02/FR-05 to catch DI-graph breakage.
- Consider **Turbine** for `StateFlow`/`Flow` assertions in `LiveLocationViewModelTest`/`LocationStreamTest` (optional test-only dep).
- Consider a **monotonic timestamp** source for ordering during multi-hour runs (Etapa 2), guarding against wall-clock jumps (mirrors FR-02's note).
- When the foreground service is built, keep its notification copy honest and minimal - it feeds the **store-review** location justification.

> Sections 9 (Design Reference) and 10 (Corrections From Current State) intentionally omitted: this is logic-only work with no UI/visual spec, and it is greenfield (no prior FR-05 implementation to correct - the new `Trackpoint` table is an additive seam over FR-02, covered in Steps/Assumptions, not a correction).
