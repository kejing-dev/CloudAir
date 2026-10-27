# QA round 2 · Cloud Air on emulator-5554 (PICO_6.1_Air) · regression of the round-1 defects

Date 2026-10-12 · build: round-4 debug APK as installed (lastUpdateTime 17:43:31, not rebuilt) · eye (gaze + pinch)
mode · tester: QA agent. Input: real injected pinches through `tools/harness/ca.sh click` (Compose buttons and 3D
entities), debug broadcasts only to reach states (`set`, `dice`, `new`, `mode`, `lang`, `speed`, `dump`, plus the
extra broadcast checks marked as such). Shots: `.scratch/shots/r2_*.png` (2160 x 2158, second of two), crops
`.scratch/shots/r2_crop_*.png`. Logs: `.scratch/logs/r2_initial.log`, `r2_pre_soak.log`, `soak_*.log`,
`r2_soak_stdout.txt`. App processes during the session: pid 19475 (found running), 3557, 9576, 14669 (each after an
`am force-stop` + relaunch of mine). Crash buffer: no `io.embodit.cloudair` entry before the soak.

Camera note: D6 (control window partly outside the view at launch) is unchanged, as expected; after every launch the
emulator camera was yawed right with one posted right-drag (`ca.sh drag 1200 1700 1500 1700 -Button right`).

## Verdict

**No blocker left; 13 of 16 checks PASS (D9 and D10 counted separately, soak included), 3 still FAIL (all cosmetic).** The S1 (D1) and the flow defects D2, D3, D4
are fixed and verified with real pinches, kills and relaunches; D5/D7, D8, D9, D11, D13, D14, D17 and D22 are fixed;
the 2-seat soak ended by victory in 294 s with 0 watchdogs, 0 save failures and no crash. Still failing: **D10**
(the English replace-dialog action is now clipped to "Start new ga", S3), **D15** (the die is still never the gaze
target, S4) and **D16** (Sky loses AI·聪明 after 3 → 2 → 3 seats, S4). One new S4 nit (N1). Nothing UNTESTED.

## Results per brief item

| Defect | Check | Result |
| --- | --- | --- |
| D1 (S1) | Save and leave in the six choice → Continue → both buttons; manual plane list restored with rings, board pinch moves; force-stop + Continue from the six choice | **PASS** |
| D2 | Pause (菜单) while an AI's die is in the air → no move until 继续游戏, then `deferred=paused` → `MOVE begin` | **PASS** |
| D3 | Leave before the die settles → move applied before `GAME restored`, no second roll; force-stop mid-throw → `pendingRoll=N` → `DICE roll ... result=N` | **PASS** |
| D4 | `set 0:0:15`, `dice 1` → `WALK:15>16, JUMP:16>20, FLY:20>28, JUMP:28>32` | **PASS** |
| D5 / D7 | 4 seats: fleet strip on one line; six-choice hints and plane list clear of the strip; list scrolls | **PASS** (new S4 nit N1) |
| D8 | 4-seat setup shows all four rows | **PASS** |
| D9 | 中文: restart and replace dialogs show 取消 | **PASS** |
| D10 | Dialog action labels on one line in both languages | **FAIL** (English "Start new game" now clipped to "Start new ga") |
| D11 | After Continue the 3D die shows the last rolled value | **PASS** |
| D13 | Four planes home sit in two stacked pairs beside the tower | **PASS** |
| D14 | Game started by the `new` broadcast while on HOME → victory page appears by itself | **PASS** |
| D15 | Hover/pinch on the die's upper half targets `die`; tray floor still `tray` | **FAIL** (die still never targeted) |
| D16 | 3 seats, Sky AI·聪明 → 2 seats → 3 seats: Sky keeps AI·聪明 | **FAIL** (Sky falls back to AI·轻松) |
| D17 | Manual six choice hints "然后选一架…" / "Then pick a plane…" | **PASS** |
| D22 | `region half=` once per launch, not per frame | **PASS** |
| Soak | `bash tools/test/soak.sh H,S 900` | **PASS** (ENDED after 294 s, 0 watchdog, 0 crash) |

