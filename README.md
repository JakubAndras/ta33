# TA33

A Kotlin Multiplatform app for Android and iOS: one shared Kotlin core (including ViewModels) with
**native UI per platform** — Jetpack Compose on Android, SwiftUI on iOS. Everything except the UI layer
is shared.

## Structure

- **[/shared](./shared/src)** — shared core. [`commonMain`](./shared/src/commonMain/kotlin) is organized
  into layer packages:
  - `domain/` — models, use cases, business rules (framework-free)
  - `data/` — repositories, DTOs, data sources (+ [SQLDelight schema](./shared/src/commonMain/sqldelight))
  - `presentation/` — ViewModels exposing `StateFlow<UiState>`
  - `di/` — Koin modules and `initKoin`
  - `core/` — platform abstractions, localized-string helpers, utilities

  Platform `actual` implementations live in [androidMain](./shared/src/androidMain/kotlin) /
  [iosMain](./shared/src/iosMain/kotlin). Shared localization/assets are in
  [composeResources](./shared/src/commonMain/composeResources). The iOS framework (`Shared`) is consumed
  from Swift via [SKIE](https://skie.touchlab.co/) for ergonomic `Flow`/`StateFlow`/`suspend` interop.
- **[/androidApp](./androidApp/src)** — Android app (native Jetpack Compose; `Ta33Application` starts Koin).
- **[/iosApp](./iosApp/iosApp)** — iOS app (native SwiftUI; `iOSApp.swift` starts Koin via `KoinKt.doInitKoin()`).

## Running

> **Prerequisites**: Gradle **9.1.0** wrapper (AGP 9, already pinned). The iOS app links `-lsqlite3`
> (see `iosApp/Configuration/Config.xcconfig`), required by the SQLDelight native driver.

**From the IDE** — shared run configs are committed in [`.run/`](./.run): `androidApp` (Android, with the
toolbar device picker) and `iosApp (simulator)` (builds, boots a simulator, installs, and launches in one
click). Or open `iosApp/iosApp.xcodeproj` in Xcode and Run.

**Android (CLI)**:

```bash
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n com.example.ta33/.MainActivity
```

The debug APK is debug-signed — no Google/Play account or signing setup needed. For a physical device,
enable **Developer options → USB debugging** first.

**iOS (CLI)**:

```bash
xcrun simctl boot "iPhone 16"; open -a Simulator
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'name=iPhone 16' CODE_SIGNING_ALLOWED=NO build
xcrun simctl install booted "$(xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -showBuildSettings 2>/dev/null | awk '/CODESIGNING_FOLDER_PATH/{ $1=$2=""; sub(/^  = /,""); print; exit }')"
xcrun simctl launch booted com.example.ta33.TA33
```

A physical iPhone **requires code signing**: in Xcode, target `iosApp` → **Signing & Capabilities** →
select your **Team** (a free Apple ID works for development builds).

## Tests

```bash
./gradlew :shared:allTests   # shared tests (both platforms)
./gradlew check              # everything
```

---

See [.claude/project-stack.md](./.claude/project-stack.md) for the full technical stack.
Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
