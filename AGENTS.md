# 云间航线 Cloud Air: project guidance

Aeroplane chess (飞行棋) for PICO OS 6 Shared Space, PICO Spatial SDK 6.1.9, package `io.embodit.cloudair`.
Two surfaces:

- **Cloud airport** (default container `CloudBoard`, Volumetric 0.78 × 0.5 × 1.0 m, no base panel, fixed world
  scale): the classic mat (0.72 m, tipped 28° toward the player) as a 15 × 15 grid of tiles with a circle on every
  cell, four corner hangars with rims (the active one is deep-coloured and lifted), a 52-cell loop round the cross,
  four home lanes to the centre hub with its control tower, the Blender airplane per plane, and a dice tray in front
  of the mat with the Blender die. Skin v2 (`design/skin-v2/`): linen mat, sage binding, bent-oak frame and a turned
  porcelain tower come from `models/board.glb`; the oak tray with its padded felt comes from `models/tray.glb`.
  Both are built by `tools/build_skin.py`, and the scene falls back to the v1 primitives if a GLB fails to load (a ring under it while the human may roll). A pinch on the die or
  the tray throws; a pinch on a plane picks it in manual mode (rings mark the candidates).
- **Control window** (`WindowContainer("CloudControl")`, Planar 560 × 340 dp, `Placement.right(board)`): home,
  setup (2–4 seats, human / AI easy / AI smart), the turn page (die, roll button, status, fleets), six choice, plane
  pick, pause menu, settings (language, decision mode, speed, sound, reduced motion), rules, victory.

The design contract is `design/design-doc.md`; the upstream V4 package from Codex is referenced there.

## Rules that stay in force

- All 2D UI is SpatialUI (`com.pico.spatial.ui.*`) inside `CloudAirTheme` (a `PicoTheme` with all 16 `ColorScheme`
  roles assigned, Noto Sans). No Material / Material3. Every `Text` passes a colour or is marked
  `// design-style: inherited-content-color <Provider>`.
- Rules live in `domain/usecase/RulesEngine.kt` only. A roll is committed before anything moves
  (`GameFlowUseCase.roll`), the dice trajectory is derived from the committed result (`DiceSolver`), and the
  board only reports `DiceSettled` / `MoveFinished`. Watchdogs in the flow finish animations a board never reports.
  A committed roll is written to the save at once (`pendingRoll`) and replayed if the process dies mid-throw; a
  throw that settles while paused waits for resume; leaving resolves a throw or move in flight first; a restored
  six choice / plane pick recomputes its legal plans (`restoredPlans`).
- Per-frame 3D motion belongs in `BoardScene.onFrame` (driven by `BoardFrameSystem`), never in Compose.
- Copy lives in `res/values` (en) and `res/values-zh`; the in-app language switch installs a localised context in
  `CloudAirTheme`, so plain `stringResource` follows the setting.

## Layout of the code

| Path | What it holds |
| --- | --- |
| `domain/model/` | `Board` (grid geometry, loop, lanes, hangars, fly-over), `Team`/`Seat`/`Settings`, `Game` (phases, `MovePlan`, `GameSnapshot`, `GameSession`). |
| `domain/usecase/` | `RulesEngine` (legal plans, jumps, fly-over, captures, bounce, AI picks), `DiceSolver` (240 Hz rigid cube, symmetry-mapped result), `SaveCodec` (line format), `GameFlowUseCase` (turn sequencing, AI timing, persistence, test hooks). |
| `data/` | `GameSessionStore` (the one live session), `GameRepository` / `SettingsRepository` (+ Prefs and Fake implementations). |
| `platform/` | `SpatialApplication`, `LaunchActivity`, `CloudAirGraph` (object graph), `SoundPlayer`; debug builds add `DebugCommandReceiver`. |
| `ui/board/` | `BoardScreen` / `BoardViewModel` / `BoardUiState`; `components/BoardView` (SpatialView, taps, model loading); `scene/` (`BoardScene`, `BoardLayout` metres, `SceneResources`, `PlaneView`, `Motion`, `BoardFrameSystem`). |
| `ui/control/` | `ControlScreen` / `ControlViewModel` / `ControlUiState`; `components/` (`Widgets`, `MenuPanels`, `GamePanels`). |
| `ui/theme/` | `CloudAirTheme`, `CloudAirColors`, `CloudAirType`. |
| `Main.kt` | The two containers and their placement, nothing else. |

Board space: the mat's top surface is y = 0 of `boardRoot`, +X right, +Z toward the player; grid row 0 is the far
edge, column 0 the left edge; cell pitch `BoardLayout.CELL` = 0.72 / 15 m. `BoardScene.setRegion` tips the root by
`TILT_RAD` and rests the tray's near rim on the volume floor. Progress per plane: -1 hangar, 0..50 loop
(0 = launch cell, 50 = lane entrance), 51..56 lane, 57 centre. Loop cell `i` is colour `i % 4`; fleet `t`'s cells are
`(13 t + p) mod 52`. Fly-over: relative 20 → 28 across the opposite lane's cell 53, then the +4 jump.

