#!/usr/bin/env bash
set -euo pipefail

# Capture a cold Android app launch as frame-by-frame PNGs and remove exact
# duplicate frames. Defaults to 10 seconds at 60 fps (600 frames before dedupe).
#
# Usage:
#   ./capture_startup_frames.sh
#   ./capture_startup_frames.sh OUTPUT_DIR [DURATION_SECONDS] [FPS]
#
# Optional environment variables:
#   ANDROID_SERIAL  adb device serial
#   APP_PACKAGE     package/application id
#   APP_ACTIVITY    fully-qualified launch activity

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${1:-$ROOT_DIR/app/src/main/startup-frame-capture}"
DURATION="${2:-10}"
FPS="${3:-60}"
APP_PACKAGE="${APP_PACKAGE:-dev.tricked.solidverdant.dev}"
APP_ACTIVITY="${APP_ACTIVITY:-dev.tricked.solidverdant.MainActivity}"
PREROLL_SECONDS=1

for command in adb ffmpeg sha256sum; do
    command -v "$command" >/dev/null || {
        echo "Missing required command: $command" >&2
        exit 1
    }
done

if [[ -z "${ANDROID_SERIAL:-}" ]]; then
    ANDROID_SERIAL="$(adb devices | awk '$2 == "device" { print $1; exit }')"
fi

if [[ -z "$ANDROID_SERIAL" ]]; then
    echo "No authorized Android device found." >&2
    exit 1
fi

ADB=(adb -s "$ANDROID_SERIAL")
REMOTE_VIDEO="/sdcard/solidverdant-startup-$$.mp4"
LOCAL_VIDEO="$(mktemp --suffix=.mp4)"
HASH_FILE="$(mktemp)"
RECORDER_PID=""

cleanup() {
    if [[ -n "$RECORDER_PID" ]] && kill -0 "$RECORDER_PID" 2>/dev/null; then
        "${ADB[@]}" shell pkill -INT screenrecord >/dev/null 2>&1 || true
        wait "$RECORDER_PID" 2>/dev/null || true
    fi
    "${ADB[@]}" shell rm -f "$REMOTE_VIDEO" >/dev/null 2>&1 || true
    rm -f "$LOCAL_VIDEO" "$HASH_FILE"
}
trap cleanup EXIT INT TERM

mkdir -p "$OUTPUT_DIR"
if find "$OUTPUT_DIR" -maxdepth 1 -name 'frame-*.png' -print -quit | grep -q .; then
    echo "Output already contains frames: $OUTPUT_DIR" >&2
    echo "Move or remove them first so an existing cleanup is never overwritten." >&2
    exit 1
fi

"${ADB[@]}" shell input keyevent KEYCODE_WAKEUP
"${ADB[@]}" shell wm dismiss-keyguard
"${ADB[@]}" shell am force-stop "$APP_PACKAGE"

echo "Recording $APP_PACKAGE on $ANDROID_SERIAL..."
"${ADB[@]}" shell screenrecord "$REMOTE_VIDEO" &
RECORDER_PID=$!

# A fixed preroll makes the launch boundary deterministic in the output video.
sleep "$PREROLL_SECONDS"
"${ADB[@]}" shell am start -n "$APP_PACKAGE/$APP_ACTIVITY" >/dev/null
sleep "$DURATION"

# Stop screenrecord inside Android. Signalling the host adb process does not
# reliably forward SIGINT and can leave adb waiting forever.
"${ADB[@]}" shell pkill -INT screenrecord >/dev/null 2>&1 || true
wait "$RECORDER_PID" 2>/dev/null || true
RECORDER_PID=""

"${ADB[@]}" pull "$REMOTE_VIDEO" "$LOCAL_VIDEO" >/dev/null

EXPECTED_FRAMES=$((DURATION * FPS))
ffmpeg -hide_banner -loglevel error \
    -ss "$PREROLL_SECONDS" \
    -i "$LOCAL_VIDEO" \
    -t "$DURATION" \
    -vf "fps=$FPS" \
    -frames:v "$EXPECTED_FRAMES" \
    -start_number 0 \
    "$OUTPUT_DIR/frame-%04d.png"

sha256sum "$OUTPUT_DIR"/frame-*.png | sort -k2,2 > "$HASH_FILE"
GENERATED="$(find "$OUTPUT_DIR" -maxdepth 1 -name 'frame-*.png' | wc -l)"

declare -A SEEN_HASHES=()
REMOVED=0
while read -r hash file; do
    if [[ -n "${SEEN_HASHES[$hash]+present}" ]]; then
        rm -f -- "$file"
        ((REMOVED += 1))
    else
        SEEN_HASHES[$hash]="$file"
    fi
done < "$HASH_FILE"

KEPT="$(find "$OUTPUT_DIR" -maxdepth 1 -name 'frame-*.png' | wc -l)"
echo "Done: extracted $GENERATED frames, removed $REMOVED exact duplicates, kept $KEPT."
echo "Frames: $OUTPUT_DIR"
