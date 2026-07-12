# RD-01 - Deník (VariantPrehled) - Android + iOS

> **Summary**: Přepsat obrazovku Deník na kanonický design `VariantPrehled`: tmavý header (TRASA/TA33/Přepnout + Délka/Čas startu/Finální čas), timeline „Kontroly na trase" (start/kontroly/cíl s km, úsekem, mezičasem a stavem) a výškový profil - na Androidu (Compose) i iOS (SwiftUI), nad novým route katalogem + stavem běhu.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Současný Deník je postavený podle zastaralého `Screens.jsx::DenikOnRouteScreen` (prostý seznam „Příští/Hotovo"). Kanonický design je `VariantPrehled` (TrasaVariants.jsx): itinerář-timeline s km/úseky/mezičasy, header s časem startu/cíle a výškový profil.

### 1.2 Solution Overview
Nový sdílený `DenikViewModel` složí **RouteItinerary** (katalog, RD-00) + **stav běhu** (`RunRepository.observeRun`/`observeCollected` + `AppViewModel` aktivní běh/trasa) do `DenikUiState` (header + stops se stavem/km/úsekem + elevace). Android Compose a iOS SwiftUI ho vykreslí nativně dle `VariantPrehled`. Přepínač TA33/TA50 mění zobrazený itinerář (stav se počítá jen pro aktivní trasu). Nahradí dosavadní Deník (ui-01/02) i v shellu.

### 1.3 Scope: What This IS
- Sdílený `DenikViewModel` + `DenikUiState` (commonMain) - kombinuje katalog + běh.
- Android Compose Deník dle `VariantPrehled` (header, timeline, elevační graf) - nahradí `DenikScreen`/`DenikContent`.
- iOS SwiftUI Deník dle `VariantPrehled` - nahradí `DenikView`/`DenikViews`.
- Přepínač tras TA33/TA50; napojení do shellu (tab Deník).
- Elevační graf (Compose Canvas / SwiftUI Path) dle `ElevProfile`.

### 1.4 Scope: What This IS NOT
- Mapa, Profil - samostatné plány (RD-02/03).
- Reálné mezičasy per kontrola nad rámec startu/sběru - „mezičas -:-" placeholder tam, kde chybí (design to tak má).
- Reálná elevační data - z mock katalogu (RD-00).

---

## 2. SUCCESS CRITERIA
| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | Android `:androidApp:assembleDebug` + iOS `xcodebuild -scheme iosApp` zelené | příkazy |
| 2 | Header: „TRASA / TA33", tlačítko Přepnout, staty Délka `33,2 km` / Čas startu (z běhu) / Finální čas (`-`/finish) | preview + běh |
| 3 | Timeline „Kontroly na trase": START, KONTROLA 1..N, CÍL s km vpravo, názvem, úsekem „úsek X,X km \| mezičas -:-" | preview |
| 4 | Stav uzlů: done=zelený (číslo/fajfka) + „splněno", next=oranžový glow + „následující", upcoming=bílý/šedý obrys, start=slate + čas, finish=slate hvězda; spojnice zelená po splněné části | preview (fake stavy) |
| 5 | Výškový profil: „Výškový profil" + ↑ascent/↓descent, area+line graf, min/peak popisky (462/727), km osa | preview |
| 6 | Přepínač TA33/TA50 mění itinerář i staty | běh |
| 7 | Žádný hardcoded hex/dp(Android) / hex/CGFloat(iOS) - vše přes tokeny | code review |
| 8 | iOS-nativní provedení (ScrollView/Canvas/SF Symbols), ne klon Compose | code review |

---

## 3. TECHNICAL DESIGN

