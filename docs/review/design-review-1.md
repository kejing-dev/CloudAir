# Design review 1 · Cloud Air (round 1 build)

Crops (in `.scratch/shots/`): `review_r2b_{board2x,minthangar4x,apricot4x,centre4x,tray4x,window}`,
`review_tilt2_board2x`, `review_game3_{board2x,window}`, `review_others_stack`, `tilt2_planes`. No capture shows a
plane on the loop, so the on-track size is computed from the `plane.glb` bounds (wingspan 0.788 units).

## 1 · Verdict

**Works.** The classic cross reads at once (r2b at 2x): four hangars, 3-wide arms, the four-colour cycle,
single-colour home lanes, launch arrows and a centre pinwheel. The 22° tilt fixed the unreadable flat board of
the older build (compare `review_game3_board2x`). The palette keeps the calm upstream mood, the window has one
obvious primary action, and the die is a real object with correct pip layout.

**Undermines "good-looking and fun".**
1. **Everything that matters is white-on-white.** Pastel planes look cream on ivory spots on pastel slabs
   (`review_r2b_apricot4x`, `tilt2_planes`), and the ivory die sits in a near-white tray (`review_r2b_tray4x`). The
   objects you play with have the least contrast on the board. Emissive glow (0.22–0.34) flattens the rest.
2. **Scale is off.** Planes are 1.9 cells wide on the track and the mat is only 0.66 m, while the layout leaves
   about 10 cm of the 0.5 m volume height unused.
3. **The board never invites you to act.** `BoardScene` never reads `canRoll` or `selectable`, the planes have no
   hover, and the 2D die shows the result while the 3D die is still tumbling (`review_game3_window`).
4. **Planes glide instead of hopping.** A 6-cell walk is one 2.2 cm hump in 0.84 s, so you cannot count the
   cells as the plane moves.

## 2 · Punch list

### P1: must fix before showing the user

| # | Change | Where | Why |
|---|---|---|---|
| 1 | **Paint planes in a real fleet colour.** Add `CloudAirColors.planeOf(team)` = 0.4·pastel + 0.6·deep (mint `#5A9F8B`, sky `#6596BE`, apricot `#BB8C67`, lavender `#9485B3`). Airframe PBR `glow 0.3 → 0.12`, `roughness 0.35 → 0.45`. | `SceneResources.airframe`, `CloudAirColors` | Parked planes now look cream/white and fleets can't be told apart. The GLB's own airframe base (`#337A54`) shows the intended depth. |
| 2 | **Shrink planes to about 1.2 cells.** `PLANE_SCALE 0.105 → 0.072`, `PLANE_SCALE_PARKED 0.082 → 0.072`, `PLANE_SCALE_HOME 0.07 → 0.05`. Wingspan becomes 5.7 cm = 1.18 cells at the new CELL (item 3). | `BoardLayout` | A 0.105 plane is 8.3 cm across a 4.4 cm cell and hides both neighbours, which breaks counting and same-colour stacks. One scale also removes the grow-on-launch pop. |
| 3 | **Use the volume.** `MAT 0.66 → 0.72` (CELL 44 → 48 mm, TILE 39 mm), `TILT_RAD 0.384 → 0.489` (28°). Cap the arcs at JUMP 0.05 m and FLY 0.10 m. Computed vertical span including `FLOOR_LIFT` is about 0.48 m of 0.5 m; depth is about 0.79 m of 1.0 m. Verify with the `evidence()` visualMin/visualMax log. | `BoardLayout.MAT`, `BoardScene.TILT_RAD`, `segmentMotion` | At 22° the far hangars are foreshortened to about 40 % (r2b). A seated viewer needs more tilt, and 9 % more cell is free. (`design-doc` §1 says 0.80 m, which cannot fit with the front tray at a useful tilt.) |
| 4 | **Make the die pop and invite a pinch.** Tray floor goes to a dark felt: new token `TrayFelt #3F5E58`, `glow 0.05`, `roughness 1.0`; walls stay `TrayStone`. When `state.canRoll`, show the `turnRing` torus around the die on the tray floor in `deep(activeTeam)`, plus a 3 mm idle bob at 0.8 Hz. Hide both on throw. | `SceneResources.trayFloor`, `BoardScene.onFrame/apply` | The porcelain `#FAF5DE` die on `#CBD8CF` has almost no contrast, and nothing on the board says "pinch me". The 4.8 mm pips read fine once the background is dark, so keep `DIE_SIDE 0.042`. |
| 5 | **Don't spoil the roll.** In `RollArea`, pass `null` while `Phase.ROLLING`. A null face draws all six pips at 20 % alpha, not an empty ivory square. Pip colour = `deepOf(currentTeam)`, not always `MintDeep`. Drop the duplicate "Rolling…" status line. | `GamePanels.RollArea`, `Widgets.DiceFace` | game3 shows a 6 in the window while the 3D die is mid-air, and r2b shows a blank tile that looks broken. |
| 6 | **Mark tappable planes.** Add `HoverEffectComponent` to `PlaneView.holder`. For each plane in `state.selectable`, add a deep-colour ring (turnRing at 0.8 scale) under the plane and a 3 mm bob. Glow the recommended plan's destination tile with `r.glow`. | `PlaneView`, `BoardScene.apply` | The window says "Plane 2 · Space 3 → 9", but the board has no numbers and no cue, so the user can't map choice to plane. |
| 7 | **Hop cell by cell** (numbers in §3). | `BoardScene.segmentMotion` / move `Clip.update` | Visible counting is the core pleasure of the genre. |
| 8 | **Right-size the window.** `defaultSize 640×440 → 560×340 dp` (min 520×300). | `Main.kt`, `ControlScreen` constraints | About 60 % of the glass is empty (`review_r2b_window`), and the window looks as big as the board. The board must be the hero. |

