# FR-01 App Skeleton — Navigation Contract & App-Level State (Logic Only)

> **Summary**: Establish the shared, unit-testable *logic* skeleton of TA33 — a serializable `Destination` navigation contract, an `AppViewModel` exposing readiness + active-route app state, and a pure `StartDestinationResolver` that decides the initial gate — while the back-stack stays owned by each native UI (Compose Navigation / SwiftUI NavigationStack), with no screens or visuals built.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
TA33 needs an application skeleton: a defined set of screens (Deník, Mapa, Přehled/Profil), navigation between them, and a startup path that works on both iOS and Android — including an "offline ready" gate before a participant can use the app on the trail. Right now the only shared code is a demo `Greeting*` scaffold; there is no notion of *what destinations exist*, *what state the app is in at launch*, or *where the app should start* (preparation flow vs. main tabs vs. resuming an in-progress run).

### 1.2 Solution Overview
Build the skeleton as **shared logic only**: a serializable `Destination` sealed hierarchy (the navigation *contract* — what destinations exist and what typed arguments they carry), a headless `AppViewModel` that composes the FR-02 repositories into one `StateFlow<AppUiState>` (readiness + active route + resolved start destination), and a pure `StartDestinationResolver`. The **native layers keep owning their own back-stack** (Compose Navigation, SwiftUI NavigationStack) and merely consume the shared contract + app state to pick their start destination and gate. No UI, no visuals, no Decompose.

### 1.3 Scope: What This IS
- A shared **navigation contract**: `Destination` (serializable sealed interface) with typed argument value types + a `TopLevelDestination` enum for the 3 tabs (Deník / Mapa / Přehled).
- A shared **app-state model**: `AppReadiness`, `ContentAvailability`, `AppUiState`.
- A headless **`AppViewModel`** (`StateFlow<AppUiState>`) composing the FR-02 `RouteRepository`, `RunRepository`, and `EnsureLocalParticipantUseCase`.
- A pure **`StartDestinationResolver`** + pure **`AppStateReducer`** (the source-of-truth logic for start destination / offline-ready gate).
- **Startup-sequence contract**: exact ordering for Android (`Ta33Application`/`MainActivity`) and iOS (`iOSApp.swift`), plus a `ViewModelProvider.appViewModel()` accessor for Swift (mirroring the existing `greetingViewModel()`).
- **Native consumption contract** (prose + mapping seam) describing how Compose typed routes and a SwiftUI enum-driven `NavigationStack` map to the shared `Destination`.
- **Koin registration** of the new resolver + ViewModel in `di/AppModule.kt`.
- **Unit tests** in `commonTest` for the resolver, the reducer, `Destination` argument round-trip/serialization, and `AppViewModel` state emission with fake FR-02 repositories.
- One **minimal, coordinated additive seam on FR-02**: expose the already-planned `selectActiveRun` query as `RunRepository.observeActiveRun()`.

### 1.4 Scope: What This IS NOT
- **No UI whatsoever** — no Compose/SwiftUI screens, no `NavHost`/`NavigationStack` code, no bottom bar, no theming, no visuals, no "look per prototype". UI is a deliberately deferred later phase.
- **No shared back-stack / no Decompose** — the shared core never owns navigation history. Decompose is explicitly deferred per project-stack §3.
- **No redefinition of FR-02** — models (`Participant`, `Route`, `ControlPoint`, `RunSession`, `CollectedControl`, `SyncStatus`, `RunLogEntry`, `RunProgress`, `GeoPoint`) and repositories are **referenced**, not duplicated. The single additive change (`observeActiveRun`) is explicitly flagged.
- **No offline-package download / no Ktor / no networking** — that is FR-11 / Etapa 2. Readiness is *derived from local content presence* now; FR-11 will own the real download and a persisted flag.
- **No platform GPS / camera / QR** — only the *destinations* that later feed those flows are modeled at contract level.
- **No new Gradle modules** — everything lives in the existing `:shared` module as package layers.

---

## 2. SUCCESS CRITERIA

