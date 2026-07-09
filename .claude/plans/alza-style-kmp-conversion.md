# Alza-Style KMP Conversion (Scaffold → Cílový Stack)

> **Summary**: Převést KMP wizard scaffold TA33 na cílový Alza-style stav — jeden sdílený modul s balíčkovým vrstvením (domain/data/presentation/feature), sdílené ViewModely se `StateFlow` konzumované nativním Compose (Android) i SwiftUI (iOS přes SKIE), a přidané sdílené knihovny (Koin, Ktor, kotlinx.serialization, SQLDelight, Napier).

> ✅ **STAV: IMPLEMENTOVÁNO (2026-07-07).** Všech 12 kroků hotovo a ověřeno: `./gradlew build` (exit 0), `:shared:allTests` OK, `:androidApp:assembleDebug` OK, a iOS `xcodebuild` → **BUILD SUCCEEDED** na simulátoru. Odchylky od původního plánu viz §11 CHANGELOG a §12.2. Neověřeno jen runtime spuštění na zařízení (build/link/deploy hotové).

---

## 1. PROBLEM & SOLUTION

### 1.1 Problem Statement
Projekt je zatím na výchozím KMP wizard scaffoldu se dvěma sdílenými moduly (`sharedLogic`, `sharedUI`), balíčkem `com.example.ta33` a bez klíčových knihoven z cílového stacku (Koin, Ktor, SQLDelight, kotlinx.serialization, SKIE, logování). Cílový „Alza-style" stav podle `.claude/project-stack.md` = sdílené jádro + sdílené ViewModely + nativní UI (Compose/SwiftUI), s vrstvenou strukturou. Scaffold je od toho vzdálen a je potřeba ho převést najednou.

### 1.2 Solution Overview
Sjednotit sdílený kód do jednoho modulu `shared` s balíčkovými vrstvami, přesunout Compose UI z `sharedUI` do `androidApp` (nativní Android UI), ponechat v `shared/commonMain` z Compose ekosystému jen `components-resources` (lokalizace). Přidat sdílené knihovny a SKIE a ověřit end-to-end smyčku sdíleného `ViewModel` → `StateFlow` → Compose i SwiftUI. (Balíček `com.example.ta33` se nemění.)

### 1.3 Scope: What This IS
- Konsolidace `sharedLogic` + `sharedUI` → jeden modul `shared` (balíčkové vrstvení, ne Gradle multi-modul split).
- Přesun nativního Compose UI do `androidApp`; `shared/commonMain` Compose zúžen na `components-resources`.
- Přidání knihoven do `shared`: kotlinx-coroutines, kotlinx.serialization, Ktor Client, SQLDelight, Koin, Napier, multiplatform Lifecycle `ViewModel`.
- Přidání **SKIE** pro Kotlin↔Swift interop.
- Balíček **zůstává `com.example.ta33`** (žádné přejmenování — vědomé rozhodnutí uživatele).
- Demo sdílený `ViewModel` se `StateFlow<UiState>` konzumovaný Compose i SwiftUI (důkaz Alza-style smyčky).
- Založení lokalizace přes Compose resources (`values/strings.xml` en + `values-cs`) + iOS wrapper funkce.
- Aktualizace `.claude/project-stack.md` §12 na nový skutečný stav.

### 1.4 Scope: What This IS NOT
- **Není** rozdělení na samostatné Gradle moduly `:core`/`:data`/`:domain`/`:feature-*` (vědomě odloženo — viz §12 stack dokumentu; zde jen balíčky).
- **Není** implementace reálných feature obrazovek (deník, mapa, sken, profil) — jen kostra vrstev + jedno demo.
- **Není** MapLibre, GPS, kamera, QR, bezpečné úložiště (Etapa 1/2 funkce, samostatné plány).
- **Není** backend / Ktor Server (Etapa 2).
- **Není** konverze iOS na SwiftUI — scaffold ji už má hotovou (viz Sekce 10).
- **Není** detekt/ktlint/CI setup (nice-to-have, §6 stacku).

---

## 2. SUCCESS CRITERIA

Implementace je KOMPLETNÍ, když jsou splněna VŠECHNA kritéria:

