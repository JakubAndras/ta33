# FR-09 Měření Času — QR Start/Finish Timing, Elapsed & Splits (Logic Only)

> **Summary**: Build the shared, headless timing logic for TA33 — a pure QR-payload parser/validator that recognises the *start* vs *finish* QR of the event, a `HandleScannedQrUseCase` that drives FR-02's existing `StartRunUseCase`/`FinishRunUseCase` off a scanned string, a pure drift-free `Stopwatch` + `SplitCalculator` + `TimeFormatter`, a ticking `Flow` stopwatch that derives elapsed from wall-clock time (never persisting per second), and a headless `TimingViewModel` (`StateFlow`: isRunning / elapsed / splits / finished) — all offline, unit-tested, with NO UI and NO camera implementation (the scan is a pure entry-point contract; the camera/preview is a deferred later phase).

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
On the TA33 course a participant's time is measured by QR codes in the marshalled start and finish areas: scanning the **start** QR starts the stopwatch, scanning the **finish** QR stops it, and each checkpoint records a *split* (mezičas) when it is collected. It must work with no signal, survive app restarts, and the wrong or a foreign QR must be rejected. Today the app can persist start/finish timestamps (FR-02) and checkpoint collection times (FR-08), but nothing turns a *scanned QR string* into a start/finish decision, and nothing exposes a live running clock + splits.

### 1.2 Solution Overview
Add a thin FR-09 layer over the existing shared core: a **pure** `QrPayloadParser` (field-configurable format) that classifies a scanned string as start / finish / unrecognised; a `HandleScannedQrUseCase` that inspects the run state and delegates the actual timestamp write to FR-02's **existing** `StartRunUseCase`/`FinishRunUseCase`, returning a sealed `ScanTimingResult`; pure, deterministic `Stopwatch.elapsed(now, start, finish)` and `SplitCalculator.splits(start, collected)`; a `Ticker`-driven `ObserveTimingUseCase` that recomputes elapsed from the clock on each tick (so nothing is persisted per second and the value never drifts); and a headless `TimingViewModel` exposing a `StateFlow<TimingUiState>`. The scanned string enters shared logic through a plain entry point (`TimingViewModel.onQrScanned(raw)`, formalised as a `QrScanHandler` contract) — the camera that produces that string is a deferred UI/platform phase.

### 1.3 Scope: What This IS
- **Config model** (`domain/model`): `QrTimingConfig` — field-tunable QR format (scheme, separator, start/finish keywords, optional route-scoping, case sensitivity), with a temporary dev pattern `TA33:START` / `TA33:FINISH`.
- **Pure parser** (`domain/qr`): `QrPayloadParser.parse(raw, config)` → sealed `QrParseResult { StartQr(routeId?) / FinishQr(routeId?) / Unrecognized(raw) }`. No coroutines/I/O/platform.
- **Scan contract** (`domain/qr`): a `QrScanHandler` fun-interface (the shared entry point the native scanner will call with a decoded string). Implemented by `TimingViewModel`. **No camera, no `expect/actual`** in this phase (see §3.2 / §12).
- **Orchestration use-case** (`domain/usecase`): `HandleScannedQrUseCase(runId, routeId, raw)` → sealed `ScanTimingResult`. Reads run state, then **REUSES** FR-02 `StartRunUseCase`/`FinishRunUseCase` for the write. Never writes timestamps directly.
- **Pure timing utils** (`core`): `Stopwatch.elapsed(now, start, finish)`, `SplitCalculator.splits(start, collected)` (→ ordered `List<Split>`), `TimeFormatter.format(millis)` (`H:MM:SS` / `MM:SS`).
- **Tick source** (`core`): `Ticker` fun-interface + `DefaultTicker` (coroutine `delay`-based) — injectable/fakeable.
- **Observation use-case** (`domain/usecase`): `ObserveTimingUseCase(runId)` → `Flow<TimingSnapshot>` that `combine`s FR-02 `observeRun` + `observeCollected` and, only while the run is active, ticks to refresh elapsed.
- **Headless `TimingViewModel`** (`presentation`): `StateFlow<TimingUiState>` (isRunning / isFinished / elapsedMillis / elapsedFormatted / splits / lastScan) + `bind(runId, routeId)` + `onQrScanned(raw)`.
- **Koin registration** (`di/AppModule`) + Swift `ViewModelProvider.timingViewModel()` accessor (`di/Koin.kt`).
- **Unit tests** (`commonTest`): parser, stopwatch, splits, formatter, `HandleScannedQrUseCase` transitions, ticking `ObserveTimingUseCase`, ViewModel — using fakes over a real DB.

### 1.4 Scope: What This IS NOT
- **No UI whatsoever** — no Compose/SwiftUI, no camera preview, no scan overlay, no stopwatch widget, no splits list rendering. The ViewModel is headless; rendering `TimingUiState` is the deferred UI phase's job.
- **No camera / scanner implementation** — CameraX + ML Kit Barcode (Android) and AVFoundation/Vision (iOS) are built in the later UI/platform phase. This phase defines only the *contract* that receives the already-decoded string. **No `expect/actual`** is introduced now (parsing is pure; the camera producer is deferred — see §3.2).
- **No QR generation** — generating any QR (payment/check-in) is Etapa 2 (project-stack §4). Etapa 1 only *scans* start/finish.
- **No re-modelling of time** — `startedAtMillis`/`finishedAtMillis` stay on FR-02's `RunSession`; the write goes through FR-02's `StartRunUseCase`/`FinishRunUseCase`. No new timestamp columns, no new table.
- **No change to FR-08 geofence/collection** — FR-09 only *reads* `CollectedControl.collectedAtMillis`; it never collects or touches proximity logic.
- **No change to FR-04 deník derivation** — FR-09 reuses the *split notion* (`collectedAt − startedAt`) but does not modify the log deriver; both compute the same formula.
- **No server sync / networking / map SDK** — Etapa 1 on-device only; `syncStatus` stays `PENDING`.
- **No new Gradle modules / no new external dependencies** — everything lives in `:shared` as package layers.

---

## 2. SUCCESS CRITERIA

