"""Builds Cloud Air's layered spatial app icon (PICO OS 6) with Pillow.

  python tools/build_icon.py [--preview out.png]

Writes into app/src/main/res:
  drawable/icon_3d_layer_0.png  background: sky gradient and the dashed flight route
  drawable/icon_3d_layer_1.png  middle: two clouds
  drawable/icon_3d_layer_2.png  top: the mint toy plane
  drawable/icon_3d_sdf_N.png    each layer's SDF (inward edge distance x 8, grey in RGB and alpha)
  mipmap-xxxhdpi/ic_spatial_launcher.png   flattened round icon
  drawable/ic_launcher_foreground.png      flattened square for the adaptive icon
Layers are 1024 px, kept inside the inscribed circle, foreground edges opaque and sharp, no baked shadows
(the system adds highlights and shadows itself).
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
S = 1024
SS = 4  # supersampling

SKY_TOP = (150, 202, 236)
SKY_BOTTOM = (232, 243, 236)
ROUTE = (255, 255, 255)
CLOUD = (255, 255, 255)
AIRFRAME = (63, 145, 122)
WING = (98, 176, 151)
CANOPY = (169, 211, 242)
NOSE = (255, 241, 199)


def mask(size, draw_fn, round_px=0):
    """Draws white shapes at SS x and returns an anti-aliased L mask of `size`; round_px rounds convex corners."""
    big = Image.new("L", (size * SS, size * SS), 0)
    draw_fn(ImageDraw.Draw(big), SS)
    if round_px:
        big = big.filter(ImageFilter.GaussianBlur(round_px * SS)).point(lambda v: 255 if v >= 128 else 0)
    return big.resize((size, size), Image.LANCZOS)


def solid(size, color, m):
    layer = Image.new("RGBA", (size, size), color + (0,))
    layer.putalpha(m)
    return layer


def circle_mask(size):
    return mask(size, lambda d, k: d.ellipse((0, 0, size * k - 1, size * k - 1), fill=255))


def sky(size):
    grad = Image.new("RGB", (1, size))
    for y in range(size):
        t = y / (size - 1)
        grad.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(SKY_TOP, SKY_BOTTOM)))
    return grad.resize((size, size)).convert("RGBA")


def route_points(size, scale):
    # A dashed trail curving out of the plane's tail toward the lower left.
    c = size / 2
    p0 = (c - 300 * scale, c + 330 * scale)
    p1 = (c - 175 * scale, c + 300 * scale)
    p2 = (c - 132 * scale, c + 212 * scale)
    pts = []
    for i in range(200):
        t = i / 199
        pts.append(tuple((1 - t) ** 2 * a + 2 * (1 - t) * t * b + t * t * e for a, b, e in zip(p0, p1, p2)))
    return pts


def route_mask(size, scale):
    pts = route_points(size, scale)
    # Cumulative length, then dashes of 34 px with 24 px gaps.
    acc, lens = 0.0, [0.0]
    for a, b in zip(pts, pts[1:]):
        acc += math.dist(a, b)
        lens.append(acc)
    dash, gap, w = 34 * scale, 24 * scale, 16 * scale

    def draw(d, k):
        for i in range(len(pts) - 1):
            if lens[i] % (dash + gap) < dash:
                (x0, y0), (x1, y1) = pts[i], pts[i + 1]
                d.line((x0 * k, y0 * k, x1 * k, y1 * k), fill=255, width=round(w * k))
                r = w * k / 2
                d.ellipse((x0 * k - r, y0 * k - r, x0 * k + r, y0 * k + r), fill=255)
    return mask(size, draw)


def background(size, scale, round_canvas):
    img = sky(size)
    img.alpha_composite(solid(size, ROUTE, route_mask(size, scale).point(lambda v: v * 0.85)))
    if round_canvas:
        img.putalpha(circle_mask(size))
    return img


def cloud_mask(size, scale):
    c = size / 2

    def blob(d, k, cx, cy, lobes, w, h):
        for (dx, dy, r) in lobes:
            x, y = (c + (cx + dx) * scale) * k, (c + (cy + dy) * scale) * k
            d.ellipse((x - r * scale * k, y - r * scale * k, x + r * scale * k, y + r * scale * k), fill=255)
        x0, x1 = (c + (cx - w / 2) * scale) * k, (c + (cx + w / 2) * scale) * k
        y0, y1 = (c + (cy - 10) * scale) * k, (c + (cy + h) * scale) * k
        d.rounded_rectangle((x0, y0, x1, y1), radius=h * scale * k / 2, fill=255)

    def draw(d, k):
        # Large cloud, lower right, the plane climbs out of it.
        blob(d, k, 120, 215, [(-95, 0, 62), (-20, -42, 86), (75, -12, 70), (135, 22, 46)], 330, 70)
        # Small cloud, upper left.
        blob(d, k, -215, -170, [(-50, 0, 42), (5, -26, 58), (62, 0, 40)], 180, 48)
    return mask(size, draw)


def clouds(size, scale):
    return solid(size, CLOUD, cloud_mask(size, scale))


def plane(size, scale, angle=-38, centre=(40, -10)):
    """Top view of the toy plane drawn nose-up, then turned so it climbs to the upper right."""
    n = size
    c = n / 2
    layer = Image.new("RGBA", (n, n), (0, 0, 0, 0))

    def at(x, y, k):
        return ((c + x * scale) * k, (c + y * scale) * k)

    def poly(points):
        return lambda d, k: d.polygon([at(x, y, k) for x, y in points], fill=255)

    def ell(x0, y0, x1, y1):
        return lambda d, k: d.ellipse((*at(x0, y0, k), *at(x1, y1, k)), fill=255)

    wings = poly([(-58, -20), (-268, 48), (-268, 98), (-58, 70), (58, 70), (268, 98), (268, 48), (58, -20)])
    tail = poly([(-30, 168), (-118, 196), (-118, 232), (-30, 222), (30, 222), (118, 232), (118, 196), (30, 168)])
    fin_top = ell(-14, 150, 14, 250)
    body = ell(-66, -238, 66, 248)
    canopy = ell(-34, -120, 34, 18)

    def nose(d, k):
        d.ellipse((*at(-66, -238, k), *at(66, 248, k)), fill=255)
        d.rectangle((*at(-80, -180, k), *at(80, 260, k)), fill=0)

    for fn, color, rnd in ((wings, WING, 6), (tail, WING, 5), (body, AIRFRAME, 0), (fin_top, AIRFRAME, 0),
                           (nose, NOSE, 0), (canopy, CANOPY, 0)):
        layer.alpha_composite(solid(n, color, mask(n, fn, rnd)))
    layer = layer.rotate(angle, resample=Image.BICUBIC, center=(c, c))
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.alpha_composite(layer, (round(centre[0] * scale), round(centre[1] * scale)))
    return out


def sdf(layer):
    """Inward distance from the layer edge, x 8 and capped at 255, matching the template's SDF files."""
    inside = layer.getchannel("A").point(lambda v: 255 if v >= 128 else 0)
    dist = Image.new("L", inside.size, 0)
    cur = inside
    for i in range(1, 33):
        dist.paste(min(255, i * 8), mask=cur)
        cur = cur.filter(ImageFilter.MinFilter(3) if i % 2 else ImageFilter.Kernel((3, 3), (0, 1, 0, 1, 1, 1, 0, 1, 0), 5))
        cur = cur.point(lambda v: 255 if v >= 255 else 0)
    dist = Image.composite(dist, Image.new("L", inside.size, 0), inside)
    return Image.merge("RGBA", (dist, dist, dist, dist))


