# TA33 - Technický stack (Kotlin Multiplatform, Alza-style)

**Přístup:** KMP se sdíleným jádrem + nativní UI vrstvou (SwiftUI na iOS, Jetpack Compose na Androidu). Sdílí se prakticky vše kromě view vrstvy, včetně ViewModelů.

**Dvojí cíl projektu:** (1) dodat funkční aplikaci TA33, (2) naučit se KMP a technologie žádané na trhu práce. Stack je proto volený tak, aby maximálně pokrýval dovednosti z reálných inzerátů (Kotlin, KMP/CMP, MVVM, Clean Architecture, Ktor, SQLDelight, Koin, Coroutines, unit testy, základy SwiftUI).

Tento dokument je první verze a bude se doplňovat, jak přijdou další doporučení.

---

## 1. Architektura

- **Clean Architecture** ve třech vrstvách: data (zdroje, repozitáře), domain (use-case, modely, business pravidla), presentation (ViewModely). Domain nezná platformu ani frameworky.
- **MVVM se sdílenými ViewModely** v `commonMain`. ViewModel drží stav a logiku obrazovky, nativní UI (SwiftUI / Compose) jen vykresluje stav a posílá události. Tím je zdvojená jen tenká view vrstva.
- **Unidirekční tok stavu (MVI-lite):** ViewModel vystavuje jeden `StateFlow<UiState>` a přijímá `events`. Odpovídá to požadavku „state management" z inzerátů (v Kotlin světě je ekvivalent BLoC/Provider právě StateFlow + MVI, ne knihovna).
- **Modularizace:** oddělené moduly (např. `core`, `data`, `domain`, `feature-denik`, `feature-mapa`, `feature-sken`, `feature-profil`). I pro sólo projekt to učí strukturu, kterou inzeráty jmenují (modularizace + separation of concerns + SOLID).

---

## 2. Sdílené jádro (`commonMain`) - KMP knihovny

| Oblast | Volba | Proč |
|---|---|---|
| Jazyk | **Kotlin** | jádro celého projektu, hlavní žádaná dovednost |
| Asynchronní kód | **Coroutines + Flow / StateFlow** | standard pro async a reaktivní stav; přímo v inzerátech |
| Serializace | **kotlinx.serialization** | KMP-nativní, ladí s Ktorem |
| Síť / REST | **Ktor Client** | KMP-nativní HTTP klient (Retrofit je jen Android, proto ne) |
| Offline data | **SQLDelight** | typované SQL, výborné pro offline-first a synchronizační frontu, silná KMP dovednost |
| Dependency injection | **Koin** | KMP-nativní DI (Hilt/Dagger jsou jen Android, proto ne) |
| ViewModely | **AndroidX Lifecycle ViewModel (multiplatform)** vystavující `StateFlow` | sdílené v `commonMain`, konzumované Compose i SwiftUI |
| Interop Kotlin ↔ Swift | **SKIE (Touchlab)** | zpříjemní volání `suspend` funkcí a `Flow` ze Swiftu; klíčové pro Alza-style |
| Logování | **Napier** nebo Kermit | jednoduché multiplatformní logování |

---

## 3. UI vrstva (nativní, per platforma)

- **Android:** Jetpack Compose + **Material3**. Pokrývá požadavky „Jetpack Compose", „Material3", „Android lifecycle".
- **iOS:** **SwiftUI**. Pokrývá požadavek „základy SwiftUI a ochota se rozvíjet" - přesně ta dovednost, kterou Alza-style přidává navíc oproti čistému Compose Multiplatform.
- **Napojení UI na ViewModel:** obě UI vrstvy odebírají `StateFlow<UiState>` ze sdíleného ViewModelu (na iOS přes SKIE) a posílají zpět události.
- **Navigace:** pro první verzi nativní navigace per platforma (SwiftUI `NavigationStack`, Compose Navigation). Sdílená navigace přes **Decompose** je možné rozšíření (víc sdílení, ale i víc složitosti) - nechat na později, ne do v1.

---

## 4. Projektově specifické části (přes expect/actual nebo nativně)

Tyhle věci KMP nesdílí kompletně, řeší se platformním kódem. Jsou to zároveň přesně ty „AI-neurychlitelné" části z odhadu.

