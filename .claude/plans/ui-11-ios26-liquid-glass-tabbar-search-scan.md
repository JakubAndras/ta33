# iOS 26 Liquid Glass Tab Bar — Oddělené Scan Tlačítko přes `Tab(role: .search)`

> **Summary**: Přepsat iOS shell tak, aby na iOS 26 vznikala oddělená scan „kapsle" nativním mechanismem `Tab(role: .search)` (namísto ručně pozicovaného overlay FABu), včetně `.tabBarMinimizeBehavior(.onScrollDown)`, s čistým fallbackem na overlay FAB pro iOS 18.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
iOS 26 zavedl plovoucí Liquid Glass tab bar, kde poslední prvek vpravo je vizuálně **oddělená kapsle** (jako search tlačítko v Health appce). V naší appce je scan vstup dnes udělaný jako ruční `.overlay(alignment: .bottomTrailing)` s natvrdo zadaným paddingem, který se jen *snaží trefit* vedle systémové lišty. To je křehké (různé velikosti zařízení, Dynamic Type, home indicator) a tlačítko se **neúčastní** nativního glass morphingu ani minimalizace lišty při scrollu.

### 1.2 Solution Overview
Na iOS 26 přidáme scan jako `Tab(role: .search)` — systém ho sám vykreslí jako oddělenou glass kapsli ve správné pozici a zapojí do morph/minimalizace. Protože scan je **akce** (otevírá `fullScreenCover`), ne destinace, odchytíme jeho selection a hned ji vrátíme na předchozí tab; bez `.searchable` se neaktivuje search field. Přidáme `.tabBarMinimizeBehavior(.onScrollDown)`. Pro iOS 18 (kde search role nedává oddělenou kapsli) ponecháme dosavadní overlay FAB přes `#available`.

### 1.3 Scope: What This IS
- Úprava `iosApp/iosApp/UI/Shell/RootView.swift` (struktura TabView + scan handling).
- Úprava `iosApp/iosApp/UI/Shell/ScanButton.swift` (doc komentář; overlay varianta zůstává jen pro iOS 18).
- Zavedení Swift wrapper typu pro selection (`TabSelection`) v rámci iOS shellu.
- Přidání `.tabBarMinimizeBehavior(.onScrollDown)` (iOS 26+).
- Aktualizace doc komentářů popisujících dnešní overlay-FAB pattern.

### 1.4 Scope: What This IS NOT
- **Žádná změna sdíleného Kotlin kódu** — `TopLevelDestination { DENIK, MAPA, PREHLED }` zůstává beze změny.
- Žádná změna Androidu (Compose shell má vlastní nativní řešení).
- Žádná změna scan flow logiky (`ScanFlowModel`, `ScanView`, `CollectionOfferView`, `SplneniView`).
- Žádná změna chování `fullScreenCover` pro scan/splnění, `collectionOffer`, resetů při změně běhu, ani DEV `TA33_TAB`.
- Nepřidáváme skutečné vyhledávání — search role používáme čistě pro *vizuál oddělené kapsle*.

---

## 2. SUCCESS CRITERIA