Implementation is COMPLETE when ALL criteria are met:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | Project compiles with the new contract, state model, resolver, reducer and ViewModel | `./gradlew build` succeeds |
| 2 | `Destination` is a serializable sealed hierarchy covering the 3 tabs + preparation gate + the referenced later flows (route detail, run active, start/finish scan, control collected) | Types compile; serialization test passes |
| 3 | Each `Destination` with arguments survives a serialize → deserialize round-trip unchanged | Unit test `DestinationSerializationTest` (kotlinx.serialization `Json`) |
| 4 | `StartDestinationResolver` returns `Preparation` when not ready, `RunActive(runId)` when a run is active, `Main(DENIK)` when ready with no active run, and `null` while loading | Unit test `StartDestinationResolverTest` (all readiness permutations) |
| 5 | `AppViewModel` emits `NOT_READY` + `Preparation` start when no content; `READY` + `Main(DENIK)` + single `activeRouteId` when content but no run; `READY` + `RunActive(runId)` + run's `routeId` when a run is active | Unit test `AppViewModelTest` with fake FR-02 repos |
| 6 | Active route is derived: `activeRun.routeId` if a run is active, else the single available route, else `null` | Assertion in `AppStateReducerTest` / `AppViewModelTest` |
| 7 | `AppViewModel` + `StartDestinationResolver` are resolvable via Koin | Koin resolution (`checkModules`/resolution test or app launch) |
| 8 | Swift can obtain the ViewModel via `ViewModelProvider.appViewModel()` | `ViewModelProvider` accessor compiles into the `Shared` framework; iOS build succeeds |
| 9 | Shared module contains **zero** UI / navigation-library dependencies (contract only) | No `navigation-compose` / Compose UI import in `:shared`; `./gradlew :shared:build` |
| 10 | `RunRepository.observeActiveRun()` returns the open run (or `null`) backed by the FR-02 `selectActiveRun` query | Unit test via fake + note real query wiring in FR-02 impl |
| 11 | `./gradlew :shared:allTests` green | Run tests |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
   NATIVE UI (later phase — NOT built here)
   ┌────────────────────────────┐        ┌────────────────────────────┐
   │ Android: Compose Navigation │        │ iOS: SwiftUI NavigationStack │
   │  owns its own back-stack    │        │  owns its own back-stack     │
   │  typed routes = @Serializable│        │  switch over SKIE sealed enum│
   │  Destination                │        │  = Destination               │
   └──────────────┬─────────────┘        └──────────────┬──────────────┘
                  │ reads start destination + gating      │
                  │ (does NOT push nav state down)         │
                  ▼                                        ▼
   ══════════════════════ SHARED CORE (:shared, commonMain) ══════════════════════
                         presentation/
                    ┌───────────────────────────┐
                    │        AppViewModel        │  StateFlow<AppUiState>
                    │       (headless, no UI)     │  (readiness, activeRouteId,
                    └─────────────┬──────────────┘   activeRunId, startDestination?)
                                  │ combine()
        ┌─────────────────────────┼─────────────────────────────┐
        │                         │                               │
  StartDestinationResolver   AppStateReducer            presentation/navigation/
  (pure: readiness+run →      (pure: routes+run →         Destination (sealed, @Serializable)
   Destination?)              AppUiState)                 TopLevelDestination (enum)
                                  │                        AppReadiness / ContentAvailability
                                  │ consumes (FR-02, referenced — NOT redefined)
        ┌─────────────────────────┼─────────────────────────────┐
   RouteRepository          RunRepository                EnsureLocalParticipantUseCase
   .observeRoutes()         .observeActiveRun()  ← FR-02 seam    (seeds anonymous participant)
                            (backed by selectActiveRun)
