#!/usr/bin/env bash
set -euo pipefail

# SolidVerdant performance harness. Run from the repo root on the build machine
# with the test phone attached to adb.
#
#   ./perf/run_perf.sh [LABEL]
#
# Measures two things and writes both to perf-results/<timestamp>-<sha>-<label>.txt:
#
#  1. Cold startup (release-representative): installs the `benchmark` build
#     (R8-minified like release, debug-signed, package .bench) and measures
#     `am start -W` over N iterations. The splash screen holds the first frame
#     until auth state resolves, so this is user-perceived time to content.
#  2. Frame timing while logged in: runs ScrollingFramePerformanceTest against the
#     `perftest` build (release build type, non-debuggable so ART honors AOT — the
#     GrapheneOS phone has no JIT — but unminified so the Hilt harness works) and
#     captures PERF_JSON lines with jank %, P50/P90/P95/worst frame times, and
#     time-to-history-content. Multiple repetitions because the mock-server world
#     has inherent run-to-run variance; compare medians across reps.
#
# Environment overrides:
#   ANDROID_SERIAL       adb device serial (default: first attached device)
#   STARTUP_ITERATIONS   cold start sample count (default 10)
#   FRAME_REPS           measured frame-suite repetitions after warmup (default 3)
#   SKIP_BUILD=1         reuse existing APKs
#   SKIP_STARTUP=1       skip the benchmark-variant cold-start section

LABEL="${1:-run}"
ITERATIONS="${STARTUP_ITERATIONS:-10}"
FRAME_REPS="${FRAME_REPS:-3}"
BENCH_PACKAGE="dev.tricked.solidverdant.bench"
PERFTEST_PACKAGE="dev.tricked.solidverdant.perftest"
BENCH_ACTIVITY="dev.tricked.solidverdant.MainActivity"
ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -z "${ANDROID_SERIAL:-}" ]]; then
    ANDROID_SERIAL="$(adb devices | awk '$2 == "device" { print $1; exit }')"
fi
[[ -n "$ANDROID_SERIAL" ]] || { echo "No adb device found" >&2; exit 1; }
ADB=(adb -s "$ANDROID_SERIAL")

GIT_SHA="$(git rev-parse --short HEAD)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="$ROOT_DIR/perf-results"
OUT_FILE="$OUT_DIR/$STAMP-$GIT_SHA-$LABEL.txt"
mkdir -p "$OUT_DIR"

note() { echo "$@" | tee -a "$OUT_FILE"; }

# Pin clocks to the sustainable fixed-performance level for the whole run and undo on exit.
# Without this, back-to-back builds/AOT compiles leave the SoC thermally throttled and frame
# numbers vary several-fold between otherwise identical runs.
"${ADB[@]}" shell cmd power set-fixed-performance-mode-enabled true >/dev/null 2>&1 || true
trap '"${ADB[@]}" shell cmd power set-fixed-performance-mode-enabled false >/dev/null 2>&1 || true' EXIT

# Wait for the device to cool below 38.0C (battery temp is reported in tenths) so a hot chassis
# from a previous build/compile cannot skew the first measured section. Max 5 minutes.
cooldown() {
    for _ in $(seq 60); do
        local temp
        temp="$("${ADB[@]}" shell dumpsys battery | awk '/temperature/ { print $2 }' | tr -d '\r')"
        [[ -n "$temp" && "$temp" -lt 380 ]] && { note "device_temp=$temp"; return 0; }
        sleep 5
    done
    note "device_temp=STILL_HOT_AFTER_5MIN"
}