Implementation is COMPLETE when ALL criteria are met:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | Project compiles with `QrTimingConfig`, `QrPayloadParser`, `QrScanHandler`, `HandleScannedQrUseCase`, `Stopwatch`, `SplitCalculator`, `TimeFormatter`, `Ticker`, `ObserveTimingUseCase`, `TimingViewModel` | `./gradlew build` succeeds |
| 2 | `QrPayloadParser` is pure (no coroutines/I/O/platform) and classifies valid start / valid finish / foreign & malformed strings correctly | `QrPayloadParserTest` (valid start, valid finish, route-scoped, unknown/empty/foreign → `Unrecognized`) |
| 3 | Scanning the **start** QR when the run has not started starts it via FR-02 `StartRunUseCase` (sets `RunSession.startedAtMillis`) | `HandleScannedQrUseCaseTest`: start → `Started`, run's `startedAtMillis` set |
| 4 | Scanning the **finish** QR while running stops it via FR-02 `FinishRunUseCase` (sets `finishedAtMillis`) | `HandleScannedQrUseCaseTest`: finish while running → `Finished`, `finishedAtMillis` set |
| 5 | Invalid transitions are rejected without writing: finish-before-start, start-when-already-started, scan-when-finished, wrong route, foreign QR | `HandleScannedQrUseCaseTest` per-case (`FinishBeforeStart`, `AlreadyStarted`, `AlreadyFinished`, `WrongRoute`, `NotATimingQr`) — no repository write |
| 6 | `Stopwatch.elapsed` = 0 before start, `now − start` while running, `finish − start` (frozen) once finished; never negative | `StopwatchTest` |
| 7 | `SplitCalculator.splits` returns one split per collected control, `collectedAt − start`, sorted by `collectedAt`; empty when not started | `SplitCalculatorTest` (incl. unsorted input) |
| 8 | The ticking stopwatch `Flow` emits an advancing elapsed while running and a single frozen value once finished; nothing is persisted per tick | `ObserveTimingUseCaseTest` with a fake `Ticker` + fixed `TimeProvider`; no repo write on tick |
| 9 | `TimeFormatter.format` renders `MM:SS` under an hour and `H:MM:SS` at/over an hour | `TimeFormatterTest` boundary cases |
| 10 | `TimingViewModel.onQrScanned` handles the scanned string and reflects the outcome in `lastScan`; `bind` surfaces live elapsed/splits | `TimingViewModelTest` (`runTest` + `Dispatchers.setMain`) |
| 11 | No FR-02 timestamp write is duplicated — FR-09 calls FR-02 use-cases and never `RunRepository.setStarted/setFinished` directly | Code review (no direct `setStarted`/`setFinished` in FR-09) |
| 12 | All new components resolvable via Koin; Swift `timingViewModel()` compiles into `Shared` | Koin resolution / app launch; `xcodebuild` headless |
| 13 | `./gradlew :shared:allTests` green | Run tests |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
      NATIVE UI + CAMERA (later phase — NOT built here)
      CameraX + ML Kit Barcode (Android) / AVFoundation + Vision (iOS)
      decodes a QR frame → hands the raw String to shared logic
      renders TimingUiState (clock, splits); calls bind() / onQrScanned(raw)
  ═════════════════ SHARED CORE (:shared, commonMain) ═════════════════
                    presentation/
          ┌─────────────────────────────────────────────┐
          │            TimingViewModel                    │  StateFlow<TimingUiState>
          │  (headless; implements QrScanHandler)         │  (isRunning, isFinished,
          └───┬───────────────────────────────┬──────────┘   elapsedMillis, elapsedFormatted,
     bind()   │ observe                        │ onQrScanned(raw)     splits, lastScan)
              ▼                                ▼
   domain/usecase/                    domain/usecase/
   ┌──────────────────────────┐      ┌──────────────────────────────────────────┐
   │ ObserveTimingUseCase(runId)│    │ HandleScannedQrUseCase(runId,routeId,raw)  │ → ScanTimingResult
   │  combine(observeRun,       │    │  1. QrPayloadParser.parse(raw, config)     │
   │          observeCollected) │    │  2. read run state (runs.getRun)           │
   │  .flatMapLatest {          │    │  3. start → StartRunUseCase (FR-02, REUSED) │
   │     active ? ticker.ticks  │    │     finish→ FinishRunUseCase (FR-02, REUSED)│
   │              .map{now→snap}│    │     else → sealed error/ignore             │
   │            : flowOf(snap) }│    └───┬───────────────────────────┬────────────┘
   │  → Flow<TimingSnapshot>    │        │ (write via FR-02)          │ (parse, pure)
   └───┬────────────┬──────────┘        ▼                            ▼
 FR-02 │      FR-02  │          domain/usecase/ (FR-02, REUSED)  domain/qr/ (PURE)
observeRun    observeCollected  ┌───────────────────────────┐  ┌────────────────────────┐
 (runId)       (runId)          │ StartRunUseCase(runId)     │  │ QrPayloadParser.parse   │
    │             │             │ FinishRunUseCase(runId)    │  │  → QrParseResult        │
    ▼             ▼             │  → RunRepository.setStarted│  │ (Start/Finish/Unrecog.) │
  Ta33Database (FR-02 RunSession / CollectedControl)  /setFinished │ QrTimingConfig (format)│
                                └───────────────────────────┘  └────────────────────────┘
   core/ (PURE, no platform)
   ┌──────────────────────────────────────────────────────────────────────────┐
   │ Stopwatch.elapsed(now,start,finish)   SplitCalculator.splits(start,collected)│
   │ TimeFormatter.format(millis)          Ticker (fun-interface) + DefaultTicker │
   └──────────────────────────────────────────────────────────────────────────┘

   QrScanHandler (domain/qr) = the pure contract the native scanner calls: onQrScanned(raw).
   The camera-backed producer is DEFERRED (UI phase); no expect/actual here.
