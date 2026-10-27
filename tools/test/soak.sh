#!/usr/bin/env bash
# Autopilot soak: starts a game with the given seat kinds, lets the flow play every seat, and reports.
#   tools/test/soak.sh [H,E,S,E] [max_seconds]
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
H="bash $ROOT/tools/harness/ca.sh"
SEATS="${1:-H,E}"
MAX="${2:-600}"
DEV="${DEV:-emulator-5554}"
cd "$ROOT"
adb -s "$DEV" logcat -c
mkdir -p "$ROOT/.scratch/logs"
LOGFILE="$ROOT/.scratch/logs/soak_$(date +%H%M%S).log"
adb -s "$DEV" logcat -v time -s CloudAir:V > "$LOGFILE" &
LOGPID=$!
$H cmd speed quick >/dev/null
$H cmd new "$SEATS" >/dev/null
$H cmd auto on >/dev/null
start=$(date +%s)
while true; do
  sleep 10
  now=$(date +%s)
  log="$(tr -d '\r' < "$LOGFILE")"
  if echo "$log" | grep -q "MOVE done .*phase=ENDED"; then echo "ENDED after $((now - start)) s"; break; fi
  if [ $((now - start)) -ge "$MAX" ]; then echo "TIMEOUT after $MAX s"; break; fi
  if adb -s "$DEV" logcat -d -b crash | grep -q "io.embodit.cloudair"; then echo "CRASH"; break; fi
done
$H cmd auto off >/dev/null
kill $LOGPID 2>/dev/null
echo "log=$LOGFILE"
echo "rolls=$(echo "$log" | grep -c 'DICE roll') moves=$(echo "$log" | grep -c 'MOVE done') passes=$(echo "$log" | grep -c 'PASS') watchdogs=$(echo "$log" | grep -c watchdog) captures=$(echo "$log" | grep -c 'captures=[1-9]') flies=$(echo "$log" | grep -c 'FLY:') jumps=$(echo "$log" | grep -c 'JUMP:') bounces=$(echo "$log" | grep -c 'BOUNCE:') finishes=$(echo "$log" | grep -c 'FINISH:')"
echo "$log" | grep -E "ENDED|watchdog|SAVE failed" | tail -5
adb -s "$DEV" logcat -d -b crash | grep -A12 "io.embodit.cloudair" | head -30
