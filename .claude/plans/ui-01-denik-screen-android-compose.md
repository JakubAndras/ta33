# UI-01 — Deník / Itinerář (Android Compose)

> **Summary**: Postavit první nativní Android obrazovku (výchozí tab „Deník") v Jetpack Compose nad hotovými sdílenými ViewModely, ve třech stavech (loading / obsah nestažen / na trase), s novou sadou znovupoužitelných TA33 komponent — vše přes existující design tokeny.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Projekt TA33 má hotovou veškerou sdílenou logiku (Etapa 1, FR-01–FR-11) včetně ViewModelů, ale **žádné reálné UI** — Android app zobrazuje jen placeholder „TA33". Potřebujeme první skutečnou obrazovku: **Deník / Itinerář**, což je výchozí tab a přehled průběhu závodu (kolik kontrol splněno, která je další, mezičasy).

### 1.2 Solution Overview
Vytvoříme Compose obrazovku `DenikScreen`, která odebírá `AppViewModel` (gate stavu) a `RunLogViewModel` (obsah deníku) přes Koin, a vykresluje tři stavy podle `AppUiState.readiness`. Zavedeme sadu znovupoužitelných komponent (`IdentityCard`, `PaperCard`, `PrimaryButton`, `OutlineButton`, `Overline`, `OfflineBanner`, `StatChip`, `KPRow`) mapovaných 1:1 z design systému na už existující theme tokeny. Obrazovku napojíme do `MainActivity` přes `App()`, aby šla reálně spustit.

### 1.3 Scope: What This IS
- Android (Jetpack Compose) obrazovka **Deník** ve 3 stavech: LOADING, obsah nestažen (`DenikBefore`), na trase (`DenikOnRoute`).
- Sada 8 znovupoužitelných Compose komponent v `com.example.ta33.ui.components` (sdílené i pro budoucí Mapa/Profil).
- Napojení na `AppViewModel` + `RunLogViewModel` (+ `RouteDetailViewModel` pro label trasy) přes `koinViewModel()`.
- Drobné rozšíření theme (`Ta33Colors`) o chybějící sémantický token pro tmavou identity-kartu.
- `@Preview` pro každý stav; zobrazení v běžícím appu přes `MainActivity`.
- Statické české texty jako Compose string resources (values / values-cs).

### 1.4 Scope: What This IS NOT
- **App shell** (bottom nav Deník/Mapa/Profil, scan FAB, tab routing, plný splash) — samostatný plán (UI app skeleton / FR-01 UI).
- **iOS SwiftUI** verze Deníku — samostatný navazující plán.
- **Scan / Splnění** obrazovky (FR-08/09 UI), obrazovky **Mapa** a **Profil**.
- **Reálné spuštění stahování / Preparation UI** — CTA jen vyvolá hoisted callback; plná navigace + Preparation obrazovka je app-shell.
- Skutečná vzdálenost k další kontrole („310 m") — v deníku není (LogUiState ji nenese).
- Přidání licencovaných fontů (Big Shoulders / Inter) — viz Assumptions; zůstává systémový fallback.

---

## 2. SUCCESS CRITERIA

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | `./gradlew :androidApp:assembleDebug` proběhne zeleně | spuštění příkazu |
| 2 | `App()` v `MainActivity` zobrazí Deník; při `readiness==LOADING` je vidět spinner | spuštění appu / `@Preview` |
| 3 | Stav „obsah nestažen" (`NOT_READY`/`ABSENT`) ukáže IdentityCard + empty-state kartu + oranžové CTA „STÁHNOUT DATA AKCE · 84 MB" | `@Preview` DenikBefore |
| 4 | Stav „na trase" (`READY` + run) ukáže offline banner, progress kartu s „2 / 5" a progress barem, a dvě skupiny KP řádků („Příští checkpoint" / „Hotovo") | `@Preview` DenikOnRoute s fake LogUiState |
| 5 | KP řádky mají správný stavový swatch: DONE = zelená fajfka, ACTIVE = oranžová s číslem + glow, LOCKED = šedý dim, FINISH = hvězda | vizuální kontrola preview |
| 6 | Prázdný běh (`collectedCount==0`) se vykreslí bez pádu (0 / N, první ACTIVE, zbytek LOCKED) | `@Preview` empty-run |
| 7 | Žádná hardcoded hex/dp hodnota v UI kódu — vše přes `Ta33Theme` / `MaterialTheme` | code review + grep na `Color(0x` / `.dp` literály v `ui/` mimo theme |
| 8 | DONE řádky zobrazují „Splněno · HH:MM" z `collectedAtMillis`; progress „2 / 5" z `collectedCount`/`totalCount` | preview s fake daty |
| 9 | Všechny user-facing statické texty jsou z `Res.string.*` (cs) | grep na string literály v composable |
| 10 | Obrazovka reálně naběhne na zařízení/emulátoru bez runtime pádu (Koin resolve `AppViewModel`, `RunLogViewModel`) | manuální spuštění appu |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
 MainActivity.setContent
        │
        ▼
   Ta33Theme { DenikScreen() }
        │  koinViewModel()
        ├──────────────► AppViewModel.state : StateFlow<AppUiState>   (FR-01 gate)
        │                     │ readiness / activeRunId / activeRouteId
        │                     ▼
        │          when(readiness) ─ LOADING ─► DenikLoading()
        │                          ─ NOT_READY/ABSENT ─► DenikBefore(onDownload)
        │                          ─ READY + run+route ─► bind & render
        │  koinViewModel()
        ├──────────────► RunLogViewModel.bind(runId, routeId) ─► LogUiState  (FR-04)
        │                     entries / collectedCount / totalCount
        └──────────────► RouteDetailViewModel.bind(routeId) ─► name + distanceKm (FR-03, label)
                              │
                              ▼
                    DenikOnRoute(log, routeLabel, offline)
                        ├─ OfflineBanner
                        ├─ ProgressCard (PaperCard + "2 / 5" + bar)
                        ├─ Overline "Příští checkpoint" + KPRow(state != DONE)
                        └─ Overline "Hotovo" + KPRow(state == DONE)
```

Composables jsou rozdělené na **stateful wrapper** (`DenikScreen`, drží VM + binduje) a **stateless obsahové** (`DenikBefore`, `DenikOnRoute`, `DenikLoading`) přijímající plain data → plně previewovatelné bez Koinu.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Stateful vs stateless split | `DenikScreen` (VM) → `DenikXxx` (data-in) | Previews + testovatelnost; VM se nedostane do preview |
| Zdroj obsahu | `RunLogViewModel` (FR-04), needuplikovat | Deník = přesně `LogUiState` |
| Label trasy „Trasa A · 33 km" | `RouteDetailViewModel` (FR-03) bind na `activeRouteId` | `LogUiState` nenese jméno/km trasy; RouteDetail ano |
| Gate stavů | `AppViewModel.readiness` + `activeRunId`/`activeRouteId` | Jediný zdroj pravdy pro readiness (FR-01) |
| Slate-800 identity karta | Přidat sémantický token `identityBg` do `Ta33Colors` | Raw `Ta33Palette` je `internal` a „nepoužívat v UI"; kp*/fg* tokeny nesedí sémanticky |
| Statické texty | Compose string resources (cs) | Stack §12 localization kontrakt; učí správný vzor |
| Download CTA | Hoisted `onDownload: () -> Unit` | Navigace/Preparation je app-shell; drží obrazovku bez závislosti na nav |
| Čas „HH:MM" | Android `java.time` helper v `ui/format` | Wall-clock z epoch millis je platform/UI concern (ne shared) |

---

## 4. IMPLEMENTATION STEPS

> Execute steps in order. Do not skip. Balíček UI: `com.example.ta33.ui` (v modulu `androidApp`).

### Step 1: Rozšířit theme o token pro identity kartu
**Goal**: Sémantický token pro tmavou (slate-800) identity kartu, ať se v UI nesahá na raw paletu.
**Files**: `androidApp/src/main/kotlin/com/example/ta33/ui/theme/Color.kt`

Do `data class Ta33Colors` přidat pole a do `Ta33LightColors` hodnotu:
```kotlin
// v Ta33Colors (sekce Surfaces)
val identityBg: Color,      // tmavá "kdo a kdy" karta (date/place, startovní číslo)
// v Ta33LightColors
identityBg = Ta33Palette.Slate800,
```
Text na ní: existující `fgOnDark` (place/hodnota) a `fgOnDarkMuted` (overline/label).

**Done when**: Projekt se kompiluje; `Ta33Theme.colors.identityBg` je dostupné.

---

### Step 2: Přidat řetězcové resources (cs) pro Deník
**Goal**: Statické texty přes `Res.string.*` dle stack localization kontraktu.
**Files**: `shared/src/commonMain/composeResources/values/strings.xml` (en fallback), `shared/src/commonMain/composeResources/values-cs/strings.xml` (cs)

Přidat klíče (en = provizorní zrcadlo cs, protože appka je CZ-only):
```xml
<string name="denik_event_date">Sobota 19. 9. 2026</string>
<string name="denik_event_place">Teplice n. Metují</string>
<string name="denik_event_sub">Start 7:00–10:00 · prezentace u sokolovny</string>
<string name="denik_not_downloaded_title">Akce ještě není stažená</string>
<string name="denik_not_downloaded_body">Stáhni si trasy, kontroly a mapu, dokud máš signál. Na trase pak vše funguje offline.</string>
<string name="denik_download_cta">Stáhnout data akce · 84 MB</string>
<string name="denik_offline_banner">Offline režim — záznamy se uloží lokálně</string>
<string name="denik_group_next">Příští checkpoint</string>
<string name="denik_group_done">Hotovo</string>
<string name="denik_status_locked">Zamčeno</string>
<string name="denik_status_next">Další</string>
<string name="denik_status_done_prefix">Splněno</string>   <!-- "Splněno · 08:14" složí kód -->
<string name="denik_finish_title">Cíl</string>
```
Po přidání spustit `./gradlew build` (regeneruje `Res`).

**Done when**: `Res.string.denik_*` klíče existují a jsou přístupné z `androidApp`.

---

### Step 3: Time-format helper (HH:MM)
**Goal**: Převod `collectedAtMillis` (epoch) na lokální „HH:MM".
**Files**: `androidApp/src/main/kotlin/com/example/ta33/ui/format/TimeFormat.kt` (create)

```kotlin
package com.example.ta33.ui.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** Epoch millis → lokální "HH:mm" (např. 08:14). */
fun formatClock(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    CLOCK.format(Instant.ofEpochMilli(millis).atZone(zone))
```

**Done when**: `formatClock(...)` kompiluje a vrací „HH:mm".

---

### Step 4: Základní komponenty — karty, tlačítka, label, banner, stat
**Goal**: Znovupoužitelné atomy mapované z `Components.jsx` na tokeny.
**Files**: `androidApp/src/main/kotlin/com/example/ta33/ui/components/Cards.kt`, `Buttons.kt`, `Text.kt`, `Banners.kt`, `Chips.kt` (create)

Mapování (bez hardcodu):
- `IdentityCard(date, place, sub)` — `Surface(color = Ta33Theme.colors.identityBg, shape = Ta33Radius.lg)`, padding `x5`/`x6`; date = `overline` `fgOnDarkMuted`, place = `display3`/`h2` velké UPPER `fgOnDark`, sub = `small` `fgOnDarkMuted`. Shadow přes `Modifier.shadow(…, Ta33Radius.lg)` (jemný, odpovídá `shadow-card`).
- `PaperCard(modifier, content)` — `Surface(color = MaterialTheme.colorScheme.surface /*Paper*/, shape = Ta33Radius.lg)`, elevation/shadow jemný, padding `x4`.
- `PrimaryButton(text, onClick)` — `Button` s `MaterialTheme.colorScheme.primary` (Orange), `Ta33Radius.pill`, min-height 56.dp (`x9`+…), styl `MaterialTheme.typography.labelLarge` (=button, UPPER), plná šířka. Glow: `Modifier.shadow` s orange tónem (aproximace `shadow-cta-glow`).
- `OutlineButton(text, onClick)` — `OutlinedButton`, border 2.dp `primary`, transparentní fill, orange text.
- `Overline(text, color = Ta33Theme.colors.fgMuted)` — `Text` styl `labelSmall` (=overline), UPPER.
- `OfflineBanner()` — `Row` v `Surface(color = Ta33Theme.colors.warningTint, shape = Ta33Radius.md)`, ikona ⚡ (viz Step 5 ikony) `warning`, text `Res.string.denik_offline_banner` bold.
- `StatChip(value, label)` — `Column` ve `Surface(warningTint→surfaceVariant slate100, Ta33Radius.md)`; value `display2/3`, label `overline` `fgMuted`. (Použije se hlavně na Mapě; sem přidat kvůli znovupoužití.)

**Done when**: Každá komponenta má `@Preview`; modul se kompiluje; grep nenajde hex/`.dp` literály mimo theme (kromě strukturálních jako `2.dp` border — povoleno, ale preferuj `Ta33Spacing`).

---

### Step 5: Ikony
**Goal**: Ikony `download`, `check`, `star`, `zap`, (bottom-nav ikony jsou app-shell).
**Files**: `androidApp/src/main/kotlin/com/example/ta33/ui/components/Icons.kt` (create) nebo použít `androidx.compose.material.icons`

Rozhodnutí: použít **Material Icons Extended** není v závislostech; místo toho definovat pár jednoduchých ikon jako `ImageVector` inline, nebo použít základní `Icons.Filled` (Check, Star, Download nejsou všechny v základní sadě). **Default**: přidat závislost `androidx.compose.material:material-icons-core` (Check, Star, ... ) — ověřit dostupnost; pokud chybí, nakreslit minimalistické vektory (rounded, 2px feel) ručně dle design systému (Lucide styl).

**Done when**: Ikony `check`, `star`, `download`, `zap` jsou vykreslitelné v požadované velikosti/barvě (`currentColor`).

> Pozn.: viz Section 12 open question k ikonám (Material vs ruční Lucide-like).

### Step 6: `KPRow` — řádek kontroly v deníku
**Goal**: Jeden řádek dle `ControlPointState`.
**Files**: `androidApp/src/main/kotlin/com/example/ta33/ui/components/KPRow.kt` (create)

Signatura pracuje přímo s domain typy:
```kotlin
@Composable
fun KPRow(
    ordinal: Int,
    title: String,          // "KP-03 · Sloní pramen" nebo "Cíl · Adršpach"
    subtitle: String,       // "Splněno · 08:14" | "Další" | "Zamčeno"
    state: ControlPointState,
    isFinish: Boolean = false,
) { /* PaperCard-like řádek: 56dp swatch + titul(body 700) + sub */ }
```
Swatch podle stavu (tokeny z `Ta33Theme.colors`):
- `DONE` → bg `kpDoneBg`, fajfka `kpDoneFg`
- `ACTIVE` → bg `kpActiveBg`, číslo `kpActiveFg`, glow `Modifier.shadow` orange
- `LOCKED` → bg `kpLockedBg`, číslo/hvězda `fgOnDark`; celý řádek `Modifier.alpha(0.55f)`
- `FINISH` → bg `kpFinishBg` (slate800), hvězda `kpFinishFg`

**Done when**: `@Preview` ukazuje všechny 4 stavy + finish.

---

### Step 7: Stateless obsahové obrazovky
**Goal**: `DenikLoading`, `DenikBefore`, `DenikOnRoute` — plain data, žádný VM.
**Files**: `androidApp/src/main/kotlin/com/example/ta33/ui/denik/DenikContent.kt` (create)

- `DenikLoading()` — vycentrovaný `CircularProgressIndicator` na cream pozadí.
- `DenikBefore(onDownload: () -> Unit)` — `Column` (scroll), `IdentityCard(...)` + `PaperCard` s download ikonou v kruhu (`orange100` bublina), `h1` titul, `body`, `PrimaryButton(cta, onDownload)`. Texty z `Res.string`.
- `DenikOnRoute(log: LogUiState, routeLabel: String, offline: Boolean)`:
  ```kotlin
  Column {
    if (offline) OfflineBanner()
    ProgressCard(routeLabel = routeLabel, collected = log.collectedCount, total = log.totalCount)
    Overline(stringResource(Res.string.denik_group_next))
    log.entries.filter { it.state != ControlPointState.DONE }.forEach { KPRow(rowArgs(it)) }
    Overline(stringResource(Res.string.denik_group_done))
    log.entries.filter { it.state == ControlPointState.DONE }.forEach { KPRow(rowArgs(it)) }
  }
  ```
  `ProgressCard` = `PaperCard { Row(routeLabel left `bodyStrong`, "$collected / $total" right `display2`); Box progress bar (track `surfaceVariant`, fill `primary`, šířka = collected/total, `Ta33Radius.pill`, výška 10.dp) }`. Ošetřit `total == 0` → fill 0.
  `rowArgs(entry)` mapuje `RunLogEntry` → `KPRow` argumenty:
    - title: `"KP-%02d · %s".format(control.ordinal, control.name)`; pro finish `"${stringResource(denik_finish_title)} · ${control.name}"`
    - subtitle dle stavu: DONE → `"${done_prefix} · ${formatClock(collectedAtMillis!!)}"`; ACTIVE → `status_next`; LOCKED → `status_locked`; FINISH → `status_locked`/done
    - isFinish: odvodit z `state == FINISH` nebo `entry == entries.last()` (poslední ordinál = cíl)

**Done when**: `@Preview` pro Loading, Before, OnRoute (s fake `LogUiState`) a OnRoute-empty (0 sebráno).

---

### Step 8: Stateful `DenikScreen` (napojení na VM)
**Goal**: Spojit VM + binding + gate.
**Files**: `androidApp/src/main/kotlin/com/example/ta33/ui/denik/DenikScreen.kt` (create)

```kotlin
@Composable
fun DenikScreen(
    appViewModel: AppViewModel = koinViewModel(),
    onDownload: () -> Unit = {},
) {
    val app by appViewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            app.readiness == AppReadiness.LOADING -> DenikLoading()
            app.readiness == AppReadiness.READY &&
                app.activeRunId != null && app.activeRouteId != null ->
                    DenikOnRouteRoute(app.activeRunId!!, app.activeRouteId!!)
            else -> DenikBefore(onDownload = onDownload)
        }
    }
}

@Composable
private fun DenikOnRouteRoute(runId: String, routeId: String) {
    val logVm: RunLogViewModel = koinViewModel()
    val routeVm: RouteDetailViewModel = koinViewModel()
    LaunchedEffect(runId, routeId) { logVm.bind(runId, routeId); routeVm.bind(routeId) }
    val log by logVm.state.collectAsStateWithLifecycle()
    val route by routeVm.state.collectAsStateWithLifecycle()
    DenikOnRoute(log = log, routeLabel = routeLabelOf(route), offline = true /* TODO connectivity */)
}
```
`routeLabelOf(...)` složí „Trasa <letter> · <km> km" z RouteDetail (ověř názvy polí; pokud detail nemá letter, použij jen jméno + km). `offline` prozatím `true` (skutečný stav z ConnectivityMonitor je enhancement — viz §12).

**Done when**: Kompiluje; `koinViewModel()` rezolvuje všechny tři VM (jsou v `appModule`).

---

### Step 9: Napojit do `MainActivity` / `App()`
**Goal**: Reálné zobrazení.
**Files**: `androidApp/src/main/kotlin/com/example/ta33/App.kt`

```kotlin
@Composable
fun App() {
    Ta33Theme {
        DenikScreen(onDownload = { /* TODO: navigace na Preparation (app-shell) */ })
    }
}
```
`MainActivity.setContent { App() }` už existuje.

**Done when**: App se spustí a zobrazí Deník podle aktuálního `AppUiState`.

---

### Step 10: Ověření
**Goal**: Zelený build + vizuální kontrola.
**Files**: —

Spustit `./gradlew :androidApp:assembleDebug` a `./gradlew build`. Projít previews (Android Studio) pro všechny stavy. Pokud je zařízení/emulátor dostupný, spustit app a ověřit Koin resolve + vykreslení.

**Done when**: Success criteria 1–10 splněné.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| `total == 0` (prázdná trasa) | Progress „0 / 0", bar prázdný, žádné řádky, žádný pád | dělení ošetřit: fraction = if (total==0) 0f else collected/total |
| `collectedCount == 0` (běh nezačal / 0 sebráno) | Skupina „Hotovo" prázdná (nezobrazovat nadpis, pokud prázdná), první KP ACTIVE | filtrovat; nadpis skupiny renderovat jen když nonempty |
| `READY` ale `activeRunId == null` | Nespadnout; ukázat `DenikBefore`-like nebo placeholder „naskenuj start" | spadne do `else` větve → `DenikBefore` (dočasně); rich pre-start view je app-shell |
| `LogUiState.loading == true` (první emise) | Krátký spinner / prázdný stav | volitelně respektovat `log.loading` a ukázat spinner v OnRoute |
| DONE bez `collectedAtMillis` (nemělo by nastat) | Sub bez času („Splněno") | null-safe: čas přidat jen když `collectedAtMillis != null` |
| RouteDetail ještě nenačten | Label prázdný/placeholder, layout nespadne | routeLabel default „Trasa" dokud nedorazí data |
| Font TTF chybí | Systémový fallback (už zavedeno v `Type.kt`) | žádná akce; sledovat Assumptions |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: N/A — obrazovka jen zobrazuje lokální stav, žádný uživatelský vstup kromě tlačítek.
- **Auth/Access control**: N/A v Etapě 1.
- **Sensitive data**: Časy a poloha kontrol jsou osobní data, ale zůstávají **on-device** (žádný upload). Deník je jen zobrazuje.
- **Logging**: Nelogovat souřadnice ani časy sběru do produkčních logů.

---

## 7. ASSUMPTIONS

1. **Uživatel se rozhodl pro scope defaulty (prompt byl detailní)** — clarifikační fáze přeskočena; scope dle §1.3/§1.4.
2. **Fonty Big Shoulders Display + Inter nejsou v repu** → zůstává systémový fallback (`Type.kt` už tak má). Přidání TTF je samostatný drobný krok, neblokuje.
3. **`RouteDetailViewModel` (FR-03) vystavuje jméno trasy a `distanceKm`** pro label. Pokud pole mají jiné názvy nebo detail nenese `letter` (A/B), použije se jen jméno + km; ověřit na prvním buildu.
4. **`koin-compose-viewmodel` `koinViewModel()`** rezolvuje `AppViewModel`, `RunLogViewModel`, `RouteDetailViewModel` (všechny v `appModule` jako `factory`). Ověřeno v `AppModule.kt`.
5. **Download CTA** jen vyvolá hoisted callback (default no-op/log); reálné stažení a navigace na Preparation jsou app-shell (jiný plán).
6. **Offline indikátor** je prozatím `true` (natvrdo) — skutečný stav z `ConnectivityMonitor` je enhancement (§12), ať tento plán nezávisí na app-shellu.
7. **Statické texty jdou do Compose string resources**; appka je CZ-only, `values/` (en) je provizorní zrcadlo `values-cs/`.

> Open questions v Section 12.

---

## 8. QUICK REFERENCE

### Files to Modify
- `androidApp/src/main/kotlin/com/example/ta33/ui/theme/Color.kt` — token `identityBg`
- `androidApp/src/main/kotlin/com/example/ta33/App.kt` — `App()` → `DenikScreen`
- `shared/src/commonMain/composeResources/values/strings.xml` — klíče `denik_*` (en)
- `shared/src/commonMain/composeResources/values-cs/strings.xml` — klíče `denik_*` (cs)

### Files to Create
- `androidApp/.../ui/format/TimeFormat.kt` — `formatClock()`
- `androidApp/.../ui/components/{Cards,Buttons,Text,Banners,Chips,Icons,KPRow}.kt` — komponenty
- `androidApp/.../ui/denik/DenikContent.kt` — stateless obrazovky (Loading/Before/OnRoute)
- `androidApp/.../ui/denik/DenikScreen.kt` — stateful wrapper + VM binding

### Dependencies
- (možná) `androidx.compose.material:material-icons-core` — ikony; jinak ruční vektory. Ověřit před přidáním.
- Vše ostatní už v `androidApp/build.gradle.kts` (compose, koin-compose-viewmodel, lifecycle-runtime-compose).

### Commands
```bash
# Verify
./gradlew :androidApp:assembleDebug
./gradlew build
```

---

## 9. DESIGN REFERENCE

### Visual Spec
- Claude Design projekt „TA33 Design System": https://claude.ai/design/p/5105d303-ffec-4143-864d-a61d529f3c9d
- Referenční kód: `ui_kits/ta33-app/Screens.jsx` (`DenikBeforeScreen`, `DenikOnRouteScreen`, `DenikLockedScreen`), `Components.jsx`.
- Lokální destiláty: `.claude/design/design-system.md`, `.claude/design/denik-screen-spec.md`.

### Component/Screen Mapping
| Design (JSX) | Compose |
|---|---|
| `IdentityCard` | `ui/components/Cards.kt::IdentityCard` |
| `PaperCard` | `ui/components/Cards.kt::PaperCard` |
| `PrimaryButton` / `OutlineButton` | `ui/components/Buttons.kt` |
| `Overline` | `ui/components/Text.kt::Overline` |
| `OfflineBanner` | `ui/components/Banners.kt::OfflineBanner` |
| `StatChip` | `ui/components/Chips.kt::StatChip` |
| `KPRow` | `ui/components/KPRow.kt::KPRow` |
| `DenikBeforeScreen` | `ui/denik/DenikContent.kt::DenikBefore` |
| `DenikOnRouteScreen` | `ui/denik/DenikContent.kt::DenikOnRoute` |

### Style Mapping (design → theme, žádný hardcode)
| Design Spec | Code Equivalent | Value |
|---|---|---|
| pozadí cream | `MaterialTheme.colorScheme.background` | `#F7F2EA` |
| paper karta | `MaterialTheme.colorScheme.surface` | `#FFFFFF` |
| identity karta bg | `Ta33Theme.colors.identityBg` (nový) | `#1C2A36` |
| orange CTA | `MaterialTheme.colorScheme.primary` | `#F76A0E` |
| progress track | `MaterialTheme.colorScheme.surfaceVariant` | `#E6EAEC` |
| KP done | `Ta33Theme.colors.kpDoneBg/Fg` | `#1FA85A` / `#FFF` |
| KP active | `Ta33Theme.colors.kpActiveBg/Fg` | `#F76A0E` / `#FFF` |
| KP locked | `Ta33Theme.colors.kpLockedBg/Fg` (+ alpha 0.55) | `#D2D9DE` / `#5A6C7A` |
| KP finish | `Ta33Theme.colors.kpFinishBg/Fg` | `#1C2A36` / `#FFF` |
| offline banner | `Ta33Theme.colors.warningTint` + `warning` | `#FBE9C2` / `#E8A92A` |
| display „2 / 5" | `MaterialTheme.typography.displayMedium` (=display2) | 32/34/900 |
| h1 titul | `MaterialTheme.typography.headlineSmall` (=h1) | 24/30/700 |
| overline | `MaterialTheme.typography.labelSmall` (=overline) | 13/16/700, +0.10em UPPER |
| button | `MaterialTheme.typography.labelLarge` (=button) | 16/20/800, +0.04em UPPER |
| radii karta / pill | `Ta33Radius.lg` / `Ta33Radius.pill` | 20dp / 999dp |
| spacing mezi kartami | `Ta33Theme.spacing.x5` | 20dp |

---

## 10. CORRECTIONS FROM CURRENT STATE

| What | Before | After |
|------|--------|-------|
| `App()` obsah | Placeholder „TA33" text | `DenikScreen()` (3 stavy z `AppUiState`) |
| `Ta33Colors` | bez tokenu pro identity bg | + `identityBg = Slate800` |
| `composeResources` strings | prázdné (po odebrání greeting) | klíče `denik_*` (cs + en) |
| Android UI vrstva | žádné komponenty | `ui/components/*` + `ui/denik/*` |

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
| **A. Stateful wrapper + stateless obsah, komponenty v `ui/components`** | Previews bez Koinu, znovupoužití pro Mapa/Profil, čisté testy | Trochu víc souborů | ✅ |
| B. Jeden monolitický `DenikScreen` s VM uvnitř všeho | Méně souborů | Nepreviewovatelné (VM v preview), špatné znovupoužití | — |
| C. Compose Multiplatform UI ve `shared` (sdílet i pro iOS) | Jedno UI pro obě platformy | Odporuje stacku (Alza-style = nativní SwiftUI na iOS), velký zásah | — |

**Why the selected approach won**: Odpovídá stacku (nativní Compose na Androidu), maximalizuje znovupoužití komponent pro další obrazovky a drží UI plně previewovatelné a testovatelné bez DI.

### 12.2 Open Questions
- [ ] **Ikony: Material Icons vs ruční Lucide-like vektory?** — Proposed direction: zkusit `material-icons-core` (Check/Star/…); co chybí (Lucide `zap`, `scan`) dokreslit ručně ve 2px stylu. Rozhodnout v Step 5.
- [ ] **Label trasy — nese `RouteDetail` písmeno (A/B) a `distanceKm`?** — Proposed direction: ověřit pole na prvním buildu; když ne, zobrazit jen jméno + km, letter vynechat.
- [ ] **Chování CTA „Stáhnout data akce" v Etapě UI-01** — Proposed direction: hoisted no-op/log; reálné spuštění `DownloadViewModel` + Preparation obrazovka + navigace řešit v app-shell plánu.
- [ ] **Offline indikátor** — Proposed direction: dočasně `true`; napojit `ConnectivityMonitor` (přes malý `ConnectivityViewModel`/use-case) v navazujícím kroku.

### 12.3 Suggestions & Follow-ups
- **App shell plán** (bottom nav + scan FAB + tab routing + splash) — přímý navazující krok; `DenikScreen` do něj zapadne jako obsah tabu „Deník". **Musí být platform-native**: Android Material3 nav vs. iOS nativní tab bar („liquid glass"), scan FAB nativně per platforma — viz KLÍČOVÝ PRINCIP v `.claude/design/design-system.md`. Mockup vypadá jednotně, ale chrome/navigace/interakce se dělá nativně; sdílí se jen značka, tokeny, obsah a doménové koncepty.
- **iOS SwiftUI Deník** — zrcadlový plán nad stejnými VM (přes SKIE accessory `runLogViewModel()`/`appViewModel()`).
- **Fonty** — přidat Big Shoulders Display + Inter TTF do `androidApp/src/main/res/font/` a přepnout `Ta33Type.Display/Body` (1 řádek každý).
- **Compose UI testy** — po ustálení komponent přidat testy na 3 stavy (Compose test rule).
- **Pre-start view** (READY, běh nezačal) — rich obrazovka „připraveno, naskenuj start" místo dočasného fallbacku.
