# FR-04 Deník Kontrol — Canonical Control-State Model & Pure Log Derivation (Logic Only)

> **Summary**: Turn FR-02's provisional log sketch into the real *deník kontrol* by introducing one canonical `ControlPointState` enum (LOCKED / ACTIVE / DONE / FINISH), a pure deterministic `deriveLog(controls, collected, run) -> LogUiState` function (per-control state, `collectedCount`/`totalCount` "2 z 5", `nextControl`, proof-of-completion), and refining FR-02's existing `ObserveRunLogUseCase` + `RunLogViewModel` to expose it as `StateFlow<LogUiState>` — all pure, headless, unit-tested, no UI.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
A TA33 participant needs a *deník* (log) that, at any moment on the trail, shows the state of every control on their route, how many they have collected out of the total (e.g. "2 z 5"), which control is next, and — at the finish — whether they have completed the whole route (the "green" proof of completion). This must be correct purely from on-device data and work with no signal. FR-02 only sketched a rough log (a provisional `State` enum COLLECTED/NEXT/PENDING with no notion of a "finish" state and no single reusable derivation function), which is not the canonical state model the design system defines.

### 1.2 Solution Overview
Consolidate all deník logic into one canonical, pure state model layered on the **existing** FR-02 data foundation and FR-03 ordered-controls seam — no new persistence, no duplicate ViewModel. Introduce a single `ControlPointState` enum (LOCKED/ACTIVE/DONE/FINISH), a pure `ControlLogDeriver.deriveLog(...)` that maps ordered controls + collected controls + the run into a `LogUiState` (per-control state, counts, next control, proof-of-completion), and **refine** FR-02's `ObserveRunLogUseCase` and `RunLogViewModel` to produce/expose it. Everything is deterministic and table-tested in `commonTest`.

### 1.3 Scope: What This IS
- **Canonical enum** (`domain/model`): `ControlPointState { LOCKED, ACTIVE, DONE, FINISH }` — the single logical state model (colors are UI, out of scope).
- **Pure derivation** (`domain/log`): `ControlLogDeriver.deriveLog(controls, collected, run): LogUiState` — deterministic, no repos, no coroutines, no platform APIs.
- **Refined read models** (`domain/model`): `LogUiState` (entries + `collectedCount`/`totalCount` + `nextControl` + `finishState` + `isComplete`/`isRunFinished`) and `RunLogEntry` re-typed to carry `state: ControlPointState` and per-control split time.
- **Refined use-case** (`domain/usecase`): the **same** `ObserveRunLogUseCase` (FR-02) rewired to `combine(...)` FR-03 ordered controls + FR-02 run/collected Flows and delegate to `deriveLog`, emitting `Flow<LogUiState>`.
- **Refined ViewModel** (`presentation`): the **same** `RunLogViewModel` (FR-02) exposing `StateFlow<LogUiState>` (headless — no UI consumes it yet).
- **Proof-of-completion** modeled as a pure derived flag (`isComplete`) from collection completeness, plus the terminal `FINISH` state.
- **Table-driven unit tests** (`commonTest`) over every state combination.

### 1.4 Scope: What This IS NOT
- **No UI whatsoever** — no Compose/SwiftUI screens, no list rows, no colors, no "green screen" visual, no Czech pluralization of "2 z 5" as final copy. UI + localization are a deliberately deferred later phase. Read models expose raw fields (+ a dev-only convenience label).
- **No GPS acquisition / geofence collection flow** — that is **FR-08**. FR-04 only *consumes* the `CollectedControl` rows FR-08 will produce.
- **No QR scanning / timing capture** — that is **FR-09**. FR-04 only *consumes* `RunSession.startedAtMillis`/`finishedAtMillis` + `CollectedControl.collectedAtMillis` already stored by FR-02.
- **No new persistence / schema / repositories** — reuses FR-02 `RunRepository`/`RouteRepository`/`Ta33Database` and FR-03 seams as-is. No new SQLDelight tables.
- **No redefinition of FR-01/FR-02/FR-03** — `Route`, `ControlPoint`, `RunSession`, `CollectedControl`, `observeRouteWithControls`, `ActiveRouteResolver`, `observeRun`, `observeCollected` are **referenced**, not duplicated.
- **No new Gradle modules** — everything lives in `:shared` as package layers (project-stack §12). Base package `com.example.ta33`.
- **No new external dependencies** — `kotlinx-coroutines-test` already added by FR-02.

---

## 2. SUCCESS CRITERIA

