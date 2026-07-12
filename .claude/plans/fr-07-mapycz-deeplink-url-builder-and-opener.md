# FR-07 Open Route in mapy.cz - URL Builder & Platform URL Opener (Logic Only)

> **Summary**: Add the shared, headless *logic* for "open this route in mapy.cz navigation" - a pure `MapyCzUrlBuilder` that turns an FR-03 `RouteDetail` (its FR-02 controls, ordered by `ordinal`) into a universal `https://mapy.com/fnc/v1/route` link, an `expect/actual`-style `UrlOpener` platform seam (Android `Intent.ACTION_VIEW`, iOS `UIApplication.open`) wired through `platformModule`, and an `OpenRouteInMapyCzUseCase` that glues them - with `commonTest` unit tests for the URL, and **no UI / no button** built.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Some TA33 participants want classic turn-by-turn guidance instead of (or alongside) the app's own offline map. The zadání (FR-07) calls for a button that opens the selected route in mapy.cz. The button and its screen are UI and are deferred; what's missing *underneath* is the logic: given a route's ordered control points, produce a correct mapy.cz link and hand it to the OS so the mapy.cz app (or, if not installed, the browser) opens it.

### 1.2 Solution Overview
Add a thin FR-07 layer on top of the existing FR-03/FR-02 data: one **pure** `MapyCzUrlBuilder` in `commonMain` (control points → mapy.cz route URL, start + end + intermediate waypoints in `ordinal` order, longitude-first coordinates), a small **platform seam** `UrlOpener` (a `commonMain` interface with Android/iOS implementations bound in `platformModule`, exactly like the existing `SqlDriver`), and an `OpenRouteInMapyCzUseCase` that builds the URL and opens it. The chosen URL is a **universal `https` link**, which mapy.cz resolves to the installed app or falls back to the web automatically - so no custom URI scheme and no manual "is-app-installed" check are needed.

### 1.3 Scope: What This IS
- **Pure URL builder** (`domain/mapy/MapyCzUrlBuilder.kt`): `List<ControlPoint>` (+ a `MapyRouteType`) → mapy.cz route URL `String?`. Longitude-first coordinate order, `ordinal` ordering, locale-independent decimal formatting, waypoint clamping.
- **Route-type enum** (`domain/mapy/MapyRouteType.kt`): the mapy.cz `routeType` values, default `FOOT_HIKING`.
- **Platform seam** (`core/UrlOpener.kt` interface + `core/UrlOpener.android.kt` / `core/UrlOpener.ios.kt` impls) bound per platform in `di/PlatformModule.{android,ios}.kt` - mirrors the `SqlDriver` pattern.
- **Use-case** (`domain/usecase/OpenRouteInMapyCzUseCase.kt`): takes an FR-03 `RouteDetail`, builds the URL, calls the opener, returns a sealed result.
- **Koin registration** in `di/AppModule.kt` + a Swift-facing accessor in `di/Koin.kt`.
- **Unit tests** (`commonTest`): URL correctness (coordinate order/values, ordering, no over-encoding, edge cases) + use-case behaviour with a fake `UrlOpener`.

### 1.4 Scope: What This IS NOT
- **No UI whatsoever** - no Compose/SwiftUI, no button, no screen, no icon, no navigation, no theming, no string resources. That is a deferred later phase. FR-07 exposes only callable logic.
- **No new models** - coordinates come from FR-02 `ControlPoint.location: GeoPoint(latitude, longitude)` and the FR-03 `RouteDetail`. No duplicate route/coordinate types are introduced.
- **No route fetching / no repository coupling** - the use-case receives an already-loaded `RouteDetail` (the FR-03 `RouteDetailViewModel` will supply it later). FR-07 does not read SQLDelight or call `RouteRepository`.
- **No mapy.cz REST Routing API, no API key, no network call** - this is a plain deep link, not the developer.mapy.com routing service. Fully offline to build; the OS handles opening.
- **No offline-map render (FR-06)** and no in-app navigation - FR-07 only hands off to the external mapy.cz app/web.
- **No new Gradle modules** - everything lives in `:shared` as package layers (project-stack §12).

---

## 2. SUCCESS CRITERIA