| Funkce | Android | iOS | Poznámka |
|---|---|---|---|
| Offline mapa + trasa/body/poloha | **MapLibre Native** (Android SDK) | **MapLibre Native** (iOS SDK) | offline dlaždice (MBTiles/vektor), trasa a kontroly jako vrstvy; jednotné řešení na obou |
| GPS poloha / geofencing (sběr kontrol do ~50 m) | Fused Location / `LocationManager` | CoreLocation | `expect/actual` API nad sdílenou logikou; ošetřit přesnost ve skalách |
| Běh na pozadí (dlouhý tracking) | foreground service + úsporné režimy výrobců | background location modes | platformně citlivé, hodně terénního testu |
| Kamera / QR sken (start a cíl) | CameraX + ML Kit Barcode | AVFoundation / Vision | jen start a cíl (časomíra), ne kontroly |
| Offline synchronizace | vlastní fronta nad SQLDelight + Coroutines + sledování konektivity | (sdíleno) | Etapa 1 = pouze lokální ukládání do telefonu; odesílání na server až Etapa 2 |
| **Generování QR (Etapa 2)** | `qrcode-kotlin` (multiplatformní) nebo platformně | (sdíleno) | platební QR (FR-12) a odbavovací QR (FR-13); u platby český formát **SPD „QR Platba"** (IBAN, částka, VS). **Etapa 2, nespěchá.** |
| **Bezpečné úložiště tokenu (Etapa 2)** | Android Keystore | iOS Keychain | přes `expect/actual` nebo `multiplatform-settings`; uložení přihlašovacího tokenu/session z rezervačního systému (FR-15). **Etapa 2, nespěchá.** |

> Poznámka: generování QR i bezpečné úložiště tokenu patří k funkcím **Etapy 2** (platba, odbavení, přihlášení). Do Etapy 1 (letošní ročník) nejsou potřeba, takže se řeší až po spuštění rezervačního systému. V Etapě 1 se QR jen **skenuje** (start a cíl), negeneruje.

---

## 5. Testování

- **Unit testy sdíleného jádra** (`commonTest`): domain logika, use-case, ViewModely, mapování dat. Velká výhoda KMP - napíšeš jednou, platí pro obě platformy. Pokrývá požadavek „unit testing".
- **UI testy per platforma:** Compose UI testy (Android), XCTest / XCUITest (iOS). To je ekvivalent „widget testů" z Flutter inzerátu.
- Cíl: solidní pokrytí domény a ViewModelů, UI testy na klíčové obrazovky.

---

## 6. Nástroje a workflow

- **Android Studio** (KMP plugin) + **Xcode** (nutný pro iOS build a SwiftUI).
- **Git** + code review workflow (i sólo se vyplatí PR + review návyk, inzeráty ho jmenují).
- **Figma** pro design handoff z prototypu (inzeráty jmenují).
- **AI nástroje ve vývoji** - vědomě používat a umět popsat jak (inzeráty to opakovaně chtějí); je to i tvoje konkurenční výhoda.
- **CI/CD** (základ): build a testy na push (např. GitHub Actions). Nice-to-have, ne nutnost pro v1.

---

## 7. Co jsem záměrně vyřadil a proč

| Doporučení z inzerátů | Verdikt | Důvod |
|---|---|---|
| **Retrofit** | vyřazeno | jen Android; sdílené jádro potřebuje KMP klienta → Ktor |
| **Room** | alternativa | Room už má KMP podporu, ale volím SQLDelight (typované SQL, lepší kontrola nad offline frontou); Room lze zvážit, pokud bys chtěl Jetpack ekosystém |
| **Hilt / Dagger** | vyřazeno | jen Android; DI ve sdíleném jádru → Koin |
| **BLoC / Provider** | nepoužitelné | Flutter/Dart koncept; ekvivalent v Kotlinu je StateFlow + MVI |
| **widget testy** | přeloženo | Flutter pojem; u nás Compose UI testy + XCTest |
| **Compose Multiplatform (sdílené UI)** | vědomě NE | dobrá a legitimní cesta, ale zvolili jsme Alza-style (nativní UI), protože přidává SwiftUI dovednost, kterou trh chce |

