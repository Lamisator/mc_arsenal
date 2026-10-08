#!/usr/bin/env python3
"""
Builds the 3D item models of every gun, launcher, rocket and grenade from boxes given in real millimetres.

Coordinates of a gun: x to its right, y up, z backwards (negative z is towards the muzzle). The origin is the grip,
where the right hand holds it: the model is scaled so that this point is the model's centre, which is where Minecraft
holds an item. Each gun gets its own scale (mm per model pixel) so that it fits the -16..32 pixel limit, and display
transforms that bring it back to its true size in the world (1 block = 1 m).

Writes models/item/<gun>.json, items/<gun>.json, the palette texture and client/GunGeometry.java (sight height,
eye relief, muzzle position) so that the code lines up with what is drawn.
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
A = os.path.join(ROOT, "src/main/resources/assets/arsenal")
GEOMETRY = os.path.join(ROOT, "src/client/java/net/antwire/arsenal/client/GunGeometry.java")

# ---------------------------------------------------------------- palette
# name: (r, g, b). Each colour is a 4x4 cell of a 32x32 texture with a little grain.
COLORS = {
    "polymer": (34, 34, 36), "black": (22, 23, 26), "gunmetal": (52, 55, 60), "parkerized": (66, 68, 64),
    "steel": (148, 150, 156), "steel_dark": (92, 94, 98), "silver": (196, 198, 202), "chrome": (215, 218, 222),
    "wood": (128, 74, 38), "wood_dark": (90, 52, 26), "wood_light": (156, 98, 52), "bakelite": (132, 58, 30),
    "olive": (84, 92, 58), "od": (72, 80, 50), "dark_green": (50, 60, 38), "awm_green": (92, 106, 70),
    "tan": (178, 154, 110), "fde": (160, 134, 96), "coyote": (128, 102, 70), "rubber": (16, 16, 16),
    "brass": (198, 160, 72), "copper": (182, 108, 60), "red": (190, 28, 28), "red_dot": (255, 40, 40),
    "lens": (26, 40, 62), "glass": (92, 122, 150), "white": (226, 226, 226), "yellow": (210, 180, 40),
    "rpg_green": (76, 90, 54), "warhead": (96, 104, 64), "gray": (110, 112, 112), "light_gray": (160, 162, 160),
    "orange": (210, 110, 30), "blue_gray": (70, 80, 92), "leather": (108, 72, 44), "sand": (194, 178, 128),
    "dark_tan": (120, 104, 72), "aluminium": (170, 172, 176), "pin": (178, 180, 184), "spoon": (120, 124, 112),
}
NAMES = list(COLORS)
CELL = 4
TEX = 32


def palette_png(path):
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    import random
    rnd = random.Random(1)
    for i, name in enumerate(NAMES):
        cx, cy = (i % 8) * CELL, (i // 8) * CELL
        r, g, b = COLORS[name]
        for y in range(CELL):
            for x in range(CELL):
                d = rnd.randint(-5, 5)
                img.putpixel((cx + x, cy + y), (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), 255))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def uv(color):
    i = NAMES.index(color)
    u = (i % 8) * CELL * 16 / TEX
    v = (i // 8) * CELL * 16 / TEX
    s = CELL * 16 / TEX
    m = 0.5
    return [u + m, v + m, u + s - m, v + s - m]


# ---------------------------------------------------------------- model builder
class Model:
    def __init__(self, name):
        self.name = name
        self.parts = []  # (color, (x0,y0,z0), (x1,y1,z1), rotation or None) in mm

    def box(self, color, x, y, z, rot=None, shade=True):
        """x, y, z are (min, max) pairs in mm. rot = (axis, degrees, (ox, oy, oz) mm)."""
        x0, x1 = sorted(x)
        y0, y1 = sorted(y)
        z0, z1 = sorted(z)
        self.parts.append((color, (x0, y0, z0), (x1, y1, z1), rot, shade))

    def bar(self, color, cx, cy, z, r, rot45=True):
        """A round bar along z (barrel, tube): a square and the same square turned 45 degrees."""
        self.box(color, (cx - r, cx + r), (cy - r, cy + r), z)
        if rot45:
            k = r * 0.98
            self.box(color, (cx - k, cx + k), (cy - k, cy + k), z, rot=("z", 45, (cx, cy, 0)))

    def ybar(self, color, cx, cz, y, r):
        """A round bar standing up (grenade body)."""
        self.box(color, (cx - r, cx + r), y, (cz - r, cz + r))
        k = r * 0.98
        self.box(color, (cx - k, cx + k), y, (cz - k, cz + k), rot=("y", 45, (cx, 0, cz)))

    def extent(self):
        e = 0
        for _, a, b, rot, _ in self.parts:
            for v in (*a, *b):
                e = max(e, abs(v))
            if rot:
                # a turned box can poke out a little further
                e = max(e, max(abs(v) for v in (*a, *b)) * 1.0)
        return e

    def bounds(self):
        lo = [1e9, 1e9, 1e9]
        hi = [-1e9, -1e9, -1e9]
        for _, a, b, _, _ in self.parts:
            for i in range(3):
                lo[i] = min(lo[i], a[i])
                hi[i] = max(hi[i], b[i])
        return lo, hi

    def elements(self, mpp):
        out = []
        for color, a, b, rot, shade in self.parts:
            frm = [round(8 + a[i] / mpp, 4) for i in range(3)]
            to = [round(8 + b[i] / mpp, 4) for i in range(3)]
            # Minecraft wants from <= to and a non-zero size
            for i in range(3):
                if to[i] - frm[i] < 0.01:
                    to[i] = frm[i] + 0.01
            faces = {f: {"uv": uv(color), "texture": "#p"} for f in ("north", "south", "east", "west", "up", "down")}
            e = {"from": frm, "to": to, "faces": faces}
            if rot:
                axis, angle, origin = rot
                e["rotation"] = {"origin": [round(8 + origin[i] / mpp, 4) for i in range(3)], "axis": axis, "angle": angle}
            if not shade:
                e["shade"] = False
            out.append(e)
        return out


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=1)
        f.write("\n")


def emit(model, kind="gun", fp=1.0, tp=None, gui_rot=None, fixed_rot=None, name=None):
    """Writes the model with display transforms that give it its true size (1 px = 62.5 mm at scale 1)."""
    name = name or model.name
    ext = model.extent()
    mpp = max(ext / 23.4, 1.0)
    true = mpp / 62.5
    lo, hi = model.bounds()
    c = [(lo[i] + hi[i]) / 2 / mpp for i in range(3)]  # bbox centre in px, relative to the model centre
    length_px = (hi[2] - lo[2]) / mpp
    height_px = (hi[1] - lo[1]) / mpp
    width_px = (hi[0] - lo[0]) / mpp

    def r3(v):
        return [round(x, 4) for x in v]

    display = {}
    if kind == "gun":
        g = min(15.0 / max(length_px, 1e-3), 13.0 / max(height_px, 1e-3), 4.0)
        display["gui"] = {"rotation": gui_rot or [0, -90, 0], "translation": r3([g * c[2], -g * c[1], 0]), "scale": r3([g] * 3)}
        display["fixed"] = {"rotation": fixed_rot or [0, -90, 0], "translation": r3([true * c[2] * 16 / 16, -true * c[1], 0]), "scale": r3([true] * 3)}
        display["ground"] = {"rotation": [0, -90, 0], "translation": r3([true * c[2] * 0.8, -true * c[1] * 0.8 + 2, 0]), "scale": r3([true * 0.8] * 3)}
        display["firstperson_righthand"] = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": r3([true] * 3)}
        display["firstperson_lefthand"] = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": r3([true] * 3)}
        t = tp or {"rotation": [0, 0, 0], "translation": [0, 0, 0]}
        display["thirdperson_righthand"] = {"rotation": t["rotation"], "translation": t["translation"], "scale": r3([true * 0.95] * 3)}
        display["thirdperson_lefthand"] = {"rotation": t["rotation"], "translation": t["translation"], "scale": r3([true * 0.95] * 3)}
        display["head"] = {"rotation": [0, -90, 0], "translation": [0, 0, 0], "scale": r3([true] * 3)}
    elif kind == "small":
        # grenades and the like: shown bigger than life in the inventory
        g = min(12.0 / max(height_px, width_px, length_px, 1e-3), 4.0)
        display["gui"] = {"rotation": [25, 45, 0], "translation": r3([0, -g * c[1], 0]), "scale": r3([g] * 3)}
        display["ground"] = {"rotation": [0, 0, 0], "translation": r3([0, -true * lo[1] / mpp, 0]), "scale": r3([true * 1.4] * 3)}
        display["fixed"] = {"rotation": [0, 0, 0], "translation": r3([0, -g * c[1], 0]), "scale": r3([g * 0.8] * 3)}
        display["firstperson_righthand"] = {"rotation": [0, -20, 0], "translation": [0, 0, 0], "scale": r3([true * 0.9] * 3)}
        display["firstperson_lefthand"] = {"rotation": [0, 20, 0], "translation": [0, 0, 0], "scale": r3([true * 0.9] * 3)}
        display["thirdperson_righthand"] = {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": r3([true * 1.1] * 3)}
        display["thirdperson_lefthand"] = {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": r3([true * 1.1] * 3)}
    elif kind == "rocket":
        g = min(15.0 / max(length_px, 1e-3), 4.0)
        display["gui"] = {"rotation": [0, -90, -35], "translation": [0, 0, 0], "scale": r3([g] * 3)}
        display["ground"] = {"rotation": [0, -90, 0], "translation": [0, 2, 0], "scale": r3([true * 0.8] * 3)}
        display["fixed"] = {"rotation": [0, -90, 0], "translation": [0, 0, 0], "scale": r3([true] * 3)}
        display["firstperson_righthand"] = {"rotation": [-10, 0, 0], "translation": [0, 2, -2], "scale": r3([true] * 3)}
        display["firstperson_lefthand"] = {"rotation": [-10, 0, 0], "translation": [0, 2, -2], "scale": r3([true] * 3)}
        display["thirdperson_righthand"] = {"rotation": [90, 0, 0], "translation": [0, 3, 1], "scale": r3([true * 0.95] * 3)}
        display["thirdperson_lefthand"] = {"rotation": [90, 0, 0], "translation": [0, 3, 1], "scale": r3([true * 0.95] * 3)}
    obj = {"textures": {"p": "arsenal:item/gun_palette", "particle": "arsenal:item/gun_palette"}, "elements": model.elements(mpp), "display": display}
    write(os.path.join(A, "models/item", name + ".json"), obj)
    return mpp


def item_def(name, model=None):
    write(os.path.join(A, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": model or f"arsenal:item/{name}"}})


GEOM = {}


def gun(model, enum, sight, eye_relief, muzzle, bore, **kw):
    emit(model, **kw)
    GEOM[enum] = (sight / 1000, eye_relief / 1000, muzzle / 1000, bore / 1000)


# ---------------------------------------------------------------- pistols
def glock():
    m = Model("glock17")
    # slide and frame
    m.box("black", (-12, 12), (6, 34), (-166, 20))
    m.box("gunmetal", (-12.5, 12.5), (20, 30), (-30, 8))  # ejection port area, a shade lighter
    m.box("black", (-4, 4), (14, 24), (-170, -166))  # muzzle face
    m.box("rubber", (-2.5, 2.5), (16, 22), (-171, -169))  # bore
    m.box("polymer", (-13, 13), (-6, 7), (-160, 10))  # frame with dust cover
    m.box("polymer", (-10, 10), (-10, -6), (-150, -80))  # accessory rail
    # grip, raked back
    m.box("polymer", (-14, 14), (-112, -2), (-20, 26), rot=("x", -18, (0, -4, 4)))
    m.box("black", (-14.5, 14.5), (-120, -110), (-12, 34), rot=("x", -18, (0, -4, 4)))  # magazine base plate
    # trigger guard and trigger
    m.box("polymer", (-5, 5), (-34, -29), (-72, -22))
    m.box("polymer", (-5, 5), (-34, -6), (-74, -68))
    m.box("black", (-2, 2), (-26, -8), (-44, -39), rot=("x", 10, (0, -8, -42)))
    # sights: front post, rear notch
    m.box("black", (-1.5, 1.5), (34, 40), (-158, -150))
    m.box("white", (-0.8, 0.8), (37, 39), (-158.5, -157.5))
    m.box("black", (-10, -2.5), (34, 41), (6, 14))
    m.box("black", (2.5, 10), (34, 41), (6, 14))
    gun(m, "GLOCK_17", 38, 10, 171, 19, fp=1.35)


def deagle():
    m = Model("deagle")
    m.box("silver", (-14, 14), (4, 40), (-150, 32))  # slide
    m.box("silver", (-13, 13), (12, 44), (-262, -150))  # barrel with its triangular top rib
    m.box("chrome", (-6, 6), (44, 48), (-262, -150))
    m.box("steel_dark", (-4, 4), (22, 34), (-264, -261))
    m.box("rubber", (-3, 3), (24, 32), (-265, -263))
    m.box("steel", (-14.5, 14.5), (-10, 6), (-210, 20))  # frame
    for z in range(-20, 30, 7):
        m.box("steel_dark", (-14.6, 14.6), (22, 34), (z, z + 2.5))  # slide serrations
    m.box("black", (-16, 16), (-126, -6), (-26, 30), rot=("x", -14, (0, -8, 6)))  # rubber grip
    m.box("silver", (-15, 15), (-130, -124), (-20, 38), rot=("x", -14, (0, -8, 6)))
    m.box("steel", (-5, 5), (-40, -34), (-86, -22))
    m.box("steel", (-5, 5), (-40, -10), (-88, -82))
    m.box("black", (-2, 2), (-30, -10), (-50, -44), rot=("x", 10, (0, -10, -47)))
    m.box("black", (-2, 2), (48, 54), (-255, -246))  # front sight
    m.box("black", (-11, -3), (40, 49), (14, 24))
    m.box("black", (3, 11), (40, 49), (14, 24))
    m.box("steel_dark", (-16, -14), (24, 30), (8, 22))  # safety
    gun(m, "DESERT_EAGLE", 49, 18, 265, 28, fp=1.25)


# ---------------------------------------------------------------- SMG
def mp5():
    m = Model("mp5")
    m.box("black", (-15, 15), (0, 52), (-150, 170))  # receiver
    m.box("gunmetal", (-15.5, 15.5), (30, 44), (-60, 10))  # ejection port
    m.bar("black", 0, 40, (-300, -150), 9)  # cocking tube
    m.box("black", (-26, -9), (36, 44), (-262, -250))  # cocking handle (left)
    m.box("polymer", (-18, 18), (-2, 30), (-282, -150))  # handguard
    for z in range(-275, -155, 12):
        m.box("black", (-18.5, 18.5), (8, 22), (z, z + 4))
    m.bar("gunmetal", 0, 18, (-345, -282), 6.5)  # barrel
    m.box("black", (-8, 8), (10, 26), (-345, -326))  # tri-lug
    # front sight hood: two posts and a top bar
    m.box("black", (-11, -8), (50, 80), (-292, -270))
    m.box("black", (8, 11), (50, 80), (-292, -270))
    m.box("black", (-11, 11), (77, 80), (-292, -270))
    m.box("black", (-1.5, 1.5), (52, 64), (-284, -278))
    m.box("black", (-9, 9), (48, 52), (-296, -266))
    # rear drum
    m.box("black", (-14, 14), (52, 74), (110, 136))
    m.box("rubber", (-3, 3), (60, 68), (109, 137))
    # trigger group and grip
    m.box("polymer", (-14, 14), (-22, 0), (-70, 70))
    m.box("polymer", (-14, 14), (-112, -4), (8, 46), rot=("x", -14, (0, -6, 24)))
    m.box("polymer", (-5, 5), (-40, -34), (-60, 10))
    m.box("black", (-2, 2), (-30, -12), (-26, -20), rot=("x", 10, (0, -12, -23)))
    # curved magazine
    m.box("black", (-12, 12), (-80, 0), (-128, -86), rot=("x", 8, (0, 0, -107)))
    m.box("black", (-12, 12), (-170, -80), (-150, -110), rot=("x", 20, (0, -80, -125)))
    # retractable stock: struts and butt plate
    m.box("black", (-14, -10), (10, 18), (170, 330))
    m.box("black", (10, 14), (10, 18), (170, 330))
    m.box("black", (-14, -10), (36, 44), (170, 330))
    m.box("black", (10, 14), (36, 44), (170, 330))
    m.box("rubber", (-18, 18), (-30, 62), (326, 340))
    gun(m, "MP5", 64, 140, 345, 18, fp=1.15)


# ---------------------------------------------------------------- rifles
def m4a1():
    m = Model("m4a1")
    # upper receiver and flat-top rail
    m.box("black", (-11, 11), (14, 44), (-70, 104))
    m.box("polymer", (-10, 10), (44, 50), (-60, 104))
    for z in range(-58, 104, 9):
        m.box("black", (-10.3, 10.3), (44, 50.5), (z, z + 4))
    m.box("gunmetal", (11, 12), (22, 38), (-4, 46))  # ejection port cover (right)
    m.box("black", (8, 16), (28, 36), (8, 18))  # forward assist
    m.box("black", (-8, 8), (42, 48), (104, 114))  # charging handle
    # lower receiver, magwell, trigger guard, grip
    m.box("black", (-11.5, 11.5), (-6, 14), (-74, 70))
    m.box("black", (-12, 12), (-46, -6), (-78, -14))
    m.box("black", (-3, 3), (-26, -22), (-20, 16))
    m.box("black", (-2, 2), (-20, -6), (-8, -3), rot=("x", 10, (0, -8, -5)))
    m.box("polymer", (-12, 12), (-106, -4), (6, 40), rot=("x", -22, (0, -6, 22)))
    # STANAG magazine, curving forward
    m.box("parkerized", (-10.5, 10.5), (-110, -44), (-74, -16), rot=("x", 4, (0, -44, -45)))
    m.box("parkerized", (-10.5, 10.5), (-190, -108), (-84, -26), rot=("x", 12, (0, -110, -55)))
    m.box("black", (-11.5, 11.5), (-198, -186), (-88, -24), rot=("x", 12, (0, -110, -55)))
    # free-floating rail handguard
    m.box("black", (-16, 16), (-2, 46), (-262, -70))
    for z in range(-258, -76, 12):
        m.box("polymer", (-16.4, 16.4), (8, 36), (z, z + 6))
        m.box("polymer", (-10, 10), (46, 51), (z, z + 6))
    # barrel, gas block, flash hider
    m.bar("gunmetal", 0, 29, (-372, -262), 6.5)
    m.box("black", (-8, 8), (20, 38), (-382, -366))
    for a in (0, 1, 2):
        m.box("black", (-9, 9), (24 + a * 0.01, 34), (-404, -382), rot=("z", 60 * a, (0, 29, 0)))
    # buffer tube and stock
    m.box("black", (-8, 8), (20, 40), (104, 232))
    m.box("polymer", (-11, 11), (-26, 44), (196, 290))
    m.box("polymer", (-10, 10), (36, 52), (196, 268))  # cheek rest
    m.box("rubber", (-12, 12), (-36, 46), (286, 298))
    # holographic sight: a frame you can see through, and the red dot
    m.box("black", (-14, 14), (50, 58), (2, 64))
    m.box("black", (-14, -11), (58, 86), (8, 22))
    m.box("black", (11, 14), (58, 86), (8, 22))
    m.box("black", (-14, 14), (83, 88), (6, 24))
    m.box("glass", (-11, 11), (58, 59), (8, 22))
    m.box("black", (-14, -11), (58, 86), (52, 62))
    m.box("black", (11, 14), (58, 86), (52, 62))
    m.box("black", (-14, 14), (83, 88), (50, 64))
    m.box("black", (8, 14), (58, 64), (24, 52))  # battery housing
    m.box("red_dot", (-0.6, 0.6), (71.4, 72.6), (14, 15), shade=False)
    gun(m, "M4A1", 72, 75, 404, 29, fp=1.05)


def ak47():
    m = Model("ak47")
    m.box("blue_gray", (-12, 12), (-6, 44), (-104, 150))  # receiver
    m.box("blue_gray", (-11, 11), (44, 54), (-52, 150))  # dust cover
    m.box("blue_gray", (-10, 10), (44, 58), (-110, -86))  # rear sight block
    m.box("black", (-3, 3), (56, 61), (-100, -90))
    m.box("blue_gray", (-1, 1), (54, 60), (-88, -60))  # sight leaf
    m.box("steel_dark", (12, 34), (28, 36), (66, 82))  # charging handle
    m.box("steel_dark", (12, 13), (6, 30), (-30, 90))  # selector lever
    # wooden upper handguard over the gas tube, lower handguard
    m.box("wood", (-12, 12), (38, 58), (-276, -110))
    m.bar("blue_gray", 0, 48, (-372, -276), 8)
    m.box("wood", (-17, 17), (2, 38), (-300, -110))
    m.box("wood_dark", (-17.5, 17.5), (12, 28), (-290, -270))
    m.box("blue_gray", (-15, 15), (0, 40), (-120, -104))
    # barrel, gas block, front sight, muzzle brake
    m.bar("blue_gray", 0, 28, (-470, -300), 7)
    m.box("blue_gray", (-10, 10), (20, 56), (-384, -360))
    m.box("blue_gray", (-9, 9), (28, 66), (-456, -440))
    m.box("blue_gray", (-1.5, 1.5), (66, 74), (-450, -446))
    m.box("blue_gray", (-9, -6), (66, 76), (-456, -440))
    m.box("blue_gray", (6, 9), (66, 76), (-456, -440))
    m.box("black", (-8, 8), (20, 36), (-500, -470), rot=("x", -8, (0, 28, -485)))
    # curved magazine (steel, the classic shape)
    m.box("blue_gray", (-11, 11), (-60, -6), (-104, -40), rot=("x", 6, (0, -6, -72)))
    m.box("blue_gray", (-11, 11), (-130, -58), (-124, -62), rot=("x", 22, (0, -60, -92)))
    m.box("blue_gray", (-11, 11), (-190, -126), (-152, -92), rot=("x", 38, (0, -128, -120)))
    # trigger guard, wooden pistol grip
    m.box("blue_gray", (-3, 3), (-26, -22), (-34, 22))
    m.box("blue_gray", (-2, 2), (-20, -6), (-14, -9), rot=("x", 10, (0, -8, -11)))
    m.box("wood", (-12, 12), (-104, -4), (14, 46), rot=("x", -18, (0, -6, 30)))
    # wooden stock, dropping to the butt
    m.box("wood", (-12, 12), (-14, 40), (150, 250))
    m.box("wood", (-13, 13), (-50, 34), (240, 400), rot=("x", 8, (0, 20, 250)))
    m.box("wood", (-12, 12), (-30, 0), (150, 260), rot=("x", 14, (0, 0, 150)))
    m.box("steel_dark", (-14, 14), (-60, 32), (396, 408), rot=("x", 8, (0, 20, 250)))
    gun(m, "AK47", 65, 90, 500, 28, fp=1.05)


# ---------------------------------------------------------------- shotguns
def r870():
    m = Model("r870")
    m.box("black", (-16, 16), (-4, 46), (-44, 150))  # receiver
    m.box("gunmetal", (16, 17), (14, 36), (0, 70))  # ejection port
    m.bar("black", 0, 34, (-760, -44), 10)  # barrel
    m.box("white", (-1.5, 1.5), (44, 48), (-752, -748))  # bead
    m.bar("black", 0, 8, (-600, -44), 10)  # magazine tube
    m.box("black", (-9, 9), (-2, 44), (-606, -594))  # barrel clamp
    # pump forend with grooves
    m.box("polymer", (-19, 19), (-12, 26), (-330, -136))
    for z in range(-322, -144, 14):
        m.box("black", (-19.5, 19.5), (-10, 20), (z, z + 6))
    # trigger group
    m.box("black", (-12, 12), (-22, -4), (20, 120))
    m.box("black", (-3, 3), (-40, -34), (10, 70))
    m.box("black", (-2, 2), (-30, -14), (36, 41), rot=("x", 10, (0, -14, 38)))
    # buttstock (synthetic), dropping, with the rubber pad
    m.box("polymer", (-15, 15), (-30, 40), (150, 260), rot=("x", 6, (0, 0, 150)))
    m.box("polymer", (-16, 16), (-80, 40), (250, 500), rot=("x", 6, (0, 0, 150)))
    m.box("rubber", (-17, 17), (-90, 42), (496, 514), rot=("x", 6, (0, 0, 150)))
    gun(m, "REMINGTON_870", 48, 120, 760, 34, fp=1.0)


def m4super90():
    m = Model("m4super90")
    m.box("black", (-16, 16), (-6, 50), (-40, 168))
    m.box("polymer", (-10, 10), (50, 56), (-30, 160))
    for z in range(-28, 160, 9):
        m.box("black", (-10.3, 10.3), (50, 56.5), (z, z + 4))
    m.box("gunmetal", (16, 17), (16, 40), (10, 80))
    m.box("steel", (16, 30), (24, 30), (50, 60))  # charging handle
    # ghost ring rear sight, front post
    m.box("black", (-12, -8), (56, 82), (126, 140))
    m.box("black", (8, 12), (56, 82), (126, 140))
    m.box("black", (-12, 12), (78, 84), (126, 140))
    m.box("black", (-12, 12), (56, 60), (124, 142))
    m.bar("black", 0, 38, (-470, -40), 9.5)
    m.box("black", (-8, -5), (44, 72), (-456, -440))
    m.box("black", (5, 8), (44, 72), (-456, -440))
    m.box("orange", (-1.2, 1.2), (48, 70), (-450, -446))
    m.bar("black", 0, 12, (-420, -40), 11)
    m.box("polymer", (-20, 20), (-10, 34), (-330, -60))
    for z in range(-320, -70, 16):
        m.box("black", (-20.5, 20.5), (0, 24), (z, z + 6))
    # pistol grip and trigger
    m.box("black", (-12, 12), (-24, -6), (40, 140))
    m.box("polymer", (-13, 13), (-114, -6), (88, 126), rot=("x", -16, (0, -8, 106)))
    m.box("black", (-3, 3), (-42, -36), (30, 90))
    m.box("black", (-2, 2), (-30, -14), (60, 65), rot=("x", 10, (0, -14, 62)))
    # collapsible stock
    m.box("black", (-9, 9), (16, 40), (168, 330))
    m.box("polymer", (-14, 14), (-50, 48), (290, 410))
    m.box("rubber", (-15, 15), (-58, 50), (404, 418))
    gun(m, "BENELLI_M4", 70, 130, 470, 38, fp=1.0)


# ---------------------------------------------------------------- sniper rifles
def scope(m, z0, z1, cy, r, eye=None):
    """A riflescope: tube, objective bell, eyepiece, turrets, rings."""
    m.bar("black", 0, cy, (z0 + 70, z1 - 60), r)
    m.bar("black", 0, cy, (z0, z0 + 70), r * 1.45)
    m.box("lens", (-r * 1.2, r * 1.2), (cy - r * 1.2, cy + r * 1.2), (z0 - 1, z0 + 2))
    m.bar("black", 0, cy, (z1 - 60, z1), r * 1.3)
    m.box("lens", (-r * 1.0, r * 1.0), (cy - r * 1.0, cy + r * 1.0), (z1 - 2, z1 + 1))
    mid = (z0 + z1) / 2 - 10
    m.box("black", (-7, 7), (cy + r, cy + r + 16), (mid - 8, mid + 8))
    m.box("black", (r, r + 16), (cy - 7, cy + 7), (mid - 8, mid + 8))
    for z in (z0 + 110, z1 - 110):
        m.box("gunmetal", (-r - 3, r + 3), (cy - r - 14, cy - r + 6), (z - 10, z + 10))


def awm():
    m = Model("awm")
    # chassis and stock in olive green
    m.box("awm_green", (-24, 24), (-26, 38), (-360, 120))  # forend
    m.box("awm_green", (-22, 22), (0, 44), (120, 300))
    m.box("awm_green", (-22, 22), (-20, 44), (290, 540))
    m.box("awm_green", (-21, 21), (-128, 4), (430, 540))  # butt
    m.box("awm_green", (-18, 18), (-112, -60), (290, 440))  # lower rail of the thumbhole
    m.box("awm_green", (-18, 18), (-110, 0), (50, 110), rot=("x", -12, (0, -50, 80)))  # pistol grip of the thumbhole
    m.box("awm_green", (-16, 16), (44, 60), (300, 470))  # cheek piece
    m.box("rubber", (-22, 22), (-132, 46), (536, 552))
    # action, bolt
    m.box("black", (-15, 15), (30, 62), (-140, 140))
    m.bar("steel_dark", 0, 48, (60, 150), 9)
    m.box("steel_dark", (15, 44), (40, 48), (110, 120))
    m.box("black", (40, 52), (36, 52), (106, 124))
    m.box("black", (-14, 14), (-72, -20), (-90, -20))  # magazine
    m.box("black", (-3, 3), (-40, -34), (-6, 52))
    m.box("black", (-2, 2), (-30, -14), (18, 23), rot=("x", 10, (0, -14, 20)))
    # barrel and muzzle brake
    m.bar("black", 0, 46, (-720, -140), 9.5)
    m.box("black", (-13, 13), (34, 58), (-770, -720))
    m.box("gunmetal", (-13.5, 13.5), (38, 54), (-760, -752))
    m.box("gunmetal", (-13.5, 13.5), (38, 54), (-740, -732))
    # folded bipod
    m.box("black", (-12, 12), (-34, -26), (-350, -330))
    m.box("black", (-10, -6), (-34, -26), (-330, -90))
    m.box("black", (6, 10), (-34, -26), (-330, -90))
    scope(m, -190, 220, 88, 14)
    m.box("black", (-12, 12), (62, 72), (-60, -40))
    m.box("black", (-12, 12), (62, 72), (70, 90))
    gun(m, "AWM", 88, 220, 770, 46, fp=0.95)


def m82():
    m = Model("m82")
    m.box("gunmetal", (-22, 22), (22, 84), (-400, 360))  # upper receiver
    m.box("black", (-22.5, 22.5), (30, 76), (-380, -120))
    for z in range(-370, -130, 22):
        m.box("gunmetal", (-23, 23), (40, 66), (z, z + 10))  # cooling slots
    m.box("gunmetal", (-21, 21), (-12, 22), (-160, 320))  # lower receiver
    m.box("black", (-12, 12), (84, 92), (-320, 180))  # rail
    for z in range(-316, 180, 12):
        m.box("gunmetal", (-12.3, 12.3), (84, 92.5), (z, z + 5))
    m.box("black", (-8, 8), (92, 110), (-120, -100))  # carry handle posts
    m.box("black", (-8, 8), (92, 110), (60, 80))
    m.box("black", (-8, 8), (106, 112), (-120, 80))
    m.box("steel_dark", (22, 42), (54, 62), (-60, -40))  # charging handle
    # barrel and the big double-chamber muzzle brake
    m.bar("gunmetal", 0, 54, (-880, -400), 11)
    m.box("black", (-24, 24), (36, 72), (-950, -880))
    m.box("gunmetal", (-25, 25), (40, 68), (-930, -918))
    m.box("gunmetal", (-25, 25), (40, 68), (-906, -894))
    # magazine, trigger, grip
    m.box("black", (-20, 20), (-96, -12), (-60, 60))
    m.box("black", (-4, 4), (-48, -42), (60, 140))
    m.box("black", (-2, 2), (-36, -14), (90, 96), rot=("x", 10, (0, -14, 93)))
    m.box("polymer", (-14, 14), (-130, -10), (120, 166), rot=("x", -14, (0, -10, 143)))
    # butt with recoil pad and monopod
    m.box("gunmetal", (-22, 22), (-40, 84), (360, 540))
    m.box("rubber", (-26, 26), (-64, 90), (536, 562))
    m.box("black", (-6, 6), (-100, -40), (446, 466))
    # bipod folded along the barrel
    m.box("black", (-14, 14), (32, 44), (-420, -396))
    m.box("black", (-14, -9), (30, 38), (-760, -420))
    m.box("black", (9, 14), (30, 38), (-760, -420))
    scope(m, -140, 280, 118, 15)
    m.box("black", (-13, 13), (92, 102), (-30, -10))
    m.box("black", (-13, 13), (92, 102), (120, 140))
    gun(m, "BARRETT_M82", 118, 280, 950, 54, fp=0.9)


# ---------------------------------------------------------------- launchers
def pg7v_warhead(m, z_tip, cy=20, scale=1.0):
    """The PG-7V's 85 mm HEAT warhead, nose forward at z_tip (negative), as seen sticking out of the tube."""
    s = scale
    m.bar("warhead", 0, cy, (z_tip, z_tip + 24 * s), 6 * s)  # piezo fuze tip
    m.bar("warhead", 0, cy, (z_tip + 24 * s, z_tip + 90 * s), 22 * s)
    m.bar("warhead", 0, cy, (z_tip + 90 * s, z_tip + 150 * s), 34 * s)
    m.bar("warhead", 0, cy, (z_tip + 150 * s, z_tip + 270 * s), 42 * s)
    m.bar("od", 0, cy, (z_tip + 270 * s, z_tip + 330 * s), 30 * s)
    m.bar("steel_dark", 0, cy, (z_tip + 330 * s, z_tip + 360 * s), 20 * s)


