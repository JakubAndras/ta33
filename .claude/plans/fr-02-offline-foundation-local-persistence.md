# FR-02 Offline Foundation — Local Persistence & Domain Core (Logic Only)

> **Summary**: Establish the shared offline-first data foundation for TA33 (domain models, SQLDelight schema on the existing `Ta33Database`, repositories, use-cases, a headless log/progress ViewModel, and unit tests) that stores routes, controls, collections, times and log locally on the phone with no network — the base every later FR builds on.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
TA33 must work entirely without a signal on the trail: a participant's collected controls, start/finish times, split times, and their control log (deník) have to be saved directly on the phone and survive app restarts. In Etapa 1 there is no server — the result is proven at the finish by showing the app to the organizer, so everything must be correct and durable purely locally.

### 1.2 Solution Overview
Build the shared Kotlin core (in the existing `:shared` module) as a Clean-Architecture data foundation: a small extensible domain model, an extended `Ta33Database` SQLDelight schema, repository interfaces (`domain`) with SQLDelight-backed implementations (`data`), coroutine/Flow reactive reads, use-cases for the core actions (start/finish run, collect control, observe log/progress), and one headless `StateFlow` ViewModel for the log. A `SyncStatus` field is baked into every user-generated row now, unused for upload, so Etapa 2 sync attaches with no migration.

### 1.3 Scope: What This IS
- Shared **domain models**: `Participant`, `Route`, `ControlPoint`, `RunSession`, `CollectedControl`, plus value/read models `SyncStatus`, `RunLogEntry`, `RunProgress`, `GeoPoint`.
- **SQLDelight schema** extending the existing `Ta33Database` (new `.sq` files) with typed queries.
- **Repository interfaces** in `domain/repository` + **implementations** in `data/repository` (row ⇄ domain mapping).
- **Use-cases** in `domain/usecase`: ensure local participant, start run, finish run, collect control (with geofence decision), observe run log + progress.
- Pure **domain utilities**: Haversine distance / within-radius decision (`GeoUtils`) feeding FR-08's ~50 m rule.
- Testable **`TimeProvider`** and **`IdGenerator`** abstractions in `core` (deterministic in tests).
- One **headless ViewModel** (`RunLogViewModel`) exposing `StateFlow<RunLogUiState>` (log + progress "2 z 5"); no UI consumes it yet.
- **Koin registration** of all of the above in `di/AppModule.kt`.
- **Unit tests** in `commonTest` (mapping, use-cases, geo, progress, SyncStatus default).

### 1.4 Scope: What This IS NOT
- **No UI whatsoever** — no Compose/SwiftUI screens, no navigation, no theming, no visuals. UI is a deliberately deferred later phase.
- **No server sync / no Ktor networking / no JSON content download** — that is FR-11 / Etapa 2. Only local persistence now. `SyncStatus` is stored but never used to upload.
- **No QR camera / no scanning implementation** and **no platform GPS acquisition** — those are platform/later FRs. We model only the *logic and data* they feed (start/finish timestamp capture; the within-radius decision given a coordinate).
- **No column-adapter-based enum storage / no schema migration framework** — first schema version, enums stored as TEXT and mapped in Kotlin.
- **No new Gradle modules** — everything lives in the existing `:shared` module as package layers.

---

## 2. SUCCESS CRITERIA

Implementation is COMPLETE when ALL criteria are met:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | Project compiles with new schema, models, repos, use-cases, ViewModel | `./gradlew build` succeeds; SQLDelight codegen produces `Ta33Database` with new tables |
| 2 | All new tables exist on `Ta33Database` (Route, ControlPoint, Participant, RunSession, CollectedControl) with typed queries | Generated `*Queries` classes exist; build passes |
| 3 | A control can be collected once and re-collection is a no-op | Unit test on `CollectControlUseCase`: second call for same (run, control) does not add a row |
| 4 | Collecting a control records the wall-clock timestamp (split time) from `TimeProvider` | Unit test with fixed `TimeProvider` asserts stored `collectedAt` |
| 5 | Start/finish set run timestamps; finish before start is rejected | Unit tests on `StartRunUseCase`/`FinishRunUseCase` |
| 6 | Geofence decision returns collectable only within control radius (~50 m default) | Unit test on `GeoUtils`/`CanCollectControlUseCase` with known coords across the 50 m boundary |
| 7 | Log exposes controls in order with state (COLLECTED / NEXT / PENDING) + split time, and progress "collected of total" | Unit test on log assembly + `RunLogViewModel` state |
| 8 | Every user-generated row defaults to `SyncStatus.PENDING` | Unit test asserts default; mapper parses unknown/empty TEXT → `PENDING` |
| 9 | Reactive reads emit on data change (Flow) | Repository returns `Flow`; ViewModel state updates when collection added (verified in VM test with fake repo) |
| 10 | All new components resolvable via Koin | `appModule` registers repos/use-cases/ViewModel; a Koin `checkModules`/resolution test or app start passes |
| 11 | `./gradlew :shared:allTests` green | Run tests |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
                         presentation/
                    ┌──────────────────────┐
                    │   RunLogViewModel     │  StateFlow<RunLogUiState>
                    │  (headless, no UI)    │  (log entries + progress)
                    └───────────┬──────────┘
                                │ use-cases
                    domain/usecase/
   ┌───────────────┬───────────┼─────────────┬────────────────────┐
   │ EnsureLocal   │ StartRun  │ CollectCtrl │ FinishRun │ Observe │
   │ Participant   │           │ (+geofence) │           │ RunLog  │
   └───────┬───────┴─────┬─────┴──────┬──────┴─────┬─────┴────┬────┘
           │             │            │            │          │
   domain/repository/ (interfaces)    │   domain/geo/GeoUtils  │ core/TimeProvider, IdGenerator
   ┌───────────────────────────────────────────────────────────────┐
   │ ParticipantRepository │ RouteRepository │ RunRepository         │
   └───────────────────────────────┬───────────────────────────────┘
                                    │ implements
   data/repository/ (SQLDelight-backed)   data/mapper/ (row ⇄ domain)
                                    │
                            data/db/Ta33Database  (SQLDelight, generated)
                                    │  driver via di/platformModule (expect/actual)
                        ┌───────────┴───────────┐
                   Android AndroidSqliteDriver   iOS NativeSqliteDriver