## SDK facts learned the hard way (see also ../threeviews/AGENTS.md and ../spatial-ruler/AGENTS.md)

- Creating a mesh, material or collision shape waits for the renderer: `SceneResources()` runs on
  `Dispatchers.Default`; entities are assembled on the main thread. Shared resources are `toGlobal()` and closed in
  `BoardScene.dispose`.
- The Blender GLB keeps Blender's axes: the plane's length runs along Y (nose at -Y), canopy toward -Z; `PlaneView`
  pitches the model +90° about X. The die's pips: 1 on -Z, 6 on +Z, 2 +X, 5 -X, 3 +Y, 4 -Y (`DiceSolver.FACE_NORMALS`).
- `Entity.clone(CloneOptions(recursive = true, shouldShareMaterialInstance = true))` makes the 16 planes from one
  loaded template; the airframe material is swapped per fleet by entity-name prefix `Airframe`.
- `MeshResource.createTorus` lies flat in XZ already; `createCylinder` stands along Y. A pinch on a collider arrives
  as a 2D touch on the SpatialView and `detectSpatialTapGesture` reports it with `targetEntity` = the collider's
  entity (log `TAP target=`); the system logs `SELECT_ENTERED touch down <name>` first.
- A `SpatialView` must see an Activity up its context chain. `Context.createConfigurationContext` returns a bare
  `ContextImpl`, and composing the board under it crashes at launch ("not attached to an Activity, cannot
  retrieve SpatialContainer") as soon as a non-system language is persisted. `CloudAirTheme.localized` therefore
  wraps the Activity context in a `ContextWrapper` that only overrides `getResources()`.
- `board.glb` / `tray.glb` are in board metres. Their origins are the mat's top centre and `TRAY_CENTER`
  (felt top = solver floor). The tray's inner box must stay the solver's 2.8 × 1.8 × 0.5 units × `DIE_SCALE`, so
  change `DiceSolver` and `tools/build_skin.py` together. The tap target is still the thin `trayShape` collider,
  never the GLB. Sounds come from `tools/make_sounds.py`. Keep peaks at -11 … -16 dBFS with nothing above
  ~3 kHz, and check with `--check`.
- Moves are timelines of clips (`Motion.kt`): walks are one hop per cell; the plane's attitude (pitch, bank, the
  fly-over barrel roll) lives on the `attitude` entity between holder and lift in `PlaneView`.

## Build, test, run (Git Bash; device commands pinned to emulator-5554, AVD `PICO_6.1_Air`)

```bash
JAVA_HOME='C:\Users\Admin\.pico\primer-cli\jdk\jdk-21.0.12.1+1' PICO_HOME='C:\Users\Admin\AppData\Local\PICO\sdk' \
  ./gradlew.bat assembleDebug :app:testDebugUnitTest --no-daemon --console=plain
python tools/count_tests.py
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n io.embodit.cloudair/.platform.LaunchActivity
bash tools/harness/ca.sh shot NAME | click X Y | drag ... | cmd NAME [a] | waitlog REGEX | log | fast on
```

Take a screenshot twice; the first frame can be stale. Other sessions own emulator-5556 and emulator-5558.

Debug commands (`ca.sh cmd NAME [a]`): `new H,E,S` · `roll` · `dice 6,3` · `kind launch|advance` · `plane N` · `back` ·
`set S:P:V,...` · `auto on|off` · `pause` / `resume` / `leave` / `continue` / `abandon` · `mode recommend|manual|auto` ·
`speed gentle|standard|quick` · `lang system|zh|en` · `reduced on|off` · `sound on|off` · `dump` · `evidence`.

Log tag `CloudAir`: `GAME new|restored|paused|resumed`, `DICE roll|settled|play|watchdog`, `MOVE begin|play|played|done|watchdog`,
`PASS`, `SAVE failed`, `TAP target=<entity>`, `SCENE built`, `FLEETS built`, `MODELS loaded`, `CMD`, `DUMP`, `EVIDENCE`.

## QA and review rounds

`docs/review/design-review-1.md`, `design-review-2.md` (opus design reviews; their P1/P2 items are implemented),
`docs/test/round1.md` and `round2.md` (opus QA with real injected input). Everything from round 1 is fixed and
re-verified in round 2 except D6 (the control window opens partly outside the emulator's view; `Placement.right`
is owned by the system, so check it on a headset) and D21 (hop timing a little above the design at the emulator's
frame rate). Round 2's leftovers (clipped English dialog label, die never the gaze target, seat kinds forgotten,
4th plane row cut) are fixed in round 5 (dialog buttons share the row, thin tray collider, kinds kept per fleet,
44 dp plane rows). `tools/test/rule.sh` (QA helper) and `tools/test/soak.sh` drive the game
through the debug broadcasts; `tools/record_demo.sh` records the showcase clip.