```

**Startup + data flow:** Koin already initialized (Android `Ta33Application`, iOS `iOSApp.swift`) → native layer resolves `AppViewModel` (Android `koinViewModel()`, iOS `ViewModelProvider.appViewModel()`) → VM `init` fires `EnsureLocalParticipantUseCase` (anonymous participant, for later run creation) and `combine(observeRoutes, observeActiveRun)` → `AppStateReducer.reduce(...)` derives readiness + active route → `StartDestinationResolver.resolve(...)` sets `startDestination` → native layer reads `AppUiState.startDestination` (shows a splash while it is `null`/`LOADING`) and picks its own start destination.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| **Where navigation state lives** | **Native (per-platform) owns the back-stack; shared owns only the *contract* + app state** | project-stack §3 mandates native navigation per platform for v1 (Compose Navigation / SwiftUI NavigationStack). Duplicating a back-stack in shared would fight the native frameworks. Sharing only the `Destination` contract + start-destination logic keeps the win (single source of truth for *what exists* and *where to start*, unit-testable) without the cost (Decompose complexity, §3 defers it). |
| `Destination` representation | **Serializable sealed interface** + typed arg `data class`es; `TopLevelDestination` enum for tabs | `@Serializable` makes Compose Navigation 2.8+ typed routes work directly (no string mapping) and gives a testable round-trip; SKIE turns the sealed interface into a Swift enum the SwiftUI layer can `switch` over exhaustively. Enum for the 3 fixed tabs is the natural SKIE-friendly shape. |
| Start-destination logic | **Pure `StartDestinationResolver` + pure `AppStateReducer`** (no coroutines, no repos) | Fully deterministic and unit-testable in `commonTest` without a DB or `Main` dispatcher; the VM is a thin wiring layer over them. |
| Readiness source | **Derived from local content presence** (`RouteRepository.observeRoutes()` non-empty) for now | FR-11 owns the actual offline-package download + an explicit persisted flag. Deriving from content avoids duplicating FR-02 persistence and gives a correct gate today; `AppReadiness.PREPARING` is reserved for FR-11 to drive. |
| Active-run access | **Add `RunRepository.observeActiveRun(): Flow<RunSession?>`** (one additive seam on FR-02) | FR-02 already *plans* the `selectActiveRun` SQL query but does not expose it on the interface. Exposing it is additive (not a redefinition) and is the minimal touch FR-01 needs. |
| Active route derivation | `activeRun?.routeId` → else single available route → else `null` | Matches FR-02 "one active run at a time"; leaves multi-route selection (a UI/later concern) cleanly `null`. |
| `startDestination` while loading | **Nullable; `null` until the first repo emission** | Avoids inventing a fake "Splash" destination; native shows a splash while `null` and navigates once resolved. |
| Package placement | New `presentation/navigation/` package inside `:shared` | Matches project-stack §12 "layers as packages, not Gradle modules yet"; navigation contract is a presentation-layer concern consumed by native UI. |
| Participant at startup | Fire `EnsureLocalParticipantUseCase` (fire-and-forget) — **does not gate navigation** | Etapa 1 is anonymous; the participant row is only needed later for run creation. Keeps the readiness gate purely about offline content. |

---

## 4. IMPLEMENTATION STEPS

> Execute steps in order. Do not skip. Paths under `shared/src/commonMain/kotlin/com/example/ta33/` unless stated. Base package `com.example.ta33`.
> **Dependency ordering:** FR-01 assumes **FR-02 is implemented first** (its models, repositories, use-cases, and the `Ta33Database` schema). FR-01 references those directly.

### Step 0 (pre-flight): Confirm dependencies
**Goal**: Ensure the two test/serialization dependencies exist.
**Files**: `gradle/libs.versions.toml`, `shared/build.gradle.kts`

- `kotlinx-coroutines-test` — **already added by FR-02 (its Step 1)**. If FR-02 not yet merged, add it to `commonTest` as FR-02 specifies. Reused here for `runTest`.
- `kotlinx-serialization-json` — required at runtime for the `Destination` serialization round-trip test (the `@Serializable` *plugin* is already applied per project-stack §12; the JSON *runtime* may only be present transitively via Ktor). Add an explicit alias if absent:
```toml
# [libraries]
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
```
and in `shared/build.gradle.kts` `commonMain.dependencies { implementation(libs.kotlinx.serialization.json) }` (or `commonTest` if only the test needs it — prefer `commonMain` so the contract's serializability is a first-class guarantee).

**Done when**: `./gradlew :shared:build` resolves without error.

---

### Step 1: Add the navigation contract — `Destination` + `TopLevelDestination`
**Goal**: Enumerate *what destinations exist* and *what typed args they carry*, serializable so it crosses the native boundary cleanly.
**Files**: `presentation/navigation/Destination.kt`

```kotlin
package com.example.ta33.presentation.navigation

import kotlinx.serialization.Serializable

/** The three top-level tabs. Stable order (bottom-nav order). SKIE exposes this as a Swift enum. */
enum class TopLevelDestination { DENIK, MAPA, PREHLED }

/**
 * Shared navigation CONTRACT — the single source of truth for which destinations exist
 * and which typed arguments each carries. It does NOT model a back-stack: the native
 * layers (Compose Navigation / SwiftUI NavigationStack) own navigation history (stack §3).
 *
 * @Serializable enables Compose Navigation 2.8+ typed routes directly and a serialization
 * round-trip guarantee; SKIE turns this sealed interface into a Swift enum for exhaustive `switch`.
 */
@Serializable
sealed interface Destination {

