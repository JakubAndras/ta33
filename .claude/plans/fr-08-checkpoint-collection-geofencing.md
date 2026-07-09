# FR-08 Sběr Kontrol Přes GPS — Geofence Proximity Detection & Collection Orchestration (Logic Only)

> **Summary**: Build the shared, headless logic that offers a checkpoint for collection once the participant walks into its ~50 m radius — a pure accuracy-aware, hysteresis-guarded `ProximityEvaluator`, an `ObserveCollectionCandidateUseCase` that folds one shared GPS stream (FR-05) + ordered controls (FR-03) + collected rows/run (FR-02) into a `Flow<CollectionCandidate?>`, and a headless `ControlCollectionViewModel` whose `confirm()` writes through FR-02's existing `CollectControlUseCase` — all offline, unit-tested, with NO UI and NO confirmation ("green") screen.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
On the TA33 trail a participant collects a checkpoint not by scanning a printed QR (which can be lost or abused) but by physically arriving at it: once inside roughly a 50 m circle, the app should *offer* to collect that control, and the participant confirms with a tap. It has to work with no signal, and — critically — GPS in the rock canyons of Adršpach/Teplice is noisy, so the offer must not flicker on and off as the fix jitters across the radius boundary. Today nothing turns a live GPS position + the route's controls into a "this control is in range, offer it" decision.

### 1.2 Solution Overview
Add a thin FR-08 layer over the existing shared core: a **pure** `ProximityEvaluator` (accuracy gate + enter/exit hysteresis + a short consecutive-fix debounce) that decides which uncollected control is the collection **candidate**; an `ObserveCollectionCandidateUseCase` that `combine`s the **single shared** `LocationStream` (FR-05) with FR-03 ordered controls and FR-02 collected/run Flows, holds the cross-emission hysteresis state, and emits `Flow<CollectionCandidate?>`; and a headless `ControlCollectionViewModel` exposing the candidate + permission status, whose explicit `confirm()` calls FR-02's **existing** `CollectControlUseCase` to persist the `CollectedControl`. Collection is **any-order** and **manual** (the app offers; the participant taps). No new GPS source, no new persistence, no UI.

### 1.3 Scope: What This IS
- **Config model** (`domain/model`): `GeofenceConfig` (max-accuracy gate, exit margin, min consecutive fixes) — field-tunable.
- **Candidate read model** (`domain/model`): `CollectionCandidate` (the control the app would offer + distance + fix accuracy + the position location that produced it).
- **Pure evaluator** (`domain/geo`): `ProximityEvaluator` — deterministic `evaluate(previousState, position, controls, collectedIds)` reusing FR-02 `GeoUtils.distanceMeters`; accuracy gate, enter/exit hysteresis, N-consecutive-fix debounce; caller holds the cross-emission `ProximityState`.
- **Observation use-case** (`domain/usecase`): `ObserveCollectionCandidateUseCase(runId, routeId)` — folds FR-05 `LocationStream.positions()` + FR-03 `observeRouteWithControls` + FR-02 `observeCollected`/`observeRun` through the evaluator → `Flow<CollectionCandidate?>`. Gates on an **active run**.
- **Write path**: **REUSE** FR-02 `CollectControlUseCase(runId, controlId, location)` → `CollectResult`. No rewrite.
- **Headless `ControlCollectionViewModel`** (`presentation`): `StateFlow<ControlCollectionUiState>` (permission, candidate, collecting flag, last outcome) + `bind(runId, routeId)` + `confirm()`. Manual confirmation only.
- **Koin registration** (`di/AppModule`) + Swift `ViewModelProvider.controlCollectionViewModel()`.
- **Unit tests** (`commonTest`) with a **fake location Flow** + fake repos: evaluator (pure), use-case (scripted stream), ViewModel.

### 1.4 Scope: What This IS NOT
- **No UI whatsoever** — no Compose/SwiftUI, no "green" confirmation screen, no map overlay, no permission dialogs. UI is a deliberately deferred later phase. The ViewModel is headless; rendering `justCollected` as the green screen is that phase's job.
- **No new GPS acquisition** — FR-08 **reuses** FR-05's `LocationStream` (one shared GPS subscription for breadcrumb + geofence). It creates **no** `LocationProvider` and no second GPS listener (battery-critical on a multi-hour route).
- **No new persistence / schema / repository** — the write goes through FR-02's `CollectControlUseCase` → `RunRepository.addCollected` → existing `CollectedControl` table. No new SQLDelight tables.
- **No change to FR-04** — the deník updates automatically off the reactive `observeCollected` Flow after a write; FR-08 touches no FR-04 code.
- **No QR / timing capture** — that is FR-09. FR-08 only writes `CollectedControl.collectedAtMillis` (the checkpoint *mezičas*), which FR-09 consumes.
- **No permission-request UX** — only the FR-05 permission **status** is surfaced; requesting/dialogs are UI-phase.
- **No auto-collect** — zadani: the app *offers*, the participant *taps*. The write happens only on explicit `confirm()`.
- **No server sync** — Etapa 1 on-device only; `syncStatus` stays `PENDING` (project-stack §10).
- **No new Gradle modules / no new external dependencies** — everything lives in `:shared` as package layers (project-stack §12).

---

## 2. SUCCESS CRITERIA

