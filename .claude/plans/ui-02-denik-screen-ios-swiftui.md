# UI-02 - Deník / Itinerář (iOS SwiftUI)

> **Summary**: Postavit nativní iOS SwiftUI obrazovku „Deník" jako zrcadlo Android `ui-01` - stejné ViewModely (přes SKIE), stejná značka/tokeny/obsah, ale **nativní SwiftUI vzhled a chování** (ne klon Compose).

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Deník je hotový na Androidu (`ui-01`), ale iOS app zobrazuje jen placeholder „TA33". Potřebujeme nativní SwiftUI verzi téže obrazovky nad stejnou sdílenou logikou.

### 1.2 Solution Overview
`DenikView` (SwiftUI) přes `ViewModelProvider` (SKIE) drží `AppViewModel` + `RunLogViewModel` (+ `RouteDetailViewModel`), pozoruje jejich `StateFlow` jako `AsyncSequence`, a vykresluje tři stavy podle `AppUiState.readiness`. Komponenty (`IdentityCard`, `PaperCard`, `PrimaryButton`, `OfflineBanner`, `KPRow`…) jako nativní SwiftUI views nad existujícími `Ta33Color/Ta33Font/Ta33Spacing/Ta33Radius`. **Layout a interakce jsou iOS-nativní** (viz KLÍČOVÝ PRINCIP v `.claude/design/design-system.md`), ne 1:1 přepis Androidu.

### 1.3 Scope: What This IS
- iOS SwiftUI obrazovka **Deník** ve 3 stavech: LOADING, obsah nestažen (`DenikBefore`), na trase (`DenikOnRoute`).
- Sada znovupoužitelných SwiftUI komponent v `iosApp/iosApp/UI/Components/` (sdílené i pro Mapa/Profil).
- ObservableObject wrappery pro pozorování sdílených `StateFlow` (SKIE `AsyncSequence`).
- Napojení do `ContentView` → reálné zobrazení (`iOSApp.swift`).

### 1.4 Scope: What This IS NOT
- **Android** (hotovo v `ui-01`).
- **App shell** (tab bar Deník/Mapa/Profil, scan tlačítko, navigace, splash) - samostatný plán; na iOS **nativní** `TabView`/liquid-glass, ne klon Android pill.
- Scan/Splnění/Mapa/Profil obrazovky.
- Reálné stahování/Preparation (CTA jen callback).
- Fonty (Big Shoulders/Inter) - zůstává systémový fallback (`Ta33Font`).
- Vzdálenost „310 m" (LogUiState ji nenese).

---

## 2. SUCCESS CRITERIA

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | `Shared` framework se slinkuje: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` zelený | příkaz |
| 2 | iOS app se zkompiluje přes `xcodebuild ... -scheme iosApp ... build` (pokud je Xcode k dispozici) | příkaz / odloženo na stroj uživatele |
| 3 | `readiness==LOADING` → `ProgressView` (nativní spinner) na cream pozadí | běh / SwiftUI Preview |
| 4 | `NOT_READY/ABSENT` → IdentityCard + empty-state + CTA „STÁHNOUT DATA AKCE · 84 MB" | Preview |
| 5 | `READY`+run → OfflineBanner + progress („2 / 5" + bar) + dvě sekce KP (Příští checkpoint / Hotovo) | Preview s fake LogUiState |
| 6 | KP swatch stavy (DONE/ACTIVE/LOCKED/FINISH) barevně odpovídají tokenům | vizuální kontrola |
| 7 | Prázdný běh (`collectedCount==0`) se vykreslí bez pádu | Preview |
| 8 | Žádné hardcoded hex/CGFloat v UI - vše přes `Ta33Color/Ta33Font/Ta33Spacing/Ta33Radius` | code review |
| 9 | Layout je iOS-nativní: `List`/`ScrollView`+`Section`, safe areas, nativní scroll - ne přepis Compose | code review vůči principu |
| 10 | Reálný běh na simulátoru/zařízení - DEFERRED (simulátor v sandboxu nenaboot); ověřit na stroji uživatele | manuální |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
 iOSApp (doInitKoin) → ContentView → DenikView
        │  @StateObject DenikModel (ObservableObject)
        │     ├─ ViewModelProvider.shared.appViewModel()        (FR-01 gate)
        │     │     for await s in appVM.state { self.app = s }
        │     ├─ ViewModelProvider.shared.runLogViewModel()     (FR-04 obsah)
        │     │     bind(runId:routeId:) → for await s in state { self.log = s }
        │     └─ ViewModelProvider.shared.routeDetailViewModel()(FR-03 label)
        ▼
   switch app.readiness {
     LOADING            → DenikLoadingView()
     READY + run+route  → DenikOnRouteView(log, routeLabel, offline)
     else               → DenikBeforeView(onDownload)
   }
```