    /** Offline-readiness / preparation gate (FR-11 flow — screen built later). */
    @Serializable
    data object Preparation : Destination

    /** The main tabbed shell; carries the initially selected tab. */
    @Serializable
    data class Main(val tab: TopLevelDestination = TopLevelDestination.DENIK) : Destination

    /** Detail of a route (FR-03) — referenced by the contract; screen built later. */
    @Serializable
    data class RouteDetail(val routeId: String) : Destination

    /** Resume / view an in-progress run — used at startup when a run is active. */
    @Serializable
    data class RunActive(val runId: String) : Destination

    /** QR start-scan gate (FR-09) — referenced only. `runId` null when starting fresh. */
    @Serializable
    data class StartScan(val runId: String? = null) : Destination

    /** QR finish-scan gate (FR-09) — referenced only. */
    @Serializable
    data class FinishScan(val runId: String) : Destination

    /** "Control collected" confirmation (FR-08) — referenced only. */
    @Serializable
    data class ControlCollected(val runId: String, val controlId: String) : Destination
}
```
> The later-flow destinations (`RouteDetail`, `StartScan`, `FinishScan`, `ControlCollected`) are included **now** so their screens (later FRs) slot in without breaking the contract. Only `Preparation`, `Main`, and `RunActive` are actually produced by FR-01's resolver.

**Done when**: File compiles; all variants are `@Serializable`.

---

### Step 2: Add the app-state model — `AppReadiness`, `ContentAvailability`, `AppUiState`
**Goal**: The observable app-level state that is the source of truth for the start gate.
**Files**: `presentation/navigation/AppReadiness.kt`, `presentation/navigation/AppUiState.kt`

```kotlin
// presentation/navigation/AppReadiness.kt
package com.example.ta33.presentation.navigation

/** Offline-readiness state. PREPARING is reserved for FR-11 to drive (download in progress). */
enum class AppReadiness { LOADING, NOT_READY, PREPARING, READY }

/** Whether offline content (routes/controls) is present locally. Derived from RouteRepository. */
enum class ContentAvailability { UNKNOWN, ABSENT, PRESENT }
```
```kotlin
// presentation/navigation/AppUiState.kt
package com.example.ta33.presentation.navigation

data class AppUiState(
    val readiness: AppReadiness = AppReadiness.LOADING,
    val contentAvailability: ContentAvailability = ContentAvailability.UNKNOWN,
    val activeRouteId: String? = null,
    val activeRunId: String? = null,
    /** Initial destination/gate. `null` while LOADING → native shows a splash and does not navigate. */
    val startDestination: Destination? = null,
)
```
**Done when**: Files compile.

---

### Step 3: Add the pure `StartDestinationResolver`
**Goal**: Deterministic mapping from readiness (+ active run) to the initial destination. No repos, no coroutines.
**Files**: `presentation/navigation/StartDestinationResolver.kt`

```kotlin
package com.example.ta33.presentation.navigation

class StartDestinationResolver {
    /** @return the initial destination, or null while still LOADING (native shows a splash). */
    fun resolve(readiness: AppReadiness, activeRunId: String?): Destination? = when (readiness) {
        AppReadiness.LOADING -> null
        AppReadiness.NOT_READY,
        AppReadiness.PREPARING -> Destination.Preparation
        AppReadiness.READY ->
            activeRunId?.let { Destination.RunActive(it) } ?: Destination.Main() // Main defaults to DENIK
    }
}
```
> Kept as a (dependency-free) class rather than an object so it registers in Koin and mirrors the FR-02 use-case style; it is still trivially pure-testable.

**Done when**: Compiles.

---

### Step 4: Add the pure `AppStateReducer`
**Goal**: Derive `AppUiState` from the FR-02 repository outputs. Pure → testable without coroutines/DB.
**Files**: `presentation/navigation/AppStateReducer.kt`

```kotlin
package com.example.ta33.presentation.navigation

import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RunSession