def rpg7(loaded=True):
    m = Model("rpg7" if loaded else "rpg7_empty")
    m.bar("rpg_green", 0, 20, (-330, 600), 20)  # launch tube
    m.bar("wood", 0, 20, (-160, 150), 26)  # the wooden heat shield
    m.box("wood_dark", (-26.5, 26.5), (14, 26), (-150, -140))
    m.box("wood_dark", (-26.5, 26.5), (14, 26), (140, 150))
    m.bar("black", 0, 20, (-345, -325), 24)  # muzzle ring
    m.bar("rpg_green", 0, 20, (560, 600), 26)  # venturi, flaring out
    m.bar("black", 0, 20, (600, 640), 32)
    m.box("rubber", (-25, 25), (-5, 45), (639, 642))
    # grips: trigger grip and rear grip
    m.box("wood", (-12, 12), (-100, -10), (-12, 28), rot=("x", -10, (0, -10, 8)))
    m.box("black", (-10, 10), (-10, 0), (-30, 40))
    m.box("black", (-3, 3), (-30, -26), (-36, 6))
    m.box("wood", (-12, 12), (-88, -10), (150, 186), rot=("x", -8, (0, -10, 168)))
    # iron sights, PGO-7 optic on the left
    m.box("black", (-2, 2), (46, 70), (-150, -144))
    m.box("black", (-6, 6), (46, 64), (60, 70))
    m.box("od", (-62, -26), (30, 74), (40, 170))
    m.box("od", (-58, -30), (40, 66), (170, 200))
    m.box("rubber", (-56, -32), (42, 64), (200, 216))
    m.box("lens", (-58, -30), (40, 66), (38, 41))
    m.box("black", (-30, -24), (40, 60), (80, 130))
    if loaded:
        pg7v_warhead(m, -690)
    gun(m, "RPG7" if loaded else "RPG7_EMPTY", 52, 120, 690 if loaded else 345, 20, fp=0.95)


