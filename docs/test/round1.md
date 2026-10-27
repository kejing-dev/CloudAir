# QA round 1 · Cloud Air on emulator-5554 (PICO_6.1_Air)

Date 2026-10-12 · build: debug APK already installed (not rebuilt) · eye (gaze + pinch) mode · tester: QA agent.
Input: real injected pinches (`tools/harness/ca.sh click`, `-NoFast` for Compose buttons where timing mattered),
debug broadcasts only to reach states. Shots: `.scratch/shots/r1_*.png` (2160 x 2158, second of two);
defect crops: `.scratch/shots/r1_def*.png`. Logs: `.scratch/logs/round1_pre_soak.log`, `round1_soak.txt`,
`round1_soak_full.log` (the device's 2 MiB log buffer rotates within minutes, partly because `region half=...` is
logged on every board update, so most log lines below are quoted from the live session). New helper: `tools/test/rule.sh`
(waits for seat 0 READY, `set` + `dice` + `roll`, waits for the move, shoots before/after).

Camera note: at launch the control window sits partly outside the view (defect D6), so the emulator camera was
yawed right with posted right-drags (harness `drag ... -Button right`) after each launch. No other emulator was touched.

## Verdict

**FAIL for release**: one S1 (a saved game restored in the six choice or the plane pick can never be continued). The
core loop (setup, die and button rolls, six choice, manual pick, all board rules, victory, pause/resume, restart, save
and continue from READY, language, speed, reduced motion) works with real pinches. Several S3 layout and copy defects.

## Results per brief item

| # | Item | Result |
| --- | --- | --- |
| 1 | Home → new game → setup 2/3/4 seats, human/AI → Start (pinches) | PASS |
| 2 | Pinch the die: TAP + DICE roll, tumble, committed face up | PASS (target is always `tray`, never `die`, D15) |
| 3 | Roll button: same result; disabled while rolling/moving and on AI turns | PASS |
| 4 | Six with hangar + loop planes shows the choice; both choices work | PASS |
| 5 | Manual mode: plane list, pinch a plane on the board, back button only after a six choice | PASS |
| 6 | Rules: jump, fly-over + crossing capture, landing capture, bounce, exact finish, victory page | PASS |
| 7 | Menu pause/resume, restart confirm, save and leave + continue, force-stop + continue | **FAIL** (D1 S1; D2, D3 S3) |
| 8 | Language zh/en, reduced motion, speed | PASS with defects (D9 dialog Cancel not localised) |
| 9 | Soak `H,E,S,E 900` | PASS: ENDED at 899 s, 0 watchdog, 0 crash (watchdog count covers the last ~8 min only) |
| 10 | Visual review | FAIL: S3/S4 list below |

### 1 · Setup by pinch — PASS
- Pinched 开始新航程 → setup (`r1_01`), 4 席 (`r1_02_setup4`), scrolled the seat list (`r1_03_setup4_scrolled`),
  Sky → AI·聪明, Lavender → 真人 (`r1_04_setup4_kinds`), 3 席 (`r1_05_setup3`), 2 席 (`r1_06_setup2`), all-AI →
  Start disabled + "至少保留一个真人席位" (`r1_07_setup_noHuman`), back to H,S,E,E, 开始游戏 → replace dialog
  (`r1_09_replace`) → 开始新航程 → `GAME new seats=MINT:HUMAN, SKY:AI_SMART, APRICOT:AI_EASY, LAVENDER:AI_EASY mode=RECOMMEND`
  (16:31:03), turn page `r1_10_game_start`.
- Also via pinch later: `GAME new seats=MINT:HUMAN, APRICOT:AI_EASY` (16:51:34, 16:55:44), 4 seats in English (17:13:05).

### 2 · Die on the board — PASS
- `16:31:59.965 TAP target=tray` → `DICE roll id=2 seat=0 team=MINT result=6` → `DICE play id=2 result=6 duration=1.37 reduced=false`
  → `DICE settled id=2 face=6`. Mid-air tumble frame `r1_11_die_tumble`; at rest 6 up `r1_12_after_launch`.
- `16:32:53.397 TAP target=tray` → `DICE roll id=4 result=5` → `DICE settled id=4 face=5`.
- Face check on a known result: `DICE settled id=8 face=1` → `r1_14_die_rest` shows one pip on top.
- The pinch always resolves to `tray`: hover probes on the die's top and front faces give `HOVER_ENTERED ... tray` (D15, harmless).

