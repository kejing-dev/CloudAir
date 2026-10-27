"""Renders orthographic reference views of the plane and die GLBs for image-to-3D tools (Tripo multi-view).

Run with Blender 5.x:
  blender --background --factory-startup --python tools/render_views.py -- <out_dir>

Each model is imported, turned so it stands upright (+Z up) facing +Y, framed to fill ~80 % of the
frame, and rendered with transparent film from front, back, left, right, top and a 3/4 hero angle.
"""
import math
import os
import sys

import bpy
from mathutils import Vector

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else os.path.join(ROOT, "design", "model-views")
RES = 1024
MODELS = {
    # name: (glb, rotation in degrees XYZ applied after import, airframe tint or None)
    "plane-mint": ("app/src/main/assets/models/plane.glb", (90, 0, 0), (0x5B / 255, 0x9F / 255, 0x8B / 255)),
    "plane-white": ("app/src/main/assets/models/plane.glb", (90, 0, 0), (0xEE / 255, 0xEF / 255, 0xE8 / 255)),
    "die": ("app/src/main/assets/models/die.glb", (0, 0, 0), None),
}
# Camera direction (where the camera sits, as a unit vector from the object) per view.
VIEWS = {
    "front": Vector((0, 1, 0)),
    "back": Vector((0, -1, 0)),
    "left": Vector((-1, 0, 0)),
    "right": Vector((1, 0, 0)),
    "top": Vector((0, 0, 1)),
    "bottom": Vector((0, 0, -1)),
    "hero": Vector((0.62, 0.62, 0.48)).normalized(),
}


def srgb_to_linear(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    for engine in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT"):
        try:
            scene.render.engine = engine
            break
        except TypeError:
            continue
    scene.render.resolution_x = RES
    scene.render.resolution_y = RES
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    try:
        scene.view_settings.view_transform = "AgX"
    except TypeError:
        scene.view_settings.view_transform = "Filmic"
    world = bpy.data.worlds.new("World")
    scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    bg.inputs[0].default_value = (0.92, 0.94, 0.93, 1)
    bg.inputs[1].default_value = 0.6
    return scene


def add_lights(radius):
    def area(name, loc, energy, size):
        data = bpy.data.lights.new(name, "AREA")
        data.energy = energy
        data.size = size
        obj = bpy.data.objects.new(name, data)
        bpy.context.collection.objects.link(obj)
        obj.location = loc
        direction = -Vector(loc)
        obj.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()
    d = radius * 4
    area("key", (d, d * 0.8, d * 1.2), 560 * radius * radius, radius * 3)
    area("fill", (-d, d * 0.6, d * 0.4), 200 * radius * radius, radius * 4)
    area("rim", (0, -d, d), 280 * radius * radius, radius * 3)
    area("under", (0, 0, -d), 70 * radius * radius, radius * 4)


def load(path, rot, tint):
    bpy.ops.import_scene.gltf(filepath=os.path.join(ROOT, path))
    meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    roots = [o for o in bpy.context.scene.objects if o.parent is None]
    pivot = bpy.data.objects.new("pivot", None)
    bpy.context.collection.objects.link(pivot)
    for o in roots:
        o.parent = pivot
    pivot.rotation_euler = [math.radians(a) for a in rot]
    bpy.context.view_layer.update()
    pts = [o.matrix_world @ Vector(c) for o in meshes for c in o.bound_box]
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    centre = (lo + hi) / 2
    pivot.location -= centre
    bpy.context.view_layer.update()
    if tint:
        lin = tuple(srgb_to_linear(c) for c in tint) + (1,)
        for o in meshes:
            for slot in o.material_slots:
                m = slot.material
                if m and m.name.lower().startswith("airframe") and m.use_nodes:
                    bsdf = next((n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
                    if bsdf:
                        bsdf.inputs["Base Color"].default_value = lin
    return (hi - lo).length / 2


def render(name, radius, scene):
    cam_data = bpy.data.cameras.new("cam")
    cam_data.type = "ORTHO"
    cam = bpy.data.objects.new("cam", cam_data)
    bpy.context.collection.objects.link(cam)
    scene.camera = cam
    os.makedirs(OUT, exist_ok=True)
    for view, direction in VIEWS.items():
        cam.location = direction * radius * 6
        up = "Y" if view != "top" else "Y"
        cam.rotation_euler = (-direction).to_track_quat("-Z", up).to_euler()
        cam_data.ortho_scale = radius * 2 / 0.82
        cam_data.clip_end = radius * 20
        scene.render.filepath = os.path.join(OUT, f"{name}-{view}.png")
        bpy.ops.render.render(write_still=True)
        print("RENDERED", scene.render.filepath)


# Extra arguments after the output folder render other models instead: name=path/to/model.glb
extra = sys.argv[sys.argv.index("--") + 2:] if "--" in sys.argv else []
if extra:
    MODELS = {a.split("=", 1)[0]: (a.split("=", 1)[1], (0, 0, 0), None) for a in extra}

for name, (path, rot, tint) in MODELS.items():
    scene = reset()
    radius = load(path, rot, tint)
    add_lights(radius)
    render(name, radius, scene)
