# Project Stack

> This file is the single source of truth for this project's technology, conventions, and commands.
> The `/plan-create`, `/plan-implement`, `/plan-update`, and `/translate` commands read this file
> instead of hardcoding any stack-specific detail.
>
> Copy this file to `.claude/project-stack.md` and fill in every `TODO`. Delete this template comment when done.
> Keep section headings intact — the commands locate information by these headings.

## 1. Project Overview

- **Type**: TODO (e.g. Kotlin Multiplatform — shared business logic, native Android + iOS UI)
- **Modules**: TODO (e.g. `shared` = common/KMP code, `androidApp` = Android app, `iosApp` = iOS app)
- **Primary language(s)**: TODO (e.g. Kotlin, Swift)

## 2. Module Structure

> Where source code lives, so commands know what paths to read and modify.

- TODO (e.g. `shared/src/commonMain/kotlin/...` — shared logic)
- TODO (e.g. `shared/src/androidMain/kotlin/...` — Android-specific)
- TODO (e.g. `shared/src/iosMain/kotlin/...` — iOS-specific)
- TODO (e.g. `androidApp/src/main/kotlin/...` — Android UI)
- TODO (e.g. `iosApp/iosApp/...` — iOS UI)

## 3. Build & Verification Commands

> Exact commands the executor runs to confirm work is correct. One per purpose.

- **Compile / build**: TODO (e.g. `./gradlew :shared:build` or `./gradlew assembleDebug`)
- **Static analysis / lint**: TODO (e.g. `./gradlew detekt` and/or `./gradlew ktlintCheck`)
- **Tests**: TODO (e.g. `./gradlew :shared:allTests` or `./gradlew testDebugUnitTest`)
- **Format**: TODO (e.g. `./gradlew ktlintFormat`)

## 4. Dependency Injection

> How and where services / view models are registered.

- **Framework**: TODO (e.g. Koin, Kodein, manual)
- **Where to register**: TODO (e.g. `shared/src/commonMain/kotlin/.../di/AppModule.kt`)

## 5. Theming / Design Tokens

> Where colors, spacing, typography, dimensions come from. Never hardcode these.

- **Android (Compose)**: TODO (e.g. `androidApp/src/main/kotlin/.../ui/theme/`)
- **iOS (SwiftUI)**: TODO (e.g. `iosApp/iosApp/Theme/`)
- **Shared tokens (if any)**: TODO

## 6. Localization / Translations

> Translation system and the source-of-truth files. Used by `/translate`.

- **System**: TODO (e.g. moko-resources, Lyricist, Compose resources, or native strings.xml + Localizable.strings)
- **Source-of-truth (English) file**: TODO (e.g. `shared/src/commonMain/moko-resources/base/strings.xml`)
- **Per-language files / directories**: TODO (e.g. `.../<lang>/strings.xml`)
- **How keys are referenced in code**: TODO (e.g. `MR.strings.<key>`, `stringResource(...)`)
- **Files `/translate` MAY write**: TODO (e.g. only `en` and `cs`; other locales handled elsewhere)
- **Placeholder format to preserve**: TODO (e.g. `%s`, `%1$s`, `{name}`)

## 7. Code Style Conventions

- **Max line width**: TODO (e.g. 120)
- **Other conventions**: TODO (naming, file organization, etc.)

## 8. Code Generation

> Codegen tooling and when it must run after changes.

- **Tooling**: TODO (e.g. KSP, Room, SQLDelight, kotlinx.serialization)
- **When to run**: TODO (e.g. after changing Room `@Entity` / `@Dao` or `@Serializable` models)
- **Command**: TODO (e.g. `./gradlew kspCommonMainKotlinMetadata` or handled by a normal build)

## 9. Plan Output Directory

> Where `/plan-create` and `/plan-update` store implementation plans.

- **Directory**: TODO (e.g. `.claude/plans` or `docs/implementation-plans`)

## 10. Ticket / Branch Conventions (optional)

- **Ticket prefix example**: TODO (e.g. `TA-1234`)
- **Branch naming**: TODO (e.g. `feature/TA-1234_description`)
