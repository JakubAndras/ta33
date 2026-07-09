# FR-03 Route (Trasa) — Summary, Detail & Active-Route Selection (Logic Only)

> **Summary**: Add the shared, headless *logic* for browsing routes — a route list with a per-route summary (name · distance · control count), a route detail (route metadata + controls ordered by `ordinal`), and a persisted "selected active route" — as use-cases + `StateFlow` ViewModels over the existing FR-02 repositories, with no UI built.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
A TA33 participant needs to see the available route(s) and open the detail of one — e.g. "Trasa A · 33 km · 5 kontrol" with its list of control points — and to pick which route is *their* active route (the one the Overview/FR-10 and navigation revolve around). Today FR-02 stores routes and controls and FR-01 *derives* an active route heuristically, but nothing reads a route's control count for a list, exposes an ordered detail reactively, or *remembers* an explicit route choice across restarts.

### 1.2 Solution Overview
Add a thin FR-03 presentation + domain layer on top of the FR-02 data foundation: two read models (`RouteSummary`, `RouteDetail`), three use-cases (`ObserveRoutes`, `ObserveRouteDetail`, `SelectActiveRoute`) plus a small pure resolver and a selection observer, and two headless `StateFlow` ViewModels (`RouteListViewModel`, `RouteDetailViewModel`). Route length is the **stored** `distanceKm` value from the content JSON (FR-11), never computed. Four clearly-flagged additive seams supply what FR-02/FR-01 don't yet: a JOIN+COUNT summary query, a reactive detail read, a new persisted `AppPreferences.selectedRouteId`, and a coordinated tweak so FR-01's active-route derivation prefers the persisted selection.

### 1.3 Scope: What This IS
- **Read models** (`domain/model`): `RouteSummary` (routeId, name, distanceKm, controlCount) and `RouteDetail` (id, name, distanceKm, controls sorted by `ordinal`).
- **Pure helper** (`domain/route`): `ActiveRouteResolver` — deterministic effective-route rule shared by FR-03 and the FR-01 seam.
- **Use-cases** (`domain/usecase`): `ObserveRoutesUseCase`, `ObserveRouteDetailUseCase`, `ObserveSelectedRouteUseCase`, `SelectActiveRouteUseCase`.
- **Headless ViewModels** (`presentation`): `RouteListViewModel` + `RouteDetailViewModel` (each a `StateFlow<…UiState>`); no UI consumes them yet.
- **Seam A (FR-02, additive)**: `RouteRepository.observeRouteSummaries(): Flow<List<RouteSummary>>` backed by a new `selectRouteSummaries` JOIN+COUNT query.
- **Seam B (FR-02, additive)**: `RouteRepository.observeRouteWithControls(routeId): Flow<Route?>` (reactive detail; reuses existing `selectRouteById` + `selectControlsForRoute` queries).
- **Seam C (FR-03-owned, new)**: `AppPreferencesRepository` + single-row `AppPreferences` SQLDelight table persisting `selectedRouteId`.
- **Seam D (FR-01, coordinated)**: `AppStateReducer` / `AppViewModel` read the persisted selection so `activeRouteId` prefers it (via `ActiveRouteResolver`).
- **Koin registration** + Swift `ViewModelProvider` accessors.
- **Unit tests** (`commonTest`) with fakes over the repositories.

### 1.4 Scope: What This IS NOT
- **No UI whatsoever** — no Compose/SwiftUI screens, no lists, no detail layout, no theming, no visuals, no string/plural formatting of "Trasa A · 33 km · 5 kontrol" (that label + Czech pluralization is a UI/localization concern, deferred). The read models expose **raw** fields only.
- **No map render (FR-06)** and **no mapy.cz link (FR-07)** — those consume the route/controls later; FR-03 only loads and shapes the data.
- **No deník/log logic (FR-04)** — FR-03 provides the ordered controls seam FR-04 builds on, but computes no collection state, progress, or split times.
- **No redefinition of FR-02** — `Route`, `ControlPoint`, `GeoPoint`, `RouteRepository`, `Ta33Database`, `TimeProvider` are **referenced**, not duplicated. Seams A/B are additive methods; content is still populated by FR-11 via `upsertRoute`.
- **No distance computation** — the official `distanceKm` from content is used as-is (FR-02 stores no route polyline to compute from anyway).
- **No new Gradle modules** — everything lives in `:shared` as package layers (project-stack §12).

---

## 2. SUCCESS CRITERIA

