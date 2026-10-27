# QA brief · Cloud Air round 2 (regression of the round-1 defects)

Same product, tools and constraints as `qa-brief-1.md` (read it, plus `AGENTS.md` and `docs/test/round1.md`). The
round-4 debug build is installed and running on emulator-5554. New debug command: `continue` (restores the saved
game; logs `continue restored=true plans=N`). The emulator camera may still be yawed from round 1; a relaunch
re-centres the board.

Verify each fix below with real pinches where a control is involved, and with the debug broadcasts to reach states.
Write `docs/test/round2.md` (PASS/FAIL per item with evidence) and reply with a short summary.

| Defect | What to check |
| --- | --- |
| D1 (S1) | Save and leave during the six choice → Continue → both choice buttons work (`MOVE begin`). Same for the manual plane list (`mode manual`): the list comes back with rings on the board, pinching a plane moves it. Also `am force-stop` + relaunch + Continue from the six choice. |
| D2 | Pause (菜单) while an AI's die is in the air: no `MOVE begin` until 继续游戏; after resume the move plays (`DICE settled ... deferred=paused` then `MOVE begin`). |
| D3 | Roll, then 保存并离开 before the die settles → Continue shows the plane moved by the committed roll (log: `DICE settled` + `MOVE done` before `GAME restored`), no second roll. Force-stop mid-throw → relaunch → Continue → the same value is rolled automatically (`GAME restored ... pendingRoll=N` then `DICE roll ... result=N`). |
| D4 | `set 0:0:15`, `dice 1`, roll → `segs=WALK:15>16, JUMP:16>20, FLY:20>28, JUMP:28>32`. |
| D5 / D7 | 4-seat game: the fleet strip shows all four fleets with pips and `n/4` on one line; six choice hints and the plane list (manual, 4 planes on the loop: `set 0:0:1,0:1:5,0:2:9,0:3:13`, `dice 2`) do not overlap the strip; the list scrolls if needed. |
| D8 | Setup with 4 seats: all four rows visible without scrolling at the default window size. |
| D9 / D10 | Language 中文: the restart and replace dialogs show 取消 (not Cancel); action labels on one line in both languages. |
| D11 | After Continue the 3D die shows the last rolled value. |
| D13 | Four planes home (`set 0:0:57,0:1:57,0:2:57,0:3:54`, `dice 3`, roll): the home planes sit in two stacked pairs beside the tower, each visible. |
| D14 | Start a game with the `new` broadcast while the window is on HOME, reach a victory: the victory page appears by itself. |
| D15 | Hover/pinch on the die's upper half targets `die` (`TAP target=die`); the tray floor still targets `tray`. |
| D16 | Setup 3 seats, set Sky to AI·聪明, switch to 2 seats and back to 3: Sky keeps AI·聪明. |
| D17 | `mode manual`, six with both options: the hints say "然后选一架…" / "Then pick a plane…". |
| D22 | `region half=` appears once per launch in the log, not per frame. |

Also: one more 2-seat soak (`bash tools/test/soak.sh H,S 900`) at the end, nothing else on the emulator meanwhile;
the log now streams to `.scratch/logs/soak_*.log` so the counters cover the whole run.