Implementace je HOTOVÁ, když jsou splněna všechna kritéria:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | Na iOS 26 se při aktivním běhu scan zobrazí jako **oddělená glass kapsle** vpravo v řádku lišty (ne overlay) | Spustit na iOS 26 simulátoru s aktivním během; vizuálně ověřit oddělenou kapsli |
| 2 | Tap na scan kapsli otevře `showScan` `fullScreenCover` a **nepřepne** obsah na scan tab ani nespustí search field | Tapnout kapsli; ověřit, že se otevře scan cover a po zavření je aktivní původní tab |
| 3 | Tab bar se při scrollu dolů minimalizuje (`.onScrollDown`) a při scrollu nahoru vrátí | Scrollovat v Deníku na iOS 26; lišta se zmenší/rozbalí |
| 4 | Scan kapsle je vidět **jen** při `activeRunId != nil`; jinak jsou 3 taby | Přepnout stav běhu; ověřit přítomnost/nepřítomnost kapsle |
| 5 | Na iOS 18 zůstává funkční overlay FAB (solid orange kruh) beze změny chování | Spustit na iOS 18.x simulátoru; ověřit FAB vedle lišty a otevření coveru |
| 6 | Zachováno: scan/splnění cover, `collectionOffer` inset, reset `showScan`/`dismissedControlId` při změně `activeRunId`, DEV `TA33_TAB` | Projít scénáře; ověřit beze změny |
| 7 | Projekt se přeloží pro iOS (build bez chyb) | `xcodebuild ... -scheme iosApp ... build` (viz §8) |
| 8 | Doc komentáře v `RootView`/`ScanButton` popisují nový pattern (search-role kapsle + iOS 18 fallback), ne starý overlay-only popis | Code review komentářů |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
                         RootView (readyContent → tabShell)
                                        │
        ┌───────────────────────────────┴────────────────────────────────┐
        │                        TabView(selection: $selection)           │
        │  Tab(.destination(.denik))   → DenikView                        │
        │  Tab(.destination(.mapa))    → MapaView                         │
        │  Tab(.destination(.prehled)) → ProfilView                       │
        │  ── iOS 26 & activeRunId != nil ────────────────────────────┐   │
        │  Tab(.scan, role: .search)   → prázdný placeholder (Color.clear)│
        │  (systém renderuje jako oddělenou glass kapsli vpravo)      │   │
        └───────────────────────────────┬────────────────────────────┘   │
                                         │                                │
     .onChange(of: selection) ──────────┘                                │
        newValue == .scan → showScan = true; selection = oldValue        │
                                                                          │
     .modifier: iOS26 → .tabBarMinimizeBehavior(.onScrollDown)           │
     .overlay(bottomTrailing): iOS<26 & activeRunId != nil → ScanButton  │
     .safeAreaInset(bottom): collectionOffer                             │
     .fullScreenCover: scanCover / splneniCover                          │
```

**Datový tok scan tapu (iOS 26):** uživatel tapne search kapsli → SwiftUI změní `selection` na `.scan` → `onChange` detekuje `.scan` → nastaví `showScan = true` a `selection = oldValue` (vrácení na původní destinaci, žádný obsah scan tabu se reálně nezobrazí) → `fullScreenCover(isPresented: $showScan)` prezentuje `scanCover`.

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Mechanismus oddělené kapsle (iOS 26) | `Tab(role: .search)` | Jediný nativní způsob, jak dostat vizuálně oddělený trailing prvek v řádku Liquid Glass lišty; morphuje a minimalizuje se se systémem. |
| Selection typ | Swift `enum TabSelection { case destination(TopLevelDestination); case scan }` | Shared `TopLevelDestination` musí zůstat čistý (`DENIK/MAPA/PREHLED`); scan je iOS-only akční sentinel. Wrapper drží typovou jednotu binding-u. |
| Scan jako akce, ne destinace | Odchyt v `.onChange(of: selection)` + revert na `oldValue` | Scan otevírá `fullScreenCover`, nemá vlastní obrazovku-tab. Revert zajistí, že se obsah scan tabu nikdy nezobrazí. |
| Potlačení search fieldu | Scan tab **nemá** `.searchable` | Search field se aktivuje jen když obsah search-role tabu deklaruje `.searchable`. Bez něj je tap čistě selection → hned revert. |
| iOS 18 chování | Overlay FAB přes `#available(iOS 26, *)` else větev | `role: .search` na iOS 18 nevytváří oddělenou kapsli; dosavadní overlay FAB je tam ověřené řešení. |
| Minimalizace lišty | `.tabBarMinimizeBehavior(.onScrollDown)` (iOS 26+) přes availability helper | Explicitní požadavek; API existuje jen na 26+, musí být guarded. |
| Vizuální sjednota (req. 5) | iOS 26 = jen nativní glass kapsle; iOS 18 = jen solid FAB | Obě řešení se nikdy nezobrazí současně → nedrží se dvě různá vizuální řešení naráz. |

---

## 4. IMPLEMENTATION STEPS

> Prováděj v pořadí. Nepřeskakuj kroky.

### Step 1: Zavést `TabSelection` wrapper typ
**Goal**: Typ pokrývající destinace i scan sentinel pro `TabView(selection:)`.
**Files**: `iosApp/iosApp/UI/Shell/RootView.swift` (privátní typ nad/pod `struct RootView`)

```swift
/// Selection model tab baru. Destinace zrcadlí sdílený `TopLevelDestination`;
/// `.scan` je iOS-only akční sentinel pro oddělený search-role tab (otevírá cover,
/// není to samostatná obrazovka). Držíme ho ve Swiftu, sdílený enum zůstává čistý.
private enum TabSelection: Hashable {
    case destination(TopLevelDestination)
    case scan
}
```

