"""Builds the game die procedurally and exports it as GLB.

Run with Blender 5.x:
  blender --background --factory-startup --python tools/build_die.py -- <out.glb>

A 0.6-unit cube (the dice solver's collision size) with rounded edges and 21 recessed spherical pips.
Faces in glTF axes match DiceSolver.FACE_NORMALS: 1 on -Z, 6 on +Z, 2 on +X, 5 on -X, 3 on +Y, 4 on -Y.
Blender is Z-up and the glTF exporter maps Blender (x, y, z) to glTF (x, z, -y), so in Blender:
1 on +Y, 6 on -Y, 2 on +X, 5 on -X, 3 on +Z, 4 on -Z.
"""
import math
import sys

import bmesh
import bpy
from mathutils import Vector

OUT = sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else "die.glb"

SIDE = 0.6
HALF = SIDE / 2
BEVEL = 0.075          # rounded edge radius
PIP_R = 0.07           # radius of the cutting sphere
PIP_DEPTH = 0.028      # how deep a pip is pressed into the face (opening is about 0.11 across)
PIP_STEP = 0.14        # distance of corner pips from the face centre
ACE_R = 0.095          # the single pip of the 1 is bigger, as on real dice
ACE_DEPTH = 0.035


def srgb(hex_):
    c = [int(hex_[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c) + (1.0,)


def material(name, color, roughness, coat=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = srgb(color)
    bsdf.inputs["Roughness"].default_value = roughness
    bsdf.inputs["Metallic"].default_value = 0.0
    if coat and "Coat Weight" in bsdf.inputs:
        bsdf.inputs["Coat Weight"].default_value = coat
        bsdf.inputs["Coat Roughness"].default_value = 0.08
    return m


# Pip positions on a face, in the face's own (u, v) plane.
d = PIP_STEP
LAYOUT = {
    1: [(0, 0)],
    2: [(-d, -d), (d, d)],
    3: [(-d, -d), (0, 0), (d, d)],
    4: [(-d, -d), (-d, d), (d, -d), (d, d)],
    5: [(-d, -d), (-d, d), (0, 0), (d, -d), (d, d)],
    6: [(-d, -d), (-d, 0), (-d, d), (d, -d), (d, 0), (d, d)],
}
# Face value -> (outward normal, u axis, v axis) in Blender axes.
FACES = {
    1: (Vector((0, 1, 0)), Vector((1, 0, 0)), Vector((0, 0, 1))),
    6: (Vector((0, -1, 0)), Vector((1, 0, 0)), Vector((0, 0, 1))),
    2: (Vector((1, 0, 0)), Vector((0, 1, 0)), Vector((0, 0, 1))),
    5: (Vector((-1, 0, 0)), Vector((0, 1, 0)), Vector((0, 0, 1))),
    3: (Vector((0, 0, 1)), Vector((1, 0, 0)), Vector((0, 1, 0))),
    4: (Vector((0, 0, -1)), Vector((1, 0, 0)), Vector((0, 1, 0))),
}

bpy.ops.wm.read_factory_settings(use_empty=True)
porcelain = material("Die body porcelain", "FBF4E2", 0.28, coat=0.5)
enamel = material("Die pips enamel", "1C4A3D", 0.3)

# Body: a cube with rounded edges.
mesh = bpy.data.meshes.new("Die body")
bm = bmesh.new()
bmesh.ops.create_cube(bm, size=SIDE)
bmesh.ops.bevel(bm, geom=bm.verts[:] + bm.edges[:], offset=BEVEL, segments=6, profile=0.5, affect="EDGES")
bm.to_mesh(mesh)
bm.free()
body = bpy.data.objects.new("Die", mesh)
bpy.context.collection.objects.link(body)
body.data.materials.append(porcelain)
body.data.materials.append(enamel)

# Pips: subtract one sphere per pip; its faces take the enamel material.
def cutter(name, radius, segments):
    m = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=segments, v_segments=segments // 2, radius=radius)
    bm.to_mesh(m)
    bm.free()
    m.materials.append(enamel)
    for poly in m.polygons:
        poly.use_smooth = True
    return m


pip_mesh = cutter("pip", PIP_R, 20)
ace_mesh = cutter("ace", ACE_R, 28)
cutters = bpy.data.collections.new("cutters")
bpy.context.scene.collection.children.link(cutters)
for value, (n, u, v) in FACES.items():
    ace = value == 1
    mesh_, offset = (ace_mesh, HALF + ACE_R - ACE_DEPTH) if ace else (pip_mesh, HALF + PIP_R - PIP_DEPTH)
    for (a, b) in LAYOUT[value]:
        o = bpy.data.objects.new(f"pip{value}", mesh_)
        o.location = n * offset + u * a + v * b
        cutters.objects.link(o)

mod = body.modifiers.new("pips", "BOOLEAN")
mod.operation = "DIFFERENCE"
mod.operand_type = "COLLECTION"
mod.collection = cutters
mod.solver = "EXACT"
mod.material_mode = "TRANSFER"
bpy.context.view_layer.objects.active = body
body.select_set(True)
bpy.ops.object.modifier_apply(modifier="pips")
bpy.data.collections.remove(cutters)

# Smooth the bevels and dimples, keep the flat faces crisp where they meet the dimples.
for poly in body.data.polygons:
    poly.use_smooth = True
try:
    bpy.ops.object.shade_auto_smooth(angle=math.radians(35))
except Exception as e:  # older API
    print("auto smooth unavailable:", e)

bpy.ops.object.select_all(action="DESELECT")
body.select_set(True)
tris = sum(len(p.vertices) - 2 for p in body.data.polygons)
dims = body.dimensions
print(f"DIE tris={tris} dims=({dims.x:.4f}, {dims.y:.4f}, {dims.z:.4f})")

bpy.ops.export_scene.gltf(
    filepath=OUT,
    export_format="GLB",
    use_selection=True,
    export_apply=True,
    export_yup=True,
    export_normals=True,
    export_materials="EXPORT",
)
print("EXPORTED", OUT)