Implementation is COMPLETE when ALL criteria are met:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | Project compiles with the new models, resolver, repository seams, use-cases and ViewModels | `./gradlew build` succeeds; SQLDelight regenerates with `AppPreferences` + `selectRouteSummaries` |
| 2 | `observeRouteSummaries()` emits one `RouteSummary` per route with the correct `controlCount` (incl. 0 for a route with no controls) | `RouteSummaryQueryTest` / repo test with fake DB rows or `RouteRepositoryImpl` fake |
| 3 | `ObserveRouteDetailUseCase(routeId)` emits a `RouteDetail` whose `controls` are ordered by `ordinal`, and `null` for an unknown route | `ObserveRouteDetailUseCaseTest` (fake repo) |
| 4 | Route distance in both models is the **stored** `distanceKm` (never recomputed) | Assertion in model/use-case tests; no geo math in FR-03 detail path |
| 5 | `SelectActiveRouteUseCase(routeId)` persists the selection for a known route (`Selected`) and rejects an unknown route (`UnknownRoute`, nothing written) | `SelectActiveRouteUseCaseTest` (fake repos) |
| 6 | The selection survives restart (persisted on `Ta33Database`) and is observable | `AppPreferencesRepository` test / `ObserveSelectedRouteUseCaseTest` |
| 7 | A persisted selection pointing to a no-longer-available route is ignored (not surfaced as selected/active) | `ActiveRouteResolverTest` + `ObserveSelectedRouteUseCaseTest` |
| 8 | FR-01 `activeRouteId` prefers `activeRun.routeId`, else the validated persisted selection, else the sole route, else `null` | `ActiveRouteResolverTest` + updated `AppStateReducerTest`/`AppViewModelTest` |
| 9 | `RouteListViewModel` exposes summaries + the selected route id and `selectRoute(id)` persists it; `RouteDetailViewModel.bind(id)` emits detail / `notFound` and `makeActive()` persists | `RouteListViewModelTest`, `RouteDetailViewModelTest` (`runTest` + `Dispatchers.setMain`) |
| 10 | All new components resolvable via Koin; Swift accessors compile into `Shared` | Koin resolution test / app launch; iOS `xcodebuild` |
| 11 | `./gradlew :shared:allTests` green | Run tests |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
        NATIVE UI (later phase — NOT built here)
        reads RouteListUiState / RouteDetailUiState, sends selectRoute()/makeActive()
   ══════════════════ SHARED CORE (:shared, commonMain) ══════════════════
                     presentation/
      ┌────────────────────────────┐   ┌────────────────────────────┐
      │      RouteListViewModel     │   │     RouteDetailViewModel     │
      │  StateFlow<RouteListUiState>│   │ StateFlow<RouteDetailUiState>│
      └──────────────┬─────────────┘   └───────────────┬─────────────┘
                     │ use-cases                        │
        domain/usecase/                                 │
   ┌───────────────┬────────────────┬──────────────────┴──────┬───────────────────┐
   │ ObserveRoutes │ ObserveSelected│ ObserveRouteDetail       │ SelectActiveRoute  │
   │ (summaries)   │ Route (marker) │ (routeId → RouteDetail?) │ (persist choice)   │
   └───────┬───────┴───────┬────────┴────────────┬────────────┴─────────┬──────────┘
           │               │  domain/route/       │                      │
           │               │  ActiveRouteResolver │                      │
           │               │  (PURE)              │                      │
     domain/repository/    │                      │                      │
   ┌──────────────────────────────────────────────┴──────────┐  ┌────────────────────────┐
   │ RouteRepository  (FR-02, +Seam A observeRouteSummaries,  │  │ AppPreferencesRepository│
   │                   +Seam B observeRouteWithControls)      │  │ (Seam C, FR-03-owned)   │
   └───────────────────────────┬──────────────────────────────┘  └───────────┬────────────┘
     data/repository (SQLDelight)                                              │
   RouteRepositoryImpl: selectRouteSummaries (JOIN+COUNT),          AppPreferencesRepositoryImpl:
   selectRouteById + selectControlsForRoute (reactive combine)      AppPreferences.sq (single row)
                              │                                                 │
                              └──────────────► Ta33Database ◄───────────────────┘

   Seam D (coordinated): FR-01 AppStateReducer/AppViewModel add observeSelectedRouteId()
                          → activeRouteId = ActiveRouteResolver.resolve(run, selection, available)