### P2: should

- **Colour and shading.** Mat glow `0.22 → 0.10`, tile glow `0.22 → 0.14`, so side faces shade and the board stops
  reading as a flat print. Set the `midOf` mix to `0.32 → 0.40` so mint vs sky and sky vs lavender separate on the
  loop (they are the closest pairs in `review_r2b_centre4x`).
- **Home lanes as runways.** Lane tiles get a 0.55 mix toward deep, 1.5× `TILE_HEIGHT` (8 mm) and full `TILE`
  width (now 0.9×). Add an inward deep arrow on each `Board.laneEntrance` cell. Today the lanes share `mid` with
  the loop.
- **Hangars.** `HANGAR_SIZE 5.2 → 4.4 CELL`; the four slabs now cover about 48 % of the mat as flat pastel. Add
  rim walls (4 boxes, 10 mm tall, 0.2 CELL thick, `mid`) like the upstream V3 trays. Mark the active turn by
  switching that rim to `deep` and lifting the hangar 3 mm over 240 ms. This replaces the torus, which hides under
  the parked planes (`review_r2b_minthangar4x`). Give unused seats `RunwayPale` slabs.
- **Fly-over marks.** Five dashes of 0.16×0.32 CELL over a 2-cell span merge into one bar ("—" across each arm in
  r2b). Use 3 dashes, 0.12×0.22 CELL, at t = 0.25/0.5/0.75, 2.5 mm proud. Put a deep `turnRing` at 0.45 scale on
  each `FLY_FROM` tile so the entry is findable.
- **Launch arrows.** Shaft `TILE*0.18 → 0.26` wide and `0.58 → 0.7` long; heads to match; height
  `0.0016 → 0.0025`. They are barely visible at 2x zoom.
- **Route highlight.** Besides the `RouteGlow` swap, raise highlighted tiles 3 mm. Cream `#FFF1C7` alone is
  close to the apricot tiles.
