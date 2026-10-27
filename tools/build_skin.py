"""Builds the skin-v2 board pieces procedurally and exports them as GLB.

Run with Blender 5.x:
  blender --background --factory-startup --python tools/build_skin.py -- <models_dir>

board.glb  origin = the mat's top centre (board space). Linen mat 0.72 m, a sage binding cord on its edge,
           a bent-wood frame round it (FRAME_MARGIN 12 mm, lip 3 mm above the mat), and the hub with a
           turned porcelain control tower (the beacon torus stays a Kotlin primitive above the roof).
tray.glb   origin = the dice tray's felt top centre (BoardLayout.TRAY_CENTER). The inner box is exactly the
           dice solver's tray (2.8 x 1.8 x 0.5 solver units x DIE_SCALE); only the outside is new: a wooden
           shell with rounded corners and rim, a padded felt cushion with a stitched welt, and a band of the
           four fleet colours inlaid on the front.

Textures (wood, linen, felt) are generated here with numpy, tileable by construction (FFT-filtered noise),
so the build is reproducible. Blender is Z-up and the glTF exporter maps Blender (x, y, z) to glTF
(x, z, -y): the player side (+Z in board space) is -Y here.
"""
import math
import os
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Vector

OUT = sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else "."

# ---- the game's dimensions (BoardLayout / DiceSolver) ----
MAT = 0.72
CELL = MAT / 15
MAT_THICKNESS = 0.018
FRAME_MARGIN = 0.012
DIE_SCALE = 0.042 / 0.6
TRAY_HX = 2.8 / 2 * DIE_SCALE       # 0.098
TRAY_HY = 1.8 / 2 * DIE_SCALE       # 0.063
TRAY_WALL_H = 0.5 * DIE_SCALE       # 0.035
HUB_RADIUS = CELL * 0.42
HUB_HEIGHT = 0.008

# ---- palette (design-doc §4), sRGB ----
IVORY = "F4F4E9"
SAGE = "A9C2B7"
FELT = "3F5E58"
INK = "203C39"
PORCELAIN = "FBF4E2"
FLEET = [("A8DCCB", "277661"), ("A9D3F2", "386E9B"), ("F6C9A8", "93643B"), ("C9BDE8", "70608F")]


def rgb(hex_):
    return np.array([int(hex_[i:i + 2], 16) / 255 for i in (0, 2, 4)])


def lin(c):
    c = rgb(c) if isinstance(c, str) else np.asarray(c, dtype=float)
    return tuple(np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)) + (1.0,)


def mid(light, deep, k=0.4):
    """CloudAirColors.midOf: a straight mix in sRGB, as Compose Color does."""
    return rgb(light) + (rgb(deep) - rgb(light)) * k


# ---- textures ----

def fft_noise(h, w, fu, fv, seed):
    """Tileable noise: white noise low-passed in frequency space (fu, fv = cut-off in cycles per tile)."""
    rng = np.random.default_rng(seed)
    spec = np.fft.fft2(rng.standard_normal((h, w)))
    ky = np.fft.fftfreq(h)[:, None] * h
    kx = np.fft.fftfreq(w)[None, :] * w
    spec *= np.exp(-(kx / fu) ** 2 - (ky / fv) ** 2)
    n = np.real(np.fft.ifft2(spec))
    return n / (n.std() + 1e-9)


def image(name, srgb_rows):
    """srgb_rows: (h, w, 3) floats in 0..1, row 0 at the top (v = 1)."""
    h, w, _ = srgb_rows.shape
    img = bpy.data.images.new(name, w, h, alpha=False)
    px = np.ones((h, w, 4), dtype=np.float32)
    px[:, :, :3] = np.clip(srgb_rows[::-1], 0, 1)
    img.pixels.foreach_set(px.ravel())
    img.pack()
    return img