SKIE vystavuje `StateFlow<T>` jako Swift `AsyncSequence` - pozorujeme přes `for await` v `Task`. Vzor: jeden `DenikModel: ObservableObject` s `@Published` stavy; views jsou čistě deklarativní a jde je previewovat s fake daty.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Pozorování stavu | `ObservableObject` + `for await` nad SKIE `AsyncSequence` | Zavedený vzor (starý `ContentView`); čisté SwiftUI |
| Views vs model split | `DenikModel` (VM) → stateless `DenikXxxView(data)` | Previews + testovatelnost bez Koinu |
| Layout | Nativní `ScrollView`+`VStack` (příp. `List`+`Section`) | iOS-nativní pocit; ne klon Compose Column |
| Label trasy | `RouteDetailViewModel` (FR-03) | LogUiState nenese jméno/km trasy |
| Barvy/typo/rozměry | Existující `Ta33Color/Ta33Font/Ta33*` | Sdílený brand kontrakt; žádný hardcode |
| Texty | Prozatím Swift string konstanty (viz §12) | Compose `Res.string` je z commonMain přes SKIE async - pro statické texty overkill; sjednocení lokalizace je follow-up |

---

## 4. IMPLEMENTATION STEPS

> Balíček/adresář: `iosApp/iosApp/UI/`. Přidávané `.swift` soubory je nutné zařadit do `iosApp.xcodeproj` (target `iosApp`).

### Step 1: Time-format helper + drobné utility
**Goal**: Epoch millis → „HH:mm"; složení labelů.
**Files**: `iosApp/iosApp/UI/Format.swift` (create)
```swift
import Foundation
enum Ta33Format {
    static func clock(_ millis: Int64) -> String {
        let d = Date(timeIntervalSince1970: Double(millis) / 1000)
        let f = DateFormatter(); f.dateFormat = "HH:mm"; return f.string(from: d)
    }
}
```
**Done when**: kompiluje.

### Step 2: Základní komponenty
**Goal**: Nativní SwiftUI atomy nad tokeny.
**Files**: `iosApp/iosApp/UI/Components/{Cards,Buttons,Labels,Banners,Chips}.swift` (create)
- `IdentityCard(date:place:sub:)` - `VStack` na `Ta33Color.slate800`, `RoundedRectangle(cornerRadius: Ta33Radius.lg)`, text `fgOnDark`/`fgOnDarkMuted`, styly `Ta33Font.overline/display3/small`, `.shadow` jemný.
- `PaperCard { content }` - `Ta33Color.paper`, radius `lg`, padding `Ta33Spacing.x4`, jemný `.shadow`.
- `PrimaryButton(_:action:)` - `Ta33Color.orange`, `Capsule()`, `Ta33Font.button` UPPER, `fgOnOrange`, plná šířka, min výška 56, teplý `.shadow(color: orange…)`.
- `OutlineButton(_:action:)` - `Capsule().stroke(Ta33Color.orange, lineWidth: 2)`, orange text.
- `OverlineLabel(_:color:)` - UPPER, `.kerning`, `Ta33Font.overline`.
- `OfflineBanner()` - `Ta33Color.warningTint`, ikona `Image(systemName: "bolt.fill")` `warning`, text bold.
- `StatChip(value:label:)` - `Ta33Color.slate100`, radius `md`, value `display2/3`, label overline.

Ikony: **SF Symbols** (`checkmark`, `star.fill`, `arrow.down.circle`, `bolt.fill`) - iOS-nativní, ne Lucide.

**Done when**: každá komponenta má `#Preview`.

