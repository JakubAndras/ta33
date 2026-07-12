# UI-03 - App Shell (Android Compose)

> **Summary**: Nativní Android tabbed shell (Deník / Mapa / Přehled) s Material-idiomatic plovoucím bottom navem + scan FAB, splash gate a hostováním hotového `DenikScreen`; Mapa/Přehled zatím stub.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
`App()` teď zobrazuje přímo `DenikScreen` - chybí navigační skořápka. Aplikace potřebuje tři taby (Deník / Mapa / Přehled) s nativním bottom navem, scan tlačítko a splash při startu, aby byla reálně použitelná.

### 1.2 Solution Overview
Zavedeme `Ta33App` (Compose): odebírá `AppViewModel.state` a podle `readiness` ukáže splash (LOADING) nebo tabbed shell (`Scaffold` + plovoucí pill bottom nav dle design systému + volitelný scan FAB). Obsah tabu se přepíná stavově (`TopLevelDestination`): Deník = `DenikScreen` (hotový), Mapa/Přehled = stub placeholdery. Scan FAB (jen když běží běh) vyvolá hoisted callback (scan UI je FR-09, zatím stub). `Preparation`/`RunActive` stavy zatím jednoduché placeholdery (obrazovky se staví později).

### 1.3 Scope: What This IS
- Android tabbed shell `Ta33App` se 3 taby (`TopLevelDestination`: DENIK/MAPA/PREHLED).
- Nativní bottom nav (plovoucí pill dle design systému, Material-idiomatic) + scan FAB.
- Splash na `readiness==LOADING`.
- Hostování `DenikScreen` v tabu Deník; stub obrazovky Mapa/Přehled.
- Napojení `App()` → `Ta33App()`.

### 1.4 Scope: What This IS NOT
- **iOS** shell - samostatný plán (ui-04), nativní `TabView`/liquid-glass.
- Reálné obrazovky **Mapa** a **Přehled/Profil** - jen stub; samostatné plány.
- **Scan / Splnění** obrazovky (FR-09/08 UI) - FAB jen vyvolá stub callback.
- **Preparation** obrazovka (FR-11 UI) a **RouteDetail/RunActive** obrazovky - jen jednoduché placeholder větve.
- Compose Navigation knihovna / detail routes s back-stackem - zatím stavové přepínání tabů (viz §12).

---

## 2. SUCCESS CRITERIA

| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | `./gradlew :androidApp:assembleDebug` zelený | příkaz |
| 2 | `readiness==LOADING` → splash (logo/spinner), žádný tab bar | preview / běh |
| 3 | `readiness==READY` → tabbed shell, výchozí tab Deník = `DenikScreen` | běh |
| 4 | Bottom nav přepíná Deník/Mapa/Přehled; aktivní tab je zvýrazněný (slate-800) | běh / preview |
| 5 | Mapa a Přehled ukazují stub placeholder (bez pádu) | běh |
| 6 | Scan FAB je vidět jen když je aktivní běh (`activeRunId != null`); klik vyvolá callback | preview obou variant |
| 7 | Žádný hardcoded hex/dp v UI - vše přes theme | code review |
| 8 | Cream pozadí, safe-area insety (edge-to-edge) v pořádku | běh / preview |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture
```
App() → Ta33Theme { Ta33App() }
   │  koinViewModel() AppViewModel.state : AppUiState
   ▼
 when(readiness):
   LOADING            → SplashView()
   NOT_READY/PREPARING→ PreparationPlaceholder()      (stub; FR-11 UI později)
   READY              → MainShell(app)
                          │ var selectedTab by rememberSaveable  (TopLevelDestination)
                          ├ Scaffold(bottomBar = Ta33BottomNav(selectedTab, onSelect),
                          │          floatingActionButton = if(app.activeRunId!=null) ScanFab(onScan))
                          └ content: when(selectedTab)
                               DENIK  → DenikScreen(onDownload)
                               MAPA   → StubScreen("Mapa")
                               PREHLED→ StubScreen("Přehled")
```