**Done when**: Typ existuje a překládá se (`TopLevelDestination` je už dnes Hashable — používá se jako selection i `@State`).

---

### Step 2: Přepnout stav `tab` na `selection: TabSelection`
**Goal**: Binding TabView umí reprezentovat i scan.
**Files**: `iosApp/iosApp/UI/Shell/RootView.swift`

- Změnit `@State private var tab: TopLevelDestination = RootView.initialTab()` → `@State private var selection: TabSelection = .destination(RootView.initialTab())`.
- `initialTab()` zůstává a vrací `TopLevelDestination` (žádná změna DEV `TA33_TAB` logiky).

**Done when**: `selection` je jediný zdroj pravdy pro TabView; `initialTab()` beze změny.

---

### Step 3: Přestavět `baseTabView` — destinace přes `.destination(...)` + podmíněný scan tab
**Goal**: 3 content taby + (iOS 26 & aktivní běh) oddělený search-role scan tab.
**Files**: `iosApp/iosApp/UI/Shell/RootView.swift`

```swift
private var baseTabView: some View {
    TabView(selection: $selection) {
        Tab("Deník", systemImage: "book", value: TabSelection.destination(.denik)) {
            DenikView()
        }
        Tab("Mapa", systemImage: "map", value: TabSelection.destination(.mapa)) {
            MapaView()
        }
        Tab("Profil", systemImage: "person", value: TabSelection.destination(.prehled)) {
            ProfilView()
        }
        // iOS 26: scan jako oddělená glass kapsle (search role). Jen při aktivním běhu.
        // Bez `.searchable` → tap neaktivuje search field, jen selection (odchyceno níže).
        if #available(iOS 26.0, *), model.app.activeRunId != nil {
            Tab("Skenovat", systemImage: "qrcode.viewfinder",
                value: TabSelection.scan, role: .search) {
                Color.clear // scan nemá vlastní obrazovku; selection se hned vrací
            }
        }
    }
    .tint(Ta33Color.orange)
}
```

**Done when**: Na iOS 26 se při aktivním běhu objeví 4. oddělená kapsle; jinak 3 taby.

> **Pozn.:** kombinace `if #available(...)`+ boolean podmínky uvnitř `@TabContentBuilder` je validní. Pokud by konkrétní SDK dělalo potíže, rozdělit na vnořené `if #available { if activeRunId { Tab(...) } }`.

---

### Step 4: Odchytit scan selection a otevřít cover (revert na předchozí tab)
**Goal**: Tap na scan kapsli = akce, ne navigace.
**Files**: `iosApp/iosApp/UI/Shell/RootView.swift` (v `readyContent`/`tabShell` řetězci modifikátorů)

```swift
.onChange(of: selection) { oldValue, newValue in
    guard newValue == .scan else { return }
    showScan = true
    selection = oldValue // oldValue je vždy .destination(...) — na .scan nikdy nezůstaneme
}
```

**Done when**: Tap na kapsli otevře scan cover; po zavření je aktivní původní tab; obsah scan tabu se nikdy nezobrazí; neobjeví se search field.

---

### Step 5: `.tabBarMinimizeBehavior(.onScrollDown)` přes availability helper
**Goal**: Minimalizace lišty při scrollu (iOS 26+), čistě guarded.
**Files**: `iosApp/iosApp/UI/Shell/RootView.swift` (helper extension na konci souboru) + aplikace na `baseTabView`

```swift
private extension View {
    /// iOS 26: lišta se minimalizuje při scrollu dolů. Na iOS 18 no-op.
    @ViewBuilder func ta33TabBarMinimizeOnScroll() -> some View {
        if #available(iOS 26.0, *) {
            self.tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
    }
}
```

Aplikace: `baseTabView.ta33TabBarMinimizeOnScroll()` (uvnitř `tabShell`).

**Done when**: Na iOS 26 lišta reaguje na scroll; na iOS 18 se překládá a chová beze změny.

---

### Step 6: Overlay FAB omezit na iOS 18 (`< 26`)
**Goal**: Na iOS 26 řídí scan nativní kapsle; overlay FAB jen fallback pro iOS 18.
**Files**: `iosApp/iosApp/UI/Shell/RootView.swift` (`tabShell`, `scanFab`)

