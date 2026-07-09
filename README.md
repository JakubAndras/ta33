This is a Kotlin Multiplatform project targeting Android and iOS, built **Alza-style**: one shared
Kotlin core (including ViewModels) with **native UI per platform** — Jetpack Compose on Android,
SwiftUI on iOS. UI is *not* shared; everything else is.

* [/shared](./shared/src) is the shared core used by both platforms. The most important subfolder is
  [commonMain](./shared/src/commonMain/kotlin), organized into layer packages:
  - `domain/` — models, use cases, business rules (framework-free)
  - `data/` — repositories, DTOs, data sources (+ [SQLDelight `.sq` schema](./shared/src/commonMain/sqldelight))
  - `presentation/` — ViewModels exposing `StateFlow<UiState>`
  - `di/` — Koin modules (`appModule`, `platformModule`) and `initKoin`
  - `core/` — `Platform`, localized-string helpers, utilities
  - `feature/` — feature groupings (currently empty)

  Platform-specific `actual` implementations live in [androidMain](./shared/src/androidMain/kotlin) and
  [iosMain](./shared/src/iosMain/kotlin). Shared localization/assets are in
  [composeResources](./shared/src/commonMain/composeResources) (`values/`, `values-cs/`). The iOS
  framework is named `Shared` and is consumed from Swift via [SKIE](https://skie.touchlab.co/) for
  ergonomic `Flow`/`StateFlow`/`suspend` interop.

* [/androidApp](./androidApp/src) is the Android application — native Jetpack Compose UI
  (`App.kt`, `MainActivity.kt`) plus `Ta33Application` (starts Koin).

* [/iosApp](./iosApp/iosApp) is the iOS application — native SwiftUI. `iOSApp.swift` starts Koin via
  `KoinKt.doInitKoin()`; `ContentView.swift` observes the shared ViewModel's `StateFlow` through SKIE.

### Running locally

> **Prerequisites**: Gradle **9.1.0** wrapper (AGP 9 — already pinned). The iOS app links `-lsqlite3`
> (see `iosApp/Configuration/Config.xcconfig`), required by the SQLDelight native driver.

#### From the IDE (Run/Debug button)

> Note: this is **not** Flutter. In Flutter one toolbar dropdown runs a single `main.dart` on any
> device (incl. iOS). KMP has no single cross-platform run target — Android runs from Android Studio,
> iOS from Xcode (or an iOS simulator via the KMP plugin). The setup below gets you as close as KMP allows.

- **Android** — three shared run configurations are committed in [`.run/`](./.run):
  - **`androidApp`** (Android App type) — the full experience: **this is the one with the device dropdown
    in the toolbar** (emulator + connected phones), plus **Run ▷** / **Debug 🐞**, logcat, debugger, and
    auto-launch. Depends on the IJ module name; if Android Studio reports the module can't be found, open
    *Run → Edit Configurations → androidApp* and re-select module `androidApp.main` (the name can differ per
    AS version) — one click, then it sticks. **Prefer this config** — the two below are module-independent
    fallbacks for when it won't resolve.
  - **`androidApp (install + launch)`** (Shell type) — runs [`scripts/run-android.sh`](./scripts/run-android.sh):
    builds, installs, **and launches** the app in one click. Module-independent. It has **no toolbar device
    dropdown** (that UI is Android-App-only), so it targets: `$ANDROID_SERIAL` if set → else the single
    connected device → else it lists connected devices and asks you to set `ANDROID_SERIAL` (Edit
    Configurations → *Environment variables*).
  - **`androidApp (Gradle install)`** (Gradle type) — runs `:androidApp:installDebug` only (build + install,
    no launch). Also module-independent; respects `ANDROID_SERIAL`. Launch afterwards with
    `adb shell am start -n com.example.ta33/.MainActivity`.

  > **Device selection:** the toolbar device picker exists only for the **Android App** config (`androidApp`).
  > The Shell/Gradle fallbacks pick the device via `ANDROID_SERIAL` / auto-detect instead. So for point-and-click
  > device choice, use `androidApp`; use the fallbacks only when the module won't resolve.
- **iOS** — two options from Android Studio:
  - **`iosApp (simulator)`** (Shell, committed in [`.run/`](./.run)) — runs
    [`scripts/run-ios.sh`](./scripts/run-ios.sh): builds the `Shared` framework + the app via `xcodebuild`,
    boots a simulator, installs, **and launches** — one click, **no KMP plugin needed**. Simulator selection:
    `$IOS_SIMULATOR` (name or UDID) → a booted sim → preferred iPhone → first available.
  - Or install the **Kotlin Multiplatform** plugin (JetBrains; *Settings → Plugins → Marketplace →
    "Kotlin Multiplatform"*, restart) for a native `iosApp` run config with a simulator picker.

  Running on a *physical* iPhone needs code signing either way — do that from Xcode (see below).

The command-line equivalents are below.

#### Android — emulator

If you have no AVD yet, create one (needs a JDK 17+ for `avdmanager`; Android Studio bundles one in `jbr/`):

```bash
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # any JDK 17+
# Use any installed system image — list them with:
#   "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --list_installed | grep system-images
echo "no" | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager" create avd \
  -n ta33_test -k "system-images;android-36.1;google_apis_playstore;arm64-v8a" -d pixel_6
```

Then boot it, install, and launch:

```bash
"$ANDROID_SDK_ROOT/emulator/emulator" -avd ta33_test &        # or use Android Studio's Device Manager
adb wait-for-device
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n com.example.ta33/.MainActivity
```

Tap **Click me!** → the shared ViewModel emits and Compose shows `Compose: Hello, Android …`.
Switch the device language to Czech → the button reads **Klikni na mě!** (localization via `stringResource`).

#### Android — real device

1. On the phone: enable **Developer options → USB debugging**, plug in over USB, accept the trust prompt.
2. `adb devices` should list it, then the same `install` / `am start` commands above. The debug APK is
   debug-signed, so **no Google/Play account or signing setup is needed**.

#### iOS — simulator

```bash
xcrun simctl boot "iPhone 16"; open -a Simulator
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'name=iPhone 16' CODE_SIGNING_ALLOWED=NO build
xcrun simctl install booted "$(xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -showBuildSettings 2>/dev/null | awk '/CODESIGNING_FOLDER_PATH/{ $1=$2=""; sub(/^  = /,""); print; exit }')"
xcrun simctl launch booted com.example.ta33.TA33
```

Or just open `iosApp/iosApp.xcodeproj` in Xcode, pick a simulator, and Run.

#### iOS — real device

Physical devices **require code signing** (the simulator does not):

1. Open `iosApp/iosApp.xcodeproj` in Xcode → target `iosApp` → **Signing & Capabilities** → select your **Team**
   (or set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig`, which feeds `PRODUCT_BUNDLE_IDENTIFIER`).
2. Plug in the iPhone, trust the Mac, select it as the run destination, and Run. A free Apple ID works for
   development builds; the App Store is not involved.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- All shared tests (both platforms): `./gradlew :shared:allTests`
- Everything: `./gradlew check`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
See [.claude/project-stack.md](./.claude/project-stack.md) for the full technical stack and the
[conversion plan](./.claude/plans/alza-style-kmp-conversion.md) for how the scaffold was migrated.