wake_screen() {
    "${ADB[@]}" shell input keyevent KEYCODE_WAKEUP
    "${ADB[@]}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

note "# SolidVerdant perf run"
note "label=$LABEL sha=$GIT_SHA date=$STAMP device=$ANDROID_SERIAL"
note "dirty_files=$(git status --porcelain | wc -l)"
note ""

BENCH_TASKS="assembleBenchmark"
[[ "${SKIP_STARTUP:-0}" == "1" ]] && BENCH_TASKS=""
if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
    echo "Building $BENCH_TASKS + perftest APKs..."
    nix develop --command env -u LD_LIBRARY_PATH ./gradlew --no-daemon \
        -Pperf.testBuildType=perftest \
        $BENCH_TASKS assemblePerftest assemblePerftestAndroidTest
fi

PERFTEST_APK="$(ls app/build/outputs/apk/perftest/*.apk | head -1)"
PERFTEST_TEST_APK="$(ls app/build/outputs/apk/androidTest/perftest/*.apk | head -1)"

# ---- 1. Cold startup on the minified benchmark build --------------------------------
if [[ "${SKIP_STARTUP:-0}" != "1" ]]; then
BENCH_APK="$(ls app/build/outputs/apk/benchmark/*.apk | head -1)"
echo "Installing $BENCH_APK..."
"${ADB[@]}" install -r -g "$BENCH_APK" >/dev/null
wake_screen

# Apply the packaged baseline profile the way a store install would: ProfileInstaller writes
# the profile, then speed-profile dexopt AOT-compiles against it. Both steps are no-ops for a
# build without a packaged profile, so runs stay comparable.
"${ADB[@]}" shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE \
    -n "$BENCH_PACKAGE/androidx.profileinstaller.ProfileInstallReceiver" >/dev/null 2>&1 || true
sleep 3
COMPILE_OUT="$("${ADB[@]}" shell cmd package compile -m speed-profile -f "$BENCH_PACKAGE" 2>&1 || true)"
note "dexopt: $COMPILE_OUT"

note "## Cold startup ($BENCH_PACKAGE, R8 minified, $ITERATIONS iterations)"
cooldown
declare -a TOTALS=()
for i in $(seq "$ITERATIONS"); do
    "${ADB[@]}" shell am force-stop "$BENCH_PACKAGE"
    sleep 2
    START_OUT="$("${ADB[@]}" shell am start -W -n "$BENCH_PACKAGE/$BENCH_ACTIVITY")"
    TOTAL="$(echo "$START_OUT" | awk -F': *' '/TotalTime/ { print $2 }' | tr -d '\r')"
    if [[ -z "$TOTAL" ]]; then
        note "iter=$i FAILED: $START_OUT"
        continue
    fi
    TOTALS+=("$TOTAL")
    note "iter=$i total_ms=$TOTAL"
done
"${ADB[@]}" shell am force-stop "$BENCH_PACKAGE"

if [[ "${#TOTALS[@]}" -gt 0 ]]; then
    STATS="$(printf '%s\n' "${TOTALS[@]}" | sort -n | awk '
        { v[NR] = $1; sum += $1 }
        END {
            median = (NR % 2) ? v[(NR + 1) / 2] : int((v[NR / 2] + v[NR / 2 + 1]) / 2)
            printf "startup_ms min=%d median=%d mean=%d max=%d n=%d", v[1], median, sum / NR, v[NR], NR
        }')"
    note "$STATS"
fi
note ""
fi # SKIP_STARTUP

# ---- 2. Frame timing + time-to-content on the instrumented stress world -------------
echo "Installing perftest + test APKs..."
"${ADB[@]}" install -r -g "$PERFTEST_APK" >/dev/null
"${ADB[@]}" install -r -g "$PERFTEST_TEST_APK" >/dev/null

# Non-debuggable release-type build: AOT-compile everything so measurements reflect compiled
# code (the GrapheneOS phone never JITs; an uncompiled app would run interpreted forever).
"${ADB[@]}" shell cmd package compile -m speed -f "$PERFTEST_PACKAGE" >/dev/null || true

INSTRUMENTATION="$("${ADB[@]}" shell pm list instrumentation | \
    grep -o "instrumentation:${PERFTEST_PACKAGE}[^ ]*" | head -1 | cut -d: -f2)"
[[ -n "$INSTRUMENTATION" ]] || { echo "No perftest instrumentation found" >&2; exit 1; }

note "## Frame timing (perftest build: release type, AOT speed, mock-server stress world)"
note "instrumentation=$INSTRUMENTATION"

# warmup pass absorbs first-run work (profile install, disk caches); then FRAME_REPS measured
# repetitions. The mock-server world varies between runs — compare per-metric medians.
for pass in warmup $(seq -f 'measured%.0f' "$FRAME_REPS"); do
    cooldown
    wake_screen
    "${ADB[@]}" logcat -c || true
    INSTRUMENT_OUT="$("${ADB[@]}" shell am instrument -w \
        -e class dev.tricked.solidverdant.e2e.perf.ScrollingFramePerformanceTest \
        "$INSTRUMENTATION" 2>&1 | tr -d '\r')"

    # println() from the test process lands in logcat (System.out), not in am instrument output.
    PERF_LINES="$("${ADB[@]}" logcat -d -s System.out:I | grep -E 'PERF(_JSON|_WARN)? ' | sed 's/^.*PERF/PERF/' || true)"
    if [[ -n "$PERF_LINES" ]]; then
        note "pass=$pass"
        note "$PERF_LINES"
    else
        note "NO PERF OUTPUT (pass=$pass) — instrumentation output follows:"
        note "$INSTRUMENT_OUT"
        exit 1
    fi
    echo "$INSTRUMENT_OUT" | grep -qE 'OK \(' || {
        note "INSTRUMENTED TEST DID NOT PASS (pass=$pass):"
        note "$(echo "$INSTRUMENT_OUT" | tail -20)"
        [[ "$pass" == "warmup" ]] || exit 1
    }
done
note ""
note "done"
echo "Results: $OUT_FILE"
