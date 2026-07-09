# FR-10 Přehled a Nastavení — Aggregated Overview State & Settings/Preferences (Logic Only)

> **Summary**: Build the shared, headless *logic* for the third screen (Přehled/Overview + Nastavení) — a pure composition of the existing FR-01/FR-03/FR-04/FR-11 Flows into one `StateFlow<OverviewUiState>` (active route, progress "2 z 5", offline-package/"sync" status, readiness) plus a persistent notifications preference (an additive column on FR-03's `AppPreferences`) and bundled organizer-contact + FAQ content — as use-cases, a pure composer, and two headless ViewModels, with no UI built.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
TA33's third screen must, at a glance, tell a participant where they stand: which route is active, how far along they are (e.g. "2 z 5"), and whether the offline package is downloaded ("sync" status in Etapa 1). The same screen holds basic settings — a notifications on/off toggle, the organizer's contact, and a short FAQ. Today each of those facts lives behind a *different* FR's Flow (readiness/active route in FR-01, route summary + persisted selection in FR-03, progress in FR-04, offline-package status in FR-11), but nothing composes them into one overview state, and there is no persisted notifications preference nor any organizer/FAQ content source.

### 1.2 Solution Overview
Add a thin FR-10 aggregation layer on top of the existing seams — **compose, don't duplicate**. A pure `OverviewComposer` maps the upstream states (`AppUiState` + `RouteSummary` list + FR-04 `LogUiState?` + FR-11 `PreparationState`) into an `OverviewUiState`; an `ObserveOverviewUseCase` wires the upstream Flows (subscribing to the run log only when a run is active); and an `OverviewViewModel` exposes it as a `StateFlow`. Settings are a separate concern: extend FR-03's single-row `AppPreferences` table with a `notificationsEnabled` column (the one and only new persistence), expose it through two small use-cases, and serve organizer-contact + FAQ from a bundled `AppInfoRepository`; a small `SettingsViewModel` exposes all three. Everything is deterministic, headless, and unit-tested in `commonTest` with fakes.

### 1.3 Scope: What This IS
- **Read models** (`domain/model`): `OverviewUiState` (+ slim `OverviewProgress`), `SettingsUiState`, `OrganizerContact`, `FaqItem`.
- **Pure composer** (`domain/overview`): `OverviewComposer.compose(app, routeSummaries, log, preparation) -> OverviewUiState` — deterministic, no repos/coroutines (mirrors FR-04 `ControlLogDeriver` / FR-03 `ActiveRouteResolver`).
- **Use-cases** (`domain/usecase`): `ObserveOverviewUseCase`, `ObserveNotificationsEnabledUseCase`, `SetNotificationsEnabledUseCase` (+ a coordinated `ObserveAppStateUseCase` extracted from FR-01 — see Seam A).
- **Headless ViewModels** (`presentation`): `OverviewViewModel` (`StateFlow<OverviewUiState>`, read-only) and `SettingsViewModel` (`StateFlow<SettingsUiState>` + `setNotificationsEnabled(...)`). No UI consumes them yet.
- **Seam A (FR-01, coordinated refactor)**: extract FR-01's `combine(...) → AppStateReducer.reduce(...)` into `ObserveAppStateUseCase(): Flow<AppUiState>`; `AppViewModel` and FR-10 both consume it → single source of truth for readiness/active route/active run.
- **Seam B (FR-03-owned, additive)**: add a `notificationsEnabled` column to the existing single-row `AppPreferences` SQLDelight table and three methods to `AppPreferencesRepository`.
- **App-info source** (`domain/repository` + `data/repository`): `AppInfoRepository` interface + bundled `StaticAppInfoRepository` (organizer contact + FAQ) — a clean seam for later content-package/resource sourcing.
- **Koin registration** + Swift `ViewModelProvider` accessors (`overviewViewModel()`, `settingsViewModel()`).
- **Unit tests** (`commonTest`) with fakes over the upstream use-cases/repositories.

### 1.4 Scope: What This IS NOT
- **No UI whatsoever** — no Compose/SwiftUI screens, no third-screen layout, no cards/toggles, no theming, no visuals, no Czech pluralization of "2 z 5" or FAQ copy formatting. UI + localization are a deliberately deferred later phase. Read models expose **raw** fields only.
- **No real notification delivery** — no push, no local notification scheduling, no OS permission prompts, no platform notification APIs. FR-10 persists **only** an on/off *preference*; delivery is a separate/later concern.
- **No server sync** — "stav synchronizace" in Etapa 1 == **offline-package status** (FR-11 `PreparationState`). Server sync is Etapa 2 (project-stack §10). FR-10 does not touch download orchestration.
- **No redefinition of upstream FRs** — `AppUiState`/`AppReadiness`/`ContentAvailability`/`AppStateReducer` (FR-01), `RouteSummary`/`RouteRepository`/`AppPreferencesRepository`/`ObserveSelectedRouteUseCase` (FR-03), `LogUiState`/`ObserveRunLogUseCase` (FR-04), `PreparationState`/`ObservePreparationStateUseCase` (FR-11) are **referenced**, not duplicated.
- **No new persistence except the additive `notificationsEnabled` column** on FR-03's `AppPreferences`. No new table, no new dependency, no new Gradle module.
- **No re-derivation of readiness/progress** — FR-10 reuses FR-01's `activeRouteId`/readiness and FR-04's `LogUiState` as-is.

---

## 2. SUCCESS CRITERIA

Implementation is COMPLETE when ALL criteria are met:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | Project compiles with the new models, composer, use-cases, ViewModels, repository + the additive `notificationsEnabled` column | `./gradlew build` succeeds; SQLDelight regenerates `AppPreferences` with the new column |
| 2 | `OverviewComposer.compose(...)` maps active route (by `activeRouteId`), progress, sync status, readiness into `OverviewUiState`; pure and deterministic | `OverviewComposerTest` table cases |
| 3 | When `activeRunId == null`, overview shows `hasActiveRun = false` and `progress = null` (no attempt to bind the run log) | `OverviewComposerTest` + `ObserveOverviewUseCaseTest` no-run case |
| 4 | When a run is active, progress reflects FR-04 `LogUiState` (`collectedCount`/`totalCount`/`isComplete`/`isRunFinished`) unchanged | `ObserveOverviewUseCaseTest` active-run case |
| 5 | `activeRouteId` pointing to a route not present in the summaries yields `activeRoute = null` (graceful), no crash | `OverviewComposerTest` stale-id case |
| 6 | Sync status surfaces FR-11 `PreparationStatus` (+ `manifestVersion`/`readyAtMillis`); `PREPARING`/`READY`/`ERROR`/`NOT_STARTED` all pass through | `OverviewComposerTest` prep cases |
| 7 | `notificationsEnabled` persists across restart (single-row `AppPreferences`), defaults to `true`, and is observable | `NotificationsPreferenceTest` (fake or real-DB repo) |
| 8 | `SetNotificationsEnabledUseCase(false)` flips the persisted value; `ObserveNotificationsEnabledUseCase()` re-emits `false` | `NotificationsPreferenceTest` |
| 9 | `AppInfoRepository` returns a non-empty organizer contact and FAQ list (bundled) | `AppInfoRepositoryTest` |
| 10 | `OverviewViewModel` exposes `StateFlow<OverviewUiState>` (loading→composed); `SettingsViewModel` exposes `StateFlow<SettingsUiState>` and `setNotificationsEnabled(...)` persists | `OverviewViewModelTest`, `SettingsViewModelTest` (`runTest` + `Dispatchers.setMain`) |
| 11 | `AppViewModel` (FR-01) and `ObserveOverviewUseCase` derive app state from the **same** `ObserveAppStateUseCase` (no second reducer path) | Code review + `AppViewModelTest` still green + `ObserveAppStateUseCaseTest` |
| 12 | All new components resolvable via Koin; Swift accessors compile into `Shared` | Koin resolution test / app launch; iOS `xcodebuild` |
| 13 | `./gradlew :shared:allTests` green | Run tests |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
        NATIVE UI (later phase — NOT built here)
        reads OverviewUiState / SettingsUiState, sends setNotificationsEnabled()
   ══════════════════ SHARED CORE (:shared, commonMain) ══════════════════
                     presentation/
      ┌────────────────────────────┐     ┌────────────────────────────┐
      │      OverviewViewModel      │     │       SettingsViewModel      │
      │ StateFlow<OverviewUiState>  │     │  StateFlow<SettingsUiState>  │
      └──────────────┬─────────────┘     └───────────────┬─────────────┘
                     │                                    │
        domain/usecase/                       domain/usecase/
   ┌───────────────────────────┐   ┌──────────────────┬──────────────────┐
   │   ObserveOverviewUseCase   │   │ ObserveNotif.    │ SetNotif.        │
   │  combine + flatMapLatest   │   │ EnabledUseCase   │ EnabledUseCase   │
   └──────────────┬────────────┘   └────────┬─────────┴─────────┬────────┘
                  │ delegates mapping to                        │
        domain/overview/                                        │
   ┌───────────────────────────┐                                │
   │  OverviewComposer.compose  │  PURE / deterministic          │
   │  (no repos, no coroutines) │  → OverviewUiState             │
   └──────────────┬────────────┘                                │
                  │ inputs (all REFERENCED, not redefined)       │
   ┌──────────────┼──────────────┬───────────────┬──────────────┴──────────┐
   │ FR-01        │ FR-03         │ FR-04         │ FR-11        │ FR-10-new │
   │ ObserveApp-  │ RouteRepo     │ ObserveRunLog │ ObservePrep- │ AppInfo-  │
   │ StateUseCase │ .observeRoute-│ UseCase       │ arationState │ Repository│
   │ (Seam A)     │  Summaries()  │ (runId,routeId│ UseCase      │ (bundled) │
   │ → AppUiState │ → RouteSummary│ )→ LogUiState │ → Preparation│           │
   └──────┬───────┴───────┬───────┴───────┬───────┴──────┬───────┴────┬──────┘
          │ Seam A (FR-01) │               │              │            │
   AppStateReducer.reduce  │        AppPreferencesRepository (Seam B: + notificationsEnabled)
   (routes, activeRun,     │        ┌───────────────────────────────────────┐
    selectedRouteId, prep) │        │ AppPreferences.sq (single row):        │
          │                │        │  id, selectedRouteId, notificationsEnabled
          └────────────────┴────────┴───────────────► Ta33Database ◄─────────┘
```

**Data flow (open Přehled):** native layer resolves `OverviewViewModel` (Koin) → VM collects `ObserveOverviewUseCase()` which `combine`s `ObserveAppStateUseCase()` (FR-01 `AppUiState`), `RouteRepository.observeRouteSummaries()` (FR-03), and `ObservePreparationStateUseCase()` (FR-11), then `flatMapLatest` subscribes `ObserveRunLogUseCase(runId, routeId)` (FR-04) **only** when `activeRunId`+`activeRouteId` are non-null (else `flowOf(null)`), and feeds all four into the pure `OverviewComposer.compose(...)` → `OverviewUiState`. **Settings:** `SettingsViewModel` seeds `organizerContact`/`faq` from `AppInfoRepository` and collects `ObserveNotificationsEnabledUseCase()`; `setNotificationsEnabled(...)` calls `SetNotificationsEnabledUseCase` → persists on `AppPreferences` → re-emits.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Where code lives | Existing `:shared`, new/reused **package layers** (`domain/model`, `domain/overview`, `domain/usecase`, `domain/repository`, `data/repository`, `presentation`) | project-stack §12 "layers as packages"; no new Gradle module |
| Overview assembly | **Pure `OverviewComposer` + thin `ObserveOverviewUseCase`** | Mirrors FR-04 `ControlLogDeriver` / FR-03 `ActiveRouteResolver`; table-testable with zero coroutines; VM/use-case are thin wiring |
| Readiness/active-route source | **Reuse FR-01 `AppUiState`** (readiness, `activeRouteId`, `activeRunId`) — do not recompute | Single source of truth; FR-01 already resolves run/selection/sole route via `ActiveRouteResolver`; re-deriving would risk drift |
| Avoiding VM→VM dependency | **Extract `ObserveAppStateUseCase` (Seam A)** consumed by both `AppViewModel` and FR-10 | A ViewModel must not depend on another ViewModel; extracting the existing `combine+reduce` into a use-case keeps ONE derivation path and stays DI-clean |
| Progress source | **Consume FR-04 `LogUiState`** via `ObserveRunLogUseCase`, bound only when a run is active | FR-04 owns progress derivation; FR-10 must not re-derive "2 z 5". No active run ⇒ `progress = null` (not "0 z N") |
| Conditional run-log binding | `flatMapLatest` over app state: `if (runId != null && routeId != null) observeRunLog(...) else flowOf(null)` | `ObserveRunLogUseCase` requires **both** ids; binding with a null id is invalid. Re-subscribes cleanly when a run starts/ends |
| "Sync" semantics | **Offline-package `PreparationStatus`** (FR-11), surfaced as `syncStatus` | zadani "stav synchronizace" in Etapa 1 = offline package; server sync is Etapa 2 (§10). Field documented as such |
| Notifications persistence | **Additive `notificationsEnabled` column on FR-03's `AppPreferences`** (Seam B) | FR-03 §12.3 explicitly nominates `AppPreferencesRepository` as the home for FR-10 settings; reuses the single-row + reactive SQLDelight pattern; no new table/dependency |
| Notifications default | **`true` (ON), stored as `INTEGER DEFAULT 1`** | Event app: participants expect pre-event reminders by default and can opt out; a persisted default via `DEFAULT 1` needs no seeding logic |
| Settings VM vs Overview VM | **Separate `SettingsViewModel`** | Overview is read-only aggregation; settings carries a **write** intent (toggle) + static info — distinct concern, lifecycle, and tests. Both can back the one Přehled tab in the UI phase |
| Organizer contact + FAQ source | **Bundled `AppInfoRepository` (static)**, not FR-11 content JSON | FR-11 `ContentDto` carries only routes/controls/tiles → adding org/FAQ = an FR-11 DTO change; this info is app-level, low-churn, and must exist **before any download** (offline-first, first launch). Interface is a seam for later content-package/resource sourcing |
| Contact/FAQ copy | Repo returns **raw/placeholder domain data** now; final localized copy migrates to Compose string resources in the UI phase | Consistent with FR-03/FR-04 stance (labels/plurals are UI/localization); organizer supplies real texts (zadani) via a `ContentConfig`-style placeholder until delivered |
| ViewModel style | `MutableStateFlow` + `asStateFlow`, `viewModelScope`, initial `loading = true` | Mirrors FR-01/02/03/04/11 ViewModels |

---

## 4. IMPLEMENTATION STEPS

> Execute steps in order. Do not skip. Paths under `shared/src/commonMain/kotlin/com/example/ta33/` unless stated. Base package `com.example.ta33`.
> **Dependency ordering**: assumes **FR-01, FR-03, FR-04, FR-11 implemented** (their models/use-cases/repositories are **referenced**). If any is unmerged, define the referenced symbol canonically in that FR and fold the coordinated seams here into it. No new external dependencies.

### Step 1: Extract `ObserveAppStateUseCase` from FR-01 (Seam A, coordinated)
**Goal**: One reusable, testable source of `AppUiState` consumed by both `AppViewModel` and FR-10.
**Files**: `domain/usecase/ObserveAppStateUseCase.kt` (new), `presentation/AppViewModel.kt` (edit, FR-01)

```kotlin
// domain/usecase/ObserveAppStateUseCase.kt
package com.example.ta33.domain.usecase

import com.example.ta33.domain.repository.AppPreferencesRepository
import com.example.ta33.domain.repository.PreparationRepository
import com.example.ta33.domain.repository.RouteRepository
import com.example.ta33.domain.repository.RunRepository
import com.example.ta33.presentation.navigation.AppStateReducer
import com.example.ta33.presentation.navigation.AppUiState
import com.example.ta33.presentation.navigation.StartDestinationResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Single source of truth for AppUiState (FR-01). Wraps the exact combine + AppStateReducer
 * that AppViewModel used, so FR-10's overview and FR-01's ViewModel share ONE derivation.
 * (Arity mirrors the FR-01 reducer after FR-03 Seam D + FR-11 Step 12: routes, activeRun,
 *  selectedRouteId, preparationState.)
 */
class ObserveAppStateUseCase(
    private val routes: RouteRepository,
    private val runs: RunRepository,
    private val prefs: AppPreferencesRepository,
    private val prep: PreparationRepository,
    private val resolver: StartDestinationResolver,
) {
    operator fun invoke(): Flow<AppUiState> =
        combine(
            routes.observeRoutes(),
            runs.observeActiveRun(),
            prefs.observeSelectedRouteId(),
            prep.observePreparationState(),
        ) { routeList, activeRun, selectedRouteId, preparation ->
            AppStateReducer.reduce(routeList, activeRun, selectedRouteId, preparation, resolver)
        }
}
```
Refactor `AppViewModel.start()` to consume it (keep `ensureLocalParticipant()` fire-and-forget):
```kotlin
private fun start() {
    viewModelScope.launch { ensureLocalParticipant() }
    observeAppState()                       // injected ObserveAppStateUseCase
        .onEach { _state.value = it }
        .launchIn(viewModelScope)
}
```
> Match the **actual** `AppStateReducer.reduce(...)` signature present after FR-03/FR-11 merged. If those seams differ, align this use-case to the real signature — do not add a second reducer variant. If FR-01 is unmerged, introduce this use-case as FR-01's derivation from the start.

**Done when**: `ObserveAppStateUseCase` compiles; `AppViewModel` delegates to it; FR-01 tests still pass.

---

### Step 2: Add the notifications column to `AppPreferences` (Seam B, additive)
**Goal**: Durable notifications on/off preference on the existing single-row table.
**Files**: `shared/src/commonMain/sqldelight/com/example/ta33/data/db/AppPreferences.sq` (edit, FR-03)

```sql
-- AppPreferences.sq  (single row, id = 1) — FR-03 table, FR-10 adds notificationsEnabled
CREATE TABLE AppPreferences (
    id INTEGER NOT NULL PRIMARY KEY DEFAULT 1,
    selectedRouteId TEXT,
    notificationsEnabled INTEGER NOT NULL DEFAULT 1   -- 0/1 boolean; default ON
);
INSERT OR IGNORE INTO AppPreferences(id, selectedRouteId, notificationsEnabled) VALUES (1, NULL, 1);

selectPreferences: SELECT * FROM AppPreferences WHERE id = 1;
setSelectedRouteId: UPDATE AppPreferences SET selectedRouteId = ? WHERE id = 1;
setNotificationsEnabled: UPDATE AppPreferences SET notificationsEnabled = ? WHERE id = 1;
```
> Pre-release DB (project-stack §11): editing the `CREATE TABLE` is acceptable. If a versioned/released schema is ever enforced, add an `AppPreferences.sqm` migration (`ALTER TABLE AppPreferences ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 1;`) instead of editing the create — see Edge Cases. `notificationsEnabled` maps to `Long` (0/1) in generated code; convert to `Boolean` in the repo.

**Done when**: `./gradlew build` regenerates `AppPreferencesQueries` with `setNotificationsEnabled` and the new column on `selectPreferences`.

---

### Step 3: Extend `AppPreferencesRepository` (Seam B contract) + impl
**Goal**: Reactive read + write of the notifications preference.
**Files**: `domain/repository/AppPreferencesRepository.kt` (edit, FR-03), `data/repository/AppPreferencesRepositoryImpl.kt` (edit, FR-03)

Add to the interface (additive — keep existing selection methods):
```kotlin
fun observeNotificationsEnabled(): kotlinx.coroutines.flow.Flow<Boolean>
suspend fun getNotificationsEnabled(): Boolean
suspend fun setNotificationsEnabled(enabled: Boolean)
```
Impl:
```kotlin
override fun observeNotificationsEnabled(): Flow<Boolean> =
    q.selectPreferences().asFlow().mapToOneOrNull(Dispatchers.Default)
        .map { (it?.notificationsEnabled ?: 1L) == 1L }

override suspend fun getNotificationsEnabled(): Boolean = withContext(Dispatchers.Default) {
    (q.selectPreferences().executeAsOneOrNull()?.notificationsEnabled ?: 1L) == 1L
}

override suspend fun setNotificationsEnabled(enabled: Boolean) = withContext(Dispatchers.Default) {
    q.setNotificationsEnabled(if (enabled) 1L else 0L)
}
```
**Done when**: Interface + impl compile; `./gradlew build` passes.

---

### Step 4: Add the FR-10 read models
**Goal**: The shapes the overview + settings ViewModels expose (raw fields only).
**Files**: `domain/model/OverviewUiState.kt`, `domain/model/OrganizerContact.kt`, `domain/model/FaqItem.kt`, `domain/model/SettingsUiState.kt`

```kotlin
// domain/model/OverviewUiState.kt
package com.example.ta33.domain.model

import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.presentation.navigation.AppReadiness
import com.example.ta33.presentation.navigation.ContentAvailability

/** Slim progress projection for the overview (raw counts; no label/plurals — UI concern). */
data class OverviewProgress(
    val collectedCount: Int,   // e.g. 2
    val totalCount: Int,       // e.g. 5
    val isComplete: Boolean,
    val isRunFinished: Boolean,
)

/** Aggregated Přehled state (FR-10). Pure read model returned by OverviewComposer.compose(...). */
data class OverviewUiState(
    val readiness: AppReadiness = AppReadiness.LOADING,
    val contentAvailability: ContentAvailability = ContentAvailability.UNKNOWN,
    val activeRoute: RouteSummary? = null,       // reuses FR-03 RouteSummary; null if none/stale
    val hasActiveRun: Boolean = false,
    val progress: OverviewProgress? = null,      // null when no active run (not "0 z N")
    val syncStatus: PreparationStatus = PreparationStatus.NOT_STARTED, // offline package (Etapa 1)
    val manifestVersion: Int? = null,
    val readyAtMillis: Long? = null,
    val loading: Boolean = true,                 // VM initial; composer emits false
)
```
```kotlin
// domain/model/OrganizerContact.kt
package com.example.ta33.domain.model
/** Organizer contact (FR-10). Data, not translatable copy; real values organizer-supplied. */
data class OrganizerContact(
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
)
```
```kotlin
// domain/model/FaqItem.kt
package com.example.ta33.domain.model
/** One FAQ entry (FR-10). Final localized copy is a UI/localization concern (deferred). */
data class FaqItem(val id: String, val question: String, val answer: String)
```
```kotlin
// domain/model/SettingsUiState.kt
package com.example.ta33.domain.model
data class SettingsUiState(
    val notificationsEnabled: Boolean = true,
    val organizerContact: OrganizerContact? = null,
    val faq: List<FaqItem> = emptyList(),
    val loading: Boolean = true,
)
```
> `RouteSummary` (FR-03) is reused directly for `activeRoute` — no duplicate route model. If `AppReadiness`/`ContentAvailability` live in `presentation/navigation` (FR-01), the import above is correct; keep it, do not redefine them.

**Done when**: Files compile.

---

### Step 5: Add the pure `OverviewComposer` (heart of FR-10)
**Goal**: Deterministic mapping of upstream states → `OverviewUiState`. No repos, no coroutines.
**Files**: `domain/overview/OverviewComposer.kt`

```kotlin
package com.example.ta33.domain.overview

import com.example.ta33.domain.model.*
import com.example.ta33.presentation.navigation.AppUiState

/**
 * PURE, deterministic overview composition (FR-10). No repositories, no coroutines.
 * Composes FR-01 app state + FR-03 route summaries + FR-04 log + FR-11 preparation.
 */
object OverviewComposer {
    fun compose(
        app: AppUiState,
        routeSummaries: List<RouteSummary>,
        log: LogUiState?,               // null when no active run
        preparation: PreparationState,
    ): OverviewUiState {
        val activeRoute = app.activeRouteId
            ?.let { id -> routeSummaries.firstOrNull { it.routeId == id } } // null if stale/missing
        val progress = log?.let {
            OverviewProgress(
                collectedCount = it.collectedCount,
                totalCount = it.totalCount,
                isComplete = it.isComplete,
                isRunFinished = it.isRunFinished,
            )
        }
        return OverviewUiState(
            readiness = app.readiness,
            contentAvailability = app.contentAvailability,
            activeRoute = activeRoute,
            hasActiveRun = app.activeRunId != null,
            progress = progress,
            syncStatus = preparation.status,
            manifestVersion = preparation.manifestVersion,
            readyAtMillis = preparation.readyAtMillis,
            loading = false,
        )
    }
}
```
**Done when**: Compiles; holds no repo/coroutine reference.

---

### Step 6: Add `ObserveOverviewUseCase`
**Goal**: Wire the upstream Flows and delegate to `OverviewComposer`, binding the run log only when a run is active.
**Files**: `domain/usecase/ObserveOverviewUseCase.kt`

```kotlin
package com.example.ta33.domain.usecase

import com.example.ta33.domain.model.LogUiState
import com.example.ta33.domain.model.OverviewUiState
import com.example.ta33.domain.overview.OverviewComposer
import com.example.ta33.domain.repository.RouteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class ObserveOverviewUseCase(
    private val observeAppState: ObserveAppStateUseCase,        // FR-01 (Seam A)
    private val routes: RouteRepository,                        // FR-03 observeRouteSummaries
    private val observeRunLog: ObserveRunLogUseCase,            // FR-04
    private val observePreparation: ObservePreparationStateUseCase, // FR-11
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<OverviewUiState> =
        combine(
            observeAppState(),
            routes.observeRouteSummaries(),
            observePreparation(),
        ) { app, summaries, prep -> Triple(app, summaries, prep) }
            .flatMapLatest { (app, summaries, prep) ->
                val logFlow: Flow<LogUiState?> =
                    if (app.activeRunId != null && app.activeRouteId != null)
                        observeRunLog(app.activeRunId!!, app.activeRouteId!!)
                    else
                        flowOf(null)
                logFlow.map { log -> OverviewComposer.compose(app, summaries, log, prep) }
            }
}
```
> `flatMapLatest` re-subscribes the run log whenever `activeRunId`/`activeRouteId` change (run starts/ends), and cancels the previous subscription — exactly the desired "bind only while a run exists" behavior.

**Done when**: Compiles.

---

### Step 7: Add the settings + notifications use-cases
**Goal**: Thin read/write use-cases over the extended `AppPreferencesRepository`.
**Files**: `domain/usecase/ObserveNotificationsEnabledUseCase.kt`, `domain/usecase/SetNotificationsEnabledUseCase.kt`

```kotlin
class ObserveNotificationsEnabledUseCase(private val prefs: AppPreferencesRepository) {
    operator fun invoke(): Flow<Boolean> = prefs.observeNotificationsEnabled()
}
class SetNotificationsEnabledUseCase(private val prefs: AppPreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) = prefs.setNotificationsEnabled(enabled)
}
```
**Done when**: Compile; hold no platform/UI concern.

---

### Step 8: Add `AppInfoRepository` + bundled static impl
**Goal**: Serve organizer contact + FAQ from bundled content, behind a swappable seam.
**Files**: `domain/repository/AppInfoRepository.kt`, `data/repository/StaticAppInfoRepository.kt`

```kotlin
// domain/repository/AppInfoRepository.kt
package com.example.ta33.domain.repository
import com.example.ta33.domain.model.FaqItem
import com.example.ta33.domain.model.OrganizerContact

