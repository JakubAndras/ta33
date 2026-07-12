# UI-07 - Preparation / Stažení dat akce (Android Compose)

> **Summary**: Android gate obrazovka „Příprava dat akce" nad `DownloadViewModel` (FR-11) - prompt/průběh/pauza/chyba/Wi-Fi-gating; po dokončení shell sám přejde do Main. Nahrazuje `PreparationPlaceholder`.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
App-shell (ui-03) ukazuje při `readiness NOT_READY/PREPARING` jen `PreparationPlaceholder`. Logika stahování offline balíčku (FR-11, `DownloadViewModel`) je hotová a nespotřebovaná - chybí obrazovka, kde si uživatel stáhne data akce.

### 1.2 Solution Overview
`PreparationScreen` (Compose) přes `koinViewModel()` odebírá `DownloadViewModel.state` (`DownloadUiState`) a podle `progress.overallStatus` + `blockedByNetwork` vykreslí: výzvu ke stažení (IDLE), průběh + pauzu (DOWNLOADING), pokračování (PAUSED), chybu + opakování (ERROR), případně upozornění „čeká na Wi-Fi". Ovládá `start/pause/resume/retry/setNetworkPreference`. Po dokončení se `preparation.status` → READY promítne přes `ObserveAppStateUseCase` do `AppReadiness.READY` a `Ta33App` sám přepne na `MainShell` (žádná explicitní navigace). Nahradí `PreparationPlaceholder` v `Ta33App`.

### 1.3 Scope: What This IS
- Android `PreparationScreen` nad `DownloadViewModel` (FR-11).
- Stavy: IDLE (výzva), DOWNLOADING (progress + per-item + pauza), PAUSED (pokračovat), ERROR (chyba + opakovat), blockedByNetwork (čeká na Wi-Fi).
- Wi-Fi-only přepínač (`setNetworkPreference`).
- Napojení do `Ta33App` (větev NOT_READY/PREPARING) + Deník `onDownload` navádí sem (přes shell gate).

### 1.4 Scope: What This IS NOT
- **iOS** verze - ui-08.
- **Reálná data** balíčku (manifest/dlaždice) - jen UI nad VM; skutečný obsah řeší FR-11 backend (statický JSON, dodá se).
- Odhad velikosti „84 MB" je zástupný text (skutečná velikost z manifestu je follow-up).
- Scan flow, Mapa, ostatní obrazovky.

---

## 2. SUCCESS CRITERIA
| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | `./gradlew :androidApp:assembleDebug` zelený | příkaz |
| 2 | `readiness NOT_READY/PREPARING` → `PreparationScreen` místo placeholderu | běh / preview |
| 3 | IDLE: výzva + „Stáhnout data akce" → `start()` | preview |
| 4 | DOWNLOADING: progress bar (`overallFraction`), per-item řádky, tlačítko Pozastavit → `pause()` | preview |
| 5 | PAUSED: tlačítko Pokračovat → `resume()` | preview |
| 6 | ERROR: chybová hláška + Zkusit znovu → `retry()` | preview |
| 7 | `blockedByNetwork`: upozornění „Čeká na Wi-Fi", stahování se nespustí | preview |
| 8 | Wi-Fi-only přepínač volá `setNetworkPreference` | preview |
| 9 | Žádný hardcoded hex/dp v UI - vše přes theme | code review |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture
```
Ta33App: readiness NOT_READY/PREPARING → PreparationScreen
   │  koinViewModel() DownloadViewModel.state : DownloadUiState
   ▼
 PreparationContent(state, onStart, onPause, onResume, onRetry, onToggleWifiOnly)
   IdentityCard(event)
   PaperCard: when(progress.overallStatus)
     IDLE     → text + Wi-Fi toggle + PrimaryButton "Stáhnout data akce · 84 MB"
     DOWNLOADING → progress bar + per-item list + OutlineButton "Pozastavit"
     PAUSED   → progress bar + PrimaryButton "Pokračovat"
     ERROR    → error text + PrimaryButton "Zkusit znovu"
     DONE     → (přechodně; shell přepne na Main)
   if blockedByNetwork → warning banner "Čeká na Wi-Fi"
```
Po `DONE` se `preparation.status=READY` → `AppReadiness.READY` → `Ta33App` přepne na `MainShell` (gate, ne navigace).