### 3.1 Sdílený model + VM (commonMain)
```kotlin
enum class StopStatus { DONE, NEXT, UPCOMING }   // odvozeno z běhu
data class DenikStop(
    val kind: WaypointKind, val label: String,     // "Start"/"Kontrola 3"/"Cíl"
    val name: String, val km: Double,
    val controlOrdinal: Int?, val status: StopStatus,
    val segmentKm: Double?,        // úsek k dalšímu stopu
    val timeText: String?,         // "07:12" (start) / "splněno" / null
    val splitText: String = "-:-",// mezičas placeholder (dokud nemáme per-KP)
    val isFinish: Boolean = false,
)
data class DenikUiState(
    val shortId: String = "", val distanceKm: Double = 0.0,
    val startTimeText: String = "-", val finishTimeText: String = "-",
    val ascentMeters: Int = 0, val descentMeters: Int = 0,
    val elevation: ElevationProfile? = null,
    val stops: List<DenikStop> = emptyList(),
    val canSwitch: Boolean = true, val loading: Boolean = true,
)
class DenikViewModel(observeApp, runRepo, catalog, timeProvider) : ViewModel() {
    fun bindSelected(routeId: String)   // toggle mění vybranou trasu
    val state: StateFlow<DenikUiState>
}
```
Odvození: stops = katalog waypointy `kind in {START,CONTROL,FINISH}`; status z `observeCollected` (collected control → DONE, první nesebraná CONTROL → NEXT, zbytek UPCOMING; START = DONE s časem `startedAtMillis`; FINISH = UPCOMING dokud `finishedAtMillis==null`, pak DONE). `segmentKm` = rozdíl km k dalšímu stopu. Staty: distanceKm/ascent/descent z itineráře; startTimeText z `startedAtMillis` (HH:mm), finishTimeText z `finishedAtMillis`. Stav se počítá jen když `selectedRouteId == activeRun.routeId`; jinak vše UPCOMING (náhled).

Registrace v Koinu + Swift accessor `denikViewModel()`.

### 3.2 UI (obě platformy, dle VariantPrehled)
- **Header** (`RouteSummary`): slate-800 karta, „TRASA" overline, `shortId` display, „Přepnout" pill tlačítko (↔ ikona) → `bindSelected(other)`, řádek 3 statů (display číslo + overline label).
- **Timeline**: levý rail (uzel 38dp + spojnice), pravý obsah (label overline barevný dle stavu + km vpravo display; název; úsek chip). Uzel: DONE zelený, NEXT oranžový + glow, UPCOMING bílý s obrysem slate-200 / slate-400 číslo, START/FINISH slate-800 s ikonou (map/star). Spojnice zelená po DONE, jinak slate-200.
- **Výškový profil** (`ElevProfile`): karta „Výškový profil" + ↑ascent/↓descent staty; graf: area (fill barvou trasy 12%) + line (2.4), min/peak callouts (kroužek + vertikální vodítko + „462 / 727" + „m n. m."), km osa s gridlines. Android: `Canvas`; iOS: `Path`/`Canvas`.

### 3.3 Native-specific
- Android: `Column`+`Scroll`, `Canvas` pro graf, existující `PaperCard`/`Overline`/`Ta33Theme`. Přepínač = tlačítko.
- iOS: `ScrollView`+`VStack`, SwiftUI `Canvas`/`Path` pro graf, SF Symbols (`arrow.up.right`/`arrow.down.right`, `map`, `star.fill`), `Ta33Color/Font/Spacing`.

---

## 4. IMPLEMENTATION STEPS

> Nejdřív sdílený VM (Step 1), pak Android (2-3), pak iOS (4-5), pak ověření (6).

### Step 1: Sdílený DenikViewModel + UiState
**Files**: `shared/.../presentation/DenikViewModel.kt` (+ modely v `domain/model/DenikUiState.kt`) (create/replace), `di/AppModule.kt` (register), `di/Koin.kt` (`denikViewModel()`).
Nahradí starý `RunLogViewModel` konzumovaný Deníkem (RunLogViewModel může zůstat, ale Deník teď používá DenikViewModel). Odvození dle §3.1. Přidej unit test (`DenikViewModelTest`): done/next/upcoming, start čas, segmenty, přepínač.
**Done when**: `:shared:allTests` zelené; VM Koin-resolvable.

### Step 2: Android komponenty (timeline uzel, elevační graf)
**Files**: `androidApp/.../ui/denik/TimelineNode.kt`, `ElevationChart.kt` (create).
`ElevationChart(profile)` - `Canvas` area+line+callouts+osa. `TimelineNode(status, kind, ordinal)` - swatch dle stavu.
**Done when**: `@Preview`.

### Step 3: Android Deník obrazovka
**Files**: `androidApp/.../ui/denik/DenikContent.kt` (replace), `DenikScreen.kt` (replace - konzumuje DenikViewModel). Header (RouteSummary composable), timeline, elevace. Přepínač → `vm.bindSelected`.
**Done when**: `@Preview` (TA33 s během) + `:androidApp:assembleDebug` zelený; tab Deník ukazuje nový design.

### Step 4: iOS komponenty
**Files**: `iosApp/iosApp/UI/Denik/TimelineNodeView.swift`, `ElevationChartView.swift` (create). Nativní `Canvas`/`Path`.
**Done when**: `#Preview`.

