# DEV Sandbox - Přepínače stavů appky na obrazovce Profil (iOS + Android, DEBUG only)

> **Summary**: Přidat na konec Profilu DEBUG-only sekci „Sandbox" se 4 přepínači (Zaplaceno, Aktivní běh, Data/mapa stažena, Běh dokončen), které přes sdílený `SandboxViewModel` a repozitáře reálně mění stav, ve kterém aplikace běží.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Pro vývoj a testování je potřeba rychle přepínat aplikaci mezi stavy, ve kterých může být (zaplaceno/ne, data stažena/ne, před akcí / na trase / dokončeno) bez ruční manipulace s DB nebo čekání na reálné flow (stažení, sken start/cíl). Dnes to jde jen přes `DevSeed` při startu nebo reálné akce - nelze to za běhu přepínat z UI.

### 1.2 Solution Overview
Na konec Profilu (iOS SwiftUI + Android Compose, **jen v DEBUG**) přidáme sekci „Sandbox" se 4 přepínači. Přepínače volají nový sdílený `SandboxViewModel`, který přes rozšířené repozitáře (nové aditivní metody: delete běhu, un-finish, reset přípravy, clear tras) a znovupoužité seedování z `RouteCatalog` reálně mění stav - takže se okamžitě projeví v celé appce (gate Příprava, scan kapsle, obsah Deníku, Profil sekce).

### 1.3 Scope: What This IS
- Sdílený `SandboxViewModel` + `SandboxUiState` (commonMain) se 4 přepínatelnými stavy a akcemi.
- Aditivní rozšíření repozitářů + SQLDelight dotazů (delete/reset/un-finish/clear).
- In-memory `SandboxState` pro „paid" override (dev-only mutable flag).
- DEBUG-only „Sandbox" sekce v iOS `ProfilView` a Android `ProfilContent`.
- Napojení „Zaplaceno" na kartu startovního čísla (v DEBUG čte override, v release `ProfileMock.paid`).

### 1.4 Scope: What This IS NOT
- **Žádná user-facing funkce** - čistě dev nástroj, v release buildu se sekce nevykreslí ani nezabírá.
- Žádná reálná platba/identita (Etapa 2) - „Zaplaceno" zůstává vizuální mock flag.
- Žádné reálné stahování přes síť pro „Data stažena ON" - seedujeme deterministicky z bundled `RouteCatalog` (jako `DevSeed`).
- Žádná změna produkčního chování `DevSeed` při startu.
- Žádné nové user-facing stringy v `composeResources` (dev sekce používá lokální DEBUG literály).

---