```

**Data flow (collect a control, FR-08 + FR-09 split):** GPS coordinate (supplied by a later FR) → `CollectControlUseCase(runId, controlId, currentLocation?)` → `GeoUtils.isWithin(control, location)` decision → `RunRepository.collectControl(...)` inserts a `CollectedControl` row with `collectedAt = TimeProvider.nowMillis()` and `syncStatus = PENDING` → Flow re-emits → `RunLogViewModel` recomputes log + progress.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Where the code lives | Existing `:shared` module, new **package layers** (`domain/model`, `domain/repository`, `domain/usecase`, `domain/geo`, `data/repository`, `data/mapper`, `core`, `presentation`) | Matches project-stack §12 "layers as packages, not Gradle modules yet"; no duplication |
| Database | **Extend existing `Ta33Database`** with new `.sq` files under `.../sqldelight/com/example/ta33/data/db/` | Stack mandates one DB; SQLDelight codegen already wired to `./gradlew build` |
| Enum storage | `syncStatus` as **TEXT**, mapped in Kotlin (safe-parse → `PENDING`) | Avoids column adapters that complicate `Ta33Database(get())` construction; trivial to change later |
| Timestamps | `Long` epoch-millis via injectable **`TimeProvider`** | No `kotlinx-datetime` dependency present; `Long` is DB-native (INTEGER) and deterministic in tests |
| IDs | `String` (UUID) via injectable **`IdGenerator`** | KMP-safe, stable across platforms, deterministic in tests; ready for server-assigned ids in Etapa 2 |
| The core aggregate | **`RunSession`** (one participant's attempt at a route) owns start/finish + collected controls | Cleanly ties times ↔ collections ↔ progress; split time = `collectedAt` relative to `startedAt` |
| "order" column | Named **`ordinal`** (INTEGER) | `order` is a reserved SQL keyword |
| Reactive reads | SQLDelight **`.asFlow().mapToList(Dispatchers.Default)`** (coroutines-extensions already in deps) | Offline-first reactive log without polling |
| DB write threading | `withContext(Dispatchers.Default)` in repo impls | `Dispatchers.IO` is not in commonMain; Default is correct for KMP mobile |
| Geofence radius | Per-control `radiusMeters` column, **default 50.0** | FR-08 "~50 m"; per-control override keeps data-driven flexibility |
| Demo scaffold | **Remove** the placeholder `Checkpoint` table + `Greeting*` demo files is out of scope; leave `Greeting*` untouched, only retire the demo `Checkpoint` table (unused) | Keeps schema clean without disturbing the working greeting sample used by the app scaffold |

---

## 4. IMPLEMENTATION STEPS

> Execute steps in order. Do not skip. All paths are under `shared/src/commonMain/kotlin/com/example/ta33/` unless stated. Base package `com.example.ta33`.

### Step 1: Add test-only coroutine runner dependency
**Goal**: Enable `runTest` for suspend use-case tests in `commonTest` (only `kotlin.test` is present today).
**Files**: `gradle/libs.versions.toml`, `shared/build.gradle.kts`

In `libs.versions.toml` add under `[libraries]`:
```toml
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
```
In `shared/build.gradle.kts` `commonTest.dependencies`:
```kotlin
commonTest.dependencies {
    implementation(libs.kotlin.test)
    implementation(libs.kotlinx.coroutines.test)
}
```
**Done when**: `./gradlew :shared:compileKotlinIosSimulatorArm64` (or `build`) resolves the new dependency without error.

---

### Step 2: Add `core` abstractions — `TimeProvider` and `IdGenerator`
**Goal**: Deterministic, injectable time and id sources (production impls + test fakes).
**Files**: `core/TimeProvider.kt`, `core/IdGenerator.kt`

```kotlin
// core/TimeProvider.kt
package com.example.ta33.core