| # | Criterion | Verification Method |
|---|-----------|---------------------|
| 1 | Existuje jediný sdílený modul `shared`; moduly `sharedLogic`/`sharedUI` už neexistují | `settings.gradle.kts` obsahuje `:shared`, `:androidApp`; `sharedUI`/`sharedLogic` složky smazány |
| 2 | Projekt se sestaví | `./gradlew build` projde bez chyb |
| 3 | Android APK se sestaví a spustí | `./gradlew :androidApp:assembleDebug` projde; app běží v emulátoru |
| 4 | iOS se sestaví ve Xcode a spustí | `iosApp/iosApp.xcodeproj` build v Xcode projde; app běží v simulátoru |
| 5 | `shared/commonMain` z Compose obsahuje **jen** `components-resources` (žádné `compose.ui`/`foundation`/`material3`) | Kontrola `shared/build.gradle.kts` |
| 6 | Balíček zůstává `com.example.ta33`; žádné zbytky názvů `sharedLogic`/`sharedUI` v kódu/Gradle/Swift | `grep -rn "sharedLogic\|sharedUI"` vrátí 0 výsledků |
| 7 | Sdílený `GreetingViewModel` vystavuje `StateFlow<GreetingUiState>` v `commonMain/.../presentation` | Soubor existuje, vrací `StateFlow` |
| 8 | Android Compose UI čte stav přes `collectAsStateWithLifecycle()` z toho ViewModelu | `androidApp` UI kód volá sdílený ViewModel |
| 9 | SwiftUI čte stav ze sdíleného ViewModelu přes SKIE (observuje `StateFlow`) | `ContentView.swift` používá SKIE-generovanou observaci |
| 10 | Knihovny přidány a použitelné: Koin, Ktor, kotlinx.serialization, SQLDelight, Napier | `libs.versions.toml` + `shared/build.gradle.kts`; DI graf startuje bez pádu |
| 11 | Koin `appModule` startuje na obou platformách (`initKoin()`) | Android `Application`/`MainActivity` a iOS `iOSApp` volají init; app nespadne |
| 12 | Lokalizace: `values/strings.xml` (en) + `values-cs/strings.xml` existují, oba jazyky se zobrazí | Přepnutí jazyka telefonu mění text |
| 13 | Balíčkové vrstvy existují v `shared/commonMain`: `domain`, `data`, `presentation`, `di`, `feature` | Adresářová struktura |
| 14 | `.claude/project-stack.md` §12 aktualizována (názvy modulů, balíček, přidané knihovny) | Diff dokumentu |

---

## 3. TECHNICAL DESIGN

### 3.1 Architecture

Cílová struktura modulů (balíčkové vrstvení uvnitř jednoho `shared`):

```
┌──────────────────────────┐        ┌──────────────────────────┐
│   androidApp (Compose)    │        │     iosApp (SwiftUI)      │
│  App.kt, MainActivity,    │        │  ContentView.swift,       │
│  ui/theme/*               │        │  iOSApp.swift             │
└────────────┬─────────────┘        └────────────┬─────────────┘
             │ implementation(projects.shared)    │ import Shared (framework, SKIE)
             ▼                                     ▼
        ┌──────────────────────────────────────────────────┐
        │                     :shared (KMP)                  │
        │  commonMain/kotlin/com/example/ta33/             │
        │    presentation/  (ViewModely → StateFlow<UiState>)│
        │    domain/        (modely, use-case, rozhraní)     │
        │    data/          (repo impl, DTO, DataSource)     │
        │    di/            (Koin appModule, initKoin)       │
        │    feature/…      (feature-specifické seskupení)   │
        │    core/          (utility, Result, logging fasáda)│
        │  commonMain/composeResources/ (strings.xml, i18n)  │
        │  androidMain / iosMain  (expect/actual: DB driver, │
        │                          Ktor engine, platform)   │
        └──────────────────────────────────────────────────┘
```

Data flow (Alza-style smyčka): UI (Compose i SwiftUI) → volá metody sdíleného `ViewModel` → ViewModel mění `MutableStateFlow` → UI odebírá `StateFlow<UiState>` (Compose `collectAsStateWithLifecycle`, SwiftUI přes SKIE observaci).