```

**Data flow (open detail + make active):** native layer resolves `RouteDetailViewModel`, calls `bind(routeId)` → VM `combine(ObserveRouteDetail(routeId), ObserveSelectedRoute())` → `ObserveRouteDetail` maps `RouteRepository.observeRouteWithControls(routeId)` (Route aggregate) into a `RouteDetail` with controls sorted by `ordinal` → state emits. User taps "make active" → `SelectActiveRoute(routeId)` validates via FR-02 `getRouteWithControls` then `AppPreferencesRepository.setSelectedRouteId(routeId)` → the persisted `selectedRouteId` re-emits → FR-01 `AppViewModel` recomputes `activeRouteId` through `ActiveRouteResolver`.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Where code lives | Existing `:shared`, new/reused **package layers** (`domain/model`, `domain/route`, `domain/usecase`, `domain/repository`, `data/repository`, `presentation`) + SQLDelight | project-stack §12 "layers as packages"; no duplication |
| Route length | **Stored `distanceKm`** from content JSON (FR-11), read straight through | Organizer supplies the official distance; FR-02 stores only control *points* (no polyline), so computing would be both unnecessary and inaccurate |
| List control count | **Seam A**: `selectRouteSummaries` = `LEFT JOIN … GROUP BY … COUNT` → `RouteRepository.observeRouteSummaries()` | `observeRoutes()` returns metadata-only routes (empty `controls`), so it can't show "5 kontrol"; one reactive JOIN avoids N+1 count queries |
| Detail reactivity | **Seam B**: reactive `observeRouteWithControls(routeId): Flow<Route?>` (reuses existing `selectRouteById` + `selectControlsForRoute`) | Consistent with the Flow-based offline-first core; auto-updates if FR-11 re-downloads content while a detail is observed. One-shot `getRouteWithControls` kept for validation (§12.1) |
| Persisted selection | **Seam C**: new single-row `AppPreferences` table + `AppPreferencesRepository` (FR-03-owned) | Mirrors FR-11's single-row `Preparation` pattern; SQLDelight-native + reactive; extensible home for later prefs (FR-10 notifications) — avoids a new `multiplatform-settings` dependency and avoids coupling to FR-11's table |
| Effective active route | **Pure `ActiveRouteResolver`** shared by FR-03 + FR-01 | One tested rule (`run ?: validated-selection ?: sole-route ?: null`); prevents FR-01 and FR-03 from drifting apart |
| Selection vs. app active route | FR-03 **persists the user's selection**; FR-01 owns the **run-aware effective** `activeRouteId` (via the shared resolver) | Keeps FR-03 free of `RunRepository` coupling; FR-01 stays the single source for navigation/overview |
| Label / pluralization | **Not in domain** — models expose raw `name`/`distanceKm`/`controlCount` | "· 33 km · 5 kontrol" needs Czech plurals + units, a UI/localization (Compose resources) concern; deferred with the rest of UI |
| `SelectActiveRoute` validation | Reuse FR-02 **`getRouteWithControls`** to reject unknown ids (no new seam) | Prevents persisting a dangling selection at the source |
| ViewModel style | `MutableStateFlow` + `asStateFlow`, `viewModelScope`, `bind(routeId)` for detail | Mirrors FR-02 `RunLogViewModel` / FR-01 `AppViewModel` |

---

## 4. IMPLEMENTATION STEPS

> Execute steps in order. Do not skip. Paths under `shared/src/commonMain/kotlin/com/example/ta33/` unless stated. Base package `com.example.ta33`.
> **Dependency ordering**: assumes **FR-02 implemented** (models, `RouteRepository`, `Ta33Database`, `appModule`, `kotlinx-coroutines-test`). **FR-01** should exist for Seam D (Step 8); if not yet merged, fold Step 8 into FR-01. No new external dependencies.

### Step 1: Add read models + pure resolver
**Goal**: The shapes FR-03 exposes and the shared active-route rule.
**Files**: `domain/model/RouteSummary.kt`, `domain/model/RouteDetail.kt`, `domain/route/ActiveRouteResolver.kt`

```kotlin
// domain/model/RouteSummary.kt
package com.example.ta33.domain.model
/** List-row projection for FR-03. Raw fields only — label/units/plurals are a UI concern. */
data class RouteSummary(
    val routeId: String,
    val name: String,        // e.g. "Trasa A"
    val distanceKm: Double,  // stored value from content JSON (FR-11)
    val controlCount: Int,   // e.g. 5
)
```
```kotlin
// domain/model/RouteDetail.kt
package com.example.ta33.domain.model
/** Detail projection: route metadata + its controls ordered by ordinal. */
data class RouteDetail(
    val routeId: String,
    val name: String,
    val distanceKm: Double,
    val controls: List<ControlPoint>, // sorted by ordinal (1-based)
) {
    val controlCount: Int get() = controls.size
}
```
```kotlin
// domain/route/ActiveRouteResolver.kt
package com.example.ta33.domain.route
/**
 * Effective active route id. An in-progress run's route wins; else the persisted selection
 * if it is still among the available routes; else the sole route; else null.
 * Pure + deterministic — shared by FR-03 (ObserveSelectedRoute) and FR-01 (AppStateReducer).
 */