```swift
private var tabShell: some View {
    baseTabView
        .ta33TabBarMinimizeOnScroll()
        .overlay(alignment: .bottomTrailing) { legacyScanFab }
        .safeAreaInset(edge: .bottom) { collectionOffer }
}

/// iOS 18 fallback: plovoucí scan FAB vedle klasické lišty (na iOS 26 řeší
/// oddělená search-role kapsle v `baseTabView`, tady se nevykreslí).
@ViewBuilder
private var legacyScanFab: some View {
    if #unavailable(iOS 26.0), model.app.activeRunId != nil {
        ScanButton(action: { showScan = true })
            .padding(.trailing, Ta33Spacing.x5)
            .padding(.bottom, Ta33Spacing.x4)
    }
}
```

- Odstranit dosavadní `scanFabBottomPadding` (byl kvůli rozdílu iOS 26 vs 18; teď je FAB jen iOS 18).

**Done when**: iOS 26 nemá overlay FAB (jen nativní kapsli); iOS 18 má FAB beze změny vzhledu.

---

### Step 7: Aktualizovat doc komentáře (`RootView` + `ScanButton`)
**Goal**: Komentáře popisují nový pattern, ne dnešní overlay-only přístup.
**Files**: `RootView.swift` (hlavička typu, `tabShell`, `baseTabView`), `ScanButton.swift` (hlavička)

- `RootView` hlavička: scan na iOS 26 = oddělený `Tab(role: .search)` (glass kapsle, morph, minimalizace); iOS 18 = overlay FAB.
- `ScanButton` hlavička: „používá se **jen na iOS 18** jako solid orange FAB; na iOS 26 renderuje scan systémová search-role kapsle." Zvážit zjednodušení `ScanButtonSurface` (viz §12.3).

**Done when**: Žádný komentář netvrdí, že iOS 26 scan je overlay `.tabViewBottomAccessory`/FAB.

---

### Step 8: Build & vizuální ověření
**Goal**: Kompilace + funkční chování na obou OS.
**Files**: —

- Build (viz §8). Vizuálně ověřit na iOS 26 i iOS 18 simulátoru scénáře z §2.

**Done when**: Splněna všechna kritéria §2.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| Dvojité rychlé tapnutí scan kapsle | Cover se otevře jednou; druhý tap idempotentní | `showScan = true` je idempotentní; revert zajistí, že další tap znovu projde `.scan` |
| `activeRunId` se změní na `nil`, když je kapsle vybraná | Kapsle zmizí, selection zůstane na platné destinaci | Selection nikdy nesedí na `.scan` (vždy revert); zmizení tabu neovlivní aktivní destinaci |
| Změna běhu (`activeRunId` se změní) během otevřeného scanu | `showScan` a `dismissedControlId` se resetují | Zachovat stávající `.onChange(of: model.app.activeRunId)` reset |
| iOS 18 zařízení | Žádná search kapsle; overlay FAB; žádný `tabBarMinimizeBehavior` | `#available`/`#unavailable` větve |
| Search-role tab by aktivoval search UI | Neaktivuje se — chybí `.searchable` | Scan tab obsah = `Color.clear`, žádný `.searchable` v hierarchii |
| Revert `selection` způsobí vizuální blik výběru kapsle | Minimalizovat — revert je synchronní v témže onChange | `selection = oldValue` hned; obsah `Color.clear` je nenákladný. Pokud blik ruší → viz §12.2 |
| `Mapa` tab bez scroll view | Lišta se u Mapy neminimalizuje | Očekávané (minimalizace váže na scroll); nevadí |

---

## 6. SECURITY CONSIDERATIONS

> Bez reálných bezpečnostních dopadů — čistě UI/navigační změna iOS shellu.

- **Input validation**: N/A (žádný uživatelský vstup).
- **Auth/Access control**: N/A.
- **Sensitive data**: N/A — scan flow (kamera/QR) se nemění, jen vstupní bod do coveru.
- **Logging**: Nepřidávat žádné logování selection/tapů.

---

## 7. ASSUMPTIONS

Odvozeno ze zadání a kódu — ověřit:

1. **`TopLevelDestination` je Hashable ve Swiftu** — dnes se používá jako `@State` i `Tab(value:)`, což Hashable vyžaduje a kompiluje se; wrapper `TabSelection` tedy může derivovat Hashable. Pokud ne, doplnit `Hashable` ručně.
2. **`Tab(role: .search)` dovolí custom `systemImage`** (`qrcode.viewfinder`) místo defaultní lupy — signatura `Tab(_:systemImage:value:role:content:)` to umožňuje. Pokud SDK ikonu přebije, řešit dle §12.2.
3. **Uživatel opustil clarifikaci volbou v předchozím kroku** — mechanismus (search-role vs overlay) byl rozhodnut přes `AskUserQuestion` = „Nativní search-role tab". Další scope otázky nevznikly (jasné zadání) → `AskUserQuestion` v této fázi nevoláno.
4. **iOS 18 fallback ponechat** — `role: .search` na iOS 18 nedává oddělenou kapsli, proto overlay FAB. Předpoklad opřen o to, že Liquid Glass separace je iOS 26 feature.
5. **Deployment target 18.2** — availability guardy `iOS 26.0` jsou nutné; `#Preview` běží na aktuálním SDK.

> Otevřené otázky viz Sekce 12.

---

## 8. QUICK REFERENCE

### Files to Modify
- `iosApp/iosApp/UI/Shell/RootView.swift` — `TabSelection` typ, `selection` state, `baseTabView` se scan tabem, `onChange` scan handling, minimize helper, overlay FAB jen iOS 18, doc komentáře.
- `iosApp/iosApp/UI/Shell/ScanButton.swift` — doc komentář (iOS 18-only role); volitelně zjednodušit `ScanButtonSurface`.

### Files to Create
- žádné.

### Dependencies
- žádné nové (jen SwiftUI iOS 26 API `Tab(role:)`, `tabBarMinimizeBehavior`).

### Commands
```bash
# Build (headless, bez podpisu) — nahraď <simulator-id> reálným ID (xcrun simctl list devices)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'id=<simulator-id>' CODE_SIGNING_ALLOWED=NO build

# Vizuální ověření: spustit na iOS 26 i iOS 18 simulátoru z Xcode (scheme iosApp).
# DEV výchozí tab: nastav env TA33_TAB=denik|mapa|profil ve schématu pro deterministické snímky.
```

---

## 9. DESIGN REFERENCE

### Visual Spec
Referenční pattern: iOS 26 Liquid Glass tab bar (WWDC25). Poslední prvek vpravo je oddělená „kapsle" — kanonický příklad je search tlačítko v Health/Maps. Zdroj vzhledu = **systém** (native Liquid Glass), ne vlastní mockup. V souladu s memory „Native look per platform" — neklonovat unifikovaný mockup, vzhled patří platformě.

### Component/Screen Mapping
- Oddělená glass kapsle → `Tab("Skenovat", systemImage: "qrcode.viewfinder", value: .scan, role: .search)` v `RootView.baseTabView`.
- iOS 18 solid FAB → `ScanButton` v `RootView.legacyScanFab`.
- Hlavní lišta (Deník/Mapa/Profil) → 3 `Tab(...)` s `.tint(Ta33Color.orange)`.

### Style Mapping

| Design Spec | Code Equivalent | Value |
|-------------|-----------------|-------|
| Akcentní barva (tint symbolů/kapsle) | `.tint(Ta33Color.orange)` na TabView | brand orange (token) |
| Scan ikona | `systemImage: "qrcode.viewfinder"` | SF Symbol |
| iOS 26 povrch scan kapsle | systémový Liquid Glass (search role) | native — nehardcodovat |
| iOS 18 FAB povrch | `ScanButtonSurface` (solid orange + glow) | `Ta33Color.orange`, shadow `x4/x2` |
| iOS 18 FAB odsazení | `.padding(.trailing, Ta33Spacing.x5)` `.padding(.bottom, Ta33Spacing.x4)` | 20 / 16 pt |
| Rozměr FAB (iOS 18) | `frame(width/height: Ta33Spacing.x9)` | 56 pt |

---

## 10. CORRECTIONS FROM CURRENT STATE