def javelin():
    m = Model("javelin")
    # launch tube on the shoulder, CLU on its left
    m.bar("od", 0, 70, (-600, 600), 70)
    m.bar("dark_green", 0, 70, (-620, -590), 74)
    m.bar("dark_green", 0, 70, (590, 620), 74)
    m.box("black", (-60, 60), (128, 146), (-300, -200))  # sling mounts
    m.box("black", (-60, 60), (128, 146), (250, 350))
    m.box("rubber", (40, 80), (100, 150), (60, 200))  # shoulder pad
    m.box("tan", (-250, -76), (30, 170), (-220, 60))  # command launch unit
    m.box("dark_tan", (-252, -74), (30, 40), (-222, 62))
    m.box("lens", (-230, -100), (60, 150), (-225, -219))
    m.box("rubber", (-210, -150), (90, 140), (60, 100))  # eyepiece
    m.box("black", (-200, -160), (100, 130), (100, 104))
    m.box("tan", (-240, -220), (-40, 30), (-160, -120))  # grips
    m.box("tan", (-120, -100), (-40, 30), (-160, -120))
    m.box("tan", (-240, -100), (-48, -40), (-164, -116))
    m.box("black", (-200, -140), (170, 182), (-120, 0))  # battery
    gun(m, "JAVELIN", 115, 100, 620, 70, fp=0.85)


