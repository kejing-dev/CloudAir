# Design review 2 · Cloud Air (round 2 build)

Source: `r4b.png`, a fresh 4-seat game with mint to roll. Crops are `.scratch/shots/review2_*` (`board2x`, `window2x`,
`tray4x`, `flyarc6x`, `toparm4x`, `mintpair6x`/`lavpair6x`). r4b is a still, so motion is checked in code only.

## 1 · Round-1 status

**Resolved**
- P1-1 to P1-4: fleet paint, the 1.2-cell plane, 0.72 m at 28°, and the felt tray with its ring.
- P1-7: per-cell hops and pitch in code (not seen in motion).
- P2: shading, lanes, launch arrows, route lift, hub and tower, the 3-dash count.
- P3: notch, home packing, frame, doc sync.

**Partly resolved**
- P1-5: the empty die draws 9 dots (#7).
- P1-6: pick rings and hover are in, but the recommended destination is still not glowed.
- P1-8: the window was resized, but the strip overflows (#2) and the lower glass is empty (#8).
- Hangars are 4.6 cells (fine), but the lift adds a P1 (#1).

**Open**
- `RunwayPale` for unused seats (`SceneResources.neutral` is unused).
- The cloud puff.
- The turn pop and the jump crouch.

## 2 · New issues

### P1

1. **The active fleet sinks into its pads.** `onFrame` lifts the whole `hangar.group` 3 mm, but parked planes are
   not children of it. The spot top goes to 10.5 mm, while the plane origin stays at 8.5 mm and its shadow at
   8.9 mm. In `mintpair6x` the pads cut the fuselages and there are no shadows; the lavender planes stand properly.
   This is the player's fleet in the first frame. **Fix:** lift only the four `rims` (y = `HANGAR_HEIGHT +
   RIM_HEIGHT/2 + lifted`) and keep the group at 0.
2. **The 4th fleet is cut off the strip.** At about 1 px per dp, each seat is about 152 dp wide. Four seats need
   about 590 dp, but the content width is 512 dp. **Fix:** set `turn_home_count` to `"%1$d/4"` (the pips already say
   "home"). In `FleetStrip`, change `spacedBy` from 18 to 16 dp and `HGap` from 6 to 4 dp. That gives about 436 dp.

### P2

3. **The middle fly-over dash is hidden.** Its top is at 8.85 mm, under the lane circle at 9.05 mm (`flyarc6x`).
   **Fix:** set dash k = 2 to y = `LANE_TILE_HEIGHT + CIRCLE_HEIGHT + 0.0013f`.
4. **The sky-bridge beads are invisible.** They are pastel with r = 2.2 mm. **Fix:** use the `deep` material at
   r = 3.5 mm, shown only for the active fleet (`updateTurnMarks`).
5. **The lane arrow sits on the wrong fleet's tile.** Progress 50 has colour (t+2) mod 4, so the mint arrow sits on
   apricot (`toparm4x`). **Fix:** draw it on `Board.laneCell(team, 0)` at y = `LANE_TILE_HEIGHT + CIRCLE_HEIGHT +
   0.0012f`.
6. **The bank is likely inverted.** A left turn gives `turn > 0`, and `bank = -turn*0.4` then lowers the right wing,
   since `Quat` is right-handed (see `TILT_RAD`). **Fix:** `bank = turn*0.4f`, then check one recorded corner.
7. **The empty die shows a 3×3 grid.** **Fix:** in `DiceFace`, set `val v = value ?: 6` and draw the pips `faint`
   when `value == null`.
8. **The lower third of the glass is empty.** The strip sits about 187 dp down a glass about 300 dp tall, so
   `TurnPanel`'s `weight(1f)` is not getting the full height. Log the root size with `onSizeChanged`. Don't shrink
   the window, because `PlanePick` needs the height.
9. **The window's right edge is off-frame** (r2b too). The volume is 0.9 m wide for a 0.744 m mat. **Fix:** set the
   manifest `defaultsize` to `0.78x0.5x1.0`, which pulls the window about 6 cm in. Check that `evidence()` x stays
   within ±0.39.

### P3

- In zh, `seat_label` should be `%1$s机队` with no space, as upstream writes it.
- The tower cab is ivory on ivory and reads as a peg. Use the `frame` sage for it.

## 3 · Verdict

The still is good-looking. It now reads as a real toy set: chunky tiles with a circle on every cell, rimmed hangars,
distinct fleet planes, a tower at the hub, and a dark tray that invites a pinch. It is clearly better than round 1.
It is **not yet ready to show as "good-looking and fun"**, for two reasons:

- Both P1s are visible in the opening frame: the player's own fleet sinks into its pads, and the 4th fleet is cut off
  the strip.
- The fun part is unverified. No capture shows the hops, roll or spins, and the bank may be inverted.

Fix the two P1s plus #3, #6 and #7 (each a few lines). Then record one 30-second clip (roll a 6, launch, corner,
fly-over, capture) and a full-window 4-seat capture. If those hold, show it.