| What | Before (Wrong/Current) | After (Correct/Target) |
|------|------------------------|------------------------|
| Scan vstup iOS 26 | Ruční `.overlay(.bottomTrailing)` FAB s hardcoded paddingem, mimo nativní lištu | Nativní `Tab(role: .search)` — systémová oddělená glass kapsle v řádku lišty |
| Selection typ | `@State var tab: TopLevelDestination` | `@State var selection: TabSelection` (destinace + scan sentinel) |
| Minimalizace lišty | Chybí | `.tabBarMinimizeBehavior(.onScrollDown)` (iOS 26+) |
| Overlay FAB rozsah | Renderuje se na iOS 26 i 18 (s `scanFabBottomPadding` větví) | Jen iOS 18 (`#unavailable(iOS 26)`), bez padding větve |
| Glass řešení | Dvě paralelní řešení (native lišta + vlastní `glassEffect` FAB) vedle sebe na 26 | iOS 26 = jen native kapsle; iOS 18 = jen solid FAB (nikdy současně) |
| Doc komentáře | Popisují overlay FAB / `.tabViewBottomAccessory` jako iOS 26 řešení | Popisují search-role kapsli (26) + FAB fallback (18) |

---

## 11. CHANGELOG

| Date | Change |
|------|--------|
| 2026-07-11 | Initial plan created |

---

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES

### 12.1 Alternative Approaches Considered

| Approach | Pros | Cons | Selected? |
|----------|------|------|-----------|
| **A. `Tab(role: .search)` + revert (akce)** | Nativní oddělená glass kapsle, zdarma morph/minimalizace, správná pozice napříč zařízeními | Sémantický hijack search role; nutný revert selection; riziko blику/edge-case chování search UI | ✅ |
| **B. Robustní overlay FAB** | Plná kontrola nad akcí (žádný search mód), jednoduché | Tlačítko není „součástí" nativní lišty, křehké zarovnání, neúčastní se native morph/minimalizace | — |
| **C. `.tabViewBottomAccessory`** | Nativní, morphuje s lištou při minimalizaci | Sedí **nad** lištou (ne vedle), je vidět na všech tabech, sémanticky pro status/now-playing — ne oddělené trailing tlačítko | — |

**Why the selected approach won**: Uživatel explicitně chce nativní iOS 26 pattern „oddělené kapsle vpravo" a `Tab(role: .search)` je jediný nativní mechanismus, který ho produkuje (potvrzeno rešerší — Health-style search tlačítko). Přijímáme cenu (revert + hlídání search UI) výměnou za nativní vzhled a chování.

### 12.2 Open Questions

- [ ] **Přebíjí `role: .search` naši `systemImage` (`qrcode.viewfinder`) defaultní lupou?** — Proposed direction: signatura `Tab(_:systemImage:value:role:content:)` custom ikonu umožňuje; ověřit na simulátoru. Pokud přebije, zvážit `label` bez systému nebo přijmout, že kapsle je čistě akční s vlastním glyphem přes `Tab { } label:`.
- [ ] **Blikne výběr kapsle při synchronním revertu `selection`?** — Proposed direction: nejdřív ověřit vizuálně; pokud ruší, zkusit revert v `Task { @MainActor in selection = oldValue }` nebo `withTransaction` bez animace (`var t = Transaction(); t.disablesAnimations = true`).
- [ ] **Aktivuje search-role tap přesto search prezentaci, i bez `.searchable`?** — Proposed direction: bez `.searchable` by neměl; pokud ano, přidat `.searchable` skrytě a okamžitě dismiss NENÍ žádoucí — místo toho padnout na Alternative B (overlay FAB) i pro iOS 26. Rozhodovací bod při testu.
- [ ] **Interaguje `.tabBarMinimizeBehavior` s viditelností search kapsle korektně?** — Proposed direction: ověřit, že se kapsle minimalizuje spolu s lištou a po scrollu nahoru se vrátí.

### 12.3 Suggestions & Follow-ups

- Zvážit zjednodušení `ScanButtonSurface` na iOS 18-only (odstranit `glassEffect` větev, kterou už shell na iOS 26 nevyužívá) — ale ponechat, pokud ji používá `#Preview` nebo jiná místa; ověřit grepem před smazáním.
- Po ověření chování zvážit `.tabBarMinimizeBehavior(.automatic)` místo `.onScrollDown`, pokud automatika lépe ladí s Mapou (bez scrollu).
- Až budou doplněny reálné fonty (Big Shoulders/Inter), znovu ověřit vertikální zarovnání kapsle vs. label baseline.
- Zvážit haptiku (`.sensoryFeedback`) při otevření scanu z kapsle pro parity s FABem.
- Sdílený `TopLevelDestination` má case `PREHLED`, ale UI ho zobrazuje jako „Profil" — mimo rozsah, ale hodné budoucího sjednocení názvosloví.