# ---------------------------------------------------------------- rockets (in flight and as ammunition)
def rockets():
    m = Model("pg7v")
    pg7v_warhead(m, -460)
    m.bar("gray", 0, 0, (-100, 300), 20)  # sustainer motor
    for a in range(4):
        m.box("steel_dark", (-1.5, 1.5), (20, 60), (240, 300), rot=("z", 45 + 90 * a, (0, 0, 0)))
    m.bar("rpg_green", 0, 0, (300, 450), 15)  # booster
    # the warhead is drawn from z=-460 forward; centre the round on the model
    for p in list(m.parts):
        pass
    emit(m, kind="rocket")
    item_def("pg7v")

    m = Model("javelin_missile")
    m.bar("od", 0, 0, (-540, 540), 63)
    m.bar("dark_green", 0, 0, (-580, -540), 40)
    m.box("lens", (-30, 30), (-30, 30), (-584, -580))
    for a in range(4):
        m.box("od", (-2, 2), (60, 120), (-260, -160), rot=("z", 45 + 90 * a, (0, 0, 0)))
        m.box("od", (-2, 2), (60, 110), (420, 540), rot=("z", 90 * a, (0, 0, 0)))
    m.bar("black", 0, 0, (540, 560), 45)
    emit(m, kind="rocket")
    item_def("javelin_missile")