object AppStateReducer {
    fun reduce(
        routes: List<Route>,
        activeRun: RunSession?,
        resolver: StartDestinationResolver,
    ): AppUiState {
        val availability =
            if (routes.isEmpty()) ContentAvailability.ABSENT else ContentAvailability.PRESENT
        // Readiness derived from local content presence (FR-11 will drive PREPARING + a persisted flag).
        val readiness = if (routes.isEmpty()) AppReadiness.NOT_READY else AppReadiness.READY
        val activeRouteId = activeRun?.routeId ?: routes.singleOrNull()?.id
        val activeRunId = activeRun?.id
        return AppUiState(
            readiness = readiness,
            contentAvailability = availability,
            activeRouteId = activeRouteId,
            activeRunId = activeRunId,
            startDestination = resolver.resolve(readiness, activeRunId),
        )
    }
}
```
> `LOADING` is never produced here — it is only the `AppViewModel`'s initial value before the first `combine` emission.

**Done when**: Compiles.

---

### Step 5: Extend the FR-02 `RunRepository` with `observeActiveRun()` (the single coordinated seam)
**Goal**: Expose the already-planned `selectActiveRun` query so app state can detect an in-progress run.
**Files**: `domain/repository/RunRepository.kt` (FR-02), `data/repository/RunRepositoryImpl.kt` (FR-02)

Add to the interface (additive, not a redefinition):
```kotlin
fun observeActiveRun(): kotlinx.coroutines.flow.Flow<RunSession?>
suspend fun getActiveRun(): RunSession?
```
Implement in `RunRepositoryImpl` using the FR-02 `selectActiveRun` query:
```kotlin
override fun observeActiveRun(): Flow<RunSession?> =
    q.selectActiveRun().asFlow().mapToOneOrNull(Dispatchers.Default)
        .map { it?.toDomain() }