```

**Data flow — start/finish scan:** the (later) camera screen decodes a QR frame and calls `TimingViewModel.onQrScanned(raw)`. The VM launches `HandleScannedQrUseCase(runId, routeId, raw)`, which (1) `QrPayloadParser.parse`s the string using `QrTimingConfig`; (2) loads the run via `RunRepository.getRun(runId)`; (3) for a **start** QR on a not-yet-started run calls FR-02 `StartRunUseCase(runId)` (which writes `startedAtMillis = TimeProvider.nowMillis()`), for a **finish** QR on a running run calls FR-02 `FinishRunUseCase(runId)` (writes `finishedAtMillis`); every invalid case returns a sealed `ScanTimingResult` **without** writing. The FR-02 write makes `observeRun` re-emit.

**Data flow — live clock & splits:** `bind(runId, routeId)` launches `ObserveTimingUseCase(runId)`, which `combine`s FR-02 `observeRun(runId)` + `observeCollected(runId)`; while the run is active it `flatMapLatest`es onto `Ticker.ticks(1000ms)` and, each tick, recomputes `Stopwatch.elapsed(TimeProvider.nowMillis(), start, finish)` + `SplitCalculator.splits(start, collected)` → a `TimingSnapshot`. Before start it emits a single zeroed snapshot; after finish a single frozen snapshot (`finish − start`), no further ticking. Nothing is written per second — elapsed is *derived*, not accumulated, so it cannot drift and survives restart (start timestamp is durable in FR-02).

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Timestamp ownership | **REUSE FR-02 `RunSession.startedAtMillis`/`finishedAtMillis`** — no new columns/table | FR-02 already models run time and its comments already say "set by QR start/finish (FR-09)"; re-modelling would duplicate + risk drift |
| Timestamp write | **REUSE FR-02 `StartRunUseCase`/`FinishRunUseCase`**; FR-09 never calls `setStarted`/`setFinished` directly | Single source of the write + FR-02's guards (start no-op if already started; finish rejects if not started / finish < start) act as belt-and-braces |
| Decision home | FR-09's `HandleScannedQrUseCase` reads run state and **decides** which FR-02 use-case to call, returning a rich sealed result | Keeps the QR→timing policy in one testable place; decouples from FR-02's internal return shape (FR-09 re-reads the run for the result) |
| QR parsing | **Pure `QrPayloadParser`** in `domain/qr`; format via injected `QrTimingConfig` | Deterministic, table-testable in `commonTest` (mirrors FR-02 `GeoUtils` / FR-08 `ProximityEvaluator`); no platform |
| QR format | Field-configurable (`scheme`/`separator`/keywords/route-scope/case), **dev default `TA33:START` / `TA33:FINISH`** | Organizer finalises the real format later; a simple prefixed token unblocks dev + tests now; route-scoping optional for multi-route safety |
| Scan seam | **Pure `QrScanHandler` entry point** (`onQrScanned(raw)`), implemented by `TimingViewModel`; **no `expect/actual`** this phase | The only platform part is the camera *producer*, which is deferred UI; the string it yields is plain — parsing/validation are pure shared Kotlin. Introducing `expect/actual` now would be an empty seam (project-stack: "expect/actual only where platform is required") |
| Elapsed computation | **Pure `Stopwatch.elapsed(now, start, finish)`** — derive from wall-clock, never accumulate | Drift-free; correct after app kill/restart (recomputed from durable `startedAtMillis`); trivially unit-testable with a fixed `TimeProvider` |
| Ticking | **`Ticker` fun-interface + `DefaultTicker`** (`flow { emit; delay }`); ticks only *trigger* recomputation | Nothing persisted per second (battery + DB churn); injectable fake makes the stopwatch deterministically testable; `flatMapLatest` stops ticking when the run is not active |
| Splits | **Pure `SplitCalculator.splits(start, collected)`** → `List<Split>(controlId, collectedAt, splitMillis)` sorted by `collectedAt` | Same formula as FR-04 `RunLogEntry.splitMillis` (`collectedAt − startedAt`); FR-09 derives directly from `CollectedControl` so it stays decoupled from the deník deriver, while the *notion* is shared (UI can join names via FR-04 later) |
| Time formatting | **Pure `TimeFormatter`** (`H:MM:SS` ≥ 1 h, else `MM:SS`) in `commonMain` | No `kotlinx-datetime` dependency needed for a duration; pure integer math is testable and locale-neutral |
| Observation shape | `combine(observeRun, observeCollected).flatMapLatest { active ? ticker : flowOf(snap) }.distinctUntilChanged()` | `flatMapLatest` runs the ticker only while active (stops on finish); `distinctUntilChanged` suppresses duplicate frozen/zeroed snapshots |
| DI | `QrTimingConfig`, `QrPayloadParser`, `Ticker` as `single`; use-cases + ViewModel as `factory` in `appModule`; Swift accessor in `di/Koin.kt` | Matches FR-02/FR-08 registration style; nothing platform-specific → no `platformModule` entry |

---

## 4. IMPLEMENTATION STEPS

> Execute in order. Do not skip. Paths under `shared/src/commonMain/kotlin/com/example/ta33/` unless stated. Base package `com.example.ta33`.
> **Dependency ordering**: assumes **FR-02** (`RunSession`, `RunRepository.observeRun`/`observeCollected`/`getRun`, `StartRunUseCase`, `FinishRunUseCase`, `TimeProvider`, `CollectedControl`, `appModule`, `di/Koin.kt`, `kotlinx-coroutines-test`) is implemented. FR-04 (`RunLogEntry.splitMillis` notion) and FR-08 (`CollectedControl.collectedAtMillis` writer) are consumed/aligned only — untouched. No new external dependencies.

### Step 1: Add `QrTimingConfig` (field-tunable QR format)
**Goal**: One place for the start/finish QR format the organizer will finalise.
**Files**: `domain/model/QrTimingConfig.kt`

```kotlin
package com.example.ta33.domain.model

/**
 * QR format for start/finish timing (FR-09). Dev default recognises "TA33:START" / "TA33:FINISH".
 * The real format is finalised by the organizer; all knobs are field-tunable, no code change.
 */
data class QrTimingConfig(
    val scheme: String = "TA33",         // first segment must equal this
    val separator: String = ":",         // segment delimiter
    val startKeyword: String = "START",  // second segment for a start QR
    val finishKeyword: String = "FINISH",// second segment for a finish QR
    val routeScoped: Boolean = false,    // when true, an optional 3rd segment carries a routeId
    val caseSensitive: Boolean = false,  // keyword/scheme comparison
)
```
**Done when**: Compiles.

---

### Step 2: Add QR parse models + pure `QrPayloadParser`
**Goal**: Classify a scanned string as start / finish / unrecognised. Pure, no platform.
**Files**: `domain/qr/QrParseResult.kt`, `domain/qr/QrPayloadParser.kt`

```kotlin
// domain/qr/QrParseResult.kt
package com.example.ta33.domain.qr

/** Outcome of parsing a scanned QR string (FR-09). routeId is present only when route-scoped. */
sealed interface QrParseResult {
    data class StartQr(val routeId: String?) : QrParseResult
    data class FinishQr(val routeId: String?) : QrParseResult
    data class Unrecognized(val raw: String) : QrParseResult   // foreign / malformed / empty
}
```
```kotlin
// domain/qr/QrPayloadParser.kt
package com.example.ta33.domain.qr

import com.example.ta33.domain.model.QrTimingConfig

/** PURE, deterministic. No coroutines / I/O / platform. Format comes from QrTimingConfig. */
class QrPayloadParser {

    fun parse(raw: String, config: QrTimingConfig): QrParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return QrParseResult.Unrecognized(raw)

        val parts = trimmed.split(config.separator)
        if (parts.size < 2) return QrParseResult.Unrecognized(raw)

        fun eq(a: String, b: String) =
            if (config.caseSensitive) a == b else a.equals(b, ignoreCase = true)