### Step 5: iOS Deník obrazovka
**Files**: `iosApp/iosApp/UI/Denik/DenikView.swift` + `DenikViews.swift` (replace), `DenikModel.swift` (replace - konzumuje `denikViewModel()` přes SKIE). Header + timeline + elevace + přepínač.
**Done when**: framework link + `xcodebuild -scheme iosApp` zelené.

### Step 6: Ověření
`:androidApp:assembleDebug`, `xcodebuild`, `:shared:allTests`. Runtime/vizuál na zařízení/simulátoru = uživatel (dev seed z RD-00 dodá data).

---

## 5. EDGE CASES & ERRORS
| Scenario | Expected | Handle |
|---|---|---|
| Vybraná trasa != aktivní běh | Náhled, vše UPCOMING, staty z katalogu, start `-` | status jen když routeId==activeRun.routeId |
| Žádný běh | Vše UPCOMING, „-" časy | podmínka |
| Elevation null (katalog chybí) | Skrýt profil kartu | `?.let` |
| Poslední stop (cíl) | žádný úsek | segmentKm null u posledního |
| Přepínač bez druhé trasy | disabled | canSwitch dle počtu tras |

## 6. SECURITY CONSIDERATIONS
- Jen zobrazení lokálního/mock stavu; žádná citlivá data.

## 7. ASSUMPTIONS
1. **RD-00 hotové** - `RouteItinerary`/`RouteCatalog`/`RouteCatalogRepository` + rich DevSeed existují.
2. **Mezičas per KP** není v modelu → placeholder „-:-" (design to tak zobrazuje); reálné mezičasy = follow-up (FR-09 splits mapované na kontroly).
3. **Přepínač** mění jen zobrazenou trasu; nepřepíná aktivní běh.
4. **Deník používá nový DenikViewModel**; `RunLogViewModel` může zůstat pro jiné použití.

## 8. QUICK REFERENCE
### Files to Create/Replace
- shared: `presentation/DenikViewModel.kt`, `domain/model/DenikUiState.kt`; DI
- android: `ui/denik/{DenikContent,DenikScreen,TimelineNode,ElevationChart}.kt`
- ios: `UI/Denik/{DenikView,DenikViews,DenikModel,TimelineNodeView,ElevationChartView}.swift`
### Commands
```bash
./gradlew :shared:allTests
./gradlew :androidApp:assembleDebug
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## 9. DESIGN REFERENCE
- Kanonický zdroj (přečti přes MCP claude-design, project `5105d303-ffec-4143-864d-a61d529f3c9d`): `ui_kits/ta33-app/TrasaVariants.jsx` → `VariantPrehled`, `RouteSummary`, `ElevProfile`; render `screenshots/obr1-*.png`; uživatelovy screenshoty Deníku.
- `.claude/design/design-system.md` (tokeny). Barva trasy: TA33 orange, TA50 #D63A2F.
### Style Mapping (klíč)
| Prvek | Token |
|---|---|
| header karta | slate-800 (`identityBg` / `Ta33Color.slate800`) |
| stat číslo | display2/3 | 
| uzel done / next / upcoming | success / orange(+glow) / paper+slate-200 obrys |
| start/finish uzel | slate-800 + ikona |
| spojnice done | success, jinak slate-200 |
| elevace area/line | barva trasy (orange/red) |
| úsek chip | slate-50 bg |

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before (ui-01/02) | After |
|---|---|---|
| Deník layout | seznam „Příští/Hotovo" | VariantPrehled timeline + header + elevace |
| Zdroj | RunLogViewModel (LogUiState) | DenikViewModel (katalog + běh) |
| Header/staty/přepínač/elevace | chybí | přidáno |

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-10 | Initial plan created |

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES
### 12.1 Alternatives
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Nový sdílený DenikViewModel (katalog+běh), nativní UI** | Sdílená logika, věrný design, obě platformy konzistentní | Nový VM + přepis UI | ✅ |
| B. Rozšířit RunLogViewModel | Méně nových tříd | LogUiState nemá km/úsek/elevaci/header → stejně přepis | - |
| C. UI si samo skládá z katalogu+repo (bez VM) | Méně sdíleného | Duplikace logiky Android×iOS, netestovatelné | - |
### 12.2 Open Questions
- [ ] **Mezičasy per kontrola** - Proposed: placeholder „-:-" teď; namapovat FR-09 splits na kontroly později.
- [ ] **Náhled druhé trasy (TA50) bez běhu** - Proposed: vše UPCOMING; potvrdit UX.
### 12.3 Suggestions & Follow-ups
- Mapa (RD-02), Profil (RD-03).
- Reálné mezičasy z FR-09.
- Reálná elevace/GPS od zadavatele.