Implementation is COMPLETE when ALL criteria are met:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | Project compiles with `GeofenceConfig`, `CollectionCandidate`, `ProximityEvaluator`, `ObserveCollectionCandidateUseCase`, `ControlCollectionViewModel` | `./gradlew build` succeeds |
| 2 | `ProximityEvaluator` is pure (no coroutines/I/O), reuses FR-02 `GeoUtils.distanceMeters` (no re-implemented haversine) | Code review + `ProximityEvaluatorTest` |
| 3 | A fix inside a control's `radiusMeters` (after debounce) yields that control as the candidate; a fix clearly outside yields `null` | `ProximityEvaluatorTest` in/out cases |
| 4 | A fix with `accuracyMeters > GeofenceConfig.maxAccuracyMeters` never *promotes* a new candidate; it holds the current one (no flip on a bad fix) | `ProximityEvaluatorTest` poor-accuracy case |
| 5 | Hysteresis: once offered, a control stays the candidate until distance exceeds `radius + exitMargin`; a fresh control is offered only at `distance <= radius` after `minConsecutiveFixes` — no flicker at the boundary | `ProximityEvaluatorTest` enter/exit + boundary-jitter cases |
| 6 | Any-order collection: a control physically reached out of ordinal order becomes a candidate even if an earlier-ordinal control is uncollected | `ProximityEvaluatorTest` any-order case |
| 7 | Already-collected controls are never offered; when all are collected the candidate is `null` | `ProximityEvaluatorTest` / use-case test |
| 8 | `ObserveCollectionCandidateUseCase` emits candidates only when the run is active (started, not finished); otherwise `null` | `ObserveCollectionCandidateUseCaseTest` run-state cases |
| 9 | `ObserveCollectionCandidateUseCase` uses the **shared** `LocationStream.positions()` (no new provider) and reacts to controls/collected/run changes | Code review + scripted-stream test |
| 10 | `ControlCollectionViewModel.confirm()` calls FR-02 `CollectControlUseCase` with the candidate id + current location and reflects `CollectResult` as `justCollected`/`alreadyCollected`/`outOfRange` | `ControlCollectionViewModelTest` |
| 11 | No FR-04 change required; a collect write flips the deník via `observeCollected` (already covered by FR-04) | Code review (no FR-04 diff) |
| 12 | All new components resolvable via Koin; Swift `controlCollectionViewModel()` compiles into `Shared` | Koin resolution/app launch; `xcodebuild` |
| 13 | `./gradlew :shared:allTests` green | Run tests |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
        NATIVE UI (later phase — NOT built here; "green" confirm screen = deferred UI)
        reads ControlCollectionUiState (candidate, permission, lastResult); calls bind()/confirm()
   ═════════════════ SHARED CORE (:shared, commonMain) ═════════════════
                       presentation/
             ┌───────────────────────────────────────┐
             │      ControlCollectionViewModel         │  StateFlow<ControlCollectionUiState>
             │      (headless, no UI, manual confirm)  │  (permission, candidate, isCollecting, lastResult)
             └───────┬───────────────────────┬────────┘
        observes     │                       │ confirm() → write
        candidate +  │                       │
        permission   │                       ▼
                     │            domain/usecase/  (REUSED, FR-02)
                     │            ┌────────────────────────────────┐
                     │            │ CollectControlUseCase(runId,     │ → CollectResult
                     │            │   controlId, location: GeoPoint?)│   (Collected / AlreadyCollected /
                     │            └───────────────┬─────────────────┘    OutOfRange / UnknownControl)
        domain/usecase/                           │ RunRepository.addCollected (INSERT OR IGNORE)
        ┌────────────────────────────────────────┴───────────────┐
        │ ObserveCollectionCandidateUseCase(runId, routeId)         │  Flow<CollectionCandidate?>
        │  combine( positions, controls, collected, run )           │
        │  .scan(ProximityState) { evaluator.evaluate(...) }        │
        │  run-active gate; distinctUntilChanged                    │
        └───┬─────────────┬──────────────┬───────────────┬─────────┘
   FR-05    │        FR-03 │         FR-02│           FR-02│
  LocationStream    observeRoute-   observeCollected   observeRun
  .positions()      WithControls    (runId)            (runId)
  (SHARED GPS)      (routeId)          │                  │
        │                              ▼                  ▼
        │                        Ta33Database (FR-02 CollectedControl / RunSession)
        ▼
   domain/geo/ (PURE)
   ┌──────────────────────────────┐        ┌───────────────────────────────┐
   │ ProximityEvaluator            │──uses──▶ GeoUtils.distanceMeters (FR-02,│
   │ evaluate(prevState, pos,      │        │ reused — clean haversine, tested)│
   │   controls, collectedIds)     │        └───────────────────────────────┘
   │ accuracy gate + hysteresis +  │
   │ N-consecutive debounce        │
   └──────────────────────────────┘
   also surfaces FR-05 LocationPermissionController.observeStatus() → permission in UiState

   ── After a successful collect, FR-04 deník (deriveLog/nextControl) updates automatically
      via observeCollected — NO FR-08 change to FR-04. ──
