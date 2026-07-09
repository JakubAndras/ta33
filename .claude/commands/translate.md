---
allowed-tools: Read, Write, Edit, Glob, Grep, MultiEdit
description: Translation assistant for this project. Finds missing translations in the specified language files and translates them from the source language with proper context awareness. Reads the localization setup from .claude/project-stack.md.
argument-hint: $LANGUAGES
model: opus
---

## Task Overview
Translate missing translations from the source language into the specified target languages: $LANGUAGES

## Variables

STACK_FILE: `.claude/project-stack.md`
LANGUAGES: $ARGUMENTS

## Project Stack Reference

This command is stack-agnostic — it does NOT assume Flutter, JSON files, or any specific i18n library. All localization details come from `STACK_FILE`.

**Before doing anything, read `STACK_FILE` and extract its "Localization / Translations" section:**
- **System** — the i18n library / mechanism in use (e.g. moko-resources, Lyricist, Compose resources, native `strings.xml` / `Localizable.strings`)
- **Source-of-truth (source language) file** — where the original strings live
- **Per-language files / directories** — where each locale's strings live and how they are named
- **How keys are referenced in code** — the pattern to grep for usage/context
- **Files this command MAY write** — respect any restriction on which locales are hand-translated here
- **Placeholder format** — the exact placeholder syntax that must be preserved

**If `STACK_FILE` does not exist or has no Localization section**, STOP and tell the user to create it (see `.claude/project-stack.template.md`). Do not guess the localization layout.

## Process Steps

### 1. For each target language specified in `LANGUAGES`:
- Confirm the language is one this command is allowed to write (per `STACK_FILE`). Skip and report any that are handled by a separate workflow.
- Locate the target language's translation file/resource using the paths from `STACK_FILE`.
- Identify all keys that are **missing** — empty values, or keys present in the source language but absent from the target. Use whatever "missing" means for this project's format (empty string, missing entry, untranslated marker).
- Build a list of missing keys per language.

### 2. For each missing translation key:
- Look up the source text from the source-of-truth file (per `STACK_FILE`) for that specific key.
- Search the codebase for usage context using the reference pattern from `STACK_FILE` (e.g. the key's generated accessor or resource lookup). Read surrounding code to understand:
  - Is this a button label, error message, title, description, etc.?
  - What screen/component/feature does it belong to?
  - Are there placeholders (per the format in `STACK_FILE`) that must be preserved?

### 3. Generate contextually appropriate translations:
- Base the translation on the source text.
- Consider the code context and UI usage.
- Maintain consistent terminology across the app (reuse existing translations for the same term).
- Preserve placeholders exactly as they appear in the source (format per `STACK_FILE`).
- Match the tone to context (formal for errors, concise for buttons, friendly for UI copy).

### 4. Update the target language files:
- Fill in the missing translations in place.
- Preserve the file's existing formatting, structure, ordering, and encoding.
- Keep the file syntactically valid for its format (XML, JSON, `.strings`, etc.).
- Do NOT overwrite existing non-empty translations.

### 5. Provide a summary:
- Translations added per language.
- Any languages skipped because they are out of this command's scope.
- Challenging translations that might need human review.
- Keys that couldn't be translated due to missing context.

## Important Guidelines
- **Context is crucial**: a word like "scan" can be a noun or a verb depending on usage — check the code.
- **Preserve placeholders**: keep the placeholder syntax defined in `STACK_FILE` exactly.
- **Maintain consistency**: same source term → same translation across the app.
- **Consider UI constraints**: button text concise, error messages clear.
- **Cultural adaptation**: adapt cultural context, not just words.
- **Respect scope**: only write the locale files `STACK_FILE` permits.

## Quality Checks
- All modified files remain valid for their format.
- No existing translations were overwritten.
- Placeholder formatting is preserved.
- Translations fit the UI context.

Proceed with translating missing keys for the specified languages: $ARGUMENTS