Implementation is COMPLETE when ALL criteria are met:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | Project compiles on all targets with the builder, enum, `UrlOpener` seam, use-case, and Koin wiring | `./gradlew build` succeeds; `xcodebuild … build` succeeds |
| 2 | `MapyCzUrlBuilder` renders coordinates **longitude-first** (`lon,lat`), swapping `GeoPoint(latitude, longitude)` | `MapyCzUrlBuilderTest`: known control `GeoPoint(50.0878, 14.4606)` → `start=14.4606,50.0878` |
| 3 | `start` = first control by `ordinal`, `end` = last, intermediate controls become `waypoints` in `ordinal` order (semicolon-separated) | `MapyCzUrlBuilderTest` with shuffled-input controls asserts full URL |
| 4 | Decimal formatting is **locale-independent** (`.` separator) and stable | `MapyCzUrlBuilderTest` asserts exact string; no `String.format`/locale API used |
| 5 | Structural chars (`,` `;` `&` `=`) are **not** percent-encoded; coordinate values need no encoding | `MapyCzUrlBuilderTest` asserts literal commas/semicolons in the URL |
| 6 | Empty controls → `null` URL and use-case `NoRoute` (no-op, no opener call) | `MapyCzUrlBuilderTest` + `OpenRouteInMapyCzUseCaseTest` (fake opener never called) |
| 7 | Single control → valid URL with `start == end`, no waypoints; two controls → start+end, no waypoints | `MapyCzUrlBuilderTest` |
| 8 | More than 15 intermediate waypoints are reduced to ≤15 (start/end preserved, order preserved) | `MapyCzUrlBuilderTest` with 20 controls asserts `waypoints` count ≤ 15 |
| 9 | `routeType` defaults to `foot_hiking` and is overridable via `MapyRouteType` | `MapyCzUrlBuilderTest` (default + one override) |
| 10 | `OpenRouteInMapyCzUseCase` returns `Opened(url)` on success, `NoAppAvailable(url)` when the opener reports failure, `NoRoute` when no URL | `OpenRouteInMapyCzUseCaseTest` with a fake `UrlOpener` |
| 11 | `UrlOpener` resolves via Koin on both platforms; Swift accessor compiles into `Shared` | Koin resolution / app launch; iOS `xcodebuild` |
| 12 | `./gradlew :shared:allTests` green | Run tests |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
        NATIVE UI (later phase - NOT built here)
        a "Open in mapy.cz" button calls the use-case (via a ViewModel)
   ═══════════════════ SHARED CORE (:shared, commonMain) ═══════════════════
                          domain/usecase/
                 ┌───────────────────────────────────┐
                 │      OpenRouteInMapyCzUseCase        │
                 │  (RouteDetail) -> OpenRouteResult    │
                 └───────┬───────────────────┬─────────┘
             builds URL  │                   │ opens URL
        domain/mapy/      │                   │        core/ (platform seam)
   ┌──────────────────────▼──────┐     ┌──────▼───────────────────────────┐
   │       MapyCzUrlBuilder       │     │        interface UrlOpener         │
   │  (PURE: controls -> String?) │     │        fun open(url): Boolean      │
   │  + enum MapyRouteType        │     └──────┬───────────────────┬────────┘
   └──────────────────────────────┘   actual  │ (via platformModule)│ actual
       reads FR-02 ControlPoint /      ┌───────▼────────┐   ┌────────▼─────────┐
       FR-03 RouteDetail (no copy)     │ AndroidUrlOpener│   │   IosUrlOpener    │
                                       │ Intent.ACTION_  │   │ UIApplication     │
                                       │ VIEW (+NEW_TASK)│   │ .open (main queue)│
                                       └─────────────────┘   └──────────────────┘
                                                │                     │
                                        mapy.cz app if installed → else browser