```

**Data flow (offer → confirm → collect):** native layer resolves `ControlCollectionViewModel` (Koin), calls `bind(runId, routeId)` → the VM launches `ObserveCollectionCandidateUseCase(runId, routeId)`, which `combine`s the **shared** `LocationStream.positions()` (FR-05) + `observeRouteWithControls(routeId)` (FR-03) + `observeCollected(runId)` + `observeRun(runId)` (FR-02); it `scan`s a `ProximityState`, feeding each tuple to the **pure** `ProximityEvaluator.evaluate(...)` (which reuses `GeoUtils.distanceMeters`), and — gated on an *active* run — emits a `CollectionCandidate?`. The VM also observes `LocationPermissionController.observeStatus()` for `permissionStatus`. When the participant taps, `confirm()` calls FR-02 `CollectControlUseCase(runId, candidate.control.id, candidate.atLocation)`; on `Collected` it sets `lastResult = JustCollected(controlId)`. The new `CollectedControl` row makes `observeCollected` re-emit → the candidate flips to `null` (now collected) **and** FR-04's deník recomputes `nextControl`/progress — with zero FR-08→FR-04 coupling.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| GPS source | **REUSE FR-05 `LocationStream.positions()`** (shared hot Flow) | One GPS subscription for breadcrumb (FR-05) + geofence (FR-08); no second listener — battery-critical on a multi-hour trail (FR-05 §12.3 seam) |
| Distance math | **REUSE FR-02 `GeoUtils.distanceMeters` / `isWithinRadius`** | Clean haversine `distance(a,b)` already exists and is unit-tested (FR-02 §5/§10); re-implementing would duplicate + risk drift |
| Proximity decision home | **Pure `ProximityEvaluator`** in `domain/geo`; caller holds `ProximityState` | Deterministic, table-testable (mirrors FR-05 `BreadcrumbThrottle` / FR-04 `ControlLogDeriver`); no coroutines/I/O |
| Accuracy handling | **Max-accuracy gate**: a fix worse than `maxAccuracyMeters` cannot *promote* a candidate; it **holds** the current one | Canyon reflections give huge accuracy radii; a bad fix must not create or drop an offer (anti-flicker) |
| Anti-flicker | **Enter/exit hysteresis** (enter `distance <= radius`, exit only `distance > radius + exitMargin`) **+ N consecutive in-range fixes** before first offer | Radius-boundary jitter would otherwise toggle the offer every fix ("blikání"); asymmetric thresholds + debounce make it sticky |
| Candidate selection | **Nearest uncollected control within enter radius**; a held candidate stays until it exits | Deterministic single offer; avoids two controls fighting (they are ~50 m spheres, typically far apart) |
| Collection ordering policy | **ANY-ORDER** (a reached control is collectable regardless of ordinal) | GPS proximity is physical reality; poor GPS at an earlier control must not block collecting one you actually reached; **FR-04's deník already tolerates out-of-order** (keeps first-uncollected ACTIVE, marks collected DONE). Strict-order is a localized rule swap (§12) |
| Run gate | Offer only when the run is **active**: `startedAtMillis != null && finishedAtMillis == null` | You collect *during* a run (started via FR-09 QR, not yet finished); before start / after finish there is nothing to offer |
| Write path | **REUSE FR-02 `CollectControlUseCase`** unchanged; pass the current `GeoPosition.location` so its geofence guard re-validates at write time | No rewrite; single source of the write + a belt-and-braces range check; `INSERT OR IGNORE` makes double-tap a safe no-op |
| Confirmation | **Manual** — app offers a candidate; write only on explicit `confirm()` | zadani: "aplikace nabídne kontrolu sebrat a účastník ji odklikne". No auto-collect |
| Candidate carries `atLocation` | `CollectionCandidate.atLocation: GeoPoint` (the fix that produced the offer) | `confirm()` can pass a coherent location to `CollectControlUseCase` without re-reading the stream / racing |
| Stateful stream fold | `combine(...).scan(ProximityState()) { … }.map { candidate }.distinctUntilChanged()` | `scan` threads the hysteresis/debounce state across emissions while the evaluator stays pure; `distinctUntilChanged` suppresses duplicate offers |
| DI | `GeofenceConfig` + `ProximityEvaluator` as `single`; use-case + ViewModel as `factory` in `appModule` | Matches FR-02/FR-03/FR-05 registration style; nothing platform-specific (no `platformModule` entry) |

---

## 4. IMPLEMENTATION STEPS

> Execute in order. Do not skip. Paths under `shared/src/commonMain/kotlin/com/example/ta33/` unless stated. Base package `com.example.ta33`.
> **Dependency ordering**: assumes **FR-02** (`GeoUtils`, `ControlPoint`, `CollectControlUseCase`/`CollectResult`, `RunRepository.observeRun`/`observeCollected`, `RunSession`, `TimeProvider`, `IdGenerator`, `appModule`, `kotlinx-coroutines-test`), **FR-03** (`RouteRepository.observeRouteWithControls`), **FR-04** (deník; consumed only, untouched) and **FR-05** (`LocationStream`, `GeoPosition`, `LocationPermissionController`) are implemented. No new external dependencies.

### Step 1: Add `GeofenceConfig` (field-tunable thresholds)
**Goal**: One place for the accuracy gate + hysteresis knobs.
**Files**: `domain/model/GeofenceConfig.kt`

```kotlin
package com.example.ta33.domain.model

/**
 * Geofence tuning for checkpoint collection (FR-08). Defaults are field-tunable on the
 * Adršpach/Teplice trail (canyon GPS noise). Independent of FR-05's BreadcrumbConfig but
 * the accuracy gate plays the same role.
 */
data class GeofenceConfig(
    /** Fixes worse than this cannot PROMOTE a candidate (they hold the current one). */
    val maxAccuracyMeters: Double = 50.0,
    /** Exit hysteresis: an offered control stays offered until distance exceeds radius + this. */
    val exitMarginMeters: Double = 20.0,
    /** Debounce: this many consecutive in-range fixes before a fresh control is offered. */
    val minConsecutiveFixes: Int = 2,
)
```
**Done when**: Compiles.

---

### Step 2: Add the `CollectionCandidate` read model
**Goal**: The control the app would offer, plus the fix that produced it (for a coherent `confirm()`).
**Files**: `domain/model/CollectionCandidate.kt`

```kotlin
package com.example.ta33.domain.model

/** The control currently in range and offered for collection (FR-08). Null when nothing to offer. */
data class CollectionCandidate(
    val control: ControlPoint,        // FR-02/FR-03 model (has location + radiusMeters)
    val distanceMeters: Double,       // distance from the producing fix to the control
    val accuracyMeters: Double,       // accuracy of the producing fix (for UI trust hints, later)
    val atLocation: GeoPoint,         // the fix location that produced this offer (fed to confirm())
)
```
**Done when**: Compiles against FR-02 `ControlPoint`/`GeoPoint`.

---

### Step 3: Add the pure `ProximityEvaluator` (+ `ProximityState`)
**Goal**: Deterministic candidate decision; accuracy gate + hysteresis + debounce; reuse `GeoUtils`.
**Files**: `domain/geo/ProximityEvaluator.kt`

```kotlin
package com.example.ta33.domain.geo

