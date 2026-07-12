# UI-10 - Scan flow: QR sken + sběr kontroly + Splnění (iOS SwiftUI)

> **Summary**: iOS scan flow (zrcadlo Androidu ui-09) - QR scan přes `.fullScreenCover` (start/cíl, FR-09) spouštěný scan tlačítkem, nabídka sběru kontroly (FR-08) a zelená Splnění obrazovka. Kamera simulovaná, reálná AVFoundation/Vision je follow-up. Nativní SwiftUI.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Scan tlačítko v iOS shellu (ui-04) má jen stub `onScan`. FR-09 (`TimingViewModel`) a FR-08 (`ControlCollectionViewModel`) jsou hotové a nespotřebované - chybí scan obrazovka, nabídka sběru a Splnění.

### 1.2 Solution Overview
`RootView` (nebo shell wrapper) drží `TimingViewModel` + `ControlCollectionViewModel` (SKIE) bound na aktivní běh. Scan tlačítko otevře `ScanView` přes `.fullScreenCover` (slate-900, oranžový rámeček + pulzující linka; kamera zástupná; tlačítka „Simulovat start/cíl QR" → `onQrScanned`). Když `candidate != nil`, ukáže se `CollectionOfferView` (nativní overlay/sheet) → `confirm()`. Při `JustCollected` se přes `.fullScreenCover` ukáže `SplneniView` (zelená). Nativní SwiftUI.

### 1.3 Scope: What This IS
- `ScanView` (FR-09) přes `.fullScreenCover`: scan vizuál + simulační tlačítka + výsledek. Napojení na `TimingViewModel`.
- `CollectionOfferView` (FR-08): nabídka sběru pro `candidate` → `confirm()`.
- `SplneniView` (zelená success) pro `JustCollected` přes `.fullScreenCover`.
- `ScanFlowModel` (ObservableObject nad oběma VM); napojení do `RootView` (scan tlačítko + overlaye).

### 1.4 Scope: What This IS NOT
- **Android** (ui-09).
- **Reálná kamera** (AVFoundation/Vision) + oprávnění - follow-up (device); teď simulace.
- **Reálný GPS candidate** - device.
- Mapa, ostatní obrazovky.

---

## 2. SUCCESS CRITERIA
| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` zelený | příkaz |
| 2 | `xcodebuild ... -scheme iosApp build` → BUILD SUCCEEDED | příkaz / Mac |
| 3 | Scan tlačítko (při aktivním běhu) otevře `ScanView` (`.fullScreenCover`) | běh |
| 4 | ScanView: rámeček + pulzující linka + „Namiř na QR kontroly" | Preview |
| 5 | „Simulovat start/cíl QR" → `onQrScanned(payload)`; výsledek (`ScanTimingResult`) zobrazen | Preview |
| 6 | Špatná trasa / cizí QR → hláška | Preview |
| 7 | `candidate != nil` → `CollectionOfferView` (název + vzdálenost); „Sebrat" → `confirm()` | Preview |
| 8 | `lastResult == JustCollected` → `SplneniView` (zelená, název, čas); „Pokračovat" zavře | Preview |
| 9 | Žádný hardcoded hex/CGFloat - vše přes tokeny | code review |
| 10 | Runtime na simulátoru - DEFERRED na Mac | manuální |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture
```
RootView (aktivní běh) → ScanFlowModel
   │  ViewModelProvider.shared.timingViewModel()            → bind(runId:routeId:)
   │  ViewModelProvider.shared.controlCollectionViewModel() → bind(runId:routeId:)
   │  @Published timing / collection
   ├─ scan tlačítko → showScan=true → .fullScreenCover { ScanView(...) }
   ├─ .overlay(if candidate != nil) CollectionOfferView(candidate, confirm)
   └─ .fullScreenCover(isPresented: lastResult is JustCollected && !dismissed) { SplneniView(...) }
```

### 3.2 Key Decisions
| Decision | Choice | Reasoning |
|---|---|---|
| Kamera | Zástupná + simulační tlačítka | Rozhodnutí uživatele; kamera follow-up (device) |
| Scan modal | `.fullScreenCover` | Nativní iOS full-screen prezentace (design: fullscreen slate) |
| Splnění | `.fullScreenCover` | Nativní; oslavná zelená obrazovka |
| Nabídka sběru | overlay/`.safeAreaInset(bottom)` nebo `.sheet` | Nativní spodní nabídka; nerušivá |
| Splnění dismiss | lokální `@State dismissed` | VM `lastResult` nevyčistí sám |

---

## 4. IMPLEMENTATION STEPS

> Adresář: `iosApp/iosApp/UI/`. Nové soubory se do targetu zařadí samy.

### Step 0: QR payload formát
**Files**: (čtení) `shared/.../domain/qr/QrPayloadParser.kt`, `domain/model/QrTimingConfig.kt`
Zjisti formát start/cíl payloadu pro simulaci (`simulateStart(routeId)`/`simulateFinish(routeId)`).
**Done when**: znám formát.

### Step 1: `ScanFlowModel`
**Files**: `iosApp/iosApp/UI/Scan/ScanFlowModel.swift` (create)
```swift
@MainActor
final class ScanFlowModel: ObservableObject {
    private let timing = ViewModelProvider.shared.timingViewModel()
    private let collection = ViewModelProvider.shared.controlCollectionViewModel()
    @Published var timingState: TimingUiState = /* explicit init */ ...
    @Published var collectionState: ControlCollectionUiState = /* explicit init */ ...
    private var bound = false
    func bind(runId: String, routeId: String) {
        guard !bound else { return }; bound = true
        timing.bind(runId: runId, routeId: routeId); collection.bind(runId: runId, routeId: routeId)
    }
    func observe() async { for await s in timing.state { self.timingState = s } }
    func observeCollection() async { for await s in collection.state { self.collectionState = s } }
    func simulateStart(_ routeId: String) { timing.onQrScanned(raw: startPayload(routeId)) }
    func simulateFinish(_ routeId: String) { timing.onQrScanned(raw: finishPayload(routeId)) }
    func confirmCollect() { collection.confirm() }
}
```
Ověřit SKIE: accessory `timingViewModel()`/`controlCollectionViewModel()`, `onQrScanned(raw:)`, explicitní init stavů, `ScanTimingResult`/`CollectionOutcome` jako Swift enums (pattern match).
**Done when**: framework link zelený.

### Step 2: `ScanView`
**Files**: `iosApp/iosApp/UI/Scan/ScanView.swift` (create)
- Full-screen `ZStack { Ta33Color.slate900.ignoresSafeArea(); ... }`. Zavírací X (SF `xmark`). Uprostřed zástupná scan plocha: oranžové L-rohy (`Path`/SF) + pulzující linka (`.animation(.easeInOut.repeatForever)`). Text „Namiř na QR kontroly" + hint.
- Dole `PrimaryButton("Simulovat start QR")` / `OutlineButton("Simulovat cíl QR")`.
- Výsledek dle `timingState.lastScan` (Swift enum switch → hláška).
- `// TODO(device): AVCaptureSession + VNBarcodeObservation → onQrScanned(raw)`.
**Done when**: `#Preview`.

### Step 3: `CollectionOfferView` + `SplneniView`
**Files**: `iosApp/iosApp/UI/Scan/CollectionOfferView.swift`, `SplneniView.swift` (create)
- `CollectionOfferView(candidate, isCollecting, onCollect)` - `PaperCard`-styl karta: „Kontrola v dosahu", `KP-{ordinal} · {name}` + „{distance} m", `PrimaryButton("Sebrat")` (disabled + `ProgressView` když `isCollecting`).
- `SplneniView(controlName, timeText, subtitle, onClose)` - full-screen `Ta33Color.success`, velký SF `checkmark` v kruhu, název (display), čas (velký), podtitul, dole „Pokračovat na trase".
**Done when**: `#Preview` obou.

### Step 4: Napojení do `RootView`
**Files**: `iosApp/iosApp/UI/Shell/RootView.swift` (modify)
- V `.ready` větvi (kde je TabView + scan overlay z ui-04): vytvořit `@StateObject ScanFlowModel`, `bind` když `app.activeRunId/activeRouteId != nil`, `.task` observery.
- Scan tlačítko (overlay z ui-04) → `showScan = true`; `.fullScreenCover(isPresented: $showScan) { ScanView(...) }`.
- `CollectionOfferView` jako `.safeAreaInset(edge: .bottom)` nebo overlay když `candidate != nil`.
- `.fullScreenCover` pro `SplneniView` když `lastResult is CollectionOutcome.JustCollected && !dismissed`.
**Done when**: scan tlačítko otevře ScanView; offer + Splnění dle stavu.

### Step 5: Ověření
`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` + `xcodebuild ... build`. Runtime na Mac.

---

## 5. EDGE CASES & ERRORS
| Scenario | Expected | Handle |
|---|---|---|
| Scan bez běhu | Scan tlačítko skryté (ui-04) | už řešeno |
| `RunNotFound`/`NotATimingQr` | Hláška, cover zůstane | enum switch |
| Finish před startem | `FinishBeforeStart` hláška | enum |
| `JustCollected` pak nový candidate | Splnění zavře, offer se může objevit | `dismissed` flag |
| `AlreadyCollected`/`OutOfRange` | Nekritická hláška, žádné Splnění | pattern match jen JustCollected |
| SKIE enum/init | Ověřit na buildu | build |

## 6. SECURITY CONSIDERATIONS
- QR payload veřejný. Kamera (follow-up) potřebuje `NSCameraUsageDescription` v Info.plist + runtime permission. Poloha sběru on-device; nelogovat.

## 7. ASSUMPTIONS
1. **Kamera simulovaná** (rozhodnutí uživatele); AVFoundation/Vision follow-up (device).
2. **GPS candidate** device-only.
3. **SKIE**: accessory + `onQrScanned(raw:)` + explicitní init stavů + enum pattern match - ověřit na buildu.
4. **QR formát** z parseru (Step 0).
5. **Splnění dismiss** lokálním `@State`.
6. **Simulátor nenaboot** → runtime na Mac.

## 8. QUICK REFERENCE
### Files to Create
- `iosApp/iosApp/UI/Scan/{ScanFlowModel,ScanView,CollectionOfferView,SplneniView}.swift`
### Files to Modify
- `iosApp/iosApp/UI/Shell/RootView.swift` - scan tlačítko → ScanView; offer + Splnění; bind VM
### Commands
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## 9. DESIGN REFERENCE
- `ui_kits/ta33-app/Screens.jsx::ScanModal, SplneniScreen`; `mockups/02-splneni-zelena.html`; `.claude/design/design-system.md`.
### Native adaptace (odlišné od Android ui-09)
- `.fullScreenCover` místo Compose overlaye; SF Symbols (`xmark`, `checkmark`, `viewfinder`).
- Nabídka sběru přes `.safeAreaInset(.bottom)`/overlay.
### Style Mapping
| Design | Swift | Value |
|---|---|---|
| scan modal bg | `Ta33Color.slate900` | #15202B |
| scan rohy/linka | `Ta33Color.orange` + glow | #F76A0E |
| Splnění bg | `Ta33Color.success` | #1FA85A |
| offer karta | `PaperCard`/`Ta33Color.paper` | #FFFFFF |
| CTA | `Ta33Color.orange` | #F76A0E |

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before | After |
|---|---|---|
| Scan tlačítko `onScan` (iOS) | stub | otevře `ScanView` |
| Sběr / Splnění (iOS) | žádné UI | offer + Splnění |

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-10 | Initial plan created |

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES
### 12.1 Alternatives
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Simulace teď + `.fullScreenCover`, kamera později** | Testovatelné, nativní | Kamera zvlášť (device) | ✅ |
| B. Reálná AVFoundation/Vision hned | Kompletní | Velký lift, v sandboxu neověřitelné | - |
| C. Klon Compose overlaye | Shoda s Androidem | Porušuje nativní princip | - |
### 12.2 Open Questions
- [ ] **QR formát** - zjistit z parseru (Step 0).
- [ ] **Nabídka sběru: `.sheet` vs `.safeAreaInset`** - Proposed: `.safeAreaInset(.bottom)` (nerušivé), potvrdit UX.
- [ ] **Auto-dismiss ScanView po Started/Finished** - Proposed: krátká hláška, pak zavřít.
### 12.3 Suggestions & Follow-ups
- **Reálná kamera** (AVFoundation + Vision `VNBarcodeObservation`) + `NSCameraUsageDescription` - device plán; `onQrScanned` beze změny.
- GPS candidate terénní test.
- Splnění i pro cíl (Finished).
- Sjednocené stringy přes SKIE.