Implementation is COMPLETE when ALL criteria are met:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | Project compiles with the canonical enum, pure deriver, refined models/use-case/ViewModel | `./gradlew build` succeeds |
| 2 | There is exactly ONE control-state enum in the codebase (`ControlPointState`); the provisional `RunLogEntry.State` (COLLECTED/NEXT/PENDING) no longer exists | `grep` finds no `COLLECTED`/`NEXT`/`PENDING` control-state references; only `ControlPointState` used |
| 3 | `deriveLog` marks each control `DONE` if collected, `ACTIVE` for the first uncollected control by `ordinal`, `LOCKED` for later uncollected controls | `ControlLogDeriverTest` table cases (nothing / partial / all) |
| 4 | `collectedCount`/`totalCount` are correct and the convenience label renders "2 z 5" for 2-of-5 | `ControlLogDeriverTest` progress cases |
| 5 | `nextControl` is the first uncollected control in ordinal order, and `null` when all collected | `ControlLogDeriverTest` |
| 6 | Order is respected even when controls are collected out of order (e.g. c2 collected before c1) — c1 stays `ACTIVE`, c2 `DONE` | `ControlLogDeriverTest` out-of-order case |
| 7 | Proof-of-completion `isComplete` is `true` iff `totalCount > 0` and all controls collected; `finishState` is `FINISH` when finished, `ACTIVE` when all collected but run not finished, else `LOCKED` | `ControlLogDeriverTest` completion cases |
| 8 | Per-control `splitMillis` = `collectedAtMillis − run.startedAtMillis` when both known, else `null` | `ControlLogDeriverTest` split case |
| 9 | Empty route (0 controls) yields empty entries, `nextControl = null`, `isComplete = false`, `finishState = LOCKED`, "0 z 0", no crash | `ControlLogDeriverTest` empty case |
| 10 | `RunLogViewModel` exposes `StateFlow<LogUiState>`; state recomputes when a collection is added | `RunLogViewModelTest` (`runTest` + fake repos feeding `MutableStateFlow`) |
| 11 | No duplicate ViewModel/use-case introduced — FR-02's `RunLogViewModel`/`ObserveRunLogUseCase` are the ones refined | Code review + `grep` (one class each) |
| 12 | `./gradlew :shared:allTests` green | Run tests |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
        NATIVE UI (later phase — NOT built here)
        reads StateFlow<LogUiState>, calls bind(runId, routeId)
   ═════════════════ SHARED CORE (:shared, commonMain) ═════════════════
                       presentation/
             ┌───────────────────────────────────┐
             │   RunLogViewModel  (FR-02, refined) │  StateFlow<LogUiState>
             └──────────────────┬────────────────┘
                                │ ObserveRunLogUseCase (FR-02, refined)
                 domain/usecase/│
                                │ combine(3 Flows) → deriveLog(...)
        ┌───────────────────────┼───────────────────────────────┐
        │ FR-03 ordered controls│ FR-02 run           FR-02 collected
        │ observeRouteWith-     │ observeRun(runId)   observeCollected(runId)
        │ Controls(routeId)     │                     │
        └───────────────────────┼───────────────────────────────┘
                                │
                       domain/log/
             ┌───────────────────────────────────┐
             │  ControlLogDeriver.deriveLog(...)   │  PURE / deterministic
             │  (no repos, no coroutines)          │  → LogUiState
             └──────────────────┬────────────────┘
                                │ uses
                       domain/model/
             ControlPointState {LOCKED,ACTIVE,DONE,FINISH}
             LogUiState, RunLogEntry (re-typed), ControlPoint, RunSession, CollectedControl