import com.example.ta33.domain.geo.GeoUtils               // FR-02 — reused, NOT re-implemented
import com.example.ta33.domain.model.CollectionCandidate
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeofenceConfig
import com.example.ta33.domain.model.GeoPosition

/** Cross-emission state held by the CALLER (use-case), so the evaluator stays pure/deterministic. */
data class ProximityState(
    val offeredControlId: String? = null, // currently offered candidate (post-debounce), sticky via hysteresis
    val pendingControlId: String? = null, // nearest in-range control being debounced
    val pendingStreak: Int = 0,           // consecutive in-range fixes for pendingControlId
)

/** Result of one evaluation: the next state to carry + the candidate to emit (or null). */
data class ProximityEvaluation(
    val state: ProximityState,
    val candidate: CollectionCandidate?,
)

/**
 * PURE, deterministic geofence decision (FR-08). No coroutines, no I/O, no platform APIs.
 * Reuses FR-02 GeoUtils.distanceMeters (clean haversine, already unit-tested).
 *
 * Rules (see plan §3.2):
 *  - Accuracy gate: a fix worse than config.maxAccuracyMeters cannot promote a NEW candidate;
 *    it holds the current offered one (no flip on a bad fix).
 *  - Enter: a fresh control becomes pending at distance <= radiusMeters; it is offered only after
 *    config.minConsecutiveFixes consecutive in-range fixes (debounce).
 *  - Exit hysteresis: an already-offered control stays offered until distance > radius + exitMargin.
 *  - Any-order: only "uncollected" filters candidates — ordinal is irrelevant here.
 */
class ProximityEvaluator(private val config: GeofenceConfig = GeofenceConfig()) {

    fun evaluate(
        previous: ProximityState,
        position: GeoPosition,
        controls: List<ControlPoint>,
        collectedIds: Set<String>,
    ): ProximityEvaluation {
        val uncollected = controls.filter { it.id !in collectedIds }

        fun candidateFor(control: ControlPoint, distance: Double) =
            CollectionCandidate(control, distance, position.accuracyMeters, position.location)

        // 1) Exit hysteresis first: keep a held candidate sticky until it clearly exits.
        val held = previous.offeredControlId
            ?.let { id -> uncollected.firstOrNull { it.id == id } }
            ?.let { c -> c to GeoUtils.distanceMeters(c.location, position.location) }
        if (held != null && held.second <= held.first.radiusMeters + config.exitMarginMeters) {
            // Still within the (loose) exit threshold → keep offering; reset pending.
            return ProximityEvaluation(
                state = ProximityState(offeredControlId = held.first.id, pendingControlId = null, pendingStreak = 0),
                candidate = candidateFor(held.first, held.second),
            )
        }

        // 2) Poor-accuracy gate: cannot promote a NEW candidate on an untrustworthy fix.
        //    (Held candidate already returned above; here we simply hold the previous state.)
        if (position.accuracyMeters > config.maxAccuracyMeters) {
            return ProximityEvaluation(state = previous.copy(offeredControlId = previous.offeredControlId), candidate = null)
        }

        // 3) Find the nearest uncollected control within the (tight) enter radius.
        val nearest = uncollected
            .map { it to GeoUtils.distanceMeters(it.location, position.location) }
            .filter { (c, d) -> d <= c.radiusMeters }
            .minByOrNull { it.second }

        if (nearest == null) {
            return ProximityEvaluation(state = ProximityState(), candidate = null)
        }

        val (control, distance) = nearest
        val streak = if (previous.pendingControlId == control.id) previous.pendingStreak + 1 else 1

        return if (streak >= config.minConsecutiveFixes) {
            ProximityEvaluation(
                state = ProximityState(offeredControlId = control.id, pendingControlId = null, pendingStreak = 0),
                candidate = candidateFor(control, distance),
            )
        } else {
            // Debouncing: in range but not yet offered.
            ProximityEvaluation(
                state = ProximityState(offeredControlId = null, pendingControlId = control.id, pendingStreak = streak),
                candidate = null,
            )
        }
    }
}
```
> The evaluator never sees `collected` order or ordinal — it is order-agnostic by construction (any-order policy). Switching to strict-order later means filtering `uncollected` to just the order-based next control before the distance step (§12).

**Done when**: Compiles; holds no coroutine/repo/platform reference; behaviour locked by Step 7 tests.

---

### Step 4: Add `ObserveCollectionCandidateUseCase`
**Goal**: Fold the shared GPS stream + controls + collected + run into `Flow<CollectionCandidate?>`, gated on an active run, threading the hysteresis state.
**Files**: `domain/usecase/ObserveCollectionCandidateUseCase.kt`

```kotlin
package com.example.ta33.domain.usecase

import com.example.ta33.data.location.LocationStream          // FR-05 (shared GPS)
import com.example.ta33.domain.geo.ProximityEvaluator
import com.example.ta33.domain.geo.ProximityState
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.CollectionCandidate
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPosition
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.repository.RouteRepository       // FR-03
import com.example.ta33.domain.repository.RunRepository         // FR-02
import kotlinx.coroutines.flow.*

