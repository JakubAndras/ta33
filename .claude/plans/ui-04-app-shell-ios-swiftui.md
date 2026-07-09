# UI-04 — App Shell (iOS SwiftUI)

> **Summary**: Nativní iOS `TabView` shell (Deník / Mapa / Přehled) — liquid-glass tab bar, splash gate, hostuje hotový `DenikView`; Mapa/Přehled zatím stub. Zrcadlo Androidu (ui-03), ale iOS-nativní.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
`ContentView` zobrazuje přímo `DenikView` — chybí navigační skořápka. iOS app potřebuje nativní tabovou navigaci (Deník / Mapa / Přehled), scan vstup a splash.

### 1.2 Solution Overview
`RootView` (SwiftUI) přes `AppModel` (ObservableObject nad `AppViewModel` přes SKIE) sleduje `readiness`. LOADING → splash; READY → nativní **`TabView`** se třemi taby (Deník = `DenikView` hotový, Mapa/Přehled stub). Scan vstup jako prominentní tlačítko (toolbar / centrální), zobrazený jen při aktivním běhu. Tab bar je nativní iOS (na iOS 26 „liquid glass" automaticky) — **ne klon Android pillu**.

### 1.3 Scope: What This IS
- iOS `RootView` s nativním `TabView` (3 taby: Deník/Mapa/Přehled).
- Splash na `readiness==LOADING`.
- Scan vstupní bod (prominentní, jen při aktivním běhu) → stub callback.
- Hostování `DenikView`; stub Mapa/Přehled.
- `ContentView` → `RootView`.

### 1.4 Scope: What This IS NOT
- **Android** shell (ui-03, hotovo).
- Reálné **Mapa/Přehled** obrazovky, **Scan/Splnění/Preparation** — stub / callback.
- Detail routes s `NavigationStack` push historií — zatím taby; detail flow později.

---

## 2. SUCCESS CRITERIA
| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` zelený | příkaz |
| 2 | `xcodebuild ... -scheme iosApp build` → BUILD SUCCEEDED (je-li Xcode) | příkaz / odloženo na Mac |
| 3 | `readiness==LOADING` → splash (`ProgressView`) | Preview / běh |
| 4 | `READY` → nativní `TabView`, výchozí tab Deník = `DenikView` | běh |
| 5 | Taby Deník/Mapa/Přehled přepínají; nativní tab bar (liquid glass na iOS 26) | běh |
| 6 | Mapa/Přehled stub bez pádu | běh |
| 7 | Scan vstup jen při aktivním běhu; klik → callback | Preview obou variant |
| 8 | Žádný hardcoded hex/CGFloat — vše přes `Ta33Color/Font/Spacing/Radius` | code review |
| 9 | iOS-nativní: `TabView` + SF Symbols taby, ne klon Android pillu | code review vůči principu |
| 10 | Runtime na simulátoru — DEFERRED na Mac | manuální |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture
```
iOSApp → ContentView → RootView
   │  @StateObject AppModel (ObservableObject)
   │     ViewModelProvider.shared.appViewModel(); for await s in state { app = s }
   ▼
 switch app.readiness {
   .loading             → SplashView()
   .notReady/.preparing → PreparationPlaceholder()   (stub)
   .ready               → TabView(selection:$tab) {
                             DenikView().tabItem { Label("Deník", systemImage:"book") }.tag(.denik)
                             StubView("Mapa").tabItem { Label("Mapa", systemImage:"map") }.tag(.mapa)
                             StubView("Přehled").tabItem { Label("Přehled", systemImage:"person") }.tag(.prehled)
                           }
                           + scan vstup (toolbar/overlay) když app.activeRunId != nil
 }
```
`TopLevelDestination` (Kotlin enum přes SKIE: `.denik/.mapa/.prehled`) řídí `selection`. Nativní `TabView` = automatický iOS tab bar (liquid glass na iOS 26).

### 3.2 Key Decisions
| Decision | Choice | Reasoning |
|---|---|---|
| Tab bar | Nativní `TabView` | iOS-nativní pocit, liquid glass zdarma; ne klon Android pillu |
| Stav VM | `AppModel: ObservableObject` + SKIE `for await` | Zavedený vzor (ui-02) |
| Scan vstup | Prominentní tlačítko/overlay jen při běhu, hoisted | Scan UI = FR-09; shell drží vstupní bod |
| Tab výběr | Kotlin `TopLevelDestination` přes SKIE | Sdílený nav contract (FR-01) |
| Preparation/RunActive | Placeholder | Skutečné obrazovky později |

---

## 4. IMPLEMENTATION STEPS

> Adresář: `iosApp/iosApp/UI/`. Nové soubory se do targetu zařadí samy (`PBXFileSystemSynchronizedRootGroup`).

### Step 1: Splash + stub + preparation placeholder
**Files**: `iosApp/iosApp/UI/Shell/Placeholders.swift` (create)
- `SplashView` — „TA33" (`Ta33Font.display1`) + `ProgressView` na `Ta33Color.cream`.
- `StubView(title:)` — cream, centrovaný `OverlineLabel(title)` + „Připravujeme".
- `PreparationPlaceholder` — stub „Příprava dat akce".
**Done when**: `#Preview` každého.

### Step 2: Scan vstup
**Files**: `iosApp/iosApp/UI/Shell/ScanButton.swift` (create)
- `ScanButton(action:)` — kruhové orange tlačítko (SF Symbol `qrcode.viewfinder`/`viewfinder`), `Ta33Color.orange`, glow `.shadow`, `fgOnOrange`. iOS-idiomatické (ne Material FAB).
**Done when**: `#Preview`.

### Step 3: `RootModel` (AppViewModel přes SKIE)
**Files**: `iosApp/iosApp/UI/Shell/RootModel.swift` (create)
```swift
@MainActor
final class RootModel: ObservableObject {
    private let appVM = ViewModelProvider.shared.appViewModel()
    @Published var app: AppUiState = /* explicit init — SKIE nemá no-arg */ ...
    func observe() async { for await s in appVM.state { self.app = s } }
    func onScan() { /* TODO FR-09 scan */ }
}
```
Ověřit SKIE init `AppUiState` (ui-02 zjistilo, že no-arg init chybí — sestavit explicitně, příp. optional + placeholder).
**Done when**: kompiluje, framework link zelený.

### Step 4: `RootView` (TabView gate)
**Files**: `iosApp/iosApp/UI/Shell/RootView.swift` (create), `ContentView.swift` (modify)
```swift
struct RootView: View {
    @StateObject private var model = RootModel()
    @State private var tab: TopLevelDestination = .denik
    var body: some View {
        Group {
            switch model.app.readiness {
            case .loading: SplashView()
            case .notReady, .preparing: PreparationPlaceholder()
            case .ready:
                TabView(selection: $tab) {
                    DenikView().tabItem { Label("Deník", systemImage: "book") }.tag(TopLevelDestination.denik)
                    StubView(title: "Mapa").tabItem { Label("Mapa", systemImage: "map") }.tag(TopLevelDestination.mapa)
                    StubView(title: "Přehled").tabItem { Label("Přehled", systemImage: "person") }.tag(TopLevelDestination.prehled)
                }
                .tint(Ta33Color.orange)
                .overlay(alignment: .bottomTrailing) {
                    if model.app.activeRunId != nil { ScanButton(action: model.onScan).padding() }
                }
            default: SplashView()
            }
        }
        .task { await model.observe() }
    }
}
```
`ContentView` → `RootView()`. Ověřit enum casing `AppReadiness` / `TopLevelDestination` (SKIE) na prvním buildu.
**Done when**: `ContentView` renderuje `RootView`.

### Step 5: Ověření
`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`; je-li Xcode `xcodebuild ... build`. Runtime na simulátoru odložit na Mac.

---

## 5. EDGE CASES & ERRORS
| Scenario | Expected | Handle |
|---|---|---|
| `activeRunId==nil` | Žádné scan tlačítko | podmínka na overlay |
| SKIE enum casing | Bezpečný `switch` | ověřit `.loading/.ready/…`, `default` větev |
| `AppUiState` init | Explicitní počáteční stav | SKIE nemá no-arg init |
| iOS < 26 | TabView funguje (bez liquid glass) | nativní fallback automaticky |
| Scan overlay překrývá obsah | padding + bottomTrailing | ScanButton `.padding()` |

## 6. SECURITY CONSIDERATIONS
- N/A (navigace/zobrazení).

## 7. ASSUMPTIONS
1. **SKIE**: `ViewModelProvider.shared.appViewModel()`, `AppReadiness`/`TopLevelDestination` jako Swift enums, `AppUiState` bez no-arg init (dle ui-02) — sestavit explicitně; ověřit na buildu.
2. **Taby bez `NavigationStack` push** — detail routes později.
3. **Scan/Preparation stub** — skutečné obrazovky jiné plány.
4. **Simulátor nenaboot v sandboxu** → runtime na Mac; v sandboxu framework link (+ xcodebuild je-li Xcode).

## 8. QUICK REFERENCE
### Files to Create
- `iosApp/iosApp/UI/Shell/{Placeholders,ScanButton,RootModel,RootView}.swift`
### Files to Modify
- `iosApp/iosApp/ContentView.swift` → `RootView()`
### Commands
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## 9. DESIGN REFERENCE
- `.claude/design/design-system.md` (KLÍČOVÝ PRINCIP: iOS = nativní TabView/liquid-glass, ne Android pill).
### Style Mapping
| Design | Swift | Value |
|---|---|---|
| tab tint (aktivní) | `Ta33Color.orange` | #F76A0E |
| pozadí splash/stub | `Ta33Color.cream` | #F7F2EA |
| scan tlačítko | `Ta33Color.orange` + glow | #F76A0E |
### Native adaptace (odlišné od Android ui-03)
- Nativní `TabView` (systémový tab bar / liquid glass) místo plovoucího pillu.
- SF Symbols (`book`/`map`/`person`/`viewfinder`) místo Lucide.
- Scan jako iOS overlay/tlačítko, ne Material FAB.

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before | After |
|---|---|---|
| `ContentView` | přímo `DenikView` | `RootView()` (splash + TabView) |

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-09 | Initial plan created |

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES
### 12.1 Alternatives
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Nativní `TabView` + overlay scan** | iOS-nativní, liquid glass zdarma | Scan overlay je kompromis do FR-09 | ✅ |
| B. Klon Android plovoucího pillu | Vizuální shoda napříč platformami | Porušuje princip nativního vzhledu | — |
| C. Custom tab bar (ne TabView) | Plná kontrola vzhledu | Ztráta nativního chování/liquid glass, víc práce | — |
### 12.2 Open Questions
- [ ] **SKIE enum casing / `AppUiState` init** — Proposed: ověřit na prvním buildu, adaptovat.
- [ ] **Scan umístění** (overlay vs. centrální tab item) — Proposed: overlay bottom-trailing při běhu; finalizovat s FR-09.
### 12.3 Suggestions & Follow-ups
- Obrazovky Mapa (MapLibre) a Přehled/Profil nahradí stuby.
- Scan/Preparation/Splnění obrazovky + `NavigationStack` pro detail routes.
- Fonty Big Shoulders/Inter do bundlu (`UIAppFonts`).