        if (!eq(parts[0], config.scheme)) return QrParseResult.Unrecognized(raw)

        val keyword = parts[1]
        val routeId = if (config.routeScoped) parts.getOrNull(2)?.takeIf { it.isNotBlank() } else null

        return when {
            eq(keyword, config.startKeyword)  -> QrParseResult.StartQr(routeId)
            eq(keyword, config.finishKeyword) -> QrParseResult.FinishQr(routeId)
            else                              -> QrParseResult.Unrecognized(raw)
        }
    }
}
```
**Done when**: Compiles; behaviour locked by Step 9 tests.

---

### Step 3: Add the `QrScanHandler` scan contract (pure entry point)
**Goal**: The shared-side seam the native scanner (built later) calls with a decoded string. No camera, no `expect/actual`.
**Files**: `domain/qr/QrScanHandler.kt`

```kotlin
package com.example.ta33.domain.qr

/**
 * Contract the native QR scanner (CameraX + ML Kit / AVFoundation + Vision — built in the UI phase)
 * uses to hand a decoded payload into shared logic. This logic-only phase defines the seam;
 * TimingViewModel implements it. The camera-backed producer is deferred.
 */
fun interface QrScanHandler {
    /** Called by the native scanner for each decoded QR string. Fire-and-forget from the caller. */
    fun onQrScanned(raw: String)
}
```
> Rationale (§3.2): the only platform-specific piece is the camera *producer*, which is deferred UI. The decoded value is a plain `String`, so no `expect/actual` is warranted now. If a producer-style seam is preferred when the camera lands, a `QrScanner` interface with platform `actual`s via `platformModule` can be added then (see §12) — it is not needed for logic.

**Done when**: Compiles.

---

### Step 4: Add pure `Stopwatch` (elapsed)
**Goal**: Drift-free elapsed derived from wall-clock time.
**Files**: `core/Stopwatch.kt`

```kotlin
package com.example.ta33.core

/** PURE. Elapsed derives from wall-clock time — never accumulated, so it cannot drift and
 *  is correct after app restart (start timestamp is durable in FR-02 RunSession). */
object Stopwatch {
    fun elapsed(nowMillis: Long, startedAtMillis: Long?, finishedAtMillis: Long?): Long = when {
        startedAtMillis == null      -> 0L
        finishedAtMillis != null     -> (finishedAtMillis - startedAtMillis).coerceAtLeast(0L) // frozen
        else                         -> (nowMillis - startedAtMillis).coerceAtLeast(0L)         // running
    }
}
```
**Done when**: Compiles; locked by Step 9 tests.

---

### Step 5: Add `Split` model + pure `SplitCalculator`
**Goal**: Derive checkpoint splits (mezičasy) relative to the start, ordered.
**Files**: `domain/model/Split.kt`, `core/SplitCalculator.kt`

```kotlin
// domain/model/Split.kt
package com.example.ta33.domain.model

/** A checkpoint split (mezičas) for FR-09. splitMillis == collectedAtMillis - run.startedAtMillis,
 *  the same notion as FR-04 RunLogEntry.splitMillis. */
data class Split(
    val controlId: String,
    val collectedAtMillis: Long,
    val splitMillis: Long,
)
```
```kotlin
// core/SplitCalculator.kt
package com.example.ta33.core

import com.example.ta33.domain.model.CollectedControl   // FR-02 model
import com.example.ta33.domain.model.Split

/** PURE. Splits = collectedAt - start for each collected control, sorted by collectedAt.
 *  Empty until the run has started. Mirrors FR-04's splitMillis formula (kept decoupled from
 *  the deník deriver; the UI can join control names via FR-04 later). */
object SplitCalculator {
    fun splits(startedAtMillis: Long?, collected: List<CollectedControl>): List<Split> {
        if (startedAtMillis == null) return emptyList()
        return collected
            .sortedBy { it.collectedAtMillis }
            .map { c ->
                Split(
                    controlId = c.controlId,
                    collectedAtMillis = c.collectedAtMillis,
                    splitMillis = (c.collectedAtMillis - startedAtMillis).coerceAtLeast(0L),
                )
            }
    }
}
```
> Verify the FR-02 `CollectedControl` field name (`collectedAtMillis`) and package on first build; adjust the import if FR-02 shipped a different name.

**Done when**: Compiles against FR-02 `CollectedControl`.

---

### Step 6: Add pure `TimeFormatter`
**Goal**: Format a duration for display (used by the ViewModel; pure so it is unit-tested).
**Files**: `core/TimeFormatter.kt`

```kotlin
package com.example.ta33.core

/** PURE duration formatter. "MM:SS" under an hour, "H:MM:SS" at/over an hour. Locale-neutral. */
object TimeFormatter {
    fun format(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        fun two(v: Long) = v.toString().padStart(2, '0')
        return if (hours > 0L) "$hours:${two(minutes)}:${two(seconds)}" else "${two(minutes)}:${two(seconds)}"
    }
}
```
**Done when**: Compiles; locked by Step 9 tests.

---

### Step 7: Add `Ticker` (injectable tick source)
**Goal**: A fakeable cadence that *triggers* recomputation; no per-tick persistence.
**Files**: `core/Ticker.kt`

```kotlin
package com.example.ta33.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits Unit every periodMillis, first tick immediately (so elapsed updates at once). */
fun interface Ticker {
    fun ticks(periodMillis: Long): Flow<Unit>
}

class DefaultTicker : Ticker {
    override fun ticks(periodMillis: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(periodMillis)
        }
    }
}
```
> Tests inject a `FakeTicker` wrapping a `MutableSharedFlow<Unit>` (or a scripted flow) for deterministic control without real delays.

**Done when**: Compiles.

---

### Step 8: Add `ScanTimingResult` + `HandleScannedQrUseCase`
**Goal**: Turn a scanned string into a start/finish action via FR-02 use-cases, or a rejection. No direct timestamp write.
**Files**: `domain/usecase/HandleScannedQrUseCase.kt`

```kotlin
package com.example.ta33.domain.usecase

import com.example.ta33.domain.model.QrTimingConfig
import com.example.ta33.domain.qr.QrParseResult
import com.example.ta33.domain.qr.QrPayloadParser
import com.example.ta33.domain.repository.RunRepository   // FR-02