### Step 3: `KPRow`
**Goal**: Řádek kontroly dle `ControlPointState`.
**Files**: `iosApp/iosApp/UI/Components/KPRow.swift` (create)
```swift
struct KPRow: View {
    let ordinal: Int32; let title: String; let subtitle: String
    let state: ControlPointState; let isFinish: Bool
    // 56pt swatch (barva dle stavu) + VStack(title bodyStrong, subtitle small)
}
```
Stavy → tokeny: DONE `kpDoneBg`+`checkmark`; ACTIVE `kpActiveBg`+číslo+`.shadow` orange; LOCKED `kpLockedBg`+číslo/hvězda, řádek `.opacity(0.55)`; FINISH `kpFinishBg`+`star.fill`.
`ControlPointState` je Kotlin enum vystavený přes SKIE.
**Done when**: `#Preview` ukazuje všechny stavy.

### Step 4: Stateless views (Loading/Before/OnRoute)
**Goal**: Obsahové views s plain daty.
**Files**: `iosApp/iosApp/UI/Denik/DenikViews.swift` (create)
- `DenikLoadingView` - `ProgressView()` centrovaně na `Ta33Color.cream`.
- `DenikBeforeView(onDownload:)` - `ScrollView { VStack { IdentityCard; PaperCard { ikona v kruhu, h1, body, PrimaryButton } } }`.
- `DenikOnRouteView(log:routeLabel:offline:)` - `ScrollView`/`List`:
  - `if offline { OfflineBanner() }`
  - progress `PaperCard`: `HStack(routeLabel, Spacer, "\(log.collectedCount) / \(log.totalCount)" display2)` + progress bar (`Capsule` track `slate100`, fill `orange`, šířka dle frakce; `total==0` → 0).
  - sekce „Příští checkpoint": `log.entries.filter { $0.state != .done }` → `KPRow`.
  - sekce „Hotovo": `log.entries.filter { $0.state == .done }` → `KPRow` (prázdnou sekci neukazovat).
  - `rowArgs(entry)`: title `"KP-\(ordinal) · \(control.name)"` / finish `"Cíl · \(name)"`; subtitle DONE `"Splněno · \(Ta33Format.clock(collectedAtMillis))"`, ACTIVE „Další", LOCKED „Zamčeno".
**Done when**: `#Preview` pro Loading, Before, OnRoute (fake `LogUiState`), OnRoute-empty.

### Step 5: `DenikModel` (pozorování VM přes SKIE)
**Goal**: Napojení na sdílené ViewModely.
**Files**: `iosApp/iosApp/UI/Denik/DenikModel.swift` (create)
```swift
@MainActor
final class DenikModel: ObservableObject {
    private let appVM = ViewModelProvider.shared.appViewModel()
    private let logVM = ViewModelProvider.shared.runLogViewModel()
    private let routeVM = ViewModelProvider.shared.routeDetailViewModel()
    @Published var app: AppUiState = AppUiState()
    @Published var log: LogUiState = LogUiState()
    @Published var routeLabel: String = ""
    private var boundRun: String? = nil

    func observe() async { for await s in appVM.state { self.app = s; bindIfNeeded(s) } }
    func observeLog() async { for await s in logVM.state { self.log = s } }
    func observeRoute() async { for await s in routeVM.state { self.routeLabel = label(s) } }
    private func bindIfNeeded(_ s: AppUiState) {
        guard let run = s.activeRunId, let route = s.activeRouteId, run != boundRun else { return }
        boundRun = run; logVM.bind(runId: run, routeId: route); routeVM.bind(routeId: route)
    }
    func onDownload() { /* TODO: Preparation navigace (app-shell) */ }
}
```
Ověřit přesné SKIE názvy (`ViewModelProvider.shared.*`, `bind(runId:routeId:)`, default init `AppUiState()`/`LogUiState()` - Kotlin data class default args vystavené přes SKIE) na prvním buildu; adaptovat, když se liší.
**Done when**: kompiluje, framework se slinkuje.

