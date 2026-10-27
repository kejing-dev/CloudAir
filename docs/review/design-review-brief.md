# Design review brief · Cloud Air round 1

You are reviewing the first playable build of 云间航线 Cloud Air, a spatial aeroplane-chess (飞行棋) app for
PICO OS 6 Shared Space, running on the PICO emulator. Judge it as a product designer who wants it to be
**good-looking and fun** on a headset: a toy-like miniature airport on a tilted board, a real 3D die you pinch
to throw, low-poly planes that hop, jump and fly across the board, and a small glass control window beside it.

## Inputs

- `design/design-doc.md` — the binding design contract (board topology, colours, motion, surfaces).
- `design/reference-mat.webp` — the classic aeroplane-chess mat the board must evoke.
- `design/upstream/*.png` — the upstream Figma concept (Codex V3/V4): the mood (fresh, calm, pastel), the
  Blender plane and die, the control window idea. We deliberately changed the board to the classic cross layout.
- Screenshots in `.scratch/shots/` (2160 x 2158 emulator captures; the board is small in frame, crop and zoom
  with PIL): `r2b.png` (new game, mint's turn), `tilt2.png` (previous build), `game3.png` (mid-roll, older build).
- Source of the visuals: `app/src/main/java/io/embodit/cloudair/ui/board/scene/*.kt` (BoardLayout = sizes in
  metres, SceneResources = materials/meshes, BoardScene = entities + animation timing) and
  `ui/control/components/*.kt` (the window), `ui/theme/CloudAirColors.kt` (tokens).

## What to produce

Write `docs/review/design-review-1.md` with:

1. A short verdict (what already works, what undermines "good-looking and fun").
2. A prioritised punch list, P1 (must fix before showing the user) / P2 (should) / P3 (nice), each item with
   a concrete, implementable change: numbers (metres, dp, hex), which file/constant, and why. Cover at least:
   board legibility from a seated viewer (tile size, contrast, gaps, the cross/arm reading, hangars, lanes,
   pinwheel centre, launch arrows, fly-over dashes), plane size relative to cells, the die and tray (size,
   placement, visibility of pips), the tilt angle and board size inside the 0.9 x 0.5 x 1.0 m volume, the control
   window (hierarchy, copy, button sizes, what is missing), and colour (the four fleet pastels vs the ivory mat).
3. Motion suggestions (durations, arc heights, easing) you would change from the current values in BoardScene.
4. Anything in the classic mat that is missing and would add charm (keep the scope realistic: we build with
   box/cylinder/torus meshes and the two GLB models; no textures or text on the board).

Do not change any code. Do not run Gradle or touch the emulator. Keep the review under ~1500 words; be
specific and decisive, not exhaustive. Cite the screenshot crops you looked at.