class ObserveCollectionCandidateUseCase(
    private val locationStream: LocationStream,   // FR-05 — REUSED shared stream, no new provider
    private val routes: RouteRepository,          // FR-03 observeRouteWithControls
    private val runs: RunRepository,              // FR-02 observeCollected / observeRun
    private val evaluator: ProximityEvaluator,
) {
    private data class Inputs(
        val position: GeoPosition,
        val controls: List<ControlPoint>,
        val collectedIds: Set<String>,
        val run: RunSession?,
    )
    private data class Acc(val state: ProximityState = ProximityState(), val candidate: CollectionCandidate? = null)

    operator fun invoke(runId: String, routeId: String): Flow<CollectionCandidate?> =
        combine(
            locationStream.positions(),                    // fast: each GPS fix
            routes.observeRouteWithControls(routeId),      // Route? (controls ordered by ordinal)
            runs.observeCollected(runId),                  // List<CollectedControl>
            runs.observeRun(runId),                        // RunSession?
        ) { position, route, collected, run ->
            Inputs(
                position = position,
                controls = route?.controls.orEmpty(),
                collectedIds = collected.map(CollectedControl::controlId).toSet(),
                run = run,
            )
        }
            .scan(Acc()) { acc, input ->
                val runActive = input.run?.startedAtMillis != null && input.run.finishedAtMillis == null
                if (!runActive || input.controls.isEmpty()) {
                    Acc(state = ProximityState(), candidate = null)   // reset hysteresis when not collecting
                } else {
                    val eval = evaluator.evaluate(acc.state, input.position, input.controls, input.collectedIds)
                    Acc(state = eval.state, candidate = eval.candidate)
                }
            }
            .map { it.candidate }
            .distinctUntilChanged()
}
```
> `combine` waits for all four sources to emit once; FR-02's `observeRun` emits `null`/an open run, `observeCollected` emits `[]`, FR-03 emits the route, FR-05 emits on the first fix. `scan` threads `ProximityState`; `distinctUntilChanged` prevents re-offering the same candidate every fix.

**Done when**: Compiles; behaviour locked by Step 7 tests.

---

### Step 5: Confirm the FR-02 write path is reused as-is (no rewrite)
**Goal**: Use FR-02's existing `CollectControlUseCase(runId, controlId, location: GeoPoint?)` → `CollectResult` for the write. Verify no change is needed.
**Files**: none to create; verify `domain/usecase/CollectControlUseCase.kt` (FR-02) signature.

- FR-02 already exposes `suspend operator fun invoke(runId, controlId, location: GeoPoint?)` returning `sealed CollectResult { Collected(CollectedControl) / AlreadyCollected / OutOfRange / UnknownControl }`, writing via `RunRepository.addCollected` (`INSERT OR IGNORE` + unique index → double-tap safe).
- FR-08 passes `candidate.atLocation` as `location`, so FR-02's `GeoUtils.isWithinRadius` guard **re-validates at write time** (defence in depth against a stale offer).
- **No additive tweak required** — the `location: GeoPoint?` parameter already accepts what FR-08 provides. If (and only if) FR-02 shipped without the `location` parameter, add it there as a minimal additive overload; do not rewrite the use-case.

**Done when**: Confirmed the signature matches; no FR-02 edit, or a one-line additive overload if absent.

---

### Step 6: Add the headless `ControlCollectionViewModel`
**Goal**: Expose candidate + permission + last outcome as `StateFlow`; `confirm()` writes via FR-02. No UI, manual confirm.
**Files**: `presentation/ControlCollectionViewModel.kt`

```kotlin
package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.data.location.LocationPermissionController   // FR-05 (status only)
import com.example.ta33.domain.model.CollectionCandidate
import com.example.ta33.domain.model.LocationPermissionStatus
import com.example.ta33.domain.usecase.CollectControlUseCase          // FR-02 (reused)
import com.example.ta33.domain.usecase.CollectResult
import com.example.ta33.domain.usecase.ObserveCollectionCandidateUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Outcome of the last confirm() — the "green screen" (later UI) renders JustCollected. */
sealed interface CollectionOutcome {
    data class JustCollected(val controlId: String) : CollectionOutcome
    data object AlreadyCollected : CollectionOutcome
    data object OutOfRange : CollectionOutcome
    data object UnknownControl : CollectionOutcome
}

data class ControlCollectionUiState(
    val permissionStatus: LocationPermissionStatus = LocationPermissionStatus.NOT_DETERMINED,
    val candidate: CollectionCandidate? = null,   // the control the app OFFERS to collect
    val isCollecting: Boolean = false,            // confirm() in flight
    val lastResult: CollectionOutcome? = null,    // consumed by UI; cleared on next candidate
)

