# QA brief · Cloud Air round 1 (emulator, real injected input)

Product: 云间航线 Cloud Air, aeroplane chess for PICO OS 6 Shared Space. Project `C:\Users\Admin\Downloads\cloud-air`
(read `AGENTS.md` first, then `design/design-doc.md` §3 rules and §6 states). The debug build is installed and
running on **emulator-5554** (AVD `PICO_6.1_Air`); never touch 5556 or 5558. Rebuild only if you change nothing:
`bash tools/dev.sh install && bash tools/dev.sh relaunch` reinstalls the current APK.

## Tools

- `bash tools/harness/ca.sh shot NAME` → `.scratch/shots/NAME.png` (2160 x 2158). Shoot twice, keep the second.
  Crop/zoom with Python PIL and view with Read. The board is roughly x 640..1500, y 1000..1420; the control window
  roughly x 1440..2160, y 820..1320 (it moves if the user drags it; re-locate from a fresh shot).
- `bash tools/harness/ca.sh fast on` then `bash tools/harness/ca.sh click X Y` → a gaze + pinch at framebuffer pixel
  (X, Y). Clicks need the 60 fps window (`fast on` gives ~10 s; `click` refuses below 50 fps, exit 3). Compose buttons
  in the control window accept clicks at any frame rate; 3D entities (die, tray, planes) need the fast window.
  `drag`, `key`, `wheel` exist (see the header of `tools/harness/emu.sh`). Never use SendInput/SetCursorPos.
- `bash tools/dev.sh log REGEX N` → last N lines of tag `CloudAir`; `bash tools/dev.sh crash`.
  `bash tools/harness/ca.sh waitlog REGEX [SECONDS]` polls for a line since the last mark.
- Debug broadcasts (`bash tools/dev.sh cmd NAME [a]`): `new H,E` · `dice 6,3` · `roll` · `kind launch|advance` ·
  `plane N` · `set S:P:V,...` (seat:plane:progress) · `auto on|off` · `pause` / `resume` / `leave` / `abandon` ·
  `mode recommend|manual|auto` · `speed quick|standard|gentle` · `lang zh|en|system` · `reduced on|off` · `dump` · `evidence`.
  Use broadcasts to reach states quickly; use **real injected pinches** to verify every user-facing control at least once.
- System pointer evidence in `adb -s emulator-5554 logcat -d | grep -E "HOVER_ENTERED|SELECT_ENTERED"` names the
  entity under the gaze (`die`, `tray`, `plane:<seat>:<plane>`, `loop:<i>` are not colliders, only die/tray/planes are).

## What to verify (write results to docs/test/round1.md: PASS/FAIL per item, evidence = log lines + shot names)

1. Home → "Start a new game" → setup (2/3/4 seats; human/AI segments) → "Start game" by pinching the buttons.
2. Pinch the **die** on the board: `TAP target=die|tray` then `DICE roll` in the log; the die visibly tumbles and
   rests with the committed face up (compare `DICE settled ... face=N` with a zoomed shot of the die).
3. Pinch the "Roll the die" button: same result. Button disabled while rolling/moving and on AI turns.
4. A six with planes both in the hangar and on the loop shows the six choice; both choices work (`dice 6` + `set`).
5. Manual mode (`mode manual`): a roll shows the plane list; pinching a **plane on the board** picks it
   (`TAP target=plane:0:N` → `MOVE begin`); the "Back to the six choice" button appears only after a six choice.
6. Rules on the board, each reached with `set` + `dice` and checked in the log + a shot: connected jump (own colour
   +4), fly-over (relative 20 → 28, crossing capture of the opposite fleet's lane 53), capture on landing, overshoot
   bounce back, exact finish, four home = victory page (`MOVE done ... phase=ENDED`) with "Play again" / "Back to main menu".
7. Menu → pause stops AI (`GAME paused`), Resume continues, Restart asks for confirmation, Save and leave returns home
   and "Continue game" restores the same state (`GAME restored`). Also `am force-stop` + relaunch → Continue.
8. Settings: language zh/en switches every visible string (shots of home, turn page, settings in both); reduced
   motion makes the die show its result at once and planes slide (`DICE play ... reduced=true`); speed changes the
   `MOVE play ... total=` durations.
9. Soak: `bash tools/test/soak.sh H,E,S,E 900` ends with `ENDED`, zero `watchdog`, zero crash.
10. Anything visually wrong in the shots (planes floating, wrong heading, tiles misaligned, text clipped, overlap of
    the control window with the board, black panels) → list with a crop.

Severity: S1 blocks play · S2 wrong rule or state · S3 visual/copy defect · S4 nit. Do not edit app code; you may
add scripts under `tools/test/`. Keep the emulator in eye mode. Report honestly: an item you could not exercise is
UNTESTED, not PASS.
