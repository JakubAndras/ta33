#!/usr/bin/env bash
# Build + install + launch the Android debug app on a connected device/emulator.
# Module-independent (uses the stable Gradle path :androidApp), so it works even when
# Android Studio can't resolve the IJ module for the Android App run config.
#
# Device selection (no toolbar dropdown for shell configs - that's Android App-only):
#   1. $ANDROID_SERIAL if set   2. the single connected device   3. otherwise lists them and exits.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APP_ID="com.example.ta33"
ACTIVITY="com.example.ta33/.MainActivity"

# Locate adb
if command -v adb >/dev/null 2>&1; then
  ADB="$(command -v adb)"
else
  ADB="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}/platform-tools/adb"
fi
[ -x "$ADB" ] || { echo "adb not found (set ANDROID_SDK_ROOT)." >&2; exit 1; }

# Pick the target device
serials="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
count="$(printf '%s\n' "$serials" | grep -c . || true)"

if [ -n "${ANDROID_SERIAL:-}" ]; then
  TARGET="$ANDROID_SERIAL"
elif [ "$count" -eq 1 ]; then
  TARGET="$serials"
elif [ "$count" -eq 0 ]; then
  echo "No device/emulator connected. Start one (Device Manager, or: emulator -avd ta33_test)." >&2
  exit 1
else
  echo "Multiple devices connected - set ANDROID_SERIAL to choose one:" >&2
  printf '%s\n' "$serials" | sed 's/^/  - /' >&2
  echo "e.g. ANDROID_SERIAL=$(printf '%s\n' "$serials" | head -1)   (Edit Configurations → Environment variables)" >&2
  exit 1
fi

export ANDROID_SERIAL="$TARGET"   # so Gradle installs to this device too
echo "▶ Target device: $TARGET"
./gradlew :androidApp:installDebug
"$ADB" -s "$TARGET" shell am start -n "$ACTIVITY"
echo "✅ Launched $APP_ID on $TARGET"