/** Outcome of handling one scanned QR (FR-09). */
sealed interface ScanTimingResult {
    data class Started(val startedAtMillis: Long) : ScanTimingResult
    data class Finished(val finishedAtMillis: Long, val elapsedMillis: Long) : ScanTimingResult
    data object AlreadyStarted : ScanTimingResult      // start QR after already started
    data object AlreadyFinished : ScanTimingResult     // any timing QR after finish
    data object FinishBeforeStart : ScanTimingResult   // finish QR before start
    data class WrongRoute(val expectedRouteId: String, val scannedRouteId: String?) : ScanTimingResult
    data class NotATimingQr(val raw: String) : ScanTimingResult  // foreign / malformed
    data class RunNotFound(val runId: String) : ScanTimingResult
}

class HandleScannedQrUseCase(
    private val parser: QrPayloadParser,
    private val runs: RunRepository,             // FR-02: getRun/observeRun
    private val startRun: StartRunUseCase,       // FR-02: writes startedAtMillis
    private val finishRun: FinishRunUseCase,     // FR-02: writes finishedAtMillis (guards)
    private val config: QrTimingConfig,
) {
    suspend operator fun invoke(runId: String, routeId: String, raw: String): ScanTimingResult {
        val parsed = parser.parse(raw, config)
        val run = runs.getRun(runId) ?: return ScanTimingResult.RunNotFound(runId)

        return when (parsed) {
            is QrParseResult.Unrecognized -> ScanTimingResult.NotATimingQr(raw)

            is QrParseResult.StartQr -> {
                config.wrongRouteOrNull(routeId, parsed.routeId)?.let { return it }
                when {
                    run.finishedAtMillis != null -> ScanTimingResult.AlreadyFinished
                    run.startedAtMillis != null  -> ScanTimingResult.AlreadyStarted
                    else -> {
                        startRun(runId)  // FR-02 writes startedAtMillis = TimeProvider.nowMillis()
                        val started = runs.getRun(runId)?.startedAtMillis
                        started?.let { ScanTimingResult.Started(it) } ?: ScanTimingResult.RunNotFound(runId)
                    }
                }
            }

            is QrParseResult.FinishQr -> {
                config.wrongRouteOrNull(routeId, parsed.routeId)?.let { return it }
                when {
                    run.finishedAtMillis != null -> ScanTimingResult.AlreadyFinished
                    run.startedAtMillis == null  -> ScanTimingResult.FinishBeforeStart
                    else -> {
                        finishRun(runId)  // FR-02 writes finishedAtMillis (guards finish >= start)
                        val fresh = runs.getRun(runId)
                        val finished = fresh?.finishedAtMillis
                        val start = fresh?.startedAtMillis
                        if (finished != null && start != null)
                            ScanTimingResult.Finished(finished, (finished - start).coerceAtLeast(0L))
                        else ScanTimingResult.RunNotFound(runId)
                    }
                }
            }
        }
    }
}

/** Route-scope guard helper (only enforced when config.routeScoped). */
private fun QrTimingConfig.wrongRouteOrNull(expectedRouteId: String, scannedRouteId: String?): ScanTimingResult.WrongRoute? =
    if (routeScoped && scannedRouteId != null && scannedRouteId != expectedRouteId)
        ScanTimingResult.WrongRoute(expectedRouteId, scannedRouteId) else null
```
> **Verify against FR-02**: `StartRunUseCase`/`FinishRunUseCase` `invoke` signatures (assumed `suspend operator fun invoke(runId: String)`) and that `RunRepository.getRun(runId)` exists. FR-09 re-reads the run for the result so it does not depend on FR-02's return type. FR-02's own guards (start no-op if started; finish rejects if not started / finish < start) remain a safety net; FR-09's pre-checks give the richer sealed result. If FR-02 exposes a start/finish result type, prefer mapping it over the re-read (minor).

**Done when**: Compiles; behaviour locked by Step 9 tests.

---

### Step 9: Add `TimingSnapshot` + `ObserveTimingUseCase` (ticking stopwatch)
**Goal**: A `Flow<TimingSnapshot>` combining run + collected + a tick, live only while active.
**Files**: `domain/model/TimingSnapshot.kt`, `domain/usecase/ObserveTimingUseCase.kt`

```kotlin
// domain/model/TimingSnapshot.kt
package com.example.ta33.domain.model

data class TimingSnapshot(
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val elapsedMillis: Long = 0L,
    val splits: List<Split> = emptyList(),
)
```
```kotlin
// domain/usecase/ObserveTimingUseCase.kt
package com.example.ta33.domain.usecase

import com.example.ta33.core.SplitCalculator
import com.example.ta33.core.Stopwatch
import com.example.ta33.core.Ticker
import com.example.ta33.core.TimeProvider          // FR-02
import com.example.ta33.domain.model.TimingSnapshot
import com.example.ta33.domain.repository.RunRepository  // FR-02
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class ObserveTimingUseCase(
    private val runs: RunRepository,   // FR-02 observeRun / observeCollected
    private val time: TimeProvider,    // FR-02
    private val ticker: Ticker,
    private val tickPeriodMillis: Long = 1_000L,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(runId: String): Flow<TimingSnapshot> =
        combine(
            runs.observeRun(runId),        // RunSession?
            runs.observeCollected(runId),  // List<CollectedControl>
        ) { run, collected -> run to collected }
            .flatMapLatest { (run, collected) ->
                val start = run?.startedAtMillis
                val finish = run?.finishedAtMillis
                fun snapshot(now: Long) = TimingSnapshot(
                    isRunning = start != null && finish == null,
                    isFinished = finish != null,
                    elapsedMillis = Stopwatch.elapsed(now, start, finish),
                    splits = SplitCalculator.splits(start, collected),
                )
                if (start != null && finish == null) {
                    ticker.ticks(tickPeriodMillis).map { snapshot(time.nowMillis()) }  // live, ticking
                } else {
                    flowOf(snapshot(time.nowMillis()))  // zeroed (pre-start) or frozen (finished)
                }
            }
            .distinctUntilChanged()
}
```
> `flatMapLatest` cancels the ticker as soon as the run leaves the active state (finish/reset), so ticking stops on finish. `distinctUntilChanged` collapses repeated identical snapshots. Nothing is written to the DB on a tick.

**Done when**: Compiles; behaviour locked by Step 10 tests.

---

### Step 10: Add the headless `TimingViewModel`
**Goal**: Expose timing state + the scan entry point; no UI.
**Files**: `presentation/TimingViewModel.kt`

```kotlin
package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.core.TimeFormatter
import com.example.ta33.domain.model.Split
import com.example.ta33.domain.qr.QrScanHandler
import com.example.ta33.domain.usecase.HandleScannedQrUseCase
import com.example.ta33.domain.usecase.ObserveTimingUseCase
import com.example.ta33.domain.usecase.ScanTimingResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TimingUiState(
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val elapsedMillis: Long = 0L,
    val elapsedFormatted: String = "00:00",
    val splits: List<Split> = emptyList(),
    val lastScan: ScanTimingResult? = null,   // consumed by UI (e.g. show "started"/"finished"/"wrong QR")
)

