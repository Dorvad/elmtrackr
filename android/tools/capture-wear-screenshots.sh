#!/usr/bin/env bash
#
# Captures Play-compliant Wear OS store screenshots from a connected watch or
# Wear OS emulator.
#
# Google rejected the August listing because the Wear screenshots were placed
# inside device frames and carried added text and backgrounds. The rule is that
# a Wear screenshot must be the app's own interface and nothing else, so the
# only safe way to produce one is to read the framebuffer straight off the
# device: no compositing, no mockup, no border, no caption.
#
#   Usage:  tools/capture-wear-screenshots.sh [output-dir] [device-serial]
#
#   Default output dir: android/build/play-screenshots
#
# Walk the watch to the screen you want between prompts; the script captures
# each one on Enter and validates it before moving on.

set -euo pipefail

OUT_DIR="${1:-build/play-screenshots}"
SERIAL="${2:-}"
ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not on PATH. Install the Android platform-tools first." >&2
  exit 1
fi

if [ -z "$("${ADB[@]}" get-state 2>/dev/null || true)" ]; then
  echo "No device. Start a Wear OS emulator or connect a watch, then re-run." >&2
  echo "Check with: adb devices -l" >&2
  exit 1
fi

# A watch, not a phone: a phone-shaped screenshot is not a Wear screenshot and
# Play checks the aspect ratio.
CHARACTERISTICS="$("${ADB[@]}" shell getprop ro.build.characteristics | tr -d '\r')"
case "$CHARACTERISTICS" in
  *watch*) ;;
  *)
    echo "The connected device does not report itself as a watch (ro.build.characteristics=$CHARACTERISTICS)." >&2
    echo "Wear store screenshots must come from a Wear OS device or emulator." >&2
    exit 1
    ;;
esac

mkdir -p "$OUT_DIR"

echo "Device      : $("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
echo "Screen      : $("${ADB[@]}" shell wm size | tr -d '\r')"
echo "Output      : $OUT_DIR"
echo

# Each entry is "filename|what to put on screen before pressing Enter".
SHOTS=(
  "01-punch-in|Watch app, clocked out: the bolt button and PUNCH IN"
  "02-on-shift|Watch app, clocked in: the goal ring around the live count-up"
  "03-countdown|Watch app: the 3-2-1 pre-punch countdown"
  "04-tile|The ElmTrackr tile in the tile carousel"
  "05-complication|A watch face carrying the ElmTrackr complication"
)

capture() {
  local name="$1"
  local path="$OUT_DIR/$name.png"
  "${ADB[@]}" exec-out screencap -p > "$path"
  if [ ! -s "$path" ]; then
    echo "  capture failed (empty file) — is the screen on?" >&2
    rm -f "$path"
    return 1
  fi
  echo "  saved $path"
}

for shot in "${SHOTS[@]}"; do
  name="${shot%%|*}"
  hint="${shot#*|}"
  echo "next: $hint"
  read -r -p "  set it up on the watch, then press Enter (s to skip): " answer
  [ "$answer" = "s" ] && { echo "  skipped"; echo; continue; }
  capture "$name" || true
  echo
done

echo "Validating against the Play Wear screenshot spec"
python3 "$(dirname "$0")/check-wear-screenshots.py" "$OUT_DIR"