object ActiveRouteResolver {
    fun resolve(
        activeRunRouteId: String?,
        selectedRouteId: String?,
        availableRouteIds: List<String>,
    ): String? =
        activeRunRouteId
            ?: selectedRouteId?.takeIf { it in availableRouteIds }
            ?: availableRouteIds.singleOrNull()
}
```
**Done when**: Files compile.

---

### Step 2: Add SQLDelight — summary query (Seam A) + `AppPreferences` table (Seam C)
**Goal**: A JOIN+COUNT projection for the list and a durable single-row selection store.
**Files**: `shared/src/commonMain/sqldelight/com/example/ta33/data/db/Route.sq` (edit, FR-02), `.../AppPreferences.sq` (new)

Add to `Route.sq`:
```sql
selectRouteSummaries:
SELECT Route.id, Route.name, Route.distanceKm, COUNT(ControlPoint.id) AS controlCount
FROM Route
LEFT JOIN ControlPoint ON ControlPoint.routeId = Route.id
GROUP BY Route.id, Route.name, Route.distanceKm
ORDER BY Route.name;
```
```sql
-- AppPreferences.sq  (single row, id = 1)
CREATE TABLE AppPreferences (
    id INTEGER NOT NULL PRIMARY KEY DEFAULT 1,
    selectedRouteId TEXT
);
INSERT OR IGNORE INTO AppPreferences(id, selectedRouteId) VALUES (1, NULL);
selectPreferences: SELECT * FROM AppPreferences WHERE id = 1;
setSelectedRouteId: UPDATE AppPreferences SET selectedRouteId = ? WHERE id = 1;
```
> `COUNT(...)` generates a `Long` column — map to `Int` in Step 4. The seed `INSERT OR IGNORE` guarantees the row exists (same pattern as FR-11's `Preparation`).

**Done when**: `./gradlew build` regenerates `RouteQueries.selectRouteSummaries` (with a generated projection type, e.g. `SelectRouteSummaries`) and `AppPreferencesQueries`.

---

### Step 3: Extend `RouteRepository` (Seams A + B) + add `AppPreferencesRepository` (Seam C)
**Goal**: Contracts for summaries, reactive detail, and the persisted selection.
**Files**: `domain/repository/RouteRepository.kt` (edit, FR-02), `domain/repository/AppPreferencesRepository.kt` (new)

Add to `RouteRepository` (additive — do not remove existing methods):
```kotlin
import com.example.ta33.domain.model.RouteSummary
import kotlinx.coroutines.flow.Flow

fun observeRouteSummaries(): Flow<List<RouteSummary>>       // Seam A (list)
fun observeRouteWithControls(routeId: String): Flow<Route?> // Seam B (reactive detail)
```
```kotlin
// domain/repository/AppPreferencesRepository.kt  (Seam C, FR-03-owned)
package com.example.ta33.domain.repository
import kotlinx.coroutines.flow.Flow
interface AppPreferencesRepository {
    fun observeSelectedRouteId(): Flow<String?>
    suspend fun getSelectedRouteId(): String?
    suspend fun setSelectedRouteId(routeId: String?)
}
```
**Done when**: Interfaces compile.

---

### Step 4: Implement the repositories
**Goal**: SQLDelight-backed reactive reads + selection persistence.
**Files**: `data/repository/RouteRepositoryImpl.kt` (edit, FR-02), `data/mapper/Mappers.kt` (edit, FR-02), `data/repository/AppPreferencesRepositoryImpl.kt` (new)

`RouteRepositoryImpl` (uses the exact generated accessor names — verify after build):
```kotlin
override fun observeRouteSummaries(): Flow<List<RouteSummary>> =
    routeQueries.selectRouteSummaries().asFlow().mapToList(Dispatchers.Default)
        .map { rows -> rows.map { RouteSummary(it.id, it.name, it.distanceKm, it.controlCount.toInt()) } }

override fun observeRouteWithControls(routeId: String): Flow<Route?> =
    combine(
        routeQueries.selectRouteById(routeId).asFlow().mapToOneOrNull(Dispatchers.Default),
        controlPointQueries.selectControlsForRoute(routeId).asFlow().mapToList(Dispatchers.Default),
    ) { routeRow, controlRows ->
        routeRow?.let { r ->
            Route(id = r.id, name = r.name, distanceKm = r.distanceKm,
                  controls = controlRows.map { it.toDomain() }) // toDomain() from FR-02 mapper
        }
    }
```
`AppPreferencesRepositoryImpl(db)`:
```kotlin
private val q get() = db.appPreferencesQueries
override fun observeSelectedRouteId(): Flow<String?> =
    q.selectPreferences().asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.selectedRouteId }