def wood_texture(size=512):
    """Honey oak, grain along u."""
    u = np.arange(size)[None, :] / size
    v = np.arange(size)[:, None] / size
    warp = fft_noise(size, size, 3, 4, 1)
    rings = 0.5 + 0.5 * np.sin(2 * np.pi * (v * 11 + u * 0 + 0.22 * warp))
    rings = rings ** 4
    fibre = fft_noise(size, size, 3, 90, 2)
    pores = fft_noise(size, size, 40, 200, 3)
    t = np.clip(0.42 * rings + 0.18 * fibre + 0.06 * pores + 0.35, 0, 1)
    light, dark = rgb("D2AD7E"), rgb("A57A50")
    col = light[None, None, :] * (1 - t[..., None]) + dark[None, None, :] * t[..., None]
    return col


def linen_texture(size=512, threads=48):
    u = np.arange(size)[None, :] / size
    v = np.arange(size)[:, None] / size
    a = np.floor(u * threads) + np.floor(v * threads)
    warp = np.abs(np.sin(np.pi * u * threads))
    weft = np.abs(np.sin(np.pi * v * threads))
    weave = np.where(a % 2 == 0, warp, weft)
    slub = fft_noise(size, size, 6, 60, 4) * 0.5 + fft_noise(size, size, 60, 6, 5) * 0.5
    k = 1 + 0.028 * (weave - 0.6) + 0.012 * slub
    return rgb(IVORY)[None, None, :] * k[..., None]


def felt_floor_texture(hx, hy, inset=0.0058, radius=0.004, px_per_m=5200):
    """The tray floor as one picture: felt fibres, a soft darkening toward the walls, a stitched welt."""
    w = int(2 * hx * px_per_m)
    h = int(2 * hy * px_per_m)
    fine = fft_noise(h, w, 300, 300, 6)
    cloud = fft_noise(h, w, 8, 8, 7)
    x = (np.arange(w)[None, :] + 0.5) / px_per_m - hx
    y = hy - (np.arange(h)[:, None] + 0.5) / px_per_m
    edge = np.minimum(hx - np.abs(x), hy - np.abs(y))
    shade = 1 - 0.10 * np.exp(-edge / 0.004)
    k = (1 + 0.045 * fine + 0.02 * cloud) * shade
    col = rgb(FELT)[None, None, :] * k[..., None]
    thread = rgb("B4CABF")
    # Stitches: short capsules every 4 mm along the inset rounded rectangle.
    path = rrect_path(hx - inset, hy - inset, radius, seg=6)
    pts = [Vector((p[0], p[1])) for p in path]
    lengths = [(pts[(i + 1) % len(pts)] - pts[i]).length for i in range(len(pts))]
    total = sum(lengths)
    pitch, dash, half_w = 0.0042, 0.0026, 0.00036
    s = 0.0
    while s < total - pitch * 0.5:
        a = point_at(pts, lengths, s)
        b = point_at(pts, lengths, s + dash)
        x0, x1 = min(a.x, b.x) - 0.001, max(a.x, b.x) + 0.001
        y0, y1 = min(a.y, b.y) - 0.001, max(a.y, b.y) + 0.001
        c0, c1 = max(0, int((x0 + hx) * px_per_m)), min(w, int((x1 + hx) * px_per_m) + 1)
        r0, r1 = max(0, int((hy - y1) * px_per_m)), min(h, int((hy - y0) * px_per_m) + 1)
        xs, ys = x[:, c0:c1], y[r0:r1, :]
        ab = b - a
        t = np.clip(((xs - a.x) * ab.x + (ys - a.y) * ab.y) / max(1e-12, ab.length_squared), 0, 1)
        d = np.hypot(xs - (a.x + t * ab.x), ys - (a.y + t * ab.y))
        cover = np.clip((half_w - d) * px_per_m + 0.5, 0, 1)[..., None]
        col[r0:r1, c0:c1] = col[r0:r1, c0:c1] * (1 - cover) + thread[None, None, :] * cover
        s += pitch
    return col


def point_at(pts, lengths, s):
    for i, l in enumerate(lengths):
        if s <= l:
            return pts[i].lerp(pts[(i + 1) % len(pts)], s / l if l else 0)
        s -= l
    return pts[0]


# ---- materials ----

