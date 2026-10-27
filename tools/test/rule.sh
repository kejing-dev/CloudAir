#!/usr/bin/env bash
# QA helper: wait until seat 0 (human) may roll, place planes, force the die, roll, wait for the move, shoot.
#   tools/test/rule.sh "S:P:V,..." DICE SHOTNAME [extra-wait-seconds]
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
H="bash $ROOT/tools/harness/ca.sh"
DEV="${DEV:-emulator-5554}"
SET="$1"; DICE="$2"; NAME="$3"; EXTRA="${4:-0}"
cd "$ROOT"
for i in $(seq 1 60); do
  st="$($H cmd dump >/dev/null; sleep 0.4; adb -s "$DEV" logcat -d -s CloudAir:V | grep 'CMD dump' | tail -1)"
  echo "$st" | grep -q "phase=READY turn=0" && break
  sleep 1
done
echo "state: $(echo "$st" | sed 's/.*CMD/CMD/')"
$H cmd set "$SET" >/dev/null
sleep 1.2
$H shot "${NAME}_before0" >/dev/null; $H shot "${NAME}_before" >/dev/null
$H cmd dice "$DICE" >/dev/null
$H cmd roll >/dev/null
$H waitlog "MOVE done|PASS" 20 >/dev/null
sleep "$EXTRA"
$H shot "${NAME}_after0" >/dev/null; $H shot "${NAME}_after" >/dev/null
adb -s "$DEV" logcat -d -s CloudAir:V | grep -E "DICE roll|DICE settled id=[0-9]+ result|MOVE begin|MOVE play |MOVE done|PASS" | tail -6 | cut -c19-400
