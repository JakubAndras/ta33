# UI-06 — Přehled / Profil (iOS SwiftUI)

> **Summary**: iOS SwiftUI obrazovka tabu „Přehled" nad FR-10 ViewModely (přes SKIE) — sekce „Tvoje akce" + „Nastavení" v nativním `List`/`Form` (Toggle, DisclosureGroup). Zrcadlo Androidu (ui-05), iOS-nativní.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Tab „Přehled" v iOS shellu (ui-04) je `StubView`. FR-10 logika je hotová. Potřebujeme nativní SwiftUI obrazovku: souhrn akce/běhu + nastavení.

### 1.2 Solution Overview
`PrehledView` přes `PrehledModel` (ObservableObject nad `OverviewViewModel` + `SettingsViewModel`, SKIE `for await`) vykreslí nativní `List`/`Form` se dvěma sekcemi: „Tvoje akce" (aktivní trasa, naskenováno, stav dat akce) a „Nastavení" (nativní `Toggle` Notifikace → `setNotificationsEnabled`, kontakt na pořadatele, `DisclosureGroup` FAQ). Nahradí `StubView(title: "Přehled")` v `RootView`.

### 1.3 Scope: What This IS
- iOS `PrehledView` (tab Přehled) nad `OverviewViewModel` + `SettingsViewModel` (SKIE).
- Nativní `List`/`Form` sekce „Tvoje akce" + „Nastavení"; `Toggle`, `DisclosureGroup` (FAQ).
- `PrehledModel` (ObservableObject) pozorující oba `StateFlow`.
- Napojení do `RootView` (tab Přehled).

### 1.4 Scope: What This IS NOT
- **Android** (ui-05, hotovo).
- **Etapa 2** prvky: avatar/jméno/e-mail, startovní číslo, „Zaplaceno", odbavovací QR, „Hlasové pokyny" — v Etapě 1 nejsou.
- Intent akce kontaktu (mail/tel) — zobrazit; akce follow-up.

---

## 2. SUCCESS CRITERIA
| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` zelený | příkaz |
| 2 | `xcodebuild ... -scheme iosApp build` → BUILD SUCCEEDED | příkaz / na Mac |
| 3 | Tab Přehled ukáže `PrehledView` místo stubu | běh |
| 4 | „Tvoje akce": aktivní trasa „{name} · {km} km", naskenováno „{c} z {t} kontrol" (jen běh), stav dat akce | Preview |
| 5 | Bez běhu: bez řádky naskenováno; bez trasy: hint „Zatím žádná aktivní akce" | Preview |
| 6 | Nativní `Toggle` Notifikace odráží stav a volá `setNotificationsEnabled` | Preview |
| 7 | Kontakt na pořadatele zobrazen; FAQ přes `DisclosureGroup` (nativní rozbalení) | Preview |
| 8 | `loading` (dokud nejsou hotové OBĚ VM, tj. `overview.loading \|\| settings.loading`) → `ProgressView` | Preview |
| 9 | Žádný hardcoded hex/CGFloat — vše přes `Ta33Color/Font/Spacing/Radius` | code review |
| 10 | Runtime na simulátoru — DEFERRED na Mac | manuální |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture
```
RootView tab .prehled → PrehledView
   │  @StateObject PrehledModel
   │     OverviewViewModel.state → @Published overview   (SKIE for await)
   │     SettingsViewModel.state → @Published settings
   ▼
 List {
   Section("Tvoje akce")  { aktivní trasa | naskenováno (jen běh) | data akce }
   Section("Nastavení")   { Toggle Notifikace; kontakt; DisclosureGroup(faq) }
 }