# ---------------------------------------------------------------- grenades
def grenades():
    m = Model("m67")
    # 64 mm steel sphere, fuze, spoon and pin
    m.box("od", (-32, 32), (-24, 24), (-24, 24))
    m.box("od", (-24, 24), (-32, 32), (-24, 24))
    m.box("od", (-24, 24), (-24, 24), (-32, 32))
    m.ybar("spoon", 0, 0, (32, 50), 10)
    m.box("spoon", (-8, 8), (34, 44), (10, 18))
    m.box("spoon", (-7, 7), (-10, 46), (16, 22), rot=("x", -8, (0, 44, 14)))
    m.box("pin", (8, 20), (42, 44), (-2, 2))
    m.box("pin", (20, 40), (30, 56), (-1, 1))
    m.box("pin", (20, 40), (30, 32), (-6, 6))
    m.box("pin", (20, 40), (54, 56), (-6, 6))
    emit(m, kind="small")
    item_def("m67")

    m = Model("m84")
    m.ybar("black", 0, 0, (-60, 50), 22)
    for y in range(-48, 40, 16):
        m.box("gray", (-22.5, 22.5), (y, y + 6), (-12, 12))
        m.box("gray", (-12, 12), (y, y + 6), (-22.5, 22.5))
    m.ybar("spoon", 0, 0, (50, 66), 10)
    m.box("spoon", (-7, 7), (-50, 64), (22, 28), rot=("x", -6, (0, 60, 22)))
    m.box("pin", (8, 22), (58, 60), (-2, 2))
    m.box("pin", (22, 42), (46, 72), (-1, 1))
    emit(m, kind="small")
    item_def("m84")

    m = Model("m18")
    m.ybar("dark_green", 0, 0, (-72, 50), 31)
    m.box("white", (-31.5, 31.5), (-20, 0), (-12, 12))
    m.box("white", (-12, 12), (-20, 0), (-31.5, 31.5))
    m.ybar("spoon", 0, 0, (50, 68), 12)
    m.box("spoon", (-7, 7), (-60, 66), (31, 37), rot=("x", -5, (0, 62, 31)))
    m.box("pin", (10, 24), (60, 62), (-2, 2))
    m.box("pin", (24, 44), (48, 74), (-1, 1))
    emit(m, kind="small")
    item_def("m18")