### D1 · restored six choice and plane pick — PASS
4-seat game (Mint human, Sky AI·聪明, Apricot / Lavender AI·轻松, started by pinch, recommend mode).
- **Six choice, save and leave, 出动新飞机**: `set 0:0:5`, `dice 6`, pinch 投掷骰子 → `DICE settled id=18 result=6 legal=4`
  (`r2_16_six_choice4`) → pinch 菜单 → 保存并离开 (`r2_17_home_after_leave_decision`) → pinch 继续航程 →
  `18:02:00.764 GAME restored phase=DECISION turn=0 round=2 pendingRoll=null`, choice back (`r2_18_restored_decision`)
  → pinch 出动新飞机 (`ViewRootImpl ACTION_DOWN x=300 y=329`) → `18:02:42.248 MOVE begin id=20 seat=0 plane=1 LAUNCH dice=6 -1->0`.
- **Same, 前进 6 步**: `DICE settled id=21 result=6 legal=4` → 菜单 → 保存并离开 → 继续航程 →
  `18:03:56.247 GAME restored phase=DECISION` (`r2_19_restored_decision2`) → pinch 前进 6 步 →
  `18:04:32.709 MOVE begin id=23 seat=0 plane=0 ADVANCE dice=6 5->11 segs=WALK:5>11`.
- **Force-stop from the six choice**: `18:05:11.680 DICE settled id=24 result=6 legal=4` → `am force-stop` → relaunch
  (pid 3557, `r2_20_relaunch`) → pinch 继续航程 → `18:06:38.454 GAME restored phase=DECISION turn=0 round=2`
  (`r2_22_restored_decision_after_kill`) → pinch 出动新飞机 → `18:07:15.665 MOVE begin id=2 seat=0 plane=2 LAUNCH dice=6 -1->0`.
- **Manual list after a six (selectedKind=ADVANCE)**: `mode manual`, `set 0:1:3,0:2:7`, `dice 6`, pinch roll → manual
  choice → pinch 前进 6 步 → list of 3 + 返回选择动作 + rings on 3 planes (`r2_24_manual_adv_list`) → 菜单 → 保存并离开 →
  继续航程 → `18:10:15.116 GAME restored phase=SELECT turn=0 round=2`, identical list, back button and 3 rings
  (`r2_25_restored_select_adv`) → pinch a ringed plane on the board: `HOVER_ENTERED ... plane:0:0`,
  `SELECT_ENTERED touch down plane:0:0`, `18:10:55.743 TAP target=plane:0:0` → `MOVE begin id=5 seat=0 plane=0 ADVANCE dice=6 11->17`.
- **Manual list on a non-six (the round-1 repro B)**: `set 0:0:1,0:1:5,0:2:9,0:3:13`, `dice 2`, pinch roll →
  `DICE settled id=6 result=2 legal=4`, list of 4 + 4 rings (`r2_26_manual_list4`) → 菜单 → 保存并离开 → 继续航程 →
  `18:13:16.697 GAME restored phase=SELECT turn=0 round=2`, list of 4 and 4 rings back (`r2_28_restored_select_list4`) →
  pinch a ringed plane: `SELECT_ENTERED touch down plane:0:1`, `18:14:06.432 TAP target=plane:0:1` →
  `MOVE begin id=8 seat=0 plane=1 ADVANCE dice=2 5->7`.

### D2 · pause while the AI's die is in the air — PASS
Speed gentle for a wider window. A watcher pinched 菜单 as soon as the AI roll appeared in the log:
```
18:16:15.182 DICE roll id=21 seat=1 team=SKY result=6
18:16:16.550 GAME paused                         (pinch 菜单, die in the air)
18:16:17.574 DICE settled id=21 face=6
18:16:17.574 DICE settled id=21 deferred=paused
   ... no DICE/MOVE line for 63 s; Sky's hangar still full, die at rest on 6 (r2_29_paused_ai_die_settled)
18:17:20.631 GAME resumed                        (pinch 继续游戏)
18:17:20.631 DICE settled id=21 result=6 legal=4
18:17:20.632 MOVE begin id=22 seat=1 plane=0 LAUNCH dice=6 -1->0
18:17:21.741 MOVE done id=22                     (r2_30_resumed_ai_launched)
```
- Extra (broadcasts, human recommend-mode throw): `18:32:13.027 DICE roll id=36 seat=0`, `13.379 GAME paused`,
  `15.840 DICE settled id=36 deferred=paused`, nothing until `21.769 GAME resumed` → `DICE settled id=36 result=3 legal=1`
  → `MOVE begin id=37`.
- (A first attempt with a slow `-NoFast` pinch landed after the die had settled: `18:14:38.078 DICE settled id=15 ... legal=0`,
  `18:14:39.598 GAME paused`. Tester timing, not a defect.)