```

**Data flow (open deník):** native layer resolves `RunLogViewModel` (Koin), calls `bind(runId, routeId)` (ids come from FR-01 app state / FR-03 active route) → the VM launches `ObserveRunLogUseCase(runId, routeId)` which `combine`s FR-03 `observeRouteWithControls(routeId)` (controls ordered by `ordinal`) + FR-02 `observeRun(runId)` + FR-02 `observeCollected(runId)` and feeds the three into the **pure** `ControlLogDeriver.deriveLog(...)`, producing a `LogUiState`. When FR-08 later inserts a `CollectedControl`, the collected Flow re-emits → `deriveLog` recomputes → `StateFlow<LogUiState>` updates. No polling, no UI.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| State model | **One canonical enum** `ControlPointState { LOCKED, ACTIVE, DONE, FINISH }` in `domain/model` | The design system names these four as the key control concept; a single top-level enum prevents drift. Replaces FR-02's provisional `RunLogEntry.State` |
| Derivation home | **Pure object** `ControlLogDeriver.deriveLog(...)` in `domain/log` (no repos/coroutines) | Mirrors FR-03's pure `ActiveRouteResolver`; trivially unit-testable, deterministic, table-driven |
| Where log logic lives (reconciliation) | **Refine FR-02's `ObserveRunLogUseCase` + `RunLogViewModel` in place** — no new/parallel classes | Task mandate: unify the deník into FR-02's sketch, don't duplicate. The use-case swaps its inline assembly for `deriveLog` |
| `LogUiState` location | **`domain/model`** (a domain read model), not `presentation` | So the pure `deriveLog` can *return* it without depending on the presentation layer; the ViewModel exposes it directly. Refines/replaces FR-02's `presentation`-local `RunLogUiState` |
| Ordered controls source | **Consume FR-03** `RouteRepository.observeRouteWithControls(routeId)` (already `ORDER BY ordinal`) | FR-03 owns ordering; FR-04 re-sorts only defensively inside `deriveLog` |
| ACTIVE selection | **First uncollected control by `ordinal`** (not GPS proximity) | Deník is order-based; proximity "next" is a map concern (FR-06), addable later with no model change |
| `FINISH` semantics | Terminal state of a conceptual finish step: `LOCKED` (controls remaining) → `ACTIVE` (all collected, run not finished) → `FINISH` (run finished) | Gives all four enum values meaning; separates "collected all" from "officially finished" (QR, FR-09) cleanly |
| Proof-of-completion | Derived flag `isComplete = totalCount > 0 && collectedCount == totalCount` (collection completeness) | zadani: deník proves *projití celé trasy* (all controls collected). Finish-QR timing is surfaced separately as `isRunFinished`, not the proof |
| Split time | `splitMillis = collectedAtMillis − run.startedAtMillis` when both present, else `null` | Consumes FR-02/FR-09 timestamps; no timing capture here |
| `collected` param type | `List<CollectedControl>` (has `controlId` + `collectedAtMillis`) | Needed for split times; pure function builds a `controlId → collectedAt` map internally |
| Progress label | Raw `collectedCount`/`totalCount` are canonical; a convenience `progressLabel` ("2 z 5") is a dev aid only | Final Czech pluralization/units are UI/localization (deferred), consistent with FR-03's stance |
| ViewModel style | Keep FR-02's `MutableStateFlow` + `asStateFlow`, `viewModelScope`, `bind(runId, routeId)` | Consistency with FR-01/02/03 ViewModels |
| DI | **No new Koin registration** — FR-02 already registers `ObserveRunLogUseCase` + `RunLogViewModel`; `ControlLogDeriver` is a stateless `object` (no injection) | Minimal surface; nothing new to wire |

---

## 4. IMPLEMENTATION STEPS

> Execute steps in order. Do not skip. Paths under `shared/src/commonMain/kotlin/com/example/ta33/` unless stated. Base package `com.example.ta33`.
> **Dependency ordering**: assumes **FR-02 implemented** (`RunSession`, `CollectedControl`, `RunRepository.observeRun`/`observeCollected`, `ObserveRunLogUseCase`, `RunLogViewModel`, `appModule`, `kotlinx-coroutines-test`) and **FR-03 implemented** (`RouteRepository.observeRouteWithControls`). If FR-02/FR-03 are not yet merged, define these types canonically here and fold the refinements into those FRs. No new external dependencies.

### Step 1: Add the canonical `ControlPointState` enum
**Goal**: One logical state model for the whole app.
**Files**: `domain/model/ControlPointState.kt` (new)

```kotlin
package com.example.ta33.domain.model

/**
 * Canonical logical state of a control point in the deník (FR-04).
 * Colors/visuals are a UI concern (design system) and are NOT defined here.
 *
 * - LOCKED: uncollected and not the next control in ordinal order (a later control).
 * - ACTIVE: the next control to collect (first uncollected by ordinal); also the finish
 *           step once all controls are collected but the run is not yet finished.
 * - DONE:   collected.
 * - FINISH: terminal state of the finish step once the run is finished (FR-09 QR).
 */
enum class ControlPointState { LOCKED, ACTIVE, DONE, FINISH }
```
**Done when**: File compiles.

---

### Step 2: Re-type `RunLogEntry` to the canonical state (refine FR-02)
**Goal**: Remove the provisional nested `State` enum; carry `ControlPointState` + split time.
**Files**: `domain/model/RunLogEntry.kt` (edit, FR-02)

```kotlin
package com.example.ta33.domain.model