def item_defs():
    for name in ("glock17", "deagle", "mp5", "m4a1", "ak47", "r870", "m4super90", "awm", "m82", "javelin"):
        item_def(name)
    # the RPG shows its rocket only while loaded (flag 0 of custom_model_data set = empty)
    write(os.path.join(A, "items", "rpg7.json"), {"model": {
        "type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0,
        "on_true": {"type": "minecraft:model", "model": "arsenal:item/rpg7_empty"},
        "on_false": {"type": "minecraft:model", "model": "arsenal:item/rpg7"}}})


def geometry_java():
    lines = []
    for enum in ("GLOCK_17", "DESERT_EAGLE", "MP5", "M4A1", "AK47", "REMINGTON_870", "BENELLI_M4", "AWM", "BARRETT_M82", "RPG7", "JAVELIN"):
        s, e, mz, b = GEOM[enum]
        lines.append(f"\t\t\tcase {enum} -> new GunGeometry({s:.4f}, {e:.4f}, {mz:.4f}, {b:.4f});")
    src = open(GEOMETRY).read()
    start = src.index("\t\treturn switch (type) {") + len("\t\treturn switch (type) {\n")
    end = src.index("\t\t};", start)
    src = src[:start] + "\n".join(lines) + "\n" + src[end:]
    open(GEOMETRY, "w").write(src)


def main():
    palette_png(os.path.join(A, "textures/item/gun_palette.png"))
    glock()
    deagle()
    mp5()
    m4a1()
    ak47()
    r870()
    m4super90()
    awm()
    m82()
    rpg7(True)
    rpg7(False)
    javelin()
    rockets()
    grenades()
    item_defs()
    geometry_java()
    print("guns written")


if __name__ == "__main__":
    main()