def material(name, color=None, roughness=0.8, tex=None, emit=0.0, uv_scale=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nodes = m.node_tree.nodes
    bsdf = nodes["Principled BSDF"]
    bsdf.inputs["Roughness"].default_value = roughness
    bsdf.inputs["Metallic"].default_value = 0.0
    if tex is not None:
        node = nodes.new("ShaderNodeTexImage")
        node.image = tex
        m.node_tree.links.new(node.outputs["Color"], bsdf.inputs["Base Color"])
        if emit:
            m.node_tree.links.new(node.outputs["Color"], bsdf.inputs["Emission Color"])
    else:
        bsdf.inputs["Base Color"].default_value = lin(color)
        if emit:
            bsdf.inputs["Emission Color"].default_value = lin(color)
    if emit:
        # Like SceneResources.pbr's glow: a little light of its own keeps the piece readable in the volume.
        bsdf.inputs["Emission Strength"].default_value = emit
    return m


# ---- geometry ----

def rrect_path(hx, hy, r, seg=8):
    """A rounded rectangle, counter-clockwise: (x, y, nx, ny) with the outward normal."""
    out = []
    for cx, cy, a0 in ((hx - r, -hy + r, -90), (hx - r, hy - r, 0), (-hx + r, hy - r, 90), (-hx + r, -hy + r, 180)):
        for k in range(seg + 1):
            a = math.radians(a0 + 90 * k / seg)
            out.append((cx + r * math.cos(a), cy + r * math.sin(a), math.cos(a), math.sin(a)))
    return out


def new_object(name, bm, materials):
    mesh = bpy.data.meshes.new(name)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-7)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    for f in bm.faces:
        f.smooth = True
    bm.normal_update()
    for e in bm.edges:
        if len(e.link_faces) == 2 and e.calc_face_angle(0) > math.radians(38):
            e.smooth = False
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    for m in materials:
        mesh.materials.append(m)
    return obj