class ControlCollectionViewModel(
    private val observeCandidate: ObserveCollectionCandidateUseCase,
    private val collectControl: CollectControlUseCase,         // FR-02, reused
    private val permission: LocationPermissionController,      // FR-05, status only
) : ViewModel() {

    private val _state = MutableStateFlow(ControlCollectionUiState())
    val state: StateFlow<ControlCollectionUiState> = _state.asStateFlow()

    private var runId: String? = null

    fun bind(runId: String, routeId: String) {
        this.runId = runId
        permission.observeStatus()
            .onEach { s -> _state.update { it.copy(permissionStatus = s) } }
            .launchIn(viewModelScope)
        observeCandidate(runId, routeId)
            .onEach { c -> _state.update { it.copy(candidate = c, lastResult = if (c != null) null else it.lastResult) } }
            .launchIn(viewModelScope)
    }

    /** Explicit user action ("odklikne"). Writes the current candidate via FR-02. NO auto-collect. */
    fun confirm() {
        val rid = runId ?: return
        val candidate = _state.value.candidate ?: return
        if (_state.value.isCollecting) return
        _state.update { it.copy(isCollecting = true) }
        viewModelScope.launch {
            val outcome = when (val r = collectControl(rid, candidate.control.id, candidate.atLocation)) {
                is CollectResult.Collected -> CollectionOutcome.JustCollected(r.control.controlId)
                CollectResult.AlreadyCollected -> CollectionOutcome.AlreadyCollected
                CollectResult.OutOfRange -> CollectionOutcome.OutOfRange
                CollectResult.UnknownControl -> CollectionOutcome.UnknownControl
            }
            _state.update { it.copy(isCollecting = false, lastResult = outcome) }
            // No manual candidate clear needed: observeCollected re-emits → candidate flips to null,
            // and FR-04's deník recomputes automatically. (Adjust r.control field access to FR-02's actual shape.)
        }
    }
}
```
> Mirrors FR-02 `RunLogViewModel` / FR-05 `LiveLocationViewModel` (`MutableStateFlow` + `asStateFlow`, `viewModelScope`, `bind(...)`). `runId`/`routeId` come from FR-01 app state / FR-03 active route. Verify the exact `CollectResult.Collected` payload accessor against FR-02 (`r.control.controlId` vs `r.collected.controlId`).

**Done when**: Compiles.

---

### Step 7: Register in Koin + Swift accessor
**Goal**: Make everything resolvable; expose the ViewModel to SwiftUI.
**Files**: `di/AppModule.kt` (edit), `di/Koin.kt` (edit)

`appModule` (additive):
```kotlin
// FR-08 checkpoint collection / geofencing
single { GeofenceConfig() }                                                   // field-tunable
single { ProximityEvaluator(get()) }                                          // config
factory { ObserveCollectionCandidateUseCase(get(), get(), get(), get()) }     // LocationStream(FR-05), RouteRepo(FR-03), RunRepo(FR-02), evaluator
factory { ControlCollectionViewModel(get(), get(), get()) }                   // observeCandidate, CollectControlUseCase(FR-02), LocationPermissionController(FR-05)
```
> `LocationStream`, `LocationPermissionController` (FR-05), `RouteRepository` (FR-02/03), `RunRepository`, `CollectControlUseCase` (FR-02) are already `single`/`factory` in `appModule`/`platformModule`. Nothing platform-specific here → no `platformModule` change.

`di/Koin.kt` `ViewModelProvider` (mirrors `runLogViewModel()` / `liveLocationViewModel()`):
```kotlin
fun controlCollectionViewModel(): ControlCollectionViewModel = KoinPlatform.getKoin().get()
```
**Done when**: `./gradlew build` passes; Koin resolves (Step 8 / app launch); iOS `Shared` framework compiles.

---

### Step 8: Unit tests (`commonTest`, fake location Flow)
**Goal**: Lock the pure evaluator, the stream fold, and the ViewModel. Prefer fakes over a real DB.
**Files**: under `shared/src/commonTest/kotlin/com/example/ta33/`

- **`ProximityEvaluatorTest`** (pure, no coroutines) — table-driven over `evaluate(prevState, pos, controls, collectedIds)`:
  1. **In range, debounced** — first in-range fix → `candidate == null`, `pendingStreak == 1`; second consecutive → candidate is that control (default `minConsecutiveFixes = 2`).
  2. **Out of range** → `candidate == null`, empty state.
  3. **Poor accuracy** — a fix with `accuracyMeters > maxAccuracyMeters` inside the radius (no prior offer) → `candidate == null` (not promoted).
  4. **Poor accuracy holds a held candidate** — with `offeredControlId` set and the control still within `radius + exitMargin`, a poor-accuracy fix keeps offering it.
  5. **Exit hysteresis** — offered control at `distance` between `radius` and `radius + exitMargin` → still offered; beyond `radius + exitMargin` → dropped (candidate null / re-debounce).
  6. **Boundary jitter (no flicker)** — feed a sequence oscillating a few metres across `radius` while `offeredControlId` is set → candidate stays the same control (never toggles) until a clean exit.
  7. **Any-order** — controls c1..c3 all uncollected, position inside c3 only → candidate c3 even though c1 is the ordinal-next (uncollected).
  8. **Already-collected excluded** — position inside c1 but `collectedIds = {c1}` → no candidate.
  9. **All collected** — every control in `collectedIds` → no candidate regardless of position.
  10. **Nearest wins** — position inside two overlapping radii → nearest uncollected control offered.
  Use small lat/lon deltas so `GeoUtils.distanceMeters` crosses the 50 m boundary (reuse FR-02 `GeoUtilsTest` fixtures).

- **`ObserveCollectionCandidateUseCaseTest`** (`runTest`) — a `FakeLocationStream` (reuse FR-05's or a local fake wrapping a `MutableSharedFlow<GeoPosition>`), a `FakeRouteRepository` emitting a `Route` with controls (reuse FR-03 fake), a `FakeRunRepository` (reuse FR-02 fake) feeding `observeCollected`/`observeRun`:
  - Run **not started** → candidate stays `null` even when inside a radius.
  - Run **active** → scripted approach (far → near → near) yields `null … null … candidate(cX)` after debounce; then scripted collect (`observeCollected` emits `[cX]`) → candidate flips to `null`.
  - Run **finished** → candidate `null` (gate).
  - Assert `distinctUntilChanged`: repeated identical in-range fixes emit the candidate once.

- **`ControlCollectionViewModelTest`** (`runTest` + `Dispatchers.setMain(StandardTestDispatcher())`):
  - `bind(runId, routeId)` surfaces `permissionStatus` (fake `LocationPermissionController`) and `candidate` (fake use-case Flow / fake stream).
  - `confirm()` with a candidate → calls a **fake `CollectControlUseCase`** returning `Collected` → `lastResult == JustCollected(id)`, `isCollecting` toggles true→false.
  - `confirm()` returning `AlreadyCollected` / `OutOfRange` → reflected in `lastResult`.
  - `confirm()` with no candidate → no-op (use-case not called).

Fakes: reuse `FakeLocationStream`/`FakeLocationProvider` (FR-05), `FakeRouteRepository` (FR-03), `FakeRunRepository` (FR-02), `FakePermissionController` (FR-05); add a `FakeCollectControlUseCase` (or a spyable interface) for the VM test.

**Done when**: `./gradlew :shared:allTests` green.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| GPS fix jitters across the radius edge | Offer does not flicker | Exit hysteresis (`radius + exitMargin`) keeps a held candidate; enter needs `minConsecutiveFixes` |
| Poor-accuracy fix (canyon reflection) | No new offer created / dropped on a single bad fix | Accuracy gate: cannot promote a new candidate; a held one stays until it cleanly exits |
| Two controls' radii overlap | Single, nearest offer | `minByOrNull { distance }` over uncollected in-range controls |
| Control already collected | Never offered | `uncollected` filter excludes `collectedIds` |
| All controls collected | No candidate | `uncollected` empty → `null` |
| Run not started (no QR start yet, FR-09) | No candidate | Run-active gate in the use-case (`startedAtMillis != null`) |
| Run already finished | No candidate | Run-active gate (`finishedAtMillis == null`) |
| Double-tap `confirm()` | One collection only | `isCollecting` guard in VM + FR-02 `INSERT OR IGNORE`/`AlreadyCollected` |
| Confirm after walking out of range (stale offer) | Rejected, not persisted | FR-02 `CollectControlUseCase` re-checks `isWithinRadius(candidate.atLocation)` → `OutOfRange` |
| Permission not granted / GPS off | No fixes → no candidate; status surfaced | FR-05 stream is empty; VM shows `permissionStatus`; nothing offered |
| Content re-downloaded mid-run (FR-11) | Candidate re-evaluated live | `observeRouteWithControls` is reactive; `combine` re-emits with new controls |
| Empty route (0 controls) | No candidate, no crash | `controls.isEmpty()` short-circuits to `null` in the fold |
| App killed / relaunched near a control | Offer re-derives from live fixes + persisted collected | No FR-08 state persisted; state rebuilds from the stream + `observeCollected` (already durable in FR-02) |
| Collected row updates deník | Deník refreshes automatically | FR-04 `ObserveRunLogUseCase` observes `observeCollected` — no FR-08 involvement |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: GPS coordinates/accuracy are numeric from the OS (via FR-05); the evaluator tolerates any finite values and never throws. `runId`/`routeId` come from FR-01 app state / FR-03 nav args, not free user input. Control ids come from app content.
- **Auth/Access control**: None in Etapa 1 — anonymous local participant; no tokens (Etapa 2, Keystore/Keychain per stack §4).
- **Sensitive data**: Positions and collection timestamps are personal data but stay **on-device only** (no upload in Etapa 1; `syncStatus = PENDING`). The candidate is transient in-memory; only the confirmed `CollectedControl` (FR-02) is persisted. Before any coordinate leaves the device (Etapa 2 sync), run a privacy review — relevant to store approval (zadani location-permission justification).
- **Anti-abuse**: Collecting by physical proximity (not a printed QR) removes the printed-QR loss/abuse risk (zadani). The write-time `isWithinRadius` re-check (FR-02) prevents confirming a stale, out-of-range offer.
- **Logging**: Napier at debug only; do **not** log full position streams or precise candidate coordinates at info level (mirror FR-02/FR-05 §6). Keep geofence logs coarse (e.g. "candidate=<controlId> distance≈Xm"); never log to persistent files.

---

## 7. ASSUMPTIONS

> User opted out of clarification (`skip questions` / `proceed directly`). Accepted defaults recorded here.

1. **FR-02, FR-03, FR-04, FR-05 are implemented first** and their types are **referenced, not duplicated** (`GeoUtils`, `ControlPoint`, `CollectControlUseCase`/`CollectResult`, `RunRepository`, `RouteRepository.observeRouteWithControls`, `LocationStream`, `GeoPosition`, `LocationPermissionController`, `appModule`, `kotlinx-coroutines-test`). If any is unmerged: fold the missing seam in (e.g. add `CollectControlUseCase`'s `location` param) rather than restructuring.
2. **FR-08 reuses FR-05's `LocationStream`** as the single GPS source and creates no new provider/subscription. Impact if wrong: doubled battery drain over a multi-hour route.
3. **`GeoUtils.distanceMeters` (FR-02) is the only distance math** — no haversine is re-implemented in FR-08. Impact if wrong: duplicated, drift-prone math.
4. **Collection is ANY-ORDER in Etapa 1** — a physically reached, uncollected control is collectable regardless of ordinal (FR-04's deník already tolerates this). Switching to strict-order is a localized change (filter to the order-based next before the distance step). Impact if wrong: one filter line.
5. **The run must be active** (`startedAtMillis != null && finishedAtMillis == null`) to offer a candidate. If the organizer wants collection before the start QR, relax the gate in the use-case only.
6. **Confirmation is manual** — the app offers; the participant taps `confirm()`. No auto-collect (zadani). Impact if wrong: change `confirm()` to auto-fire on candidate — but that contradicts the spec.
7. **Geofence thresholds** (`maxAccuracy = 50 m`, `exitMargin = 20 m`, `minConsecutiveFixes = 2`) are reasonable starting points, **field-tunable** via `GeofenceConfig`. Impact if wrong: change config values only; logic unchanged.
8. **The candidate carries `atLocation`** so `confirm()` feeds a coherent location to FR-02 (write-time re-validation). Impact if wrong: minor model tweak.
9. **`CollectResult.Collected` exposes the collected control's id** (`r.control.controlId` or `r.collected.controlId`) — verify against FR-02's exact shape. Impact if wrong: one accessor rename in the VM.
10. **Only Android + iOS targets** — pure Kotlin + coroutines; `Dispatchers.setMain` only in VM tests.

> Open questions live in Section 12.

---

## 8. QUICK REFERENCE

### Files to Modify
- `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt` — register `GeofenceConfig`, `ProximityEvaluator`, `ObserveCollectionCandidateUseCase`, `ControlCollectionViewModel`.
- `shared/src/commonMain/kotlin/com/example/ta33/di/Koin.kt` — add `controlCollectionViewModel()` Swift accessor.
- *(Only if FR-02 shipped without it)* `domain/usecase/CollectControlUseCase.kt` — additive `location: GeoPoint?` param. Otherwise **no FR-02/FR-03/FR-04/FR-05 edits**.

### Files to Create
- `domain/model/GeofenceConfig.kt` — accuracy gate + hysteresis knobs.
- `domain/model/CollectionCandidate.kt` — offered-control read model (+ `atLocation`).
- `domain/geo/ProximityEvaluator.kt` — pure evaluator (+ `ProximityState`, `ProximityEvaluation`).
- `domain/usecase/ObserveCollectionCandidateUseCase.kt` — stream fold → `Flow<CollectionCandidate?>`.
- `presentation/ControlCollectionViewModel.kt` — headless `StateFlow` + `bind`/`confirm` (+ `CollectionOutcome`, `ControlCollectionUiState`).
- `commonTest/…` — `ProximityEvaluatorTest`, `ObserveCollectionCandidateUseCaseTest`, `ControlCollectionViewModelTest` (+ `FakeCollectControlUseCase`; reuse FR-02/FR-03/FR-05 fakes).

### Dependencies
- **None new.** (Already present: coroutines-core + `kotlinx-coroutines-test`, SQLDelight, Koin, lifecycle-viewmodel, Napier; FR-05 `LocationStream`; FR-02 `GeoUtils`/`CollectControlUseCase`.) **No map SDK** (that is FR-06).

### Commands
```bash
./gradlew build                      # compile shared + apps
./gradlew :shared:allTests           # shared unit tests (fake location Flow)
./gradlew :androidApp:assembleDebug  # Android sanity
# iOS headless (compiles controlCollectionViewModel() into Shared):
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
| **A. Pure `ProximityEvaluator` (accuracy gate + hysteresis + debounce) + `ObserveCollectionCandidateUseCase` folding the shared `LocationStream`, writing via FR-02's `CollectControlUseCase`; manual confirm; any-order** | Reuses FR-05 stream (one GPS sub) + FR-02 write + FR-02 haversine; pure, table-testable decision; anti-flicker; zero FR-04 change; no new deps/schema | A little cross-emission state to thread via `scan` | ✅ |
| B. **Platform geofencing APIs** (Android `GeofencingClient`, iOS `CLCircularRegion` monitoring) | OS-managed, battery-optimized background triggers | Not shared/pure (can't unit-test the ~50 m + accuracy/hysteresis logic in `commonTest`); OS geofence radii/behaviour differ per platform and are unreliable in canyons; can't reuse FR-05's stream; heavy field variance | — |
| C. **Naive `isWithinRadius` per fix, offer whenever true** (no hysteresis/accuracy gate) | Simplest code | Offer flickers on boundary jitter and canyon noise ("blikání") — the exact failure the zadani/stack §4 warns about | — |
| D. **Auto-collect on entry** (no confirm) | Fewer taps | Contradicts zadani ("účastník ji odklikne"); risks false collections from a single noisy fix | — |
| E. **Strict-order collection** (only the ordinal-next control is collectable) | Matches a linear course model | Poor GPS at one control blocks the whole run; FR-04 already tolerates out-of-order; brittle in the field | — (kept as a one-line switch) |

**Why the selected approach won**: It keeps the entire geofence decision in shared, pure, unit-testable Kotlin (stack goals), reuses FR-05's single GPS subscription and FR-02's tested haversine + write path with zero duplication, is robust to canyon GPS noise via accuracy gating + hysteresis, and requires no change to FR-04 — the deník updates itself reactively.

### 12.2 Open Questions

- [ ] **Final geofence thresholds and debounce (`maxAccuracy`, `exitMargin`, `minConsecutiveFixes`) and whether the enter test should be accuracy-aware (e.g. `distance + k·accuracy <= radius`)** — Proposed direction: ship the defaults, then tune from real field logs on the Adršpach/Teplice trail; all localized to `GeofenceConfig`/`ProximityEvaluator`.
- [ ] **Any-order vs strict-order collection** — Proposed direction: ship **any-order** (justified in §3.2); if the organizer requires sequential control-passing, filter `uncollected` to the order-based next control before the distance step (single localized change; FR-04's `nextControl` already computes it).
- [ ] **Should collection be allowed before the start QR (run not yet started)?** — Proposed direction: no (offer only during an active run); relax the run-active gate in the use-case if the organizer wants pre-start collection.
- [ ] **Where do `runId`/`routeId` come from when binding the collection screen?** — Proposed direction: FR-01 app state (active run) + FR-03 active route; bind only when a run is active. Confirm when the UI phase lands.
- [ ] **Should the candidate switch to a nearer control while one is still "held" (sticky)?** — Proposed direction: no (keep the held offer until it exits) — controls are ~50 m spheres, rarely overlapping; revisit only if field data shows adjacent controls.

### 12.3 Suggestions & Follow-ups

- **UI phase**: render `CollectionCandidate` as the "nabídnout sebrat" prompt and `CollectionOutcome.JustCollected` as the green confirmation screen; map `permissionStatus` to a permission prompt. All data the screen needs is already in `ControlCollectionUiState`.
- **FR-06 (map)**: reuse the same `CollectionCandidate`/proximity data to highlight the in-range control on the map; no new contract needed.
- **FR-09 (timing)**: `CollectedControl.collectedAtMillis` written here IS the checkpoint *mezičas* FR-09 surfaces; no extra FR-08 work.
- Consider a light **haptic/notification** cue when a candidate first appears (UI/platform phase) so the participant notices without staring at the screen.
- Add a **JVM SQLite driver** integration test (`androidHostTest`) exercising the full `positions → candidate → confirm → CollectedControl → deník` chain against a real DB — good coverage, out of scope here.
- Add a Koin **`checkModules()`** test spanning FR-02/03/04/05/08 to catch DI-graph breakage.
- Consider **Turbine** for `Flow`/`StateFlow` assertions in the use-case/VM tests (optional test-only dep).

> Sections 9 (Design Reference) and 10 (Corrections From Current State) intentionally omitted: this is logic-only work with no UI/visual spec (the "green" confirmation screen is deferred UI), and it is greenfield FR-08 with no prior implementation to correct — all upstream touches are reuse of existing FR-02/03/04/05 seams, not corrections.
