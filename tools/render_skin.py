"""Renders before / after close-ups of the dice tray, the mat edge and the control tower (skin v2).

Run with Blender 5.x:
  blender --background --factory-startup --python tools/render_skin.py -- <out_dir> [models_dir]

"before" rebuilds the v1 primitives from SceneResources (sage frame slab, ivory mat slab, stone walls on a
felt floor, cylinder tower); "after" imports board.glb + tray.glb. Both get the same die.glb, the same
tilt (BoardScene.TILT_RAD) and the same cameras, so the pictures differ only in the skin.
"""
import math
import os
import sys

import bpy
from mathutils import Vector

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ARGS = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = os.path.abspath(ARGS[0]) if ARGS else os.path.join(ROOT, "design", "skin-v2")
MODELS = ARGS[1] if len(ARGS) > 1 else os.path.join(ROOT, "app", "src", "main", "assets", "models")

MAT = 0.72
CELL = MAT / 15
K = 0.042 / 0.6
TRAY_Z = MAT / 2 + 0.012 + 0.004 + 1.8 * K / 2 + 0.08 * K   # BoardLayout.TRAY_CENTER.z (toward the player)
TILT = 0.489


def srgb(hex_):
    c = [int(hex_[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c) + (1.0,)


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = 1400
    scene.render.resolution_y = 900
    scene.render.film_transparent = False
    try:
        scene.view_settings.view_transform = "AgX"
    except TypeError:
        pass
    world = bpy.data.worlds.new("World")
    scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.80, 0.82, 0.81, 1)
    bg.inputs[1].default_value = 0.55
    return scene


def pbr(name, hex_, rough, glow):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = srgb(hex_)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Emission Color"].default_value = srgb(hex_)
    b.inputs["Emission Strength"].default_value = glow
    return m


def box(name, size, at, mat, bevel, parent):
    """size / at in board space (x right, y up, z toward the player)."""
    bpy.ops.mesh.primitive_cube_add(size=1)
    o = bpy.context.object
    o.name = name
    o.scale = (size[0], size[2], size[1])
    bpy.ops.object.transform_apply(scale=True)
    if bevel:
        mod = o.modifiers.new("b", "BEVEL")
        mod.width = bevel
        mod.segments = 4
    o.location = (at[0], -at[2], at[1])
    o.data.materials.append(mat)
    o.parent = parent
    return o


def cylinder(name, r, h, at, mat, parent):
    bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=h, vertices=48)
    o = bpy.context.object
    o.name = name
    o.location = (at[0], -at[2], at[1])
    o.data.materials.append(mat)
    o.parent = parent
    return o