### 3.2 Key Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| Počet sdílených modulů | Jeden `shared` (přejmenovaný z `sharedLogic`), `sharedUI` zrušen | Odpovídá `:shared` modelu v §12 stacku; méně Gradle složitosti pro sólo projekt; user zvolil balíčkové vrstvení |
| Umístění Compose UI | `androidApp` | Alza-style = nativní UI per platforma; iOS už UI nesdílí, tak i Android UI patří do app modulu |
| Compose v `shared/commonMain` | Jen `components-resources` (+ nutné `runtime`/plugin pro codegen) | §7/§12 stacku: úzká závislost pro lokalizaci, nevynucuje sdílené UI |
| Kotlin↔Swift interop | SKIE (plugin na `shared` modulu) | §2 stacku „klíčové pro Alza-style"; zpříjemní `suspend`/`Flow`/`StateFlow` observaci ze Swiftu |
| Sdílené ViewModely | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` (multiplatform) v `commonMain` | Sdíleno pro obě platformy; verze 2.11.0-beta01 (už v projektu) |
| DI | Koin (`koin-core` v commonMain, `initKoin()` volané z obou platforem) | §2; KMP-nativní |
| Síť | Ktor Client + `content-negotiation` + `serialization-kotlinx-json`; engine OkHttp (Android) / Darwin (iOS) | §2 |
| Offline DB | SQLDelight 2.x + `android-driver` / `native-driver` | §2 |
| Logování | Napier | §2 (jednodušší multiplatformní logger) |
| Balíček | Ponechat `com.example.ta33` | Uživatel rozhodl balíček neměnit; přejmenování je izolovaná operace, dá se udělat kdykoli později bez dopadu na architekturu |
| iOS framework baseName | `Shared` (z `SharedLogic`) | Sjednocení názvu s modulem `:shared`; `.claude/project-stack.md` používá framework `Shared` |

---

## 4. IMPLEMENTATION STEPS

> Execute steps in order. Do not skip. Po každém „strukturálním" kroku (rename/přesun) spusť `./gradlew build`, ať se chyba chytí brzy.

### Step 1: Rozšířit `gradle/libs.versions.toml` o verze, knihovny a pluginy
**Goal**: Centralizovat všechny nové závislosti do version catalogu.
**Files**: `gradle/libs.versions.toml`

Přidat do `[versions]` (⚠️ verze OVĚŘIT proti Kotlin 2.4.0 — hlavně SKIE, viz §12):
```toml
coroutines = "1.10.2"
kotlinxSerialization = "1.8.1"
ktor = "3.2.0"
sqldelight = "2.1.0"
koin = "4.1.0"
napier = "2.7.1"
skie = "0.10.13"  # latest; řada 0.10.x deklaruje podporu Kotlin 2.0.0–2.4.0, SKIE ověří verzi při buildu
```
Do `[libraries]`:
```toml
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-contentNegotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-androidDriver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-nativeDriver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
napier = { module = "io.github.aakira:napier", version.ref = "napier" }
androidx-lifecycle-viewmodel = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", version.ref = "androidx-lifecycle" }
```
Do `[plugins]`:
```toml
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
skie = { id = "co.touchlab.skie", version.ref = "skie" }
```
**Done when**: Soubor obsahuje všechny výše uvedené záznamy; `./gradlew help` projde (catalog se parsuje).

---

### Step 2: Přejmenovat modul `sharedLogic` → `shared`
**Goal**: Sjednotit název s cílovým `:shared` a připravit na konsolidaci.
**Files**: `settings.gradle.kts`, složka `sharedLogic/` → `shared/`, `sharedLogic/build.gradle.kts`, `iosApp` framework reference

1. Přejmenovat adresář `sharedLogic/` → `shared/`.
2. V `settings.gradle.kts`: `include(":sharedLogic")` → `include(":shared")` (řádek 32). `:sharedUI` zatím ponech (smaže se ve Step 4).
3. V `shared/build.gradle.kts`: `namespace = "com.example.ta33.sharedLogic"` → `namespace = "com.example.ta33.shared"`; `baseName = "SharedLogic"` → `baseName = "Shared"`.
4. iOS: framework se teď jmenuje `Shared`. V `iosApp/iosApp/ContentView.swift` (a kdekoli jinde) `import SharedLogic` → `import Shared`. V Xcode projektu (`project.pbxproj`) upravit odkaz na embedded framework `SharedLogic` → `Shared` (název v „Frameworks, Libraries, and Embedded Content" a v build fázi).

**Done when**: `./gradlew :shared:build` projde; `grep -r "sharedLogic\|SharedLogic" --include=*.kts --include=*.swift` nic nevrací.

---

### Step 3: Ponechat balíček `com.example.ta33` (žádné přejmenování)
**Goal**: Vědomé rozhodnutí — balíček zůstává beze změny; tento krok jen zaznamenává, co se NEmění a co pohlídat.
**Files**: —

- Balíček `com.example.ta33` a `applicationId` `com.example.ta33` v `androidApp/build.gradle.kts` **zůstávají**.
- Namespace přejmenovaného modulu je `com.example.ta33.shared` (viz Step 2) — to je jen sufix modulu, ne změna kořenového balíčku.
- Compose resources generovaný balíček: dnes `ta33.sharedui.generated.resources.*`; po přesunu resources do modulu `shared` (Step 6/10) se změní na `ta33.shared.generated.resources.*`. To vyplývá z **názvu modulu**, ne z balíčku — vyřeší se tam.
- Pokud později padne rozhodnutí balíček přejmenovat, je to izolovaná operace (refactor v IDE) nezávislá na tomto plánu.

**Done when**: Nic k provedení; potvrzeno, že `com.example.ta33` zůstává napříč projektem.

---

### Step 4: Zrušit `sharedUI` — přesunout Compose UI do `androidApp`
**Goal**: Nativní Android UI v app modulu; sdílený modul bez Compose UI závislostí.
**Files**: `sharedUI/` (smazat), `androidApp/build.gradle.kts`, `androidApp/src/main/kotlin/com/example/ta33/App.kt` (nový), `settings.gradle.kts`, `build.gradle.kts` (root)

1. Přesunout `sharedUI/src/commonMain/kotlin/.../App.kt` → `androidApp/src/main/kotlin/com/example/ta33/App.kt`. Upravit import resources na `com.example.ta33` generovanou třídu `Res` z modulu `shared` (viz Step 6/Step 10). Prozatím lze `Image(painterResource(...))` demo nahradit textem, dokud nejsou resources přesunuté.
2. `androidApp/build.gradle.kts`: přidat Compose UI závislosti, které dřív byly v `sharedUI`:
   ```kotlin
   implementation(projects.shared)
   implementation(libs.compose.runtime)
   implementation(libs.compose.foundation)
   implementation(libs.compose.material3)
   implementation(libs.compose.ui)
   implementation(libs.androidx.lifecycle.viewmodelCompose)
   implementation(libs.androidx.lifecycle.runtimeCompose)
   implementation(libs.androidx.activity.compose)
   implementation(libs.compose.uiToolingPreview)
   debugImplementation(libs.compose.uiTooling)
   ```
   Odstranit `implementation(projects.sharedUI)`.
3. Smazat adresář `sharedUI/`.
4. `settings.gradle.kts`: odstranit `include(":sharedUI")`.
5. Root `build.gradle.kts`: pluginy zůstávají (`composeMultiplatform`, `composeCompiler` použije androidApp i shared pro resources).
6. Přesunout `sharedUI/src/commonMain/composeResources/` (drawable atd.) → `shared/src/commonMain/composeResources/` (dokončí se v Step 10).

**Done when**: `sharedUI` neexistuje; `./gradlew :androidApp:assembleDebug` projde; Android app zobrazí demo obrazovku.

---

### Step 5: Založit balíčkové vrstvy v `shared/commonMain`
**Goal**: Struktura Clean Architecture přes balíčky (§1 stacku).
**Files**: nové adresáře v `shared/src/commonMain/kotlin/com/example/ta33/`

Vytvořit balíčky (zatím i s prázdným `.gitkeep` nebo drobným placeholder souborem, ať se commitnou):
```
domain/         # modely, use-case rozhraní, business pravidla (bez frameworků)
data/           # repozitáře (impl), DTO, DataSource, mapování
presentation/   # ViewModely (StateFlow<UiState>)
di/             # Koin moduly + initKoin
core/           # utility, Result typy, logging fasáda nad Napier
feature/        # feature seskupení (denik, mapa, sken, profil) — prozatím prázdné
```
Přesunout stávající scaffold soubory: `Greeting.kt`, `GreetingUtil.kt` → `domain/` (nebo `core/`); `Platform.kt` (+ actual varianty v androidMain/iosMain) → `core/`.

**Done when**: Adresáře existují; `./gradlew :shared:build` projde po přesunu souborů (importy aktualizované).

---

### Step 6: Přidat knihovny do `shared/build.gradle.kts`
**Goal**: Sdílené jádro má síť, DB, DI, serializaci, logování, multiplatform ViewModel.
**Files**: `shared/build.gradle.kts`

1. Pluginy: přidat `alias(libs.plugins.composeMultiplatform)`, `alias(libs.plugins.composeCompiler)` (pro `components-resources` codegen), `alias(libs.plugins.kotlinSerialization)`, `alias(libs.plugins.sqldelight)`. (SKIE se přidá v Step 7.)
2. `commonMain.dependencies`:
   ```kotlin
   implementation(libs.kotlinx.coroutines.core)
   implementation(libs.kotlinx.serialization.json)
   implementation(libs.ktor.client.core)
   implementation(libs.ktor.client.contentNegotiation)
   implementation(libs.ktor.serialization.json)
   implementation(libs.sqldelight.runtime)
   implementation(libs.sqldelight.coroutines)
   implementation(libs.koin.core)
   implementation(libs.napier)
   implementation(libs.androidx.lifecycle.viewmodel)
   implementation(libs.compose.components.resources) // JEN resources, žádné compose.ui/foundation/material3
   ```
3. `androidMain.dependencies`: `implementation(libs.ktor.client.okhttp)`, `implementation(libs.sqldelight.androidDriver)`, `implementation(libs.koin.android)`.
4. `iosMain.dependencies`: `implementation(libs.ktor.client.darwin)`, `implementation(libs.sqldelight.nativeDriver)`.
5. SQLDelight blok:
   ```kotlin
   sqldelight {
       databases {
           create("Ta33Database") { packageName.set("com.example.ta33.data.db") }
       }
   }
   ```
   Vytvořit schéma `shared/src/commonMain/sqldelight/com/example/ta33/data/db/schema.sq` (minimální tabulka pro ověření codegenu).
6. Přidat `expect`/`actual` `DatabaseDriverFactory` (commonMain expect; androidMain `AndroidSqliteDriver`, iosMain `NativeSqliteDriver`).

**Done when**: `./gradlew :shared:build` projde; SQLDelight vygeneruje `Ta33Database`.

---

### Step 7: Přidat SKIE plugin na `shared`
**Goal**: Příjemná Swift observace `StateFlow`/`suspend`.
**Files**: `shared/build.gradle.kts`

Přidat `alias(libs.plugins.skie)` do bloku `plugins`. SKIE se váže na iOS framework definovaný v `iosTarget.binaries.framework { baseName = "Shared" }` — konfiguraci netřeba měnit, plugin ji převezme.

**Done when**: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` projde a vygeneruje framework `Shared` se SKIE příponami.