/** One deník row: a control plus its derived state and (optional) split time. */
data class RunLogEntry(
    val control: ControlPoint,
    val state: ControlPointState,       // was RunLogEntry.State (COLLECTED/NEXT/PENDING) — replaced
    val collectedAtMillis: Long? = null, // null unless DONE
    val splitMillis: Long? = null,       // collectedAt - run.startedAt, null unless both known
)
```
> Delete the old nested `enum class State { COLLECTED, NEXT, PENDING }`. Mapping for readers: `COLLECTED → DONE`, `NEXT → ACTIVE`, `PENDING → LOCKED` (see Section 10).

**Done when**: File compiles; no reference to the old nested `State` remains.

---

### Step 3: Add the refined `LogUiState` read model (domain)
**Goal**: The single state object the deriver returns and the ViewModel exposes; absorbs FR-02's `RunProgress`.
**Files**: `domain/model/LogUiState.kt` (new)

```kotlin
package com.example.ta33.domain.model

/**
 * Full deník state (FR-04). Pure read model returned by ControlLogDeriver.deriveLog(...)
 * and exposed by RunLogViewModel as StateFlow<LogUiState>. No UI/formatting concerns.
 */
data class LogUiState(
    val entries: List<RunLogEntry> = emptyList(), // one per control, ordered by ordinal
    val collectedCount: Int = 0,                  // e.g. 2
    val totalCount: Int = 0,                      // e.g. 5
    val nextControl: ControlPoint? = null,        // first uncollected by ordinal, null if all done
    val finishState: ControlPointState = ControlPointState.LOCKED,
    val isComplete: Boolean = false,              // proof of completion: all controls collected
    val isRunFinished: Boolean = false,           // finish QR scanned (FR-09), consumed only
    val loading: Boolean = true,                  // VM sets false after first emission
) {
    /** Dev convenience only. Final pluralization/units are a UI/localization concern (deferred). */
    val progressLabel: String get() = "$collectedCount z $totalCount" // e.g. "2 z 5"
}
```
> This **replaces** FR-02's `presentation`-local `RunLogUiState` and **absorbs** FR-02's `RunProgress` (counts move here). Remove `domain/model/RunProgress.kt` if present (see Section 10) — or keep it only if referenced elsewhere; there should be no such reference.

**Done when**: File compiles.

---

### Step 4: Add the pure `ControlLogDeriver` (heart of FR-04)
**Goal**: Deterministic derivation of the full deník state from raw inputs.
**Files**: `domain/log/ControlLogDeriver.kt` (new)

```kotlin
package com.example.ta33.domain.log

import com.example.ta33.domain.model.*

/**
 * PURE, deterministic deník derivation. No repositories, no coroutines, no platform APIs.
 * Single source of truth for the control-state model (FR-04).
 */
object ControlLogDeriver {

    /**
     * @param controls  route controls (expected ordered by ordinal; sorted defensively here)
     * @param collected collected controls for this run (FR-08 output; may be empty / out of order)
     * @param run       the run session, or null if not started (FR-02/FR-09 timing consumed only)
     */
    fun deriveLog(
        controls: List<ControlPoint>,
        collected: List<CollectedControl>,
        run: RunSession?,
    ): LogUiState {
        val ordered = controls.sortedWith(compareBy({ it.ordinal }, { it.id })) // defensive, stable
        val collectedByControl = collected.associateBy { it.controlId }         // last-wins if dup
        val startedAt = run?.startedAtMillis

        // First uncollected control by ordinal = ACTIVE; later uncollected = LOCKED.
        val firstUncollectedId = ordered.firstOrNull { it.id !in collectedByControl }?.id

        val entries = ordered.map { control ->
            val hit = collectedByControl[control.id]
            val state = when {
                hit != null            -> ControlPointState.DONE
                control.id == firstUncollectedId -> ControlPointState.ACTIVE
                else                   -> ControlPointState.LOCKED
            }
            val collectedAt = hit?.collectedAtMillis
            RunLogEntry(
                control = control,
                state = state,
                collectedAtMillis = collectedAt,
                splitMillis = if (collectedAt != null && startedAt != null) collectedAt - startedAt else null,
            )
        }

        val total = ordered.size
        val collectedCount = ordered.count { it.id in collectedByControl }
        val isComplete = total > 0 && collectedCount == total
        val isRunFinished = run?.finishedAtMillis != null
        val nextControl = ordered.firstOrNull { it.id == firstUncollectedId }

        val finishState = when {
            isRunFinished -> ControlPointState.FINISH
            isComplete    -> ControlPointState.ACTIVE  // all collected, head to finish (scan QR)
            else          -> ControlPointState.LOCKED
        }

        return LogUiState(
            entries = entries,
            collectedCount = collectedCount,
            totalCount = total,
            nextControl = nextControl,
            finishState = finishState,
            isComplete = isComplete,
            isRunFinished = isRunFinished,
            loading = false,
        )
    }
}
```
**Done when**: Compiles; holds no repo/coroutine/platform reference.

---

### Step 5: Refine `ObserveRunLogUseCase` to delegate to the deriver (FR-02, in place)
**Goal**: Rewire the *existing* use-case to `combine` the FR-02/FR-03 Flows and emit `Flow<LogUiState>` via `deriveLog`. Do **not** create a new use-case.
**Files**: `domain/usecase/ObserveRunLogUseCase.kt` (edit, FR-02)

```kotlin
package com.example.ta33.domain.usecase