override suspend fun getSelectedRouteId(): String? = withContext(Dispatchers.Default) {
    q.selectPreferences().executeAsOneOrNull()?.selectedRouteId
}
override suspend fun setSelectedRouteId(routeId: String?) = withContext(Dispatchers.Default) {
    q.setSelectedRouteId(routeId)
}
```
> `selectControlsForRoute` already `ORDER BY ordinal` (FR-02), so `observeRouteWithControls` returns controls pre-sorted; the use-case still sorts defensively (Step 5).

**Done when**: Impls compile; `./gradlew build` passes.

---

### Step 5: Add use-cases
**Goal**: Encapsulate the FR-03 reads/writes the ViewModels call.
**Files**: `domain/usecase/ObserveRoutesUseCase.kt`, `ObserveRouteDetailUseCase.kt`, `ObserveSelectedRouteUseCase.kt`, `SelectActiveRouteUseCase.kt`

```kotlin
// ObserveRoutesUseCase.kt
class ObserveRoutesUseCase(private val routes: RouteRepository) {
    operator fun invoke(): Flow<List<RouteSummary>> = routes.observeRouteSummaries()
}
```
```kotlin
// ObserveRouteDetailUseCase.kt
class ObserveRouteDetailUseCase(private val routes: RouteRepository) {
    operator fun invoke(routeId: String): Flow<RouteDetail?> =
        routes.observeRouteWithControls(routeId).map { route ->
            route?.let {
                RouteDetail(
                    routeId = it.id, name = it.name, distanceKm = it.distanceKm,
                    controls = it.controls.sortedBy { c -> c.ordinal }, // defensive ordering
                )
            }
        }
}
```
```kotlin
// ObserveSelectedRouteUseCase.kt  — validated persisted selection (for the "selected" marker)
class ObserveSelectedRouteUseCase(
    private val routes: RouteRepository,
    private val prefs: AppPreferencesRepository,
) {
    operator fun invoke(): Flow<String?> =
        combine(routes.observeRouteSummaries(), prefs.observeSelectedRouteId()) { summaries, selected ->
            ActiveRouteResolver.resolve(
                activeRunRouteId = null, // FR-03 marker is selection-based; run-awareness is FR-01's job
                selectedRouteId = selected,
                availableRouteIds = summaries.map { it.routeId },
            )
        }
}
```
```kotlin
// SelectActiveRouteUseCase.kt
sealed interface SelectRouteResult {
    data object Selected : SelectRouteResult
    data object UnknownRoute : SelectRouteResult
}
class SelectActiveRouteUseCase(
    private val routes: RouteRepository,
    private val prefs: AppPreferencesRepository,
) {
    suspend operator fun invoke(routeId: String): SelectRouteResult {
        if (routes.getRouteWithControls(routeId) == null) return SelectRouteResult.UnknownRoute // FR-02 one-shot
        prefs.setSelectedRouteId(routeId)
        return SelectRouteResult.Selected
    }
}
```
**Done when**: Use-cases compile and hold no platform/UI concerns.

---

### Step 6: Add headless ViewModels
**Goal**: Expose list + detail as `StateFlow`; no UI consumes them here.
**Files**: `presentation/RouteListViewModel.kt`, `presentation/RouteDetailViewModel.kt`

```kotlin
// RouteListViewModel.kt
data class RouteListUiState(
    val routes: List<RouteSummary> = emptyList(),
    val selectedRouteId: String? = null,
    val loading: Boolean = true,
)
class RouteListViewModel(
    private val observeRoutes: ObserveRoutesUseCase,
    private val observeSelectedRoute: ObserveSelectedRouteUseCase,
    private val selectActiveRoute: SelectActiveRouteUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(RouteListUiState())
    val state: StateFlow<RouteListUiState> = _state.asStateFlow()
    init {
        combine(observeRoutes(), observeSelectedRoute()) { routes, selected ->
            RouteListUiState(routes = routes, selectedRouteId = selected, loading = false)
        }.onEach { _state.value = it }.launchIn(viewModelScope)
    }
    fun selectRoute(routeId: String) { viewModelScope.launch { selectActiveRoute(routeId) } }
}
```
```kotlin
// RouteDetailViewModel.kt
data class RouteDetailUiState(
    val detail: RouteDetail? = null,
    val isActive: Boolean = false,
    val notFound: Boolean = false,
    val loading: Boolean = true,
)
class RouteDetailViewModel(
    private val observeRouteDetail: ObserveRouteDetailUseCase,
    private val observeSelectedRoute: ObserveSelectedRouteUseCase,
    private val selectActiveRoute: SelectActiveRouteUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(RouteDetailUiState())
    val state: StateFlow<RouteDetailUiState> = _state.asStateFlow()
    private var routeId: String? = null

    fun bind(routeId: String) {
        this.routeId = routeId
        combine(observeRouteDetail(routeId), observeSelectedRoute()) { detail, selected ->
            RouteDetailUiState(
                detail = detail,
                isActive = detail != null && selected == detail.routeId,
                notFound = detail == null,
                loading = false,
            )
        }.onEach { _state.value = it }.launchIn(viewModelScope)
    }
    fun makeActive() { routeId?.let { id -> viewModelScope.launch { selectActiveRoute(id) } } }
}
```
> Mirrors FR-02 `RunLogViewModel` (`bind(...)` + `MutableStateFlow`/`asStateFlow`, `viewModelScope`).

**Done when**: Compiles.

---

### Step 7: Register in Koin `appModule` + Swift accessors
**Goal**: Make everything resolvable (mirrors FR-02/FR-01 style).
**Files**: `di/AppModule.kt`, `di/Koin.kt`

`appModule`:
```kotlin
// FR-03 route browsing + selection
single<AppPreferencesRepository> { AppPreferencesRepositoryImpl(get()) } // Ta33Database
factory { ObserveRoutesUseCase(get()) }
factory { ObserveRouteDetailUseCase(get()) }
factory { ObserveSelectedRouteUseCase(get(), get()) }
factory { SelectActiveRouteUseCase(get(), get()) }
factory { RouteListViewModel(get(), get(), get()) }
factory { RouteDetailViewModel(get(), get(), get()) }
```
`Koin.kt` `ViewModelProvider` (for SwiftUI, mirrors `greetingViewModel()`):
```kotlin
fun routeListViewModel(): RouteListViewModel = KoinPlatform.getKoin().get()
fun routeDetailViewModel(): RouteDetailViewModel = KoinPlatform.getKoin().get()
```
**Done when**: `./gradlew build` passes; Koin resolves (Step 9 / app launch); iOS builds.

---

### Step 8: Coordinated FR-01 seam (Seam D) — prefer the persisted selection
**Goal**: FR-01's `activeRouteId` honours the explicit choice via the shared resolver.
**Files**: `presentation/navigation/AppStateReducer.kt` (FR-01), `presentation/AppViewModel.kt` (FR-01)

- `AppViewModel` adds `appPreferencesRepository.observeSelectedRouteId()` to its `combine(...)` (alongside `observeRoutes`, `observeActiveRun`, and — if FR-11 merged — `observePreparationState`). Inject `AppPreferencesRepository`.
- `AppStateReducer.reduce(routes, activeRun, selectedRouteId, /*prep if FR-11*/, resolver)` replaces the interim derivation:
```kotlin
// before (FR-01): val activeRouteId = activeRun?.routeId ?: routes.singleOrNull()?.id
val activeRouteId = ActiveRouteResolver.resolve(
    activeRunRouteId = activeRun?.routeId,
    selectedRouteId = selectedRouteId,
    availableRouteIds = routes.map { it.id },
)
```
> Additive, single-site change (mirrors how FR-01 added `observeActiveRun` and FR-11 added `observePreparationState`). If FR-01 is unmerged, fold this into FR-01. `combine` arity: routes + activeRun + selection (+ prep) = 3–4 sources — within `combine`'s typed overloads.

**Done when**: FR-01 tests updated to feed a fake `AppPreferencesRepository`; `activeRouteId` assertions pass for run / selection / single / ambiguous cases.

---

### Step 9: Unit tests (`commonTest`)
**Goal**: Lock the FR-03 logic. Prefer fakes over a real DB driver (mirrors FR-02/FR-01).
**Files**: under `shared/src/commonTest/kotlin/com/example/ta33/`

- **`ActiveRouteResolverTest`** — `run wins`; `selection used when available`; `stale selection ignored → sole route / null`; `single route auto-selected`; `two routes, no run/selection → null`.
- **`ObserveRouteDetailUseCaseTest`** — fake `RouteRepository` emitting a `Route` with shuffled-ordinal controls → detail controls sorted by `ordinal`; unknown route → `null`.
- **`ObserveSelectedRouteUseCaseTest`** — persisted id present + route available → that id; persisted id for a removed route → resolver falls back; no persisted id + single route → that route.
- **`SelectActiveRouteUseCaseTest`** — known route → `Selected` + fake prefs received `setSelectedRouteId(id)`; unknown route → `UnknownRoute` + prefs untouched.
- **`RouteSummaryMappingTest`** — a route with 0 controls → `controlCount == 0`; `COUNT` `Long`→`Int` mapping; ordering by name (repo-level fake or a small mapping unit).
- **`RouteListViewModelTest`** — `runTest` + `Dispatchers.setMain(StandardTestDispatcher())`: emits summaries + `selectedRouteId`; `selectRoute(id)` invokes the use-case (fake asserts persistence).
- **`RouteDetailViewModelTest`** — `bind(id)` emits `detail` with `isActive` true when selection matches; unknown id → `notFound`; `makeActive()` persists.

Use fakes (`FakeRouteRepository` backed by `MutableStateFlow`, `FakeAppPreferencesRepository` backed by `MutableStateFlow<String?>`). `Dispatchers.setMain`/`resetMain` around VM tests; pure resolver/use-case tests need no dispatcher.

**Done when**: `./gradlew :shared:allTests` is green.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| Route with 0 controls | Summary `controlCount = 0`; detail `controls = []`; no crash | `LEFT JOIN` + `COUNT` yields 0; empty list flows through |
| Unknown `routeId` in detail | `notFound` state, no crash | `observeRouteWithControls` emits `null` → `RouteDetail = null` → `notFound = true` |
| Persisted selection points to a removed/renamed route (content re-downloaded) | Selection ignored; falls back to sole route or `null` | `ActiveRouteResolver` validates against `availableRouteIds` |
| `SelectActiveRoute` for a non-existent route | Nothing persisted; `UnknownRoute` | Use-case validates via FR-02 `getRouteWithControls` before writing |
| Content updates while a detail screen observes it (FR-11 re-download) | Detail refreshes live | Seam B is reactive (`asFlow` on both queries) |
| Controls with equal/duplicate `ordinal` | Deterministic order | `sortedBy { ordinal }` is stable; ties keep DB order (by `id` via query) |
| Multiple routes, no run, no selection | No route marked active | Resolver returns `null` (unchanged FR-01 behavior) |
| `distanceKm` is 0 / malformed from bad content | Read and surfaced as-is | FR-11 validates on ingest (its §6); FR-03 does not re-validate content |
| Empty route list (content not yet downloaded) | Empty list, `loading = false` after first emission | `observeRouteSummaries` emits `[]`; VM clears `loading` |
| `commonTest` has no `Main` dispatcher | VM tests still run | `Dispatchers.setMain(StandardTestDispatcher())` in VM tests only |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: `routeId` originates from app content / the `Destination.RouteDetail` nav arg (FR-01), not free user input; `SelectActiveRoute` still validates existence before persisting. Content field validity (coords, distance) is FR-11's ingest responsibility.
- **Auth/Access control**: None in Etapa 1 — anonymous local participant; no tokens (Etapa 2, Keystore/Keychain per stack §4).
- **Sensitive data**: Route/control coordinates and the selected-route id stay **on-device** (SQLDelight, app-private). No upload, no PII.
- **Logging**: Napier at debug for selection changes / route ids; do not log full control coordinate lists at info level; never log to persistent files.

---

## 7. ASSUMPTIONS

> User opted out of clarification (`skip questions` / `proceed directly`). Accepted defaults recorded here.

1. **FR-02 is implemented first** — its `Route`/`ControlPoint`/`GeoPoint` models, `RouteRepository` (incl. `getRouteWithControls`, `selectRouteById`, `selectControlsForRoute`), `Ta33Database`, mappers, and `appModule` exist and are **referenced**. If wrong: FR-03 cannot compile; do FR-02 first.
2. **Route length is the stored `distanceKm`** from the content JSON (organizer-supplied via FR-11), not computed. If a computed distance is ever required, add it as a separate field — do not overwrite the official value.
3. **The `RouteRepository` seams (A + B) are additive** — new methods only, no change to existing signatures. RouteSummary lives in `domain/model` and is referenced by the interface. If wrong: coordinate with the FR-02 owner.
4. **Persisted selection lives in a new `AppPreferences` single-row table** (FR-03-owned), not in FR-11's `Preparation` and not via a new `multiplatform-settings` dependency. Impact if wrong: swapping the persistence backend is a localized change behind `AppPreferencesRepository`.
5. **Seam D (FR-01 coordination) is a single additive site** using `ActiveRouteResolver`. If FR-01 is unmerged, fold it into FR-01. Impact: `activeRouteId` derivation gains a preferred source; no model change.
6. **The label "Trasa A · 33 km · 5 kontrol" is UI/localization** (Czech plurals) — deferred; the domain exposes raw fields only.
7. **`ObserveRouteDetail` is reactive** (Seam B). If reactivity proves unnecessary, it can degrade to a one-shot `getRouteWithControls` in the VM with no contract change to the use-case's callers.
8. **One user-selected route at a time** (single-row `AppPreferences`) — matches FR-02's "one active run" assumption and the single-event use case.
9. **Only Android + iOS targets** — `Dispatchers.Default` + native/android SQLDelight drivers suffice; no new dependencies.

> Open questions live in Section 12.

---

## 8. QUICK REFERENCE

### Files to Modify
- `shared/src/commonMain/sqldelight/com/example/ta33/data/db/Route.sq` — add `selectRouteSummaries` (Seam A).
- `shared/src/commonMain/kotlin/com/example/ta33/domain/repository/RouteRepository.kt` — add `observeRouteSummaries` + `observeRouteWithControls` (FR-02).
- `shared/src/commonMain/kotlin/com/example/ta33/data/repository/RouteRepositoryImpl.kt` — implement Seams A + B (FR-02).
- `shared/src/commonMain/kotlin/com/example/ta33/data/mapper/Mappers.kt` — summary-row → `RouteSummary` (if a mapper is preferred over inline).
- `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt` — register repo, use-cases, ViewModels.
- `shared/src/commonMain/kotlin/com/example/ta33/di/Koin.kt` — add `routeListViewModel()` / `routeDetailViewModel()`.
- **(Seam D, FR-01)** `presentation/navigation/AppStateReducer.kt` + `presentation/AppViewModel.kt` — read `observeSelectedRouteId()`, derive via `ActiveRouteResolver`.

### Files to Create
- `domain/model/RouteSummary.kt`, `domain/model/RouteDetail.kt`
- `domain/route/ActiveRouteResolver.kt`
- `domain/repository/AppPreferencesRepository.kt`
- `data/repository/AppPreferencesRepositoryImpl.kt`
- `domain/usecase/ObserveRoutesUseCase.kt`, `ObserveRouteDetailUseCase.kt`, `ObserveSelectedRouteUseCase.kt`, `SelectActiveRouteUseCase.kt`
- `presentation/RouteListViewModel.kt`, `presentation/RouteDetailViewModel.kt`
- SQLDelight: `AppPreferences.sq`
- `commonTest/…` — `ActiveRouteResolverTest`, `ObserveRouteDetailUseCaseTest`, `ObserveSelectedRouteUseCaseTest`, `SelectActiveRouteUseCaseTest`, `RouteSummaryMappingTest`, `RouteListViewModelTest`, `RouteDetailViewModelTest` (+ fakes `FakeRouteRepository`, `FakeAppPreferencesRepository`).

### Dependencies
- **None new.** (Already present: SQLDelight 2.1.0 + coroutines-extensions + drivers, Koin 4.1.0, coroutines-core, lifecycle-viewmodel; `kotlinx-coroutines-test` via FR-02.)

### Commands
```bash
./gradlew build                      # compile + regenerate SQLDelight (selectRouteSummaries, AppPreferences)
./gradlew :shared:allTests           # shared unit tests
./gradlew :androidApp:assembleDebug  # Android sanity
# iOS headless (compiles ViewModelProvider accessors into Shared):
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
| **A. Read models + use-cases + StateFlow VMs over FR-02; JOIN+COUNT summary; reactive detail; persisted selection in new `AppPreferences`; shared `ActiveRouteResolver` for the FR-01 seam** | Reuses FR-02 with minimal, enumerated additive seams; reactive + offline-first; single tested active-route rule; no new deps; matches stack §12 | Adds two `RouteRepository` methods + one new table/repo | ✅ |
| B. **Compute control count / distance on the fly** (eager-load controls in `observeRoutes`, count in Kotlin; distance via Haversine over controls) | No summary query | Wasteful eager loads / N+1; distance from control points is inaccurate (no polyline stored); overrides the organizer's official figure | — |
| C. **Persist selection via `multiplatform-settings`** (key-value) instead of a DB table | Tiny API; no schema change | New dependency for one value; not reactive by default; diverges from the SQLDelight single-row pattern (FR-11 `Preparation`) | — |
| D. **One-shot detail** (`RouteDetailViewModel` calls suspend `getRouteWithControls` in `bind`) | No Seam B; simplest | Stale if FR-11 updates content while viewing; inconsistent with the Flow-based core | — (kept as fallback in Assumption 7) |
| E. **Keep active-route derivation only in FR-01** (FR-03 just persists; FR-01 re-derives inline) | No shared helper | Duplicated validation logic drifts between FR-01 and FR-03 | — (superseded by shared `ActiveRouteResolver`) |