```

**Data flow (open route):** UI (later) resolves the use-case → `OpenRouteInMapyCzUseCase(routeDetail)` → `MapyCzUrlBuilder.build(routeDetail.controls, routeType)` sorts controls by `ordinal`, maps each `GeoPoint(lat, lon)` to `"lon,lat"`, assembles `https://mapy.com/fnc/v1/route?start=…&end=…&waypoints=…&routeType=…` → use-case calls `UrlOpener.open(url)` → the platform impl fires an `Intent.ACTION_VIEW` (Android) / `UIApplication.open` (iOS) → the OS opens the mapy.cz app, or the browser as fallback → use-case returns `Opened` / `NoAppAvailable` / `NoRoute`.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Where code lives | Existing `:shared`, package layers `domain/mapy`, `domain/usecase`, `core`, `di` | project-stack §12 "layers as packages"; `domain/mapy` mirrors FR-02's pure `domain/geo/GeoUtils` |
| Link type | **Universal `https://mapy.com/fnc/v1/route` link** (verified on developer.mapy.com, Jul 2026) | mapy.cz opens its app when installed and falls back to the web otherwise - the required "works without the app → browser" behaviour comes free; no custom scheme, no install probe |
| Coordinate order | **Longitude first** (`lon,lat`), swapping `GeoPoint(latitude, longitude)` | mapy.cz URL format is `lon,lat`; `GeoPoint` stores `lat,lon` - the swap is the #1 correctness risk, so it is explicitly tested |
| Start / end / waypoints | `start` = first control by `ordinal`, `end` = last, middle = `waypoints` (`;`-separated, in order) | Matches the FR ("start + kontroly jako waypointy dle ordinal"); naturally degrades: 1 control → start==end, 2 → start+end, N → N-2 waypoints |
| Decimal formatting | Kotlin `Double.toString()` (locale-independent, always `.`) via a `formatCoordinate` helper | KMP-safe on all targets; **avoids** JVM `String.format`/`Locale` (not in commonMain and locale-dependent), keeping tests deterministic |
| Percent-encoding | **None** - coordinate strings contain only `[0-9 . , ; -]`, all URL-safe; must NOT encode structural `,`/`;` | mapy.cz expects literal commas/semicolons; over-encoding would break parsing. A guard test locks this |
| Waypoint cap | Clamp intermediate waypoints to **15** (mapy.cz max) by even down-sampling, keeping order + endpoints | mapy.cz limit; TA33 realistically has ~5 controls so it never triggers, but the builder stays correct for any content |
| `routeType` | Enum `MapyRouteType`, **default `FOOT_HIKING`** | TA33 is a pochod/běh on foot; enum keeps the string values in one typed place, overridable later (e.g. bike) |
| `navigate` param | **Omit** (default off) | Opening the *planned route* view is the intent; `navigate=true` force-starts turn-by-turn, which is aggressive as a default - left as a documented toggle (§12) |
| Platform seam shape | `commonMain` **`interface UrlOpener`** + platform impls bound in `platformModule` | This IS the KMP platform seam and mirrors the existing `SqlDriver` binding exactly; cleaner for DI + testing than an `expect class`, and Android needs the `Context` already available via `androidContext()` |
| Use-case input | Takes a ready **`RouteDetail`** (not a `routeId`) | Keeps FR-07 free of `RouteRepository` coupling; the FR-03 `RouteDetailViewModel` already holds the detail and will pass it |
| Opener return | `Boolean` (opened / no handler) → mapped to a sealed `OpenRouteResult` | Simple, testable with a fake; lets a later UI show a "no app/browser" message |
| iOS threading | `IosUrlOpener` dispatches `UIApplication.open` onto the main queue | `UIApplication` is main-thread-only; guarantees correctness regardless of caller dispatcher |

---

## 4. IMPLEMENTATION STEPS

> Execute steps in order. Do not skip. Paths under `shared/src/…/kotlin/com/example/ta33/` unless stated. Base package `com.example.ta33`.
> **Dependency ordering**: assumes **FR-02** (`GeoPoint`, `ControlPoint`, `appModule`, `kotlinx-coroutines-test`) and **FR-03** (`RouteDetail`) are implemented and referenced. Existing `platformModule` (`SqlDriver`), `di/Koin.kt`, and Napier are present. No new external dependencies.

### Step 1: Add the route-type enum
**Goal**: A typed home for the mapy.cz `routeType` values.
**Files**: `domain/mapy/MapyRouteType.kt`

