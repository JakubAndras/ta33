# TA33 — Použité knihovny a technologie

Stručný přehled technologického stacku projektu TA33 a toho, jak je každá věc v kódu reálně použita.
Verze jsou převzaty z `gradle/libs.versions.toml`, použití ze `shared/build.gradle.kts` a struktury `shared/src/`.

> **Architektura:** Kotlin Multiplatform (KMP) — veškerá logika žije ve sdíleném modulu `shared`
> (`commonMain`) a je psaná v Clean Architecture ve třech vrstvách:
> - **data/** — zdroje dat, repozitáře, mappery, DTO, platformové adaptéry
> - **domain/** — modely, use-case, business pravidla (bez znalosti platformy i frameworků)
> - **presentation/** — ViewModely a UI-state modely
>
> Etapa 1 je **logic-only**: veškerá logika + ViewModely jsou hotové a otestované; nativní UI
> (Compose / SwiftUI) je odložená fáze. Cíle: Android (`minSdk 24`, `target/compileSdk 36`) a iOS
> (`iosArm64`, `iosSimulatorArm64`) přes statický framework `Shared`.

---

## Jazyk, platforma a build

| Technologie | Verze | Jak je použita |
|---|---|---|
| **Kotlin** | 2.4.0 | Hlavní jazyk celého projektu (shared + androidApp). |
| **Kotlin Multiplatform** | (kotlin 2.4.0) | Modul `shared` sdílí veškerou logiku mezi Androidem a iOS. Cíle: `iosArm64`, `iosSimulatorArm64`, `androidLibrary`. |
| **Android Gradle Plugin (AGP)** | 9.0.1 | Build Android aplikace (`androidApp`) i KMP Android knihovny (`com.android.kotlin.multiplatform.library`). |
| **Gradle Version Catalog** | — | Centrální správa verzí a závislostí v `gradle/libs.versions.toml`. |
| **JVM target** | 11 | Cílový bytecode pro Android/host testy. |

**Build & ověření:**
- Kompilace vše: `./gradlew build`
- Android APK (debug): `./gradlew :androidApp:assembleDebug`
- Testy (vše): `./gradlew check` / sdílené jádro: `./gradlew :shared:allTests`
- Lint: `./gradlew :androidApp:lintDebug` (detekt/ktlint zatím nekonfigurován — záchytnou sítí je kompilace)
- Codegen (SQLDelight + Compose resources) běží **automaticky při buildu**, žádný samostatný krok.

---

## Asynchronní zpracování a reaktivita

| Knihovna | Verze | Jak je použita |
|---|---|---|
| **kotlinx.coroutines** | 1.10.2 | Základ veškeré asynchronní a reaktivní logiky. `Flow`/`StateFlow` ve všech ViewModelech a use-case, `combine`/`flatMapLatest`/`scan` pro odvození stavu (např. deník kontrol, timing, proximity detekce). ViewModely drží stav jako `StateFlow<…UiState>`. Konvence: `bind()` ruší předchozí `Job` před spuštěním nového kolektoru. |

Reaktivní vzory v projektu: živá poloha a breadcrumb (`LocationStream` sdílí jedno upstream odběratelství přes `shareIn`), měření času (odvozený elapsed přes `Ticker` + `combine`, žádný per-second zápis), agregovaný stav aplikace (`ObserveAppStateUseCase`).

---

## Serializace a síť

| Knihovna | Verze | Jak je použita |
|---|---|---|
| **kotlinx.serialization (JSON)** | 1.8.1 | `@Serializable` DTO pro obsah offline balíčku (`data/dto/ManifestDto`, `ContentDto`) a `Destination` navigační kontrakt (serializovatelné argumenty pro navigaci). |
| **Ktor Client** | 3.5.0 | HTTP klient pro stažení offline balíčku (manifest + mapové dlaždice). `ktor-client-core` + `content-negotiation` + `kotlinx-json` v `commonMain`. Podpora resumování stahování přes HTTP `Range` (416 = hotovo). |
| ↳ **ktor-client-okhttp** | 3.5.0 | Engine pro Android (`androidMain`). |
| ↳ **ktor-client-darwin** | 3.5.0 | Engine pro iOS (`iosMain`). |

---

## Perzistence (offline-first)

| Knihovna | Verze | Jak je použita |
|---|---|---|
| **SQLDelight** | 2.1.0 | Lokální databáze `Ta33Database` (package `com.example.ta33.data.db`). Typově bezpečné dotazy generované z `.sq` souborů. Reaktivní čtení přes `coroutines-extensions` (`asFlow()`). Konvence: bez column-adapterů — `INTEGER` (Long) s konverzí `.toInt()`/`.toLong()` v mapperech. |
| ↳ **android-driver** | 2.1.0 | SQLite driver pro Android. |
| ↳ **native-driver** | 2.1.0 | SQLite driver pro iOS. |

**Tabulky (`.sq`):** `Route`, `ControlPoint`, `Participant`, `RunSession`, `CollectedControl`, `Trackpoint`, `AppPreferences`, `Preparation`, `DownloadedAsset` (+ `schema.sq`). Pokrývají trasy a kontroly, běh závodu (start/finish), sebrané kontroly, GPS stopu, uživatelské preference a stav stažení offline balíčku.

---

## Dependency Injection

| Knihovna | Verze | Jak je použita |
|---|---|---|
| **Koin** | 4.1.0 | DI kontejner. `di/AppModule.kt` registruje repozitáře, use-case a ViewModely; `di/Koin.kt` obsahuje `ViewModelProvider` accessory pro Swift/iOS. Platformové služby (SQL driver, `FileStorage`, `ConnectivityMonitor`, `LocationProvider`, `UrlOpener`) se registrují v `di/PlatformModule.android.kt` / `.ios.kt`. |
| ↳ **koin-android** | 4.1.0 | Android-specifické rozšíření Koinu. |
| ↳ **koin-compose-viewmodel** | 4.1.0 | Napojení ViewModelů do Compose (pro UI fázi). |

---

## Prezentační vrstva

| Knihovna | Verze | Jak je použita |
|---|---|---|
| **AndroidX Lifecycle ViewModel** (multiplatform) | 2.11.0-beta01 | Základní třída ViewModelů (`viewModelScope`) sdílená v `commonMain`. ViewModely: `AppViewModel`, `RouteList/RouteDetailViewModel`, `RunLogViewModel`, `LiveLocationViewModel`, `ControlCollectionViewModel`, `TimingViewModel`, `MapViewModel`, `DownloadViewModel`, `Overview/SettingsViewModel`. |
| **Compose Multiplatform** | 1.11.1 | `compose-runtime` a `components-resources` jsou v `commonMain` (pro stabilitu a přístup k resources). Vlastní UI (`compose.material3`, `foundation`, `ui`) je připraveno pro odloženou UI fázi; `androidApp` už závisí na Compose. `compose.resources` generuje resource třídu do `com.example.ta33.resources`. Lokalizace: `values/` (výchozí) + `values-cs/` (čeština). |

> Pozn.: `presentation` vrstva vystavuje čistý `…UiState` a navigační `Destination` kontrakt — sdílená
> logika neimportuje žádnou UI ani mapovou SDK (ověřeno grep-em v `shared/`).

---

## Platformově specifické funkce (expect/actual + interface + platform impl)

Funkce, které potřebují platformové API, jsou v `commonMain` definované jako rozhraní / `expect` a implementované zvlášť v `androidMain` a `iosMain`:

| Funkce | Common | Android | iOS |
|---|---|---|---|
| **Geolokace** | `data/location/LocationProvider` (`Flow<GeoPosition>`), `LocationPermissionController`, `LocationTrackingController` | FusedLocationProvider (`play-services-location`), foreground service | `CLLocationManager` (callbacky na main queue) |
| **Úložiště souborů** | `data/file/FileStorage` (dlaždice offline balíčku) | Android FS | iOS FS |
| **Konektivita** | `data/connectivity/ConnectivityMonitor` (Wi-Fi-only gating stahování) | `ConnectivityManager` | `NWPathMonitor` |
| **Otevření URL** | `core/UrlOpener` (deeplink do mapy.cz) | `Intent` | `UIApplication.openURL` (main queue) |
| **Platform info** | `core/Platform` | — | — |

| Knihovna | Verze | Jak je použita |
|---|---|---|
| **Google Play Services — Location** | 21.3.0 | Zdroj GPS fixů na Androidu (jen `androidMain`). |

---

## iOS interoperabilita

| Knihovna | Verze | Jak je použita |
|---|---|---|
| **SKIE** | 0.10.13 | Vylepšuje Swift API generované z Kotlinu — mapuje `Flow` na Swift async/`AsyncSequence`, sealed classes na Swift enums, default argumenty apod. Vzniká statický framework `Shared` konzumovaný `iosApp`. |

---

## Logování

| Knihovna | Verze | Jak je použita |
|---|---|---|
| **Napier** | 2.7.1 | Multiplatform logging v `commonMain` (jednotné logy Android + iOS). |

---

## Testování

| Knihovna | Verze | Jak je použita |
|---|---|---|
| **kotlin-test** | 2.4.0 | Základní asserce, běží v `commonTest` na Android hostu i iOS simulátoru. |
| **kotlinx.coroutines.test** | 1.10.2 | `runTest`, virtuální čas a `TestDispatcher` pro deterministické testy coroutines/Flow. |
| **Turbine** | 1.2.0 | Testování `Flow`/`StateFlow` emisí. Konvence u reaktivních use-case: `turbineScope + testIn(backgroundScope) + runCurrent`. |
| **ktor-client-mock** | 3.5.0 | Mockování HTTP odpovědí při testech stahování offline balíčku. |
| **JUnit 4** | 4.13.2 | Android instrumentace / host test runner. |

Testy jsou čistě na sdílené logice (use-case, mappery, kalkulátory, ViewModely) přes fakes (`commonTest/.../Fakes.kt`) — žádná závislost na reálné DB nebo síti. Celkově stovky unit testů zelených na obou platformách.

---

## Struktura repozitáře — soubor po souboru

### Kořen projektu

| Soubor | Co je v něm |
|---|---|
| `settings.gradle.kts` | Definice Gradle modulů (`shared`, `androidApp`) a repozitářů pluginů. |
| `build.gradle.kts` | Root build skript — aplikuje pluginy `apply false` pro celý build. |
| `gradle.properties` | Gradle/Kotlin flagy (AndroidX, KMP, JVM args, config cache). |
| `gradle/libs.versions.toml` | Version catalog — centrální verze a definice všech knihoven a pluginů. |
| `gradle/gradle-daemon-jvm.properties` | JVM toolchain pro Gradle daemon. |
| `local.properties` | Lokální cesta k Android SDK (mimo git). |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/` | Gradle wrapper. |

---

### `shared/` — KMP modul (veškerá sdílená logika)

Konfigurace: `shared/build.gradle.kts` (KMP cíle, závislosti per source-set, SQLDelight & Compose resources bloky).

#### `commonMain/kotlin/com/example/ta33/`

**`core/` — základní stavební kameny a utility (většinou čisté funkce)**

| Soubor | Co je v něm |
|---|---|
| `TimeProvider.kt` | Rozhraní pro získání aktuálního času (`nowMillis`) — testovatelná abstrakce nad systémovými hodinami. |
| `IdGenerator.kt` | Generování unikátních ID (pro nové řádky v DB). |
| `Stopwatch.kt` | Čistý výpočet uplynulého času (0 / běžící / zmrazený po finiši), nikdy záporný. |
| `SplitCalculator.kt` | Výpočet mezičasů (splits) ze startu a sebraných kontrol. |
| `TimeFormatter.kt` | Formátování času na `MM:SS` / `H:MM:SS`. |
| `Ticker.kt` | Zdroj tiků po 1 s (`Flow`) pro živé hodiny — bez per-second zápisu. |
| `UrlOpener.kt` | Rozhraní pro otevření externí URL (deeplink) — `expect`/actual přes platform impl. |
| `Platform.kt` | Rozhraní pro platform-specifické info (název platformy). |
| `Strings.kt` | Sdílené řetězcové konstanty logiky. |

**`domain/model/` — datové modely a UI-state (bez chování)**

| Soubor | Co je v něm |
|---|---|
| `GeoPoint.kt` | Souřadnice (latitude, longitude). |
| `GeoPosition.kt` | GPS fix — `GeoPoint` + přesnost + timestamp. |
| `Route.kt`, `RouteDetail.kt`, `RouteSummary.kt` | Trasa (plná), detail s kontrolami seřazenými podle ordinálu, souhrn pro seznam (vč. počtu kontrol). |
| `ControlPoint.kt`, `ControlPointState.kt` | Kontrolní bod trasy; kanonický stav kontroly (DONE / ACTIVE / LOCKED apod.). |
| `CollectedControl.kt`, `CollectionCandidate.kt` | Sebraná kontrola (perzistentní); kandidát na sebrání (přechodný, z GPS proximity). |
| `GeofenceConfig.kt` | Konfigurace geofence (radius, hystereze, min. přesnost, počet fixů). |
| `RunSession.kt` | Běh závodu (startedAtMillis? / finishedAtMillis?). |
| `RunLogEntry.kt`, `LogUiState.kt` | Řádek deníku kontrol; agregovaný UI-state deníku (progress „2 z 5", next control…). |
| `Participant.kt` | Účastník (lokální identita). |
| `Trackpoint.kt`, `BreadcrumbConfig.kt` | Bod GPS stopy; konfigurace throttlingu breadcrumbů. |
| `Split.kt`, `TimingSnapshot.kt`, `QrTimingConfig.kt` | Mezičas; snapshot časomíry (elapsed + splits); konfigurace QR časomíry. |
| `MapCamera.kt`, `MapOverlay.kt`, `MapTileSource.kt` | Kamera/bounding box mapy; překryv (markery + breadcrumb); zdroj dlaždic + jeho stav. |
| `PreparationState.kt` | Stav přípravy/stažení offline balíčku (status, verze, readyAt). |
| `NetworkPreference.kt`, `NetworkType.kt` | Preference sítě (Wi-Fi-only) a typ připojení. |
| `SyncStatus.kt` | Stav synchronizace řádku (default PENDING). |
| `LocationPermissionStatus.kt` | Stav oprávnění k poloze. |
| `OverviewUiState.kt`, `SettingsUiState.kt` | Agregovaný UI-state přehledu a nastavení. |
| `OrganizerContact.kt`, `FaqItem.kt` | Statická data — kontakt na organizátora, položka FAQ. |

**`domain/` — business logika (čisté kalkulátory, resolvery, deriveery)**

| Soubor | Co je v něm |
|---|---|
| `geo/GeoUtils.kt` | Haversine vzdálenost mezi souřadnicemi (sdílená napříč geofencingem a mapou). |
| `geo/BreadcrumbThrottle.kt` | Rozhodnutí, zda uložit nový GPS bod (podle vzdálenosti/času). |
| `geo/ProximityEvaluator.kt` | Vyhodnocení blízkosti ke kontrole s hysterezí (enter/exit + min. počet fixů). |
| `log/ControlLogDeriver.kt` | Čisté odvození deníku kontrol z trasy + sebraných kontrol + běhu. |
| `route/ActiveRouteResolver.kt` | Určení aktivní trasy (běh → validovaný výběr → jediná trasa → null). |
| `map/RouteBounds.kt`, `MapCameraCalculator.kt` | Bounding box trasy; výpočet kamery. |
| `map/MarkerSelector.kt`, `OverlayMapper.kt` | Výběr nejbližšího markeru; mapování stavu na překryv. |
| `map/TileSourceSelector.kt`, `TileStore.kt`, `MapTileConfig.kt` | Výběr zdroje dlaždic (Ready jen když READY + asset DONE + soubor existuje); rozhraní úložiště dlaždic; konfigurace. |
| `mapy/MapyCzUrlBuilder.kt`, `MapyRouteType.kt` | Sestavení deeplink URL do mapy.cz (swap lon/lat, waypointy, capping); typ trasy (pěší/kolo). |
| `qr/QrPayloadParser.kt`, `QrParseResult.kt`, `QrScanHandler.kt` | Parsování QR (start/finish/cizí); výsledek parsování; handler naskenování. |
| `download/ProgressReducer.kt`, `DownloadProgress.kt` | Redukce průběhu stahování (precedence + zlomek); model průběhu. |
| `overview/OverviewComposer.kt` | Čisté složení `OverviewUiState` z více zdrojů. |

**`domain/repository/` — rozhraní repozitářů (kontrakty pro data vrstvu)**

| Soubor | Co je v něm |
|---|---|
| `RouteRepository.kt` | Trasy — `observeRouteSummaries`, `observeRouteWithControls`, `getRouteWithControls`, `upsertRoute`… |
| `RunRepository.kt` | Běh — `observeRun`/`getRun`, `observeCollected`, `observeActiveRun`, přidání sebrané kontroly. |
| `ParticipantRepository.kt` | Lokální účastník. |
| `TrackpointRepository.kt` | Ukládání/čtení GPS stopy. |
| `AppPreferencesRepository.kt` | Uživatelské preference (aktivní trasa, notifikace). |
| `PreparationRepository.kt` | Stav přípravy offline balíčku. |
| `OfflinePackageRepository.kt` | Orchestrace stažení balíčku. |
| `AppInfoRepository.kt` | Statické info (organizátor, FAQ). |

**`domain/usecase/` — aplikační use-case (jedna operace / jeden reaktivní stream)**

| Soubor | Co je v něm |
|---|---|
| `EnsureLocalParticipantUseCase.kt` | Zajistí existenci lokálního účastníka. |
| `ObserveRoutesUseCase.kt`, `ObserveRouteDetailUseCase.kt` | Stream seznamu tras; stream detailu trasy (seřazeno dle ordinálu). |
| `SelectActiveRouteUseCase.kt`, `ObserveSelectedRouteUseCase.kt` | Nastavení aktivní trasy (validace existence); stream vybrané trasy (ignoruje stale). |
| `StartRunUseCase.kt`, `FinishRunUseCase.kt` | Start běhu; finish (idempotentní, odmítá finish-před-startem). |
| `CollectControlUseCase.kt` | Sebrání kontroly (idempotentní, s polohou). |
| `ObserveRunLogUseCase.kt` | Reaktivní deník kontrol (combine trasy + běhu + sebraných). |
| `RecordBreadcrumbUseCase.kt`, `ObserveTrackUseCase.kt` | Nahrávání breadcrumbů (collect→throttle→persist); čtení stopy. |
| `ObserveCollectionCandidateUseCase.kt` | Reaktivní kandidát na sebrání z GPS proximity (any-order). |
| `HandleScannedQrUseCase.kt` | Zpracování naskenovaného QR → start/finish běhu. |
| `ObserveTimingUseCase.kt` | Reaktivní časomíra (ticker + odvozený elapsed + splits). |
| `OpenRouteInMapyCzUseCase.kt` | Sestaví URL a otevře trasu v mapy.cz. |
| `PrepareOfflinePackageUseCase.kt`, `ObservePreparationStateUseCase.kt` | Orchestrace stažení balíčku; stream stavu přípravy. |
| `ObserveNotificationsEnabledUseCase.kt`, `SetNotificationsEnabledUseCase.kt` | Čtení/zápis preference notifikací. |
| `ObserveAppStateUseCase.kt` | Jedno sdílené odvození `AppUiState` (readiness, aktivní trasa/běh). |
| `ObserveOverviewUseCase.kt` | Agregovaný stream přehledu (staví na app-state + deník + příprava). |

**`data/` — implementace repozitářů, mappery, síť, DTO**

| Soubor | Co je v něm |
|---|---|
| `repository/RouteRepositoryImpl.kt` | RouteRepository nad SQLDelight (vč. inline summary mapování). |
| `repository/RunRepositoryImpl.kt` | RunRepository nad SQLDelight (atomické přidání sebrané kontroly v transakci). |
| `repository/ParticipantRepositoryImpl.kt`, `TrackpointRepositoryImpl.kt` | Účastník; GPS stopa. |
| `repository/AppPreferencesRepositoryImpl.kt` | Preference nad tabulkou `AppPreferences`. |
| `repository/PreparationRepositoryImpl.kt`, `OfflinePackageRepositoryImpl.kt` | Stav přípravy; orchestrace stahování (manifest → obsah → dlaždice). |
| `repository/StaticAppInfoRepository.kt` | Napevno zadaný organizátor + FAQ. |
| `mapper/Mappers.kt` | Mapování DB řádků ↔ domain modely (`.toInt()`/`.toLong()` konverze). |
| `dto/ManifestDto.kt`, `ContentDto.kt`, `ContentMappers.kt` | `@Serializable` DTO manifestu a obsahu + mapování na domain. |
| `remote/ContentRemoteDataSource.kt`, `HttpClientFactory.kt`, `ContentConfig.kt` | Ktor data source pro balíček; továrna HTTP klienta; konfigurace URL. |
| `map/TileStoreImpl.kt` | Implementace `TileStore` nad `FileStorage` (+ CancellationException guard). |
| `location/LocationStream.kt` | Sdílený stream polohy (`shareIn`, jedno upstream odběratelství). |
| `location/LocationProvider.kt`, `LocationPermissionController.kt`, `LocationTrackingController.kt` | Rozhraní zdroje polohy, oprávnění a řízení trackingu (actual v platform mainech). |
| `file/FileStorage.kt` | Rozhraní úložiště souborů dlaždic (actual v platform mainech). |
| `connectivity/ConnectivityMonitor.kt` | Rozhraní sledování konektivity (actual v platform mainech). |

**`presentation/` — ViewModely a navigační kontrakt**

| Soubor | Co je v něm |
|---|---|
| `AppViewModel.kt` | Kořenový VM — vystavuje `AppUiState` (readiness, aktivní trasa/běh). |
| `RouteListViewModel.kt`, `RouteDetailViewModel.kt` | Seznam tras; detail trasy + výběr aktivní. |
| `RunLogViewModel.kt` | Deník kontrol. |
| `LiveLocationViewModel.kt` | Živá poloha + nahrávání breadcrumbů. |
| `ControlCollectionViewModel.kt` | Nabídka a potvrzení sebrání kontroly (geofencing). |
| `TimingViewModel.kt` | Časomíra — QR start/finish, elapsed, splits. |
| `MapViewModel.kt`, `MapUiState.kt` | Agregace mapy (trasa + deník + poloha + dlaždice); UI-state mapy. |
| `DownloadViewModel.kt` | Stažení offline balíčku (progress + intents). |
| `OverviewViewModel.kt`, `SettingsViewModel.kt` | Přehled; nastavení (persistuje preference). |
| `navigation/Destination.kt` | `@Serializable` sealed hierarchie cílů navigace (taby + detaily). |
| `navigation/AppUiState.kt`, `AppReadiness.kt` | App-level UI-state; enum připravenosti (READY/PREPARING…). |
| `navigation/AppStateReducer.kt` | Čistá redukce zdrojů na `AppUiState`. |
| `navigation/StartDestinationResolver.kt` | Určení výchozí obrazovky při startu. |

**`di/` — dependency injection (Koin)**

| Soubor | Co je v něm |
|---|---|
| `AppModule.kt` | Registrace repozitářů, use-case a ViewModelů. |
| `PlatformModule.kt` | `expect` deklarace platform modulu (SQL driver, FileStorage, location…). |
| `Koin.kt` | Inicializace Koinu + `ViewModelProvider` accessory pro Swift/iOS. |

#### `commonMain/sqldelight/com/example/ta33/data/db/` — SQL schéma + dotazy

| Soubor | Co je v něm |
|---|---|
| `schema.sq` | Kořenové schéma DB `Ta33Database`. |
| `Route.sq`, `ControlPoint.sq` | Trasy a jejich kontrolní body (+ `selectRouteSummaries`). |
| `Participant.sq` | Lokální účastník. |
| `RunSession.sq` | Běhy (+ `selectActiveRun`). |
| `CollectedControl.sq` | Sebrané kontroly. |
| `Trackpoint.sq` | GPS stopa. |
| `AppPreferences.sq` | Preference (aktivní trasa, notifikace). |
| `Preparation.sq`, `DownloadedAsset.sq` | Stav přípravy balíčku a stažených assetů. |

#### `commonMain/composeResources/` — sdílené resources

| Soubor | Co je v něm |
|---|---|
| `values/strings.xml`, `values-cs/strings.xml` | Řetězce (výchozí + čeština) — zatím prázdné placeholdery, plní se v UI fázi. |
| `drawable/compose-multiplatform.xml` | Demo drawable ze šablony (kandidát na výměnu za reálné ikony). |

#### `androidMain/kotlin/com/example/ta33/` — Android actual implementace

| Soubor | Co je v něm |
|---|---|
| `core/Platform.android.kt`, `core/UrlOpener.android.kt` | Platform info; otevření URL přes `Intent`. |
| `data/location/LocationProvider.android.kt` | Zdroj GPS přes FusedLocationProvider. |
| `data/location/LocationPermissionController.android.kt`, `LocationTrackingController.android.kt` | Oprávnění a řízení trackingu. |
| `data/location/LocationForegroundService.kt` | Foreground service pro tracking na pozadí. |
| `data/file/FileStorage.android.kt` | Souborové úložiště na Androidu. |
| `data/connectivity/ConnectivityMonitor.android.kt` | Konektivita přes `ConnectivityManager`. |
| `di/PlatformModule.android.kt` | Android `actual` platform modul (android-driver, contexty, actual služby). |

#### `iosMain/kotlin/com/example/ta33/` — iOS actual implementace

| Soubor | Co je v něm |
|---|---|
| `core/Platform.ios.kt`, `core/UrlOpener.ios.kt` | Platform info; otevření URL přes `UIApplication` (main queue). |
| `data/location/LocationProvider.ios.kt` | Zdroj GPS přes `CLLocationManager` (callbacky na main queue). |
| `data/location/LocationPermissionController.ios.kt`, `LocationTrackingController.ios.kt` | Oprávnění a řízení trackingu. |
| `data/file/FileStorage.ios.kt` | Souborové úložiště na iOS. |
| `data/connectivity/ConnectivityMonitor.ios.kt` | Konektivita přes `NWPathMonitor`. |
| `di/PlatformModule.ios.kt` | iOS `actual` platform modul (native-driver, actual služby). |

#### `commonTest/kotlin/com/example/ta33/` — unit testy sdílené logiky

| Skupina | Soubory |
|---|---|
| Testovací infrastruktura | `Fakes.kt` (fakes repozitářů, `FakeTicker`, `MutableLocationStream`…), `SharedCommonTest.kt` |
| Geo & sběr | `GeoUtilsTest`, `GeoPositionTest`, `BreadcrumbThrottleTest`, `ProximityEvaluatorTest`, `RecordBreadcrumbUseCaseTest`, `ObserveTrackUseCaseTest`, `LocationStreamTest`, `CollectControlUseCaseTest`, `ObserveCollectionCandidateUseCaseTest` |
| Trasy & běh | `ActiveRouteResolverTest`, `ObserveRouteDetailUseCaseTest`, `ObserveSelectedRouteUseCaseTest`, `SelectActiveRouteUseCaseTest`, `RouteSummaryMappingTest`, `ControlLogDeriverTest`, `RunTimingTest`, `SyncStatusTest` |
| Časomíra & QR | `StopwatchTest`, `SplitCalculatorTest`, `TimeFormatterTest`, `QrPayloadParserTest`, `HandleScannedQrUseCaseTest`, `ObserveTimingUseCaseTest` |
| Mapa & mapy.cz | `map/RouteBoundsTest`, `map/MapCameraCalculatorTest`, `map/MarkerSelectorTest`, `map/OverlayMapperTest`, `map/TileSourceSelectorTest`, `map/TileStoreImplTest`, `map/MapViewModelTest`, `MapyCzUrlBuilderTest`, `OpenRouteInMapyCzUseCaseTest` |
| Stahování balíčku | `ManifestParsingTest`, `ContentMappingTest`, `ProgressReducerTest`, `ConnectivityGatingTest`, `PrepareOfflinePackageUseCaseTest`, `DownloadViewModelTest` |
| Přehled & nastavení | `OverviewComposerTest`, `ObserveOverviewUseCaseTest`, `ObserveAppStateUseCaseTest`, `NotificationsPreferenceTest`, `AppInfoRepositoryTest` |
| ViewModely & navigace | `presentation/AppViewModelTest`, `presentation/LiveLocationViewModelTest`, `presentation/ControlCollectionViewModelTest`, `presentation/TimingViewModelTest`, `presentation/OverviewViewModelTest`, `presentation/SettingsViewModelTest`, `RunLogViewModelTest`, `RouteListViewModelTest`, `RouteDetailViewModelTest`, `presentation/navigation/{AppStateReducerTest, DestinationSerializationTest, StartDestinationResolverTest}` |

---

### `androidApp/` — Android aplikace (Compose UI)

| Soubor | Co je v něm |
|---|---|
| `src/main/AndroidManifest.xml` | Manifest — oprávnění (INTERNET, ACCESS_NETWORK_STATE, poloha), foreground service, launcher aktivita. |
| `src/main/kotlin/.../Ta33Application.kt` | `Application` — inicializace Koinu (Android). |
| `src/main/kotlin/.../MainActivity.kt` | Vstupní aktivita, hostí Compose. |
| `src/main/kotlin/.../App.kt` | Kořenový composable aplikace. |
| `src/main/kotlin/.../ui/theme/{Theme,Color,Type,Dimens}.kt` | Design tokens — barvy, typografie, rozměry, Material3 téma. |
| `src/main/res/` | Ikony (mipmap/drawable) a `strings.xml`. |
| `build.gradle.kts` | Build Android aplikace (závislost na `shared` + Compose). |

### `iosApp/` — iOS aplikace (SwiftUI)

| Soubor | Co je v něm |
|---|---|
| `iosApp/iOSApp.swift` | Vstupní bod SwiftUI app — inicializace Koinu (iOS). |
| `iosApp/ContentView.swift` | Kořenový SwiftUI view (konzumuje framework `Shared`). |
| `iosApp/Theme/{Color+TA33,Font+TA33,Ta33Metrics}.swift` | Design tokens na straně SwiftUI. |
| `iosApp/Info.plist` | Konfigurace (oprávnění polohy apod.). |
| `iosApp/Assets.xcassets/` | Ikony a barvy. |
| `Configuration/Config.xcconfig` | Build konfigurace Xcode. |
| `iosApp.xcodeproj/` | Xcode projekt. |

### `scripts/`
Pomocné skripty projektu.

---

## Přehled funkčních oblastí (FR) a jejich technologií

| Oblast | Klíčové technologie |
|---|---|
| Offline foundation / perzistence (FR-02) | SQLDelight, coroutines |
| App skeleton / navigace / app-state (FR-01) | kotlinx.serialization (`Destination`), Lifecycle VM |
| Detail trasy + výběr aktivní (FR-03) | SQLDelight, coroutines, AppPreferences |
| Deník kontrol / odvození stavu (FR-04) | Pure Kotlin (deriver), Flow `combine` |
| Živá poloha + breadcrumb (FR-05) | LocationProvider (expect/actual), `shareIn`, play-services-location / CLLocationManager |
| Offline mapa — agregace dat (FR-06) | FileStorage, pure geo kalkulátory, Flow (bez map SDK v shared) |
| Deeplink do mapy.cz (FR-07) | `UrlOpener` (expect/actual) |
| Sběr kontrol / geofencing (FR-08) | GeoUtils, `scan`/hystereze, LocationStream |
| Měření času / QR start-finish / mezičasy (FR-09) | `Ticker`, odvozený elapsed, coroutines |
| Stažení offline balíčku (FR-11) | Ktor (Range/resume), kotlinx.serialization, FileStorage, ConnectivityMonitor |
| Přehled a nastavení (FR-10) | Agregace přes sdílené use-case, AppPreferences |