---

### Step 8: Vytvořit Koin `appModule` + `initKoin`
**Goal**: Funkční DI graf startovaný z obou platforem.
**Files**: `shared/src/commonMain/kotlin/com/example/ta33/di/AppModule.kt`, `Koin.kt`

```kotlin
// di/AppModule.kt
val appModule = module {
    single { DatabaseDriverFactory(/* platform */) } // přes expect/actual factory
    single<GreetingRepository> { GreetingRepositoryImpl(get()) }
    factory { GreetingViewModel(get()) }
}

// di/Koin.kt
fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(appModule)
}
// pohodlný přístup pro iOS bez Koin API:
object ViewModelProvider {
    fun greetingViewModel(): GreetingViewModel = KoinPlatform.getKoin().get()
}
```
Android: v `MainActivity`/`Application` volat `initKoin { androidContext(this@…) }` (nutné pro `AndroidSqliteDriver` context). iOS: v `iOSApp.swift` volat `KoinKt.doInitKoin()` (SKIE/ObjC název) v inicializaci.

**Done when**: Android i iOS app startuje bez pádu; `ViewModelProvider.greetingViewModel()` vrátí instanci.

---

### Step 9: Vytvořit demo sdílený `GreetingViewModel` se `StateFlow`
**Goal**: Důkaz Alza-style smyčky — jeden ViewModel, dvě UI.
**Files**: `shared/.../presentation/GreetingViewModel.kt`, `shared/.../domain/GreetingRepository.kt`, `shared/.../data/GreetingRepositoryImpl.kt`