### D3 · leaving or dying mid-throw keeps the committed roll — PASS
- **Leave by pinch before the die settles** (pinch 投掷骰子 → 菜单 → 保存并离开 in quick succession, speed gentle):
  ```
  18:18:10.370 DICE roll id=30 seat=0 team=MINT result=3
  18:18:11.699 GAME paused
  18:18:12.824 DICE settled id=30 deferred=paused
  18:18:13.065 DICE settled id=30 result=3 legal=4          (保存并离开 resolves the throw)
  18:18:13.066 MOVE begin id=31 seat=0 plane=2 ADVANCE dice=3 9->16 segs=WALK:9>12, JUMP:12>16 captures=1
  18:18:13.066 MOVE done id=31 phase=READY turn=1 round=5
  18:18:49.864 GAME restored phase=READY turn=1 round=5 pendingRoll=null   (pinch 继续航程)
  ```
  DUMP after Continue: Mint `[1, 12, 16, 14]` (was `[1, 12, 9, 14]`), the Sky plane on cell 16 captured; the next roll is
  Sky's (`DICE roll id=33 seat=1`), Mint does not roll again (`r2_31_continued_after_leave_midroll`).
- **Leave with the die truly in flight** (broadcast `roll` then `leave` 0.36 s later): `18:19:06.736 DICE roll id=37 result=4`
  → `18:19:07.096 DICE settled id=37 result=4 legal=4` → `MOVE begin id=38 ... 12->32` → `18:19:07.097 MOVE done id=38`
  → `18:19:07.100 CMD leave` → `continue` → `18:19:09.480 GAME restored phase=READY turn=1 round=6 pendingRoll=null`, Mint plane at 32.
- **Leave with a move in flight** (extra, broadcasts): `18:32:45.508 MOVE begin id=45 ... 43->48` → `18:32:46.113 MOVE done id=45`
  → `18:32:46.117 CMD leave` → restored with the plane on 48.
- **Force-stop mid-throw**: pinch 投掷骰子 → `18:19:59.901 DICE roll id=46 seat=0 team=MINT result=5` → `am force-stop`
  (no settled line) → relaunch (pid 9576, `r2_32_relaunch_after_kill_midthrow`) → pinch 继续航程:
  ```
  18:21:20.167 GAME restored phase=READY turn=0 round=7 pendingRoll=5
  18:21:20.171 DICE roll id=2 seat=0 team=MINT result=5
  18:21:22.675 DICE settled id=2 result=5 legal=4
  18:21:22.678 MOVE begin id=3 seat=0 plane=1 ADVANCE dice=5 32->37
  ```
  (`r2_33_continued_pending_roll`).

### D4 · jump onto the fly-over cell then fly — PASS
`tools/test/rule.sh 0:0:15 1 r2_34_D4_jumpfly`:
`MOVE begin id=14 seat=0 plane=0 ADVANCE dice=1 15->32 segs=WALK:15>16, JUMP:16>20, FLY:20>28, JUMP:28>32 captures=1`
(the capture is the Apricot plane on loop cell 28, where the fly-over lands); plane on the right arm afterwards
(`r2_34_D4_jumpfly_before` / `_after`). Also seen in play: `MOVE begin id=38 ... dice=4 12->32 segs=WALK:12>16, JUMP:16>20, FLY:20>28, JUMP:28>32`.

### D5 / D7 · 4-seat turn page — PASS
- Fleet strip: four fleets, pips and `0/4` on one line, zh (`r2_15_game4_start`, crop `r2_crop_D5_fleet_strip4`) and en (`r2_41_after_start_en`).
- Six choice in 4 seats: hints well above the strip, recommend (`r2_16_six_choice4`), manual zh (`r2_23_manual_six_zh`), manual en (`r2_42_manual_six_en`).
- Plane list with 3 planes + back button: clear of the strip (`r2_24_manual_adv_list`).
- Plane list with 4 planes on the loop (`set 0:0:1,0:1:5,0:2:9,0:3:13`, `dice 2`): the list viewport ends at the strip's top
  edge, the 4th row is cut there (no overlap) (`r2_26_manual_list4`, crop `r2_crop_D7_list4_bottom`); a pinch-drag up
  scrolls it and all four rows show (`r2_27_manual_list4_scrolled`, crop `r2_crop_D7_list4_scrolled`). See N1.

### D8 · 4-seat setup — PASS
All four rows above 开始游戏 / Start game without scrolling: `r2_11_setup4` (zh), `r2_13_setup4_HSEE`, `r2_40_setup4_en`.