> **Upřesnění (Compose _UI_ vs Compose _resources_):** „vědomě NE" se týká sdíleného **renderování UI**. Naproti tomu **Compose resources** (jen načítání lokalizačních stringů a assetů, `Res.string.x`) se v projektu **používají a sdílejí** - je to úzká závislost, která nevynucuje sdílené UI. Android čte stringy přímo přes `stringResource(...)`, iOS/SwiftUI přes sdílenou Kotlin funkci obalující non-`@Composable` `getString(Res.string.x)`. Detaily viz §12 → Localization.

---

## 8. Mapa stacku na požadavky z inzerátů (zaměstnatelnost)

| Požadavek z inzerátů | Kde se ho v projektu naučíš |
|---|---|
| Kotlin do hloubky | celé sdílené jádro |
| KMP / CMP | celá architektura projektu |
| SwiftUI (základy) | nativní iOS UI vrstva |
| Jetpack Compose + Material3 | nativní Android UI vrstva |
| MVVM | sdílené ViewModely |
| Clean Architecture + modularizace | struktura projektu |
| SOLID, separation of concerns, clean code | napříč vrstvami |
| REST API (Ktor) | síťová vrstva (Etapa 2) |
| Offline data (SQLDelight) | offline-first jádro (Etapa 1) |
| Coroutines / async | napříč jádrem |
| DI (Koin) | dependency injection |
| Unit testing | testy sdíleného jádra |
| State management | StateFlow / MVI ve ViewModelech |
| Git + code review | vývojový workflow |
| AI nástroje | vývojový workflow |
| Figma | design z prototypu |
| Angličtina (dokumentace) | práce s KMP/Apple dokumentací |

---

## 9. Poznámky a rizika

- **SwiftUI za pochodu.** Největší riziko Alza-style pro tebe je učení SwiftUI během projektu. Je to vědomá investice do zaměstnatelnosti; termín 19. 9. je proto druhořadý.
- **Začni od sdíleného jádra.** Data, síť, offline, ViewModely jsou identické bez ohledu na UI. Postav je první, získáš tempo v Kotlinu a odložíš rozhodování o detailech UI.
- **Offline mapy jsou nejtěžší část.** MapLibre + offline dlaždice + GPS ve skalních kaňonech je technicky nejnáročnější a nejrizikovější; počítej s terénním testováním.
- **Ověřuj aktuální verze knihoven.** KMP ekosystém se rychle vyvíjí; verze a KMP kompatibilitu (hlavně multiplatform ViewModel, SKIE, MapLibre KMP obálky) si potvrď před začátkem.

---

## 10. Distribuce obsahu a backend

- **Distribuce obsahu (trasa, kontroly, metadata) - Etapa 1:** statický **JSON hostovaný externě** (Google Disk nebo podobné), aplikace ho stahuje přes Ktor Client. Read-only, snadno zaměnitelný bez zásahu do aplikace; finální data (GPS souřadnice kontrol, trasa) dodá zadavatel, pro vývoj stačí dočasný soubor. Týká se FR-03, FR-08, FR-11.
- **Backend pro synchronizaci výsledků - až Etapa 2.** V Etapě 1 se výsledky **nikam neodesílají**: v cíli stačí ukázat aplikaci a pořadatel splnění potvrdí lidsky. Data zůstávají jen v telefonu (lokálně). Vlastní server (a tím i backend) přichází až s Etapou 2.
  - Pro Etapu 2 doporučeno **Ktor Server (Kotlin)** - stejný jazyk jako zbytek, možnost **sdílet serializační modely** mezi aplikací a backendem, a full-stack Kotlin je bonus do CV.
  - Minimální perzistence (Postgres nebo SQLite), jednoduchý hosting (Railway / Fly.io / malé VPS). Oddělené od rezervačního systému.
- **Důsledek pro Etapu 1:** žádný server, žádný hosting, žádné provozní náklady na backend letos. Zjednodušuje to Etapu 1 i její nasazení.

---

## 11. Co dodělat před / na začátku implementace

Poznámkový checklist stavu, ať je jasné, co ještě není hotové (nic z toho neblokuje založení projektu a stavbu sdíleného jádra):