### 3 · Roll button — PASS
- `16:34:00.331 DICE roll id=9 seat=0 team=MINT result=3` (no TAP line: button path) → `DICE settled id=9 face=3` →
  `MOVE begin id=10 ... 5->12 segs=WALK:5>8, JUMP:8>12`.
- Disabled while rolling (`r1_15_btn_rolling`, greyed "骰子滚动中…") and while moving (`r1_22_flying`, greyed "飞行中…");
  on AI turns the button is replaced by "天空蓝 正在思考…" (`r1_19_ai_turn`). Pinching the die while paused gave
  `TAP target=tray` and no roll.

### 4 · Six choice — PASS
- `DICE settled id=22 result=6 legal=4` → choice shown (`r1_20_six_choice`); pinch 出动新飞机 →
  `MOVE begin id=23 seat=0 plane=1 LAUNCH dice=6 -1->0`.
- Next six `DICE settled id=24 result=6 legal=4` (`r1_21_six_choice2`); pinch 前进 6 步 →
  `MOVE begin id=25 seat=0 plane=0 ADVANCE dice=6 14->32 segs=WALK:14>20, FLY:20>28, JUMP:28>32`.

### 5 · Manual mode — PASS
- `mode manual`, `dice 2` → `DICE settled id=26 result=2 legal=2`; list without the back button (`r1_24_manual_select`),
  rings on both candidates. Pinch the non-recommended plane on the board: `SELECT_ENTERED touch down plane:0:1`,
  `16:40:38.880 TAP target=plane:0:1` → `MOVE begin id=27 seat=0 plane=1 ADVANCE dice=2 0->2`. (The first injected
  pinch only produced `HOVER_ENTERED plane:0:1`; the retry selected. Treated as injection flakiness.)
- Six in manual mode (`r1_25_manual_six`) → 前进 6 步 → list with "返回选择动作" (`r1_26_manual_adv_list`) → pinch it →
  back at the choice (`r1_27_back_to_choice`) → 出动新飞机 → hangar list with rings (`r1_28_manual_launch_list`) →
  pinch a hangar plane: `16:44:07.281 TAP target=plane:0:3` → `MOVE begin id=35 seat=0 plane=3 LAUNCH dice=6 -1->0`.

### 6 · Rules on the board — PASS (each `tools/test/rule.sh`, shots `<name>_before` / `<name>_after`)
| Rule | Setup | Log | Shots |
| --- | --- | --- | --- |
| Own-colour jump +4 | `0:0:9`, dice 3 | `MOVE begin id=37 ... 9->16 segs=WALK:9>12, JUMP:12>16 captures=0` | `r1_30_jump_*` |
| Fly-over + crossing capture | `0:0:17,2:1:53`, dice 3 | `MOVE begin id=42 ... 17->32 segs=WALK:17>20, FLY:20>28, JUMP:28>32 captures=1` (apricot plane on lane 53 back in hangar) | `r1_31_fly_*` |
| Capture on landing (two stacked) | `0:0:30,1:0:20,1:1:20`, dice 3 | `MOVE begin id=47 ... 30->33 segs=WALK:30>33 captures=2` | `r1_32_capture_*` |
| Overshoot bounce | `0:0:55`, dice 4 | `MOVE begin id=52 ... 55->55 segs=WALK:55>57, BOUNCE:57>55` | `r1_33_bounce_*` |
| Exact finish | `0:0:54`, dice 3 | `MOVE begin id=57 ... 54->57 segs=FINISH:54>57` (panel 1 / 4 归航) | `r1_34_finish_after` |
| Four home = victory | `0:1:57,0:2:57,0:3:54`, dice 3 | `MOVE done id=62 phase=ENDED turn=0 round=10 winner=0` | `r1_35_victory_after` |
- Victory page: pinch 返回主菜单 → home, board cleared, no Continue (`r1_36_home_after_victory`); second victory →
  pinch 再来一局 → setup with the same seats (`r1_40_play_again_setup`).
- Plane positions and headings checked on a 12-plane layout (`r1_91_board_many`): every plane on its expected cell,
  noses along the direction of travel, none floating.

### 7 · Menu, pause, restart, save, continue — FAIL
- PASS: pinch 菜单 → `16:51:56.331 GAME paused` (`r1_42_pause_page`); no AI action for 35 s while paused; pinch 继续游戏 →
  `16:53:27.742 GAME resumed`; after a later resume the AI rolled 0.7 s later (`16:54:09.415 GAME resumed`, `16:54:10.116 DICE roll id=73`).
