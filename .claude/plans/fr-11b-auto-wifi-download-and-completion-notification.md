# Automatické stažení dat akce na Wi-Fi (foreground) + notifikace na dokončení

> **Summary**: Na Wi-Fi spustit stažení dat akce/mapy automaticky bez tapu, dokud je app otevřená (a obnovit při dalším otevření, když uživatel odejde); mimo Wi-Fi jen po explicitním potvrzení; po dokončení lokální notifikace (respektuje in-app „Notifikace" toggle + OS oprávnění).

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Dnes musí uživatel stažení dat akce vždy odstartovat tapnutím na „Stáhnout data akce". Chceme, aby se na Wi-Fi stáhlo samo (bez tapu), dokud má app otevřenou. Jediné, čemu se vyhýbáme, je stahování mimo Wi-Fi bez potvrzení (mobilní data stojí peníze). Po dokončení má přijít notifikace, že je vše připravené offline.

### 1.2 Solution Overview
Přidáme **auto-start politiku** do `DownloadViewModel`: když data nejsou stažená (readiness != READY) a zařízení je na **Wi-Fi**, stažení se spustí samo (foreground). Když uživatel appku zavře/odejde uprostřed, `viewModelScope` se zruší, stažení se pozastaví a **automaticky se obnoví při dalším otevření** (na Wi-Fi) - `PrepareOfflinePackageUseCase` je resumovatelný (offset/append). Na **mobilních datech** se nikdy nespustí samo (zůstává explicitní CTA jako potvrzení). Po dokončení pošleme přes nový expect/actual `Notifier` **lokální notifikaci** (jen když je in-app „Notifikace" ON a OS oprávnění uděleno). **Žádný skutečný OS background** (rozhodnutí uživatele - varianta A).

### 1.3 Scope: What This IS
- **Auto-start na Wi-Fi** (foreground): readiness != READY & currentNetworkType == WIFI & overallStatus == IDLE → start bez tapu.
- **Auto-resume** auto-pozastaveného stažení (ztráta a návrat Wi-Fi); uživatelská pauza se neobnovuje.
- **Obnovení při dalším otevření** (na Wi-Fi) po zavření uprostřed - stažení se dotáhne (resumovatelné).
- **Notifikace na dokončení**: nový `Notifier` (expect/actual, iOS `UNUserNotificationCenter`, Android `NotificationManagerCompat` + kanál); gate na in-app „Notifikace" ON + OS oprávnění; notifikace přijde i ve foregroundu (iOS banner).
- **OS oprávnění**: iOS UN authorization + Android 13+ `POST_NOTIFICATIONS` (request při prvním stahování z UI vrstvy).

### 1.4 Scope: What This IS NOT
- **Žádný skutečný OS background** (WorkManager / BGTask / background URLSession / entitlements) - varianta A, foreground only.
- Žádné auto-stahování na mobilních datech (vždy jen s potvrzením).
- Žádná změna vzhledu download karty (UI-13) kromě toho, že na Wi-Fi rovnou naskočí progress místo idle CTA.
- Žádné org/push notifikace ze serveru (jen lokální „staženo").
- Žádná změna GPS/tracking.

---

## 2. SUCCESS CRITERIA

| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | Na Wi-Fi + not-ready se stažení spustí **samo bez tapu** | simulátor: not-ready na Wi-Fi → progress běží bez interakce |
| 2 | Na mobilních datech se **nikdy** nespustí samo (zůstává „Čeká na Wi-Fi" / CTA) | simulace CELLULAR → žádný auto-start |
| 3 | Po vypnutí „Jen přes Wi-Fi" + tapu CTA se stáhne i na datech (potvrzení) | běh |
| 4 | Připojení Wi-Fi během not-ready → auto-start / auto-resume | přepnout NONE/CELLULAR → WIFI |
| 5 | Odejití uprostřed → při dalším otevření na Wi-Fi se stažení dotáhne (resume) | zavřít + znovu otevřít |
| 6 | Po dokončení notifikace, když in-app „Notifikace" ON + OS oprávnění | běh |
| 7 | In-app „Notifikace" OFF → žádná notifikace | běh |
| 8 | Bez OS oprávnění stažení proběhne, jen bez notifikace (žádný pád) | běh |
| 9 | `:androidApp:assembleDebug` + `xcodebuild` (iOS 18 i 26) + `:shared:allTests` zelené | příkazy (§8) |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

```
  ┌───────────────┐  observe   ┌────────────────────────────┐
  │ Connectivity  │───────────▶│ DownloadViewModel           │
  │ Monitor       │            │  auto-start: WIFI & !READY   │ start()
  └───────────────┘            │              & IDLE          │──────┐
  ┌───────────────┐  observe   │  auto-resume: WIFI & PAUSED  │      ▼
  │ Preparation   │───────────▶│              & pausedByNetwork│  ┌──────────────────────┐
  └───────────────┘            │  on DONE + notifOn + perm →  │  │ PrepareOfflinePackage │
  ┌───────────────┐  observe   │      Notifier.notify(...)     │  │ UseCase (Ktor, resume)│
  │ NotifEnabled  │───────────▶│                              │  └──────────────────────┘
  └───────────────┘            └───────────────┬──────────────┘
                                               ▼  gate: „Notifikace" ON + OS perm
                                    ┌────────────────────┐
                                    │ Notifier (e/actual) │  iOS UNUserNotificationCenter
                                    └────────────────────┘  Android NotificationManagerCompat
```
Foreground only: běží ve `viewModelScope`. Zavření appky = pauza; další otevření na Wi-Fi = auto-start/resume (resumovatelný use-case). Žádný background runner.

### 3.2 Auto-start politika (`DownloadViewModel`)
- **Auto-start** když `preparation.status != READY` && `currentNetworkType == WIFI` && `overallStatus == IDLE`.
- **Auto-resume** když se síť stane `WIFI`, stav je `PAUSED` a `pausedByNetwork == true` (auto-pauza ze ztráty sítě). Uživatelská pauza (`pause()`) nastaví `pausedByNetwork = false` → neobnovuje se.
- **Mobilní data**: klíčem je `currentNetworkType == WIFI` (ne `networkAllows`), takže i při `WIFI_AND_CELLULAR` se auto nespustí na datech.
- `maybeAutoStart()` voláno z connectivity i preparation observeru.

### 3.3 Notifier (expect/actual)
`commonMain`:
```kotlin
interface Notifier {
    /** Posts a local "download complete" notification. No-op if OS permission not granted. */
    fun notifyDownloadComplete(title: String, body: String)
}
```
- **Android** (`AndroidNotifier(context)`): kanál „downloads" (O+), `NotificationManagerCompat.notify`; při Android 13+ kontrola `POST_NOTIFICATIONS` (jinak no-op). Malá ikona = systémová `stat_sys_download_done` (bez nového assetu).
- **iOS** (`IosNotifier`): `UNUserNotificationCenter` + `UNNotificationRequest` (immediate); iOS tiše zahodí, když neautorizováno.
- Registrace v `platformModule` (android/ios) jako `single<Notifier>`.

### 3.4 Notifikace na dokončení
V `launchDownload().onEach`: při přechodu `overallStatus → DONE` (jednou, flag `lastNotifiedDone`) a `notificationsEnabled == true` → `notifier.notifyDownloadComplete("Data akce stažena", "Trasa, kontroly a mapa jsou připravené offline.")`. `notificationsEnabled` sleduje `ObserveNotificationsEnabledUseCase` (FR-10).

### 3.5 OS oprávnění (request v UI vrstvě)
- **Android**: `rememberLauncherForActivityResult(RequestPermission)` v `MainShell` (nebo download větvi) - spustit `POST_NOTIFICATIONS` request, když stažení začne (Android 13+, dosud neuděleno). Manifest `<uses-permission POST_NOTIFICATIONS>`.
- **iOS**: `UNUserNotificationCenter.requestAuthorization` z `DownloadCardView.task` (když se karta objeví). Foreground banner: `UNUserNotificationCenterDelegate.willPresent` → `[.banner]` (nastaveno v `iOSApp.swift`).

### 3.6 Key Decisions
| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Model | Foreground only (varianta A) | Uživatelské rozhodnutí; 84 MB na Wi-Fi doběhne rychle; iOS/Ktor background by byl overkill |
| Auto-start jen na WIFI | `currentNetworkType == WIFI` | „mimo Wi-Fi jen s potvrzením" |
| Rozlišení auto-pauzy | `pausedByNetwork` flag | Aby auto-resume neobešel uživatelovu pauzu |
| Notifikace gating | in-app „Notifikace" ON + OS oprávnění | Ctí uživatelovu volbu |
| Notifier | expect/actual přes `platformModule` | KMP idiom |
| Permission request | v UI vrstvě (Android launcher / iOS `.task`) | Android request vyžaduje Activity/Compose launcher; VM prompt nejde |

---

## 4. IMPLEMENTATION STEPS

### Step 1: `Notifier` expect/actual + DI
- [x] **Status**: Done
**Goal**: platformní posílání lokální notifikace.
**Files**: `shared/.../core/Notifier.kt` (commonMain interface), `shared/.../core/Notifier.android.kt`, `shared/.../core/Notifier.ios.kt`, `di/PlatformModule.{android,ios}.kt`
- Android: kanál + `NotificationManagerCompat`; kontrola `POST_NOTIFICATIONS` (33+). iOS: `UNUserNotificationCenter`.
- `single<Notifier> { AndroidNotifier(androidContext()) }` / `single<Notifier> { IosNotifier() }`.
**Done when**: kompiluje na obou; DI poskytuje `Notifier`.

---

### Step 2: Auto-start / auto-resume politika + notifikace v `DownloadViewModel`
- [x] **Status**: Done
**Goal**: na Wi-Fi start/resume bez tapu; po DONE notifikace (gated).
**Files**: `shared/.../presentation/DownloadViewModel.kt`, `di/AppModule.kt` (factory args)
- Přidat `pausedByNetwork`, `notificationsEnabled`, `lastNotifiedDone` (pole).
- `pause()` = user → `pausedByNetwork = false`; nový `autoPause()` → `pausedByNetwork = true`; sdílený `doPause()`.
- Connectivity `onEach`: `if (blockedByNetwork) autoPause() else maybeAutoStart()`.
- Preparation `onEach`: po update `maybeAutoStart()`.
- `maybeAutoStart()`: `preparation != READY && currentNetworkType == WIFI` → IDLE `start()`, PAUSED&pausedByNetwork `resume()`.
- `observeNotificationsEnabled().onEach { notificationsEnabled = it }`.
- V `launchDownload().onEach { p -> ...; if (p.overallStatus == DONE && !lastNotifiedDone && notificationsEnabled) { notifier.notifyDownloadComplete(...); lastNotifiedDone = true }; if (p.overallStatus != DONE) lastNotifiedDone = false }`.
- Inject `Notifier` + `ObserveNotificationsEnabledUseCase`; `AppModule`: `factory { DownloadViewModel(get(), get(), get(), get(), get()) }`.
**Done when**: `:shared:allTests` zelené; not-ready na WIFI simulátoru → progress bez tapu.

---

### Step 3: Android - manifest + permission request
- [x] **Status**: Done
**Goal**: `POST_NOTIFICATIONS` na Android 13+.
**Files**: `androidApp/src/main/AndroidManifest.xml`, `androidApp/.../ui/shell/MainShell.kt`
- Manifest: `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`.
- MainShell: `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`; `LaunchedEffect(download.overallStatus)` - když start & `Build.VERSION.SDK_INT >= 33` & permission not granted → `launch(POST_NOTIFICATIONS)`.
**Done when**: `:androidApp:assembleDebug` OK; při prvním stahování prompt (13+).

---

### Step 4: iOS - permission + foreground banner
- [x] **Status**: Done
**Goal**: UN authorization + banner ve foregroundu.
**Files**: `iosApp/iosApp/iOSApp.swift` (UN delegate `willPresent` → `.banner`), `iosApp/iosApp/UI/Preparation/DownloadCardView.swift` (`.task` request authorization)
**Done when**: `xcodebuild` OK; karta se objeví → prompt; dokončení → banner i ve foregroundu.

---

### Step 5: Build & ověření
- [x] **Status**: Done
**Files**: -
- `./gradlew :androidApp:assembleDebug`, `:shared:allTests`, `xcodebuild` iOS 18+26.
- iOS simulátor (Wi-Fi): dočasně vypnout DevSeed → not-ready na Wi-Fi → auto-start progress bez tapu; ověřit; DevSeed vrátit. Notifikaci ověřit (permission + dokončení).
**Done when**: kritéria §2.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected | Handle |
|---|---|---|
| Not-ready, WIFI, app otevřená | auto-start bez tapu | politika §3.2 |
| Not-ready, CELLULAR | žádný auto-start; „Čeká na Wi-Fi"/CTA | `currentNetworkType == WIFI` guard |
| Ztráta Wi-Fi během stahování | auto-pauza (`pausedByNetwork=true`) | `autoPause()` |
| Návrat Wi-Fi po auto-pauze | auto-resume | `maybeAutoStart()` |
| Uživatel manuálně pauzne | žádný auto-resume | `pause()` → `pausedByNetwork=false` |
| Zavření uprostřed + znovu otevření na Wi-Fi | stažení se dotáhne | resumovatelný use-case + auto-start |
| Dokončení + „Notifikace" ON + oprávnění | notifikace (i ve foregroundu) | gate §3.4 + iOS willPresent |
| „Notifikace" OFF | žádná notifikace | `notificationsEnabled` gate |
| Bez OS oprávnění | stažení OK, bez notifikace | Notifier no-op |
| Opakované dokončení (relaunch DONE) | notifikace jen jednou | `lastNotifiedDone` |
| Chyba stažení | ERROR, žádná „hotovo" notifikace; CTA „Zkusit znovu" | stávající error stav |

---

## 6. SECURITY CONSIDERATIONS
- **Mobilní data nikdy bez potvrzení** (finanční dopad) - auto-start jen na WIFI.
- **Notifikace** vyžaduje OS souhlas; nikdy neobcházet; text bez osobních dat.
- **Logging**: nelogovat obsah; jen stav.

---

## 7. ASSUMPTIONS
1. **Rozhodnuto uživatelem**: (a) **varianta A - jen foreground** (žádný OS background); (b) notifikace respektuje in-app „Notifikace" toggle; (c) oprávnění request při prvním stahování; (d) notifikace i ve foregroundu.
2. **Auto-start jen na WIFI** (ne CELLULAR ani při WIFI_AND_CELLULAR).
3. **`PrepareOfflinePackageUseCase` je resumovatelný** (offset/append) - obnovení po znovuotevření dotáhne stažení.
4. **In-app „Notifikace" default ON** (FR-10).
5. **Android permission request z Compose launcheru** (Activity/launcher), ne z VM.

> Otevřené otázky viz §12.

---

## 8. QUICK REFERENCE

### Files to Create
- `shared/.../core/Notifier.kt` (+ `.android.kt`, `.ios.kt`)

### Files to Modify
- `shared/.../presentation/DownloadViewModel.kt`, `di/AppModule.kt`, `di/PlatformModule.{android,ios}.kt`
- Android: `AndroidManifest.xml`, `ui/shell/MainShell.kt`
- iOS: `iOSApp.swift`, `UI/Preparation/DownloadCardView.swift`

### Dependencies
- Android: `androidx.core:core-ktx` (už v projektu - `NotificationManagerCompat`)

### Commands
```bash
./gradlew :androidApp:assembleDebug
./gradlew :shared:allTests
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'id=<sim>' CODE_SIGNING_ALLOWED=NO build
```

---

## 9. DESIGN REFERENCE
### Visual Spec
Download karta (UI-13) beze změny vzhledu; na Wi-Fi rovnou naskočí stav „downloading" (progress) místo idle CTA. Notifikace = systémová, text česky bez em-dash: „Data akce stažena" / „Trasa, kontroly a mapa jsou připravené offline."
### Style Mapping
| Prvek | Code | Value |
|---|---|---|
| Progress | `ProgressView`/`LinearProgressIndicator` | brand orange |
| Notifikace ikona (Android) | `stat_sys_download_done` | systémová |

---

## 10. CORRECTIONS FROM CURRENT STATE
| What | Before | After |
|---|---|---|
| Start na Wi-Fi | Nutný tap na „Stáhnout data akce" | Auto-start bez tapu (WIFI) |
| Zavření uprostřed | Stažení stojí, uživatel musí znovu tapnout | Auto-obnoví se při dalším otevření (Wi-Fi) |
| Dokončení | Tichý přechod na obsah | + lokální notifikace (gated) |
| Mobilní data | CTA/toggle | Beze změny (vždy potvrzení) |

---

## 11. CHANGELOG
| Date | Change |
|---|---|
| 2026-07-12 | Initial plan created (auto-download + notifikace, Fáze A + B OS background) |
| 2026-07-12 | SCOPE_REMOVAL + REQUIREMENT_CHANGE: varianta A - jen foreground auto-start (zrušena celá Fáze B: WorkManager, iOS BGTask/background URLSession, entitlements, foreground service). Kritérium „pokračuje po zavření" → „obnoví se při dalším otevření". Vyřešeno OQ1 (netýká se), OQ2 ✅, OQ3 N/A, OQ4 ✅. Steps 7→5. |
| 2026-07-12 | Implementation: Steps 1-5 hotové. `Notifier` interface + `AndroidNotifier`/`IosNotifier` + DI; `DownloadViewModel` auto-start/auto-resume (jen WIFI) + gated notifikace na DONE (`pausedByNetwork`, `lastNotifiedDone`, `notificationsEnabled`); Android `POST_NOTIFICATIONS` (manifest + Compose launcher v MainShell); iOS UN authorization (DownloadCardView.task) + foreground banner (AppDelegate v iOSApp). Ověřeno: `:shared:allTests` + Android assembleDebug + iOS build (18/26) zelené; na iOS 26 simulátoru potvrzen auto-start bez tapu (not-ready na Wi-Fi → rovnou download pokus) + prompt na notifikace. Completion notifikace nešla plně ověřit bez reálného content serveru. |

---

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES

### 12.1 Alternative Approaches Considered
| Approach | Pros | Cons | Selected? |
|---|---|---|---|
| **A. Foreground auto-start + resume + gated notifikace** | Splňuje jádro; nízkorizikové; ověřitelné na simulátoru; bez iOS/Ktor background problému | Nedotáhne stažení po zavření (ale obnoví při dalším otevření) | ✅ |
| B. + OS background (WorkManager/BGTask/bg URLSession) | Dotáhne i po zavření | Terénně náročné; iOS Ktor background overkill pro 84 MB | - (možný pozdější upgrade) |
| C. Auto-start dle `networkAllows` (i CELLULAR) | Méně stavů | Stahování na datech bez tapu = proti zadání | - |

**Why the selected approach won**: Uživatel zvolil variantu A. Pro jednorázový ~84 MB balíček (na Wi-Fi rychlý) je foreground auto-start + resume dostatečný a bez rizik nativního backgroundu.

### 12.2 Open Questions
- [ ] **`single` pro `DownloadViewModel`?** - Proposed: rozhodnout u implementace; UI-13 shell už drží jednu instanci, sdílení stavu auto-startu by bylo čistší. Ponecháno otevřené.
- [ ] **Přesná Android permission timing** - Proposed: request při prvním `overallStatus == DOWNLOADING`; přijatelné i „při zobrazení download karty".

### 12.3 Suggestions & Follow-ups
- (Odloženo) skutečný OS background (varianta B) - pokud terénní test ukáže potřebu dotahovat po zavření.
- Retry/backoff politika při síťových výpadcích (Ktor download).
- Sjednotit „Notifikace" setting s reálnými org push notifikacemi (Etapa 2).