/** Source of app-level info (FR-10). Bundled for Etapa 1; a seam for later content-package/resources. */
interface AppInfoRepository {
    fun organizerContact(): OrganizerContact
    fun faq(): List<FaqItem>
}
```
```kotlin
// data/repository/StaticAppInfoRepository.kt
package com.example.ta33.data.repository
import com.example.ta33.domain.model.FaqItem
import com.example.ta33.domain.model.OrganizerContact
import com.example.ta33.domain.repository.AppInfoRepository

/**
 * Bundled Etapa-1 content. TODO(content): organizer supplies final contact + FAQ text (zadani).
 * Final localized copy migrates to Compose string resources in the UI phase; this stays the seam.
 */
class StaticAppInfoRepository : AppInfoRepository {
    override fun organizerContact() = OrganizerContact(
        name = "TA33 – Teplicko-Adršpašská 33",
        email = "info@ta33.example",     // placeholder until organizer delivers
        phone = null,
        website = "https://ta33.example",
    )
    override fun faq() = listOf(
        FaqItem("offline", "Funguje aplikace bez signálu?", "Ano — po stažení balíčku funguje offline."),
        FaqItem("controls", "Jak se sbírají kontroly?", "Po příchodu do prostoru kontroly ji potvrdíte v aplikaci."),
        FaqItem("timing", "Jak se měří čas?", "Start a cíl přes QR kód; mezičasy podle sebrání kontrol."),
    )
}
```
> Synchronous getters (static, no I/O). If a later phase sources this from the FR-11 content package or a network, change the impl behind the interface (optionally to `suspend`/`Flow`) — callers in the VM adapt locally; see Open Questions.

**Done when**: Compile.

---

### Step 9: Add the headless ViewModels
**Goal**: Expose overview + settings as `StateFlow`; no UI consumes them here.
**Files**: `presentation/OverviewViewModel.kt`, `presentation/SettingsViewModel.kt`

```kotlin
// presentation/OverviewViewModel.kt
class OverviewViewModel(
    private val observeOverview: ObserveOverviewUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(OverviewUiState())   // loading = true
    val state: StateFlow<OverviewUiState> = _state.asStateFlow()
    init {
        observeOverview().onEach { _state.value = it }.launchIn(viewModelScope)
    }
}
```
```kotlin
// presentation/SettingsViewModel.kt
class SettingsViewModel(
    observeNotifications: ObserveNotificationsEnabledUseCase,
    private val setNotifications: SetNotificationsEnabledUseCase,
    appInfo: AppInfoRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsUiState(organizerContact = appInfo.organizerContact(), faq = appInfo.faq()),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    init {
        observeNotifications()
            .onEach { enabled -> _state.update { it.copy(notificationsEnabled = enabled, loading = false) } }
            .launchIn(viewModelScope)
    }
    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { setNotifications(enabled) }
    }
}
```
> Mirrors FR-03 `RouteListViewModel` / FR-04 `RunLogViewModel`. `SettingsViewModel` seeds static info synchronously and reacts to the persisted toggle.

**Done when**: Compile.

---

### Step 10: Register in Koin `appModule` + Swift accessors
**Goal**: Make everything resolvable (mirrors FR-01/03/04/11 style).
**Files**: `di/AppModule.kt`, `di/Koin.kt`

`appModule`:
```kotlin
// FR-10 overview + settings
single<AppInfoRepository> { StaticAppInfoRepository() }
factory { ObserveAppStateUseCase(get(), get(), get(), get(), get()) } // Seam A (also used by AppViewModel)
factory { ObserveOverviewUseCase(get(), get(), get(), get()) }
factory { ObserveNotificationsEnabledUseCase(get()) }
factory { SetNotificationsEnabledUseCase(get()) }
factory { OverviewViewModel(get()) }
factory { SettingsViewModel(get(), get(), get()) }
```
> Update FR-01's `AppViewModel` registration to inject `ObserveAppStateUseCase` (Seam A) instead of the raw repos, e.g. `factory { AppViewModel(get(), get()) }` (`ObserveAppStateUseCase`, `EnsureLocalParticipantUseCase`) — match FR-01's actual constructor after the Step 1 refactor.

`Koin.kt` `ViewModelProvider` (for SwiftUI):
```kotlin
fun overviewViewModel(): OverviewViewModel = KoinPlatform.getKoin().get()
fun settingsViewModel(): SettingsViewModel = KoinPlatform.getKoin().get()
```
**Done when**: `./gradlew build` passes; Koin resolves (Step 11 / app launch); iOS builds.

---

### Step 11: Unit tests (`commonTest`)
**Goal**: Lock the FR-10 logic. Prefer fakes over a real DB driver (mirrors FR-01/03/04/11).
**Files**: under `shared/src/commonTest/kotlin/com/example/ta33/`

- **`OverviewComposerTest`** (pure, table-driven):
  1. Not ready (readiness `NOT_READY`, no routes, prep `NOT_STARTED`) → `activeRoute=null`, `progress=null`, `hasActiveRun=false`, `syncStatus=NOT_STARTED`, `loading=false`.
  2. Content present, no active run (`activeRouteId="r1"`, `activeRunId=null`, prep `READY`) → `activeRoute` = summary r1, `hasActiveRun=false`, `progress=null`, `syncStatus=READY`.
  3. Active run partial (`activeRunId="run1"`, log 2 of 5) → `hasActiveRun=true`, `progress=(2,5,false,false)`.
  4. Active run complete + finished (log 5/5, finished) → `progress.isComplete=true`, `isRunFinished=true`.
  5. Stale `activeRouteId` not in summaries → `activeRoute=null`, no crash.
  6. Prep `PREPARING` / `ERROR` → `syncStatus` passes through; `manifestVersion`/`readyAtMillis` mapped.
- **`ObserveOverviewUseCaseTest`** — fakes emitting `MutableStateFlow`s: no active run → one composed emission with `progress=null`, run log **not** subscribed (fake `ObserveRunLogUseCase` asserts not invoked or invoked only when ids present); push an active run (ids set) → `flatMapLatest` binds the log and `progress` appears; clearing the run → back to `progress=null`.
- **`NotificationsPreferenceTest`** — `SetNotificationsEnabledUseCase(false)` → repo persists `0`; `ObserveNotificationsEnabledUseCase()` emits `false`; default (fresh) → `true`.
- **`AppInfoRepositoryTest`** — `StaticAppInfoRepository.organizerContact()` non-blank name; `faq()` non-empty with unique ids.
- **`OverviewViewModelTest`** — `runTest` + `Dispatchers.setMain(StandardTestDispatcher())`: state transitions `loading=true` → composed `loading=false` after first emission.
- **`SettingsViewModelTest`** — `runTest` + `setMain`: initial state carries `organizerContact`+`faq`; `setNotificationsEnabled(false)` → fake use-case/repo received `false` and state re-emits `notificationsEnabled=false`.
- **`ObserveAppStateUseCaseTest`** (Seam A) — light test that the use-case emits the same `AppUiState` the FR-01 reducer produces for a given fake-repo set (readiness/activeRouteId/activeRunId). FR-01's existing `AppViewModelTest` should still pass after the refactor.

Use fakes (`FakeAppPreferencesRepository` — extend FR-03's with the notifications field; `FakeRouteRepository`, fake `ObserveRunLogUseCase`/`ObservePreparationStateUseCase`/`ObserveAppStateUseCase` backed by `MutableStateFlow`). `Dispatchers.setMain`/`resetMain` around VM tests only; pure composer tests need no dispatcher.

**Done when**: `./gradlew :shared:allTests` is green.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| No active run (`activeRunId == null`) | Overview shows no progress | `flatMapLatest` uses `flowOf(null)`; composer sets `progress=null`, `hasActiveRun=false` |
| Active run but route missing from content (stale) | Still `hasActiveRun=true`; `activeRoute=null` | Composer look-up returns null; progress still bound via ids (FR-04 tolerates missing controls) |
| `activeRouteId` set but not in summaries (content re-download changed ids) | `activeRoute=null`, no crash | `firstOrNull { it.routeId == id }` → null |
| Offline package not started / errored | `syncStatus = NOT_STARTED`/`ERROR` surfaced verbatim | Pass-through from FR-11 `PreparationState`; no interpretation in FR-10 |
| Notifications value never written | Defaults to `true` | `INTEGER NOT NULL DEFAULT 1` + `?: 1L` guard in repo |
| Run starts/ends mid-view | Progress appears/disappears live | `flatMapLatest` re-subscribes/cancels the run-log flow on id change |
| Content re-downloaded while Přehled open (FR-11) | Overview refreshes live | All upstream sources are reactive; `combine`/`flatMapLatest` re-emit |
| Released-DB schema enforcement (post-launch) | Column added without data loss | Ship `AppPreferences.sqm` migration (`ALTER TABLE … ADD COLUMN … DEFAULT 1`) instead of editing `CREATE TABLE` |
| `commonTest` has no `Main` dispatcher | VM tests still run | `Dispatchers.setMain(StandardTestDispatcher())` in VM tests only |
| Organizer/FAQ content not yet delivered | Placeholder content shown | `StaticAppInfoRepository` placeholders + `TODO(content)`; swap values on delivery |
| Loading flicker at first launch | Initial `loading=true` until first emission | VM initial `OverviewUiState()`/`SettingsUiState()`; composer/observer set `loading=false` |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: FR-10 consumes app-generated state (ids, counts, enums) and a bundled info source — no free user input except the boolean toggle (constrained to `0/1`). No injection surface.
- **Auth/Access control**: None in Etapa 1 — anonymous local participant; no tokens (Etapa 2, Keystore/Keychain per stack §4).
- **Sensitive data**: The notifications flag + organizer contact + FAQ stay **on-device** (SQLDelight app-private / bundled). No PII, no upload. Organizer contact is public info, not participant data.
- **Notifications**: Only a **preference** is stored — no device token, no push registration, no platform notification API is touched (delivery is out of scope), so there is no notification-permission or token-handling surface here.
- **Logging**: Napier at debug for state transitions (readiness/sync status, toggle changes); do not log FAQ/contact payloads at info level; never log to persistent files.

---

## 7. ASSUMPTIONS

> User opted out of clarification (`skip questions` / `proceed directly`). Accepted defaults recorded here.

1. **FR-01, FR-03, FR-04, FR-11 are implemented first** and their types/use-cases/repositories are **referenced, not duplicated** (`AppUiState`/`AppStateReducer`/`AppReadiness`/`ContentAvailability`, `RouteSummary`/`RouteRepository.observeRouteSummaries`/`AppPreferencesRepository`, `LogUiState`/`ObserveRunLogUseCase`, `PreparationState`/`ObservePreparationStateUseCase`). If any is unmerged, define the referenced symbol canonically there and fold the coordinated seams into it.
2. **`AppStateReducer.reduce(...)` already takes `(routes, activeRun, selectedRouteId, preparationState, resolver)`** after FR-03 Seam D + FR-11 Step 12. Step 1 wraps that exact call; align to the real signature if it differs. Impact if wrong: adjust `ObserveAppStateUseCase` arity only.
3. **Readiness/active-route are reused from FR-01, not recomputed** — FR-10 reads `AppUiState`. Impact if wrong: overview would diverge from the rest of the app; avoided by Seam A's single derivation.
4. **"Sync status" == offline-package `PreparationStatus`** (Etapa 1). Server sync (Etapa 2) is out of scope. If the organizer wants a distinct label, it is a UI concern over the same field.
5. **Notifications default ON** (`DEFAULT 1`). If the organizer prefers opt-in, change the column default + seed to `0` (one-line change). No delivery is implemented regardless.
6. **Notifications preference lives on FR-03's `AppPreferences`** (Seam B, additive), per FR-03 §12.3 — not a new table or `multiplatform-settings`. Impact if wrong: swapping backends is localized behind `AppPreferencesRepository`.
7. **Organizer contact + FAQ are bundled** (`StaticAppInfoRepository`), not in FR-11's content JSON, because they must exist pre-download and their inclusion in `ContentDto` would be an FR-11 change. The interface is the seam to move them to the content package/resources later.
8. **Final Czech copy/labels/plurals are UI/localization** (Compose resources), deferred — the domain exposes raw counts, enums, and placeholder text.
9. **`SettingsViewModel` is separate from `OverviewViewModel`** — read-only aggregation vs a write-capable settings surface; both back the one Přehled tab in the UI phase.
10. **DB is pre-release** (project-stack §11) so editing `CREATE TABLE` is safe; a `.sqm` migration is the production-safe alternative (Edge Cases).
11. **Only Android + iOS targets** — `Dispatchers.Default` + existing SQLDelight drivers suffice; no new dependencies.

> Open questions live in Section 12.

---

## 8. QUICK REFERENCE

### Files to Modify
- `shared/src/commonMain/sqldelight/com/example/ta33/data/db/AppPreferences.sq` — add `notificationsEnabled` column + `setNotificationsEnabled` (Seam B, FR-03).
- `shared/src/commonMain/kotlin/com/example/ta33/domain/repository/AppPreferencesRepository.kt` — add notifications methods (FR-03).
- `shared/src/commonMain/kotlin/com/example/ta33/data/repository/AppPreferencesRepositoryImpl.kt` — implement them (FR-03).
- `shared/src/commonMain/kotlin/com/example/ta33/presentation/AppViewModel.kt` — consume `ObserveAppStateUseCase` (Seam A, FR-01).
- `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt` — register FR-10 components + re-wire `AppViewModel`.
- `shared/src/commonMain/kotlin/com/example/ta33/di/Koin.kt` — add `overviewViewModel()` / `settingsViewModel()`.

### Files to Create
- `domain/usecase/ObserveAppStateUseCase.kt` (Seam A), `ObserveOverviewUseCase.kt`, `ObserveNotificationsEnabledUseCase.kt`, `SetNotificationsEnabledUseCase.kt`
- `domain/overview/OverviewComposer.kt`
- `domain/model/OverviewUiState.kt` (+ `OverviewProgress`), `OrganizerContact.kt`, `FaqItem.kt`, `SettingsUiState.kt`
- `domain/repository/AppInfoRepository.kt`, `data/repository/StaticAppInfoRepository.kt`
- `presentation/OverviewViewModel.kt`, `presentation/SettingsViewModel.kt`
- `commonTest/…` — `OverviewComposerTest`, `ObserveOverviewUseCaseTest`, `NotificationsPreferenceTest`, `AppInfoRepositoryTest`, `OverviewViewModelTest`, `SettingsViewModelTest`, `ObserveAppStateUseCaseTest` (+ fakes; extend `FakeAppPreferencesRepository`).

### Dependencies
- **None new.** (Already present: SQLDelight 2.1.0 + coroutines-extensions + drivers, Koin 4.1.0, coroutines-core, lifecycle-viewmodel; `kotlinx-coroutines-test` via FR-02.)

### Commands
```bash
./gradlew build                      # compile + regenerate SQLDelight (AppPreferences.notificationsEnabled)
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
| **A. Pure `OverviewComposer` + thin `ObserveOverviewUseCase` over the existing seams; extract `ObserveAppStateUseCase` (Seam A) for one app-state source; notifications as an additive `AppPreferences` column (Seam B); bundled `AppInfoRepository`; separate Overview/Settings VMs** | Reuses every upstream FR with minimal, enumerated seams; table-testable pure composition; single readiness source; no new deps/table/module | Adds one FR-01 refactor (Seam A) + one FR-03 column (Seam B) | ✅ |
| B. **`OverviewViewModel` depends on `AppViewModel`** (VM→VM) to get `AppUiState` | No new use-case | ViewModels depending on ViewModels is an anti-pattern (lifecycle/DI coupling, hard to test); no clean commonMain injection | — |
| C. **Re-derive readiness/active route inside FR-10** from the raw repos + a second `AppStateReducer` collector | No FR-01 touch | Two derivation paths risk drift; duplicates FR-01 logic; contradicts "do not recompute readiness" | — |
| D. **Fold settings into `OverviewViewModel`** (one VM for the whole tab) | One VM | Mixes read-only aggregation with write intent + static info; larger state; harder tests; less cohesive | — |
| E. **Source organizer/FAQ from the FR-11 content package** (add to `ContentDto`) | Editable without app release | Requires an FR-11 DTO change; unavailable before first download (breaks offline-first first launch); over-engineered for low-churn text | — (seam kept for later) |