- PASS: 重新开始 → confirmation (`r1_44_restart_confirm`), Cancel → pause page (`r1_45`), confirm → setup (`r1_46`),
  Back → pause page (`r1_47`), Start → `16:55:44.814 GAME new`.
- PASS: 保存并离开 → home "继续航程 · 2 席 · 第 2 回合 · 已保存" (`r1_50_left_home`) → 继续航程 →
  `16:56:43.814 GAME restored phase=READY turn=0 round=2`, DUMP identical (mint `[13,-1,-1,-1]`, apricot `[-1,-1,41,-1]`) (`r1_51_continued`).
- PASS: `am force-stop` + relaunch (pid 25011) → 继续航程 → `16:58:52.049 GAME restored phase=READY turn=0 round=2`, same DUMP (`r1_55_continued_after_kill`).
- **FAIL D1 (S1)**: save/kill during the six choice or plane pick → Continue → dead end (details below).
- **FAIL D2 (S3)**: pausing during a roll does not stop the turn: `16:53:33.032 DICE roll id=71 seat=1 team=APRICOT result=6`,
  `16:53:33.079 GAME paused`, `16:53:34.107 DICE settled id=71 result=6 legal=4`, `16:53:34.108 MOVE begin id=72 seat=1 plane=1 LAUNCH`,
  `16:53:34.741 MOVE done id=72` — the AI launched while paused.

### 8 · Settings — PASS with defects
- Language by pinch (设置 → English, `r1_82_settings_en`): every string of home (`r1_87_home_en`), setup (`r1_88_setup_en`),
  turn page (`r1_85_turn_en`, `r1_90_turn4_en`), pause (`r1_84_pause_en`), rules (`r1_86_rules_en`), settings
  (`r1_82`, `r1_83_settings_en_scrolled`) switched; no crash (same pid 25963). zh: home `r1_54_view`, turn `r1_10_game_start`,
  settings `r1_70_settings_zh`, pause `r1_42`. Exception: the dialog's Cancel button is English in zh (D9).
- Speed by pinch, same 4-cell walk `1->5`: quick `MOVE play id=9 total=0.62`, standard `MOVE play id=12 total=0.90`,
  gentle `MOVE play id=15 total=1.10` (`r1_72_settings_standard`, `r1_74_settings_gentle`).
- Reduced motion by pinch (`r1_77_settings_reduced_on`): `DICE play id=17 result=6 duration=1.13 reduced=true` →
  `DICE settled id=17 face=6` 0.35 s later; `MOVE play id=18 ... 1->7 total=0.33 reduced=true`;
  `MOVE play id=20 (WALK 5>8, JUMP 8>12) total=0.34 reduced=true`; die shows 3 up right after the roll (`r1_80_reduced_die_now`).
  Planes slide (no lift); the copy says they fade (D19).

### 9 · Soak — PASS (watchdog coverage partial)
`bash tools/test/soak.sh H,E,S,E 900`, run last, nothing else touched the emulator meanwhile:
```
ENDED after 899 s
rolls=182 moves=156 passes=26 watchdogs=0 captures=9 flies=2 jumps=27 bounces=13 finishes=5
10-12 17:30:05.425 ... MOVE done id=646 phase=ENDED turn=1 round=70 winner=1
```
- Ended by victory (Sky, AI easy, round 70); the victory page showed (`r1_99_after_soak`).
- No crash: crash buffer has no `io.embodit.cloudair` entry, same pid 25963 before and after.
- Caveat: the device's 2 MiB main log buffer only kept the last ~8 minutes (17:22:04 → 17:30, throw ids 309 → 646;
  `.scratch/logs/round1_soak_full.log`), so the counters and "zero watchdog" cover about the second half of the run
  only. 538 of the 1860 retained CloudAir lines are `region half=...`. Suggest letting `soak.sh` stream
  `logcat -s CloudAir` to a file from the start so the counts cover the whole run.
- The game took 899 s against a 900 s budget at quick speed; 4-seat autopilot games can exceed the soak window.

### 10 · Visual review — FAIL (see D6–D8, D10–D14, D16–D18)

## Defects (severity-ranked)