override suspend fun getActiveRun(): RunSession? = withContext(Dispatchers.Default) {
    q.selectActiveRun().executeAsOneOrNull()?.toDomain()
}
```
> If FR-02 is not yet implemented, fold this method into FR-02 instead of patching it afterward. Documented as the only FR-02 touch in Assumptions §7.

**Done when**: `RunRepository` exposes `observeActiveRun()`; impl compiles against the FR-02 `selectActiveRun` query.

---

### Step 6: Add the headless `AppViewModel`
**Goal**: Compose the FR-02 repositories into one `StateFlow<AppUiState>`; no UI consumes it here.
**Files**: `presentation/AppViewModel.kt`

```kotlin
package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.domain.repository.RouteRepository
import com.example.ta33.domain.repository.RunRepository
import com.example.ta33.domain.usecase.EnsureLocalParticipantUseCase
import com.example.ta33.presentation.navigation.AppStateReducer
import com.example.ta33.presentation.navigation.AppUiState
import com.example.ta33.presentation.navigation.StartDestinationResolver
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(
    private val routeRepository: RouteRepository,
    private val runRepository: RunRepository,
    private val ensureLocalParticipant: EnsureLocalParticipantUseCase,
    private val resolver: StartDestinationResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState())     // starts LOADING
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init { start() }

    private fun start() {
        // Seed the anonymous local participant for later run creation (does NOT gate navigation).
        viewModelScope.launch { ensureLocalParticipant() }

        combine(
            routeRepository.observeRoutes(),
            runRepository.observeActiveRun(),
        ) { routes, activeRun -> AppStateReducer.reduce(routes, activeRun, resolver) }
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }
}
```
> Mirrors the existing `GreetingViewModel` / FR-02 `RunLogViewModel` pattern (`MutableStateFlow` + `asStateFlow`, `viewModelScope`). `EnsureLocalParticipantUseCase` is invoked via its `operator fun invoke()` (FR-02). If FR-02's use-case name/signature differs, adapt the call — do not redefine it.

**Done when**: Compiles.

---

### Step 7: Register in Koin `appModule`
**Goal**: Make the resolver + ViewModel resolvable (mirrors FR-02 registration style).
**Files**: `di/AppModule.kt`

```kotlin
// navigation / app state (FR-01)
single { StartDestinationResolver() }
factory { AppViewModel(get(), get(), get(), get()) }
```
> `AppViewModel` deps resolve to the FR-02-registered `RouteRepository`, `RunRepository`, and `EnsureLocalParticipantUseCase`, plus the new `StartDestinationResolver`.

**Done when**: `./gradlew build` passes and Koin resolves (verified in Step 9 test / app launch).

---

### Step 8: Extend `ViewModelProvider` for Swift + document the startup-sequence contract
**Goal**: Let SwiftUI resolve `AppViewModel` without touching Koin, and lock the symmetric startup order.
**Files**: `di/Koin.kt`

Add to the existing `ViewModelProvider` object (mirrors `greetingViewModel()`):
```kotlin
object ViewModelProvider {
    fun greetingViewModel(): GreetingViewModel = KoinPlatform.getKoin().get()
    fun appViewModel(): AppViewModel = KoinPlatform.getKoin().get()   // FR-01
}
```

**Startup-sequence contract (both platforms — documentation, no screens built now):**

| Order | Android (`Ta33Application` / `MainActivity`) | iOS (`iOSApp.swift` / root view) |
|-------|----------------------------------------------|----------------------------------|
| 1 | `initKoin { androidContext(...) }` in `Ta33Application` (**already exists**) | `KoinKt.doInitKoin()` in `iOSApp.swift` (**already exists**) |
| 2 | Resolve `AppViewModel` via `koinViewModel()` at the root composable | Resolve via `ViewModelProvider.shared.appViewModel()` in the root view's model |
| 3 | Collect `state` with `collectAsStateWithLifecycle()` | `for await state in viewModel.state` (SKIE async sequence), publish to SwiftUI |
| 4 | VM `init` already fired `ensureLocalParticipant()` + started observing readiness | same VM, same behavior |
| 5 | While `startDestination == null` → show a splash; once set → set the `NavHost` start destination | While `startDestination == nil` → splash; once set → seed the `NavigationStack` root |

**Done when**: `di/Koin.kt` compiles into the `Shared` framework; iOS build (`xcodebuild ... build`) succeeds.

---

### Step 9: Unit tests (`commonTest`)
**Goal**: Lock the navigation/state logic. Prefer fakes over a real DB (mirrors FR-02).
**Files**: under `shared/src/commonTest/kotlin/com/example/ta33/presentation/navigation/` and `.../presentation/`

- **`StartDestinationResolverTest.kt`** — every readiness permutation:
  - `LOADING` → `null`
  - `NOT_READY` → `Preparation`
  - `PREPARING` → `Preparation`
  - `READY` + `activeRunId == null` → `Main(DENIK)`
  - `READY` + `activeRunId = "r1"` → `RunActive("r1")`
- **`AppStateReducerTest.kt`** — pure:
  - empty routes → `NOT_READY`, `ABSENT`, `startDestination = Preparation`, `activeRouteId = null`
  - one route, no active run → `READY`, `PRESENT`, `Main(DENIK)`, `activeRouteId = that route`
  - two routes, no active run → `READY`, `activeRouteId = null` (ambiguous, selection deferred)
  - active run → `RunActive(runId)`, `activeRouteId = run.routeId`
- **`DestinationSerializationTest.kt`** — `Json.encodeToString`/`decodeFromString` round-trip for `Main(MAPA)`, `RouteDetail("x")`, `RunActive("r")`, `ControlCollected("r","c")`; assert equality (proves typed args cross the native boundary intact).
- **`AppViewModelTest.kt`** — fakes `FakeRouteRepository` (backed by `MutableStateFlow<List<Route>>`), `FakeRunRepository` (`MutableStateFlow<RunSession?>`), `FakeEnsureLocalParticipantUseCase`; use `runTest` + `Dispatchers.setMain(StandardTestDispatcher())` in `@BeforeTest` / `Dispatchers.resetMain()` in `@AfterTest` (viewModelScope uses `Main`):
  - no content → state becomes `NOT_READY` / `Preparation`
  - push a route → state becomes `READY` / `Main(DENIK)` / `activeRouteId` set
  - push an active run → `RunActive(runId)` (resume)
  - assert `ensureLocalParticipant` was invoked once.

**Done when**: `./gradlew :shared:allTests` is green.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| No offline content (routes empty) | Gate at preparation | `reduce` → `NOT_READY` → `Preparation` |
| Content present, no active run (cold start) | Land on main tabs | `READY` → `Main(DENIK)` |
| Active run present (resume after app kill) | Resume the run | `observeActiveRun` non-null → `RunActive(runId)` |
| Multiple routes, no active run | No auto-selected route | `activeRouteId = routes.singleOrNull()?.id` → `null`; route selection is a later UI concern |
| Stale/corrupt active run whose `routeId` is missing from content | Still resume; `activeRouteId = run.routeId` (route detail handled by later FR) | Reducer does not cross-validate; detail lookups degrade gracefully in later FRs |
| Content wiped but a run row survives | Re-gate to preparation | Readiness derived purely from routes presence → `NOT_READY` even if a stale run exists (must re-prepare) |
| Loading flicker at launch | No premature navigation | `startDestination == null` while `LOADING`; native shows a splash |
| FR-11 finishes downloading mid-session (content appears) | State re-emits `READY` | `AppUiState` is a live `StateFlow`; **the FR-11 flow itself navigates on completion** — `startDestination` is only the *initial* gate, not a runtime redirect signal |
| Participant creation fails/slow | Navigation unaffected | `ensureLocalParticipant` is fire-and-forget and does not gate readiness (Etapa 1 anonymous) |
| `commonTest` has no `Main` dispatcher | Tests still run | `Dispatchers.setMain(StandardTestDispatcher())` around VM tests; pure resolver/reducer tests need no dispatcher |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: No external/user input in this layer — destinations carry app-generated ids (route/run/control ids from FR-02 UUIDs). `Destination` args are opaque strings/enums.
- **Auth/Access control**: None in Etapa 1 (anonymous local participant, no login — Etapa 2 adds auth per stack §4). No token stored here.
- **Sensitive data**: `AppUiState` holds only ids and readiness — no coordinates, no PII. All state stays on-device (no network in FR-01).
- **Logging**: Keep any startup logging at debug via Napier (existing scaffold). Do not log participant ids or run ids at info level; never log to persistent files.

---

## 7. ASSUMPTIONS

> User opted out of clarification (`skip questions` / `proceed directly`). Accepted defaults recorded here.

1. **FR-02 is implemented before FR-01** — its models, repositories, use-cases, and `Ta33Database` schema exist and are referenced (not duplicated). If wrong: FR-01 cannot compile; implement FR-02 first.
2. **The one FR-02 touch is additive**: exposing `RunRepository.observeActiveRun()`/`getActiveRun()` over the already-planned `selectActiveRun` query. If FR-02 is unmerged, this folds into FR-02 rather than patching it. Impact if wrong: minor coordination, no model change.
3. **Readiness is derived from local content presence** (routes non-empty) as the Etapa 1 proxy for "offline ready". FR-11 will own the real download and an explicit persisted flag; `AppReadiness.PREPARING` is reserved for it. Impact if wrong: readiness rule swaps to reading the persisted flag — a localized change in `AppStateReducer`.
4. **One active run at a time** (per FR-02) — `observeActiveRun()` returns a single run or null.
5. **Anonymous participant is ensured at startup and does not gate navigation** (Etapa 1). Impact if wrong (login required at launch): add a participant/auth branch to readiness — additive.
6. **Native mapping mechanisms**: Compose Navigation 2.8+ typed routes consume the `@Serializable Destination` directly; SwiftUI switches over the SKIE-generated sealed enum. No shared route-string mapping is created (keeps `:shared` UI-free). Impact if the Android nav version predates typed routes: add a thin `Destination → route` mapper in `androidApp` (UI phase), not in `:shared`.
7. **`startDestination` is the initial gate only**; runtime transitions (including FR-11 completing) are driven by the native back-stack / the FR-11 flow, not by re-reading `startDestination`.
8. **`kotlinx-serialization-json` runtime is available** (plugin already applied per stack §12); added explicitly if only present transitively.
9. **Multi-route selection UI is deferred** — `activeRouteId` is `null` when ambiguous; no auto-pick.
10. **The demo `Greeting*` scaffold stays untouched**; `AppViewModel` becomes the real app entry state, but no screens are built.

> Open questions live in Section 12.

---

## 8. QUICK REFERENCE

### Files to Modify
- `gradle/libs.versions.toml` — add `kotlinx-serialization-json` alias if absent (coroutines-test comes from FR-02).
- `shared/build.gradle.kts` — ensure `kotlinx.serialization.json` on `commonMain` (or `commonTest`).
- `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt` — register `StartDestinationResolver` + `AppViewModel`.
- `shared/src/commonMain/kotlin/com/example/ta33/di/Koin.kt` — add `ViewModelProvider.appViewModel()`.
- `shared/src/commonMain/kotlin/com/example/ta33/domain/repository/RunRepository.kt` (FR-02) — add `observeActiveRun()`/`getActiveRun()`.
- `shared/src/commonMain/kotlin/com/example/ta33/data/repository/RunRepositoryImpl.kt` (FR-02) — implement them via `selectActiveRun`.

### Files to Create
- `presentation/navigation/Destination.kt` — `Destination` sealed contract + `TopLevelDestination`.
- `presentation/navigation/AppReadiness.kt` — `AppReadiness`, `ContentAvailability`.
- `presentation/navigation/AppUiState.kt` — app-level UI state.
- `presentation/navigation/StartDestinationResolver.kt` — pure start-destination logic.
- `presentation/navigation/AppStateReducer.kt` — pure repo-output → `AppUiState`.
- `presentation/AppViewModel.kt` — headless `StateFlow<AppUiState>`.
- `commonTest/.../presentation/navigation/StartDestinationResolverTest.kt`
- `commonTest/.../presentation/navigation/AppStateReducerTest.kt`
- `commonTest/.../presentation/navigation/DestinationSerializationTest.kt`
- `commonTest/.../presentation/AppViewModelTest.kt` (+ fakes)

### Dependencies
- `org.jetbrains.kotlinx:kotlinx-serialization-json` — `Destination` serialization + round-trip test (add if not already resolvable).
- (Already present: kotlinx.serialization plugin, coroutines-core, lifecycle-viewmodel, Koin, SKIE; `kotlinx-coroutines-test` via FR-02.)

### Commands
```bash
# Build + verify shared logic
./gradlew build