import kotlin.time.Clock          // stable in Kotlin 2.2+; project is on 2.4.0
import kotlin.time.ExperimentalTime

fun interface TimeProvider {
    fun nowMillis(): Long
}

@OptIn(ExperimentalTime::class)
class SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
```
```kotlin
// core/IdGenerator.kt
package com.example.ta33.core

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun interface IdGenerator {
    fun newId(): String
}

@OptIn(ExperimentalUuidApi::class)
class UuidGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}
```
> If `kotlin.time.Clock` or `kotlin.uuid.Uuid` are still flagged experimental on this toolchain, keep the `@OptIn`; if unavailable, fall back to an `expect/actual nowMillis()`/`randomUuid()` in `core` (Android `System.currentTimeMillis()` / `java.util.UUID`, iOS `NSDate`/`NSUUID`). See Section 12.2.

**Done when**: File compiles; `SystemTimeProvider().nowMillis()` returns a positive Long.

---

### Step 3: Add domain models
**Goal**: The shared foundation models every later FR references.
**Files**: `domain/model/SyncStatus.kt`, `Participant.kt`, `Route.kt`, `ControlPoint.kt`, `RunSession.kt`, `CollectedControl.kt`, `GeoPoint.kt`, `RunLogEntry.kt`, `RunProgress.kt` (all in `domain/model/`)

```kotlin
// domain/model/SyncStatus.kt
package com.example.ta33.domain.model