```kotlin
package com.example.ta33.domain.mapy

/** mapy.cz `routeType` values (developer.mapy.com). Default for TA33 is on-foot hiking. */
enum class MapyRouteType(val apiValue: String) {
    FOOT_HIKING("foot_hiking"),
    FOOT_FAST("foot_fast"),
    BIKE_MOUNTAIN("bike_mountain"),
    BIKE_ROAD("bike_road"),
    CAR_FAST("car_fast"),
    CAR_FAST_TRAFFIC("car_fast_traffic"),
    CAR_SHORT("car_short"),
}
```
**Done when**: Compiles.

---

### Step 2: Add the pure `MapyCzUrlBuilder`
**Goal**: Deterministic control points → mapy.cz route URL. No platform, no I/O.
**Files**: `domain/mapy/MapyCzUrlBuilder.kt`

```kotlin
package com.example.ta33.domain.mapy

import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint

/**
 * Builds a universal mapy.cz route link: https://mapy.com/fnc/v1/route
 * Opens the mapy.cz app if installed, otherwise the browser (handled by the OS).
 * Pure & deterministic - unit-tested in commonTest.
 */
class MapyCzUrlBuilder {

    /**
     * @param controls route controls; order is normalised by [ControlPoint.ordinal] here.
     * @return the route URL, or null when there is nothing to navigate (no controls).
     */
    fun build(
        controls: List<ControlPoint>,
        routeType: MapyRouteType = MapyRouteType.FOOT_HIKING,
    ): String? {
        val ordered = controls.sortedBy { it.ordinal }
        if (ordered.isEmpty()) return null

        val start = ordered.first().location
        val end = ordered.last().location
        // intermediate = everything between first and last, capped to MAX_WAYPOINTS, order kept.
        val middle = if (ordered.size <= 2) emptyList()
                     else capWaypoints(ordered.subList(1, ordered.size - 1))

        val params = buildString {
            append("start=").append(coord(start))
            append("&end=").append(coord(end))
            if (middle.isNotEmpty()) {
                append("&waypoints=")
                append(middle.joinToString(WAYPOINT_SEPARATOR) { coord(it.location) })
            }
            append("&routeType=").append(routeType.apiValue)
        }
        return "$BASE_URL?$params"
    }

    /** GeoPoint(lat, lon) -> "lon,lat" (mapy.cz is longitude-first). */
    private fun coord(p: GeoPoint): String =
        "${formatCoordinate(p.longitude)},${formatCoordinate(p.latitude)}"

    /** Locale-independent decimal (Kotlin Double.toString always uses '.'), rounded to 6 dp (~0.1 m). */
    internal fun formatCoordinate(value: Double): String {
        val rounded = kotlin.math.round(value * 1_000_000.0) / 1_000_000.0
        return rounded.toString()
    }

    /** Even down-sample to at most MAX_WAYPOINTS while preserving order. */
    private fun capWaypoints(points: List<ControlPoint>): List<ControlPoint> {
        if (points.size <= MAX_WAYPOINTS) return points
        val step = points.size.toDouble() / MAX_WAYPOINTS
        return (0 until MAX_WAYPOINTS).map { i -> points[(i * step).toInt()] }
    }

    companion object {
        const val BASE_URL = "https://mapy.com/fnc/v1/route"
        const val WAYPOINT_SEPARATOR = ";"
        const val MAX_WAYPOINTS = 15
    }
}
```
> **No percent-encoding**: coordinate strings are only `[0-9 . , ; -]`; `,`/`;`/`&`/`=` are structural and must stay literal. `formatCoordinate` uses `Double.toString()` (locale-independent on every Kotlin target) - do **not** introduce `String.format`/`Locale`/`NSNumberFormatter`.

**Done when**: Compiles; a hand-traced 3-control route yields the expected URL (locked in Step 6).

---

### Step 3: Add the `UrlOpener` platform seam (common interface + actual impls)
**Goal**: A KMP seam to hand a URL to the OS, mirroring the `SqlDriver` pattern.
**Files**: `core/UrlOpener.kt` (commonMain), `core/UrlOpener.android.kt` (androidMain), `core/UrlOpener.ios.kt` (iosMain)