class TimingViewModel(
    private val observeTiming: ObserveTimingUseCase,
    private val handleScannedQr: HandleScannedQrUseCase,
) : ViewModel(), QrScanHandler {

    private val _state = MutableStateFlow(TimingUiState())
    val state: StateFlow<TimingUiState> = _state.asStateFlow()

    private var runId: String? = null
    private var routeId: String? = null

    fun bind(runId: String, routeId: String) {
        this.runId = runId
        this.routeId = routeId
        observeTiming(runId)
            .onEach { snap ->
                _state.update {
                    it.copy(
                        isRunning = snap.isRunning,
                        isFinished = snap.isFinished,
                        elapsedMillis = snap.elapsedMillis,
                        elapsedFormatted = TimeFormatter.format(snap.elapsedMillis),
                        splits = snap.splits,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** Native scanner (later phase) calls this with a decoded QR string. */
    override fun onQrScanned(raw: String) {
        val rid = runId ?: return
        val route = routeId ?: return
        viewModelScope.launch {
            val result = handleScannedQr(rid, route, raw)
            _state.update { it.copy(lastScan = result) }
        }
    }
}
```
> Mirrors FR-02 `RunLogViewModel` / FR-08 `ControlCollectionViewModel` (`MutableStateFlow` + `asStateFlow`, `viewModelScope`, `bind(...)`). `runId`/`routeId` come from FR-01 app state / FR-03 active route.

**Done when**: Compiles.

---

### Step 11: Register in Koin + Swift accessor
**Goal**: Make everything resolvable; expose the ViewModel to SwiftUI.
**Files**: `di/AppModule.kt` (edit), `di/Koin.kt` (edit)

`appModule` (additive):
```kotlin
// FR-09 timing (QR start/finish, elapsed, splits)
single { QrTimingConfig() }                                             // field-tunable format
single { QrPayloadParser() }                                            // pure
single<Ticker> { DefaultTicker() }
factory { HandleScannedQrUseCase(get(), get(), get(), get(), get()) }   // parser, RunRepo, StartRunUseCase, FinishRunUseCase, config
factory { ObserveTimingUseCase(get(), get(), get()) }                   // RunRepo, TimeProvider, Ticker
factory { TimingViewModel(get(), get()) }                               // observeTiming, handleScannedQr
```
> `RunRepository`, `StartRunUseCase`, `FinishRunUseCase`, `TimeProvider` are already registered by FR-02. Nothing platform-specific → no `platformModule` change.

`di/Koin.kt` `ViewModelProvider` (mirrors `runLogViewModel()` / `controlCollectionViewModel()`):
```kotlin
fun timingViewModel(): TimingViewModel = KoinPlatform.getKoin().get()
```
**Done when**: `./gradlew build` passes; Koin resolves (Step 12 / app launch); iOS `Shared` framework compiles.

---

### Step 12: Unit tests (`commonTest`, fakes over a real DB)
**Goal**: Lock the parser, pure utils, orchestration transitions, ticking stream and ViewModel.
**Files**: under `shared/src/commonTest/kotlin/com/example/ta33/`

- **`QrPayloadParserTest`** (pure): `TA33:START` → `StartQr(null)`; `TA33:FINISH` → `FinishQr(null)`; lowercase `ta33:start` → `StartQr` (default case-insensitive); route-scoped config `TA33:START:routeA` → `StartQr("routeA")`; `""`, `"HELLO"`, `"TA33"`, `"TA33:FOO"`, `"OTHER:START"`, a foreign URL → `Unrecognized`; leading/trailing whitespace trimmed; `caseSensitive = true` rejects wrong case.
- **`StopwatchTest`** (pure): start `null` → 0; running (`start=1000`, `now=4000`, `finish=null`) → 3000; finished (`start=1000`, `finish=5000`) → 4000 regardless of `now`; `now < start` → 0 (coerced); `finish < start` → 0 (coerced).
- **`SplitCalculatorTest`** (pure): `start=null` → empty; three unsorted `CollectedControl`s → sorted by `collectedAt`, each `splitMillis == collectedAt − start`; a `collectedAt < start` (clock skew) → 0 (coerced).
- **`TimeFormatterTest`** (pure): 0 → `"00:00"`; 65_000 → `"01:05"`; 3_600_000 → `"1:00:00"`; 3_661_000 → `"1:01:01"`; negative → `"00:00"`.
- **`HandleScannedQrUseCaseTest`** (`runTest`): a **`FakeRunRepository`** (reuse FR-02's if present) + **real FR-02 `StartRunUseCase`/`FinishRunUseCase`** wired to it with a **fixed `TimeProvider`**:
  - start QR on a not-started run → `Started(t0)`, `getRun().startedAtMillis == t0`.
  - start QR again on a started run → `AlreadyStarted`, timestamp unchanged.
  - finish QR on a running run → `Finished(t1, t1 − t0)`, `finishedAtMillis == t1`.
  - finish QR on a not-started run → `FinishBeforeStart`, no write.
  - any timing QR on a finished run → `AlreadyFinished`, no write.
  - foreign/unknown string → `NotATimingQr`, no write.
  - unknown `runId` → `RunNotFound`.
  - route-scoped config, wrong route id → `WrongRoute`.
- **`ObserveTimingUseCaseTest`** (`runTest`): a **`FakeTicker`** (wraps a `MutableSharedFlow<Unit>`) + `FakeRunRepository` + fixed/advancing `TimeProvider`:
  - not started → single snapshot `elapsed == 0`, `isRunning == false`.
  - active run, emit ticks while advancing the fake clock → `elapsedMillis` increases per tick; no repo write occurs.
  - collected list emits → `splits` reflects sorted `collectedAt − start`.
  - finish emits → one frozen snapshot (`finish − start`), `isFinished == true`, ticker no longer drives new values (assert no emission on a further tick via `distinctUntilChanged`).
- **`TimingViewModelTest`** (`runTest` + `Dispatchers.setMain(StandardTestDispatcher())`): `bind(runId, routeId)` surfaces snapshots (fake use-case Flow) with correct `elapsedFormatted`; `onQrScanned(startPayload)` → `lastScan is Started`; `onQrScanned(foreign)` → `lastScan is NotATimingQr`; `onQrScanned` before `bind` → no-op (use-case not called).

Fakes: reuse FR-02 `FakeRunRepository` / fixed-`TimeProvider` helpers; add `FakeTicker`. Prefer real FR-02 `StartRunUseCase`/`FinishRunUseCase` over stubs (exercises the reuse seam). Use `kotlinx-coroutines-test` (already present).

**Done when**: `./gradlew :shared:allTests` green.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| Finish QR scanned before any start | Rejected, run stays not-started | `HandleScannedQrUseCase` → `FinishBeforeStart` (pre-check) + FR-02 `FinishRunUseCase` guard |
| Start QR scanned twice | First start time kept | `AlreadyStarted` (pre-check) + FR-02 `StartRunUseCase` no-op |
| Any timing QR after finish | Rejected | `AlreadyFinished` (both start & finish QR) |
| Foreign / other app's QR, or garbage string | Ignored, no state change | `QrPayloadParser` → `Unrecognized` → `NotATimingQr` |
| Empty / whitespace-only scan | Ignored | Parser returns `Unrecognized` |
| Route-scoped QR for a different route | Rejected | `WrongRoute` (only when `config.routeScoped`) |
| App killed mid-run, relaunched | Clock resumes correctly | Elapsed is *derived* from durable `startedAtMillis` (FR-02); `Stopwatch.elapsed(now, start, null)` |
| Device clock jumps backward (NTP) while running | Elapsed never negative; may momentarily stall | `coerceAtLeast(0L)`; accept for Etapa 1, note monotonic source for Etapa 2 (matches FR-02 §5) |
| `collectedAt < startedAt` (skewed checkpoint time) | Split shown as 0, not negative | `SplitCalculator` `coerceAtLeast(0L)` |
| Finish scanned but `finish < start` (clock skew) | FR-02 rejects the write; FR-09 reflects no finish | FR-02 `FinishRunUseCase` guard; FR-09 re-read sees `finishedAtMillis == null` → returns without `Finished` (surface as no-op/`FinishBeforeStart`-like; log) |
| Ticks continue after finish | No new emissions, no battery/DB churn | `flatMapLatest` switches to `flowOf(frozen)`; ticker cancelled; `distinctUntilChanged` |
| Run has 0 collected controls | `splits` empty, clock still runs | `SplitCalculator` returns empty list |
| `onQrScanned` before `bind` | No-op | `runId`/`routeId` null guard in the ViewModel |
| Run id not found | Reported, no crash | `RunNotFound(runId)` |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: the scanned QR string is untrusted free input. `QrPayloadParser` treats anything not matching the exact configured format as `Unrecognized` (no exceptions, no partial writes); route-scoping optionally binds a start/finish QR to a specific route. `runId`/`routeId` come from FR-01 app state / FR-03 nav args, not the scan.
- **Auth/Access control**: none in Etapa 1 — anonymous local participant, no tokens (Etapa 2 uses Keystore/Keychain per stack §4).
- **Sensitive data**: start/finish timestamps and splits are personal data but stay **on-device only** (no upload in Etapa 1; `syncStatus = PENDING`). Before any time leaves the device (Etapa 2 sync), run a privacy review.
- **Anti-abuse**: timing QR lives in the marshalled start/finish areas (zadani); the parser + optional route-scoping reject foreign QRs, and the state machine rejects out-of-order scans (finish-before-start, double-start, scan-after-finish) so the clock cannot be trivially gamed by rescanning.
- **Logging**: Napier at debug only. Log the *classification* and *transition* (e.g. `scan=START result=Started`) — do **not** log raw QR payloads at info level or write timing logs to persistent files (mirror FR-02/FR-08 §6).

---

## 7. ASSUMPTIONS

> User opted out of clarification (`skip questions` / `proceed directly`). Accepted defaults recorded here.

1. **FR-02 is implemented first** and its types are **referenced, not duplicated**: `RunSession` (`startedAtMillis?`/`finishedAtMillis?`), `RunRepository.getRun`/`observeRun`/`observeCollected`, `StartRunUseCase`/`FinishRunUseCase`, `TimeProvider`, `CollectedControl`, `appModule`, `di/Koin.kt`, `kotlinx-coroutines-test`. If any is unmerged, fold the seam in rather than restructuring. Impact if wrong: signature-only adjustments.
2. **FR-02 `StartRunUseCase`/`FinishRunUseCase` `invoke` take `runId: String`** and write via `RunRepository.setStarted`/`setFinished` using `TimeProvider`. FR-09 re-reads the run for its result, so it does not depend on their return type. Impact if wrong: map their result instead of re-reading (localized).
3. **QR timing format is not yet finalised** — the dev default `TA33:START` / `TA33:FINISH` (case-insensitive, optional route-scoped 3rd segment) is a placeholder in `QrTimingConfig`; the organizer sets the real format later with no code change. Impact if wrong: config values only.
4. **No `expect/actual` is needed this phase** — the camera producer is deferred UI; the scanned value is a plain `String`, and parsing is pure. Impact if wrong (e.g. a producer-style `QrScanner` interface is preferred): add it when the camera lands (§12), no rework of the pure logic.
5. **Splits are derived directly from `CollectedControl`** (same formula as FR-04 `RunLogEntry.splitMillis`), keeping FR-09 decoupled from the deník deriver. Impact if wrong: source splits from FR-04's read model instead (one call-site swap).
6. **One active run at a time in Etapa 1** (consistent with FR-02) — `runId` is provided by app state. Impact if wrong: unchanged, `runId` is a parameter.
7. **Elapsed is derived, not persisted** — `Stopwatch` recomputes from `startedAtMillis`; a 1 s tick is display cadence only. Impact if wrong: none for correctness (survives restart); tick period is a constructor default.
8. **Timestamps are epoch-millis `Long`** (FR-02), no `kotlinx-datetime`. `TimeFormatter` is pure integer math. Impact if wrong: none for a duration.
9. **Only Android + iOS targets** — pure Kotlin + coroutines; `Dispatchers.setMain` only in the VM test; `flatMapLatest` `@OptIn(ExperimentalCoroutinesApi::class)`.

> Open questions live in Section 12.

---

## 8. QUICK REFERENCE

### Files to Modify
- `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt` — register `QrTimingConfig`, `QrPayloadParser`, `Ticker`, `HandleScannedQrUseCase`, `ObserveTimingUseCase`, `TimingViewModel`.
- `shared/src/commonMain/kotlin/com/example/ta33/di/Koin.kt` — add `timingViewModel()` Swift accessor.
- *(No FR-02/FR-04/FR-08 edits.)* Only verify FR-02 `StartRunUseCase`/`FinishRunUseCase`/`RunRepository.getRun` signatures on first build.

### Files to Create
- `domain/model/QrTimingConfig.kt` — field-tunable QR format.
- `domain/model/Split.kt` — checkpoint split (mezičas) read model.
- `domain/model/TimingSnapshot.kt` — timing read model.
- `domain/qr/QrParseResult.kt` — sealed parse outcome.
- `domain/qr/QrPayloadParser.kt` — pure parser/validator.
- `domain/qr/QrScanHandler.kt` — pure scan entry-point contract.
- `core/Stopwatch.kt` — pure elapsed.
- `core/SplitCalculator.kt` — pure splits.
- `core/TimeFormatter.kt` — pure duration formatting.
- `core/Ticker.kt` — `Ticker` fun-interface + `DefaultTicker`.
- `domain/usecase/HandleScannedQrUseCase.kt` — orchestration + `ScanTimingResult`.
- `domain/usecase/ObserveTimingUseCase.kt` — ticking `Flow<TimingSnapshot>`.
- `presentation/TimingViewModel.kt` — headless `StateFlow` + `bind`/`onQrScanned` (+ `TimingUiState`).
- `commonTest/…` — `QrPayloadParserTest`, `StopwatchTest`, `SplitCalculatorTest`, `TimeFormatterTest`, `HandleScannedQrUseCaseTest`, `ObserveTimingUseCaseTest`, `TimingViewModelTest` (+ `FakeTicker`; reuse FR-02 fakes).

### Dependencies
- **None new.** (Already present: coroutines-core + `kotlinx-coroutines-test`, Koin, lifecycle-viewmodel, Napier; FR-02 `RunSession`/use-cases/`TimeProvider`.) No map SDK, no networking, no `kotlinx-datetime`.

### Commands
```bash
./gradlew build                      # compile shared + apps (+ any codegen)
./gradlew :shared:allTests           # shared unit tests (fakes, no real DB)
./gradlew :androidApp:assembleDebug  # Android sanity
# iOS headless (compiles timingViewModel() into Shared):
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
| **A. Pure `QrPayloadParser` + `HandleScannedQrUseCase` delegating to FR-02 `Start/FinishRunUseCase`; derived `Stopwatch`/`SplitCalculator`; `Ticker`-driven `ObserveTimingUseCase`; headless `TimingViewModel`; scan = pure `onQrScanned` entry (no `expect/actual`)** | Reuses FR-02 time model + write path + `TimeProvider`; drift-free & restart-safe; fully pure/table-testable in `commonTest`; no new deps/schema; no platform seam until the camera actually exists | A little Flow plumbing (`flatMapLatest` + `distinctUntilChanged`) | ✅ |
| B. **`expect/actual QrScanner` producer interface now** (platform stubs) with `Flow<String>` scans | Formal platform seam upfront | Empty seam this phase (camera deferred), adds android/ios `actual` files with nothing to do; contradicts "expect/actual only where platform is required"; more surface to maintain | — |
| C. **Persist elapsed / tick counter to the DB** (write each second) | "Current time" always in the DB | DB churn + battery cost every second; drift risk; pointless — elapsed is a pure function of `start`/`now`/`finish` already durable in FR-02 | — |
| D. **FR-09 writes `startedAtMillis`/`finishedAtMillis` directly** via `RunRepository.setStarted/setFinished` | One fewer indirection | Duplicates FR-02's write + guards; two code paths can diverge; violates the reuse constraint | — |
| E. **New `TimingSession` table owning its own start/finish** | Isolated timing model | Re-models what `RunSession` already holds (FR-02 comments already reserve it for FR-09); migration + sync duplication; splits lose their `RunSession` anchor | — |

**Why the selected approach won**: it keeps the entire timing decision in shared, pure, unit-testable Kotlin (stack goal), reuses FR-02's `RunSession` time fields, its `Start/FinishRunUseCase` write path and `TimeProvider` with zero duplication, derives a drift-free clock that survives app restart, persists nothing per second, and adds no platform seam until the camera genuinely needs one.

### 12.2 Open Questions

- [ ] **Final QR payload format for start/finish** — Proposed direction: ship the `TA33:START`/`TA33:FINISH` dev default in `QrTimingConfig`; confirm the exact scheme (and whether it is route-scoped or event-global) with the organizer before the field test; changing it is config-only.
- [ ] **Exact FR-02 `Start/FinishRunUseCase` signatures & return types** — Proposed direction: assume `suspend operator fun invoke(runId)`; verify on first build and, if they return a result type, map it instead of the re-read in `HandleScannedQrUseCase`.
- [ ] **Should a rejected scan (foreign / out-of-order) surface a user-visible message?** — Proposed direction: yes, in the UI phase — `lastScan: ScanTimingResult` already carries enough for a toast/snackbar; no logic change.
- [ ] **Display cadence** — Proposed direction: 1 s tick (constructor default). If sub-second is wanted for a sprint-style display, lower `tickPeriodMillis`; elapsed math is unaffected.
- [ ] **Where do `runId`/`routeId` come from at scan time?** — Proposed direction: FR-01 app state (active run) + FR-03 active route; `bind(runId, routeId)` before enabling the scanner. Confirm when the UI phase lands.

### 12.3 Suggestions & Follow-ups

- **UI phase**: build the camera scanner (CameraX + ML Kit Barcode / AVFoundation + Vision) that decodes a frame and calls `TimingViewModel.onQrScanned(raw)`; render `elapsedFormatted`, `splits`, and map `lastScan` to a start/finish/error prompt. If a producer-style seam is preferred then, add a `QrScanner` interface with platform `actual`s via `platformModule` — the pure logic here is unaffected.
- **FR-04 deník**: the same `Split` values (`collectedAt − start`) already appear as `RunLogEntry.splitMillis`; the UI can show splits either from FR-04 (with control names) or from FR-09's `TimingUiState.splits` — keep a single formula (`coerceAtLeast(0)`); consider extracting one shared `splitMillis(start, collectedAt)` helper if drift is a worry.
- **Etapa 2 sync**: start/finish/splits are already durable with `syncStatus = PENDING`; an uploader attaches with no schema change.
- **Monotonic timing**: guard the multi-hour clock against wall-clock jumps in Etapa 2 (a monotonic time source alongside `TimeProvider`), matching FR-02 §12.3.
- Add a **JVM SQLite driver** integration test (`androidHostTest`) exercising `scan START → tick → collect → scan FINISH → frozen elapsed + splits` against a real DB — good coverage, out of scope here.
- Add a Koin **`checkModules()`** test spanning FR-02/FR-09 to catch DI-graph breakage.

> Sections 9 (Design Reference) and 10 (Corrections From Current State) intentionally omitted: this is logic-only work with no UI/visual spec (the camera/clock/splits screens are deferred UI), and it is greenfield FR-09 with no prior implementation to correct — all upstream touches are reuse of existing FR-02 seams, not corrections.