### 3.2 Key Decisions
| Decision | Choice | Reasoning |
|---|---|---|
| Přechod po dokončení | Přes readiness gate (`Ta33App`), ne explicitní nav | Jediný zdroj pravdy je `AppReadiness` (FR-01); shell už přepíná |
| Stavový rozcestník | `progress.overallStatus` + `blockedByNetwork` | Přímé mapování `DownloadUiState` |
| Komponenty | Reuse IdentityCard/PaperCard/PrimaryButton/OutlineButton/progress bar/SettingRow | Konzistence, žádná duplicita |
| Velikost „84 MB" | Zástupný string | Skutečná z manifestu je follow-up |

---

## 4. IMPLEMENTATION STEPS

> Adresář: `androidApp/src/main/kotlin/com/example/ta33/ui/`.

### Step 1: String resources (cs)
**Files**: `shared/.../composeResources/values{,-cs}/strings.xml`
Klíče: `prep_title` („Příprava dat akce"), `prep_intro` („Stáhni si trasy, kontroly a mapu, dokud máš signál."), `prep_download_cta` („Stáhnout data akce · 84 MB"), `prep_pause` („Pozastavit"), `prep_resume` („Pokračovat"), `prep_retry` („Zkusit znovu"), `prep_wifi_only` („Jen přes Wi-Fi"), `prep_waiting_wifi` („Čeká na Wi-Fi - připoj se, ať můžeš stáhnout data"), `prep_error` („Stahování selhalo"), `prep_downloading` („Stahuji data akce…").
**Done when**: klíče dostupné.

### Step 2: Progress komponenta (reuse/extract)
**Files**: `ui/components/ProgressBar.kt` (create - pokud ještě není extrahovaný z Deníku)
- `Ta33ProgressBar(fraction: Float)` - track `surfaceVariant`, fill `primary`, výška 10.dp, `Ta33Radius.pill`. (Deník má inline progress bar; extrahovat sem pro reuse; Deník nechat/přepnout na tuto - volitelné.)
**Done when**: `@Preview`; kompiluje.

### Step 3: Stateless `PreparationContent`
**Files**: `ui/preparation/PreparationContent.kt` (create)
```kotlin
@Composable
fun PreparationContent(
    state: DownloadUiState,
    onStart: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onRetry: () -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
) {
    Column(scroll, padding, cream) {
        IdentityCard(date, place, sub)   // event header (Res strings)
        PaperCard {
            when (state.progress.overallStatus) {
                IDLE, /* fresh */ -> {
                    Text(prep_intro)
                    SettingRow(prep_wifi_only) { Switch(state.networkPreference == WIFI_ONLY, onToggleWifiOnly) }
                    PrimaryButton(prep_download_cta, onStart)
                }
                DOWNLOADING -> {
                    Overline(prep_downloading)
                    Ta33ProgressBar(state.progress.overallFraction.toFloat())
                    state.progress.items.forEach { ItemRow(it) }   // label + per-item fraction/status
                    OutlineButton(prep_pause, onPause)
                }
                PAUSED -> { Ta33ProgressBar(...); PrimaryButton(prep_resume, onResume) }
                ERROR  -> { Text(prep_error, error); PrimaryButton(prep_retry, onRetry) }
                DONE   -> { /* přechodně, shell přepne */ CircularProgressIndicator() }
            }
        }
        if (state.blockedByNetwork) OfflineBannerLike(prep_waiting_wifi)  // warningTint
    }
}
```
`ItemRow(DownloadItemProgress)` - label + malý progress/stav (DONE=fajfka, ERROR=vykřičník). `formatKm`-style helpery netřeba; bytes formát volitelný (MB), jinak jen fraction.
**Done when**: `@Preview` pro IDLE, DOWNLOADING, PAUSED, ERROR, blockedByNetwork.

### Step 4: Stateful `PreparationScreen` + napojení do `Ta33App`
**Files**: `ui/preparation/PreparationScreen.kt` (create), `ui/shell/Ta33App.kt` (modify)
```kotlin
@Composable
fun PreparationScreen(vm: DownloadViewModel = koinViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    PreparationContent(s, vm::start, vm::pause, vm::resume, vm::retry,
        onToggleWifiOnly = { wifiOnly -> vm.setNetworkPreference(if (wifiOnly) WIFI_ONLY else ANY) })
}
```
V `Ta33App`: `NOT_READY, PREPARING -> PreparationScreen()` (místo `PreparationPlaceholder()`).
Ověřit `NetworkPreference` hodnoty (WIFI_ONLY / ANY nebo podobně) v enumu.
**Done when**: gate ukáže Preparation; po DONE přejde na Main.

### Step 5: Ověření
`./gradlew :androidApp:assembleDebug` + `build`; projít previews. Runtime na zařízení na uživateli (síť/stahování nutno ověřit reálně).

