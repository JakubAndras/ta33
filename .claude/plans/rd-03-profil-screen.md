# RD-03 - Profil (ProfilScreen) - Android + iOS

> **Summary**: Přepsat tab „Přehled" na kanonický „Profil" (`ProfilScreen`): identita (avatar, jméno, e-mail), startovní číslo + „Zaplaceno", odbavovací QR (před akcí) / blok „Tvoje akce" (na trase) a Nastavení - na Androidu (Compose) i iOS (SwiftUI). Identita/číslo/QR jsou **mock** (Etapa 2 data); „Tvoje akce" + nastavení z hotových FR-10 VM. Přejmenovat tab na „Profil".

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Tab je teď „Přehled" (Overview+Settings, ui-05/06). Kanonický design je **„Profil"** (`ProfilScreen`, Screens.jsx): identita účastníka, startovní číslo, stav platby, odbavovací QR (před akcí) nebo blok „Tvoje akce" (na trase), Nastavení. I label tabu má být „Profil", ne „Přehled".

### 1.2 Solution Overview
Obrazovka `ProfilScreen`: **hlava** (avatar s iniciálami, jméno, e-mail - mock), **startovní číslo** karta (slate-800: „147" + stav „Zaplaceno"), pak podle běhu buď **odbavovací QR** (před akcí; mock QR placeholder) nebo blok **„Tvoje akce"** (aktivní trasa / naskenováno / synchronizováno - z `OverviewViewModel`), a **Nastavení** (Notifikace toggle → `SettingsViewModel`, Hlasové pokyny toggle [mock/lokální], Kontaktovat pořadatele, FAQ). Mock identita ze sdíleného `ProfileMock`. Tab přejmenovat „Přehled"→„Profil" v obou shellech.

### 1.3 Scope: What This IS
- Android + iOS `ProfilScreen` dle designu (identita, startovní číslo, QR/Tvoje akce, Nastavení).
- Sdílený `ProfileMock` (jméno/iniciály/e-mail/startovní číslo/stav platby) - Etapa-2 mock data.
- Reuse `OverviewViewModel` (Tvoje akce) + `SettingsViewModel` (Notifikace/kontakt/FAQ) z FR-10.
- Mock odbavovací QR (placeholder vzor).
- Přejmenování tabu na „Profil" (bottom nav Android + iOS).

### 1.4 Scope: What This IS NOT
- **Reálná identita/platba/odbavovací QR** - Etapa 2 (auth/rezervace/platba). Teď mock.
- Deník, Mapa - RD-01/02.
- Reálné generování QR (qrcode) - placeholder; reálné = Etapa 2 (FR-13).
- „Hlasové pokyny" jako reálná funkce - jen UI toggle (mock/lokální stav), logika není.

---

