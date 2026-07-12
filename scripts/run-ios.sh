#!/usr/bin/env bash
# Build + install + launch the iOS app on a simulator, straight from Android Studio (no KMP plugin needed).
# xcodebuild also runs the "Compile Kotlin Framework" phase, so the shared `Shared` framework is built too.
#
# Simulator selection (shell configs have no toolbar device dropdown):
#   1. $IOS_SIMULATOR (device name or UDID)   2. a currently booted simulator
#   3. a preferred iPhone (16/15/17)           4. the first available iPhone.
#
# Physical iPhones need code signing - use Xcode for those (set your Team / TEAM_ID). This script is
# simulator-only (CODE_SIGNING_ALLOWED=NO).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROJECT="iosApp/iosApp.xcodeproj"
SCHEME="iosApp"
CONFIG="Debug"
BUNDLE_ID="com.example.ta33.TA33"

udid_by_name() { xcrun simctl list devices available | grep -F "$1 (" | head -1 | awk -F'[()]' '{print $2}'; }

resolve_sim() {
  if [ -n "${IOS_SIMULATOR:-}" ]; then
    if xcrun simctl list devices | grep -q "($IOS_SIMULATOR)"; then echo "$IOS_SIMULATOR"; else udid_by_name "$IOS_SIMULATOR"; fi
    return
  fi
  local booted; booted="$(xcrun simctl list devices booted | awk -F'[()]' '/Booted/{print $2; exit}')"
  [ -n "$booted" ] && { echo "$booted"; return; }
  local udid
  for name in "iPhone 16" "iPhone 15" "iPhone 17"; do
    udid="$(udid_by_name "$name")"; [ -n "$udid" ] && { echo "$udid"; return; }
  done
  xcrun simctl list devices available | grep -E "iPhone" | head -1 | awk -F'[()]' '{print $2}'
}

SIM="$(resolve_sim)"
[ -n "$SIM" ] || { echo "No iOS simulator available. Create one in Xcode (Window → Devices and Simulators)." >&2; exit 1; }
echo "▶ Simulator: $SIM"

xcrun simctl boot "$SIM" 2>/dev/null || true
open -a Simulator

echo "▶ Building (this also builds the Kotlin 'Shared' framework)…"
xcodebuild -project "$PROJECT" -scheme "$SCHEME" -configuration "$CONFIG" \
  -destination "id=$SIM" CODE_SIGNING_ALLOWED=NO build

APP="$(xcodebuild -project "$PROJECT" -scheme "$SCHEME" -configuration "$CONFIG" \
  -sdk iphonesimulator -showBuildSettings 2>/dev/null \
  | awk -F' = ' '/ TARGET_BUILD_DIR /{t=$2} / FULL_PRODUCT_NAME /{f=$2} END{print t"/"f}')"
[ -d "$APP" ] || { echo "Built .app not found at: $APP" >&2; exit 1; }

xcrun simctl install "$SIM" "$APP"
xcrun simctl launch "$SIM" "$BUNDLE_ID"
echo "✅ Launched $BUNDLE_ID on $SIM"