### S1
**D1 · A game saved in the six choice or the plane pick can never be continued.**
Repro A (six choice): start a game (recommend mode), get a six with a plane on the loop and planes in the hangar
(`dice 6` + roll) → six choice shows → 菜单 → 保存并离开 → 继续航程 → the six choice shows again → pinch 出动新飞机 or
前进 6 步 → nothing happens. Evidence: `16:59:52.964 GAME restored phase=DECISION turn=0 round=2`; window got both pinches
(`ViewRootImpl ACTION_DOWN/UP x=307 y=329` and `x=826 y=327`) but no `MOVE begin`; `CMD kind a=launch phase=DECISION`,
`CMD kind a=advance phase=DECISION` also change nothing. Same after `am force-stop` + relaunch:
`17:01:57.449 GAME restored phase=DECISION` (pid 25963), pinch → nothing. Shots `r1_58_restored_decision`,
`r1_59_restored_decision_after_pinch`, crop `r1_def07_restored_decision_dead.png`.
Repro B (plane pick): `mode manual`, two planes on the loop, roll a non-six → plane list → 保存并离开 → 继续航程 →
"选择一架飞机" with an **empty** list and no rings; pinching a plane gives `TAP target=plane:0:0` but no move;
`CMD plane a=0 phase=SELECT`. Evidence `17:03:17.298 GAME restored phase=SELECT turn=0 round=1`, shots
`r1_64_select_before_leave` vs `r1_65_restored_select`, crop `r1_def08_restored_select_empty.png`.
Only way out is 重新开始 (progress lost). Since the game is saved after every transaction, a crash, a system kill or the
user leaving while the choice is open loses the game. Likely cause: `GameFlowUseCase.continueSaved()` rebuilds the
session with `legalPlans = emptyList()`; `chooseKind`, `choosePlane`, the plane list and the pick rings all read
`session.legalPlans`. Recompute the plans on restore (for SELECT filtered by the saved `selectedKind`).

### S2
None confirmed. (D4 becomes S2 if the classic "jump onto the fly-over cell then fly" rule is intended.)

### S3
**D2 · Pause does not stop a roll or move already in flight.** Repro: during an AI turn open 菜单 (or `pause`) while the
die is in the air → the die settles, the AI's move is chosen and played behind the pause page (log in item 7). Same for a
human's auto-picked move. `diceSettled` / `beginMove` ignore `paused`. Expected: nothing moves while paused, or the
resolution waits for Resume.

**D3 · Leaving or being killed mid-roll discards the committed roll (free re-roll).** Repro: roll, then 保存并离开 (or
force-stop) before the die settles → 继续航程 → READY, roll again. Evidence `17:04:38.378 DICE roll id=6 ... result=4`,
`17:04:38.733 CMD leave`, `17:04:54.131 GAME restored phase=READY turn=0 round=1`. Design §6 says restore "不重掷"; the
roll is only saved once the move is applied.

**D4 · A connected jump that lands on the fly-over cell does not fly (rule ambiguity).** Seen in play:
`MOVE begin id=74 seat=1 plane=0 ADVANCE dice=1 15->20 segs=WALK:15>16, JUMP:16>20` (apricot ended on its fly-over
entrance). `RulesEngine.plan` only checks `FLY_FROM` after the walk. Design §2 says "一次动作最多一跳一飞" and the engine
comment says "chains at most one fly-over and one jump", which reads as jump → fly being allowed (the classic rule).
Needs a product decision; S2 if the classic rule is intended.

**D5 · Turn page overflows with 4 seats.** The fleet row has no room for the 4th fleet: Lavender's "0 / 4 归航" /
"0 / 4 home" is laid out one character per line down the right edge, pips truncated in English. Repro: any 4-seat game.
Shots `r1_10_game_start`, `r1_90_turn4_en`; crops `r1_def02_fleet_row_zh.png`, `r1_def02b_fleet_row_en.png`.

**D6 · Control window opens partly outside the view.** At every launch (`r1_00`, `r1_52_relaunch`, crops
`r1_def01*.png`) the board is centred and the control window's right ~third is off-screen: 玩法说明, 返回 / Back on setup,
AI·聪明 and 菜单 on the turn page cannot be seen or pinched without turning the head ~20°. Physical-device FOV may
differ; please check `Placement.right(board)` spacing or bring the window closer.