# Run shared unit tests
./gradlew :shared:allTests

# Android debug sanity build
./gradlew :androidApp:assembleDebug

# iOS headless build (framework Shared compiles ViewModelProvider.appViewModel)
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
| **A. Shared navigation *contract* + app state; native owns the back-stack** | One source of truth for *what exists* + *where to start*; unit-testable in `commonMain`; zero UI in shared; matches stack §3 native-nav-per-platform | Native layers must each map `Destination` → their nav (small, idiomatic) | ✅ |
| B. **Decompose** shared component tree owning the back-stack | Maximal navigation sharing across platforms | Explicitly deferred by stack §3 ("not v1"); heavier; fights native SwiftUI/Compose nav; larger learning/integration cost | — |
| C. **No shared contract** — each platform defines its own destinations, args and start logic | Zero shared-code coupling | Duplicated + drifting destination sets and start logic; args diverge; the start/readiness rule can't be unit-tested once; contradicts "shared core" goal | — |
| D. Shared `NavController`-like state holder (shared *current destination*, not full stack) | Some runtime sync | Still fights native back-stacks; blurs ownership; not needed for v1's start-gate requirement | — |

**Why the selected approach won**: It delivers the real value of a shared skeleton — a single, testable definition of destinations + the start/readiness gate — while honoring project-stack §3's decision that v1 navigation is native per platform and Decompose is deferred. Shared stays UI-free; each native layer keeps idiomatic navigation.