## 2. SUCCESS CRITERIA

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | `:androidApp:assembleDebug` + `xcodebuild -scheme iosApp` zelené; `./gradlew build` (regen SQLDelight) i `:shared:allTests` zelené | příkazy (§8) |
| 2 | V DEBUG je „Sandbox" poslední sekce Profilu (iOS + Android) se 4 přepínači | běh na simulátoru |
| 3 | „Zaplaceno" OFF → karta startovního čísla přestane zobrazovat „✓ Zaplaceno"; ON → zase ano | běh (Profil) |
| 4 | „Aktivní běh" ON → objeví se scan kapsle + Deník „na trase" + Profil „Tvoje akce"; OFF → zmizí (před akcí, QR karta) | běh |
| 5 | „Data akce / mapa stažena" OFF → aplikace se přepne na obrazovku Příprava (readiness NOT_READY); ON → zpět do READY shellu s obsahem | běh |
| 6 | „Běh dokončen" ON → běh dostane finální čas (Deník „Hotovo"), „Aktivní běh" se reaktivně přepne OFF (dokončený běh není aktivní); OFF → zpět na trasu | běh |
| 7 | Přepínače reflektují stav i když se změní jinudy (např. reálný sken cíle → „Běh dokončen" ON) | běh |
| 8 | Release build (`assembleRelease` / iOS Release) sekci neobsahuje | code review `#if DEBUG` / `BuildConfig.DEBUG` |
| 9 | Žádné destruktivní změny sdílených modelů; nové metody čistě aditivní | code review |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
  DEBUG UI (per platforma)                Shared (commonMain)                     Data (SQLDelight)
 ┌────────────────────────┐        ┌──────────────────────────────┐        ┌──────────────────────┐
 │ iOS  ProfilView         │        │ SandboxViewModel              │        │ RunRepository        │
 │   #if DEBUG Section      │──bind─▶│  state: StateFlow<Sandbox...> │◀──obs──│  observeLatestRun    │
 │   Toggles ×4             │        │  setPaid/setNaTrase/          │        │  clearFinished       │
 │                          │        │  setDownloaded/setFinished    │───────▶│  clearAllRuns        │
 │ Android ProfilContent    │        │                               │        │ PreparationRepository│
 │   if(BuildConfig.DEBUG)  │──bind─▶│  combine(                     │───────▶│  reset / markReady   │
 │   SettingRow+Switch ×4   │        │    SandboxState.paid,         │        │ RouteRepository      │
 └────────────────────────┘        │    runs.observeLatestRun,     │───────▶│  clearAll / upsert   │
                                     │    routes.observeRoutes,      │        │ SandboxState (paid)  │
   Karta „147" čte paid:            │    prep.observePreparation)   │        │  MutableStateFlow    │
   DEBUG → sandbox.paid             │  + DevContentSeeder (catalog) │        └──────────────────────┘
   release → ProfileMock.paid       └──────────────────────────────┘
```

**Tok:** přepínač → `SandboxViewModel.setX(bool)` (viewModelScope) → volá repo metody / seeder → DB/flow se změní → `ObserveAppStateUseCase`/`AppStateReducer` přepočítá `AppUiState` → celá appka reaguje (Příprava gate, scan kapsle, Deník, Profil). Stavy přepínačů se **odvozují** z týchž flows → self-consistent i při změně jinudy.

### 3.2 Run lifecycle - klíčová provázanost (rozhodnutí)

`selectActiveRun` = `WHERE finishedAtMillis IS NULL` → **dokončený běh není „aktivní"**. Proto:
- `naTrase` (Aktivní běh) = existuje běh s `finishedAtMillis == null`.
- `finished` (Hotovo) = poslední běh má `finishedAtMillis != null`.
- Tyto dva stavy jsou **vzájemně výlučné** (běh je buď na trase, nebo dokončený). „Hotovo ON" tedy nutně přepne „Aktivní běh" na OFF - to je věrné reálnému chování appky a je to **záměr**, ne bug (UI to zobrazí reaktivně).

Aby šel dokončený běh vrátit/smazat, potřebujeme číst **poslední** běh bez ohledu na finished → nový dotaz `selectLatestRun`.

### 3.3 Přepínače - sémantika akcí

| Přepínač | Odvození stavu | ON akce | OFF akce |
|---|---|---|---|
| **Zaplaceno** | `SandboxState.paid` | `paid.value = true` | `paid.value = false` |
| **Aktivní běh** | `activeRun != null` (latest & `finishedAt==null`) | zajistit *started, nedokončený* běh: pokud latest dokončený → `clearFinished`; jinak `createRun(routeId, participantId)` + `setStarted(now)` | `clearAllRuns()` (+ collected + trackpoints) → *před akcí* |
| **Data akce / mapa stažena** | `prep.status==READY \|\| routesPresent` | `DevContentSeeder.seed()` (upsert TA33 z katalogu) + `markReady` | `clearAllRuns()` → `routes.clearAll()` → `prep.reset()` → readiness NOT_READY (app → Příprava) |
| **Běh dokončen** | latest `finishedAt != null` | zajistit started běh (když není, nejdřív jako „Aktivní běh ON"), pak `setFinished(now)` | `clearFinished(latestId)` → *na trase* |

**Výběr trasy/účastníka pro createRun:** `routeId = activeRouteId(App state) ?: routes.observeRoutes().first().firstOrNull()?.id ?: RouteCatalog.TA33_ROUTE_ID` (po seedu). `participantId = ensureLocalParticipant().id`.

**Enable pravidla:** „Běh dokončen" je *disabled*, když neexistuje žádný běh (nedává smysl). „Data stažena OFF" implicitně smaže i běhy (běh referencuje trasu; bez tras je nekonzistentní).

### 3.4 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Kde žije logika | Sdílený `SandboxViewModel` (commonMain) | MVVM projektu; obě UI jen renderují stav + volají akce; iOS přes SKIE |
| „paid" persistence | **In-memory** `SandboxState` (MutableStateFlow) | Dev-only; vyhne se SQLDelight migraci `AppPreferences`. Reset na `true` po relaunch je pro dev OK (ostatní stavy jsou v DB → přežijí). Persistenci viz §12.3 |
| Run lifecycle | Dva přepínače + coupling přes `selectLatestRun` | Uživatel chtěl switche; coupling (finish→naTrase OFF) je věrný reálnému stavu |
| „Data stažena ON" zdroj | Reuse `RouteCatalog` seed (jako `DevSeed`), ne síť | Deterministické, offline, rychlé; extrahujeme `DevContentSeeder` sdílený s `DevSeed` |
| DI gating | VM registrován v `appModule` **vždy**; gate je jen v UI (`#if DEBUG`/`BuildConfig.DEBUG`) | Jednodušší než podmíněné DI; release UI VM nikdy nevytvoří |
| Stringy dev sekce | Lokální DEBUG literály (obě platformy) | Nezanáší `composeResources` dev-only klíči; povoleno pro dev nástroj |

---

## 4. IMPLEMENTATION STEPS

> Pořadí: shared data → shared VM → UI. Nepřeskakovat.

### Step 1: Přidat SQLDelight dotazy (delete / reset / un-finish / latest)
**Goal**: DB operace pro reset stavů.
**Files**: `shared/src/commonMain/sqldelight/com/example/ta33/data/db/{RunSession,CollectedControl,Trackpoint,Route,ControlPoint}.sq`

```sql
-- RunSession.sq
selectLatestRun:
SELECT * FROM RunSession ORDER BY rowid DESC LIMIT 1;

clearFinished:
UPDATE RunSession SET finishedAtMillis = NULL WHERE id = ?;

deleteAllRuns:
DELETE FROM RunSession;

-- CollectedControl.sq
deleteAllCollected:
DELETE FROM CollectedControl;

-- Trackpoint.sq
deleteAllTrackpoints:
DELETE FROM Trackpoint;

-- Route.sq
deleteAllRoutes:
DELETE FROM Route;

-- ControlPoint.sq
deleteAllControls:
DELETE FROM ControlPoint;
```

**Done when**: `./gradlew :shared:generateCommonMainTa33DatabaseInterface` (nebo `./gradlew build`) vygeneruje metody bez chyby.

---

### Step 2: Rozšířit repozitáře (interface + impl) - aditivně
**Goal**: doménové metody nad novými dotazy.
**Files**: `domain/repository/{RunRepository,PreparationRepository,RouteRepository}.kt`, `data/repository/{RunRepositoryImpl,PreparationRepositoryImpl,RouteRepositoryImpl}.kt`

- `RunRepository`: `fun observeLatestRun(): Flow<RunSession?>`, `suspend fun getLatestRun(): RunSession?`, `suspend fun clearFinished(runId: String)`, `suspend fun clearAllRuns()` (v transakci: deleteAllRuns + deleteAllCollected + deleteAllTrackpoints).
- `PreparationRepository`: `suspend fun reset()` → `pq.upsertPreparation(PreparationStatus.NOT_STARTED.name, null, null)` + `clearAssets()`.
- `RouteRepository`: `suspend fun clearAll()` → transakce deleteAllRoutes + deleteAllControls.

**Done when**: `./gradlew :shared:compileKotlinMetadata` (nebo build) projde; metody dostupné.

---

### Step 3: `SandboxState` (in-memory paid override)
**Goal**: dev-mutable „paid".
**Files**: `shared/src/commonMain/kotlin/com/example/ta33/dev/SandboxState.kt` (create)

```kotlin
package com.example.ta33.dev
import kotlinx.coroutines.flow.MutableStateFlow

/** DEV-only mutable overrides for the Sandbox panel (not persisted). */
class SandboxState {
    val paid = MutableStateFlow(com.example.ta33.presentation.ProfileMock.paid) // default = release value
}
```

**Done when**: kompiluje; registrováno v Koin (Step 5).

---

### Step 4: Extrahovat `DevContentSeeder` (reuse v DevSeed + Sandbox)
**Goal**: znovupoužitelný seed TA33 z katalogu bez duplikace.
**Files**: `shared/src/commonMain/kotlin/com/example/ta33/dev/DevContentSeeder.kt` (create); upravit `DevSeed.kt` aby ho volal.

- `DevContentSeeder(catalog, routes, prep)` s `suspend fun seed()`: přesun logiky z `DevSeed.seedIfEmpty` (upsert TA33 Route+ControlPoints z `RouteCatalog` + `prep.markReady(1)`), bez části s runem. `DevSeed` pak volá `seeder.seed()` (uvnitř své `if empty` guardy + run seed zůstává v DevSeed).

**Done when**: `DevSeed` beze změny chování; `DevContentSeeder.seed()` volatelný samostatně (idempotentní upsert).

---

### Step 5: `SandboxViewModel` + `SandboxUiState` + DI
**Goal**: sdílená orchestrace přepínačů.
**Files**: `presentation/SandboxViewModel.kt`, `presentation/SandboxUiState.kt` (create); `di/AppModule.kt` (+`SandboxState` single, +`SandboxViewModel`), `di/Koin.kt` (`ViewModelProvider.sandboxViewModel()`).

```kotlin
data class SandboxUiState(
    val paid: Boolean = true,
    val naTrase: Boolean = false,
    val downloaded: Boolean = true,
    val finished: Boolean = false,
    val runExists: Boolean = false,
)

class SandboxViewModel(
    private val sandbox: SandboxState,
    private val runs: RunRepository,
    private val routes: RouteRepository,
    private val prep: PreparationRepository,
    private val seeder: DevContentSeeder,
    private val ensureParticipant: EnsureLocalParticipantUseCase,
    private val time: TimeProvider,
) : ViewModel() {
    val state: StateFlow<SandboxUiState> = combine(
        sandbox.paid, runs.observeLatestRun(), routes.observeRoutes(), prep.observePreparationState(),
    ) { paid, latest, routeList, preparation ->
        SandboxUiState(
            paid = paid,
            naTrase = latest != null && latest.finishedAtMillis == null,
            finished = latest?.finishedAtMillis != null,
            runExists = latest != null,
            downloaded = preparation.status == PreparationStatus.READY || routeList.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SandboxUiState())

    fun setPaid(on: Boolean) { sandbox.paid.value = on }

    fun setNaTrase(on: Boolean) = viewModelScope.launch {
        if (on) ensureStartedRun() else runs.clearAllRuns()
    }
    fun setFinished(on: Boolean) = viewModelScope.launch {
        val run = ensureStartedRun()               // finish potřebuje started běh
        if (on) runs.setFinished(run.id, time.nowMillis()) else runs.clearFinished(run.id)
    }
    fun setDownloaded(on: Boolean) = viewModelScope.launch {
        if (on) seeder.seed()
        else { runs.clearAllRuns(); routes.clearAll(); prep.reset() }
    }

    private suspend fun ensureStartedRun(): RunSession {
        runs.getLatestRun()?.let { existing ->
            if (existing.finishedAtMillis != null) runs.clearFinished(existing.id)
            if (existing.startedAtMillis == null) runs.setStarted(existing.id, time.nowMillis())
            return runs.getLatestRun()!!
        }
        if (routes.observeRoutes().first().isEmpty()) seeder.seed()
        val routeId = routes.observeRoutes().first().firstOrNull()?.id ?: RouteCatalog.TA33_ROUTE_ID
        val run = runs.createRun(routeId, ensureParticipant().id)
        runs.setStarted(run.id, time.nowMillis())
        return run
    }
}
```
- `AppModule.kt`: `single { SandboxState() }`, `single { DevContentSeeder(get(), get(), get()) }`, `single { SandboxViewModel(get(), get(), get(), get(), get(), get(), get()) }` (Android/iOS oba čtou z Koin). Upravit `DevSeed` konstruktor, pokud přebírá seeder.
- `Koin.kt`: `fun sandboxViewModel(): SandboxViewModel = KoinPlatform.getKoin().get()`.

**Done when**: `./gradlew build` + `:shared:allTests` zelené.

---

### Step 6: iOS - `SandboxModel` + DEBUG sekce v `ProfilView`
**Goal**: nativní Toggle sekce + napojení „paid".
**Files**: `iosApp/iosApp/UI/Profil/SandboxModel.swift` (create), `ProfilView.swift`, `ProfilModel.swift` (paid override čtení)

- `SandboxModel: ObservableObject` (jako `ScanFlowModel`): drží `@Published var state: SandboxUiState`, `observe()` čte `viewModel.state` (SKIE async), akce delegují na `ViewModelProvider.shared.sandboxViewModel()`.
- `ProfilView`: `#if DEBUG` přidat jako **poslední** `Section("Sandbox")` s `Toggle`y (Zaplaceno, Aktivní běh, Data / mapa stažena, Běh dokončen - poslední `.disabled(!state.runExists)`), `.tint(Ta33Color.orange)`. `Toggle(isOn: Binding(get: { state.x }, set: { model.setX($0) }))`.
- Karta startovního čísla: `let paid = { #if DEBUG return sandbox.state.paid #else return ProfileMock.shared.paid #endif }()` - nebo jednodušeji v DEBUG číst `sandbox.state.paid`, v release `ProfileMock.paid`.

**Done when**: `xcodebuild` OK; na simulátoru (DEBUG) sekce funguje.

---

### Step 7: Android - DEBUG sekce v `ProfilContent` + wiring v `ProfilScreen`
**Goal**: parita na Androidu.
**Files**: `androidApp/.../ui/profil/ProfilContent.kt`, `ProfilScreen.kt`; ověřit `androidApp/build.gradle.kts` `buildFeatures { buildConfig = true }`

- `ProfilScreen.kt`: získat `SandboxViewModel` (Koin `koinViewModel()` nebo `ViewModelProvider`), collectAsState, předat `sandboxState` + callbacky do `ProfilContent`.
- `ProfilContent.kt`: `if (BuildConfig.DEBUG) { SandboxCard(...) }` jako poslední - `PaperCard` s `SettingRow` + Material `Switch` pro 4 stavy (poslední `enabled = runExists`). „Zaplaceno" karta čte `if (BuildConfig.DEBUG) sandboxState.paid else ProfileMock.paid`.
- Ověřit, že `BuildConfig` je dostupný (AGP 8+/9 vyžaduje `buildConfig = true`); pokud ne, zapnout.

**Done when**: `:androidApp:assembleDebug` OK; `@Preview` (mock SandboxUiState) vykreslí sekci.

---

### Step 8: Build & vizuální ověření
**Files**: -
- `./gradlew build` (regen DB) + `:androidApp:assembleDebug` + `xcodebuild -scheme iosApp` + `:shared:allTests`.
- iOS simulátor: projít §2 kritéria 2-7 (přepnout každý stav, ověřit reakci appky).

**Done when**: všechna kritéria §2.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected | Handle |
|---|---|---|
| „Hotovo ON" | běh dokončen → „Aktivní běh" reaktivně OFF | odvození z `selectLatestRun`; UI reflektuje |
| „Hotovo" bez běhu | přepínač disabled | `enabled = runExists` |
| „Aktivní běh OFF" při dokončeném běhu | smaže i dokončený běh (clearAllRuns) | před akcí |
| „Data stažena OFF" s aktivním během | běh smazán, trasy pryč → NOT_READY (Příprava) | `clearAllRuns` před `routes.clearAll` |
| „Data stažena ON" opakovaně | idempotentní upsert z katalogu | `INSERT OR REPLACE` + markReady |
| Rychlé přepínání | akce v `viewModelScope`, DB serialized | poslední stav vyhrává; flows dorovnají |
| Reálný sken cíle mimo Sandbox | „Hotovo" se sám zapne | odvození ze stavu, ne lokální flag |
| Release build | sekce se nevykreslí | `#if DEBUG` / `BuildConfig.DEBUG` |
| createRun bez tras | seed nejdřív, pak createRun | `ensureStartedRun` seeduje když prázdno |

---

## 6. SECURITY CONSIDERATIONS
- **DEBUG-only**: sekce ani destruktivní akce (clearAllRuns/clearAll/reset) se v release nesmí objevit → `#if DEBUG` / `BuildConfig.DEBUG`. `SandboxViewModel` v DI je neškodný (release UI ho nevytvoří).
- **Data loss**: akce mažou lokální běhy/trasy - přijatelné (dev nástroj, Etapa-1 data jsou lokální mock/seedovatelná). Žádná cloud data.
- **Sensitive data**: žádná (mock identita). „paid" je vizuální flag, ne reálná platba.
- **Logging**: nelogovat; žádné PII.

---

## 7. ASSUMPTIONS
1. **Scope potvrzen uživatelem** (`AskUserQuestion`): 4 stavy (Zaplaceno, Aktivní běh, Data/mapa stažena, Běh dokončen), platformy iOS + Android, **DEBUG only**. Zaznamenáno jako přijaté.
2. **„paid" in-memory** (ne persistováno) - reset na `true` po relaunch je pro dev OK; ostatní stavy jsou v DB (přežijí). Když by to vadilo → persistovat (viz §12.3).
3. **„Data stažena" = TA33 seed z `RouteCatalog`** (ne reálné síťové stažení) - deterministické a offline; stačí pro přepínání gate readiness.
4. **`BuildConfig.DEBUG` je dostupný** v `androidApp` (nebo se doplní `buildConfig = true`).
5. **Aditivní SQL/repo změny** nerozbijí existující migrace (nové dotazy nad stejným schématem, žádná změna tabulek → žádná SQLDelight migrace).
6. **Coupling běh↔hotovo** (finish → naTrase OFF) je akceptovaný jako věrný reálnému stavu, ne bug.

> Otevřené otázky viz §12.

---

## 8. QUICK REFERENCE

### Files to Create
- `shared/.../dev/SandboxState.kt`, `shared/.../dev/DevContentSeeder.kt`
- `shared/.../presentation/SandboxViewModel.kt`, `shared/.../presentation/SandboxUiState.kt`
- `iosApp/iosApp/UI/Profil/SandboxModel.swift`

### Files to Modify
- SQLDelight: `RunSession.sq`, `CollectedControl.sq`, `Trackpoint.sq`, `Route.sq`, `ControlPoint.sq`
- `domain/repository/{RunRepository,PreparationRepository,RouteRepository}.kt`
- `data/repository/{RunRepositoryImpl,PreparationRepositoryImpl,RouteRepositoryImpl}.kt`
- `dev/DevSeed.kt` (reuse seeder), `di/AppModule.kt`, `di/Koin.kt`
- iOS: `ProfilView.swift`, `ProfilModel.swift`
- Android: `ui/profil/ProfilContent.kt`, `ui/profil/ProfilScreen.kt`, příp. `androidApp/build.gradle.kts` (`buildConfig=true`)

### Commands
```bash
./gradlew build                    # regeneruje SQLDelight + kompiluje shared
./gradlew :androidApp:assembleDebug
./gradlew :shared:allTests
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'id=<sim-id>' CODE_SIGNING_ALLOWED=NO build
```

---

## 9. DESIGN REFERENCE

### Visual Spec
Dev nástroj - bez design-system mockupu. Sekce se **vizuálně podřídí Profilu** (nativní řádky se Switch/Toggle), aby nevyčnívala, ale je jasně označená „Sandbox" (volitelně 🧪). Native look per platforma (memory `native-look-per-platform`).

### Component/Screen Mapping
- iOS: `Section("Sandbox")` v `ProfilView` (List insetGrouped) + `Toggle` řádky (jako sekce „Nastavení").
- Android: `SandboxCard` = `PaperCard` + `SettingRow` + `Switch` v `ProfilContent` (jako `SettingsCard`).

### Style Mapping
| Design Spec | Code Equivalent | Value |
|---|---|---|
| Toggle/Switch akcentní | iOS `.tint(Ta33Color.orange)` / Android `Switch` colors primary | brand orange |
| Sekce label | iOS `Section("Sandbox")` / Android `Overline("Sandbox")` | overline styl |
| Řádek | reuse existující `SettingRow`/Toggle řádek | 1px `slate-100` divider |
| Karta (Android) | `PaperCard` | radius 20 |

---

## 10. CORRECTIONS FROM CURRENT STATE

| What | Before | After |
|---|---|---|
| Přepínání stavů appky | jen `DevSeed` při startu / reálné akce | za běhu z Profil „Sandbox" (DEBUG) |
| `paid` | `ProfileMock.paid` const, čteno napřímo v UI | DEBUG čte `SandboxState.paid` (přepínatelné); release beze změny čte const |
| `RunRepository` | bez delete / un-finish / latest | +`observeLatestRun`/`getLatestRun`/`clearFinished`/`clearAllRuns` |
| `PreparationRepository` | bez reset na NOT_STARTED | +`reset()` |
| `RouteRepository` | bez clearAll | +`clearAll()` |
| `DevSeed` | seed logika inline | seed obsahu extrahován do `DevContentSeeder` (chování zachováno) |

---

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-11 | Initial plan created |

---

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES

### 12.1 Alternative Approaches Considered

| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Sdílený `SandboxViewModel` + aditivní repo metody + reuse katalog seed** | Věrné MVVM projektu; reálné přepínání stavů; obě platformy sdílí logiku; deterministické | Nové repo metody + SQL; run/hotovo coupling k vysvětlení | ✅ |
| B. Přímé volání repo z UI (bez VM) | Méně nové shared plumbing | Duplikace logiky iOS×Android; porušuje MVVM; těžší test | - |
| C. „Data stažena ON" přes reálný `PrepareOfflinePackageUseCase` (síť) | Realistické (skutečné stažení) | Vyžaduje síť/async/progress; nedeterministické; pomalé pro dev toggle | - |
| D. Run lifecycle jako 1 segmented control (Žádný/Na trase/Hotovo) | Čistě modeluje výlučnost stavů | Uživatel chtěl switche; větší UI odchylka | - |

**Why the selected approach won**: Uživatel chce reálné přepínání stavů ze switchů; sdílený VM + aditivní repo metody dodrží architekturu (MVVM, shared core) a přes reuse `RouteCatalog`/`DevSeed` je to deterministické a offline.

### 12.2 Open Questions
- [ ] **Un-finish vs. nový běh při „Hotovo OFF"** - Proposed: `clearFinished` na latest (vrátí přesně týž běh na trasu); jednodušší a intuitivní.
- [ ] **„Data stažena ON" - seed jen TA33, nebo i TA50?** - Proposed: TA33 (jako `DevSeed`); TA50 není potřeba pro gate.
- [ ] **Segmented control místo 2 switchů pro běh?** - Proposed: nechat switche dle zadání; kdyby coupling mátl, přejít na D (§12.1).

### 12.3 Suggestions & Follow-ups
- Persistovat „paid" (sloupec v `AppPreferences` + migrace, nebo `multiplatform-settings`), ať přežije relaunch jako ostatní stavy.
- Přidat do Sandboxu další stavy: poloha permission (LocationPermissionStatus), sync status, „reset vše" tlačítko (clearAllRuns+clearAll+reset), přepínač trasy TA33/TA50.
- Zvážit sdílený `expect val isDebug` v commonMain, aby šlo gate i ve shared (teď gate jen v UI).
- Po Etapě 2 „Zaplaceno" napojit na reálný platební stav (SPD QR / rezervace) místo mock flagu.