import com.example.ta33.domain.log.ControlLogDeriver
import com.example.ta33.domain.model.LogUiState
import com.example.ta33.domain.repository.RouteRepository
import com.example.ta33.domain.repository.RunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveRunLogUseCase(
    private val routes: RouteRepository,
    private val runs: RunRepository,
) {
    operator fun invoke(runId: String, routeId: String): Flow<LogUiState> =
        combine(
            routes.observeRouteWithControls(routeId), // FR-03 seam: Route? with controls by ordinal
            runs.observeRun(runId),                    // FR-02
            runs.observeCollected(runId),              // FR-02
        ) { route, run, collected ->
            ControlLogDeriver.deriveLog(
                controls = route?.controls.orEmpty(),
                collected = collected,
                run = run,
            )
        }
}
```
> Return type changes from FR-02's provisional `Flow<Pair<List<RunLogEntry>, RunProgress>>` to `Flow<LogUiState>`. Constructor deps (`RouteRepository`, `RunRepository`) are unchanged from FR-02, so the existing `appModule` registration `factory { ObserveRunLogUseCase(get(), get()) }` still resolves. If FR-02 wired only `RouteRepository`, add `RunRepository` (already a Koin single).

**Done when**: Compiles; the use-case emits `LogUiState`.

---

### Step 6: Refine `RunLogViewModel` to expose `StateFlow<LogUiState>` (FR-02, in place)
**Goal**: Update the *existing* ViewModel to the canonical state; no parallel ViewModel.
**Files**: `presentation/RunLogViewModel.kt` (edit, FR-02)

```kotlin
package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.domain.model.LogUiState
import com.example.ta33.domain.usecase.ObserveRunLogUseCase
import kotlinx.coroutines.flow.*

