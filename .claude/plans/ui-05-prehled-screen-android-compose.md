# UI-05 — Přehled / Profil (Android Compose)

> **Summary**: Obrazovka tabu „Přehled" (Android Compose) nad FR-10 ViewModely — sekce „Tvoje akce" (aktivní trasa, progress, stav stažení) + „Nastavení" (notifikace toggle, kontakt na pořadatele, FAQ). Nahrazuje stub v app-shellu.

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Tab „Přehled" v app-shellu (ui-03) je zatím `StubScreen`. Logika FR-10 (`OverviewViewModel`, `SettingsViewModel`) je hotová a nespotřebovaná. Potřebujeme reálnou obrazovku: souhrn stavu akce/běhu + nastavení.

### 1.2 Solution Overview
`PrehledScreen` (Compose) přes `koinViewModel()` odebírá `OverviewViewModel.state` (`OverviewUiState`) a `SettingsViewModel.state` (`SettingsUiState`) a vykresluje dvě karty: „Tvoje akce" (aktivní trasa, naskenováno, stav dat akce) a „Nastavení" (přepínač notifikací → `setNotificationsEnabled`, kontakt na pořadatele, rozbalovací FAQ). Reužívá komponenty z ui-01. Nahradí `StubScreen("Přehled")` v `MainShell`.

### 1.3 Scope: What This IS
- Android obrazovka `PrehledScreen` (tab PREHLED) nad `OverviewViewModel` + `SettingsViewModel`.
- Sekce „Tvoje akce": aktivní trasa (`RouteSummary`), progress (`OverviewProgress`), stav stažení (`PreparationStatus`).
- Sekce „Nastavení": funkční přepínač Notifikace, kontakt na pořadatele (`OrganizerContact`), rozbalovací FAQ (`List<FaqItem>`).
- Nová znovupoužitelná komponenta `SettingRow` + rozbalovací FAQ položka.
- Napojení do `MainShell` (tab Přehled).

### 1.4 Scope: What This IS NOT
- **iOS** verze — samostatný plán (ui-06).
- **Etapa 2** prvky z designu: avatar/jméno/e-mail účastníka, **startovní číslo**, stav „Zaplaceno", **odbavovací QR**, přepínač „Hlasové pokyny" — v logice Etapy 1 nejsou (auth/platba/rezervace = Etapa 2). Vynechat + poznámka.
- Skutečná navigace „Kontaktovat pořadatele" do e-mailu/telefonu (Intent) — zobrazit kontakt, akce je drobný follow-up.
- Deník/Mapa obrazovky, scan, Preparation flow.

---

## 2. SUCCESS CRITERIA

| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | `./gradlew :androidApp:assembleDebug` zelený | příkaz |
| 2 | Tab Přehled ukáže `PrehledScreen` místo stubu | běh / preview |
| 3 | „Tvoje akce": aktivní trasa „{name} · {km} km", progress „{c} z {t} kontrol" (jen když běh), stav dat akce dle `PreparationStatus` | preview s fake stavy |
| 4 | Bez aktivního běhu: žádná progress řádka, žádný pád; bez trasy: hint „Zatím žádná aktivní akce" | preview |
| 5 | Přepínač Notifikace odráží `notificationsEnabled` a klik volá `setNotificationsEnabled` | preview + běh |
| 6 | Kontakt na pořadatele (`OrganizerContact.name` + kontakt) se zobrazí | preview |
| 7 | FAQ: seznam otázek, klik rozbalí odpověď (`AnimatedVisibility`) | preview |
| 8 | `loading` (obě VM) → spinner | preview |
| 9 | Žádný hardcoded hex/dp v UI — vše přes theme | code review |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture
```
MainShell tab PREHLED → PrehledScreen
   │  koinViewModel() OverviewViewModel.state : OverviewUiState
   │  koinViewModel() SettingsViewModel.state : SettingsUiState
   ▼
 PrehledContent(overview, settings, onToggleNotifications)
   ├─ Section "Tvoje akce" (PaperCard)
   │    KV řádky: Aktivní trasa | Naskenováno (jen hasActiveRun) | Data akce (syncStatus)
   ├─ Section "Nastavení" (PaperCard)
   │    SettingRow(toggle) Notifikace → onToggleNotifications
   │    SettingRow(nav) Kontaktovat pořadatele  (organizerContact)
   │    FaqRow[] rozbalovací (faq)
```