**D7 · Six choice and plane list overlap the fleet row.** The hints under 出动新飞机 / 前进 6 步 run into the fleet row
and the 4th fleet loses its text (`r1_20_six_choice`, crop `r1_def03_six_choice_overlap.png`); the second plane button
touches the fleet row (`r1_24_manual_select`, crop `r1_def04_plane_list_overlap.png`).

**D8 · 4-seat setup hides the 4th seat.** At the default window size only three seat rows fit, the 3rd row runs under the
Start button and there is no scroll affordance; Lavender is only reachable by dragging the list
(`r1_02_setup4` vs `r1_03_setup4_scrolled`, crop `r1_def05_setup4_hidden_row.png`).

**D9 · Confirmation dialogs show "Cancel" in English when the app language is 中文.** Repro: language 中文 (system
English) → 开始游戏 with a saved game, or 菜单 → 重新开始 (`r1_09_replace`, `r1_44_restart_confirm`, crop
`r1_def06_dialog_cancel_untranslated_zh.png`). The title, body and action (resolved by the caller) are Chinese; the
Cancel label is resolved inside the `AlertDialog` composition, which does not get the localised `LocalContext`.

### S4
- **D10** Dialog action labels wrap to two lines in a pill: "开始新航\n程", "Start new\ngame" (`r1_09_replace`, `r1_89_replace_en`, crop `r1_def06b_dialog_wrap_en.png`).
- **D11** After Continue the 3D die shows 6 up while the panel shows the last result 1 (`r1_55_continued_after_kill`, crop `r1_def09_die_face_after_restore.png`).
- **D12** A plane on lane cell 55 is mostly hidden behind the control tower from the default view (`r1_33_bounce_after`, crop `r1_def10_lane_plane_behind_tower.png`).
- **D13** Four planes home overlap into one cluster on the petal (`r1_35_victory_after`, crop `r1_def11_home_planes_overlap.png`).
- **D14** Victory page does not appear when the game was started while the control nav was on HOME (e.g. `new` broadcast): after `MOVE done ... phase=ENDED` the turn page stays with a disabled roll button (`r1_38_play_again`); 菜单 reveals the victory page (`r1_39_ended_menu`). `ControlViewModel.reduce` only maps ENDED to VICTORY for nav pages GAME/PAUSE; any future path that shows a game through the HOME→GAME fallback hits this.
- **D15** The die is never the gaze target; the tray collider always wins (`TAP target=tray`, hover probes). Harmless because both throw.
- **D16** When the seat count changes, kinds follow the row index, not the team: 3 seats (Sky Smart, Apricot Easy) → 2 seats gives Apricot "AI·聪明" (`r1_05_setup3` → `r1_06_setup2`).
- **D17** Manual mode six choice still says "推荐一架在途飞机前进 6 格" / "A recommended plane flies six spaces" although the player then picks (`r1_25_manual_six`).
- **D18** Rules page item 4 does not mention the +4 jump after the fly-over (`r1_86_rules_en`).
- **D19** Reduced-motion copy says planes fade ("飞机淡入落点" / "Planes fade to their destinations"); they slide (design §5 also says 淡入落点).
- **D20** Docs: design-doc §2 says the lane is 51–56 with 56 the centre; the code (and AGENTS.md) use 57 for the centre, so a lane walk is one step longer than the design text.
- **D21** Timing vs design §5: a 4-cell walk at standard is 0.90 s (design 0.12 + 0.17 n = 0.80 s); one throw settled 0.81 s after `DICE play` at quick speed (design 1.0–2.5 s physics). Low frame rate may contribute.

- **D22** `region half=(0.45, 0.25, 0.5) rootY=... tiltDeg=...` is logged at Info on every board update (29 % of the retained CloudAir lines), which speeds up log rotation and loses evidence.

## State left on the emulator
Language restored to 中文 (`lang zh`), speed quick (set by the soak), reduced motion off, sound on, mode recommend.
The emulator camera stays yawed to the right of the initial view (net right-drag of about 860 framebuffer px over the
session); a relaunch places the board in front of the current view again.
The saved game is the finished soak game (Sky won), so the home page shows no Continue.

## Not tested / limits
- "跟随系统 / System" language, the sound toggle (no audio capture), "全自动决策 / Fully automatic" mode.
- Controller mode, dragging the windows, physical-device FOV, comfort and frame rate.
- The die tumble was verified from a single mid-air frame plus logs, not a recording.
- Pause during a human move (only the AI case was reproduced; same code path).