## 2. SUCCESS CRITERIA
| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | `:androidApp:assembleDebug` + `xcodebuild -scheme iosApp` zelené | příkazy |
| 2 | Hlava: avatar (iniciály „JN"), „Jan Novák", e-mail | preview |
| 3 | Startovní číslo karta (slate-800): „STARTOVNÍ ČÍSLO" + „147" + „Zaplaceno" (zelená fajfka) | preview |
| 4 | Bez běhu → „Tvůj odbavovací QR" karta (mock QR + text); s během → blok „Tvoje akce" (aktivní trasa / naskenováno „2 z 5" / synchronizováno „Ne") | preview obou |
| 5 | Nastavení: Notifikace toggle (funkční, `setNotificationsEnabled`), Hlasové pokyny toggle (lokální), Kontaktovat pořadatele, FAQ | preview |
| 6 | Tab v bottom navu se jmenuje „Profil" (obě platformy) | běh |
| 7 | Žádný hardcoded hex/dp / hex/CGFloat - přes tokeny | code review |
| 8 | iOS-nativní (List/Form + Toggle + nativní řádky), ne klon Compose | code review |

---

## 3. TECHNICAL DESIGN

### 3.1 Mock identita (shared)
```kotlin
object ProfileMock {
    const val displayName = "Jan Novák"
    const val initials = "JN"
    const val email = "jan.novak@email.cz"
    const val startNumber = 147
    const val paid = true   // "Zaplaceno"
}
```
`odbavovací QR` = deterministický pseudo-vzor (jako design `QRGlyph`, 21×21 mřížka z jednoduchého seedu) - vykreslí UI vrstva; není skenovatelný (mock).

### 3.2 Data / VM
- **„Tvoje akce" + gate before/on-route**: `OverviewViewModel.state` (FR-10) - `hasActiveRun`, `activeRoute`, `progress`, `syncStatus`. Before = `!hasActiveRun` → QR; on-route = `hasActiveRun` → Tvoje akce.
- **Nastavení**: `SettingsViewModel` (FR-10) - Notifikace toggle (funkční), organizerContact, FAQ. „Hlasové pokyny" = lokální UI stav (mock, bez logiky).
- Není potřeba nový sdílený VM - reuse FR-10 VM + `ProfileMock` + lokální stav. (Volitelně tenký `ProfilViewModel` sdružující, ale reuse stačí.)

### 3.3 UI (dle ProfilScreen)
- Hlava: `Row` avatar (kruh, radiální oranžový gradient, iniciály display) + jméno (h2) + e-mail (muted).
- Startovní číslo: slate-800 karta, „STARTOVNÍ ČÍSLO" overline + „147" (display 48) vlevo; „STAV" + „✓ Zaplaceno" (zelená) vpravo.
- Before: `PaperCard` „TVŮJ ODBAVOVACÍ QR" + QR (mock, tmavý čtverec 168 s bílým vzorem) + „Ukaž na prezentaci, pořadatel tě jím odbaví."
- On-route: `PaperCard` „TVOJE AKCE" + řádky Aktivní trasa / Naskenováno „X z N" / Synchronizováno „Ne" (warning).
- Nastavení: `PaperCard`/`Section` „NASTAVENÍ" + řádky: Notifikace (toggle), Hlasové pokyny (toggle), Kontaktovat pořadatele (chevron), Časté dotazy FAQ (chevron/rozbalení).

### 3.4 Native-specific
- Android: reuse `PaperCard`/`Overline`/`KeyValueRow`/`SettingRow`/`FaqRow` (z ui-05), Material3 `Switch`. Avatar `Box` gradient. QR `Canvas` mřížka.
- iOS: nativní `List(.insetGrouped)`/sekce, `Toggle`, `DisclosureGroup` (z ui-06), avatar `Circle` gradient, QR `Canvas`.

---

## 4. IMPLEMENTATION STEPS

### Step 1: Sdílený ProfileMock (+ případně tenký ProfilViewModel)
**Files**: `shared/.../presentation/ProfileMock.kt` (create). Volitelně reuse `OverviewViewModel`/`SettingsViewModel` bez nového VM.
**Done when**: kompiluje.

### Step 2: Android Profil obrazovka + přejmenování tabu
**Files**: `androidApp/.../ui/profil/ProfilScreen.kt` (create; může nahradit `ui/prehled/*` nebo je reusovat), `ui/components/QrGlyph.kt` (create - mock QR), `ui/shell/MainShell.kt` (tab PREHLED → `ProfilScreen`), `ui/shell/Ta33BottomNav.kt` (label „Přehled"→„Profil"). Reuse KeyValueRow/SettingRow/FaqRow.
**Done when**: `@Preview` (before/on-route) + `:androidApp:assembleDebug`; tab „Profil".

### Step 3: iOS Profil obrazovka + přejmenování tabu
**Files**: `iosApp/iosApp/UI/Profil/ProfilView.swift`, `QrGlyphView.swift` (create; může nahradit `UI/Prehled/*`), `UI/Shell/RootView.swift` (tab `.prehled` → `ProfilView`, label „Profil"). Reuse Overview/Settings pozorování (z ui-06 `PrehledModel` nebo nové).
**Done when**: framework link + `xcodebuild`; tab „Profil".

### Step 4: Ověření
`:androidApp:assembleDebug`, `xcodebuild`, `:shared:allTests`.

---

## 5. EDGE CASES & ERRORS
| Scenario | Expected | Handle |
|---|---|---|
| Bez běhu | QR karta místo „Tvoje akce" | `!hasActiveRun` |
| S během | „Tvoje akce" (progress z overview) | `hasActiveRun` |
| organizerContact null | řádek skrytý | `?.let` |
| FAQ prázdné | sekce skrytá | podmínka |
| Notifikace toggle | funkční přes FR-10 | `setNotificationsEnabled` |
| Hlasové pokyny | lokální stav (mock) | rememberSaveable / @State |

## 6. SECURITY CONSIDERATIONS
- Mock identita/číslo/QR - žádná reálná osobní data. Reálná identita/platba/QR = Etapa 2 (pak privacy review).

## 7. ASSUMPTIONS
1. **Identita/číslo/QR/platba = mock** (Etapa 2 data nejsou v logice); vizuál teď.
2. **„Tvoje akce" + Nastavení** z existujících FR-10 VM (OverviewViewModel/SettingsViewModel).
3. **Hlasové pokyny** = jen UI toggle bez logiky (mock).
4. **Tab enum zůstává `PREHLED`**, mění se jen zobrazený **label** na „Profil".
5. Předchozí „Přehled" UI (ui-05/06) se nahradí/rozšíří na plný Profil.

## 8. QUICK REFERENCE
### Files to Create/Modify
- shared: `presentation/ProfileMock.kt`
- android: `ui/profil/ProfilScreen.kt`, `ui/components/QrGlyph.kt`, `ui/shell/{MainShell,Ta33BottomNav}.kt`
- ios: `UI/Profil/{ProfilView,QrGlyphView}.swift`, `UI/Shell/RootView.swift`
### Commands
```bash
./gradlew :androidApp:assembleDebug
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## 9. DESIGN REFERENCE
- MCP claude-design `5105d303-…`: `ui_kits/ta33-app/Screens.jsx` → `ProfilScreen` (+ `QRGlyph`), `Obrazovka 3 - Profil.html` (before/on-route stavy); render `screenshots/obr3-profil.png`.
- `.claude/design/design-system.md`.
### Style Mapping (klíč)
| Prvek | Token |
|---|---|
| startovní číslo karta | slate-800 (`identityBg`/`Ta33Color.slate800`) |
| „147" | display velké | 
| „Zaplaceno" | success (zelená) + fajfka |
| avatar gradient | orange (radial) |
| QR podklad | tmavý slate (#0E1820-ish → token) |
| Notifikace toggle | primary |
| synchronizováno „Ne" | warning |

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before (ui-05/06) | After |
|---|---|---|
| Tab | „Přehled" (Overview+Settings) | „Profil" (identita + číslo + QR/Tvoje akce + Nastavení) |
| Identita/číslo/QR | vynecháno | přidáno (mock) |
| Tab label | „Přehled" | „Profil" |

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-10 | Initial plan created |

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES
### 12.1 Alternatives
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Reuse FR-10 VM + mock identita, plný Profil** | Věrné designu, znovupoužití, minimum nového | Mock identita (Etapa 2 placeholder) | ✅ |
| B. Nechat „Přehled" bez identity | Žádná mock data | Neodpovídá designu (Profil) | - |
| C. Reálná identita/platba teď | Kompletní | Etapa 2 (auth/rezervace/platba) - data neexistují | - |
### 12.2 Open Questions
- [ ] **Mock identita hodnoty** - Proposed: „Jan Novák / 147 / Zaplaceno" dle designu; nahradit reálnými v Etapě 2.
- [ ] **Kontakt akce / FAQ navigace** - Proposed: zobrazit/rozbalit teď; `mailto:`/detail později.
### 12.3 Suggestions & Follow-ups
- Etapa 2: reálná identita, platba (SPD QR), odbavovací QR generování, secure token.
- „Hlasové pokyny" reálná logika (navigace).