```
Nativní `List` (`.insetGrouped` styl) = iOS-nativní settings vzhled. `setNotificationsEnabled` přes binding proxy (Toggle isOn → model.setNotifications).

### 3.2 Key Decisions
| Decision | Choice | Reasoning |
|---|---|---|
| Layout | Nativní `List`/`Form` (insetGrouped) | iOS-nativní settings pocit; ne klon Compose karet |
| Toggle | Nativní SwiftUI `Toggle` | Nativní chování/haptika |
| FAQ | `DisclosureGroup` | Nativní rozbalení |
| Etapa 2 prvky | Vynechat | V logice Etapy 1 nejsou |
| Model | `PrehledModel` ObservableObject (2 VM) | Vzor ui-02/ui-04 |

---

## 4. IMPLEMENTATION STEPS

> Adresář: `iosApp/iosApp/UI/`. Nové soubory se do targetu zařadí samy (PBXFileSystemSynchronizedRootGroup).

### Step 1: `PrehledModel`
**Files**: `iosApp/iosApp/UI/Prehled/PrehledModel.swift` (create)
```swift
@MainActor
final class PrehledModel: ObservableObject {
    private let overviewVM = ViewModelProvider.shared.overviewViewModel()
    private let settingsVM = ViewModelProvider.shared.settingsViewModel()
    @Published var overview: OverviewUiState = /* explicit init (SKIE nemá no-arg) */ ...
    @Published var settings: SettingsUiState = /* explicit init */ ...
    func observeOverview() async { for await s in overviewVM.state { self.overview = s } }
    func observeSettings() async { for await s in settingsVM.state { self.settings = s } }
    func setNotifications(_ on: Bool) { settingsVM.setNotificationsEnabled(enabled: on) }
}
```
Ověřit SKIE accessory `overviewViewModel()`/`settingsViewModel()` (v `Koin.kt` existují) a explicitní init state (dle ui-02/04).
**Done when**: framework link zelený.

### Step 2: Pomocné formátování
**Files**: `iosApp/iosApp/UI/Prehled/PrehledFormat.swift` (create)
- `kmLabel(Double) -> String` — česká čárka (`33,2`).
- `packageStatusLabel(PreparationStatus) -> String` — Nestaženo/Stahuje se/Staženo/Chyba.
- `packageStatusColor(PreparationStatus) -> Color` — success/warning/error.
`PreparationStatus` je Kotlin enum přes SKIE (ověřit casing `.notStarted/.preparing/.ready/.error`).
**Done when**: kompiluje.

### Step 3: `PrehledView` + napojení do `RootView`
**Files**: `iosApp/iosApp/UI/Prehled/PrehledView.swift` (create), `UI/Shell/RootView.swift` (modify)
```swift
struct PrehledView: View {
    @StateObject private var model = PrehledModel()
    var body: some View {
        Group {
            if model.overview.loading || model.settings.loading { ProgressView() }  // spinner dokud nejsou hotové OBĚ VM (brání probliknutí stale)
            else {
                List {
                    Section("Tvoje akce") {
                        if let r = model.overview.activeRoute {
                            LabeledContent("Aktivní trasa", value: "\(r.name) · \(kmLabel(r.distanceKm)) km")
                        } else { Text("Zatím žádná aktivní akce").foregroundStyle(Ta33Color.fgMuted) }
                        if let p = model.overview.progress {
                            LabeledContent("Naskenováno", value: "\(p.collectedCount) z \(p.totalCount) kontrol")
                        }
                        LabeledContent("Data akce") {
                            Text(packageStatusLabel(model.overview.syncStatus))
                                .foregroundStyle(packageStatusColor(model.overview.syncStatus))
                        }
                    }
                    Section("Nastavení") {
                        Toggle("Notifikace", isOn: Binding(
                            get: { model.settings.notificationsEnabled },
                            set: { model.setNotifications($0) }))
                        .tint(Ta33Color.orange)
                        if let c = model.settings.organizerContact {
                            LabeledContent("Kontaktovat pořadatele", value: c.name)
                        }
                        ForEach(model.settings.faq, id: \.id) { item in
                            DisclosureGroup(item.question) {
                                Text(item.answer).foregroundStyle(Ta33Color.fgMuted)
                            }
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .task { await model.observeOverview() }
        .task { await model.observeSettings() }
    }
}
```
V `RootView`: tab `.prehled` → `PrehledView()` (místo `StubView(title: "Přehled")`).
Ověřit typy přes SKIE (`FaqItem.id`, `RouteSummary.distanceKm` jako `Double`/`KotlinDouble`, `OrganizerContact.name`).
**Done when**: `ContentView`/`RootView` renderuje `PrehledView` v tabu.

### Step 4: Ověření
`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` + `xcodebuild ... build`. Runtime na Mac.

---

## 5. EDGE CASES & ERRORS
| Scenario | Expected | Handle |
|---|---|---|
| `activeRoute == nil` | Hint „Zatím žádná aktivní akce" | `if let` |
| `progress == nil` | Bez řádky Naskenováno | `if let` |
| `faq` prázdné | `ForEach` nic nevykreslí | přirozeně |
| `organizerContact == nil` | Řádka kontaktu skrytá | `if let` |
| některá VM loading | `ProgressView` (dokud nejsou hotové obě) | `overview.loading \|\| settings.loading` |
| `distanceKm` bridging | `Double` (příp. `KotlinDouble.doubleValue`) | ověřit na buildu |
| SKIE enum casing | bezpečný switch | ověřit `.ready` atd. |

## 6. SECURITY CONSIDERATIONS
- Kontakt veřejný. Žádná citlivá data.

## 7. ASSUMPTIONS
1. **SKIE**: `ViewModelProvider.shared.overviewViewModel()`/`settingsViewModel()`, `setNotificationsEnabled(enabled:)`, `OverviewUiState`/`SettingsUiState` bez no-arg init (explicitní init dle ui-02/04). Ověřit na buildu.
2. **VM startují v `init`** (bez bind) — jen pozorovat.
3. **Etapa 2 prvky vynechány** (avatar/číslo/QR/platba/hlasové pokyny).
4. **„Data akce" = `syncStatus`** (offline balíček), ne upload výsledků.
5. **Simulátor nenaboot** → runtime na Mac; v sandboxu framework link + xcodebuild.

## 8. QUICK REFERENCE
### Files to Create
- `iosApp/iosApp/UI/Prehled/{PrehledModel,PrehledFormat,PrehledView}.swift`
### Files to Modify
- `iosApp/iosApp/UI/Shell/RootView.swift` — tab .prehled → `PrehledView`
### Commands
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## 9. DESIGN REFERENCE
- `ui_kits/ta33-app/Screens.jsx::ProfilScreen`; `.claude/design/design-system.md`.
### Native adaptace (odlišné od Android ui-05)
- Nativní `List(.insetGrouped)` + `Section` místo `PaperCard` karet.
- Nativní `Toggle`, `DisclosureGroup`, `LabeledContent`.
- Tint `Ta33Color.orange`.
### Style Mapping
| Design | Swift | Value |
|---|---|---|
| sekce | `Section` header | overline-like (systémový) |
| value muted | `Ta33Color.fgMuted` | #5A6C7A |
| stav READY | `Ta33Color.success` | #1FA85A |
| stav nestaženo/chyba | `Ta33Color.warning`/`.error` | #E8A92A / #D63A2F |
| toggle tint | `Ta33Color.orange` | #F76A0E |

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before | After |
|---|---|---|
| Tab Přehled (iOS) | `StubView(title: "Přehled")` | `PrehledView()` |

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-09 | Initial plan created |

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES
### 12.1 Alternatives
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Nativní List/Form + Toggle/DisclosureGroup** | iOS-nativní settings pocit, minimum kódu | Vizuálně jiné než Compose karty (žádoucí) | ✅ |
| B. Klon Compose PaperCard karet | Shoda s Androidem | Porušuje princip nativního vzhledu | — |
| C. Custom scroll + vlastní řádky | Kontrola vzhledu | Ztráta nativního chování Toggle/Disclosure | — |
### 12.2 Open Questions
- [ ] **SKIE typy/casing** (`KotlinDouble`, enum casing, `setNotificationsEnabled(enabled:)`) — Proposed: ověřit na buildu, adaptovat.
- [ ] **Kontakt akce** — Proposed: zobrazit; `Link`/`mailto:` follow-up.
### 12.3 Suggestions & Follow-ups
- Etapa 2: identity/startovní číslo/odbavovací QR/platba, hlasové pokyny.
- `Link`/`mailto:`/`tel:` akce pro kontakt.
- Sjednocená lokalizace (Res.string přes SKIE) místo Swift konstant.