### Step 6: `DenikView` + napojení do `ContentView`
**Goal**: Gate stavů + reálné zobrazení.
**Files**: `iosApp/iosApp/UI/Denik/DenikView.swift` (create), `iosApp/iosApp/ContentView.swift` (modify)
```swift
struct DenikView: View {
    @StateObject private var model = DenikModel()
    var body: some View {
        ZStack {
            Ta33Color.cream.ignoresSafeArea()
            switch model.app.readiness {
            case .loading: DenikLoadingView()
            case .ready where model.app.activeRunId != nil && model.app.activeRouteId != nil:
                DenikOnRouteView(log: model.log, routeLabel: model.routeLabel, offline: true)
            default: DenikBeforeView(onDownload: model.onDownload)
            }
        }
        .task { await model.observe() }
        .task { await model.observeLog() }
        .task { await model.observeRoute() }
    }
}
```
`ContentView` → `DenikView()`. `AppReadiness` je Kotlin enum přes SKIE (`.loading/.ready/…` - ověřit casing).
**Done when**: `ContentView` renderuje `DenikView`.

### Step 7: Zařadit soubory do Xcode projektu + ověření
**Goal**: Nové `.swift` v targetu `iosApp`; build.
**Files**: `iosApp/iosApp.xcodeproj/project.pbxproj`
Přidat nové soubory do targetu (Xcode je přidá do `project.pbxproj`; při ruční editaci dodržet formát). Ověřit `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` a (je-li Xcode) `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build`.
**Done when**: framework link zelený; xcodebuild build zelený (nebo odloženo na stroj uživatele s poznámkou).

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected | Handle |
|---|---|---|
| `total==0` | „0 / 0", prázdný bar, žádný pád | frakce = total==0 ? 0 : collected/total |
| `collectedCount==0` | Sekce „Hotovo" skrytá; první ACTIVE | prázdnou sekci nevykreslovat |
| `READY` bez `activeRunId` | Nespadnout; `DenikBefore`/placeholder | spadne do `default` větve |
| `log.loading==true` | Krátký spinner/prázdno | volitelně respektovat |
| DONE bez `collectedAtMillis` | Sub bez času | Int64? → čas jen když non-nil |
| RouteDetail nenačten | Label placeholder | default „Trasa" |
| SKIE nullability (`String?` → `String?`) | Bezpečné unwrapy | `guard let`/`if let` |

---

## 6. SECURITY CONSIDERATIONS
- Žádný uživatelský vstup kromě tlačítek. Časy/poloha zůstávají on-device (jen zobrazení). Nelogovat je.

## 7. ASSUMPTIONS
1. **SKIE vystavuje `StateFlow` jako `AsyncSequence`** a `ViewModelProvider.shared.*` accessory (potvrzeno v `Koin.kt`; starý `ContentView` vzor `for await state in vm.state` fungoval).
2. **Kotlin data class defaulty** (`AppUiState()`, `LogUiState()`) jsou v Swiftu dostupné jako no-arg init (SKIE). Když ne, použít explicitní init nebo optional + placeholder.
3. **`bind(...)` je nesuspend** - volatelné přímo ze Swiftu (je to `fun` spouštějící collector).
4. **Simulátor nenaboot v sandboxu** → runtime ověření odložené na stroj uživatele; v sandboxu jen framework link (+ xcodebuild build je-li Xcode).
5. **Statické texty** prozatím Swift konstanty (CZ). Sjednocení s Compose `Res.string` přes SKIE je follow-up.
6. **Offline indikátor** dočasně `true`; `ConnectivityMonitor` napojení je follow-up.
7. **Nové soubory je nutné zařadit do `project.pbxproj`** - ideálně přes Xcode; při headless běhu ověřit, že target `iosApp` je vidí.

## 8. QUICK REFERENCE

### Files to Create
- `iosApp/iosApp/UI/Format.swift`
- `iosApp/iosApp/UI/Components/{Cards,Buttons,Labels,Banners,Chips,KPRow}.swift`
- `iosApp/iosApp/UI/Denik/{DenikViews,DenikModel,DenikView}.swift`

### Files to Modify
- `iosApp/iosApp/ContentView.swift` → `DenikView()`
- `iosApp/iosApp.xcodeproj/project.pbxproj` → zařadit nové soubory

### Commands
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
# je-li Xcode:
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

---

## 9. DESIGN REFERENCE

### Visual Spec
- Claude Design „TA33 Design System" + `.claude/design/{design-system.md,denik-screen-spec.md}`.
- Zrcadlí Android `ui-01`, ale **iOS-nativní** provedení (viz KLÍČOVÝ PRINCIP).

