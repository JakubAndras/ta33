# RD-00 — Redesign Foundation: bohatý route model + statický katalog (mock)

> **Summary**: Rozšířit sdílený doménový model o plný itinerář (waypointy se značkami/směry, km, úseky), výškový profil a metadata trasy (ascent/descent, letter), přidat statický **RouteCatalog** (TA33 + TA50 portovaný z designového `TrasaData.jsx` jako mock obsah) + repository, a přepsat DevSeed tak, aby appka nabíhala do READY s aktivním během. Prerekvizita pro redesign obrazovek Deník/Mapa/Profil.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Aktuální design (Deník = `VariantPrehled`, Mapa = `VariantHybrid`) stojí na bohatém route modelu z `TrasaData.jsx`: **plný itinerář** (~19–24 bodů, nejen kontroly) se **značkami KČT** a **směrovými šipkami**, **km pozicemi**, **úseky**, **výškovým profilem**, **stoupáním/klesáním** a **dvěma trasami TA33/TA50**. Náš sdílený model (`Route`, `ControlPoint`) nese jen kontroly + jméno + GPS — pro tyto obrazovky nedostačuje.

### 1.2 Solution Overview
Přidáme doménové modely pro itinerář + výškový profil, **statický `RouteCatalog`** (mock obsah TA33 + TA50 věrně portovaný z designového `TrasaData.jsx`) a `RouteCatalogRepository`. Katalog je zároveň zdrojem pro **DevSeed**: nasype `ControlPoint`y (s přibližnými GPS pro FR-08 logiku), označí přípravu READY a vytvoří + nastartuje běh na TA33 (2 kontroly sebrané). Obrazovky pak čtou plný itinerář/elevaci z katalogu a stav běhu z existujících repozitářů.

### 1.3 Scope: What This IS
- Doménové modely: `TrailMark`, `TurnDirection`, `WaypointKind`, `RouteWaypoint`, `ElevationProfile`, `RouteItinerary`.
- Statický `RouteCatalog` (TA33 + TA50) portovaný z `TrasaData.jsx` (mock obsah pro vizuál).
- `RouteCatalogRepository` (interface + impl) + Koin registrace.
- Přepsaný `DevSeed`: seeduje z katalogu (ControlPointy s GPS), READY, běh + 2 sebrané kontroly.
- Build + testy zelené; iOS framework link.

### 1.4 Scope: What This IS NOT
- **UI obrazovky** (Deník/Mapa/Profil) — samostatné navazující plány (RD-01/02/03).
- **Perzistence itineráře/elevace do SQLDelight + FR-11 content JSON** — zatím statický katalog (mock). Rozšíření DB/JSON = flagovaný follow-up.
- Reálné GPS souřadnice kontrol — přibližné (mock); přesné dodá zadavatel.
- Reálná mapa (MapLibre) — Mapa použije schematickou mapu (RD-02).

---

## 2. SUCCESS CRITERIA
| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | `./gradlew build` + `:shared:allTests` zelené; iOS framework link zelený | příkazy |
| 2 | Modely `RouteWaypoint`/`ElevationProfile`/`RouteItinerary` + enumy kompilují (commonMain) | build |
| 3 | `RouteCatalog` vrací TA33 (5 kontrol) i TA50 (6 kontrol) s plným itinerářem, značkami, směry, km a elevací — věrně dle `TrasaData.jsx` | unit test na počty/hodnoty |
| 4 | `RouteCatalogRepository` Koin-resolvable; `getItinerary("dev-ta33"/…)` + `observeItineraries()` fungují | test / build |
| 5 | DevSeed z katalogu: po startu appky readiness READY, aktivní běh na TA33, 2 kontroly sebrané | (ověří se na obrazovkách; zde build + krátký VM/seed test) |
| 6 | Existující FR-04/FR-08 logika (deník/sběr) dál funguje s naseedovanými ControlPointy | `:shared:allTests` |

---

## 3. TECHNICAL DESIGN