/** Prepared for Etapa 2 server sync; in Etapa 1 everything stays PENDING and is never uploaded. */
enum class SyncStatus {
    PENDING, SYNCED, FAILED;
    companion object {
        fun fromDb(raw: String?): SyncStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}
```
```kotlin
// domain/model/GeoPoint.kt
package com.example.ta33.domain.model
data class GeoPoint(val latitude: Double, val longitude: Double)
```
```kotlin
// domain/model/ControlPoint.kt
package com.example.ta33.domain.model
data class ControlPoint(
    val id: String,
    val routeId: String,
    val ordinal: Int,               // 1-based order along the route
    val name: String,
    val location: GeoPoint,
    val radiusMeters: Double = 50.0, // FR-08 ~50 m
)
```
```kotlin
// domain/model/Route.kt
package com.example.ta33.domain.model
data class Route(
    val id: String,
    val name: String,          // e.g. "Trasa A"
    val distanceKm: Double,     // e.g. 33.0
    val controls: List<ControlPoint> = emptyList(), // aggregate; empty when metadata-only
) {
    val controlCount: Int get() = controls.size
}
```
```kotlin
// domain/model/Participant.kt
package com.example.ta33.domain.model
data class Participant(
    val id: String,
    val displayName: String? = null, // anonymous in Etapa 1; gains identity in Etapa 2
    val createdAtMillis: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
```
```kotlin
// domain/model/RunSession.kt
package com.example.ta33.domain.model
data class RunSession(
    val id: String,
    val routeId: String,
    val participantId: String,
    val startedAtMillis: Long? = null,  // set by QR start (FR-09)
    val finishedAtMillis: Long? = null, // set by QR finish (FR-09)
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
```
```kotlin
// domain/model/CollectedControl.kt
package com.example.ta33.domain.model
data class CollectedControl(
    val id: String,
    val runSessionId: String,
    val controlId: String,
    val collectedAtMillis: Long,        // wall-clock; split time = collectedAt - run.startedAt
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
```
```kotlin
// domain/model/RunLogEntry.kt  (read model for FR-04 deník)
package com.example.ta33.domain.model
data class RunLogEntry(
    val control: ControlPoint,
    val state: State,
    val collectedAtMillis: Long?,       // null unless COLLECTED
    val splitMillis: Long?,             // collectedAt - run.startedAt, null unless both known
) {
    enum class State { COLLECTED, NEXT, PENDING }
}
```
```kotlin
// domain/model/RunProgress.kt
package com.example.ta33.domain.model
data class RunProgress(val collected: Int, val total: Int) {
    val label: String get() = "$collected z $total"   // e.g. "2 z 5"
}
```
**Done when**: All model files compile.

---

### Step 4: Extend the SQLDelight schema (retire demo table, add 5 tables)
**Goal**: Persist the model on `Ta33Database`.
**Files**: under `shared/src/commonMain/sqldelight/com/example/ta33/data/db/`:
- Edit `schema.sq` → remove the demo `Checkpoint` table + its `selectAll`/`insertCheckpoint` (unused scaffold). If preferred, keep the file but empty its contents; verify nothing references generated `Checkpoint`/`CheckpointQueries` (the greeting scaffold does not).
- Create `Route.sq`, `ControlPoint.sq`, `Participant.sq`, `RunSession.sq`, `CollectedControl.sq`.

```sql
-- Route.sq
CREATE TABLE Route (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    distanceKm REAL NOT NULL
);
selectAllRoutes: SELECT * FROM Route ORDER BY name;
selectRouteById: SELECT * FROM Route WHERE id = ?;
upsertRoute: INSERT OR REPLACE INTO Route(id, name, distanceKm) VALUES (?, ?, ?);
```
```sql
-- ControlPoint.sq
CREATE TABLE ControlPoint (
    id TEXT NOT NULL PRIMARY KEY,
    routeId TEXT NOT NULL,
    ordinal INTEGER AS Int NOT NULL,
    name TEXT NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    radiusMeters REAL NOT NULL DEFAULT 50.0
);
CREATE INDEX controlpoint_route ON ControlPoint(routeId);
selectControlsForRoute: SELECT * FROM ControlPoint WHERE routeId = ? ORDER BY ordinal;
selectControlById: SELECT * FROM ControlPoint WHERE id = ?;
upsertControl:
INSERT OR REPLACE INTO ControlPoint(id, routeId, ordinal, name, latitude, longitude, radiusMeters)
VALUES (?, ?, ?, ?, ?, ?, ?);
```
```sql
-- Participant.sq
CREATE TABLE Participant (
    id TEXT NOT NULL PRIMARY KEY,
    displayName TEXT,
    createdAtMillis INTEGER NOT NULL,
    syncStatus TEXT NOT NULL DEFAULT 'PENDING'
);
selectAnyParticipant: SELECT * FROM Participant LIMIT 1;
selectParticipantById: SELECT * FROM Participant WHERE id = ?;
insertParticipant:
INSERT INTO Participant(id, displayName, createdAtMillis, syncStatus) VALUES (?, ?, ?, ?);
```
```sql
-- RunSession.sq
CREATE TABLE RunSession (
    id TEXT NOT NULL PRIMARY KEY,
    routeId TEXT NOT NULL,
    participantId TEXT NOT NULL,
    startedAtMillis INTEGER,
    finishedAtMillis INTEGER,
    syncStatus TEXT NOT NULL DEFAULT 'PENDING'
);
selectRunById: SELECT * FROM RunSession WHERE id = ?;
selectActiveRun: SELECT * FROM RunSession WHERE finishedAtMillis IS NULL ORDER BY startedAtMillis DESC LIMIT 1;
insertRun:
INSERT INTO RunSession(id, routeId, participantId, startedAtMillis, finishedAtMillis, syncStatus)
VALUES (?, ?, ?, ?, ?, ?);
setStarted: UPDATE RunSession SET startedAtMillis = ? WHERE id = ?;
setFinished: UPDATE RunSession SET finishedAtMillis = ? WHERE id = ?;
```
```sql
-- CollectedControl.sq
CREATE TABLE CollectedControl (
    id TEXT NOT NULL PRIMARY KEY,
    runSessionId TEXT NOT NULL,
    controlId TEXT NOT NULL,
    collectedAtMillis INTEGER NOT NULL,
    syncStatus TEXT NOT NULL DEFAULT 'PENDING'
);
CREATE UNIQUE INDEX collectedcontrol_unique ON CollectedControl(runSessionId, controlId);
selectForRun: SELECT * FROM CollectedControl WHERE runSessionId = ? ORDER BY collectedAtMillis;
countForRun: SELECT COUNT(*) FROM CollectedControl WHERE runSessionId = ?;
insertCollected:
INSERT OR IGNORE INTO CollectedControl(id, runSessionId, controlId, collectedAtMillis, syncStatus)
VALUES (?, ?, ?, ?, ?);
```
> `INSERT OR IGNORE` + the unique index make re-collection a safe no-op at the DB layer (belt-and-braces with the use-case check).

**Done when**: `./gradlew build` regenerates `Ta33Database` with `RouteQueries`, `ControlPointQueries`, `ParticipantQueries`, `RunSessionQueries`, `CollectedControlQueries`; no reference to the removed `Checkpoint` remains.

---

### Step 5: Add `GeoUtils` (pure geofence math)
**Goal**: Testable within-radius decision feeding FR-08; no platform GPS.
**Files**: `domain/geo/GeoUtils.kt`

```kotlin
package com.example.ta33.domain.geo

import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import kotlin.math.*

object GeoUtils {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Haversine great-circle distance in metres. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = (b.latitude - a.latitude).toRadians()
        val dLon = (b.longitude - a.longitude).toRadians()
        val lat1 = a.latitude.toRadians()
        val lat2 = b.latitude.toRadians()
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    fun isWithinRadius(control: ControlPoint, location: GeoPoint): Boolean =
        distanceMeters(control.location, location) <= control.radiusMeters

    private fun Double.toRadians() = this * PI / 180.0
}
```
**Done when**: Compiles; two nearby coords give a small metre distance (asserted in Step 10).

---

### Step 6: Add repository interfaces (`domain/repository`)
**Goal**: Contracts later FRs and tests depend on.
**Files**: `domain/repository/ParticipantRepository.kt`, `RouteRepository.kt`, `RunRepository.kt`

```kotlin
// domain/repository/ParticipantRepository.kt
package com.example.ta33.domain.repository
import com.example.ta33.domain.model.Participant
interface ParticipantRepository {
    suspend fun getOrCreateLocalParticipant(): Participant
}
```
```kotlin
// domain/repository/RouteRepository.kt
package com.example.ta33.domain.repository
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.Route
import kotlinx.coroutines.flow.Flow
interface RouteRepository {
    fun observeRoutes(): Flow<List<Route>>                 // metadata only (FR-03 list)
    suspend fun getRouteWithControls(routeId: String): Route? // aggregate (FR-03 detail)
    suspend fun getControl(controlId: String): ControlPoint?
    // Local seeding hook; the actual content source (JSON download) is FR-11.
    suspend fun upsertRoute(route: Route, controls: List<ControlPoint>)
}
```
```kotlin
// domain/repository/RunRepository.kt
package com.example.ta33.domain.repository
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.RunSession
import kotlinx.coroutines.flow.Flow
interface RunRepository {
    suspend fun createRun(routeId: String, participantId: String): RunSession
    suspend fun getRun(runId: String): RunSession?
    fun observeRun(runId: String): Flow<RunSession?>
    suspend fun setStarted(runId: String, startedAtMillis: Long)
    suspend fun setFinished(runId: String, finishedAtMillis: Long)
    suspend fun addCollected(collected: CollectedControl): Boolean // false if already collected
    fun observeCollected(runId: String): Flow<List<CollectedControl>>
}
```
**Done when**: Interfaces compile.

---

### Step 7: Add mappers + repository implementations (`data`)
**Goal**: SQLDelight row ⇄ domain mapping and persistence behind the interfaces.
**Files**: `data/mapper/Mappers.kt`, `data/repository/ParticipantRepositoryImpl.kt`, `RouteRepositoryImpl.kt`, `RunRepositoryImpl.kt`

Mapper example (generated row type names come from SQLDelight = table names):
```kotlin
// data/mapper/Mappers.kt
package com.example.ta33.data.mapper
import com.example.ta33.data.db.ControlPoint as ControlPointRow
import com.example.ta33.domain.model.*

fun ControlPointRow.toDomain() = ControlPoint(
    id = id, routeId = routeId, ordinal = ordinal, name = name,
    location = GeoPoint(latitude, longitude), radiusMeters = radiusMeters,
)
// ...analogous toDomain() for Route, Participant, RunSession, CollectedControl;
// syncStatus via SyncStatus.fromDb(syncStatus)
```
Repository impl pattern (mirrors existing `GreetingRepositoryImpl`, adds DB + Flow):
```kotlin
// data/repository/RunRepositoryImpl.kt (essentials)
package com.example.ta33.data.repository
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.ta33.core.IdGenerator
import com.example.ta33.data.db.Ta33Database
import com.example.ta33.data.mapper.toDomain
import com.example.ta33.domain.model.*
import com.example.ta33.domain.repository.RunRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RunRepositoryImpl(
    private val db: Ta33Database,
    private val ids: IdGenerator,
) : RunRepository {
    private val q get() = db.runSessionQueries
    private val cq get() = db.collectedControlQueries

    override suspend fun createRun(routeId: String, participantId: String): RunSession =
        withContext(Dispatchers.Default) {
            val run = RunSession(ids.newId(), routeId, participantId)
            q.insertRun(run.id, routeId, participantId, null, null, run.syncStatus.name)
            run
        }

    override fun observeCollected(runId: String): Flow<List<CollectedControl>> =
        cq.selectForRun(runId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun addCollected(collected: CollectedControl): Boolean =
        withContext(Dispatchers.Default) {
            val before = cq.countForRun(collected.runSessionId).executeAsOne()
            cq.insertCollected(collected.id, collected.runSessionId, collected.controlId,
                collected.collectedAtMillis, collected.syncStatus.name)
            cq.countForRun(collected.runSessionId).executeAsOne() > before
        }
    // setStarted/setFinished/getRun/observeRun analogous
}
```
`ParticipantRepositoryImpl.getOrCreateLocalParticipant()` selects `selectAnyParticipant`; if none, inserts one with `IdGenerator`+`TimeProvider` (anonymous, `displayName = null`).

**Done when**: All impls compile; use the exact generated query-accessor names (`db.routeQueries`, `db.controlPointQueries`, etc. — verify against generated code after first build).

---

### Step 8: Add use-cases (`domain/usecase`)
**Goal**: Encapsulate the FR actions; the ViewModel and later FRs call these.
**Files**: `domain/usecase/EnsureLocalParticipantUseCase.kt`, `StartRunUseCase.kt`, `FinishRunUseCase.kt`, `CollectControlUseCase.kt`, `ObserveRunLogUseCase.kt`

- `StartRunUseCase(run, time)`: `runRepo.setStarted(runId, time.nowMillis())` (idempotent-safe; ignore if already started — see edge cases).
- `FinishRunUseCase`: reject if run not started or finish < start (throw `IllegalStateException` / return sealed result); else `setFinished`.
- `CollectControlUseCase(runId, controlId, location: GeoPoint?)`:
  1. load control; if `location != null` and `!GeoUtils.isWithinRadius(control, location)` → return `Rejected.OutOfRange` (do not persist).
  2. build `CollectedControl(id=ids.newId(), runId, controlId, collectedAtMillis=time.nowMillis())`; `runRepo.addCollected(...)`.
  3. return `Collected` or `AlreadyCollected` based on the boolean.
  > Passing `location = null` records unconditionally (used before GPS FR exists / by tests). Decision logic is here; GPS acquisition is a later FR.
- `ObserveRunLogUseCase(runId, routeId)`: `combine(routeRepo controls, runRepo.observeRun, runRepo.observeCollected)` → build ordered `List<RunLogEntry>` + `RunProgress`. State rule: collected controls → `COLLECTED` (with `splitMillis = collectedAt - startedAt` when both present); the first non-collected by `ordinal` → `NEXT`; the rest → `PENDING`.

Return type example:
```kotlin
sealed interface CollectResult {
    data class Collected(val control: CollectedControl) : CollectResult
    data object AlreadyCollected : CollectResult
    data object OutOfRange : CollectResult
    data object UnknownControl : CollectResult
}
```
**Done when**: Use-cases compile and are pure of platform/UI concerns.

---

### Step 9: Add headless `RunLogViewModel` (`presentation`)
**Goal**: Expose the deník log + progress as `StateFlow`; no UI consumes it yet.
**Files**: `presentation/RunLogViewModel.kt`

```kotlin
package com.example.ta33.presentation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.domain.model.RunLogEntry
import com.example.ta33.domain.model.RunProgress
import com.example.ta33.domain.usecase.ObserveRunLogUseCase
import kotlinx.coroutines.flow.*

data class RunLogUiState(
    val entries: List<RunLogEntry> = emptyList(),
    val progress: RunProgress = RunProgress(0, 0),
    val loading: Boolean = true,
)

class RunLogViewModel(
    private val observeRunLog: ObserveRunLogUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(RunLogUiState())
    val state: StateFlow<RunLogUiState> = _state.asStateFlow()

    fun bind(runId: String, routeId: String) {
        observeRunLog(runId, routeId)
            .onEach { (entries, progress) -> _state.value = RunLogUiState(entries, progress, loading = false) }
            .launchIn(viewModelScope)
    }
}
```
> Mirrors the existing `GreetingViewModel` pattern (`MutableStateFlow` + `asStateFlow`, `viewModelScope`). `ObserveRunLogUseCase` returns `Flow<Pair<List<RunLogEntry>, RunProgress>>` (or a small data class).

**Done when**: Compiles.

---

### Step 10: Register everything in Koin `appModule`
**Goal**: Make all components resolvable (mirrors current `appModule`).
**Files**: `di/AppModule.kt`

```kotlin
val appModule = module {
    single { Ta33Database(get()) }
    // core
    single<TimeProvider> { SystemTimeProvider() }
    single<IdGenerator> { UuidGenerator() }
    // existing greeting scaffold (leave as-is)
    single<GreetingRepository> { GreetingRepositoryImpl() }
    factory { GreetingViewModel(get()) }
    // repositories
    single<ParticipantRepository> { ParticipantRepositoryImpl(get(), get(), get()) }
    single<RouteRepository> { RouteRepositoryImpl(get()) }
    single<RunRepository> { RunRepositoryImpl(get(), get()) }
    // use-cases
    factory { EnsureLocalParticipantUseCase(get()) }
    factory { StartRunUseCase(get(), get()) }
    factory { FinishRunUseCase(get(), get()) }
    factory { CollectControlUseCase(get(), get(), get()) }
    factory { ObserveRunLogUseCase(get(), get()) }
    // viewmodel
    factory { RunLogViewModel(get()) }
}
```
**Done when**: `./gradlew build` passes and Koin resolves (verified in Step 11 VM/DI test or app launch).

---

### Step 11: Unit tests (`commonTest`)
**Goal**: Lock the core behaviour.
**Files**: under `shared/src/commonTest/kotlin/com/example/ta33/`:
- `SyncStatusTest.kt` — `fromDb(null/""/"NOPE")` → `PENDING`; `fromDb("SYNCED")` → `SYNCED`; new models default to `PENDING`.
- `GeoUtilsTest.kt` — distance of two points ~55 m apart is >50 and a point ~30 m away is ≤50 (use small lat/lon deltas; assert within tolerance).
- `CollectControlUseCaseTest.kt` — with a **fake `RunRepository`** + fixed `TimeProvider`/`IdGenerator`: first collect → `Collected` with expected timestamp; second → `AlreadyCollected`; out-of-range location → `OutOfRange` and no persistence.
- `RunTimingTest.kt` — start sets timestamp; finish before start rejected; split = collectedAt − startedAt.
- `RunLogAssemblyTest.kt` / `RunLogViewModelTest.kt` — 5 controls, 2 collected → progress label "2 z 5", states `[COLLECTED, COLLECTED, NEXT, PENDING, PENDING]` in ordinal order; use `runTest` + fake repos feeding `MutableStateFlow`.

Use `runTest { }` from `kotlinx-coroutines-test` (Step 1). Prefer fakes over a real DB driver (DB-level test needs a JVM SQLite driver — see Section 12.3).

**Done when**: `./gradlew :shared:allTests` is green.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| Control collected twice (double tap / GPS re-entry) | No duplicate; first timestamp wins | `INSERT OR IGNORE` + unique index + use-case returns `AlreadyCollected` |
| Collect while GPS location unknown/null | Persist unconditionally (decision deferred to caller/GPS FR) | `location == null` skips the geofence check |
| Collect out of the ~50 m radius | Not persisted | `GeoUtils.isWithinRadius` false → `OutOfRange`, no DB write |
| Finish tapped before start (bad QR order) | Rejected, run stays open | `FinishRunUseCase` guards `startedAtMillis != null && finish >= start` |
| Start tapped twice | Keep original start time | `StartRunUseCase` no-ops if `startedAtMillis` already set |
| App killed / restarted mid-run | State fully recovered from DB | Everything persisted immediately; `selectActiveRun` restores the open run |
| Route has 0 controls | Progress "0 z 0", empty log, no crash | Guard division/`NEXT` selection on empty list |
| Split time when run not yet started | `splitMillis = null` (show absolute time only) | Compute split only when `startedAtMillis != null` |
| Unknown enum text in DB (future/corrupt) | Treat as `PENDING` | `SyncStatus.fromDb` fallback |
| Clock moves backwards (NTP correction) | Stored as-is; ordering by `collectedAtMillis` may be imperfect | Accept for Etapa 1; note for Etapa 2 (monotonic source) |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: GPS coordinates and radius are numeric; clamp/validate `radiusMeters > 0`. IDs are app-generated UUIDs, not user input.
- **Auth/Access control**: None in Etapa 1 — participant is anonymous and local. No auth token stored (Etapa 2, Keystore/Keychain per stack §4).
- **Sensitive data**: Location history and timestamps are personal data but stay **on-device only** (no upload in Etapa 1). No PII collected (anonymous participant). When sync arrives (Etapa 2), add a privacy review before any coordinate leaves the device — relevant to store approval (zadani location-permission justification).
- **Logging**: Use Napier as the existing scaffold does. Do **not** log full coordinate streams or precise positions at info level; keep GPS/geofence logs at debug and coarse. Never log to persistent files.

---

## 7. ASSUMPTIONS

> User opted out of clarification (`skip questions` / `proceed directly`). Accepted defaults recorded here.

1. **The core aggregate is a `RunSession`** (one participant's attempt at one route). Impact if wrong: log/progress/split wiring would need re-parenting, but tables/models stay largely the same.
2. **Timestamps are epoch-millis `Long`, not `kotlinx-datetime`** (no such dependency present). Impact: if human-readable time math is later needed, add `kotlinx-datetime` and adapt — models keep `Long` at the boundary.
3. **IDs are app-generated UUID strings.** Ready for Etapa 2 where the server may assign ids; local id can map to a server id later. Impact if wrong: minimal, ids are opaque strings.
4. **`SyncStatus` stored as TEXT, mapped in Kotlin** (no column adapter). Impact: switching to an adapter later is a localized change in the DB builder + mappers.
5. **The demo `Checkpoint` table and `Greeting*` scaffold are disposable/decorative.** Plan removes only the `Checkpoint` table (unused) and leaves `Greeting*` untouched. Impact if `Checkpoint` is actually referenced somewhere: build fails fast and we keep it.
6. **Content seeding (real routes/controls) is out of scope (FR-11).** Repositories expose `upsert*` hooks; tests and temporary dev seeds use them. Impact: none on this plan's logic.
7. **Only Android + iOS targets** (no JS/JVM app target), so `Dispatchers.Default` + native/android SQLDelight drivers are sufficient.
8. **One active run at a time in Etapa 1.** `selectActiveRun` assumes a single open run. Impact if multiple routes run concurrently: revisit query (unlikely for this event).
9. **`kotlin.time.Clock` and `kotlin.uuid.Uuid` are usable on Kotlin 2.4.0** (stable/with `@OptIn`). Fallback: `expect/actual` in `core` (Section 12.2).
10. **`kotlinx-coroutines-test` may be added to `commonTest`** (only `kotlin.test` today) — a small, justified test-only dependency.

---

## 8. QUICK REFERENCE

### Files to Modify
- `gradle/libs.versions.toml` — add `kotlinx-coroutines-test` library alias.
- `shared/build.gradle.kts` — add `libs.kotlinx.coroutines.test` to `commonTest`.
- `shared/src/commonMain/sqldelight/com/example/ta33/data/db/schema.sq` — remove demo `Checkpoint` table/queries.
- `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt` — register repos, use-cases, ViewModel, `TimeProvider`, `IdGenerator`.

### Files to Create
- `core/TimeProvider.kt`, `core/IdGenerator.kt` — injectable time/id (prod + test-fakeable).
- `domain/model/…` — `SyncStatus`, `GeoPoint`, `ControlPoint`, `Route`, `Participant`, `RunSession`, `CollectedControl`, `RunLogEntry`, `RunProgress`.
- `domain/geo/GeoUtils.kt` — Haversine + within-radius.
- `domain/repository/…` — `ParticipantRepository`, `RouteRepository`, `RunRepository`.
- `domain/usecase/…` — `EnsureLocalParticipantUseCase`, `StartRunUseCase`, `FinishRunUseCase`, `CollectControlUseCase`, `ObserveRunLogUseCase`.
- `data/mapper/Mappers.kt` — row ⇄ domain.
- `data/repository/…` — `ParticipantRepositoryImpl`, `RouteRepositoryImpl`, `RunRepositoryImpl`.
- `presentation/RunLogViewModel.kt` — headless `StateFlow` log/progress.
- SQLDelight: `Route.sq`, `ControlPoint.sq`, `Participant.sq`, `RunSession.sq`, `CollectedControl.sq`.
- `commonTest/…` — `SyncStatusTest`, `GeoUtilsTest`, `CollectControlUseCaseTest`, `RunTimingTest`, `RunLogViewModelTest` (+ fakes).

### Dependencies
- `org.jetbrains.kotlinx:kotlinx-coroutines-test` @ `coroutines` version (1.10.2) — test-only, `runTest`.
- (Already present: SQLDelight 2.1.0 runtime + coroutines-extensions + drivers, Koin 4.1.0, coroutines-core, lifecycle-viewmodel.)

### Commands
```bash
# Build + regenerate SQLDelight code
./gradlew build

# Run shared unit tests
./gradlew :shared:allTests

# Android debug sanity build
./gradlew :androidApp:assembleDebug
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
| **A. `RunSession` aggregate** (participant → run → collected controls; times on run) | Clean ownership of times/splits/progress; one open run restorable; sync-ready per row | One extra table vs. bare minimum | ✅ |
| B. No `RunSession` — collected controls + a single "current times" row keyed by route | Fewer tables | No clean home for start/finish, can't support re-runs, split logic messy, poor Etapa 2 fit | — |
| C. Store enums via SQLDelight column adapters (`syncStatus TEXT AS SyncStatus`, timestamps as typed `Instant`) | Type-safe columns, less manual mapping | Requires adapters at `Ta33Database(...)` construction (touches `platformModule`/DI), adds `kotlinx-datetime`; heavier for v1 | — |
| D. Persist domain models as JSON blobs (single table) | Fast to write | Loses SQL querying/Flow-by-table, bad for progress/log queries, not the SQLDelight skill the stack targets | — |

**Why the selected approach won**: The `RunSession` aggregate maps 1:1 to the real-world "one attempt at a route with a start, splits and a finish", gives every later FR (FR-03/04/08/09) a clean query surface, and keeps v1 free of column adapters/extra deps while staying sync-ready via per-row `SyncStatus`.

### 12.2 Open Questions

- [ ] **Is `kotlin.time.Clock` / `kotlin.uuid.Uuid` stable (no opt-in) on this exact Kotlin 2.4.0 setup?** — Proposed direction: try the stdlib API first (Step 2); if the compiler flags experimental/unresolved, drop to `expect/actual nowMillis()`/`randomUuid()` in `core` (Android `System.currentTimeMillis()`/`java.util.UUID`, iOS `NSDate().timeIntervalSince1970`/`NSUUID`).
- [ ] **Does the app need multiple concurrent runs (e.g. switch routes) in Etapa 1?** — Proposed direction: assume single active run (`selectActiveRun`); revisit only if the organizer confirms multi-route same-device usage.
- [ ] **Should "NEXT" state respect ordinal or GPS proximity?** — Proposed direction: ordinal (first uncollected by order) for the deník; proximity-based "next" can be layered in the map FR without schema change.
- [ ] **Where does the temporary dev seed (a sample Trasa + controls) live until FR-11?** — Proposed direction: a small `commonTest` fixture and an optional debug-only seed calling `RouteRepository.upsertRoute`; do not ship a hardcoded route in production code.

### 12.3 Suggestions & Follow-ups

- Add a **JVM SQLite driver** (`app.cash.sqldelight:sqlite-driver`) in `androidHostTest` to add real DB integration tests (schema + queries) beyond the fake-repo unit tests — good coverage, out of scope here.
- When **FR-11** lands, add `data/dto` + Ktor + a JSON→`upsertRoute` mapper; the repository `upsert*` hooks are already the seam.
- When **Etapa 2 sync** lands, add a `syncQueries` selecting rows `WHERE syncStatus = 'PENDING'` and a background uploader; no schema change needed beyond flipping statuses to `SYNCED`.
- Consider **detekt/ktlint** (stack §6/§7 "cíl") before the codebase grows.
- Add a Koin **`checkModules()`** test to catch DI graph breakage early.
- Revisit **monotonic timing** for splits in Etapa 2 (guard against wall-clock jumps during a multi-hour run).

> Sections 9 (Design Reference) and 10 (Corrections From Current State) intentionally omitted: this is logic-only greenfield work with no UI and no prior FR-02 implementation to correct.
