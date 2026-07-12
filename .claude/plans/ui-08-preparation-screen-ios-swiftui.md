# UI-08 - Preparation / Stažení dat akce (iOS SwiftUI)

> **Summary**: iOS SwiftUI gate obrazovka „Příprava dat akce" nad `DownloadViewModel` (FR-11, SKIE) - prompt/průběh/pauza/chyba/Wi-Fi-gating; po dokončení `RootView` sám přejde do TabView. Zrcadlo Androidu (ui-07), iOS-nativní. Nahrazuje `PreparationPlaceholder`.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
iOS shell (ui-04) ukazuje při `readiness .notReady/.preparing` jen `PreparationPlaceholder`. FR-11 `DownloadViewModel` je hotový - chybí obrazovka stažení dat akce.

### 1.2 Solution Overview
`PreparationView` přes `PreparationModel` (ObservableObject nad `DownloadViewModel`, SKIE `for await`) vykreslí dle `progress.overallStatus` + `blockedByNetwork` nativní obsah: výzva (IDLE), průběh + pauza (DOWNLOADING), pokračovat (PAUSED), chyba + opakovat (ERROR), banner „Čeká na Wi-Fi". Ovládá `start/pause/resume/retry/setNetworkPreference`. Po dokončení `preparation.status=READY` → `AppReadiness.ready` → `RootView` sám přepne na `TabView`. Nahradí `PreparationPlaceholder` v `RootView`.

### 1.3 Scope: What This IS
- iOS `PreparationView` nad `DownloadViewModel` (SKIE).
- Stavy IDLE/DOWNLOADING/PAUSED/ERROR + blockedByNetwork; nativní `ProgressView` (bar), `Toggle` Wi-Fi-only.
- `PreparationModel` (ObservableObject).
- Napojení do `RootView` (větev .notReady/.preparing).

### 1.4 Scope: What This IS NOT
- **Android** (ui-07).
- Reálná data balíčku; velikost „84 MB" zástupná.
- Scan flow, Mapa.

---

## 2. SUCCESS CRITERIA
| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` zelený | příkaz |
| 2 | `xcodebuild ... -scheme iosApp build` → BUILD SUCCEEDED | příkaz / Mac |
| 3 | `.notReady/.preparing` → `PreparationView` místo stubu | běh |
| 4 | IDLE: výzva + Wi-Fi Toggle + „Stáhnout data akce" → `start()` | Preview |
| 5 | DOWNLOADING: `ProgressView(value:)` + per-item + „Pozastavit" → `pause()` | Preview |
| 6 | PAUSED → „Pokračovat" `resume()`; ERROR → „Zkusit znovu" `retry()` | Preview |
| 7 | `blockedByNetwork` → banner „Čeká na Wi-Fi" | Preview |
| 8 | Žádný hardcoded hex/CGFloat - vše přes `Ta33Color/Font/Spacing/Radius` | code review |
| 9 | Runtime na simulátoru - DEFERRED na Mac | manuální |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture
```
RootView: readiness .notReady/.preparing → PreparationView
   │  @StateObject PreparationModel (DownloadViewModel přes SKIE)
   ▼
 switch model.state.progress.overallStatus:
   .idle        → intro + Toggle(Wi-Fi only) + PrimaryButton "Stáhnout data akce · 84 MB"
   .downloading → ProgressView(value: overallFraction) + per-item + "Pozastavit"
   .paused      → ProgressView + "Pokračovat"
   .error       → error text + "Zkusit znovu"
   .done        → ProgressView() (přechodně; RootView přepne)
 + if blockedByNetwork → warning banner