**Why the selected approach won**: It delivers the route list, detail, and a durable choice with the smallest, clearly-flagged set of additive seams over FR-02/FR-01, stays fully reactive and unit-testable in shared Kotlin with zero new dependencies, and honours the organizer-supplied distance instead of fabricating one.

### 12.2 Open Questions

- [ ] **Should the FR-03 "selected" marker be run-aware (like FR-01's effective active route)?** — Proposed direction: no — FR-03 marks the persisted *selection* (run-agnostic) to avoid `RunRepository` coupling; FR-01 owns the run-aware effective `activeRouteId`. Revisit if the route list must visibly reflect an in-progress run.
- [ ] **Do multiple routes actually ship for the 2026 event, or just "Trasa A"?** — Proposed direction: support N routes (list already does); if it's always one, the sole-route fallback in `ActiveRouteResolver` makes selection a no-op. Confirm with the organizer's content.
- [ ] **Does FR-04 (deník) need a route-scoped ordered-controls accessor beyond `RouteDetail.controls`?** — Proposed direction: FR-04 reuses `observeRouteWithControls` / `ObserveRouteDetail` for the ordered controls and layers collection state on top; no extra FR-03 seam expected.
- [ ] **Auto-select the sole route on first content download?** — Proposed direction: leave it implicit via the resolver's `singleOrNull` fallback (no write); persist an explicit selection only on user action, so a later content change can still re-resolve.

### 12.3 Suggestions & Follow-ups

- When **FR-04** lands, build the deník log by combining `ObserveRouteDetail` (ordered controls) with the FR-02 run/collection Flows — the ordered-controls seam is already here.
- When **FR-06/FR-07** land, feed the map/tile layer and the mapy.cz deep link from `RouteDetail.controls` + the route's stored geometry (add a polyline field to content/FR-02 then, if geometry is delivered).
- Add a **JVM SQLite driver** in `androidHostTest` to integration-test `selectRouteSummaries` (JOIN+COUNT) and `AppPreferences` against a real DB — good coverage, out of scope here.
- Extend `AppPreferencesRepository` for **FR-10** settings (notifications toggle, etc.) — the single-row table is the natural home.
- Add a Koin **`checkModules()`** test spanning FR-01/02/03/11 to catch DI-graph breakage early.

> Sections 9 (Design Reference) and 10 (Corrections From Current State) intentionally omitted: this is logic-only work with no UI/visual spec, and it is greenfield (no prior FR-03 implementation to correct — the FR-01/FR-02 touches are additive coordinated seams covered in Steps 2–4 & 8).