```kotlin
// presentation/GreetingViewModel.kt
data class GreetingUiState(val text: String = "", val loading: Boolean = false)

class GreetingViewModel(private val repo: GreetingRepository) : ViewModel() {
    private val _state = MutableStateFlow(GreetingUiState())
    val state: StateFlow<GreetingUiState> = _state.asStateFlow()

    fun onGreetClicked() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val msg = repo.greeting()
            _state.update { GreetingUiState(text = msg, loading = false) }
        }
    }
}
```
`GreetingRepository` (domain rozhraní) + `GreetingRepositoryImpl` (data) vracející text (zatím z `Greeting()`; později Ktor/SQLDelight). Log přes Napier.

**Done when**: `./gradlew :shared:build`; ViewModel je registrovaný v Koin.

---

### Step 10: Napojit UI (Compose + SwiftUI) na sdílený ViewModel + lokalizace
**Goal**: Obě UI čtou `StateFlow` ze stejného ViewModelu; založena lokalizace.
**Files**: `androidApp/.../App.kt`, `iosApp/iosApp/ContentView.swift`, `shared/src/commonMain/composeResources/values/strings.xml`, `values-cs/strings.xml`, `shared/.../core/Strings.kt`

**Lokalizace (Compose resources v `shared`):**
- `shared/src/commonMain/composeResources/values/strings.xml` (en) + `values-cs/strings.xml` (cs) s klíčem např. `app_greeting_button`.
- iOS wrapper v `shared/.../core/Strings.kt`: sdílená funkce obalující non-`@Composable` `getString(Res.string.x)` (suspend / `runBlocking`), kterou SwiftUI volá (viz §12 stacku, Localization). Generovaná třída `Res` je nyní `com.example.ta33...` z modulu `shared`.

**Android (Compose)** — `App.kt`:
```kotlin
@Composable
fun App(viewModel: GreetingViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialTheme {
        Column(...) {
            Button(onClick = viewModel::onGreetClicked) { Text(stringResource(Res.string.app_greeting_button)) }
            if (state.text.isNotEmpty()) Text(state.text)
        }
    }
}
```
(`koinViewModel()` z `koin-compose-viewmodel` (`org.koin.compose.viewmodel`) — přidáno do androidApp.)

**iOS (SwiftUI přes SKIE)** — `ContentView.swift`:
```swift
import SwiftUI
import Shared

@MainActor class GreetingObservable: ObservableObject {
    private let vm = ViewModelProvider.shared.greetingViewModel()
    @Published var text: String = ""
    init() {
        Task { for await s in vm.state { self.text = s.text } } // SKIE dělá Flow async-sekvencí
    }
    func greet() { vm.onGreetClicked() }
}

struct ContentView: View {
    @StateObject private var model = GreetingObservable()
    var body: some View {
        VStack {
            Button("Greet") { model.greet() }
            if !model.text.isEmpty { Text(model.text) }
        }
    }
}
```

**Done when**: Android i iOS zobrazí text ze sdíleného ViewModelu po kliknutí; přepnutí jazyka telefonu mění popisek tlačítka.

---

### Step 11: Ověřit celý build a běh na obou platformách
**Goal**: Potvrdit success criteria end-to-end.
**Files**: —

```bash
./gradlew build
./gradlew :androidApp:assembleDebug
./gradlew :shared:allTests
# iOS: otevřít iosApp/iosApp.xcodeproj ve Xcode, vybrat simulátor, Build & Run
```
**Done when**: Všechny příkazy projdou; obě aplikace běží a demo smyčka funguje.

---

### Step 12: Aktualizovat `.claude/project-stack.md` §12 na nový skutečný stav
**Goal**: Strojově čtený kontrakt odpovídá realitě (jinak `/plan-*` a `/translate` hádají špatně).
**Files**: `.claude/project-stack.md`