```kotlin
// core/UrlOpener.kt  (commonMain)
package com.example.ta33.core

/** Opens an external URL via the platform. Returns true if a handler (app/browser) accepted it. */
interface UrlOpener {
    fun open(url: String): Boolean
}
```
```kotlin
// core/UrlOpener.android.kt  (androidMain)
package com.example.ta33.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.aakira.napier.Napier

class AndroidUrlOpener(private val context: Context) : UrlOpener {
    override fun open(url: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // launched from Application context (Koin androidContext)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Napier.w(e) { "No activity to open URL" }
        false
    }
}
```
```kotlin
// core/UrlOpener.ios.kt  (iosMain)
package com.example.ta33.core

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

class IosUrlOpener : UrlOpener {
    override fun open(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(nsUrl)) return false
        // UIApplication must be touched on the main thread.
        dispatch_async(dispatch_get_main_queue()) {
            app.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
        return true
    }
}
```
> `canOpenURL` for an `https` URL is always allowed (no `LSApplicationQueriesSchemes` needed since it's not a custom scheme). If `openURL(options:completionHandler:)` interop signature differs on the pinned Kotlin/Native, use the deprecated `app.openURL(nsUrl)` overload - behaviour is equivalent for this use.

**Done when**: All three files compile on their targets.

---

### Step 4: Bind `UrlOpener` in each `platformModule`
**Goal**: Provide the platform impl via DI, exactly like `SqlDriver`.
**Files**: `di/PlatformModule.android.kt`, `di/PlatformModule.ios.kt` (edit)

Android (`androidContext()` is already available in this module):
```kotlin
single<UrlOpener> { AndroidUrlOpener(androidContext()) }
```
iOS:
```kotlin
single<UrlOpener> { IosUrlOpener() }
```
> Add the `import com.example.ta33.core.UrlOpener` (+ impl) to each file. `commonMain`'s `expect val platformModule` signature is unchanged.

**Done when**: `./gradlew build` + `xcodebuild … build` compile; Koin has a `UrlOpener` binding on both platforms.

---

### Step 5: Add `OpenRouteInMapyCzUseCase`
**Goal**: Build the URL from a `RouteDetail` and open it; report the outcome.
**Files**: `domain/usecase/OpenRouteInMapyCzUseCase.kt`

```kotlin
package com.example.ta33.domain.usecase

import com.example.ta33.core.UrlOpener
import com.example.ta33.domain.mapy.MapyCzUrlBuilder
import com.example.ta33.domain.mapy.MapyRouteType
import com.example.ta33.domain.model.RouteDetail

sealed interface OpenRouteResult {
    data class Opened(val url: String) : OpenRouteResult
    data class NoAppAvailable(val url: String) : OpenRouteResult // no app/browser accepted it
    data object NoRoute : OpenRouteResult                        // route has no controls
}

class OpenRouteInMapyCzUseCase(
    private val urlBuilder: MapyCzUrlBuilder,
    private val urlOpener: UrlOpener,
) {
    operator fun invoke(
        detail: RouteDetail,
        routeType: MapyRouteType = MapyRouteType.FOOT_HIKING,
    ): OpenRouteResult {
        val url = urlBuilder.build(detail.controls, routeType) ?: return OpenRouteResult.NoRoute
        return if (urlOpener.open(url)) OpenRouteResult.Opened(url)
               else OpenRouteResult.NoAppAvailable(url)
    }
}
```
**Done when**: Compiles; no platform/UI concerns leak in.

---

### Step 6: Register in Koin `appModule` + Swift accessor
**Goal**: Make the builder and use-case resolvable (mirrors FR-02/FR-03).
**Files**: `di/AppModule.kt` (edit), `di/Koin.kt` (edit)

`appModule`:
```kotlin
// FR-07 open route in mapy.cz
single { MapyCzUrlBuilder() }
factory { OpenRouteInMapyCzUseCase(get(), get()) } // MapyCzUrlBuilder, UrlOpener (from platformModule)
```
`Koin.kt` (Swift-facing accessor, mirrors existing `*ViewModel()` providers):
```kotlin
fun openRouteInMapyCzUseCase(): OpenRouteInMapyCzUseCase = KoinPlatform.getKoin().get()
```
> `UrlOpener` comes from `platformModule` (Step 4); `initKoin` already loads `platformModule` + `appModule`, so no wiring change beyond the two lines.

**Done when**: `./gradlew build` passes; Koin resolves the use-case (Step 7 / app launch); iOS builds.

---

### Step 7: Unit tests (`commonTest`)
**Goal**: Lock URL correctness and use-case behaviour. Pure - no DB, no platform.
**Files**: under `shared/src/commonTest/kotlin/com/example/ta33/`

- **`MapyCzUrlBuilderTest`**:
  - *coordinate order*: control at `GeoPoint(latitude = 50.0878, longitude = 14.4606)` → URL contains `start=14.4606,50.0878` (lon-first swap).
  - *ordering*: 3 controls given out of ordinal order → `start` = ordinal 1, `end` = ordinal 3, `waypoints` = ordinal 2.
  - *two controls*: `start` + `end`, **no** `waypoints` param.
  - *single control*: `start == end`, no `waypoints`, URL non-null.
  - *empty*: `build(emptyList())` → `null`.
  - *routeType*: default URL ends with `routeType=foot_hiking`; override `BIKE_MOUNTAIN` → `routeType=bike_mountain`.
  - *no over-encoding*: URL contains literal `,` and `;` (assert `"14.4606,50.0878"` and the `;` separator appear verbatim; assert no `%2C`/`%3B`).
  - *decimal formatting*: negative/near-integer coord (e.g. `-0.5`, `16.0`) renders with `.` and no locale artifacts / no scientific notation for the coordinate range used.
  - *waypoint cap*: 20 controls → `waypoints` split by `;` has ≤ 15 entries; `start`/`end` still the first/last ordinal.
  - *full-string golden*: assemble a known small route and assert the exact URL string.
- **`OpenRouteInMapyCzUseCaseTest`** (fake `UrlOpener`):
  - opener returns `true` → `Opened(url)` with the built URL; fake recorded exactly that URL.
  - opener returns `false` → `NoAppAvailable(url)`.
  - empty-controls `RouteDetail` → `NoRoute`; **fake opener never called**.

```kotlin
class FakeUrlOpener(private val result: Boolean) : UrlOpener {
    var lastUrl: String? = null; var callCount = 0
    override fun open(url: String): Boolean { lastUrl = url; callCount++; return result }
}
```
Use small `ControlPoint`/`RouteDetail` fixtures (FR-02/FR-03 models). These tests need no dispatcher (pure/synchronous).

**Done when**: `./gradlew :shared:allTests` is green.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| Route with 0 controls | No URL, no open attempt | `build` → `null` → use-case `NoRoute` |
| Route with 1 control | Valid URL, `start == end`, no waypoints | `first()`/`last()` are the same element; `middle` empty |
| Route with 2 controls | `start` + `end`, no waypoints | `size <= 2` → empty middle |
| More than 17 controls (>15 middle waypoints) | Waypoints down-sampled to 15, endpoints + order preserved | `capWaypoints` even sampling |
| Controls given out of `ordinal` order | Deterministic, sorted by `ordinal` first | `sortedBy { it.ordinal }` inside `build` |
| Duplicate/equal `ordinal` | Stable order (input order kept for ties) | `sortedBy` is stable |
| mapy.cz app not installed | Opens in browser automatically | Universal `https` link; OS resolves. `open` still returns `true` (a browser handles `https`) |
| No handler at all (no browser, exotic device) | `NoAppAvailable(url)`, no crash | Android catches `ActivityNotFoundException`; iOS `canOpenURL` false → `false` |
| Coordinate value like `16.0` / `-0.5` | Rendered `16.0` / `-0.5` with `.` | `Double.toString()` locale-independent; rounded to 6 dp |
| Very high-precision coordinate | Rounded to 6 dp (~0.1 m), plenty for navigation | `formatCoordinate` rounding |
| `open` called off the iOS main thread | Still correct | `IosUrlOpener` dispatches to `dispatch_get_main_queue()` |
| Malformed URL string on iOS | `NoAppAvailable` | `NSURL.URLWithString` returns `null` → `open` returns `false` |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: Coordinates originate from organizer-supplied content (FR-11 ingest, validated there), not free user text. They are numeric; the builder emits only `[0-9 . , ; -]`, so there is no query-injection surface. The use-case receives a typed `RouteDetail`, not a raw string.
- **Auth/Access control**: None - anonymous local participant in Etapa 1; no tokens (Keystore/Keychain is Etapa 2 per stack §4).
- **Sensitive data**: The route/control coordinates leave the device only by the user's explicit action of opening mapy.cz, and only via the OS handoff (a URL). No background transmission, no PII. This matches the store-approval story (location handling) in the zadání.
- **Logging**: Napier at `warn` only for "no handler" failures. Do **not** log full built URLs (they contain the whole route's coordinates) at info level; keep any URL logging at debug. Never log to persistent files.

---

## 7. ASSUMPTIONS

> User opted out of clarification (`skip questions` / `proceed directly`). Accepted defaults recorded here.

1. **FR-02 and FR-03 are implemented and referenced** - `GeoPoint(latitude, longitude)`, `ControlPoint(ordinal, location, …)`, and `RouteDetail(controls sorted by ordinal)` exist. FR-07 introduces no coordinate/route models. If wrong: FR-07 cannot compile; land FR-02/FR-03 first.
2. **The route's controls are the navigation waypoints** - start = first ordinal, end = last ordinal, middle = waypoints. There is no separate dedicated "start/finish" point in the model (FR-02 stores no route polyline). If a separate start/finish is later added, feed them explicitly to `build`.
3. **A universal `https://mapy.com/fnc/v1/route` link is the deep link** (verified on developer.mapy.com, Jul 2026) and gives app-or-browser fallback for free. If mapy.cz changes the format, only `MapyCzUrlBuilder` + its tests change. No custom URI scheme is used.
4. **`routeType` defaults to `foot_hiking`** (TA33 is on foot). If bike/other is needed, pass a different `MapyRouteType`. The label/toggle to choose it is a later UI concern.
5. **`navigate=true` is omitted** - the link opens the planned-route view, not forced turn-by-turn. Flippable later (§12) without a contract change.
6. **The `UrlOpener` seam is realized as a `commonMain` interface bound in `platformModule`** (like `SqlDriver`), not the `expect class` keyword. Equivalent KMP seam; better DI/test ergonomics and Android `Context` access. If the team prefers `expect/actual` classes, it's a localized swap.
7. **The use-case takes a `RouteDetail`** (already loaded by FR-03), keeping FR-07 free of repository/DB coupling. If a `routeId`-based entry point is later wanted, add a thin overload that fetches via `ObserveRouteDetailUseCase` - no change to the builder.
8. **Coordinates rounded to 6 decimals** (~0.1 m) - ample for navigation and keeps test strings stable. If full precision is ever required, drop the rounding (tests update).
9. **Only Android + iOS targets** - the two `UrlOpener` actuals suffice; no JS/desktop opener.
10. **No new dependencies** - reuses Napier, Koin, coroutines already present.

> Open questions live in Section 12.

---

## 8. QUICK REFERENCE

### Files to Modify
- `shared/src/androidMain/kotlin/com/example/ta33/di/PlatformModule.android.kt` - bind `AndroidUrlOpener`.
- `shared/src/iosMain/kotlin/com/example/ta33/di/PlatformModule.ios.kt` - bind `IosUrlOpener`.
- `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt` - register `MapyCzUrlBuilder` + `OpenRouteInMapyCzUseCase`.
- `shared/src/commonMain/kotlin/com/example/ta33/di/Koin.kt` - add `openRouteInMapyCzUseCase()` accessor.

### Files to Create
- `shared/src/commonMain/kotlin/com/example/ta33/domain/mapy/MapyRouteType.kt`
- `shared/src/commonMain/kotlin/com/example/ta33/domain/mapy/MapyCzUrlBuilder.kt`
- `shared/src/commonMain/kotlin/com/example/ta33/core/UrlOpener.kt`
- `shared/src/androidMain/kotlin/com/example/ta33/core/UrlOpener.android.kt`
- `shared/src/iosMain/kotlin/com/example/ta33/core/UrlOpener.ios.kt`
- `shared/src/commonMain/kotlin/com/example/ta33/domain/usecase/OpenRouteInMapyCzUseCase.kt`
- `shared/src/commonTest/kotlin/com/example/ta33/MapyCzUrlBuilderTest.kt`
- `shared/src/commonTest/kotlin/com/example/ta33/OpenRouteInMapyCzUseCaseTest.kt` (+ `FakeUrlOpener`)

### Dependencies
- **None new.** (Napier, Koin 4.1.0, coroutines, `kotlinx-coroutines-test` via FR-02 all present.)

### Commands
```bash
./gradlew build                      # compile all targets
./gradlew :shared:allTests           # shared unit tests (builder + use-case)
./gradlew :androidApp:assembleDebug  # Android sanity
# iOS headless (compiles the accessor + IosUrlOpener into Shared):
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
| **A. Pure `MapyCzUrlBuilder` (universal https link) + `UrlOpener` interface bound in `platformModule` + `OpenRouteInMapyCzUseCase`** | Reuses FR-02/FR-03 models with zero new deps; app-or-browser fallback free; builder fully unit-testable; matches `SqlDriver` seam + stack §12 | Adds one platform seam + two tiny actuals | ✅ |
| B. **Custom URI scheme / app-only deep link** (e.g. a `mapy://` style link) then manual "is installed?" probe with browser fallback | Could force the native app | Fragile/undocumented scheme; needs install detection (`queriesSchemes` on iOS, `PackageManager` on Android); the `https` link already does app-or-web automatically | - |
| C. **mapy.cz REST Routing API** (developer.mapy.com) to compute a route, then render it in-app | Full control of the polyline | Needs an API key + network (breaks offline), and it's FR-06's map job, not "open in mapy.cz navigation"; wrong scope for FR-07 | - |
| D. **`expect class UrlOpener`** (language `expect/actual`) instead of an interface bound in DI | No DI indirection | Harder to fake in `commonTest`; Android needs a `Context` that DI already supplies; diverges from the existing `SqlDriver` seam | - (interface chosen; trivially swappable) |
| E. **Builder emits an encoded query** (percent-encode everything) | "Safe by default" instinct | Would encode the structural `,`/`;` mapy.cz needs literal → broken links; coordinates need no encoding anyway | - |

**Why the selected approach won**: It delivers exactly FR-07's "open the route in mapy.cz" with the smallest, fully-testable pure builder plus one platform seam that mirrors the codebase's existing `SqlDriver` pattern, reuses the FR-02/FR-03 models without duplication, needs no new dependencies, and gets the "works even without the app installed → browser" requirement for free from the universal `https` link.

### 12.2 Open Questions

- [ ] **Should the link force turn-by-turn navigation (`navigate=true`)?** - Proposed direction: default **off** (open the planned route view); expose a `navigate: Boolean = false` param on `build`/use-case if the organizer wants one-tap navigation. Low-risk, additive.
- [ ] **Default `routeType` - `foot_hiking` vs `foot_fast`?** - Proposed direction: `foot_hiking` (trail-aware, fits the Adršpach/Teplice terrain). Confirm with the organizer; trivially changed via the enum default.
- [ ] **Do routes ever exceed 17 controls (so the 15-waypoint cap bites)?** - Proposed direction: TA33 has ~5, so no; the even-sampling cap keeps it correct regardless. Revisit only if content grows large.
- [ ] **Should FR-07 also offer a `routeId`-based entry point?** - Proposed direction: no for now - the FR-03 `RouteDetailViewModel` already holds the `RouteDetail`; add a fetching overload later only if a caller lacks the detail.

### 12.3 Suggestions & Follow-ups

- When the **UI phase** lands, add the "Open in mapy.cz" button to the FR-03 route-detail screen: it resolves `OpenRouteInMapyCzUseCase`, calls it with the current `RouteDetail`, and shows a snackbar/toast on `NoAppAvailable`/`NoRoute` (Compose + SwiftUI, localized strings).
- The `UrlOpener` seam is reusable - later FRs (e.g. "kontakt na pořadatele" in FR-10, or a privacy-policy link for store approval) can open `tel:`/`mailto:`/`https:` through the same interface.
- If mapy.cz later needs a **shape-preserving** route, revisit `capWaypoints` (current even-sampling is order-preserving but geometry-agnostic) or move to the REST Routing API under FR-06.
- Add a Koin **`checkModules()`** test spanning FR-01/02/03/07 to catch DI-graph breakage (incl. the new `UrlOpener` platform binding) early.

> Sections 9 (Design Reference) and 10 (Corrections From Current State) intentionally omitted: this is logic-only work with no UI/visual spec, and it is greenfield (no prior FR-07 implementation to correct - the `platformModule`/`AppModule`/`Koin.kt` touches are additive).