### 3.2 Key Decisions
| Decision | Choice | Reasoning |
|---|---|---|
| Navigace tabů | Stavové přepínání (`rememberSaveable` `TopLevelDestination`) | Taby nepotřebují back-stack (nav contract: native vlastní historii); bez nové závislosti |
| Bottom nav vzhled | Plovoucí pill (design system) v Material-idiomatic provedení | Design fidelity; drží Material feel (viz alternativa NavigationBar v §12) |
| Scan vstup | FAB jen při aktivním běhu, hoisted `onScan` | Scan UI je FR-09; shell jen poskytuje vstupní bod |
| Gate | `AppViewModel.readiness` | Jediný zdroj pravdy (FR-01) |
| Preparation/RunActive | Placeholder větve | Skutečné obrazovky jsou samostatné plány |

---

## 4. IMPLEMENTATION STEPS

> Adresář: `androidApp/src/main/kotlin/com/example/ta33/ui/`.

### Step 1: Splash + stub + preparation placeholder
**Files**: `ui/shell/Placeholders.kt` (create)
- `SplashView()` - vycentrované „TA33" (display1) + `CircularProgressIndicator` na cream.
- `StubScreen(title: String)` - cream pozadí, centrovaný `Overline(title)` + text „Připravujeme".
- `PreparationPlaceholder()` - stub s textem „Příprava dat akce" (FR-11 UI později).

**Done when**: kompiluje, každý má `@Preview`.

### Step 2: Bottom nav (plovoucí pill) + scan FAB
**Files**: `ui/shell/Ta33BottomNav.kt` (create)
- `Ta33BottomNav(selected: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit)` - plovoucí bílý pill (`shadow-pop`, radius pill, 16dp boční okraj), tři položky: Deník (`book-text`), Mapa (`map`), Přehled (`user`); aktivní = slate-800 bg + bílá, ostatní `fgMuted`. Ikony viz Deník `ui/components/Icons.kt` (doplnit `map`, `book-text`/`user` pokud chybí).
- `ScanFab(onClick)` - kruhový 60dp orange FAB s glow, ikona `scan`, `fgOnOrange`.

**Done when**: `@Preview` navu (aktivní každý tab) + FAB.

### Step 3: `MainShell`
**Files**: `ui/shell/MainShell.kt` (create)
```kotlin
@Composable
fun MainShell(app: AppUiState, onScan: () -> Unit, onDownload: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(TopLevelDestination.DENIK) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = { if (app.activeRunId != null) ScanFab(onScan) },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                TopLevelDestination.DENIK -> DenikScreen(onDownload = onDownload)
                TopLevelDestination.MAPA -> StubScreen("Mapa")
                TopLevelDestination.PREHLED -> StubScreen("Přehled")
            }
            Ta33BottomNav(tab, onSelect = { tab = it })  // plovoucí, zarovnané dole
        }
    }
}
```
Bottom nav je plovoucí (`Modifier.align(BottomCenter)`), ne `Scaffold.bottomBar`, aby seděl design (pill nad obsahem). Obsah tabů dostane spodní padding, aby ho pill nepřekrýval.

**Done when**: přepínání tabů funguje, FAB se zjeví jen při běhu.

### Step 4: `Ta33App` gate + napojení `App()`
**Files**: `ui/shell/Ta33App.kt` (create), `App.kt` (modify)
```kotlin
@Composable
fun Ta33App(appViewModel: AppViewModel = koinViewModel()) {
    val app by appViewModel.state.collectAsStateWithLifecycle()
    when (app.readiness) {
        AppReadiness.LOADING -> SplashView()
        AppReadiness.NOT_READY, AppReadiness.PREPARING -> PreparationPlaceholder()
        AppReadiness.READY -> MainShell(app, onScan = { /* TODO FR-09 scan */ }, onDownload = { /* TODO */ })
    }
}
```
`App() { Ta33Theme { Ta33App() } }`.

> Pozn.: `DenikScreen` má vlastní gate (NOT_READY→DenikBefore). V shellu READY větev hostí Deník; NOT_READY/PREPARING řeší shell přes `PreparationPlaceholder`. Zvážit, ať se logika nepřekrývá - v shellu je Deník pod READY, takže DenikBefore se ukáže jen když je app READY ale obsah přesto chybí (edge). Ponechat, není konflikt.

**Done when**: app se spustí, ukáže splash → shell.