### Style Mapping (design → iOS token)
| Design | Swift token | Value |
|---|---|---|
| pozadí cream | `Ta33Color.cream` | #F7F2EA |
| paper karta | `Ta33Color.paper` | #FFFFFF |
| identity karta | `Ta33Color.slate800` + `fgOnDark` | #1C2A36 |
| orange CTA | `Ta33Color.orange` | #F76A0E |
| progress track / fill | `Ta33Color.slate100` / `.orange` | #E6EAEC / #F76A0E |
| KP done/active/locked/finish | `Ta33Color.kp*Bg/Fg` | dle Color+TA33 |
| offline banner | `Ta33Color.warningTint` + `.warning` | #FBE9C2 / #E8A92A |
| „2 / 5" | `Ta33Font.display2` | 32/black |
| overline | `Ta33Font.overline` + `.kerning(1.3)` | 13/bold UPPER |
| button | `Ta33Font.button` | 16/heavy UPPER |
| radius karta / pill | `Ta33Radius.lg` / `.pill` | 20 / 999 |
| spacing | `Ta33Spacing.x4/x5` | 16 / 20 |

### Native adaptace (odlišné od Android)
- Ikony **SF Symbols** (ne Lucide): `checkmark`, `star.fill`, `arrow.down.circle`, `bolt.fill`.
- Seznam přes nativní `ScrollView`+`VStack` (příp. `List`+`Section`) - iOS scroll fyzika, safe areas.
- Spinner `ProgressView` (nativní), ne Compose `CircularProgressIndicator`.
- Bottom nav / scan (app-shell) budou nativní `TabView` / liquid-glass - mimo tento plán.

---

## 10. CORRECTIONS FROM CURRENT STATE

| What | Before | After |
|---|---|---|
| `ContentView` | placeholder „TA33" text | `DenikView()` (3 stavy) |
| iOS UI vrstva | jen theme | `UI/Components/*` + `UI/Denik/*` |

---

## 11. CHANGELOG

| Date | Change |
|---|---|
| 2026-07-09 | Initial plan created |

---

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES

### 12.1 Alternative Approaches Considered
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. ObservableObject model + stateless views, SF Symbols, nativní scroll** | iOS-nativní, previewovatelné, znovupoužitelné | Trochu boilerplate na pozorování | ✅ |
| B. Přímé pozorování `StateFlow` ve View bez modelu | Méně souborů | Špatně previewovatelné, mísí VM a View | - |
| C. Klon Android layoutu 1:1 (stejné „pill/FAB") | Vizuální shoda s mockupem | Porušuje princip nativního vzhledu; ne-iOS pocit | - |

**Why the selected approach won**: Nativní iOS pocit + čisté oddělení pro previews/testy; drží sdílený brand přes tokeny bez klonování Compose.

### 12.2 Open Questions
- [ ] **SKIE názvy/nullability** (`ViewModelProvider.shared.*`, enum casing `AppReadiness`, `Int64?` pro `collectedAtMillis`) - Proposed direction: ověřit na prvním buildu a adaptovat call-sites.
- [ ] **Zařazení souborů do `project.pbxproj`** headless - Proposed direction: ideálně přidat v Xcode; když se dělá bez Xcode, ručně upravit `project.pbxproj` a ověřit framework link + build.
- [ ] **Lokalizace textů** (Swift konstanty vs sdílené `Res.string` přes SKIE) - Proposed direction: prozatím Swift konstanty; sjednotit v samostatném lokalizačním kroku.
- [ ] **Offline indikátor** - Proposed direction: dočasně `true`, napojit `ConnectivityMonitor`.

### 12.3 Suggestions & Follow-ups
- **App-shell iOS**: nativní `TabView` (Deník/Mapa/Profil) + liquid-glass + scan tlačítko - `DenikView` do něj zapadne.
- **Fonty**: Big Shoulders/Inter do bundlu + `UIAppFonts`, přepnout `Ta33Font`.
- **Sjednocená lokalizace** přes SKIE (`Res.string`).
- **XCUITest / SwiftUI Preview snapshoty** na 3 stavy.