- **Datový / doménový model** - navrhne se jednoduchý rozšiřitelný základ (Účastník, Trasa, Kontrola vč. GPS a poloměru, Sebrání, Časy, stav synchronizace), na kterém se dál staví podle nových požadavků. Z něj vyplyne SQLDelight schéma.
- **Design tokeny z prototypu** - barvy, fonty, rozměry a komponenty se vytáhnou z uloženého prototypu na začátku implementace (zvlášť pro Compose a SwiftUI). Logo a texty dodá pořadatel.
- **Statický JSON s obsahem** - připravit dočasný soubor s trasou a kontrolami a uložit externě (viz sekce 10).
- **Offline mapové dlaždice** - vygenerovat pro oblast Adršpach/Teplice (OSM přes planetiler/tilemaker nebo tile provider).
- **Dev účty a testovací zařízení** - Apple Developer účet (pořadatele) a fyzické iOS + Android telefony pro terénní GPS test; emulátor GPS/pozadí nezasimuluje.

---

## 12. Operativní kontrakt pro AI příkazy (`.claude/commands`)

> Tato sekce je **strojově čtený kontrakt** pro příkazy `/plan-create`, `/plan-implement`, `/plan-update` a `/translate`. Sekce 1-11 popisují *cílový* stack a záměr; tato sekce popisuje *konkrétní hodnoty a příkazy platné dnes*. Když se cíl a realita liší, je to explicitně označeno **(cíl)** vs **(teď)**.
>
> ✅ **Alza-style konverze provedena** (plán `.claude/plans/alza-style-kmp-conversion.md`). Bázový balíček zůstává **`com.example.ta33`** (vědomě nepřejmenováno). V Gradlu jsou moduly **`:shared`** (sdílené jádro) a **`:androidApp`** (nativní Compose UI); iOS se staví přes Xcode a konzumuje framework **`Shared`**. Přidané knihovny z §2: **Koin, SQLDelight, Ktor (3.5.0), kotlinx.serialization, kotlinx-coroutines, Napier, SKIE (0.10.13), multiplatform Lifecycle ViewModel**. Gradle wrapper je **9.1.0** (vyžaduje AGP 9).
>
> **Vyřešený rozpor scaffold × §7 (UI přístup) - HOTOVO:** Původní scaffold měl dva sdílené moduly `sharedLogic` + `sharedUI`, kde `sharedUI` držel sdílené Compose UI (`App.kt`); iOS byl přitom už nativní SwiftUI (žádný Compose→UIViewController bridge nikdy neexistoval). Provedeno: (a) `sharedLogic` přejmenován na **`shared`** (framework `SharedLogic`→`Shared`), (b) modul `sharedUI` **zrušen** - `App.kt` přesunut do `androidApp` (nativní Android UI), (c) v `shared/commonMain` z Compose ekosystému zůstává **jen `components-resources` + `runtime`** (lokalizace/assety), UI Compose závislosti (`compose.ui`/`foundation`/`material3`) jsou v `androidApp`. Výsledek: **Android UI = Jetpack Compose, iOS UI = SwiftUI**, sdílené ViewModely (`StateFlow`) konzumované oběma (iOS přes SKIE).

### Module Structure (Struktura modulů)

**(teď)** Skutečné cesty ke zdrojům (balíčkové vrstvení uvnitř jednoho modulu `shared`):
- `shared/src/commonMain/kotlin/com/example/ta33/` - sdílený kód, vrstvy jako **balíčky**: `domain/` (modely, use-case, rozhraní), `data/` (repozitáře, DTO), `presentation/` (ViewModely se `StateFlow`), `di/` (Koin `appModule`, `platformModule`, `initKoin`), `core/` (Platform, Strings, utility), `feature/` (zatím prázdné)
- `shared/src/androidMain/kotlin/com/example/ta33/` - Android `actual` (`core/Platform.android.kt`, `di/PlatformModule.android.kt`)
- `shared/src/iosMain/kotlin/com/example/ta33/` - iOS `actual` (`core/Platform.ios.kt`, `di/PlatformModule.ios.kt`)
- `shared/src/commonMain/sqldelight/com/example/ta33/data/db/` - SQLDelight `.sq` schéma
- `shared/src/commonTest/kotlin/…`, `shared/src/androidHostTest/kotlin/…`, `shared/src/iosTest/kotlin/…` - testy
- `shared/src/commonMain/composeResources/` - sdílené resources (Compose): `values/strings.xml`, `values-cs/strings.xml`, `drawable/`
- `androidApp/src/main/kotlin/com/example/ta33/` - Android UI (Compose): `App.kt`, `MainActivity.kt`, `Ta33Application.kt` (Koin init)
- `iosApp/iosApp/` - iOS UI (SwiftUI, Xcode projekt), konzumuje framework `Shared` (SKIE)