**Why the selected approach won**: It delivers the aggregated overview and the settings with the smallest set of clearly-flagged seams (one FR-01 refactor, one FR-03 column), keeps the composition pure and fully unit-testable in shared Kotlin with zero new dependencies, reuses FR-01's readiness/active-route as the single source of truth, and keeps organizer/FAQ available offline from first launch while leaving a clean seam to relocate them later.

### 12.2 Open Questions

- [ ] **Should the FAQ/organizer text live in Compose string resources instead of the bundled repo?** — Proposed direction: for the logic phase keep raw data in `StaticAppInfoRepository`; during the UI phase move display copy to Compose resources (keys), and have the impl return resource keys or resolved strings — no caller/interface change. Confirm with the organizer's delivered texts.
- [ ] **Does the overview need the `nextControl` (from FR-04 `LogUiState`) surfaced too?** — Proposed direction: not for the summary; the deník (FR-04 screen) owns "next control". Add a `nextControlName` to `OverviewProgress` only if the Přehled design shows it — additive, no upstream change.
- [ ] **Should `AppInfoRepository` be async (`suspend`/`Flow`) now to future-proof content-package sourcing?** — Proposed direction: keep synchronous getters (static, no I/O); switching to `suspend`/`Flow` later is a localized change behind the interface + a one-line VM adaptation.
- [ ] **Is notifications default ON correct for the organizer?** — Proposed direction: default ON (event reminders), opt-out; flip the column default + seed to `0` if opt-in is preferred. No delivery is built either way.
- [ ] **When notification *delivery* lands (later), where does scheduling live?** — Proposed direction: a separate feature/`expect-actual` platform layer reads this preference; FR-10 stays preference-only. Out of scope here.