```

### 3.2 Key Decisions
| Decision | Choice | Reasoning |
|---|---|---|
| Přechod po DONE | readiness gate v `RootView` | Konzistentní s FR-01; ne explicitní nav |
| Progress | nativní `ProgressView(value:total:)` | iOS-nativní |
| Wi-Fi toggle | nativní `Toggle` | iOS-nativní |
| Model | ObservableObject nad `DownloadViewModel` (SKIE) | Vzor ui-02/04/06 |

---

## 4. IMPLEMENTATION STEPS

> Adresář: `iosApp/iosApp/UI/`. Nové soubory se do targetu zařadí samy.

### Step 1: `PreparationModel`
**Files**: `iosApp/iosApp/UI/Preparation/PreparationModel.swift` (create)
```swift
@MainActor
final class PreparationModel: ObservableObject {
    private let vm = ViewModelProvider.shared.downloadViewModel()
    @Published var state: DownloadUiState = /* explicit init (SKIE nemá no-arg) */ ...
    func observe() async { for await s in vm.state { self.state = s } }
    func start() { vm.start() }
    func pause() { vm.pause() }
    func resume() { vm.resume() }
    func retry() { vm.retry() }
    func setWifiOnly(_ on: Bool) { vm.setNetworkPreference(pref: on ? .wifiOnly : .wifiAndCellular) }  // ověřit názvy
}
```
Ověřit SKIE: `downloadViewModel()` accessor (v Koin.kt), `NetworkPreference` casing (`.wifiOnly`/`.wifiAndCellular`), explicitní init `DownloadUiState` (vnořené `OfflinePackageProgress()`/`PreparationState()` mají default init v Kotlinu - SKIE ale ne; sestavit explicitně nebo přes optional + placeholder).
**Done when**: framework link zelený.

### Step 2: `PreparationFormat` + banner
**Files**: `iosApp/iosApp/UI/Preparation/PreparationView.swift` (bude obsahovat i pomocné)
- `statusView` switch nad `overallStatus` (SKIE enum `.idle/.downloading/.paused/.done/.error`).
- Warning banner (reuse styl z ui-02 `OfflineBanner` komponenty, nebo inline `Ta33Color.warningTint`).
**Done when**: kompiluje.

### Step 3: `PreparationView` + napojení do `RootView`
**Files**: `iosApp/iosApp/UI/Preparation/PreparationView.swift` (create), `UI/Shell/RootView.swift` (modify)
```swift
struct PreparationView: View {
    @StateObject private var model = PreparationModel()
    var body: some View {
        ScrollView {
            VStack(spacing: Ta33Spacing.x5) {
                IdentityCardView(...)   // event header (reuse z ui-02 Components)
                Group {
                    switch model.state.progress.overallStatus {
                    case .idle:
                        Text("Stáhni si trasy, kontroly a mapu, dokud máš signál.")
                        Toggle("Jen přes Wi-Fi", isOn: Binding(get: { model.state.networkPreference == .wifiOnly },
                                                               set: { model.setWifiOnly($0) })).tint(Ta33Color.orange)
                        PrimaryButtonView("Stáhnout data akce · 84 MB") { model.start() }
                    case .downloading:
                        ProgressView(value: model.state.progress.overallFraction).tint(Ta33Color.orange)
                        ForEach(model.state.progress.items, id: \.id) { ItemRow($0) }
                        OutlineButtonView("Pozastavit") { model.pause() }
                    case .paused:
                        ProgressView(value: model.state.progress.overallFraction).tint(Ta33Color.orange)
                        PrimaryButtonView("Pokračovat") { model.resume() }
                    case .error:
                        Text("Stahování selhalo").foregroundStyle(Ta33Color.error)
                        PrimaryButtonView("Zkusit znovu") { model.retry() }
                    default: ProgressView()
                    }
                }
                if model.state.blockedByNetwork { WarningBanner("Čeká na Wi-Fi - připoj se, ať můžeš stáhnout data") }
            }.padding(Ta33Spacing.x4)
        }
        .background(Ta33Color.cream)
        .task { await model.observe() }
    }
}
```
V `RootView`: `.notReady, .preparing:` → `PreparationView()` (místo `PreparationPlaceholder()`).
Reuse `PrimaryButtonView`/`OutlineButtonView`/IdentityCard z ui-02 Components (ověřit názvy; případně použít existující). `ItemRow` = label + malý `ProgressView`/status ikona (SF Symbol).
**Done when**: gate ukáže Preparation; po DONE `RootView` přepne na TabView.

### Step 4: Ověření
`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` + `xcodebuild ... build`. Runtime na Mac.

---

## 5. EDGE CASES & ERRORS
| Scenario | Expected | Handle |
|---|---|---|
| `blockedByNetwork` | Banner; start se nespustí | banner + start disabled |
| ERROR uprostřed | retry pokračuje (skip done) | `retry()` |
| DONE | Spinner, pak RootView přepne | gate |
| `items` prázdné | Jen overall progress | `ForEach` nic |
| SKIE enum casing / init | Ověřit, adaptovat | build |

## 6. SECURITY CONSIDERATIONS
- HTTPS z `ContentConfig`. Žádná citlivá data v UI.

## 7. ASSUMPTIONS
1. **SKIE**: `downloadViewModel()`, `NetworkPreference` casing, `setNetworkPreference(pref:)`, `DownloadUiState` bez no-arg init (explicitní init). Ověřit na buildu.
2. **Přechod po DONE** řeší `RootView` readiness gate.
3. **VM startuje sám** v init.
4. **Velikost „84 MB"** zástupná.
5. **Simulátor nenaboot** → runtime na Mac; sandbox: framework link + xcodebuild.

## 8. QUICK REFERENCE
### Files to Create
- `iosApp/iosApp/UI/Preparation/{PreparationModel,PreparationView}.swift`
### Files to Modify
- `iosApp/iosApp/UI/Shell/RootView.swift` - .notReady/.preparing → `PreparationView`
### Commands
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## 9. DESIGN REFERENCE
- `.claude/design/design-system.md`; `ui_kits/ta33-app/Screens.jsx::DenikBeforeScreen`.
### Native adaptace (odlišné od Android ui-07)
- Nativní `ProgressView(value:)`, `Toggle`.
- SF Symbols pro per-item stav (`checkmark`, `exclamationmark.triangle`).
### Style Mapping
| Design | Swift | Value |
|---|---|---|
| event header | reuse IdentityCard (`Ta33Color.slate800`) | #1C2A36 |
| progress tint | `Ta33Color.orange` | #F76A0E |
| CTA | `Ta33Color.orange` | #F76A0E |
| „čeká na Wi-Fi" | `Ta33Color.warningTint` + `.warning` | #FBE9C2 / #E8A92A |
| chyba | `Ta33Color.error` | #D63A2F |

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before | After |
|---|---|---|
| .notReady/.preparing gate (iOS) | `PreparationPlaceholder()` | `PreparationView()` |

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-10 | Initial plan created |

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES
### 12.1 Alternatives
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Gate obrazovka řízená readiness, nativní ProgressView/Toggle** | Konzistentní s FR-01 + iOS-nativní | Přechod „neviditelný" | ✅ |
| B. Explicitní navigace po DONE | Explicitní | Duplikuje readiness | - |
| C. Klon Compose karet | Shoda s Androidem | Porušuje nativní princip | - |
### 12.2 Open Questions
- [ ] **SKIE casing/init** (`NetworkPreference`, `DownloadUiState`) - ověřit na buildu.
- [ ] **Reuse názvy komponent z ui-02** (PrimaryButton/IdentityCard) - ověřit skutečné názvy.
### 12.3 Suggestions & Follow-ups
- Skutečná velikost/manifest; MB formát.
- Sjednocené stringy přes SKIE (Res.string).