### Step 5: Ověření
`./gradlew :androidApp:assembleDebug` + `./gradlew build`; projít previews. Runtime na zařízení (emulátor v sandboxu nenaboot) je na uživateli.

---

## 5. EDGE CASES & ERRORS
| Scenario | Expected | Handle |
|---|---|---|
| Rotace / proces death | Vybraný tab přežije | `rememberSaveable` |
| `activeRunId==null` | Žádný scan FAB | podmínka na FAB |
| READY ale obsah chybí | Deník ukáže vlastní DenikBefore | ponechat Deníku |
| Pill překrývá obsah | Spodní padding obsahu ≥ výška pill + 16 | padding v obsahu tabů |
| Edge-to-edge | Obsah pod status barem OK | `safeDrawing` insety na kořeni |

## 6. SECURITY CONSIDERATIONS
- N/A (jen navigace/zobrazení). Žádná citlivá data v shellu.

## 7. ASSUMPTIONS
1. **Taby nepotřebují Compose Navigation** - stavové přepínání stačí; detail routes (RouteDetail/scan) přijdou s Compose Navigation později.
2. **Scan a Preparation jsou stub** - skutečné obrazovky jsou jiné plány; shell drží jen vstupní body.
3. **`TopLevelDestination` (DENIK/MAPA/PREHLED)** je zdroj tabů (FR-01). „Přehled" = Profil dle design systému.
4. **Ikony `map`/`book-text`/`user`/`scan`** - doplnit do `ui/components/Icons.kt`, pokud chybí (Step 2).

## 8. QUICK REFERENCE
### Files to Create
- `ui/shell/{Placeholders,Ta33BottomNav,MainShell,Ta33App}.kt`
### Files to Modify
- `App.kt` → `Ta33App()`
- `ui/components/Icons.kt` - doplnit chybějící ikony
### Commands
```bash
./gradlew :androidApp:assembleDebug
```

## 9. DESIGN REFERENCE
- `.claude/design/design-system.md` (bottom nav / scan FAB), `ui_kits/ta33-app/Components.jsx` (`BottomNav`).
- **KLÍČOVÝ PRINCIP:** Android = Material-idiomatic plovoucí pill; iOS (ui-04) = nativní TabView/liquid-glass. Nesdílet chrome.
### Style Mapping
| Design | Code | Value |
|---|---|---|
| pill nav bg | `MaterialTheme.colorScheme.surface` | #FFFFFF |
| aktivní tab bg | `Ta33Theme.colors.identityBg` (slate-800) | #1C2A36 |
| neaktivní tab fg | `Ta33Theme.colors.fgMuted` | #5A6C7A |
| scan FAB | `MaterialTheme.colorScheme.primary` + glow | #F76A0E |
| pozadí | `MaterialTheme.colorScheme.background` | #F7F2EA |
| radius pill | `Ta33Radius.pill` | 999dp |

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before | After |
|---|---|---|
| `App()` | přímo `DenikScreen` | `Ta33App()` (splash + tabbed shell) |
| Navigace | žádná | 3 taby + scan vstup |

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-09 | Initial plan created |

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES
### 12.1 Alternatives
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Stavové taby + plovoucí pill (design)** | Bez závislosti, design fidelity | Detail routes potřebují dořešit později | ✅ |
| B. Material3 `NavigationBar` v `Scaffold.bottomBar` | Nejvíc „stock" Material | Neodpovídá plovoucímu pillu z designu | - |
| C. Compose Navigation (navigation-compose) hned | Připraveno na detail routes/back-stack | Nová závislost, víc složitosti pro 3 taby | - |
### 12.2 Open Questions
- [ ] **Compose Navigation kdy?** - Proposed: přidat, až přijdou detail routes (RouteDetail, scan flow); pro 3 taby netřeba.
- [ ] **Scan FAB viditelnost** - Proposed: jen při aktivním běhu; potvrdit až s FR-09 scan flow.
### 12.3 Suggestions & Follow-ups
- iOS app-shell (ui-04) - nativní TabView / liquid-glass.
- Obrazovky Mapa (MapViewModel + MapLibre) a Přehled/Profil (OverviewViewModel/SettingsViewModel) nahradí stuby.
- Preparation obrazovka (FR-11 UI) + scan flow (FR-09 UI) + Compose Navigation pro detail routes.