### 12.3 Suggestions & Follow-ups

- When the **UI phase** lands, bind the Přehled tab (`TopLevelDestination.PREHLED`) to `OverviewViewModel` + `SettingsViewModel`, and map `syncStatus`/readiness/progress to design-system components; format "2 z 5" and FAQ copy via Compose resources (Czech plurals) — the logic here already yields everything the screen needs.
- Add a **JVM SQLite driver** integration test (`androidHostTest`) for `AppPreferencesRepositoryImpl` notifications persistence against a real DB (beyond fakes) — good coverage, out of scope here.
- Add a Koin **`checkModules()`** test spanning FR-01/02/03/04/11/10 to catch DI-graph breakage early (also flagged by sibling plans).
- Consider a shared **`ProgressFormatter`** in the UI/localization layer (not domain) for "X z Y" pluralization reused across FR-04 and FR-10.
- When **notification delivery** is scheduled as its own feature, it consumes `ObserveNotificationsEnabledUseCase` — keep the preference as the single gate.
- If several one-off preferences accumulate, consider generalizing `AppPreferences` to key/value rows — not needed now (two columns).

> Sections 9 (Design Reference) and 10 (Corrections From Current State) intentionally omitted: this is logic-only work with no UI/visual spec, and it is greenfield FR-10 — the FR-01 `ObserveAppStateUseCase` extraction (Seam A) and the FR-03 `AppPreferences` column (Seam B) are additive/coordinated seams covered in Steps 1–3, not corrections of wrong behavior.
