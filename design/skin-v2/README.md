# Skin v2 (v1.0.1, 2026-10-26)

User feedback: the dice sound was harsh, the dice tray looked cheap, and the board and tray needed one art
direction. This round changes art and sound only. Rules, flow, dice physics, saves and UI logic are untouched.

## Art direction: a heirloom table game

The board should read as a finished product made of real materials. The V4 palette and the classic mat
layout stay as they are.

| Part | v1 | Skin v2 |
| --- | --- | --- |
| Mat | flat ivory slab | ivory **linen** (woven texture, roughness 0.95) with a **sage binding cord** on the edge (the V4 sage `#A9C2B7`) |
| Frame | sage plate under the mat | **bent-wood oak rail**: rounded top, 3 mm lip above the linen, grain follows the rail round the corners |
| Tower | three plain cylinders | **turned porcelain** tower on the hub, with an ink-glass cab, a sage roof and a sage band on the hub. The beacon torus is unchanged |
| Dice tray | flat stone walls on a felt slab | **oak shell** with rounded corners and rim. Inside is a **padded felt cushion** (V4 felt `#3F5E58`) with a sage stitched welt, and the front carries a **fleet band** (four pastel enamel pills on an ink strip) |
| Tiles / rims | roughness 0.7 | satin lacquer, roughness 0.5 |
| Circles / spots | roughness 0.7 | glazed enamel, roughness 0.45 |
| Hangar pads | roughness 0.75 | matte, felt-like, roughness 0.92 |
| Die | v6 porcelain + enamel pips | unchanged: it already matches the porcelain tower and the ink accents |

The tray's inner box is exactly the dice solver's (2.8 × 1.8 × 0.5 solver units × `DIE_SCALE`). The walls are
6.5 mm thick and stay inside the old footprint, give or take 1 mm. The felt top sits at the solver's floor
(y = 0). The tap collider (`trayShape`) and its entity are unchanged.

## Sources (all reproducible)

- `tools/build_skin.py` (Blender 5.x) writes `app/src/main/assets/models/board.glb` (4.3k tris, 157 KB) and
  `tray.glb` (2.1k tris, 149 KB). It generates the wood, linen and felt textures with numpy. The textures are
  tileable FFT noise, and the tray's stitching is drawn into the felt.
- `tools/render_skin.py` renders the v1 primitives and the v2 GLBs from the same cameras, which produces the
  comparison sheets.
- `BoardView` loads both GLBs. If either fails to load, `BoardScene` falls back to the v1 primitives.
- `tools/make_sounds.py` rebuilds every sound effect. `--check` prints the peak, the loudness, the spectral
  centroid and the share of energy above 3 kHz.

## Sound

| Sound | v1 | v2 |
| --- | --- | --- |
| dice_land | white-noise clack, peak −4.2 dBFS, sample step 0.57 (a click) | felt-damped resin knock (modes around 640 Hz and 1.3 kHz, low-passed at 2.6 kHz) plus four smaller tumbles and a felt rustle; peak −12 dBFS |
| dice_throw | three white-noise bursts, 42 % of the energy above 3 kHz | soft swish plus three muted rattles, 0.3 % above 3 kHz; peak −14 dBFS |
| others | peaks −6 … −13 dBFS, hard sine edges | soft mallet / bell notes and filtered air, every tail tapered, peaks −11 … −16 dBFS |

Headset speakers barely reproduce anything below 300 Hz, so the body of each knock sits between 600 Hz and
1.3 kHz instead of in a deep thud. See `sounds-dice-spectrum.png`.

## Comparisons

- `compare-1-board-emulator.jpg`: emulator-5554, the same game state before and after
- `compare-2-tray.jpg`: Blender close-up of the dice tray
- `compare-3-frame-tower.jpg`: Blender close-up of the mat edge and the control tower
- `sounds-dice-spectrum.png`: spectrograms and spectra of the dice sounds
- `emu-before-full.jpg` / `emu-after-full.jpg`: the full emulator frames
