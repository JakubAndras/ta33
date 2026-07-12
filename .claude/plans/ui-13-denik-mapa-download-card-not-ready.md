# Skrytá Mapa do stažení + download karta v Deníku (not-ready stav)

> **Summary**: Zrušit celoobrazovkový readiness gate; po LOADING vždy zobrazit shell. Dokud data akce/mapa nejsou stažená, tab **Mapa je skrytý** a **Deník** ukazuje download kartu; Profil funguje. Po stažení (READY) se Mapa objeví a všechny taby ukazují normální obsah.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Když data akce/mapy nejsou stažená (readiness `NOT_READY`/`PREPARING`), celá aplikace se přepne na jednu obrazovku Příprava a zmizí taby i spodní lišta. To je nechtěné - uživatel ztratí navigaci a kontext.

### 1.2 Solution Overview
Po LOADING necháme aplikaci vždy v shellu. Dokud nejsou data stažená, **tab Mapa se v liště vůbec nezobrazí** (nemá bez dat/dlaždic co ukázat) a **tab Deník** místo obsahu ukáže **download kartu** (stávající download UI nad `DownloadViewModel`). Profil funguje beze změny. Po dokončení stažení (READY) se Mapa objeví a Deník i Mapa ukazují normální obsah - reaktivně, bez ruční navigace.

### 1.3 Scope: What This IS
- Zrušení top-level gate na `NOT_READY`/`PREPARING` (iOS `RootView`, Android `Ta33App`).
- **Mapa tab podmíněný na `readiness == READY`** (skrytý jinak) - v liště i v přepínání.
- Deník: `readiness == READY ? obsah : DownloadCard`.
- Reset aktivního tabu na Deník, když se Mapa schová (readiness spadne z READY).
- iOS: extrakce `DownloadCardView` z `PreparationView`. Android: reuse existujícího stateless `PreparationContent`.
- **Jedna sdílená** `DownloadViewModel`/`PreparationModel` vlastněná shellem.
- Odstranění nepoužitých gate obrazovek (`PreparationView` iOS / `PreparationScreen` Android).

### 1.4 Scope: What This IS NOT
- Žádná změna Profilu.
- Žádná změna download flow logiky (`DownloadViewModel`, FR-11) - jen kde/kdy se UI zobrazuje.
- Žádná změna splash (LOADING → splash zůstává).
- Žádná download karta v Mapě (Mapa v not-ready neexistuje).
- Žádný reálný MapLibre/GPS layer.
- Žádná změna sdílených doménových modelů.

---

## 2. SUCCESS CRITERIA

| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | Po LOADING je vždy vidět shell (lišta + taby), i když data nejsou stažená | běh (not-ready) |
| 2 | V not-ready lišta ukazuje jen **Deník + Profil** (Mapa skrytá) | běh (Sandbox „Data stažena" OFF) |
| 3 | V not-ready Deník ukazuje download kartu místo obsahu | běh |
| 4 | Profil funguje v not-ready beze změny (QR + Nastavení + DEBUG Sandbox) | běh |
| 5 | Po READY se **Mapa objeví** v liště a Deník i Mapa ukazují normální obsah - reaktivně | běh (Sandbox toggle ON) |
| 6 | Když readiness spadne z READY na not-ready a aktivní byl tab Mapa, přepne se na Deník (žádný „prázdný" tab) | běh (na Mapě přepnout Sandbox OFF) |
| 7 | Download probíhá přes **jednu** VM instanci - přepnutí Deník↔Profil stažení nezruší | běh |
| 8 | Download karta pokrývá idle (CTA) / downloading (progress) / paused / error (retry) / done | běh / preview |
| 9 | `:androidApp:assembleDebug` + `xcodebuild -scheme iosApp` (iOS 18 i 26) zelené | příkazy (§8) |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
  PŘED:  readiness gate na top-levelu (celá appka)
    NOT_READY/PREPARING → PreparationView / PreparationScreen (bez tabů)

  PO:    shell vždy (po LOADING)
    LOADING → Splash
    jinak  → shell:
        ready = readiness == READY
        lišta: ready ? [Deník, Mapa, Profil] : [Deník, Profil]      ← Mapa skrytá
        ├─ Deník  : ready ? DenikView : DownloadCard(sdílený model)
        ├─ Mapa   : jen když ready → MapaView
        └─ Profil : ProfilView (vždy)
        když !ready && aktivní tab == Mapa → reset na Deník
```

### 3.2 Klíčové rozhodnutí - sdílená instance download VM

`DownloadViewModel` je Koin **`factory`**. Kartu drží jen Deník, ale na **Androidu** `when(tab)` komponuje jen aktivní tab → přepnutí Deník→Profil během stahování by Deníkovu instanci zahodilo a **zrušilo běžící stažení**. Proto download VM vlastní **shell** (jedna instance, přežívá přepínání tabů), stav+akce se předají do download karty.
- iOS: `RootView` drží `@StateObject var prep = PreparationModel()`, předá do `DownloadCardView(model: prep)`. (iOS taby žijí; instance stabilní.)
- Android: `MainShell` drží `val downloadVm: DownloadViewModel = koinViewModel()`, předá stav/akce do `PreparationContent(...)`.
- Koin zůstává `factory` - **žádná DI změna**.

### 3.3 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Mapa v not-ready | Skrytá (podmíněný tab) | Bez dat/dlaždic nemá co ukázat; jednodušší než karta v Mapě |
| Download karta | Jen v Deníku | Jediný smysluplný vstup do stažení; Mapa neexistuje |
| Kde je readiness větev | Na úrovni shellu | Minimální dopad; Deník/Mapa VM beze změny |
| Vlastník download VM | Shell (1 sdílená instance) | Přežije přepnutí tabu na Androidu; parity na iOS |
| Reuse download UI | iOS extrahovat `DownloadCardView`; Android reuse `PreparationContent` (už stateless) | Neduplikovat |
| Staré gate obrazovky | Odstranit (`PreparationView`, `PreparationScreen`) | Bez mrtvého kódu |
| Reset tabu | `!ready && tab == Mapa → Deník` | Zabrání prázdnému/nevalidnímu výběru |

---

## 4. IMPLEMENTATION STEPS

> Pořadí: iOS (extrakce → shell) → Android (shell → nav) → cleanup → build.

### Step 1: iOS - extrahovat `DownloadCardView` z `PreparationView`
- [x] **Status**: Done
**Goal**: znovupoužitelná download karta beroucí sdílený `PreparationModel`.
**Files**: `iosApp/iosApp/UI/Preparation/DownloadCardView.swift` (create); zdroj `PreparationView.swift`

```swift
struct DownloadCardView: View {
    @ObservedObject var model: PreparationModel   // vlastní RootView (sdílená instance)
    var body: some View { /* původní PreparationView tělo: IdentityCard + PaperCard(statusSection) + WarningBanner */ }
}
```
- Karta **nevlastní** `@StateObject` (dostává model zvenčí).

**Done when**: `DownloadCardView(model:)` vykresluje totéž co dřív `PreparationView`; kompiluje.

---

### Step 2: iOS - `RootView`: zrušit gate, skrýt Mapu, Deník download karta, reset výběru
- [x] **Status**: Done
**Goal**: shell vždy; Mapa jen v ready; Deník karta v not-ready.
**Files**: `iosApp/iosApp/UI/Shell/RootView.swift`

- `body` switch: `case .loading: SplashView()`; **default (vč. .notReady/.preparing/.ready) → readyContent**.
- Přidat `@StateObject private var prep = PreparationModel()`; v `readyContent` `.task { await prep.observe() }`.
- Zavést `private var isReady: Bool { model.app.readiness == .ready }`.
- `baseTabView`:
  ```swift
  Tab("Deník", systemImage: "book", value: .destination(.denik)) {
      if isReady { DenikView() } else { DownloadCardView(model: prep) }
  }
  if isReady {
      Tab("Mapa", systemImage: "map", value: .destination(.mapa)) { MapaView() }
  }
  Tab("Profil", systemImage: "person", value: .destination(.prehled)) { ProfilView() }
  // scan search-role tab beze změny (iOS 26 & activeRunId != nil)
  ```
- Reset výběru: `.onChange(of: model.app.readiness, initial: true) { _, _ in if !isReady, selection == .destination(.mapa) { selection = .destination(.denik) } }` (v `readyContent` řetězci).

**Done when**: `xcodebuild` OK; not-ready → lišta Deník+Profil, Deník karta; ready → +Mapa.

---

### Step 3: iOS - odstranit nepoužitý `PreparationView`
- [x] **Status**: Done
**Goal**: bez mrtvého gate kódu.
**Files**: smazat `iosApp/iosApp/UI/Preparation/PreparationView.swift`. `PreparationModel.swift` zůstává.
- Grep ověření, že `PreparationView` nikde není referencován.

**Done when**: build bez referencí na `PreparationView`.

---

### Step 4: Android - `Ta33App`: zrušit gate
- [x] **Status**: Done
**Goal**: shell vždy po LOADING.
**Files**: `androidApp/.../ui/shell/Ta33App.kt`

```kotlin
when (app.readiness) {
    AppReadiness.LOADING -> SplashView()
    else -> MainShell(app = app, onScan = { /* TODO FR-09 */ })
}
```
- Odstranit import/použití `PreparationScreen`.

**Done when**: kompiluje; po LOADING vždy `MainShell`.

---

### Step 5: Android - `MainShell`: sdílená download VM, skrytá Mapa, Deník karta, reset tabu
- [x] **Status**: Done
**Goal**: readiness větev + skrytý Mapa tab.
**Files**: `androidApp/.../ui/shell/MainShell.kt`

- `val downloadVm: DownloadViewModel = koinViewModel()`; `val download by downloadVm.state.collectAsStateWithLifecycle()`.
- `val ready = app.readiness == AppReadiness.READY`.
- Reset: `LaunchedEffect(ready) { if (!ready && tab == TopLevelDestination.MAPA) tab = TopLevelDestination.DENIK }`.
- `bottomBar = { Ta33BottomNav(selected = tab, onSelect = { tab = it }, showMapa = ready) }`.
- Tab switch:
  ```kotlin
  when (tab) {
      DENIK -> if (ready) DenikScreen() else PreparationContent(
          state = download, onStart = downloadVm::start, onPause = downloadVm::pause,
          onResume = downloadVm::resume, onRetry = downloadVm::retry,
          onToggleWifiOnly = { wifiOnly -> downloadVm.setNetworkPreference(if (wifiOnly) WIFI_ONLY else WIFI_AND_CELLULAR) },
      )
      MAPA -> MapaScreen()   // dosažitelné jen v ready
      PREHLED -> ProfilScreen()
  }
  ```

**Done when**: `:androidApp:assembleDebug` OK; not-ready → Deník karta, lišta bez Mapy.

---

### Step 6: Android - `Ta33BottomNav`: podmíněná Mapa
- [x] **Status**: Done
**Goal**: Mapa položka jen když `showMapa`.
**Files**: `androidApp/.../ui/shell/Ta33BottomNav.kt`

- Přidat param `showMapa: Boolean = true`; `NavItem(MAPA, …)` obalit `if (showMapa) { … }`.

**Done when**: lišta ukazuje Mapu jen v ready; preview OK.

---

### Step 7: Android - odstranit nepoužitý `PreparationScreen`
- [x] **Status**: Done
**Goal**: bez mrtvého gate kódu.
**Files**: smazat `androidApp/.../ui/preparation/PreparationScreen.kt` (`PreparationContent.kt` zůstává, reuse v `MainShell`).

**Done when**: build bez referencí na `PreparationScreen`.

---

### Step 8: Build & ověření
- [x] **Status**: Done
**Files**: -
- `./gradlew :androidApp:assembleDebug`; `xcodebuild -scheme iosApp` iOS 18 i 26; `./gradlew :shared:allTests`.
- iOS simulátor + DEBUG Sandbox „Data akce / mapa stažena": OFF → lišta Deník+Profil, Deník karta, Mapa pryč; ON → Mapa se objeví, Deník obsah. Na Mapě OFF → reset na Deník.

**Done when**: kritéria §2.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected | Handle |
|---|---|---|
| readiness `PREPARING` | Deník karta ukazuje progress; Mapa skrytá | `!ready` → karta + skrytý Mapa tab |
| download error | karta „Stahování selhalo / ZKUSIT ZNOVU" | DownloadUiState error stav |
| download hotov (READY) | Mapa se objeví; Deník/Mapa obsah | branch na readiness (reaktivní) |
| aktivní tab Mapa + readiness spadne na not-ready | přepnutí na Deník | reset (iOS onChange, Android LaunchedEffect) |
| přepnutí Deník↔Profil během stahování | jedno spojité stažení | shell vlastní 1 VM instanci |
| DEV `TA33_TAB=mapa` + start v not-ready (iOS) | reset na Deník při appear | `onChange(initial: true)` reset |
| not-ready + Profil | funguje (QR + Nastavení); scan FAB/kapsle se neukáže (bez běhu) | Profil nezávisí na readiness |
| `blockedByNetwork` (jen Wi-Fi) | karta „Čeká na Wi-Fi" banner | zachovat WarningBanner |
| LOADING | splash (beze změny) | `.loading → Splash` |

---

## 6. SECURITY CONSIDERATIONS
- Bez bezpečnostních dopadů - UI/navigační přeuspořádání. Download flow (FR-11) beze změny.

---

## 7. ASSUMPTIONS
1. **Scope clarification přeskočen** - požadavek specifický (Mapa skrytá do stažení; karta jen v Deníku); defaulty zaznamenány.
2. **`DenikViewModel`/`MapaViewModel` beze změny** - readiness větev na úrovni shellu.
3. **`PreparationContent` (Android) je stateless** (bere state+callbacky) - potvrzeno z kódu, reuse přímo.
4. **`MainShell` dostává `app`** (readiness) - potvrzeno; `Ta33App` mu ho předává.
5. **iOS TabView zvládne podmíněné odebrání Mapa tabu** + reset selection přes `onChange` - hlídáme prázdný výběr.
6. **readiness ERROR neexistuje** na `AppReadiness` (jen `PreparationStatus`); chybu stažení řeší karta.

> Otevřené otázky viz §12.

---

## 8. QUICK REFERENCE

### Files to Create
- iOS: `iosApp/iosApp/UI/Preparation/DownloadCardView.swift`

### Files to Modify
- iOS: `UI/Shell/RootView.swift` (`PreparationModel.swift` zůstává)
- Android: `ui/shell/Ta33App.kt`, `ui/shell/MainShell.kt`, `ui/shell/Ta33BottomNav.kt` (`ui/preparation/PreparationContent.kt` reuse beze změny)

### Files to Delete
- iOS: `UI/Preparation/PreparationView.swift`
- Android: `ui/preparation/PreparationScreen.kt`

### Commands
```bash
./gradlew :androidApp:assembleDebug
./gradlew :shared:allTests
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'id=<ios26-sim>' CODE_SIGNING_ALLOWED=NO build
xcodebuild ... -destination 'id=<ios18-sim>' ... build
```

---

## 9. DESIGN REFERENCE

### Visual Spec
Reuse download UI (Příprava, ui-07/08) - identity karta + `PaperCard` se stavem stažení (CTA/progress/pauza/chyba/hotovo) + „Čeká na Wi-Fi" banner. Renderuje se **uvnitř Deník tabu** (shell + lišta zůstávají). Mapa se v liště objeví až po stažení. Native look per platforma.

### Component/Screen Mapping
- iOS: `DownloadCardView` (z `PreparationView`) v Deník slotu `RootView.baseTabView`; Mapa `Tab` podmíněný `isReady`.
- Android: `PreparationContent` (reuse) v `MainShell` Deník větvi; `Ta33BottomNav(showMapa = ready)`.

### Style Mapping
| Design Spec | Code Equivalent | Value |
|---|---|---|
| CTA „Stáhnout data akce" | `PrimaryButton` / orange pill | brand orange |
| identity karta | `IdentityCard` (slate-800) | radius 20 |
| download karta | `PaperCard` | radius 20 |
| progress | `ProgressView(value:)` / `LinearProgressIndicator` | brand orange |
| Wi-Fi banner | `WarningBanner`/`OfflineBanner` | warning-tint |
| pozadí | `Ta33Color.cream` / theme background | cream |

---

## 10. CORRECTIONS FROM CURRENT STATE

| What | Before | After |
|---|---|---|
| Not-ready stav | Celá appka = obrazovka Příprava (bez tabů) | Shell zůstává; Deník = download karta |
| Mapa tab v not-ready | (nedostupný - gate překryl vše) | Skrytý v liště; objeví se až po stažení |
| Download UI | Gate `PreparationView`/`PreparationScreen` | `DownloadCardView` (iOS) / `PreparationContent` (Android) v Deník slotu |
| Download VM instance | Jedna (gate byl jediný) | Jedna sdílená vlastněná shellem |
| Profil v not-ready | Nedostupný | Dostupný beze změny |
| iOS `RootView` gate | `.notReady/.preparing → PreparationView` | `.loading → Splash`; jinak `readyContent`; Mapa tab podmíněný |
| Android `Ta33App` gate | `NOT_READY/PREPARING → PreparationScreen` | `LOADING → Splash`; jinak `MainShell`; `Ta33BottomNav(showMapa=ready)` |

---

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-12 | Initial plan created (download karta v Deníku i Mapě) |
| 2026-07-12 | REQUIREMENT_CHANGE: Mapa tab skrytý do stažení (READY); download karta jen v Deníku; přidán reset aktivního tabu při schování Mapy + podmíněná položka v liště; odstraněna karta v Mapě. Sekce 1-5,7,8,10 upraveny. |
| 2026-07-12 | Implementation: Steps 1-8 hotové. iOS `DownloadCardView` extrahováno, `RootView` gate zrušen + Mapa podmíněná + reset výběru; Android `Ta33App`/`MainShell`/`Ta33BottomNav` (sdílená download VM, `showMapa`, reset tabu); `PreparationView`/`PreparationScreen` smazány. Ověřeno: iOS 26 build + vizuál not-ready (lišta Deník+Profil, Deník download karta); Android assembleDebug + `:shared:allTests` zelené. |

---

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES

### 12.1 Alternative Approaches Considered

| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Skrytá Mapa + karta jen v Deníku, gate na úrovni shellu, sdílená download VM** | Splňuje požadavek; minimální dopad; Deník/Mapa VM beze změny; 1 download instance | Podmíněný tab + reset výběru | ✅ |
| B. Download karta v Deníku i Mapě (Mapa viditelná) | Konzistentní obě obrazovky | Uživatel nechce Mapu v not-ready; víc UI | - |
| C. Readiness flag uvnitř Deník/Mapa VM | Logika u obrazovky | Nutno měnit oba VM; stejně řešit sdílení VM + skrytí tabu | - |

**Why the selected approach won**: Přesně splňuje nový požadavek (Mapa až po stažení, karta jen v Deníku) s minimem změn a bez zásahu do Deník/Mapa VM.

### 12.2 Open Questions
- [ ] **Flash/přechod při objevení Mapy** - Proposed: nechat systémovou animaci lišty; případně jemný fade later.
- [ ] **Smazat staré gate soubory?** - Proposed: smazat po ověření nevyužití.

### 12.3 Suggestions & Follow-ups
- Map-specific ilustrace/text na kartě (teď generická „data akce").
- Po READY jemný přechod mezi kartou a obsahem Deníku.
- Zvážit `single` pro `DownloadViewModel` v Koin, pokud ho bude konzumovat i Profil.
