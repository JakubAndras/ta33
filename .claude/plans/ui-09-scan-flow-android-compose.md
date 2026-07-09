# UI-09 — Scan flow: QR sken + sběr kontroly + Splnění (Android Compose)

> **Summary**: Android scan flow — QR scan modal (start/cíl, FR-09) spouštěný scan FABem, nabídka sběru kontroly v dosahu (FR-08) a zelená obrazovka Splnění. Kamera zatím simulovaná (tlačítko), reálná CameraX+MLKit je follow-up.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Scan FAB v app-shellu (ui-03) má jen stub `onScan`. Logika časomíry (FR-09, `TimingViewModel`) a sběru kontrol přes GPS (FR-08, `ControlCollectionViewModel`) je hotová a nespotřebovaná — chybí scan modal, nabídka sběru a obrazovka Splnění.

### 1.2 Solution Overview
Dvě mechaniky:
- **QR start/cíl (FR-09):** scan FAB otevře `ScanModal` (full-screen, slate-900, oranžový rámeček + pulzující linka). Kamera je zatím zástupná plocha; tlačítka „Simulovat start QR" / „Simulovat cíl QR" feednou payload do `TimingViewModel.onQrScanned(raw)`. Výsledek (`ScanTimingResult`) se zobrazí (spuštěno / cíl / špatná trasa / …).
- **Sběr kontroly (FR-08):** `ControlCollectionViewModel` (bound na aktivní běh v shellu) vystaví `candidate` když je kontrola v dosahu → spodní nabídka „Kontrola v dosahu · {název} · {vzdálenost} m" + „Sebrat" → `confirm()`. Při `JustCollected` se ukáže **`SplneniScreen`** (zelená, fajfka, název kontroly, čas, „Pokračovat na trase").

Vše se hostí v `MainShell` (nad taby): scan FAB → ScanModal; candidate offer + Splnění overlay dle `ControlCollectionViewModel`.

### 1.3 Scope: What This IS
- `ScanModal` (FR-09): scan frame vizuál + pulzující linka + simulační tlačítka (start/cíl) + výsledek + zavřít. Napojeno na `TimingViewModel`.
- `CollectionOfferSheet` (FR-08): spodní nabídka sběru pro `candidate`; „Sebrat" → `confirm()`.
- `SplneniScreen` (zelená success): pro `JustCollected`.
- Napojení do `MainShell`: scan FAB otevře modal; `ControlCollectionViewModel` bound na aktivní běh; offer + Splnění overlay.

### 1.4 Scope: What This IS NOT
- **iOS** — ui-10.
- **Reálná kamera** (CameraX + ML Kit Barcode) + oprávnění — jasně označený follow-up; teď jen simulace tlačítkem. (Rozhodnutí uživatele: „UI + simulace teď, kamera později".)
- **Reálný GPS candidate** — produkuje ho FR-08 z živé polohy; v sandboxu neověřitelné, jen na zařízení. UI/wiring hotové.
- Mapa, ostatní obrazovky.

---

## 2. SUCCESS CRITERIA
| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | `./gradlew :androidApp:assembleDebug` zelený | příkaz |
| 2 | Scan FAB (při aktivním běhu) otevře `ScanModal` | běh / preview |
| 3 | ScanModal: rámeček + pulzující linka + „Namiř na QR kontroly" | preview |
| 4 | „Simulovat start QR" → `onQrScanned(startPayload)`; výsledek Started zobrazen | preview + běh |
| 5 | „Simulovat cíl QR" → Finished (příp. FinishBeforeStart/AlreadyFinished dle stavu) | preview |
| 6 | Špatná trasa / cizí QR → odpovídající hláška (`WrongRoute`/`NotATimingQr`) | preview |
| 7 | `candidate != null` → `CollectionOfferSheet` s názvem + vzdáleností; „Sebrat" → `confirm()` | preview |
| 8 | `lastResult == JustCollected` → `SplneniScreen` (zelená, název, čas); „Pokračovat" zavře | preview |
| 9 | `AlreadyCollected`/`OutOfRange` → nekritická hláška, žádný pád | preview |
| 10 | Žádný hardcoded hex/dp — vše přes theme | code review |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture
```
MainShell (aktivní běh: app.activeRunId/activeRouteId)
   │  koinViewModel() TimingViewModel            → bind(runId, routeId)
   │  koinViewModel() ControlCollectionViewModel → bind(runId, routeId)
   ├─ Scan FAB → showScan = true → ScanModal(timing.state, onSimulateStart/Finish, onClose)
   ├─ if collection.candidate != null → CollectionOfferSheet(candidate, onCollect = confirm)
   └─ if collection.lastResult is JustCollected → SplneniScreen(control, time, onClose)
```
`ScanModal` čte `TimingUiState.lastScan` (`ScanTimingResult`) pro feedback. Simulace: `onQrScanned(samplePayload)` — validní start/cíl payload dle `QrPayloadParser` formátu (agent ověří formát v parseru a QrTimingConfig).

### 3.2 Key Decisions
| Decision | Choice | Reasoning |
|---|---|---|
| Kamera | Zástupná plocha + simulační tlačítka | Uživatel zvolil simulaci teď; kamera follow-up (device) |
| Kde hostit | `MainShell` (nad taby) | Scan FAB i candidate offer jsou globální nad během |
| Binding VM | v `MainShell` když `activeRunId != null` | Sběr/časomíra dávají smysl jen při běhu |
| Splnění trigger | `CollectionOutcome.JustCollected` | Mapuje design „KP · splněno" na FR-08 výsledek |
| Sample payload | Sestavit dle `QrPayloadParser`/`QrTimingConfig` | Validní start/cíl string pro simulaci |

---

## 4. IMPLEMENTATION STEPS

> Adresář: `androidApp/src/main/kotlin/com/example/ta33/ui/`.

### Step 0: Zjistit QR payload formát
**Files**: (čtení) `shared/.../domain/qr/QrPayloadParser.kt`, `domain/model/QrTimingConfig.kt`
Zjisti, jak parser rozpozná start/cíl a route-scope, ať jdou sestavit validní simulační payloady (např. `TA33:START:<routeId>` — ověřit skutečný formát). Použij aktivní `routeId`.
**Done when**: známý formát pro `simulateStart(routeId)` / `simulateFinish(routeId)`.

### Step 1: String resources (cs)
**Files**: `shared/.../composeResources/values{,-cs}/strings.xml`
`scan_title` („Namiř na QR kontroly"), `scan_hint` („Funguje offline · čas se uloží z telefonu"), `scan_sim_start` („Simulovat start QR"), `scan_sim_finish` („Simulovat cíl QR"), `scan_close` („Zavřít"), `scan_started` („Start zaznamenán"), `scan_finished` („Cíl zaznamenán"), `scan_wrong_route` („QR je z jiné trasy"), `scan_not_timing` („Tohle není QR kontroly"), `scan_already_started`/`scan_already_finished`/`scan_finish_before_start`, `collect_offer` („Kontrola v dosahu"), `collect_action` („Sebrat"), `collect_already` („Už sebráno"), `collect_out_of_range` („Mimo dosah"), `splneni_title` („splněno"), `splneni_continue` („Pokračovat na trase").
**Done when**: klíče dostupné.

### Step 2: `ScanModal`
**Files**: `ui/scan/ScanModal.kt` (create)
- Full-screen `Surface(color = slate-900)` (reuse identity/slate token). Nahoře X (`onClose`). Uprostřed zástupná scan plocha: oranžové L-rohy (Canvas/ikona) + pulzující vodorovná linka (`rememberInfiniteTransition`). Text `scan_title` + `scan_hint`.
- Dole simulační tlačítka `PrimaryButton(scan_sim_start)` / `OutlineButton(scan_sim_finish)`.
- Výsledek: pod tlačítky text dle `ScanTimingResult` (mapování na Res string). Started/Finished → krátce zobrazit a případně auto-zavřít.
- Komentář `// TODO(device): nahradit zástupnou plochu CameraX PreviewView + ML Kit BarcodeScanner → onQrScanned(raw)`.
**Done when**: `@Preview` (výchozí, po Started, po WrongRoute).

### Step 3: `CollectionOfferSheet`
**Files**: `ui/scan/CollectionOfferSheet.kt` (create)
- Spodní `PaperCard` (nad bottom navem): `Overline(collect_offer)`, `KP-{ordinal} · {name}` + „{distance} m", `PrimaryButton(collect_action, onCollect)`. `isCollecting` → tlačítko disabled + spinner.
**Done when**: `@Preview`.

### Step 4: `SplneniScreen`
**Files**: `ui/scan/SplneniScreen.kt` (create)
- Full-screen `Surface(color = success)` (fgOnDark text). `Overline("KP · splněno")`, velký `check` glyph (kruh), název kontroly (display), čas (display velký), podtitul (trasa · progress). Dole „Pokračovat na trase" (`onClose`).
- Animace fajfky/scale volitelná (`dur-slow`).
**Done when**: `@Preview`.

### Step 5: Napojení do `MainShell`
**Files**: `ui/shell/MainShell.kt` (modify)
```kotlin
val app: AppUiState // už dostupné
val runId = app.activeRunId; val routeId = app.activeRouteId
if (runId != null && routeId != null) {
    val timing: TimingViewModel = koinViewModel()
    val collection: ControlCollectionViewModel = koinViewModel()
    LaunchedEffect(runId, routeId) { timing.bind(runId, routeId); collection.bind(runId, routeId) }
    val tState by timing.state.collectAsStateWithLifecycle()
    val cState by collection.state.collectAsStateWithLifecycle()
    // scan FAB onScan = { showScan = true }
    if (showScan) ScanModal(tState, onSimStart = { timing.onQrScanned(startPayload(routeId)) },
                            onSimFinish = { timing.onQrScanned(finishPayload(routeId)) }, onClose = { showScan = false })
    cState.candidate?.let { CollectionOfferSheet(it, cState.isCollecting, onCollect = collection::confirm) }
    (cState.lastResult as? JustCollected)?.let { SplneniScreen(..., onClose = { /* lastResult se vyčistí dalším candidate; případně lokální dismiss flag */ }) }
}
```
Pozn.: `lastResult` VM sám nevyčistí po zavření; přidat lokální `dismissed` flag, ať Splnění nezůstane napořád (řešit v UI, ne měnit VM).
**Done when**: FAB otevře modal; offer + Splnění se objeví dle stavu.

### Step 6: Ověření
`./gradlew :androidApp:assembleDebug` + `build`; projít previews. Reálný QR sken (kamera) a GPS candidate → device follow-up.

---

## 5. EDGE CASES & ERRORS
| Scenario | Expected | Handle |
|---|---|---|
| Scan bez aktivního běhu | FAB se nezobrazí (ui-03) | už řešeno |
| `RunNotFound`/`NotATimingQr` | Hláška, modal zůstane | mapování ScanTimingResult |
| Finish před startem | „Nejdřív naskenuj start" (`FinishBeforeStart`) | hláška |
| `JustCollected` pak nový candidate | Splnění se zavře, offer se může objevit | `dismissed` flag + candidate reset |
| `AlreadyCollected`/`OutOfRange` | Nekritická hláška, žádné Splnění | podmínka `as? JustCollected` |
| Splnění „Pokračovat" | zavře overlay | lokální dismiss |

## 6. SECURITY CONSIDERATIONS
- QR payload je veřejný identifikátor kontroly/trasy; žádná citlivá data. Kamera (follow-up) bude potřebovat `CAMERA` oprávnění — runtime request + zdůvodnění (store).
- Poloha sběru je on-device (FR-08), neodesílá se. Nelogovat souřadnice.

## 7. ASSUMPTIONS
1. **Kamera je simulovaná** (rozhodnutí uživatele); reálná CameraX+MLKit je follow-up na zařízení.
2. **GPS candidate** produkuje FR-08 z živé polohy — v sandboxu neověřitelné; UI/wiring hotové, ověření na zařízení.
3. **QR payload formát** je zjistitelný z `QrPayloadParser`/`QrTimingConfig` (Step 0).
4. **Splnění dismiss** se řeší lokálním UI flagem (VM `lastResult` nevyčistí sám).
5. **TimingViewModel/ControlCollectionViewModel** se bindují v shellu při aktivním běhu.

## 8. QUICK REFERENCE
### Files to Create
- `ui/scan/{ScanModal,CollectionOfferSheet,SplneniScreen}.kt`
### Files to Modify
- `ui/shell/MainShell.kt` — scan FAB → modal; candidate offer + Splnění; bind VM
- `shared/.../composeResources/values{,-cs}/strings.xml`
### Commands
```bash
./gradlew :androidApp:assembleDebug
```

## 9. DESIGN REFERENCE
- `ui_kits/ta33-app/Screens.jsx::ScanModal, SplneniScreen`; `.claude/design/design-system.md`; `mockups/02-splneni-zelena.html`.
### Style Mapping
| Design | Code | Value |
|---|---|---|
| scan modal bg | slate-900 (`Ta33Theme.colors` / palette přes token) | #15202B |
| scan rohy / linka | `colorScheme.primary` + glow | #F76A0E |
| Splnění bg | `Ta33Theme.colors.success` | #1FA85A |
| Splnění text | `fgOnDark` / white | |
| offer karta | `PaperCard` | #FFFFFF |
| CTA | `PrimaryButton` | #F76A0E |

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before | After |
|---|---|---|
| Scan FAB `onScan` | stub `{}` | otevře `ScanModal` |
| Sběr kontroly / Splnění | žádné UI | offer sheet + Splnění |

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-10 | Initial plan created |

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES
### 12.1 Alternatives
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Simulace teď (tlačítka) + reálná kamera později** | Testovatelné, odblokuje UI/wiring | Kamera se dodělá zvlášť na zařízení | ✅ |
| B. Reálná CameraX+MLKit hned | Kompletní | Velký nativní lift, v sandboxu neověřitelné | — |
| C. Sběr jen přes „simulovat" bez FR-08 wiringu | Jednodušší | Zahodí hotovou FR-08 logiku | — |
### 12.2 Open Questions
- [ ] **QR payload formát** — Proposed: zjistit z parseru (Step 0); sestavit validní start/cíl string.
- [ ] **Splnění dismiss** — Proposed: lokální `dismissed` flag; nezasahovat do VM.
- [ ] **Auto-zavření modalu po Started/Finished?** — Proposed: krátká hláška, pak zavřít; potvrdit UX.
### 12.3 Suggestions & Follow-ups
- **Reálná kamera** (CameraX + ML Kit Barcode) + `CAMERA` oprávnění — samostatný device plán, nahradí zástupnou plochu (`onQrScanned` zůstane stejné).
- iOS scan flow (ui-10).
- FR-08 `ControlCollectionViewModel` candidate na zařízení (GPS) — terénní test.
- Splnění i pro cíl (Finished) — zvážit oslavnou obrazovku po finiši.