def flatten(layers):
    out = Image.new("RGBA", layers[0].size, (0, 0, 0, 0))
    for layer in layers:
        out.alpha_composite(layer)
    return out


def main():
    layers = [background(S, 1.0, True), clouds(S, 1.0), plane(S, 1.0)]
    for i, layer in enumerate(layers):
        layer.save(os.path.join(RES, "drawable", f"icon_3d_layer_{i}.png"))
        sdf(layer).save(os.path.join(RES, "drawable", f"icon_3d_sdf_{i}.png"))
    flat = flatten(layers)
    flat.save(os.path.join(RES, "mipmap-xxxhdpi", "ic_spatial_launcher.png"))
    # Adaptive icon foreground: full-bleed square, artwork kept inside the 66/108 safe zone.
    a = 1424
    square = flatten([background(a, 0.62 * a / S, False), clouds(a, 0.62 * a / S), plane(a, 0.62 * a / S)])
    square.convert("RGB").save(os.path.join(RES, "drawable", "ic_launcher_foreground.png"))
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        w = 300
        sheet = Image.new("RGB", (w * 8, w), (60, 64, 70))
        tiles = layers + [flat] + [sdf(x) for x in layers] + [square]
        for i, t in enumerate(tiles):
            sheet.paste(t.resize((w, w), Image.LANCZOS), (i * w, 0), t.resize((w, w), Image.LANCZOS))
        sheet.save(out)
    print("ICON layers", len(layers))


main()