### D9 / D10 · confirmation dialogs — D9 PASS, D10 FAIL
- 中文: restart dialog `r2_03_restart_dialog_zh` (取消 / 重新开始), replace dialog `r2_14_replace_dialog_zh`
  (取消 / 开始新航程); both labels on one line. Pinching 取消 returns to the pause page (`r2_04_after_cancel`).
- English: restart dialog `r2_43_restart_dialog_en` (Cancel / Restart) fine. Replace dialog `r2_45_replace_dialog_en`:
  the action reads **"Start new ga"**: the label no longer wraps but is clipped mid-word with no ellipsis (crop
  `r2_crop_D10_replace_en_clipped`). See defect D10 below.

### D11 · die face after Continue — PASS
- Save and leave in the plane list after a roll of 2 → Continue → the die rests with 2 up (crop `r2_crop_D11_die_after_continue_2`,
  shot `r2_28_restored_select_list4`; before leaving it showed 2 as well, `r2_26_manual_list4`).
- Force-stop at Mint's turn after Lavender rolled 5 (`DICE roll id=49 seat=3 team=LAVENDER result=5`) → relaunch (pid 14669)
  → Continue → die 5 up and the panel die 5 (`r2_49_D11_continue_after_kill`, crop `r2_crop_D11_die_after_kill`).

### D13 · four planes home — PASS
`set 0:0:57,0:1:57,0:2:57,0:3:54`, `dice 3`: `MOVE begin id=21 ... 54->57 segs=FINISH:54>57` →
`MOVE done id=21 phase=ENDED turn=0 round=1 winner=0`. The four Mint planes sit as two pairs, left and right of the
tower, each canopy distinguishable (crop `r2_crop_D13_home_planes`; before, three home: `r2_37_D13_home_before`).
At this framebuffer resolution the two planes of a pair still overlap a little.

### D14 · victory page after a `new` broadcast from HOME — PASS
Window on HOME (`r2_35_home_before_new`) → `cmd new H,E` → `18:22:57.056 GAME new seats=MINT:HUMAN, APRICOT:AI_EASY`,
turn page (`r2_36_after_new_broadcast`) → the D13 setup above → after `phase=ENDED` the victory page shows by itself:
薄荷 获胜 · 全部平安归航 · 再来一局 · 返回主菜单 (`r2_37_D13_home_after`). 再来一局 by pinch → setup (`r2_39_setup_en_playagain`).

### D15 · die as gaze target — FAIL
- Hover probes (gaze moved off, then onto the point, 60 fps): die top face and upper front face at (809, 1280),
  (809, 1292), (800, 1300), (818, 1300), (809, 1312), (809, 1325) → every one `HOVER_ENTERED ... tray`.
- Pinch on the top face (809, 1290): `SELECT_ENTERED touch down tray`, `17:59:46.877 TAP target=tray`; pinch on the upper
  front face (809, 1300): `18:31:53.580 TAP target=tray`. Die location from `r2_15_game4_start` / `r2_48_die_before_pinch2`
  (crop `r2_crop_D15_die_hover_points`).
- Tray floor (735, 1317): `18:30:54.033 TAP target=tray` (still correct). Throwing works either way, so still harmless.

### D16 · seat kinds follow the team — FAIL
3 席 → Sky AI·聪明 (`r2_08_setup3_sky_smart`, crop `r2_crop_D16a_3seats_sky_smart`) → 2 席 (Apricot correctly keeps
AI·轻松, `r2_09_setup2`) → 3 席: Sky shows **AI·轻松** (`r2_10_setup3_back`, crop `r2_crop_D16b_back_to_3_sky_easy`).
Reproduced a second time via 4 席 (Sky kept AI·聪明 3→4, `r2_11_setup4`) → 2 席 → 3 席 (`r2_12_setup3_after2_again`).

### D17 · manual six-choice hints — PASS
zh: 然后选一架机库里的飞机 / 然后选一架在途飞机 (`r2_23_manual_six_zh`, crop `r2_crop_D17_manual_hints_zh`);
en: "Then pick a plane in the hangar" / "Then pick a plane in flight" (`r2_42_manual_six_en`). Recommend mode keeps
推荐一架在途飞机前进 6 格 (`r2_16_six_choice4`).