---

## 5. EDGE CASES & ERRORS
| Scenario | Expected | Handle |
|---|---|---|
| `blockedByNetwork` při startu | Nespustí se; banner „Čeká na Wi-Fi" | tlačítko start disabled/nahrazeno bannerem |
| ERROR uprostřed | Progress zůstane, retry pokračuje (skip done items) | `retry()` = relaunch |
| DONE | Krátce spinner, pak shell přepne | gate na `AppReadiness` |
| `items` prázdné | Jen overall progress | podmínka |
| `totalBytes == null` | fraction 0 dokud běží (per plán FR-11) | `DownloadItemProgress.fraction` už řeší |
| Pauza a znovu | resume skipuje hotové | `resume()` |

## 6. SECURITY CONSIDERATIONS
- Stahuje se z konfigurovatelné URL (FR-11 `ContentConfig`); HTTPS. Žádná citlivá data v UI. Nelogovat URL s případnými tokeny (Etapa 2).

## 7. ASSUMPTIONS
1. **Přechod po DONE řeší readiness gate**, ne obrazovka - `Ta33App` už přepíná dle `AppReadiness`.
2. **`DownloadViewModel` startuje sám** (observuje connectivity + preparation v init); jen `koinViewModel()` + intents.
3. **`NetworkPreference`** má hodnoty pro „jen Wi-Fi" a „libovolná" - ověřit názvy (WIFI_ONLY / ANY).
4. **Velikost „84 MB"** je zástupná; skutečná z manifestu je follow-up.
5. **Deník `onDownload`** (ui-01) navádí sem - ale protože Preparation je gate nad readiness, stačí, že shell při NOT_READY ukáže Preparation; `onDownload` může zůstat no-op/TODO, nebo (volitelně) rovnou `DownloadViewModel.start()`.

## 8. QUICK REFERENCE
### Files to Create
- `ui/components/ProgressBar.kt`, `ui/preparation/PreparationContent.kt`, `ui/preparation/PreparationScreen.kt`
### Files to Modify
- `ui/shell/Ta33App.kt` - NOT_READY/PREPARING → `PreparationScreen`
- `shared/.../composeResources/values{,-cs}/strings.xml`
### Commands
```bash
./gradlew :androidApp:assembleDebug
```

## 9. DESIGN REFERENCE
- `.claude/design/design-system.md`; `ui_kits/ta33-app/Screens.jsx::DenikBeforeScreen` (styl výzvy ke stažení), `MapBeforeScreen` (outline „Stáhnout dlaždice").
### Style Mapping
| Design | Code | Value |
|---|---|---|
| event header | `IdentityCard` (`identityBg`) | #1C2A36 |
| karta | `PaperCard` | #FFFFFF |
| progress fill | `colorScheme.primary` | #F76A0E |
| progress track | `colorScheme.surfaceVariant` | #E6EAEC |
| CTA | `PrimaryButton` | #F76A0E |
| pauza | `OutlineButton` | orange border |
| „čeká na Wi-Fi" | `warningTint` + `warning` | #FBE9C2 / #E8A92A |
| chyba | `Ta33Theme.colors.error` | #D63A2F |

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before | After |
|---|---|---|
| NOT_READY/PREPARING gate | `PreparationPlaceholder()` | `PreparationScreen()` |

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-10 | Initial plan created |

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES
### 12.1 Alternatives
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Gate obrazovka řízená readiness, přechod přes AppReadiness** | Konzistentní s FR-01, žádná duplicitní navigace | Přechod „neviditelný" (gate) | ✅ |
| B. Explicitní navigace Preparation → Main po DONE | Explicitní | Duplikuje readiness logiku, riziko rozsynchronizování | - |
| C. Preparation jako modal nad Deníkem | Blízko designu (download prompt v Deníku) | Míchá gate a obsah, hůř pro PREPARING stav | - |
### 12.2 Open Questions
- [ ] **`NetworkPreference` hodnoty** - Proposed: ověřit enum (WIFI_ONLY/ANY) na buildu.
- [ ] **Deník `onDownload` cíl** - Proposed: no-op (gate stačí), nebo `start()`; potvrdit chování.
- [ ] **Velikost balíčku** - Proposed: zástupné „84 MB"; napojit z manifestu později.
### 12.3 Suggestions & Follow-ups
- iOS Preparation (ui-08).
- Skutečná velikost/manifest z FR-11 backendu; MB formát bytes.
- Deník download CTA → `DownloadViewModel.start()` napřímo.