- **Window copy and hierarchy.** Replace the tertiary "Mint · seat 1 · Round 1" with an upstream-style pill
  ("Your turn · Mint fleet"), and drop the word "seat". Turn `FleetRow` into a strip: per seat an 18 dp
  `TeamDot` plus 4 plane pips (outline = hangar, pastel = flying, deep = home). On AI turns, show a status line
  instead of the disabled 56 dp button.
- **Pinwheel.** The four 45° squares read as a diamond quilt (`review_r2b_centre4x`). Add an ivory hub (cylinder,
  r = 0.42 CELL, 9 mm tall) so it reads as four petals pointing at the lanes. See the tower in §4.

### P3: nice

- Notch diamonds: use a `TILE*0.74` mesh so the rotated tile stays inside its cell. They currently poke into the
  gaps and look loose.
- Home packing: `Board.homeSpot` side offset `±0.32 → ±0.42`, back `-0.55/-0.95 → -0.5/-1.05`. At the current
  scale, finished planes overlap by about 50 %.
- Mat frame: a slab of `MAT + 0.024` m under the mat in a sage `#A9C2B7`. This echoes the mat's piped border;
  `matEdge` is defined but unused.
- Sync `design-doc.md`: MAT 0.80 → 0.72; fly-over 17→29 (+12) vs `Board.FLY_FROM/TO` 20→28 (+8); the copy "flew
  across the board" (it crosses one arm).

## 3 · Motion (BoardScene, standard speed)

| Move | Now | Proposed |
|---|---|---|
| WALK | One 2.2 cm hump over the whole path; 0.30 + 0.09·n s (≤ 0.9); one `inOut` over the path | One hop per cell, 1.2 cm, each hop `inOut` on its own; 0.12 + 0.17·n s (≤ 1.3) |
| BOUNCE | 1.8 cm, ≤ 0.7 s | Same per-cell hops; the reverse hops at 0.17 s each |
| LAUNCH | 5 cm, 0.65 s, scale 0.082 → 0.105 | 4 cm (as in the doc), 0.8 s, horizontal `Easing.out`, no scale change |
| JUMP | 6 cm, 0.5 s | 5 cm, 0.55 s, with an 80 ms crouch (lift −2 mm) before take-off |
| FLY | 12 cm, 0.9 s | 10 cm, 1.1 s; one 360° barrel roll about the nose between u 0.3 and 0.7 |
| FINISH | One 3 cm hump, ≤ 0.95 s | Lane hops, then a 4 cm hop onto the hub and a 360° yaw in 0.35 s |
| CAPTURE | 0.45 s, 9 cm, opacity 1 → 0.5 → 1 | 0.8 s, 6 cm, starting 0.1 s after the attacker lands; one full yaw spin; no opacity dip (it reads as a glitch) |
| Turn change | Snap; ring pulse ±6 % at 2.4 rad/s | 240 ms `Easing.out` pop (0.7 → 1); pulse ±4 % at 1.8 rad/s |
| Die settle | 0.24 s hold | Within the hold, a 1.0 → 1.12 → 1.0 scale pulse over 0.28 s |

Attitude is the biggest single fun gain. On `lift` in `PlaneView`, pitch the nose with the climb rate
(clamped ±20°) and bank into corners from the yaw rate (±15°). Today planes stay level through every arc.

## 4 · Missing charm from the classic mat (box/cylinder/torus only)

1. **A white circle on every cell**: ivory cylinder, r = 0.2 CELL, 0.8 mm proud. It is the mat's signature and
   gives the eye something to count.
2. **A control tower in place of the central badge**: ivory cylinder (r 0.12 CELL, 45 mm) with a cab (r 0.22 CELL,
   12 mm) and a torus beacon in the active fleet's deep colour. Finished planes park around it.
3. **Sky-bridge dots in place of the printed birds on the fly-over**: 5 cylinders (r 2 mm) floating along the real
   10 cm flight arc. They brighten while that flight plays.
4. **One cloud puff** (three overlapping ivory cylinders) drifting over a quadrant corner, after the upstream cover.