def sweep(bm, path, profile, closed_profile, tile, uv_layer, planar=None):
    """Sweeps a (d, z) cross-section along a closed path; d is measured along the path's outward normal.
    UVs run along the path (u) and across the profile (v), in metres / tile; or planar (x, y) -> uv."""
    n, m = len(path), len(profile)
    grid = [[bm.verts.new((px + d * nx, py + d * ny, z)) for (d, z) in profile] for (px, py, nx, ny) in path]
    s = [0.0]
    for i in range(n):
        a, b = path[i], path[(i + 1) % n]
        s.append(s[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    t = [0.0]
    for j in range(m):
        a, b = profile[j], profile[(j + 1) % m]
        t.append(t[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    for i in range(n):
        for j in range(m if closed_profile else m - 1):
            vs = (grid[i][j], grid[(i + 1) % n][j], grid[(i + 1) % n][(j + 1) % m], grid[i][(j + 1) % m])
            f = bm.faces.new(vs)
            uvs = ((s[i], t[j]), (s[i + 1], t[j]), (s[i + 1], t[j + 1]), (s[i], t[j + 1]))
            for loop, uv in zip(f.loops, uvs):
                if planar:
                    loop[uv_layer].uv = planar(loop.vert.co)
                else:
                    loop[uv_layer].uv = (uv[0] / tile, uv[1] / tile)
    return grid


def cap(bm, ring, uv_layer, planar):
    f = bm.faces.new(ring)
    for loop in f.loops:
        loop[uv_layer].uv = planar(loop.vert.co)
    return f


def rounded_slab(name, hx, hy, r, z0, z1, top_round, mat, planar, seg=8):
    """A slab with rounded corners and a rounded top edge; planar UVs on every face."""
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new("UVMap")
    path = rrect_path(hx, hy, r, seg)
    # From the inner edge of the top face, round over to the side, then down to the bottom.
    profile = [(-top_round, z1)] + [(-top_round + top_round * math.sin(math.radians(90 * k / 4)), z1 - top_round + top_round * math.cos(math.radians(90 * k / 4))) for k in range(1, 5)] + [(0, z0)]
    grid = sweep(bm, path, profile, False, 1, uv, planar)
    cap(bm, [row[0] for row in grid], uv, planar)
    cap(bm, [row[-1] for row in reversed(grid)], uv, planar)
    return new_object(name, bm, [mat])


def lathe(name, profile, mat, n=48):
    """Turns an (r, z) profile about Z. Points with r = 0 become poles."""
    bm = bmesh.new()
    rings = []
    for r, z in profile:
        if r < 1e-9:
            rings.append([bm.verts.new((0, 0, z))])
        else:
            rings.append([bm.verts.new((r * math.cos(2 * math.pi * k / n), r * math.sin(2 * math.pi * k / n), z)) for k in range(n)])
    for a, b in zip(rings, rings[1:]):
        for k in range(n):
            if len(a) == 1 and len(b) == 1:
                continue
            if len(a) == 1:
                bm.faces.new((a[0], b[k], b[(k + 1) % n]))
            elif len(b) == 1:
                bm.faces.new((a[k], a[(k + 1) % n], b[0]))
            else:
                bm.faces.new((a[k], a[(k + 1) % n], b[(k + 1) % n], b[k]))
    return new_object(name, bm, [mat])


def tube(name, path, radius, z, mat, sides=10):
    bm = bmesh.new()
    uv = bm.loops.layers.uv.new("UVMap")
    profile = [(radius * math.cos(2 * math.pi * k / sides), z + radius * math.sin(2 * math.pi * k / sides)) for k in range(sides)]
    sweep(bm, path, profile, True, 0.05, uv)
    return new_object(name, bm, [mat])


def export(path, objects):
    bpy.ops.object.select_all(action="DESELECT")
    for o in objects:
        o.select_set(True)
    tris = sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in objects)
    kwargs = dict(filepath=path, export_format="GLB", use_selection=True, export_apply=True, export_yup=True,
                  export_normals=True, export_materials="EXPORT", export_texcoords=True)
    try:
        bpy.ops.export_scene.gltf(export_image_format="JPEG", export_jpeg_quality=88, **kwargs)
    except TypeError:
        bpy.ops.export_scene.gltf(**kwargs)
    print(f"EXPORTED {path} objects={len(objects)} tris={tris} bytes={os.path.getsize(path)}")


# =====================================================================
bpy.ops.wm.read_factory_settings(use_empty=True)
os.makedirs(OUT, exist_ok=True)

wood_img = image("wood", wood_texture())
linen_img = image("linen", linen_texture())
felt_img = image("felt", felt_floor_texture(TRAY_HX, TRAY_HY))

wood = material("Skin wood", tex=wood_img, roughness=0.5, emit=0.08)
linen = material("Skin linen", tex=linen_img, roughness=0.95, emit=0.10)
felt = material("Skin felt", tex=felt_img, roughness=1.0, emit=0.04)
cord = material("Skin binding", SAGE, roughness=0.85, emit=0.12)
porcelain = material("Skin porcelain", PORCELAIN, roughness=0.32, emit=0.14)
glass = material("Skin cab glass", INK, roughness=0.18, emit=0.05)

# ---- board ----
half = MAT / 2
mat_obj = rounded_slab("Mat", half, half, 0.012, -MAT_THICKNESS, 0.0, 0.0012, linen, lambda co: (co.x / 0.09, co.y / 0.09))
cord_obj = tube("Binding", rrect_path(half - 0.0004, half - 0.0004, 0.0116, 8), 0.0016, 0.0004, cord)

# Frame: one bent-wood rail swept round the mat, lip 3 mm proud of the linen, a rounded top and a skirt below.
bm = bmesh.new()
uv = bm.loops.layers.uv.new("UVMap")
frame_profile = [(-0.004, -MAT_THICKNESS), (0.0, -MAT_THICKNESS), (0.0, 0.0012), (0.0004, 0.0024), (0.0014, 0.003),
                 (0.0104, 0.003), (0.0114, 0.0026), (0.0119, 0.0018), (FRAME_MARGIN, 0.0008), (FRAME_MARGIN, -0.0285),
                 (0.0115, -0.0297), (0.0103, -0.030), (-0.004, -0.030)]
sweep(bm, rrect_path(half, half, 0.012, 10), frame_profile, True, 0.16, uv)
frame_obj = new_object("Frame", bm, [wood])

# Hub with a sage band, and the turned control tower (roof top below the beacon at y = 0.040).
r_hub = HUB_RADIUS
hub_obj = lathe("Hub", [(0, 0), (r_hub, 0), (r_hub, 0.0062), (r_hub - 0.0004, 0.0072), (r_hub - 0.0012, 0.0078),
                        (r_hub - 0.0024, HUB_HEIGHT), (0, HUB_HEIGHT)], porcelain)
band_obj = lathe("HubBand", [(r_hub - 0.0001, 0.0024), (r_hub + 0.0004, 0.0027), (r_hub + 0.0004, 0.0043), (r_hub - 0.0001, 0.0046)], cord)
tower_obj = lathe("Tower", [(0, HUB_HEIGHT), (0.0078, HUB_HEIGHT), (0.0072, 0.0092), (0.0058, 0.0108), (0.0050, 0.013),
                            (0.0047, 0.026), (0.0060, 0.0275), (0.0086, 0.0288), (0.0086, 0.0296), (0, 0.0296)], porcelain, n=40)
cab_obj = lathe("Cab", [(0, 0.0296), (0.0082, 0.0296), (0.0094, 0.0336), (0.0094, 0.0342), (0, 0.0342)], glass, n=40)
roof_obj = lathe("Roof", [(0, 0.0342), (0.0100, 0.0342), (0.0100, 0.0350), (0.0080, 0.0361), (0.0042, 0.0375), (0, 0.0378)], cord, n=40)
export(os.path.join(OUT, "board.glb"), [mat_obj, cord_obj, frame_obj, hub_obj, band_obj, tower_obj, cab_obj, roof_obj])

for o in list(bpy.context.scene.objects):
    bpy.data.objects.remove(o)

# ---- tray ----
WALL = 0.0065
bm = bmesh.new()
uv = bm.loops.layers.uv.new("UVMap")
tray_profile = [(0.0, -0.010), (0.0, TRAY_WALL_H - 0.0015), (0.0005, TRAY_WALL_H - 0.0005), (0.0015, TRAY_WALL_H),
                (WALL - 0.0015, TRAY_WALL_H), (WALL - 0.0005, TRAY_WALL_H - 0.0005), (WALL, TRAY_WALL_H - 0.0015),
                (WALL, -0.0085), (WALL - 0.0005, -0.0097), (WALL - 0.0017, -0.010)]
sweep(bm, rrect_path(TRAY_HX, TRAY_HY, 0.003, 6), tray_profile, True, 0.16, uv)
shell_obj = new_object("TrayShell", bm, [wood])

# Felt cushion: fills the basin, its top exactly on the solver's floor (y = 0), its edge rounded like a pad.
felt_uv = lambda co: ((co.x + TRAY_HX) / (2 * TRAY_HX), (co.y + TRAY_HY) / (2 * TRAY_HY))
cushion_obj = rounded_slab("TrayFelt", TRAY_HX, TRAY_HY, 0.003, -0.0099, 0.0, 0.0016, felt, felt_uv, seg=6)

# Fleet band on the front face (the player side is -Y here): the four pastel fleet colours as enamel pills
# on an ink backing strip, so every colour reads against the oak (apricot alone would melt into it).
inlays = []
seg_w, gap = 0.04, 0.003
band_w = 4 * seg_w + 3 * gap
front = -(TRAY_HY + WALL)
ink = material("Skin inlay ink", INK, roughness=0.5, emit=0.05)


def pill(name, hx, hz, depth, at_x, mat_):
    # A thin rounded slab built lying in XY, then stood up on the front face.
    obj = rounded_slab(name, hx, hz, min(hx, hz) * 0.95, 0.0, depth, depth * 0.5, mat_, lambda co: (0, 0), seg=4)
    obj.rotation_euler = (math.radians(90), 0, 0)
    obj.location = (at_x, front + 0.0001, 0.0165)
    return obj


inlays.append(pill("InlayBand", band_w / 2 + 0.0022, 0.0034, 0.0006, 0.0, ink))
for i, (light, _) in enumerate(FLEET):
    m = material(f"Skin inlay {i}", light, roughness=0.5, emit=0.14)
    inlays.append(pill(f"Inlay{i}", seg_w / 2, 0.0022, 0.0011, -band_w / 2 + seg_w / 2 + i * (seg_w + gap), m))
bpy.context.view_layer.update()
export(os.path.join(OUT, "tray.glb"), [shell_obj, cushion_obj] + inlays)