### D22 · `region half=` logging — PASS
- Launch log as found (`r2_initial.log`, 17:43:32 → 17:45:27, two launches, pids 19272 and 19475, a game with 9 throws in
  between): exactly two `region half=(0.39, 0.25, 0.5) rootY=0.018962717 tiltDeg=28.0...` lines, one per launch, each right
  after `MODELS loaded`. Relaunch pid 14669: one line at `18:33:08.971`, before `SCENE built`.
- The soak log (`r2_soak_full.log`, streamed from `GAME new` to `CMD auto a=off`, 772 lines, 105 throws, 80 moves,
  same process 14669) has **zero** `region half` lines. In round 1 they were 29 % of the retained lines.

### Soak — PASS
`bash tools/test/soak.sh H,S 900`, run last; I did not touch the emulator while it ran (it was launched as a background
shell job because a foreground tool call is capped at 10 min, then only the local stdout file was polled).
```
ENDED after 294 s
log=.scratch/logs/soak_183417.log   (copy without CRs: r2_soak_full.log)
rolls=105 moves=80 passes=25 watchdogs=0 captures=1 flies=1 jumps=12 bounces=1 finishes=5
18:34:22.059 GAME new seats=MINT:HUMAN, APRICOT:AI_SMART mode=RECOMMEND
18:39:09.108 MOVE done id=187 phase=ENDED turn=0 round=42 winner=0
```
- The log now covers the whole run (first line `CMD speed a=quick` 18:34:21.712, last `CMD auto a=off` 18:39:17.536), so the
  zero-watchdog and zero-`SAVE failed` counts are complete this time.
- No crash: crash buffer has no `io.embodit.cloudair` entry; same pid 14669 before and after.
- Victory page showed by itself at the end ("Mint wins · Round 42", `r2_99_after_soak`).

## Defects still open / new

### Still failing
**D10 (S3 now) · English replace-dialog action clipped: "Start new ga".** Repro: language English, a saved game exists,
Home → Start a new game → Start game → the dialog's primary button shows "Start new ga" (`r2_45_replace_dialog_en`,
crop `r2_crop_D10_replace_en_clipped`). The fix made the label single-line (`maxLines = 1, softWrap = false` in
`PrimaryButton`) but the button inside the `AlertDialog` is narrower than the text, so it is cut mid-word. zh and the
English restart dialog fit. Raised from S4 because the main action label is now unreadable rather than wrapped.

**D15 (S4) · the die is still never the gaze/pinch target.** Evidence above: 6 hover probes and 2 pinches on the die's
upper half all resolve to `tray`. Harmless for play (the tray throws too).

**D16 (S4) · a seat kind is lost after a round trip through fewer seats.** Repro: setup 3 席, Sky → AI·聪明, 2 席, 3 席 →
Sky is AI·轻松. `ControlEvent.SetSeatCount` builds the new draft from the current draft's team→kind map only, so a team
that is not in the 2-seat draft (Sky) loses its choice. Expected per the brief: Sky keeps AI·聪明 (remember kinds per
team for the whole setup session).

### New
**N1 (S4) · 4-plane list: the recommended plane is half hidden and nothing says the list scrolls.** With four candidates
the 4th row (here the recommended one, "飞机 4 · 第 13 格 → 第 15 格 · 推荐") is cut in half at the strip and there is no
scroll indicator or fade (`r2_26_manual_list4`, crop `r2_crop_D7_list4_bottom`). Picking on the board or dragging the
list works. Suggest scrolling the recommended row into view or showing a fade/scrollbar.

### Notes (not defects)
- After leaving with the die in flight, the board finishes the old throw animation after the restore and logs
  `18:19:09.846 DICE settled id=37 face=4` (2.7 s after `GAME restored`); the flow ignores it (stale id). No visible harm seen.
- D6 unchanged (system-owned per AGENTS.md).

## State left on the emulator
Language `system` (as found at the start of the round; the device is English), decision mode recommend, reduced motion
off, sound untouched, speed quick (set by the soak). App pid 14669 on the soak's victory page; the saved game is that
finished game. The emulator camera is yawed further right than at the start (one 300 px right-drag at the start and one after each
of my three relaunches); a relaunch re-centres the board.

## Not tested / limits
- D2 was exercised with an AI throw (pinch) and a human throw (broadcast); a pause during an AI *move* was not
  targeted (moves in flight finish by design).
- All visual checks are from the emulator framebuffer; small text (hints, pips) is low resolution and was read at 3-4x zoom.
- Physical-device FOV and controller mode not covered (unchanged from round 1).
