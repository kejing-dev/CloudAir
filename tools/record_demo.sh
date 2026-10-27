#!/usr/bin/env bash
# Records a ~45 s demo of Cloud Air on this project's emulator (emulator-5554, AVD PICO_6.1_Air).
#   bash tools/record_demo.sh [out.mp4]
# Needs the debug build installed. The scenario is driven by the debug broadcasts (forced dice values and
# plane positions), so it shows a launch, hops, a connected jump, the fly-over with a crossing capture, a
# capture on landing, and a plane reaching the tower. No injected taps are involved.
set -euo pipefail
DEV="${DEV:-emulator-5554}"
PKG=io.embodit.cloudair
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/../docs/media/CloudAir-demo.mp4}"
mkdir -p "$(dirname "$OUT")"
H="bash $HERE/harness/ca.sh"

cmd() { adb -s "$DEV" shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd $1 ${2:+--es a '$2'}" >/dev/null; }

adb -s "$DEV" shell am force-stop "$PKG"
adb -s "$DEV" shell am start -n "$PKG/.platform.LaunchActivity" >/dev/null
echo "waiting for the scene"; sleep 22
cmd lang zh
cmd speed standard
cmd new "H,H"
sleep 3
# Two human seats so the forced dice land where the script expects. Mint plane 0 sits two cells before the
# fly-over entry; apricot has a plane parked on the crossed lane cell (its lane 53) and another on mint's cell 32
# (apricot progress 6: the jump landing after the fly-over). Mint plane 1 is in its home lane, one cell from the tower.
cmd set "0:0:18,0:1:56,0:2:-1,0:3:-1,1:0:53,1:1:6,1:2:-1,1:3:-1"
sleep 1
cmd dice "2,1,6,1,4"
$H fast on >/dev/null; sleep 3

adb -s "$DEV" shell "rm -f /sdcard/cloudair-demo.mp4"
adb -s "$DEV" shell "screenrecord --size 1080x1080 --bit-rate 6000000 --time-limit 46 /sdcard/cloudair-demo.mp4" &
REC=$!
# Keep the emulator at 60 fps: a posted focus-in buys about 10 s each time.
( for i in 1 2 3 4; do sleep 10; $H fast on >/dev/null 2>&1; done ) &
sleep 2
cmd roll; sleep 7        # mint 2: plane 0 hops 18 -> 20, flies over the arm (apricot sent home), lands 28, jumps to 32 (capture)
cmd roll; sleep 4        # apricot 1: nothing can move, the turn passes
cmd roll; sleep 4.5      # mint 6: both launch and advance are legal -> the six choice
cmd kind launch; sleep 3.5   # plane 2 takes off; a six rolls again
cmd roll; sleep 5        # mint 1: plane 1 56 -> 57 reaches the tower and spins
cmd roll; sleep 6        # mint 4: plane 0 32 -> 36, own colour, jumps to 40
sleep 6
wait "$REC" || true
sleep 1
adb -s "$DEV" pull //sdcard/cloudair-demo.mp4 "$OUT" >/dev/null
adb -s "$DEV" shell "rm -f /sdcard/cloudair-demo.mp4"
cmd lang system
ls -la "$OUT"