def import_glb(path, parent, at=(0, 0, 0), scale=1.0):
    before = set(bpy.context.scene.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    new = [o for o in bpy.context.scene.objects if o not in before]
    holder = bpy.data.objects.new(os.path.basename(path), None)
    bpy.context.collection.objects.link(holder)
    for o in new:
        if o.parent is None:
            o.parent = holder
    holder.location = (at[0], -at[2], at[1])
    holder.scale = (scale, scale, scale)
    holder.parent = parent
    return holder


def build(variant):
    scene = reset()
    root = bpy.data.objects.new("boardRoot", None)
    bpy.context.collection.objects.link(root)
    root.rotation_euler = (TILT, 0, 0)   # tipped toward the player (-Y), like BoardScene.setRegion
    # Context shared by both variants: one row of loop tiles with circles near the front edge, and the hangar corner.
    mids = ["7FB4A4", "7AA7CB", "C9A07E", "A898C3"]
    tile_mats = [pbr(f"tile{i}", c, 0.7, 0.14) for i, c in enumerate(mids)]
    ivory = pbr("ivory", "F4F4E9", 0.7, 0.18)
    for col in range(6, 9):
        for row in range(9, 15):
            x, z = (col - 7) * CELL, (row - 7) * CELL
            box(f"t{row}{col}", (CELL * 0.82, 0.0055, CELL * 0.82), (x, 0.00275, z), tile_mats[(row + col) % 4], 0.006, root)
            cylinder(f"c{row}{col}", CELL * 0.2, 0.0008, (x, 0.0059, z), ivory, root)
    if variant == "before":
        frame = pbr("frame", "A9C2B7", 0.9, 0.12)
        mat = pbr("mat", "F4F4E9", 0.9, 0.10)
        stone = pbr("tray", "DCE6DF", 0.9, 0.16)
        felt = pbr("felt", "3F5E58", 1.0, 0.05)
        box("frame", (MAT + 0.024, 0.0108, MAT + 0.024), (0, -0.0234, 0), frame, 0.004, root)
        box("mat", (MAT, 0.018, MAT), (0, -0.009, 0), mat, 0.004, root)
        w, d, t, h = 2.8 * K, 1.8 * K, 0.08 * K, 0.5 * K
        box("floor", (w + 2 * t, 0.006, d + 2 * t), (0, -0.003, TRAY_Z), felt, 0.002, root)
        for sx in (-1, 1):
            box("wallX", (t, h, d + 2 * t), (sx * (w / 2 + t / 2), h / 2, TRAY_Z), stone, 0.0012, root)
        for sz in (-1, 1):
            box("wallZ", (w, h, t), (0, h / 2, TRAY_Z + sz * (d / 2 + t / 2)), stone, 0.0012, root)
        cylinder("hub", CELL * 0.42, 0.008, (0, 0.004, 0), ivory, root)
        cylinder("tower", CELL * 0.11, 0.022, (0, 0.019, 0), ivory, root)
        cylinder("cab", CELL * 0.2, 0.008, (0, 0.034, 0), frame, root)
    else:
        import_glb(os.path.join(MODELS, "board.glb"), root)
        import_glb(os.path.join(MODELS, "tray.glb"), root, at=(0, 0, TRAY_Z))
    beacon = pbr("beacon", "277661", 0.7, 0.3)
    bpy.ops.mesh.primitive_torus_add(major_radius=(CELL * 0.2 * 0.9 + CELL * 0.2 * 0.55) / 2, minor_radius=CELL * 0.2 * 0.175)
    tor = bpy.context.object
    tor.location = (0, 0, 0.040)
    tor.data.materials.append(beacon)
    tor.parent = root
    die = import_glb(os.path.join(MODELS, "die.glb"), root, at=(0.02, 0.021, TRAY_Z + 0.01), scale=K)
    die.rotation_euler = (0, 0, math.radians(18))
    ring = bpy.ops.mesh.primitive_torus_add(major_radius=0.042 * 0.95 * 0.92, minor_radius=0.042 * 0.95 * 0.08)
    r = bpy.context.object
    r.location = (0.02, -(TRAY_Z + 0.01), 0.0009)
    r.data.materials.append(beacon)
    r.parent = root

    def light(name, loc, energy, size):
        data = bpy.data.lights.new(name, "AREA")
        data.energy = energy
        data.size = size
        o = bpy.data.objects.new(name, data)
        bpy.context.collection.objects.link(o)
        o.location = loc
        o.rotation_euler = (-Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    light("key", (0.6, -1.0, 1.4), 90, 1.2)
    light("fill", (-1.0, -0.6, 0.6), 30, 1.5)
    light("rim", (0.0, 1.2, 1.0), 25, 1.2)
    return scene, root


def shoot(scene, name, eye, target, lens):
    cam_data = bpy.data.cameras.new(name)
    cam_data.lens = lens
    cam_data.clip_start = 0.01
    cam = bpy.data.objects.new(name, cam_data)
    bpy.context.collection.objects.link(cam)
    cam.location = eye
    cam.rotation_euler = (Vector(target) - Vector(eye)).to_track_quat("-Z", "Y").to_euler()
    scene.camera = cam
    scene.render.filepath = os.path.join(OUT, name + ".png")
    bpy.ops.render.render(write_still=True)
    print("RENDERED", scene.render.filepath)


def world_of(root, board_point):
    bpy.context.view_layer.update()
    x, y, z = board_point
    return tuple(root.matrix_world @ Vector((x, -z, y)))


os.makedirs(OUT, exist_ok=True)
for variant in ("before", "after"):
    scene, root = build(variant)
    tray = world_of(root, (0, 0.01, TRAY_Z))
    shoot(scene, f"blender-{variant}-tray", (0.16, tray[1] - 0.36, tray[2] + 0.26), tray, 50)
    corner = world_of(root, (0.30, 0.0, 0.33))
    shoot(scene, f"blender-{variant}-edge", (corner[0] + 0.16, corner[1] - 0.22, corner[2] + 0.16), corner, 50)
    tower = world_of(root, (0, 0.02, 0))
    shoot(scene, f"blender-{variant}-tower", (tower[0] + 0.07, tower[1] - 0.16, tower[2] + 0.07), tower, 60)