### 3.2 Key Decisions
| Decision | Choice | Reasoning |
|---|---|---|
| Dva VM | `OverviewViewModel` + `SettingsViewModel` (FR-10) | Přesně dělený zdroj (přehled vs nastavení) |
| Etapa 2 prvky | Vynechat (avatar/číslo/QR/platba) | V logice Etapy 1 nejsou; nefabrikovat data |
| „Synchronizováno" | Mapovat na `syncStatus` (stav offline balíčku) | Výsledky se v Etapě 1 neodesílají; jediný reálný „sync" je stažení balíčku |
| FAQ | Rozbalovací řádky (`AnimatedVisibility`) | Nativní, bez nové obrazovky |
| Kontakt | Zobrazit hodnoty; Intent akce follow-up | Drží obrazovku bez závislosti na navigaci |

---

## 4. IMPLEMENTATION STEPS

> Adresář: `androidApp/src/main/kotlin/com/example/ta33/ui/`.

### Step 1: String resources (cs)
**Files**: `shared/.../composeResources/values/strings.xml` + `values-cs/strings.xml`
Klíče: `prehled_section_event` („Tvoje akce"), `prehled_active_route` („Aktivní trasa"), `prehled_scanned` („Naskenováno"), `prehled_event_data` („Data akce"), `prehled_no_event` („Zatím žádná aktivní akce"), `prehled_section_settings` („Nastavení"), `settings_notifications` („Notifikace"), `settings_contact_organizer` („Kontaktovat pořadatele"), `settings_faq` („Časté dotazy"), stavy stažení `pkg_not_started` („Nestaženo"), `pkg_preparing` („Stahuje se"), `pkg_ready` („Staženo"), `pkg_error` („Chyba stahování"). Build regeneruje `Res`.
**Done when**: klíče dostupné.

### Step 2: `SettingRow` + `KeyValueRow` komponenty
**Files**: `ui/components/Rows.kt` (create)
- `KeyValueRow(label, value, valueColor = fgMuted)` — řádek label vlevo (`bodyStrong` `fgStrong`), value vpravo (`body`), 1px `slate-100` divider mezi řádky (ne u prvního).
- `SettingRow(label, trailing: @Composable)` — label + trailing slot (Switch nebo chevron ikona).
**Done when**: `@Preview`.

### Step 3: `FaqRow` (rozbalovací)
**Files**: `ui/components/FaqRow.kt` (create)
- `FaqRow(item: FaqItem)` — klikací hlavička (otázka `bodyStrong` + chevron rotující), `AnimatedVisibility` s odpovědí (`body` `fgMuted`). Stav `remember { mutableStateOf(false) }`.
**Done when**: `@Preview` rozbalený i sbalený.

### Step 4: Stateless `PrehledContent`
**Files**: `ui/prehled/PrehledContent.kt` (create)
```kotlin
@Composable
fun PrehledContent(
    overview: OverviewUiState,
    settings: SettingsUiState,
    onToggleNotifications: (Boolean) -> Unit,
) {
    if (overview.loading && settings.loading) { Spinner(); return }
    Column(scroll, padding) {
        // Tvoje akce
        PaperCard {
            Overline(stringResource(R.prehled_section_event))
            if (overview.activeRoute != null)
                KeyValueRow(label = R.prehled_active_route,
                    value = "${overview.activeRoute.name} · ${formatKm(overview.activeRoute.distanceKm)} km")
            else Text(R.prehled_no_event, fgMuted)
            overview.progress?.let {
                KeyValueRow(R.prehled_scanned, "${it.collectedCount} z ${it.totalCount} kontrol")
            }
            KeyValueRow(R.prehled_event_data, packageStatusLabel(overview.syncStatus),
                valueColor = if (overview.syncStatus == READY) success else warning)
        }
        // Nastavení
        PaperCard {
            Overline(stringResource(R.prehled_section_settings))
            SettingRow(R.settings_notifications) {
                Switch(checked = settings.notificationsEnabled, onCheckedChange = onToggleNotifications)
            }
            settings.organizerContact?.let {
                SettingRow(R.settings_contact_organizer) { Text(it.name, fgMuted) /* + chevron */ }
            }
            Overline(R.settings_faq)
            settings.faq.forEach { FaqRow(it) }
        }
    }
}
```
`formatKm(Double)` → česká desetinná čárka (např. `33,2`). `packageStatusLabel(PreparationStatus)` → Res string.
**Done when**: `@Preview` pro: plný stav (trasa+běh+READY), bez běhu, bez trasy, loading.

### Step 5: Stateful `PrehledScreen` + napojení do shellu
**Files**: `ui/prehled/PrehledScreen.kt` (create), `ui/shell/MainShell.kt` (modify)
```kotlin
@Composable
fun PrehledScreen(
    overviewVm: OverviewViewModel = koinViewModel(),
    settingsVm: SettingsViewModel = koinViewModel(),
) {
    val overview by overviewVm.state.collectAsStateWithLifecycle()
    val settings by settingsVm.state.collectAsStateWithLifecycle()
    PrehledContent(overview, settings, onToggleNotifications = settingsVm::setNotificationsEnabled)
}
```
V `MainShell`: `TopLevelDestination.PREHLED -> PrehledScreen()` (místo `StubScreen("Přehled")`).
**Done when**: tab Přehled renderuje obrazovku.

### Step 6: Ověření
`./gradlew :androidApp:assembleDebug` + `./gradlew build`; projít previews. Runtime na zařízení na uživateli.

---

## 5. EDGE CASES & ERRORS
| Scenario | Expected | Handle |
|---|---|---|
| `activeRoute == null` | Hint „Zatím žádná aktivní akce" | podmínka |
| `progress == null` (žádný běh) | Řádka Naskenováno se nevykreslí | `?.let` |
| `faq` prázdné | Sekce FAQ bez řádků (skrýt nadpis) | podmínka |
| `organizerContact == null` | Řádka kontaktu skrytá | `?.let` |
| obě VM loading | spinner | `overview.loading && settings.loading` |
| `distanceKm` formát | „33,2 km" (čárka) | `formatKm` locale-independent replace '.'→',' |

## 6. SECURITY CONSIDERATIONS
- Kontakt na pořadatele je veřejný. Žádná citlivá data. Nelogovat.

## 7. ASSUMPTIONS
1. **Etapa 2 prvky vynechány** (avatar/jméno/startovní číslo/Zaplaceno/QR/hlasové pokyny) — v logice Etapy 1 nejsou; design je Etapa 2.
2. **`OverviewViewModel`/`SettingsViewModel` bez `bind()`** — startují samy v `init` (ověřeno). Jen `koinViewModel()`.
3. **`setNotificationsEnabled` je jediný zapisovací intent** v nastavení; ostatní řádky jsou zobrazení/nav.
4. **„Synchronizováno" = stav offline balíčku** (`syncStatus`), ne upload výsledků (ten je Etapa 2).

## 8. QUICK REFERENCE
### Files to Create
- `ui/components/Rows.kt`, `ui/components/FaqRow.kt`
- `ui/prehled/PrehledContent.kt`, `ui/prehled/PrehledScreen.kt`
### Files to Modify
- `ui/shell/MainShell.kt` — tab PREHLED → `PrehledScreen`
- `shared/.../composeResources/values{,-cs}/strings.xml`
### Commands
```bash
./gradlew :androidApp:assembleDebug
```

## 9. DESIGN REFERENCE
- `ui_kits/ta33-app/Screens.jsx::ProfilScreen` (sekce „Tvoje akce" + „Nastavení"); `.claude/design/design-system.md`.
- Etapa 2 části (identity/číslo/QR) v designu jsou přítomné, ale mimo Etapu 1.
### Style Mapping
| Design | Code | Value |
|---|---|---|
| karta | `PaperCard` (`colorScheme.surface`) | #FFFFFF |
| nadpis sekce | `Overline` (`labelSmall`) | 13/700 UPPER |
| label řádku | `bodyStrong`/`titleMedium` `fgStrong` | |
| value | `body` `fgMuted` | |
| stav READY | `Ta33Theme.colors.success` | #1FA85A |
| stav nestaženo/chyba | `Ta33Theme.colors.warning`/`error` | #E8A92A / #D63A2F |
| přepínač | Material3 `Switch` (thumb/track z colorScheme) | primary |
| divider | `Ta33Theme.colors` slate-100 (`colorScheme.surfaceVariant`) | #E6EAEC |

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before | After |
|---|---|---|
| Tab Přehled | `StubScreen("Přehled")` | `PrehledScreen()` |

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-09 | Initial plan created |

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES
### 12.1 Alternatives
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Overview + Settings karty, Etapa 2 prvky vynechány** | Věrné hotové logice, žádná fabrikace | Vizuálně chudší než design mockup | ✅ |
| B. Zahrnout i identity/číslo/QR jako placeholdery | Blíž mockupu | Fabrikace neexistujících dat, mate | — |
| C. Sloučit do jedné karty | Méně kódu | Míchá přehled a nastavení, hůř čitelné | — |
### 12.2 Open Questions
- [ ] **Kontakt na pořadatele — akce?** — Proposed: teď zobrazit; `Intent.ACTION_SENDTO`/`DIAL` jako drobný follow-up.
- [ ] **FAQ — inline rozbalení vs. samostatná obrazovka?** — Proposed: inline rozbalení (méně navigace); revidovat, když FAQ naroste.
### 12.3 Suggestions & Follow-ups
- iOS Přehled (ui-06) — nativní `List`/`Form`, `Toggle`, `DisclosureGroup`.
- Etapa 2: identity/startovní číslo/odbavovací QR/platba, hlasové pokyny.
- Intent akce pro kontakt (e-mail/telefon/web).