### 12.2 Open Questions

- [ ] **Where will FR-11 persist the explicit "offline ready" flag?** — Proposed direction: FR-11 adds a small settings/preparation row (SQLDelight) and flips readiness to a persisted `Ready`; FR-01 derives from content presence until then (localized change in `AppStateReducer`).
- [ ] **Should the later-flow destinations (`StartScan`/`FinishScan`/`ControlCollected`) be modeled now or when their FRs land?** — Proposed direction: keep them now as contract stubs (done) so later FRs slot in without breaking the sealed hierarchy; revisit arg shapes when those FRs are planned.
- [ ] **How should the app react when readiness flips to `READY` at runtime (FR-11 download completes)?** — Proposed direction: the FR-11 preparation flow navigates on completion via the native back-stack; `startDestination` remains the initial gate only (documented in Edge Cases). Confirm when FR-11 is planned.
- [ ] **Is the installed Navigation-Compose version ≥ 2.8 (typed routes)?** — Proposed direction: verify in `androidApp` during the UI phase; if older, add a `Destination → route` mapper in `androidApp` only (never in `:shared`).

### 12.3 Suggestions & Follow-ups

- Add a Koin **`checkModules()`** test to catch DI-graph breakage early (also useful for FR-02).
- When **FR-11** lands: drive `AppReadiness.PREPARING`, add the persisted readiness flag, and wire the preparation → main-tabs transition.
- Consider **Turbine** for `StateFlow` assertions in `AppViewModelTest` (nicer than manual collection) — optional test-only dependency.
- Keep the `Destination` sealed hierarchy the **single** place destinations are added — when a later FR needs a new screen, add a variant here first, then map it in both native layers (symmetry check).
- Consider a lightweight **`NavigationEvent` channel** in shared *only if* cross-platform deep-linking is later required — still not a back-stack, and out of scope for v1.

> Sections 9 (Design Reference) and 10 (Corrections From Current State) intentionally omitted: this is logic-only work with no UI/visual spec, and it is greenfield (no prior FR-01 implementation to correct — the `Greeting*` scaffold is left untouched).