**(cíl)** Vrstvy jsou zavedené jako **balíčky** (viz výše). Rozdělení na samostatné Gradle moduly `:core`/`:data`/`:domain`/`:feature-*` je **budoucí krok**, ne aktuální stav. Bázový balíček `com.example.ta33` zůstává (vědomě nepřejmenováno).

### Build & Verification (Příkazy pro build a ověření)

- **Gradle wrapper**: **9.1.0** (AGP 9.0.1 to vyžaduje; nižší verze selže).
- **Compile / build**: `./gradlew build`
- **Android APK (debug)**: `./gradlew :androidApp:assembleDebug`
- **iOS build**: přes **Xcode** (`iosApp/iosApp.xcodeproj`, scheme `iosApp`). Headless ověření: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'id=<simulator-id>' CODE_SIGNING_ALLOWED=NO build`. Pozn.: `Config.xcconfig` přidává `OTHER_LDFLAGS = $(inherited) -lsqlite3` (nutné pro SQLDelight native driver ze statického frameworku `Shared`).
- **Testy (vše)**: `./gradlew check` - nebo cíleně sdílené jádro: `./gradlew :shared:allTests`
- **Static analysis / lint**: **(teď)** není nakonfigurováno žádné detekt/ktlint. Ověřovací záchytnou sítí je kompilace (`./gradlew build`) a Android lint `./gradlew :androidApp:lintDebug`. **(cíl)** doporučeno přidat **detekt** + **ktlint** (viz §6/§7 níže) a pak sem doplnit `./gradlew detekt ktlintCheck`.
- **Format**: **(teď)** není (žádný ktlint). **(cíl)** `./gradlew ktlintFormat` po přidání ktlint.

### Dependency Injection

- **Framework**: **Koin** (viz §2). **(teď) přidán a funkční.**
- **Kam registrovat**: `shared/src/commonMain/kotlin/com/example/ta33/di/` - `AppModule.kt` (`appModule`: DB, repozitáře, ViewModely), `PlatformModule.kt` (`expect val platformModule`, `actual` v android/iosMain poskytuje `SqlDriver`), `Koin.kt` (`initKoin`, `doInitKoin` pro iOS, `ViewModelProvider` pro Swift). Nové služby/ViewModely registruj do `appModule`. Android startuje Koin v `Ta33Application` (`initKoin { androidContext(...) }`), iOS v `iOSApp.swift` (`KoinKt.doInitKoin()`).

### Theming / Design Tokens

- **Zdroj tokenů:** claude.ai/design projekt **„TA33 Design System"** (přístup přes MCP `claude-design` / nástroj `DesignSync` + `/design-login`). Kanonický token soubor je tam `colors_and_type.css`.
- **(teď) Android (Compose): theme vrstva HOTOVÁ** v `androidApp/src/main/kotlin/com/example/ta33/ui/theme/`:
  - `Color.kt` - `Ta33Palette` (raw) + `Ta33Colors` (sémantické role: fg\*, semantic, control-point stavy, map/sky).
  - `Type.kt` - `Ta33Type` (display1…button). ⚠️ Fonty **Big Shoulders Display** + **Inter** zatím fallback na systémové (`FontFamily.Default`); TTF doplnit do `res/font/`.
  - `Dimens.kt` - `Ta33Spacing` (4px base), `Ta33Radius`.
  - `Theme.kt` - `Ta33Theme { }` (Material3 ColorScheme/Typography/Shapes namapované z tokenů) + accessor `Ta33Theme.colors/typography/spacing/radius`.
  - **Použití v UI:** obal do `Ta33Theme { }`, ber barvy/rozměry přes `MaterialTheme.colorScheme` / `MaterialTheme.typography` / `Ta33Theme.colors` / `Ta33Theme.spacing` - **nikdy nehardcoduj hex/dp**.
- **(teď) iOS (SwiftUI): theme vrstva HOTOVÁ** v `iosApp/iosApp/Theme/`: `Color+TA33.swift` (`Ta33Color` + `Color(hex:)`), `Font+TA33.swift` (`Ta33Font`), `Ta33Metrics.swift` (`Ta33Spacing`/`Ta33Radius`). Zrcadlí Android tokeny 1:1. **Použití:** `Ta33Color.*`, `Ta33Font.*`, `Ta33Spacing.*`/`Ta33Radius.*` - nehardcoduj hex/CGFloat. Vizuálně ověřeno na simulátoru.
- **(cíl / pending):** Fonty (Big Shoulders Display + Inter - teď systémový fallback na obou platformách). Komponenty Button/StatChip/MarkBadge a obrazovky (Deník/Mapa/Profil) jsou v design systému připravené k přenosu.

### Localization / Translations

> ✅ **Potvrzeno: Compose Multiplatform string resources.** Jeden lokalizační systém pro obě platformy, sdílený z `commonMain`. Používá se úzká závislost `components-resources` (ne sdílené UI - viz upřesnění v §7 a vyřešený rozpor v úvodu §12).

- **Systém**: **Compose Multiplatform string resources** (`org.jetbrains.compose.components:components-resources`, už v `commonMain`).
- **Zdrojový (výchozí) soubor**: `shared/src/commonMain/composeResources/values/strings.xml` (jazyk `en`).
- **Soubory per jazyk**: `shared/src/commonMain/composeResources/values-<lang>/strings.xml` (např. `values-cs/strings.xml`).
- **Generovaná třída `Res`**: `com.example.ta33.resources.Res` (nastaveno v `shared/build.gradle.kts`: `compose.resources { publicResClass = true; packageOfResClass = "com.example.ta33.resources" }` - `publicResClass` je nutné, aby `Res` viděl i `androidApp`).
- **Reference v kódu**:
  - **Android (Compose)** - přímo `stringResource(Res.string.<key>)` z `com.example.ta33.resources.Res`.
  - **iOS (SwiftUI)** - nepřistupuje k `Res` přímo. Stringy se vystavují přes **sdílenou Kotlin funkci** v `core/Strings.kt` (`suspend fun greetingButtonLabel()` obalující non-`@Composable` `getString(Res.string.<key>)`), kterou SwiftUI volá z frameworku `Shared` (SKIE async). Podle tohoto vzoru přidávej další.
- **Soubory, které `/translate` SMÍ zapisovat**: `en` a `cs`. Ostatní jazyky (pokud přibudou) řešit vědomě.
- **Formát placeholderů k zachování**: `%1$s`, `%2$d` (Android-style poziční argumenty).
- **Stav**: lokalizace **založena**. Existují `values/strings.xml` (en) a `values-cs/strings.xml` (cs) s klíčem `app_greeting_button`. `/translate` může doplňovat překlady mezi `en` a `cs`.

### Code Style

- **Max šířka řádku**: **120 znaků** (doporučená hranice; potvrď/uprav).
- **Konvence**: Kotlin official code style (default Android Studio). **(cíl)** vynucovat přes ktlint + detekt.

### Code Generation

- **(teď)** Dva codegeny, oba **automaticky při buildu** (`./gradlew build`), žádný samostatný krok:
  - **Compose Resources** - třída `Res` z `composeResources/`.
  - **SQLDelight** - DB kód (`Ta33Database`) z `.sq` schémat v `shared/src/commonMain/sqldelight/`.
- Po změně `.sq` schémat, `composeResources/` nebo `@Serializable` modelů stačí `./gradlew build`.

### Plan Output Directory

- **Adresář pro plány** (`/plan-create`, `/plan-update`): `.claude/plans` (relativně ke kořeni projektu).

### Ticket / Branch konvence (volitelné)

- **(teď)** V dokumentu nedefinováno. Sdílené příkazy `jira-checkout` / `bb-review` používají obecný vzor `feature/<KEY>-<číslo>_popis`. Pokud má projekt konkrétní Jira prefix, doplň ho sem.