### 3.1 Modely (commonMain/domain/model)
```kotlin
enum class TrailMark { MODRA, ZELENA, ZLUTA, CERVENA, VLASTNI, CYKLO }
enum class TurnDirection { UP, DOWN, LEFT, RIGHT, LEFT_UP, LEFT_RIGHT, STRAIGHT }
enum class WaypointKind { START, CONTROL, FINISH, WAYPOINT }

data class RouteWaypoint(
    val index: Int,                 // pořadí v itineráři (0-based)
    val name: String,               // "Zámek Bischofstein"
    val km: Double,                 // pozice od startu, např. 5.9
    val kind: WaypointKind,
    val controlOrdinal: Int? = null,// vyplněno jen pro kind==CONTROL (1..N)
    val direction: TurnDirection = TurnDirection.STRAIGHT,
    val mark: TrailMark = TrailMark.VLASTNI,
    val markNumber: String? = null, // číslo cyklotrasy (mark==CYKLO)
)

data class ElevationProfile(
    val pointsNormalized: List<Double>, // 0..1 výška, jako TrasaData.elev
    val lowMeters: Int,                 // 462
    val highMeters: Int,                // 727
    val ascentMeters: Int,              // 740
    val descentMeters: Int,             // 740
    val kmTotal: Double,                // 33.2
    val tickKm: List<Int>,              // [8,16,24]
)

data class RouteItinerary(
    val routeId: String,
    val letter: String,      // "A" / "B"
    val name: String,        // "Teplicko-Adršpašská 33"
    val shortId: String,     // "TA33" / "TA50" (header)
    val distanceKm: Double,  // 33.2
    val ascentMeters: Int,
    val descentMeters: Int,
    val controlsCount: Int,
    val elevation: ElevationProfile,
    val waypoints: List<RouteWaypoint>,
)
```
Existující `Route`/`ControlPoint` zůstávají (běhová/geofence logika). Kontroly = waypointy `kind==CONTROL`.

### 3.2 Statický katalog
`data/content/RouteCatalog.kt` (nebo `dev/`) — **věrný port `TrasaData.jsx`** (TA33 + TA50): `rows` → `RouteWaypoint` (mapování `kind` start/kp/finish/normal, `mark` mod/zel/zlu/cer/vl/cyklo → enum, `dir` → `TurnDirection`, `km` parse s čárkou→tečkou), `elev`/`elevLow`/`elevHigh`/`ascent`/`descent`/`elevKm`/`elevTicks` → `ElevationProfile`. `RouteCatalog.itineraries: List<RouteItinerary>`, `RouteCatalog.byId(routeId)`.
Pro `kind==CONTROL` přiřaď **přibližný `GeoPoint`** (oblast Adršpach/Teplice ~50.59–50.62 N, 16.11–16.17 E) — pro FR-08 logiku.

> Agent: přečti kanonický zdroj přes MCP `claude-design` (project_id `5105d303-ffec-4143-864d-a61d529f3c9d`, path `ui_kits/ta33-app/TrasaData.jsx`) a portuj data 1:1 (obě trasy, všechny řádky, elev pole).

### 3.3 Repository
```kotlin
interface RouteCatalogRepository {
    fun observeItineraries(): Flow<List<RouteItinerary>>   // [TA33, TA50]
    suspend fun getItinerary(routeId: String): RouteItinerary?
}
class StaticRouteCatalogRepository : RouteCatalogRepository { /* z RouteCatalog */ }
```
Koin: `single<RouteCatalogRepository> { StaticRouteCatalogRepository() }`. Swift accessor netřeba (obrazovkové VM ho spotřebují a vystaví přes SKIE samy).

### 3.4 DevSeed (přepis)
Seeduje **z katalogu**: pro TA33 upsertni `Route` + `ControlPoint`y (kind==CONTROL waypointy → ControlPoint s GPS z katalogu), `prep.markReady(1)`, `ensureParticipant()`, `createRun(routeId, participantId)`, `setStarted(now - 44 min)`, sebrat první 2 kontroly. RouteId sjednotit s katalogem (např. `dev-ta33`). Idempotentní (jen když prázdno).

---

## 4. IMPLEMENTATION STEPS

### Step 1: Doménové modely
**Files**: `domain/model/{TrailMark,TurnDirection,WaypointKind,RouteWaypoint,ElevationProfile,RouteItinerary}.kt` (create). Viz §3.1.
**Done when**: kompiluje.

### Step 2: RouteCatalog (port TrasaData)
**Files**: `data/content/RouteCatalog.kt` (create).
Přečti `TrasaData.jsx` přes design MCP a portuj TA33 + TA50 věrně (§3.2). Zahrň přibližné GPS pro kontroly.
**Done when**: `RouteCatalog.byId("dev-ta33")` vrací TA33 s 5 kontrolami; TA50 s 6; elevace i značky sedí.

