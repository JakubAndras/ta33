# RD-02 - Mapa (VariantHybrid) - Android + iOS

> **Summary**: Postavit obrazovku Mapa dle kanonického `VariantHybrid`: nahoře schematická mapa (smyčka trasy + piny kontrol + route chip), pod ní kompaktní itinerář (všechny body se značkou KČT + směrem, km, kontroly zvýrazněné, klik na řádek zvýrazní pin) - na Androidu (Compose) i iOS (SwiftUI). Nahrazuje stub tab Mapa. Reálná MapLibre je follow-up; teď schematická mapa dle designu.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Tab Mapa je zatím `StubScreen`/`StubView`. Kanonický design `VariantHybrid` (TrasaVariants.jsx): velká mapa s trasou a piny nahoře + kompaktní srolovatelný itinerář dole, propojené (klik na řádek/pin).

### 1.2 Solution Overview
`MapaViewModel` (sdílený) vystaví z katalogu (RD-00) itinerář vybrané trasy + zvýraznění (`activeControlOrdinal`). UI: nahoře **SchematicMap** (parchment podklad + vrstevnice + smyčka trasy + piny kontrol; klik na pin → highlight), dole **kompaktní itinerář** (`route.waypoints`: km + KP badge + název + směrová šipka / KČT značka; kontroly zvýrazněné; klik na řádek → highlight pin). Přepínač TA33/TA50. Nahradí Mapa stub v shellu.

### 1.3 Scope: What This IS
- Sdílený `MapaViewModel` + `MapaUiState` (itinerář vybrané trasy + `highlightedControl`).
- Android Compose Mapa dle `VariantHybrid` (SchematicMap Canvas + kompaktní itinerář).
- iOS SwiftUI Mapa dle `VariantHybrid` (Canvas/Path mapa + itinerář).
- Přepínač tras; klik na pin↔řádek; MarkBadge + DirArrow komponenty; napojení do shellu (tab Mapa).

### 1.4 Scope: What This IS NOT
- **Reálná MapLibre** mapa/dlaždice/GPS poloha - follow-up (device); teď schematická mapa dle designu.
- Deník, Profil - RD-01/03.
- Detail-sheet varianta (VariantMapa) - používáme Hybrid (Obrazovka 2).

---

## 2. SUCCESS CRITERIA
| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | `:androidApp:assembleDebug` + `xcodebuild -scheme iosApp` zelené | příkazy |
| 2 | Nahoře schematická mapa: podklad + vrstevnice + smyčka trasy + start/cíl marker + piny kontrol (kosočtverec s číslem) | preview |
| 3 | Route chip „Trasa TA33 · 33 km · ↑740 m" + přepínač | preview |
| 4 | Dole kompaktní itinerář: km + (KP badge u kontrol) + název + směr/značka; kontroly zvýrazněné (oranžový obrys) | preview |
| 5 | Klik na řádek kontroly zvýrazní pin (a naopak) - `highlightedControl` | běh |
| 6 | Přepínač TA33/TA50 mění mapu i itinerář | běh |
| 7 | Žádný hardcoded hex/dp / hex/CGFloat - přes tokeny | code review |
| 8 | iOS-nativní (Canvas/Path/ScrollView/SF Symbols), ne klon Compose | code review |

---

## 3. TECHNICAL DESIGN

### 3.1 Sdílený VM
```kotlin
data class MapaUiState(
    val shortId: String = "", val letter: String = "",
    val distanceKm: Double = 0.0, val ascentMeters: Int = 0,
    val controlsCount: Int = 0,
    val waypoints: List<RouteWaypoint> = emptyList(),
    val highlightedControl: Int? = null,   // controlOrdinal
    val canSwitch: Boolean = true, val loading: Boolean = true,
)
class MapaViewModel(catalog, observeApp) : ViewModel() {
    fun bindSelected(routeId: String); fun highlight(controlOrdinal: Int?)
    val state: StateFlow<MapaUiState>
}
```
Výchozí vybraná trasa = aktivní trasa (AppUiState.activeRouteId) nebo TA33. Koin + Swift accessor `mapaViewModel()`. (Pozn.: existuje `MapViewModel` z FR-06 pro reálnou mapu - nový `MapaViewModel` je pro tento schematický design; FR-06 MapViewModel zůstává pro budoucí MapLibre napojení.)

### 3.2 Komponenty
- **SchematicMap** - podklad `linear-gradient` parchment→sage, jemné vrstevnice (SVG/Canvas paths), smyčka trasy (bílý podklad 9 + barva trasy 5, TA50 dashed), start/cíl marker (slate kroužek dole), piny kontrol (kosočtverec `border-radius:50% 50% 50% 2px` rotated 45°, oranžový / aktivní slate-800, číslo). Piny klikací. Android `Canvas`+`Box` overlay tlačítka; iOS `Canvas`/`Path` + overlay `Button`.
- **MarkBadge** - KČT značka: bílý čtverec s barevným pruhem (mod/zel/zlu/cer), `vl` = oranžový kosočtverec, `cyklo` = žlutá plaketka s číslem.
- **DirArrow** - směrová šipka dle `TurnDirection` (up/left/right/left-up/left-right/straight) - SVG/Canvas.
- **Itinerář řádek** - km (display, oranžová u end/kp) + KP badge (u kontrol) + název + DirArrow/star + MarkBadge; aktivní řádek slate-800 bg.