Aktualizovat v §12:
- Module Structure: `sharedLogic`/`sharedUI` → jeden `shared` (balíček `com.example.ta33` beze změny); poznámka „iOS UI už je nativní SwiftUI (scaffold), Compose bridge neexistoval".
- „Koin/SQLDelight/Ktor/kotlinx.serialization ještě nejsou přidané" → přesunout do „(teď) přidané".
- Localization: `shared/src/commonMain/composeResources/values/strings.xml` (cesta v modulu `shared`), stav „založeno".
- DI: Koin „(teď) přidán", `initKoin()` umístění.
- Vyřešený rozpor scaffold × §7: přepsat — konverze na SwiftUI byla už ve scaffoldu; provedeno zrušení `sharedUI` a přesun Compose do `androidApp`.

**Done when**: §12 neobsahuje zastaralá tvrzení o `:shared`+`:androidApp` jako jediných modulech ani o chybějících knihovnách.

---

## 5. EDGE CASES & ERRORS

| Scenario | Expected Behavior | How to Handle |
|----------|-------------------|---------------|
| SKIE nekompatibilní s Kotlin 2.4.0 | Build linku frameworku selže | Ověřit matici kompatibility SKIE↔Kotlin; případně downgrade Kotlin nebo počkat na SKIE release; dočasně vypustit SKIE a použít ruční `Flow`→callback wrapper |
| `koinViewModel()` chybí závislost | Android build error | Přidat `io.insert-koin:koin-compose-viewmodel` (+ `koin-android`) do androidApp |
| SQLDelight codegen balíček nesedí | Chybí třída `Ta33Database` | Sladit `packageName` v `sqldelight {}` s cestou `.sq` souboru |
| iOS framework `Shared` se neembeduje po přejmenování | Runtime „framework not found" | Ve Xcode opravit Embed & Sign a Framework Search Paths na `Shared` |
| Compose resources codegen v `shared` bez UI | `Res` třída se nevygeneruje | Zajistit `composeMultiplatform` + `composeCompiler` plugin na `shared` a `composeResources/` na správné cestě |
| iOS `runBlocking` na main threadu pro getString | UI zamrznutí | Preferovat suspend variantu přes SKIE async; `runBlocking` jen pro krátké lokalizační lookupy |
| `initKoin` volán dvakrát | `KoinAppAlreadyStartedException` | Volat jen jednou (Android `Application.onCreate`, iOS app init); guard |

---

## 6. SECURITY CONSIDERATIONS

- **Input validation**: Ktor odpovědi deserializovat přes `kotlinx.serialization` s tolerancí (`ignoreUnknownKeys = true`), validovat doménová pravidla v domain vrstvě.
- **Auth/Access control**: V této fázi žádné přihlášení (token storage je Etapa 2 dle stacku) — neřešit.
- **Sensitive data**: Zatím žádná citlivá data; token/Keychain až Etapa 2.
- **Logging**: Napier — nelogovat případné budoucí tokeny/PII; demo logy jsou neutrální.

---

## 7. ASSUMPTIONS

Odvozeno ze vstupu a stavu repozitáře — ověřit:

1. **Balíček zůstává `com.example.ta33`**: uživatel rozhodl neměnit. Přejmenování (kdyby přišlo) je izolovaný IDE refactor nezávislý na tomto plánu.
2. **Konsolidace do jednoho `shared` modulu** (zrušení `sharedUI`): user zvolil „balíčkové vrstvení uvnitř jednoho :shared modulu". Pokud by měl `sharedUI` zůstat jako samostatný Android-UI modul, je to odchylka — ale to by znovu zavádělo dělení, které user odmítl.
3. **iOS je už nativní SwiftUI** (scaffold `ContentView.swift`): potvrzeno čtením souborů; premisa „odstranit Compose bridge / MainViewController.kt" z původního zadání je bezpředmětná (viz Sekce 10).
4. **SKIE přidáváme i bez reálné feature**: demo ViewModel slouží jako ověření interop smyčky. Pokud je cílem jen „připravit", lze demo později smazat.
5. **Verze knihoven jsou orientační** a musí se ověřit proti Kotlin 2.4.0 / Compose MP 1.11.1 (stack §9 „ověřuj aktuální verze"). Zvlášť SKIE a multiplatform Lifecycle ViewModel (beta).
6. **User opted out of clarification?** Ne — scope potvrzen přes AskUserQuestion (A+B+C, balíčkové vrstvení).
7. **AGP 9 `androidLibrary {}` DSL** zůstává (nový KMP wizard); knihovny s tímto DSL fungují (SQLDelight/Ktor ano).

> Open questions v Sekci 12.

---

## 8. QUICK REFERENCE

### Files to Modify
- `gradle/libs.versions.toml` — přidat verze/knihovny/pluginy (Step 1)
- `settings.gradle.kts` — `:sharedLogic`→`:shared`, odebrat `:sharedUI`
- `shared/build.gradle.kts` (přejm. z `sharedLogic/`) — namespace, framework `Shared`, knihovny, pluginy, SQLDelight, SKIE
- `androidApp/build.gradle.kts` — Compose UI deps, `projects.shared` (namespace/applicationId beze změny)
- `androidApp/.../MainActivity.kt` — `initKoin`, `App()` s ViewModel
- `iosApp/iosApp/ContentView.swift` — `import Shared`, SKIE observace ViewModelu
- `iosApp/iosApp/iOSApp.swift` — `doInitKoin()`
- `.claude/project-stack.md` — §12 aktualizace

### Files to Create
- `shared/.../presentation/GreetingViewModel.kt`
- `shared/.../domain/GreetingRepository.kt`
- `shared/.../data/GreetingRepositoryImpl.kt`
- `shared/.../di/AppModule.kt`, `di/Koin.kt`
- `shared/.../core/Strings.kt`, `core/DatabaseDriverFactory.kt` (+ actual v android/iosMain)
- `shared/src/commonMain/sqldelight/com/example/ta33/data/db/schema.sq`
- `shared/src/commonMain/composeResources/values/strings.xml`, `values-cs/strings.xml`
- `androidApp/.../App.kt` (přesun ze `sharedUI`)

### Files/Dirs to Delete
- `sharedUI/` (celý modul)

### Dependencies (klíčové, verze ověřit)
- `io.insert-koin:koin-core:4.1.0` (+ `koin-android`) — DI
- `io.ktor:ktor-client-*:3.2.0` — síť (OkHttp/Darwin engine)
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1`
- `app.cash.sqldelight:*:2.1.0` — offline DB
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2`
- `io.github.aakira:napier:2.7.1` — logování
- `co.touchlab.skie:0.10.4` (Gradle plugin) — Swift interop
- `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0-beta01` — sdílený ViewModel

### Commands
```bash
# Build & verify
./gradlew build
./gradlew :androidApp:assembleDebug
./gradlew :shared:allTests
# iOS: Xcode → iosApp/iosApp.xcodeproj → Run
# Kontrola, že nezůstaly staré názvy modulů (balíček com.example.ta33 zůstává!)
grep -rn "sharedLogic\|sharedUI" --include=*.kt --include=*.kts --include=*.swift .
```

---

## 9. DESIGN REFERENCE

> UI se v tomto plánu mění jen minimálně (demo obrazovka); reálné design tokeny z prototypu jsou samostatný úkol (§11 stacku). Proto jen lehká reference.

### Visual Spec
Žádný Figma spec pro tento plán — demo obrazovka je funkční ověření, ne finální design. Design tokeny z prototypu se tahají zvlášť (stack §11).

### Component/Screen Mapping
- Demo „Greeting" obrazovka → `androidApp/.../App.kt` (Compose) a `iosApp/iosApp/ContentView.swift` (SwiftUI), obě napojené na `GreetingViewModel` v `shared/.../presentation`.

### Style Mapping
| Design Spec | Code Equivalent | Value |
|-------------|-----------------|-------|
| Barvy/typografie | `MaterialTheme` (default) na Androidu, systémové na iOS | zatím default — **nehardcodovat**, čeká na tokeny (stack §12 Theming) |
| Text tlačítka | `Res.string.app_greeting_button` (Compose resources) | en + cs varianta |

---

## 10. CORRECTIONS FROM CURRENT STATE

| What | Before (Current) | After (Target) |
|------|------------------|----------------|
| Sdílené moduly | `sharedLogic` + `sharedUI` (dva) | jeden `shared` s balíčkovým vrstvením |
| iOS UI | **Už nativní SwiftUI** (`ContentView.swift` → framework `SharedLogic`) | Nativní SwiftUI → framework `Shared` (+ SKIE, napojení na sdílený ViewModel) |
| Compose→UIViewController bridge | **Neexistuje** (žádný `MainViewController.kt`) | Neexistuje (žádná změna — původní premisa byla mylná) |
| Compose UI umístění | `sharedUI/commonMain` (`App.kt` + compose.ui/foundation/material3) | `androidApp` (nativní Android UI) |
| `shared/commonMain` Compose deps | (v `sharedUI`) plný Compose UI | jen `components-resources` (lokalizace) |
| Balíček | `com.example.ta33` | `com.example.ta33` (beze změny — vědomě) |
| iOS framework baseName | `SharedLogic` | `Shared` |
| Knihovny jádra | žádné (jen kotlin-test, Compose) | Koin, Ktor, kotlinx.serialization, SQLDelight, Napier, coroutines, multiplatform ViewModel |
| Swift interop | ruční (obyč. framework) | SKIE (Flow/StateFlow/suspend) |
| Sdílené ViewModely | žádné | `GreetingViewModel` (StateFlow) konzumovaný oběma UI |
| Vrstvy | ploché (`Greeting.kt`, `Platform.kt`) | `domain`/`data`/`presentation`/`di`/`core`/`feature` |
| Lokalizace | žádná (jen `drawable/`) | Compose resources `values/` + `values-cs/` |

---

## 11. CHANGELOG

| Date | Change |
|------|--------|
| 2026-07-06 | Initial plan created |
| 2026-07-06 | Rozhodnuto balíček neměnit — `com.example.ta33` zůstává (Step 3 změněn na „ponechat"); jediné přejmenování je modul `sharedLogic`→`shared` a iOS framework `SharedLogic`→`Shared` |
| 2026-07-07 | **Implementováno (12/12).** Odchylky nutné při realizaci: (1) **Gradle wrapper 8.13→9.1.0** — AGP 9.0.1 to vyžaduje, scaffold se jinak nesestavil (pre-existing blocker); (2) **Ktor 3.2.0→3.5.0** — 3.2.0 měl D8/dex bug (identifikátor s mezerami „use streaming syntax"); (3) **`-lsqlite3` v `iosApp/Configuration/Config.xcconfig`** — SQLDelight native driver ze statického frameworku `Shared`; (4) `DatabaseDriverFactory` realizován jako `expect/actual platformModule` poskytující `SqlDriver` (místo expect/actual třídy s nesourodými konstruktory); (5) do `shared/commonMain` přidán `compose.runtime` (compose compiler ho vyžaduje vedle `components-resources`); (6) přidány `koin-android` + `koin-compose-viewmodel` do `androidApp`, vytvořen `Ta33Application`; (7) `compose.resources { publicResClass=true; packageOfResClass="com.example.ta33.resources" }`; (8) scaffold test třídy `SharedLogic*Test`→`Shared*Test`. Zjištění vs. původní §12 stacku: scaffold měl moduly `sharedLogic`+`sharedUI` (ne `:shared`), iOS byl už nativní SwiftUI a Compose→UIViewController bridge nikdy neexistoval. |

---

## 12. OPEN QUESTIONS & ALTERNATIVE APPROACHES

### 12.1 Alternative Approaches Considered

| Approach | Pros | Cons | Selected? |
|----------|------|------|-----------|
| **A: Jeden `shared` modul + balíčkové vrstvy, Compose UI do `androidApp`, SKIE, všechny knihovny** | Odpovídá `:shared` modelu v §12 stacku; nejmenší Gradle složitost; učí strukturu bez overheadu; jedna změna „naráz" (Alza filozofie) | Velký jednorázový diff (rename modulu + balíčku); nutná pečlivá aktualizace Xcode projektu | ✅ |
| **B: Ponechat `sharedLogic`+`sharedUI`, jen přidat knihovny do `sharedLogic` a SKIE** | Menší zásah; nemění strukturu modulů | Zachovává „sdílené Compose UI" modul, který §7/§12 stacku chce zrušit; nesplňuje Alza-style záměr; user zvolil konsolidaci | — |
| **C: Plný Gradle multi-modul split `:core`/`:data`/`:domain`/`:feature-*` hned** | Nejvěrnější Alze; nejlepší izolace | Výrazně víc build setupu a rizika pro sólo projekt bez UI; user explicitně zvolil balíčkové vrstvení | — |

**Why the selected approach won**: A dává cílový Alza-style stav (sdílené jádro + ViewModely + nativní UI + SKIE) s minimem strukturálního rizika, přesně dle volby uživatele (A+B+C rozsah, balíčkové vrstvení) a mentálního modelu `:shared` v §12 stack dokumentu.

### 12.2 Open Questions

- [x] **Přejmenovat balíček?** — Rozhodnuto: NE, zůstává `com.example.ta33`.
- [x] **SKIE ↔ Kotlin 2.4.0?** — Ověřeno: Kotlin 2.4.0 je nejnovější stabilní (2026-06-03), SKIE 0.10.x deklaruje podporu 2.0.0–2.4.0. Použít SKIE `0.10.13`; SKIE si verzi kontroluje při buildu. Fallback při nesouladu: dočasně vynechat SKIE (ruční `Flow` wrapper).
- [x] **`koinViewModel()` vs ruční instancování na Androidu?** — Vyřešeno: přidán **`koin-compose-viewmodel`** (ne `koin-androidx-compose`), `App(viewModel: GreetingViewModel = koinViewModel())` z `org.koin.compose.viewmodel`.
- [x] **DB engine hned, nebo jen kostra?** — Vyřešeno: minimální `.sq` schéma (`Checkpoint`) jen pro ověření codegenu; reálné schéma (Účastník/Trasa/Kontrola…) až s doménovým modelem (§11 stacku).
- [x] **iOS framework rename ve Xcode** — Vyřešeno: scaffold používá synchronizované složky (Xcode 16) a framework přes build phase `:shared:embedAndSignAppleFrameworkForXcode`; stačila změna názvu v build phase + `import Shared`, žádné Search Paths. Navíc `-lsqlite3` v `Config.xcconfig` (SQLDelight native driver).

### 12.3 Suggestions & Follow-ups

- Po konverzi přidat **detekt + ktlint** a doplnit příkazy do §12 stacku (stack §6/§7 „(cíl)").
- Založit reálný **doménový model** (Účastník, Trasa, Kontrola vč. GPS+poloměr, Sebrání, Časy, stav synchronizace) → z něj SQLDelight schéma (stack §11).
- Zvážit **CI (GitHub Actions)** build+test na push (stack §6, nice-to-have).
- Feature moduly (`feature/denik`, `mapa`, `sken`, `profil`) naplnit reálnými obrazovkami — samostatné `/plan-create`.
- MapLibre + offline dlaždice + GPS je nejrizikovější část (stack §9) — samostatný plán s terénním testem.
