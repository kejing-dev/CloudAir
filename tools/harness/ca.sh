#!/usr/bin/env bash
# Cloud Air wrapper around emu.sh: pinned to this project's emulator instance.
#   tools/harness/ca.sh shot NAME | cmd NAME [a] [s] | click X Y | drag ... | waitlog REGEX | log | info | fast on
export DEV="${DEV:-emulator-5554}"
export AVD="${AVD:-PICO_6.1_Air}"
export PKG="${PKG:-io.embodit.cloudair}"
export TAG="${TAG:-CloudAir}"
HERE="$(cd "$(dirname "$0")" && pwd)"
export SHOTS="${SHOTS:-$(cd "$HERE/../.." && pwd)/.scratch/shots}"
exec bash "$HERE/emu.sh" "$@"
