#!/usr/bin/env bash
# Cloud Air developer loop, pinned to emulator-5554 (AVD PICO_6.1_Air).
#   tools/dev.sh build | test | install | launch | relaunch | stop | log [REGEX] [N] | crash | shot NAME | cmd NAME [a]
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEV="${DEV:-emulator-5554}"
PKG=io.embodit.cloudair
export JAVA_HOME='C:\Users\Admin\.pico\primer-cli\jdk\jdk-21.0.12.1+1'
export PICO_HOME='C:\Users\Admin\AppData\Local\PICO\sdk'
cd "$ROOT"
case "${1:-}" in
  build) ./gradlew.bat assembleDebug --no-daemon --console=plain -q 2>&1 | grep -v "SDK XML" ;;
  test) ./gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain -q 2>&1 | grep -v "SDK XML"; python tools/count_tests.py | tail -3 ;;
  install) adb -s "$DEV" install -r app/build/outputs/apk/debug/app-debug.apk ;;
  launch) adb -s "$DEV" shell am start -n $PKG/.platform.LaunchActivity ;;
  stop) adb -s "$DEV" shell am force-stop $PKG ;;
  relaunch) adb -s "$DEV" shell am force-stop $PKG; sleep 1; adb -s "$DEV" logcat -c; adb -s "$DEV" shell am start -n $PKG/.platform.LaunchActivity ;;
  log) adb -s "$DEV" logcat -d -s CloudAir:V | grep -E "${2:-.}" | tail -n "${3:-40}" ;;
  crash) adb -s "$DEV" logcat -d -b crash | tail -60 ;;
  shot) bash tools/harness/ca.sh shot "${2:-shot}" ;;
  cmd) shift; bash tools/harness/ca.sh cmd "$@" ;;
  *) echo "usage: tools/dev.sh build|test|install|launch|relaunch|stop|log|crash|shot|cmd"; exit 1 ;;
esac