### 3.3 Native-specific
- Android: `Column` (mapa fixní výška ~452dp + `Box` overlay chip; itinerář `LazyColumn`/scroll). `Canvas` pro mapu/značky/šipky.
- iOS: `VStack` (mapa `ZStack` height 452 + overlay; itinerář `ScrollView`). `Canvas`/`Path` pro mapu/značky/šipky. SF Symbols kde vhodné (recenter apod.).

---

## 4. IMPLEMENTATION STEPS

### Step 1: Sdílený MapaViewModel + UiState
**Files**: `shared/.../presentation/MapaViewModel.kt`, `domain/model/MapaUiState.kt`; DI (`mapaViewModel()`). Test na výběr trasy + highlight.
**Done when**: `:shared:allTests` zelené; Koin resolve.

### Step 2: Android - MarkBadge, DirArrow, SchematicMap
**Files**: `androidApp/.../ui/map/{MarkBadge,DirArrow,SchematicMap}.kt` (create). `@Preview` značek/šipek/mapy.
**Done when**: kompiluje + preview.

### Step 3: Android - Mapa obrazovka + shell
**Files**: `androidApp/.../ui/map/MapaScreen.kt` (create), `ui/shell/MainShell.kt` (modify - tab MAPA → `MapaScreen`). Mapa + itinerář + přepínač + highlight.
**Done when**: `@Preview` + `:androidApp:assembleDebug` zelený.

### Step 4: iOS - komponenty + obrazovka + shell
**Files**: `iosApp/iosApp/UI/Map/{MarkBadgeView,DirArrowView,SchematicMapView,MapaView,MapaModel}.swift` (create), `UI/Shell/RootView.swift` (modify - tab `.mapa` → `MapaView`).
**Done when**: framework link + `xcodebuild` zelené.

### Step 5: Ověření
`:androidApp:assembleDebug`, `xcodebuild`, `:shared:allTests`.

---

## 5. EDGE CASES & ERRORS
| Scenario | Expected | Handle |
|---|---|---|
| 6 kontrol (TA50) | PINS_6 rozložení | dle controlsCount |
| žádné zvýraznění | hint „Klepni na kontrolu…" (VariantMapa) / nic | highlightedControl null |
| cyklo bez čísla | plaketka bez čísla | ošetřit |
| dlouhý název | ellipsis | overflow |

## 6. SECURITY CONSIDERATIONS
- Schematická mapa, mock data. Reálná poloha/GPS až s MapLibre (device) - mimo scope.

## 7. ASSUMPTIONS
1. **RD-00 hotové** (katalog s waypointy/značkami/směry).
2. **Schematická mapa teď**, reálná MapLibre později (FR-06 MapViewModel + native SDK) - jasně flagováno.
3. **Nový MapaViewModel** je oddělený od FR-06 `MapViewModel` (ten pro budoucí reálnou mapu).

## 8. QUICK REFERENCE
### Files to Create/Modify
- shared: `presentation/MapaViewModel.kt`, `domain/model/MapaUiState.kt`, DI
- android: `ui/map/{MarkBadge,DirArrow,SchematicMap,MapaScreen}.kt`, `ui/shell/MainShell.kt`
- ios: `UI/Map/{MarkBadgeView,DirArrowView,SchematicMapView,MapaView,MapaModel}.swift`, `UI/Shell/RootView.swift`
### Commands
```bash
./gradlew :androidApp:assembleDebug
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## 9. DESIGN REFERENCE
- MCP claude-design `5105d303-…`: `ui_kits/ta33-app/TrasaVariants.jsx` → `VariantHybrid`, `SchematicMap` (LOOP_PATH, PINS_5/6); `TrasaData.jsx` → `MarkBadge`, `DirArrow`; renders `screenshots/{obr2-mapa,kit-mapa}.png`.
- `.claude/design/design-system.md`.

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before | After |
|---|---|---|
| Tab Mapa | StubScreen/StubView | VariantHybrid (schematická mapa + itinerář) |

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-10 | Initial plan created |

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES
### 12.1 Alternatives
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Schematická mapa (dle designu) teď, MapLibre později** | Věrné designu, testovatelné, bez těžké SDK | Není reálná mapa | ✅ |
| B. Reálná MapLibre hned | Reálná mapa/GPS | Velký nativní lift, v sandboxu neověřitelné, není v designu (design má schematiku) | - |
| C. VariantMapa (map-first + sheet) | Víc mapy | Obrazovka 2 = Hybrid, ne Mapa-first | - |
### 12.2 Open Questions
- [ ] **Kdy MapLibre** - Proposed: až po Etapě 1 vizuálu; napojit na FR-06 MapViewModel + native SDK.
- [ ] **Klik pin→detail** (Otevřít v deníku) - Proposed: teď jen highlight; navigace na detail později.
### 12.3 Suggestions & Follow-ups
- Reálná MapLibre mapa + offline dlaždice + živá GPS poloha (device).
- Propojení pin→Deník detail.