class RunLogViewModel(
    private val observeRunLog: ObserveRunLogUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LogUiState()) // loading = true, empty
    val state: StateFlow<LogUiState> = _state.asStateFlow()

    /** Bind to a concrete run + route. runId/routeId come from FR-01 app state / FR-03 active route. */
    fun bind(runId: String, routeId: String) {
        observeRunLog(runId, routeId)
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }
}
```
> Delete FR-02's local `RunLogUiState` data class (now `LogUiState` in `domain/model`). `appModule` registration `factory { RunLogViewModel(get()) }` is unchanged.

**Done when**: Compiles.

---

### Step 7: Verify Koin + Swift accessor (no new registration expected)
**Goal**: Confirm the refined types still resolve; expose a Swift accessor if FR-02 didn't.
**Files**: `di/AppModule.kt` (verify), `di/Koin.kt` (verify/add `runLogViewModel()` if missing)

- Confirm `appModule` still contains `factory { ObserveRunLogUseCase(get(), get()) }` and `factory { RunLogViewModel(get()) }` from FR-02 (arities unchanged). `ControlLogDeriver` is an `object` — **no** registration.
- If `di/Koin.kt` lacks a Swift `ViewModelProvider` accessor, add (mirrors FR-03):
```kotlin
fun runLogViewModel(): RunLogViewModel = KoinPlatform.getKoin().get()
```
**Done when**: `./gradlew build` passes; Koin resolves; iOS `Shared` framework compiles.

---

### Step 8: Table-driven unit tests
**Goal**: Lock every state combination of the pure deriver + a ViewModel reactivity test.
**Files**: under `shared/src/commonTest/kotlin/com/example/ta33/`
- `ControlLogDeriverTest.kt` (new) — table-driven; each row: input controls (ordinals), collected control ids (+ timestamps), run (started/finished), expected per-entry states, `collectedCount`/`totalCount`, `nextControl` id, `isComplete`, `finishState`, split values. Cases:
  1. **Nothing collected** (5 controls) → `[ACTIVE, LOCKED, LOCKED, LOCKED, LOCKED]`, "0 z 5", next = c1, `isComplete=false`, `finishState=LOCKED`.
  2. **Partial** (2 of 5, c1+c2) → `[DONE, DONE, ACTIVE, LOCKED, LOCKED]`, "2 z 5", next = c3, `finishState=LOCKED`.
  3. **All collected, run not finished** → `[DONE×5]`, "5 z 5", next = `null`, `isComplete=true`, `finishState=ACTIVE`.
  4. **All collected, run finished** (finishedAt set) → same entries, `isRunFinished=true`, `finishState=FINISH`.
  5. **Out-of-order** (c2 collected, c1 not) → c1 `ACTIVE`, c2 `DONE`, rest `LOCKED`; next = c1.
  6. **Empty route** (0 controls) → `entries=[]`, "0 z 0", next=`null`, `isComplete=false`, `finishState=LOCKED`, no crash.
  7. **Split time** — c1 collected at start+120000, run started → entry `splitMillis == 120000`; when `run == null` → `splitMillis == null`.
  8. **Unsorted input** — controls passed in shuffled ordinal order → entries come back sorted by ordinal (defensive sort).
- `RunLogViewModelTest.kt` (new/refined) — `runTest` + `Dispatchers.setMain(StandardTestDispatcher())`, fake `RouteRepository`/`RunRepository` backed by `MutableStateFlow`: after `bind(runId, routeId)` state reflects derived `LogUiState`; adding a `CollectedControl` to the fake collected Flow flips the right entry to `DONE` and advances `nextControl`; `loading` is `false` after first emission.

> Prefer fakes over a real DB driver (mirrors FR-02/FR-03). Reuse FR-02/FR-03 fakes if present.

**Done when**: `./gradlew :shared:allTests` is green.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| Route has 0 controls | "0 z 0", empty entries, `isComplete=false`, `finishState=LOCKED`, no crash | `total > 0` guard on `isComplete`; `firstOrNull` yields `null` next |
| Controls collected out of order (c2 before c1) | c1 stays `ACTIVE`, c2 `DONE`; next = c1 | ACTIVE = first uncollected by ordinal, independent of which were collected |
| Duplicate collected rows for one control | Treated as one `DONE`; no double count | `associateBy { controlId }` collapses; `count` over controls, not rows |
| Collected row referencing an unknown control id (stale content) | Ignored for state/count | Iteration is over route `controls`; orphan collected ids don't appear |
| Run not started (`run == null` or `startedAtMillis == null`) | States still derived; `splitMillis = null` | Split computed only when both timestamps present |
| All controls collected but finish QR not scanned | `isComplete=true` (proof), `finishState=ACTIVE`, `isRunFinished=false` | Completeness (proof) decoupled from finish timing (FR-09) |
| Run finished but not all controls collected (bad QR order) | `isRunFinished=true`, `isComplete=false`, `finishState=FINISH` | Flags independent; deník shows incomplete collection honestly |
| Controls with equal/duplicate `ordinal` | Deterministic order | `compareBy(ordinal, id)` stable tiebreak |
| Content re-downloaded mid-view (FR-11) | Deník refreshes live | Upstream Flows (FR-03 detail, FR-02 collected) are reactive; `combine` re-emits |
| Clock skew makes `splitMillis` negative | Surfaced as-is (negative) | Not corrected in Etapa 1; noted for Etapa 2 monotonic timing (FR-02 §12) |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: All inputs are app-generated (controls from content, collected rows from FR-08, run from FR-02). `runId`/`routeId` arrive from FR-01 nav args, not free user input. `deriveLog` is total (no throws) and tolerates empty/orphan/duplicate inputs.
- **Auth/Access control**: None in Etapa 1 — anonymous local participant; no tokens (Etapa 2, Keystore/Keychain per stack §4).
- **Sensitive data**: The deník exposes collection timestamps/splits (personal data) but stays **on-device only** — pure in-memory derivation over local SQLDelight data; no upload in Etapa 1.
- **Logging**: Napier at debug for derivation summaries only (counts/state transitions). Do **not** log full timestamp/split streams at info level; never log to persistent files.

---

## 7. ASSUMPTIONS

> User opted out of clarification (`skip questions` / `proceed directly`). Accepted defaults recorded here.

1. **FR-02 and FR-03 are implemented first** and their types are **referenced, not duplicated** (`RunSession`, `CollectedControl`, `RunRepository.observeRun`/`observeCollected`, `RouteRepository.observeRouteWithControls`, `ObserveRunLogUseCase`, `RunLogViewModel`, `appModule`, `kotlinx-coroutines-test`). If unmerged: define the canonical types here and fold the refinements into those FRs.
2. **"NEXT" is order-based** (first uncollected by `ordinal`), not GPS proximity. Proximity "next" is a map concern (FR-06) and can be layered later without a model change.
3. **Proof-of-completion = all controls collected** (`isComplete`), matching zadani's *důkaz projití celé trasy*. Finish-QR timing (`isRunFinished`, FR-09) is surfaced but is not the proof. If the organizer wants proof to require the finish QR too, tighten to `isComplete && isRunFinished` in one place.
4. **`FINISH` models a conceptual finish step** (LOCKED → ACTIVE → FINISH), not a real DB control row. No synthetic control is persisted; it is a derived `finishState` field. If a visible finish *row* is later wanted, append a synthetic entry in the UI/VM layer — no schema change.
5. **`collected` is `List<CollectedControl>`** (needs `collectedAtMillis` for splits). A convenience `Set<String>` overload is unnecessary and omitted.
6. **The "2 z 5" label is a dev convenience**; final Czech pluralization/units are UI/localization (deferred), consistent with FR-03.
7. **`LogUiState` lives in `domain/model`** so the pure deriver can return it; it replaces FR-02's `presentation`-local `RunLogUiState` and absorbs `RunProgress`.
8. **Only Android + iOS targets** — pure Kotlin, no dispatcher/platform concerns in the deriver; `Dispatchers.setMain` used only in the VM test.
9. **`bind(runId, routeId)` stays the entry point**; wiring the *active* run/route is FR-01's responsibility (see Open Questions).

> Open questions live in Section 12.

---

## 8. QUICK REFERENCE

### Files to Modify
- `shared/src/commonMain/kotlin/com/example/ta33/domain/model/RunLogEntry.kt` — re-type `state` to `ControlPointState`; drop nested `State`; add `splitMillis`.
- `shared/src/commonMain/kotlin/com/example/ta33/domain/usecase/ObserveRunLogUseCase.kt` — `combine(FR-03 controls, FR-02 run, FR-02 collected)` → `deriveLog` → `Flow<LogUiState>`.
- `shared/src/commonMain/kotlin/com/example/ta33/presentation/RunLogViewModel.kt` — expose `StateFlow<LogUiState>`; delete local `RunLogUiState`.
- `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt` — verify existing registrations still resolve (no change expected).
- `shared/src/commonMain/kotlin/com/example/ta33/di/Koin.kt` — add `runLogViewModel()` Swift accessor if missing.
- *(Remove)* `shared/src/commonMain/kotlin/com/example/ta33/domain/model/RunProgress.kt` — absorbed into `LogUiState` (only if unreferenced elsewhere).

### Files to Create
- `domain/model/ControlPointState.kt` — canonical `LOCKED/ACTIVE/DONE/FINISH` enum.
- `domain/model/LogUiState.kt` — refined deník state read model.
- `domain/log/ControlLogDeriver.kt` — pure `deriveLog(...)`.
- `commonTest/…/ControlLogDeriverTest.kt` — table-driven state tests.
- `commonTest/…/RunLogViewModelTest.kt` — reactive VM test (new or refined from FR-02).

### Dependencies
- **None new.** (Already present via FR-02: SQLDelight 2.1.0 + coroutines-extensions + drivers, Koin 4.1.0, coroutines-core, lifecycle-viewmodel, `kotlinx-coroutines-test`.)

### Commands
```bash
./gradlew build                      # compile shared + apps
./gradlew :shared:allTests           # shared unit tests (deriver + VM)
./gradlew :androidApp:assembleDebug  # Android sanity
# iOS headless (compiles Swift accessor into Shared):
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'id=<simulator-id>' CODE_SIGNING_ALLOWED=NO build
```

---

## 10. CORRECTIONS FROM CURRENT STATE

> Applies **if FR-02 is already implemented**. If FR-02 is unmerged, adopt the "After" shape from the start and fold into FR-02.

| What | Before (FR-02 provisional) | After (FR-04 canonical) |
|------|----------------------------|-------------------------|
| Control state enum | `RunLogEntry.State { COLLECTED, NEXT, PENDING }` (nested, 3 values, no finish) | Top-level `ControlPointState { LOCKED, ACTIVE, DONE, FINISH }` (1 canonical enum) |
| State mapping | `COLLECTED` / `NEXT` / `PENDING` | `DONE` / `ACTIVE` / `LOCKED` (+ new `FINISH`) |
| Log derivation | Inline assembly inside `ObserveRunLogUseCase` | Pure `ControlLogDeriver.deriveLog(...)` in `domain/log` |
| Use-case output | `Flow<Pair<List<RunLogEntry>, RunProgress>>` | `Flow<LogUiState>` (single read model) |
| Progress holder | `RunProgress(collected, total)` with `.label` | Absorbed into `LogUiState.collectedCount`/`totalCount` (+ dev `progressLabel`) |
| ViewModel state | `presentation`-local `RunLogUiState(entries, progress, loading)` | `domain/model` `LogUiState` (adds `nextControl`, `finishState`, `isComplete`, `isRunFinished`) |
| Finish / proof concept | Absent | `isComplete` (proof) + `finishState` (`FINISH`) + `isRunFinished` |
| Controls source | (FR-02 loaded controls directly) | Consumes FR-03 `observeRouteWithControls` (ordered by ordinal) |

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
| **A. Canonical `ControlPointState` enum + pure `deriveLog` returning a domain `LogUiState`; refine FR-02's use-case + ViewModel in place** | One state model, one derivation, no duplicate classes; trivially table-testable; consumes FR-02/FR-03 seams; no new deps/schema | Touches (refines) three FR-02 files | ✅ |
| B. **New parallel `CheckpointLogViewModel`/`DerivedLogUseCase`** alongside FR-02's | No edits to FR-02 files | Two ViewModels/use-cases for one deník → drift, duplicated wiring; violates the reconciliation mandate | — |
| C. **Keep derivation inline in the use-case** (no `ControlLogDeriver`) | Fewer files | Not unit-testable without coroutines/fakes; couples pure logic to Flow plumbing; harder table tests | — |
| D. **Persist the derived state** (a `LogState` table) | Cheap reads | Redundant with source-of-truth collected rows; risks staleness; needless schema/migration; deník is a pure projection | — |

**Why the selected approach won**: A single canonical enum + one pure, deterministic function gives the deník exactly one source of truth, satisfies the "unify, don't duplicate" mandate by refining FR-02's existing use-case/ViewModel, and stays fully unit-testable in shared Kotlin with zero new dependencies, schema, or UI.

### 12.2 Open Questions

- [ ] **Should `bind(...)` auto-resolve the active run + route** (via FR-01 `ActiveRouteResolver` + FR-02 active-run) instead of taking explicit ids? — Proposed direction: keep explicit `bind(runId, routeId)` (FR-01 supplies them from app state); add a `bindActive()` convenience only if the UI phase shows it's needed, to avoid `RunRepository`/resolver coupling in FR-04.
- [ ] **Does proof-of-completion require the finish QR (FR-09) too, or just all controls collected?** — Proposed direction: proof = all controls collected (`isComplete`); expose `isRunFinished` separately. Flip to `isComplete && isRunFinished` in the single `finishState`/proof site if the organizer requires the finish scan for the "green" proof.
- [ ] **Should the finish be a visible synthetic log row (`FINISH` entry) or only a `finishState` field?** — Proposed direction: field only for now; if the design wants a finish row in the list, append a synthetic entry in the UI/VM layer — no domain/schema change.
- [ ] **Is a Czech-pluralized progress label ("2 z 5" vs "2 z 5 kontrol") needed in the domain?** — Proposed direction: no; keep raw counts canonical, format in UI/localization (Compose resources) during the UI phase.

### 12.3 Suggestions & Follow-ups

- When the **UI phase** lands, map `ControlPointState` → design-system colors and the `isComplete` proof → the "green" finish screen; the logic here already yields everything the screen needs.
- When **FR-08** lands (GPS collection), it only needs to insert `CollectedControl` rows — the deník updates reactively with no FR-04 change.
- When **FR-09** lands (QR timing), setting `RunSession.finishedAtMillis` automatically flips `finishState` to `FINISH`; no FR-04 change.
- Add a **JVM SQLite driver** integration test (`androidHostTest`) asserting `deriveLog` over real `observeRouteWithControls` + `observeCollected` end-to-end — good coverage, out of scope here.
- Consider a shared **`ProgressFormatter`** in the UI/localization layer (not domain) for "X z Y" pluralization across FR-04/FR-10.

> Section 9 (Design Reference) omitted: this is logic-only work with no UI/visual spec (design-system colors are explicitly out of scope). Section 10 included: FR-04 refines/corrects FR-02's provisional log sketch.