### Step 3: RouteCatalogRepository + DI
**Files**: `domain/repository/RouteCatalogRepository.kt`, `data/repository/StaticRouteCatalogRepository.kt` (create), `di/AppModule.kt` (register).
**Done when**: Koin resolve; `observeItineraries()` emituje [TA33, TA50].

### Step 4: Přepis DevSeed
**Files**: `dev/DevSeed.kt` (modify).
Seed z katalogu (§3.4). Přidej do DI konstruktoru `RouteCatalogRepository` pokud potřeba.
**Done when**: build; seed idempotentní; readiness READY + aktivní běh.

### Step 5: Testy + ověření
**Files**: `commonTest/.../RouteCatalogTest.kt` (create) — počty kontrol/waypointů, km monotónní, elevace low<high, obě trasy.
Spusť `./gradlew build` + `:shared:allTests`. Ověř, že FR-04/FR-08 testy dál procházejí.
**Done when**: vše zelené.

---

## 5. EDGE CASES & ERRORS
| Scenario | Expected | Handle |
|---|---|---|
| km s čárkou ("14,1") | parse na 14.1 | replace ','→'.' při portu |
| cyklo bez čísla | mark CYKLO, markNumber null/"" | ošetřit |
| kontrola bez GPS | přibližné coords z katalogu | přiřadit při portu |
| seed už proběhl | no-op | idempotence (routes/catalog check) |

## 6. SECURITY CONSIDERATIONS
- Mock data, žádná citlivá informace. Přibližné GPS nejsou reálné pozice kontrol.

## 7. ASSUMPTIONS
1. **Mock katalog místo SQLDelight/JSON** pro itinerář/elevaci — vizuál teď; reálný FR-11 content pipeline (perzistence těchto polí) je follow-up.
2. **Kontroly dostanou přibližné GPS** z katalogu (mock) — reálné dodá zadavatel.
3. **Existující Route/ControlPoint zůstávají** pro běhovou/geofence logiku; katalog je navíc pro display.
4. **Screen VM** (Deník/Mapa/Profil) se staví v navazujících plánech; foundation dává jen modely + katalog + repo + seed.

## 8. QUICK REFERENCE
### Files to Create
- `domain/model/{TrailMark,TurnDirection,WaypointKind,RouteWaypoint,ElevationProfile,RouteItinerary}.kt`
- `data/content/RouteCatalog.kt`
- `domain/repository/RouteCatalogRepository.kt`, `data/repository/StaticRouteCatalogRepository.kt`
- `commonTest/.../RouteCatalogTest.kt`
### Files to Modify
- `di/AppModule.kt` (register), `dev/DevSeed.kt` (seed z katalogu)
### Commands
```bash
./gradlew build
./gradlew :shared:allTests
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

## 9. DESIGN REFERENCE
- Kanonický datový zdroj: MCP `claude-design` project `5105d303-ffec-4143-864d-a61d529f3c9d`, path `ui_kits/ta33-app/TrasaData.jsx` (TA33 + TA50 rows + elev). Port 1:1.
- `.claude/design/design-system.md`.

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before | After |
|---|---|---|
| Route model | jen kontroly (jméno+GPS) | + plný itinerář (značky/směry/km), elevace, ascent/descent, letter |
| Route obsah | jen SQLDelight seed | + statický katalog TA33/TA50 (mock) |
| DevSeed | ad-hoc trasa | seed z katalogu (sjednoceno) |

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-10 | Initial plan created |

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES
### 12.1 Alternatives
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Statický Kotlin katalog (mock), model navíc** | Rychlé, žádná DB migrace, věrný vizuál teď | Dvojí zdroj (katalog + ControlPoint) | ✅ |
| B. Rozšířit SQLDelight + FR-11 JSON hned | Architektonicky čisté, jeden zdroj | Velká migrace + content pipeline, pomalé | — |
| C. Data jen v UI vrstvě per platforma | Nejmenší sdílený zásah | Duplikace mock dat Android×iOS, nekonzistence | — |
### 12.2 Open Questions
- [ ] **Přibližné GPS kontrol** — Proposed: rozumné coords v oblasti; nahradit reálnými od zadavatele.
- [ ] **Sjednocení katalog × SQLDelight** — Proposed: až přijde reálný FR-11 content, portovat katalogová pole do JSON/DB a katalog zrušit.
### 12.3 Suggestions & Follow-ups
- FR-11 content JSON + SQLDelight rozšíření o waypointy/elevaci (nahradí mock katalog).
- Reálné GPS + reálná elevace od zadavatele.
- Reálná mapa (MapLibre) místo schematické (RD-02).
