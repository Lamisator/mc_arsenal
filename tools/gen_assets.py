#!/usr/bin/env python3
"""
Every other resource of the mod: item and block textures (drawn pixel by pixel), block models and states, armour
layers, particles, the scope and launcher sight pictures, the language file, sounds.json, recipes, loot tables,
tags and damage types. Gun models come from gen_guns.py, sounds from gen_sounds.py.
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "src/main/resources")
A = os.path.join(RES, "assets/arsenal")
D = os.path.join(RES, "data/arsenal")
MC = os.path.join(RES, "data/minecraft")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save(img, rel):
    p = os.path.join(A, "textures", rel + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


class Pix:
    """A small canvas for pixel art."""

    def __init__(self, w=16, h=16):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.w, self.h = w, h

    def p(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img.putpixel((int(x), int(y)), c)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.p(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        n = max(abs(x1 - x0), abs(y1 - y0)) + 1
        for i in range(n):
            t = i / max(1, n - 1)
            self.p(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c)

    def outline(self, c):
        """Dark outline around everything drawn, like vanilla items."""
        src = self.img.copy()
        for y in range(self.h):
            for x in range(self.w):
                if src.getpixel((x, y))[3] == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < self.w and 0 <= ny < self.h and src.getpixel((nx, ny))[3] > 0:
                            self.img.putpixel((x, y), c)
                            break

    def noise(self, amount, seed=0):
        r = random.Random(seed)
        for y in range(self.h):
            for x in range(self.w):
                c = self.img.getpixel((x, y))
                if c[3] > 0:
                    d = r.uniform(1 - amount, 1 + amount)
                    self.img.putpixel((x, y), shade(c, d))


OUT = rgb(0x1A1A1A)
BRASS = rgb(0xD4A848)
BRASS_D = rgb(0x9C7428)
BRASS_L = rgb(0xF0D080)
COPPER = rgb(0xB86A38)
COPPER_L = rgb(0xE09060)
STEEL = rgb(0x9A9CA2)
STEEL_D = rgb(0x5C5E62)
BLACK = rgb(0x262628)
OLIVE = rgb(0x56603A)
OLIVE_D = rgb(0x3C4428)
OLIVE_L = rgb(0x74805A)
TAN = rgb(0xB49A6E)
TAN_D = rgb(0x8A7450)
COYOTE = rgb(0x84684A)


# ---------------------------------------------------------------- ammunition icons
def cartridge(c, x, y, length, body=BRASS, tip=COPPER, neck=0, wide=2):
    """A cartridge standing up: case from y up to y-length, bullet on top."""
    case = length - 3
    for i in range(case):
        for w in range(wide):
            col = body if w > 0 or wide == 1 else shade(body, 1.25)
            c.p(x + w, y - i, col)
    c.p(x, y, shade(body, 0.7))
    if wide > 1:
        c.p(x + wide - 1, y, shade(body, 0.7))
    top = y - case
    for i in range(3):
        for w in range(max(1, wide - (1 if i == 2 else 0))):
            c.p(x + w, top - i, tip if w > 0 else shade(tip, 1.25))


def ammo_icons():
    c = Pix()
    for i, x in enumerate((3, 7, 11)):
        cartridge(c, x, 13 - (i % 2), 7, wide=2)
    c.outline(OUT)
    save(c.img, "item/ammo_9mm")

    c = Pix()
    for i, x in enumerate((4, 9)):
        cartridge(c, x, 13 - i, 9, wide=3, tip=rgb(0xB0B0B0))
    c.outline(OUT)
    save(c.img, "item/ammo_50ae")

    c = Pix()
    for i, x in enumerate((2, 6, 10)):
        cartridge(c, x, 14 - (i % 2), 12, wide=2)
    c.outline(OUT)
    save(c.img, "item/ammo_556")

    c = Pix()
    lac = rgb(0x5E6A3A)
    for i, x in enumerate((3, 8)):
        cartridge(c, x, 14 - i, 11, body=lac, tip=COPPER, wide=3)
    c.outline(OUT)
    save(c.img, "item/ammo_762")

    for name, hull in (("shell_buckshot", rgb(0xB82828)), ("shell_slug", rgb(0x2E6A34))):
        c = Pix()
        for i, x in enumerate((3, 9)):
            yb = 14 - i
            c.rect(x, yb - 3, x + 3, yb, BRASS)
            c.rect(x, yb - 3, x, yb, BRASS_L)
            c.rect(x, yb - 10, x + 3, yb - 4, hull)
            c.rect(x, yb - 10, x, yb - 4, shade(hull, 1.3))
            c.rect(x, yb - 10, x + 3, yb - 10, shade(hull, 0.7))
        c.outline(OUT)
        save(c.img, "item/" + name)

    c = Pix()
    for i, x in enumerate((4, 9)):
        cartridge(c, x, 15 - i, 14, wide=3)
    c.outline(OUT)
    save(c.img, "item/ammo_338")

    c = Pix()
    cartridge(c, 7, 15, 15, wide=3, tip=rgb(0x303030))
    c.p(8, 1, rgb(0xC0C0C0))
    c.outline(OUT)
    save(c.img, "item/ammo_50bmg")

    c = Pix()
    c.rect(6, 4, 9, 13, BRASS)
    c.rect(6, 4, 6, 13, BRASS_L)
    c.rect(7, 3, 8, 3, BRASS_D)
    c.rect(5, 13, 10, 14, BRASS_D)
    c.outline(OUT)
    save(c.img, "item/brass_casing")


# ---------------------------------------------------------------- equipment icons
def gear_icons():
    # Kevlar vest: soft olive vest with shoulder straps and a velcro flap
    c = Pix()
    c.rect(3, 4, 12, 14, OLIVE)
    c.rect(3, 2, 5, 4, OLIVE)
    c.rect(10, 2, 12, 4, OLIVE)
    c.rect(6, 4, 9, 6, (0, 0, 0, 0))
    c.rect(7, 6, 8, 7, (0, 0, 0, 0))
    c.rect(4, 9, 11, 9, OLIVE_D)
    c.rect(5, 11, 10, 13, OLIVE_D)
    c.rect(3, 4, 3, 14, OLIVE_L)
    c.noise(0.06, 3)
    c.outline(OUT)
    save(c.img, "item/kevlar_vest")

    # Plate carrier: coyote brown with magazine pouches
    c = Pix()
    c.rect(3, 4, 12, 14, COYOTE)
    c.rect(3, 2, 5, 4, COYOTE)
    c.rect(10, 2, 12, 4, COYOTE)
    c.rect(6, 4, 9, 5, (0, 0, 0, 0))
    c.rect(4, 6, 11, 8, shade(COYOTE, 0.8))
    for x in (4, 7, 10):
        c.rect(x, 9, x + 1, 13, shade(COYOTE, 1.15))
        c.p(x, 9, shade(COYOTE, 0.6))
    c.noise(0.05, 4)
    c.outline(OUT)
    save(c.img, "item/plate_carrier")

    # Combat helmet with an NVG shroud
    c = Pix()
    for y in range(4, 12):
        half = int(6 * math.sqrt(max(0, 1 - ((y - 11) / 7.5) ** 2))) + 1
        c.rect(8 - half, y, 7 + half, y, OLIVE)
    c.rect(2, 11, 13, 12, OLIVE_D)
    c.rect(7, 5, 8, 7, BLACK)
    c.rect(4, 5, 5, 7, OLIVE_L)
    c.noise(0.05, 5)
    c.outline(OUT)
    save(c.img, "item/combat_helmet")

    # KA-BAR style combat knife (diagonal like a sword)
    c = Pix()
    blade = rgb(0x3A3C40)
    edge = rgb(0xB8BCC2)
    for i in range(9):
        x, y = 5 + i, 10 - i
        c.p(x, y, blade)
        c.p(x + 1, y, blade)
        c.p(x, y - 1, edge)
    c.p(14, 1, edge)
    c.p(13, 1, blade)
    c.line(2, 12, 6, 8, rgb(0x8A8A8A))  # guard
    c.line(3, 13, 5, 11, rgb(0x8A8A8A))
    for i in range(4):
        x, y = 1 + i, 14 - i
        c.p(x, y + 1, rgb(0x6A4428) if i % 2 else rgb(0x8A5A34))
        c.p(x + 1, y + 1, rgb(0x5A3A20))
    c.p(1, 15, STEEL_D)
    c.outline(OUT)
    save(c.img, "item/combat_knife")

    # M57 firing device ("clacker")
    c = Pix()
    c.rect(4, 6, 11, 12, OLIVE)
    c.rect(4, 6, 4, 12, OLIVE_L)
    c.rect(5, 3, 11, 5, OLIVE_D)
    c.rect(11, 2, 12, 6, OLIVE_D)
    c.rect(6, 8, 9, 8, BLACK)
    c.rect(7, 12, 8, 15, rgb(0x303030))
    c.p(8, 15, rgb(0xB82828))
    c.outline(OUT)
    save(c.img, "item/m57_detonator")

    # mine detector: search head, pole, control box, handle
    c = Pix()
    c.rect(1, 12, 6, 14, BLACK)
    c.rect(2, 12, 5, 12, rgb(0x505050))
    c.line(4, 12, 12, 4, rgb(0x707070))
    c.rect(9, 5, 11, 8, rgb(0x5E6A3A))
    c.rect(11, 2, 14, 4, BLACK)
    c.p(10, 6, rgb(0xE0C040))
    c.outline(OUT)
    save(c.img, "item/mine_detector")


def part_icons():
    c = Pix()
    c.rect(1, 7, 14, 8, STEEL_D)
    c.rect(1, 7, 14, 7, STEEL)
    c.rect(0, 6, 2, 9, BLACK)
    c.rect(13, 6, 15, 9, STEEL_D)
    c.outline(OUT)
    save(c.img, "item/gun_barrel")

    c = Pix()
    c.rect(2, 5, 13, 10, rgb(0x34363A))
    c.rect(2, 5, 13, 5, rgb(0x56585C))
    c.rect(5, 6, 9, 7, rgb(0x1C1C1E))
    c.rect(9, 10, 11, 13, rgb(0x34363A))
    c.rect(4, 3, 12, 4, rgb(0x2A2A2C))
    c.outline(OUT)
    save(c.img, "item/gun_receiver")

    c = Pix()
    r = random.Random(7)
    for i in range(14):
        x, y = r.randint(3, 11), r.randint(4, 12)
        c.rect(x, y, x + 1, y + 1, rgb(0x2A2A2C))
        c.p(x, y, rgb(0x505052))
    c.outline(OUT)
    save(c.img, "item/polymer")

    c = Pix()
    c.rect(2, 6, 13, 9, BLACK)
    c.rect(0, 5, 3, 10, BLACK)
    c.rect(12, 5, 15, 10, BLACK)
    c.rect(1, 6, 1, 9, rgb(0x2A4A7A))
    c.rect(6, 4, 8, 5, BLACK)
    c.rect(2, 6, 13, 6, rgb(0x505052))
    c.outline(OUT)
    save(c.img, "item/scope")

    c = Pix()
    c.rect(2, 4, 13, 11, rgb(0xC8B48A))
    c.rect(2, 4, 13, 4, rgb(0xE0D0A8))
    c.rect(2, 7, 13, 8, OLIVE_D)
    c.rect(6, 2, 7, 4, rgb(0xB82828))
    c.outline(OUT)
    save(c.img, "item/explosive_charge")

    c = Pix()
    y1 = rgb(0xD8B830)
    y2 = rgb(0xB89818)
    for y in range(3, 13):
        for x in range(3, 13):
            c.p(x, y, y1 if (x + y) % 2 == 0 else y2)
    c.outline(OUT)
    save(c.img, "item/kevlar_fabric")

    c = Pix()
    c.rect(3, 2, 12, 13, rgb(0x8E9094))
    c.rect(3, 2, 12, 2, rgb(0xB8BABE))
    c.rect(3, 2, 3, 13, rgb(0xB8BABE))
    c.rect(4, 12, 12, 13, rgb(0x6A6C70))
    c.outline(OUT)
    save(c.img, "item/ceramic_plate")


# ---------------------------------------------------------------- block textures
def block_textures():
    # claymore: olive drab plastic, embossed text lines on the front
    c = Pix(32, 32)
    c.rect(0, 0, 31, 31, OLIVE)
    c.noise(0.06, 11)
    lines = ["FRONT", "TOWARD", "ENEMY"]
    font = {
        "F": ["111", "100", "110", "100", "100"], "R": ["110", "101", "110", "101", "101"], "O": ["010", "101", "101", "101", "010"],
        "N": ["101", "111", "111", "111", "101"], "T": ["111", "010", "010", "010", "010"], "W": ["101", "101", "111", "111", "101"],
        "A": ["010", "101", "111", "101", "101"], "D": ["110", "101", "101", "101", "110"], "E": ["111", "100", "110", "100", "111"],
        "M": ["101", "111", "111", "101", "101"], "Y": ["101", "101", "010", "010", "010"],
    }
    for row, word in enumerate(lines):
        x0 = (32 - len(word) * 4) // 2
        for i, ch in enumerate(word):
            g = font[ch]
            for yy in range(5):
                for xx in range(3):
                    if g[yy][xx] == "1":
                        c.p(x0 + i * 4 + xx, 6 + row * 7 + yy, OLIVE_L)
    save(c.img, "block/claymore_front")
    c = Pix(16, 16)
    c.rect(0, 0, 15, 15, OLIVE)
    c.noise(0.06, 12)
    c.rect(0, 0, 15, 0, OLIVE_D)
    save(c.img, "block/claymore")
    c = Pix(16, 16)
    c.rect(0, 0, 15, 15, rgb(0x4A4A44))
    c.noise(0.08, 13)
    save(c.img, "block/claymore_leg")

    # pressure mines
    for name, base, seed in (("ap_mine", OLIVE, 14), ("at_mine", rgb(0x4A5236), 15)):
        c = Pix(16, 16)
        c.rect(0, 0, 15, 15, base)
        c.noise(0.07, seed)
        for a in range(0, 360, 30):
            x = 8 + 6 * math.cos(math.radians(a))
            y = 8 + 6 * math.sin(math.radians(a))
            c.p(x, y, shade(base, 0.75))
        save(c.img, "block/" + name)
        c = Pix(16, 16)
        c.rect(0, 0, 15, 15, shade(base, 0.8))
        c.noise(0.07, seed + 1)
        c.rect(0, 7, 15, 8, shade(base, 0.6))
        save(c.img, "block/" + name + "_side")
    c = Pix(16, 16)
    c.rect(0, 0, 15, 15, rgb(0x6A6C64))
    c.noise(0.06, 16)
    c.rect(4, 4, 11, 11, rgb(0x3E403A))
    c.rect(6, 6, 9, 9, rgb(0x8A8C84))
    save(c.img, "block/mine_fuze")

    # C4: off-white block wrapped in olive film with a label
    c = Pix(16, 16)
    c.rect(0, 0, 15, 15, rgb(0xC9BC98))
    c.noise(0.04, 17)
    c.rect(0, 0, 15, 2, OLIVE)
    c.rect(0, 13, 15, 15, OLIVE)
    c.rect(3, 5, 12, 10, rgb(0xE8E0C8))
    for x in range(4, 12, 2):
        c.p(x, 7, rgb(0x3A3A3A))
    c.rect(4, 9, 9, 9, rgb(0x3A3A3A))
    save(c.img, "block/c4")
    c = Pix(16, 16)
    c.rect(0, 0, 15, 15, rgb(0x9A9C98))
    c.rect(0, 0, 15, 3, rgb(0x5A5C58))
    save(c.img, "block/c4_cap")
    c = Pix(16, 16)
    for x in range(16):
        c.p(x, 4, rgb(0xC02020))
        c.p(x, 11, rgb(0x20A040))
    save(c.img, "block/c4_wire")

    # ammo crate: olive green planks, yellow band, stencil
    c = Pix(16, 16)
    for y in range(16):
        for x in range(16):
            v = 1.0 + 0.06 * math.sin(x * 1.7 + y * 0.3) - (0.18 if y % 4 == 3 else 0)
            c.p(x, y, shade(rgb(0x4E5A34), v))
    c.noise(0.04, 18)
    save(c.img, "block/ammo_crate_side")
    c = Pix(16, 16)
    c.img = Image.open(os.path.join(A, "textures/block/ammo_crate_side.png")).convert("RGBA")
    c.rect(0, 6, 15, 7, rgb(0xC8A830))
    for i, col in enumerate("AMMO"):
        pass
    stencil = ["0100110110110010", "1011101101101101", "1111101101101101", "1011101101101101", "1010101101100010"]
    for yy, row in enumerate(stencil):
        for xx, ch in enumerate(row):
            if ch == "1":
                c.p(xx, 9 + yy, rgb(0xD8D0B0))
    save(c.img, "block/ammo_crate_front")
    c = Pix(16, 16)
    c.img = Image.open(os.path.join(A, "textures/block/ammo_crate_side.png")).convert("RGBA")
    c.rect(0, 0, 15, 0, rgb(0x343C22))
    c.rect(0, 15, 15, 15, rgb(0x343C22))
    c.rect(7, 2, 8, 13, rgb(0x2A2A2A))
    save(c.img, "block/ammo_crate_top")

    # gun rack: dark powder-coated steel
    c = Pix(16, 16)
    c.rect(0, 0, 15, 15, rgb(0x3A3C40))
    c.noise(0.06, 19)
    c.rect(0, 0, 15, 0, rgb(0x55575C))
    save(c.img, "block/gun_rack")
    c = Pix(16, 16)
    c.rect(0, 0, 15, 15, rgb(0x7A5A32))
    for y in range(16):
        for x in range(16):
            c.p(x, y, shade(rgb(0x7A5A32), 1 + 0.08 * math.sin(y * 2.1 + x * 0.2)))
    c.noise(0.04, 20)
    save(c.img, "block/rubber_pad")


# ---------------------------------------------------------------- armour on the body (64x32 humanoid layer)
def armor_layers():
    def body(img, base, dark, light, seed, pouches=False, plates=False):
        c = Pix(64, 32)
        c.img = img
        r = random.Random(seed)

        def fill(x0, y0, x1, y1, col):
            for y in range(y0, y1):
                for x in range(x0, x1):
                    c.p(x, y, shade(col, r.uniform(0.94, 1.06)))
        # body: front 20..28 x 20..32, back 32..40, sides 16..20 and 28..32, top 20..28 x 16..20, bottom 28..36 x 16..20
        fill(20, 20, 28, 32, base)
        fill(32, 20, 40, 32, base)
        fill(16, 20, 20, 32, dark)
        fill(28, 20, 32, 32, dark)
        fill(20, 16, 28, 20, base)
        fill(28, 16, 36, 20, dark)
        # collar opening and shoulder straps on the front
        for x in range(22, 26):
            c.p(x, 20, (0, 0, 0, 0))
        for y in range(20, 32):
            c.p(20, y, light)
            c.p(27, y, shade(base, 0.85))
        if pouches:
            for x0 in (21, 24):
                for y in range(25, 30):
                    for x in range(x0, x0 + 3):
                        c.p(x, y, shade(base, 1.12))
                for x in range(x0, x0 + 3):
                    c.p(x, 25, dark)
            for x in range(20, 28):
                c.p(x, 30, dark)
        if plates:
            for x in range(21, 27):
                c.p(x, 22, shade(base, 0.8))
        # a band of webbing round the waist
        for x in list(range(16, 40)):
            c.p(x, 31, shade(dark, 0.8))
        return c.img

    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    save(body(img, OLIVE, OLIVE_D, OLIVE_L, 21), "entity/equipment/humanoid/kevlar_vest")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    save(body(img, COYOTE, shade(COYOTE, 0.75), shade(COYOTE, 1.2), 22, pouches=True, plates=True), "entity/equipment/humanoid/plate_carrier")

    # helmet: head 0..32 x 0..16: top 8..16 x 0..8, front 8..16 x 8..16, right 0..8, left 16..24, back 24..32
    c = Pix(64, 32)
    r = random.Random(23)

    def fill(x0, y0, x1, y1, col):
        for y in range(y0, y1):
            for x in range(x0, x1):
                c.p(x, y, shade(col, r.uniform(0.93, 1.07)))
    fill(8, 0, 16, 8, OLIVE)
    fill(8, 8, 16, 11, OLIVE)  # forehead only: the face stays free
    fill(0, 8, 8, 13, OLIVE)
    fill(16, 8, 24, 13, OLIVE)
    fill(24, 8, 32, 14, OLIVE)
    for x in range(8, 16):
        c.p(x, 10, OLIVE_D)
    c.rect(11, 8, 12, 9, BLACK)  # NVG mount
    for x in (0, 16):
        c.rect(x + 2, 12, x + 5, 12, BLACK)  # chin strap
    save(c.img, "entity/equipment/humanoid/combat_helmet")


# ---------------------------------------------------------------- particles and overlays
def particle_textures():
    s = 16
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for a in range(0, 360, 45):
        r = 7.5 if a % 90 == 0 else 5
        x = 7.5 + r * math.cos(math.radians(a))
        y = 7.5 + r * math.sin(math.radians(a))
        d.polygon([(7.5, 7.5), (7.5 + 1.6 * math.cos(math.radians(a + 90)), 7.5 + 1.6 * math.sin(math.radians(a + 90))), (x, y),
                   (7.5 + 1.6 * math.cos(math.radians(a - 90)), 7.5 + 1.6 * math.sin(math.radians(a - 90)))], fill=(255, 255, 255, 230))
    d.ellipse((4, 4, 11, 11), fill=(255, 255, 255, 255))
    save(img, "particle/muzzle_flash")

    for i in range(4):
        img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        r = random.Random(30 + i)
        px = img.load()
        for y in range(s):
            for x in range(s):
                dx, dy = (x - 7.5) / 7.5, (y - 7.5) / 7.5
                dist = math.sqrt(dx * dx + dy * dy)
                a = max(0, 1 - dist) ** 0.8 * r.uniform(0.6, 1.0)
                px[x, y] = (255, 255, 255, int(255 * min(1, a * 1.3)))
        img = img.filter(ImageFilter.GaussianBlur(0.6))
        save(img, f"particle/smoke_{i}")

    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((2, 1, 5, 6), fill=(255, 255, 255, 255))
    save(img, "particle/casing")

    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((1, 1, 6, 6), fill=(255, 255, 255, 160))
    d.ellipse((2, 2, 5, 5), fill=(255, 255, 255, 255))
    save(img, "particle/spark")

    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((1, 2, 6, 7), fill=(255, 255, 255, 255))
    d.polygon([(3.5, 0), (1.5, 3), (5.5, 3)], fill=(255, 255, 255, 255))
    save(img, "particle/blood")

    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    for y in range(32):
        for x in range(32):
            dist = math.sqrt((x - 15.5) ** 2 + (y - 15.5) ** 2) / 15.5
            px[x, y] = (255, 255, 255, int(255 * max(0, 1 - dist) ** 1.5))
    save(img, "particle/flash")


def misc_textures():
    img = Image.new("RGBA", (16, 4), (0, 0, 0, 0))
    px = img.load()
    for x in range(16):
        for y in range(4):
            edge = 1.0 if y in (1, 2) else 0.35
            px[x, y] = (255, 255, 255, int(255 * edge))
    save(img, "misc/tracer")

    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = random.Random(40)
    for i in range(7):
        a = r.uniform(0, math.pi * 2)
        l = r.uniform(7, 13)
        d.line((16, 16, 16 + l * math.cos(a), 16 + l * math.sin(a)), fill=(20, 18, 16, 150), width=1)
    d.ellipse((9, 9, 23, 23), fill=(40, 36, 32, 140))
    d.ellipse((12, 12, 20, 20), fill=(8, 8, 8, 245))
    save(img, "misc/bullet_hole")

    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((11, 11, 21, 21), fill=(200, 200, 205, 160))
    d.ellipse((13, 13, 19, 19), fill=(90, 90, 95, 230))
    save(img, "misc/bullet_mark")

    size = 512
    c = size // 2
    # rifle scope: mil-dot reticle in a black ring
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(size):
        for x in range(size):
            dist = math.sqrt((x - c + 0.5) ** 2 + (y - c + 0.5) ** 2) / c
            if dist > 0.98:
                px[x, y] = (0, 0, 0, 255)
            elif dist > 0.80:
                px[x, y] = (0, 0, 0, int(255 * min(1, (dist - 0.80) / 0.18) ** 0.7))
    d = ImageDraw.Draw(img)
    k = (0, 0, 0, 255)
    d.rectangle((c - 1, 10, c + 1, size - 10), fill=k)
    d.rectangle((10, c - 1, size - 10, c + 1), fill=k)
    d.rectangle((c - 4, 10, c + 4, c - 110), fill=k)
    d.rectangle((c - 4, c + 110, c + 4, size - 10), fill=k)
    d.rectangle((10, c - 4, c - 110, c + 4), fill=k)
    d.rectangle((c + 110, c - 4, size - 10, c + 4), fill=k)
    for i in range(1, 6):
        o = i * 20
        for dx, dy in ((o, 0), (-o, 0), (0, o), (0, -o)):
            d.ellipse((c + dx - 3, c + dy - 3, c + dx + 3, c + dy + 3), fill=k)
    save(img, "misc/scope")

    # PGO-7: the RPG's rangefinding chevrons and stadia
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(size):
        for x in range(size):
            dist = math.sqrt((x - c + 0.5) ** 2 + (y - c + 0.5) ** 2) / c
            if dist > 0.97:
                px[x, y] = (0, 0, 0, 255)
            elif dist > 0.86:
                px[x, y] = (0, 0, 0, int(255 * ((dist - 0.86) / 0.11)))
    d = ImageDraw.Draw(img)
    k = (10, 10, 10, 255)
    for i, lbl in enumerate((2, 3, 4, 5)):
        y = c + i * 34
        d.polygon([(c - 12, y - 10), (c, y), (c + 12, y - 10), (c + 12, y - 6), (c, y + 4), (c - 12, y - 6)], fill=k)
        d.text((c + 22, y - 12), str(lbl), fill=k)
    d.line((c - 160, c - 40, c + 160, c - 40), fill=k, width=2)
    for i in range(-4, 5):
        d.line((c + i * 36, c - 46, c + i * 36, c - 34), fill=k, width=2)
    d.rectangle((c - 60, c + 150, c + 60, c + 170), outline=k, width=2)
    save(img, "misc/pgo7")

    # Javelin CLU: rectangular field of view with the seeker's brackets
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(size):
        for x in range(size):
            ex = abs(x - c + 0.5) / (c * 0.96)
            ey = abs(y - c + 0.5) / (c * 0.72)
            e = max(ex, ey)
            if e > 1.0:
                px[x, y] = (0, 0, 0, 255)
            elif e > 0.9:
                px[x, y] = (0, 0, 0, int(255 * (e - 0.9) / 0.1))
    d = ImageDraw.Draw(img)
    g = (80, 255, 80, 255)
    d.line((c - 30, c, c - 8, c), fill=g, width=2)
    d.line((c + 8, c, c + 30, c), fill=g, width=2)
    d.line((c, c - 30, c, c - 8), fill=g, width=2)
    d.line((c, c + 8, c, c + 30), fill=g, width=2)
    for sx in (-1, 1):
        for sy in (-1, 1):
            x0, y0 = c + sx * 150, c + sy * 110
            d.line((x0, y0, x0 - sx * 30, y0), fill=g, width=2)
            d.line((x0, y0, x0, y0 - sy * 30), fill=g, width=2)
    save(img, "misc/clu")


def icon():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((4, 4, 124, 124), fill=(54, 62, 40, 255), outline=(26, 30, 18, 255), width=4)
    d.ellipse((14, 14, 114, 114), outline=(120, 132, 90, 255), width=2)
    # a rifle silhouette across the badge
    k = (18, 18, 20, 255)
    d.rectangle((18, 58, 96, 66), fill=k)  # receiver and barrel line
    d.rectangle((96, 60, 116, 63), fill=k)
    d.polygon([(18, 58), (8, 62), (10, 76), (22, 70)], fill=k)  # stock
    d.polygon([(52, 66), (60, 66), (66, 88), (58, 90)], fill=k)  # magazine
    d.polygon([(40, 66), (46, 66), (42, 80), (36, 80)], fill=k)  # grip
    d.rectangle((62, 50, 76, 58), fill=k)  # optic
    d.text((40, 96), "ARSENAL", fill=(220, 210, 160, 255))
    p = os.path.join(A, "icon.png")
    img.save(p)


# ---------------------------------------------------------------- block models
def bm(name, obj):
    write(os.path.join(A, "models/block", name + ".json"), obj)


def faces(tex, uvs=None):
    f = {}
    for side in ("north", "south", "east", "west", "up", "down"):
        f[side] = {"texture": tex}
        if uvs and side in uvs:
            f[side]["uv"] = uvs[side]
    return f


def el(frm, to, tex, rot=None, uvs=None, tex_by_face=None):
    fcs = faces(tex, uvs)
    if tex_by_face:
        for k, v in tex_by_face.items():
            fcs[k]["texture"] = v
    e = {"from": frm, "to": to, "faces": fcs}
    if rot:
        e["rotation"] = rot
    return e


def block_models():
    # claymore, facing north (the front is the north face, legs below)
    bm("claymore", {"parent": "minecraft:block/block", "textures": {"particle": "arsenal:block/claymore", "body": "arsenal:block/claymore",
       "front": "arsenal:block/claymore_front", "leg": "arsenal:block/claymore_leg"}, "elements": [
        el([3.6, 2, 6.8], [8.1, 8, 9.2], "#body", rot={"origin": [8, 5, 8], "axis": "y", "angle": -12},
           tex_by_face={"north": "#front"}, uvs={"north": [8, 4, 16, 12]}),
        el([7.9, 2, 6.8], [12.4, 8, 9.2], "#body", rot={"origin": [8, 5, 8], "axis": "y", "angle": 12},
           tex_by_face={"north": "#front"}, uvs={"north": [0, 4, 8, 12]}),
        el([7.2, 8, 7.5], [8.8, 9, 8.5], "#leg"),
        el([4, 0, 7.4], [4.8, 2.2, 8.2], "#leg", rot={"origin": [4.4, 1, 7.8], "axis": "z", "angle": -15}),
        el([11.2, 0, 7.4], [12, 2.2, 8.2], "#leg", rot={"origin": [11.6, 1, 7.8], "axis": "z", "angle": 15}),
        el([4, 0, 8.6], [4.8, 2.2, 9.4], "#leg", rot={"origin": [4.4, 1, 9], "axis": "x", "angle": 15}),
        el([11.2, 0, 8.6], [12, 2.2, 9.4], "#leg", rot={"origin": [11.6, 1, 9], "axis": "x", "angle": 15}),
    ]})
    for name, size, h in (("ap_mine", 3, 1.5), ("at_mine", 6, 3.0)):
        lo, hi = 8 - size, 8 + size
        k = size * 0.82
        bm(name, {"parent": "minecraft:block/block", "textures": {"particle": f"arsenal:block/{name}", "top": f"arsenal:block/{name}",
           "side": f"arsenal:block/{name}_side", "fuze": "arsenal:block/mine_fuze"}, "elements": [
            el([lo, 0, lo], [hi, h, hi], "#side", tex_by_face={"up": "#top", "down": "#top"}),
            el([8 - k, 0, 8 - k], [8 + k, h, 8 + k], "#side", rot={"origin": [8, 0, 8], "axis": "y", "angle": 45},
               tex_by_face={"up": "#top", "down": "#top"}),
            el([8 - size * 0.4, h, 8 - size * 0.4], [8 + size * 0.4, h + 0.5, 8 + size * 0.4], "#fuze"),
        ]})
    bm("c4", {"parent": "minecraft:block/block", "textures": {"particle": "arsenal:block/c4", "c4": "arsenal:block/c4", "cap": "arsenal:block/c4_cap",
       "wire": "arsenal:block/c4_wire"}, "elements": [
        el([3, 0, 5], [13, 3, 11], "#c4"),
        el([7, 3, 7], [8, 4, 10], "#cap"),
        el([7.3, 3.5, 2], [7.7, 3.9, 7], "#wire"),
        el([8.1, 3.4, 1], [8.5, 3.8, 7], "#wire"),
    ]})
    # wall rack, facing north: board against the south wall, three rows of pegs
    rack = [el([1, 0.5, 14], [15, 15.5, 16], "#wood")]
    for i in range(3):
        y = 8 + (0.3 - i * 0.3) * 16
        for x in (3, 12):
            rack.append(el([x, y - 2.4, 9.5], [x + 1, y - 1.2, 14], "#wood"))
            rack.append(el([x, y - 1.2, 9.5], [x + 1, y + 0.2, 10.5], "#wood"))
    bm("weapon_rack", {"parent": "minecraft:block/block", "textures": {"particle": "minecraft:block/dark_oak_planks", "wood": "minecraft:block/dark_oak_planks"},
       "elements": rack})
    # floor stand, facing north
    stand = [
        el([0, 0, 3], [16, 2, 13], "#metal"),
        el([0, 2, 6], [2, 15, 10], "#metal"),
        el([14, 2, 6], [16, 15, 10], "#metal"),
        el([0, 12, 9], [16, 14, 10], "#metal"),
        el([1, 2, 4], [15, 3, 12], "#pad"),
    ]
    for i in range(6):
        x = 0.4 + i * 3.04
        stand.append(el([x, 12, 6], [x + 0.6, 14, 9], "#metal"))
    bm("gun_rack", {"parent": "minecraft:block/block", "textures": {"particle": "arsenal:block/gun_rack", "metal": "arsenal:block/gun_rack",
       "pad": "arsenal:block/rubber_pad"}, "elements": stand})
    bm("ammo_crate", {"parent": "minecraft:block/block", "textures": {"particle": "arsenal:block/ammo_crate_side", "side": "arsenal:block/ammo_crate_side",
       "front": "arsenal:block/ammo_crate_front", "top": "arsenal:block/ammo_crate_top"}, "elements": [
        el([1, 0, 3], [15, 9, 13], "#side", tex_by_face={"north": "#front", "south": "#front", "up": "#top"}),
        el([0.6, 9, 2.6], [15.4, 10, 13.4], "#side", tex_by_face={"up": "#top"}),
        el([0, 5, 6], [1, 6, 10], "#top"),
        el([15, 5, 6], [16, 6, 10], "#top"),
    ]})

    def horiz(name, extra=None):
        v = {}
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for key in ([""] if not extra else extra):
                variant = f"facing={f}" + key
                v[variant] = {"model": f"arsenal:block/{name}", "y": y} if y else {"model": f"arsenal:block/{name}"}
        write(os.path.join(A, "blockstates", name + ".json"), {"variants": v})

    horiz("claymore")
    horiz("weapon_rack")
    horiz("gun_rack")
    horiz("ammo_crate")
    for name in ("ap_mine", "at_mine"):
        write(os.path.join(A, "blockstates", name + ".json"), {"variants": {"": {"model": f"arsenal:block/{name}"}}})
    rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}
    write(os.path.join(A, "blockstates", "c4.json"), {"variants": {f"facing={f}": {"model": "arsenal:block/c4", **r} for f, r in rot.items()}})

    for name in ("claymore", "ap_mine", "at_mine", "c4", "weapon_rack", "gun_rack", "ammo_crate"):
        write(os.path.join(A, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": f"arsenal:block/{name}"}})


def item_models():
    flat = ["ammo_9mm", "ammo_50ae", "ammo_556", "ammo_762", "shell_buckshot", "shell_slug", "ammo_338", "ammo_50bmg", "brass_casing",
            "kevlar_vest", "plate_carrier", "combat_helmet", "m57_detonator", "mine_detector", "gun_barrel", "gun_receiver", "polymer",
            "scope", "explosive_charge", "kevlar_fabric", "ceramic_plate"]
    for name in flat:
        write(os.path.join(A, "models/item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"arsenal:item/{name}"}})
        write(os.path.join(A, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": f"arsenal:item/{name}"}})
    write(os.path.join(A, "models/item", "combat_knife.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": "arsenal:item/combat_knife"}})
    write(os.path.join(A, "items", "combat_knife.json"), {"model": {"type": "minecraft:model", "model": "arsenal:item/combat_knife"}})
    for name in ("kevlar_vest", "plate_carrier", "combat_helmet"):
        write(os.path.join(A, "equipment", name + ".json"), {"layers": {"humanoid": [{"texture": f"arsenal:{name}"}]}})


def particles_json():
    smoke = [f"arsenal:smoke_{i}" for i in range(4)]
    for name, tex in (("muzzle_flash", ["arsenal:muzzle_flash"]), ("gun_smoke", smoke), ("casing", ["arsenal:casing"]), ("spark", ["arsenal:spark"]),
                      ("blood", ["arsenal:blood"]), ("smoke_screen", smoke), ("rocket_smoke", smoke), ("rocket_flame", ["arsenal:flash"]),
                      ("flash", ["arsenal:flash"])):
        write(os.path.join(A, "particles", name + ".json"), {"textures": tex})


# ---------------------------------------------------------------- sounds.json
GUN_IDS = {"glock17": "Glock 17", "deagle": "Desert Eagle", "mp5": "MP5A3", "m4a1": "M4A1", "ak47": "AK-47", "r870": "Remington 870",
           "m4super90": "Benelli M4", "awm": "AWM", "m82": "Barrett M82A1", "rpg7": "RPG-7", "javelin": "FGM-148 Javelin"}
LAUNCHERS = ("rpg7", "javelin")
SOUND_SUBTITLES = {
    "gun.dry_fire": "Gun clicks empty", "gun.mag_out": "Magazine out", "gun.mag_in": "Magazine in", "gun.charge": "Charging handle",
    "gun.slide": "Slide released", "gun.shell_insert": "Shell loaded", "gun.pump": "Shotgun pumped", "gun.bolt": "Bolt worked",
    "gun.rocket_load": "Rocket loaded", "gun.mode": "Fire selector clicks", "gun.casing": "Brass tinkles",
    "bullet.impact_hard": "Bullet strikes stone", "bullet.impact_soft": "Bullet strikes", "bullet.impact_flesh": "Bullet hits",
    "bullet.impact_metal": "Bullet strikes metal", "bullet.armor_hit": "Body armour stops a bullet", "bullet.crack": "Bullet cracks past",
    "bullet.whiz": "Bullet whizzes past", "grenade.pin": "Grenade pin pulled", "grenade.bounce": "Grenade bounces",
    "explosion.grenade": "Grenade explodes", "explosion.rocket": "Warhead explodes", "explosion.c4": "Charge explodes",
    "flashbang.bang": "Flashbang goes off", "flashbang.ring": "Ears ring", "smoke.hiss": "Smoke grenade hisses", "mine.click": "Something clicks",
    "mine.arm": "Charge armed", "detonator.click": "Firing device squeezed", "detector.beep": "Mine detector beeps",
    "rocket.loop": "Rocket roars", "javelin.seek": "Javelin seeker growls", "javelin.lock": "Javelin locked on",
}


def sounds_json():
    s = {}

    def snd(key, files, subtitle, attenuation=16, volume=1.0, stream=False):
        entries = []
        for f in files:
            e = {"name": f"arsenal:{f}"}
            if attenuation != 16:
                e["attenuation_distance"] = attenuation
            if volume != 1.0:
                e["volume"] = volume
            if stream:
                e["stream"] = True
            entries.append(e)
        s[key] = {"sounds": entries, "subtitle": f"subtitles.arsenal.{subtitle}"}

    for gid in GUN_IDS:
        key = ("launch." if gid in LAUNCHERS else "shot.") + gid
        snd(key, [f"shot/{gid}"], "launch" if gid in LAUNCHERS else "shot", attenuation=64 if gid in ("awm", "m82") else 48)
    for key in SOUND_SUBTITLES:
        folder, n = key.split(".", 1)
        variants = {"bullet.impact_hard": 3, "bullet.impact_soft": 3, "bullet.impact_flesh": 2, "bullet.impact_metal": 2, "bullet.crack": 2,
                    "bullet.whiz": 2, "grenade.bounce": 2, "gun.casing": 2}.get(key, 1)
        files = [f"{folder}/{n}" + (f"_{i}" if variants > 1 else "") for i in range(variants)]
        att = {"explosion.grenade": 48, "explosion.rocket": 64, "explosion.c4": 64, "flashbang.bang": 48, "rocket.loop": 32}.get(key, 16)
        snd(key, files, key.replace(".", "_"), attenuation=att)
    write(os.path.join(A, "sounds.json"), s)


# ---------------------------------------------------------------- language
def lang():
    L = {"itemGroup.arsenal": "Arsenal"}
    for gid, name in GUN_IDS.items():
        L[f"item.arsenal.{gid}"] = name
    L.update({
        "item.arsenal.ammo_9mm": "9×19 mm Parabellum", "item.arsenal.ammo_50ae": ".50 Action Express", "item.arsenal.ammo_556": "5.56×45 mm NATO",
        "item.arsenal.ammo_762": "7.62×39 mm", "item.arsenal.shell_buckshot": "12 Gauge 00 Buckshot", "item.arsenal.shell_slug": "12 Gauge Slug",
        "item.arsenal.ammo_338": ".338 Lapua Magnum", "item.arsenal.ammo_50bmg": ".50 BMG", "item.arsenal.pg7v": "PG-7V Rocket",
        "item.arsenal.javelin_missile": "Javelin Missile", "item.arsenal.m67": "M67 Fragmentation Grenade", "item.arsenal.m84": "M84 Stun Grenade",
        "item.arsenal.m18": "M18 Smoke Grenade", "item.arsenal.m57_detonator": "M57 Firing Device", "item.arsenal.mine_detector": "AN/PSS-14 Mine Detector",
        "item.arsenal.combat_knife": "Combat Knife", "item.arsenal.kevlar_vest": "Kevlar Vest", "item.arsenal.plate_carrier": "Plate Carrier",
        "item.arsenal.combat_helmet": "Combat Helmet", "item.arsenal.kevlar_fabric": "Kevlar Fabric", "item.arsenal.ceramic_plate": "Ceramic Plate",
        "item.arsenal.gun_barrel": "Gun Barrel", "item.arsenal.gun_receiver": "Receiver", "item.arsenal.polymer": "Polymer",
        "item.arsenal.scope": "Riflescope", "item.arsenal.explosive_charge": "Explosive Charge", "item.arsenal.brass_casing": "Brass Casing",
        "block.arsenal.claymore": "M18A1 Claymore", "block.arsenal.ap_mine": "Anti-Personnel Mine", "block.arsenal.at_mine": "TM-62 Anti-Tank Mine",
        "block.arsenal.c4": "C4 Charge", "block.arsenal.weapon_rack": "Weapon Rack", "block.arsenal.gun_rack": "Gun Stand",
        "block.arsenal.ammo_crate": "Ammunition Crate",
        "key.categories.arsenal.arsenal": "Arsenal", "key.category.arsenal.arsenal": "Arsenal",
        "key.arsenal.reload": "Reload", "key.arsenal.fire_mode": "Fire mode / shell type",
        "tooltip.arsenal.gun.ammo": "%s / %s rounds of %s", "tooltip.arsenal.gun.action": "%s, %s rounds/min, %s",
        "tooltip.arsenal.gun.shells": "Tube: %s, loading %s", "tooltip.arsenal.gun.controls": "Left click fire, right click aim, R reload, B fire mode",
        "tooltip.arsenal.javelin": "Aim and hold the target for 2 seconds to lock on; it attacks from above",
        "tooltip.arsenal.action.semi_auto": "Semi-automatic", "tooltip.arsenal.action.pump": "Pump action", "tooltip.arsenal.action.bolt": "Bolt action",
        "tooltip.arsenal.action.single": "Single shot",
        "tooltip.arsenal.mode.semi": "Semi", "tooltip.arsenal.mode.burst": "3-round burst", "tooltip.arsenal.mode.auto": "Auto",
        "tooltip.arsenal.grenade.m67": "Steel fragments out to 15 m", "tooltip.arsenal.grenade.m84": "Blinds and deafens; stuns mobs",
        "tooltip.arsenal.grenade.m18": "A minute of thick white smoke", "tooltip.arsenal.grenade.fuse": "Fuse %s s",
        "tooltip.arsenal.grenade.controls": "Hold right click to pull the pin, let go to throw (sneak: lob)",
        "tooltip.arsenal.knife": "Stab from behind for double damage. Sneak + right click a mine to disarm it",
        "tooltip.arsenal.detonator": "Wired to %s charges", "tooltip.arsenal.detonator.controls": "Right click a claymore or C4 to wire it, right click the air to fire",
        "tooltip.arsenal.mine_detector": "Beeps near mines within %s m",
        "hud.arsenal.reloading": "Reloading", "hud.arsenal.press_reload": "Press %s to reload", "hud.arsenal.buck": "Buck", "hud.arsenal.slug": "Slug",
        "message.arsenal.no_ammo": "No ammunition for this weapon", "message.arsenal.load_next": "Loading %s next",
        "message.arsenal.linked": "Charge wired up (%s on this firing device)", "message.arsenal.unlinked": "Charge disconnected (%s left)",
        "message.arsenal.no_links": "Nothing is wired to this firing device", "message.arsenal.detonated": "Charges fired: %s",
        "message.arsenal.disarmed": "Disarmed", "message.arsenal.sensor_on": "Claymore sensor on: it fires at anyone in front",
        "message.arsenal.sensor_off": "Claymore sensor off: command detonation only",
        "commands.arsenal.kit.unknown": "Unknown loadout %s (try %s)", "commands.arsenal.kit.given": "Gave the %s loadout to %s players",
        "death.attack.arsenal.bullet": "%1$s was shot by %2$s", "death.attack.arsenal.bullet.player": "%1$s was shot by %2$s",
        "death.attack.arsenal.bullet.item": "%1$s was shot by %2$s using %3$s",
        "death.attack.arsenal.fragment": "%1$s was cut down by shrapnel", "death.attack.arsenal.fragment.player": "%1$s was cut down by %2$s's shrapnel",
        "death.attack.arsenal.backblast": "%1$s stood behind a rocket launcher", "death.attack.arsenal.backblast.player": "%1$s was burnt by %2$s's backblast",
        "death.attack.arsenal.mine": "%1$s stepped on a mine", "death.attack.arsenal.mine.player": "%1$s stepped on a mine",
        "subtitles.arsenal.shot": "Gunshot", "subtitles.arsenal.launch": "Rocket launched",
    })
    for key, text in SOUND_SUBTITLES.items():
        L["subtitles.arsenal." + key.replace(".", "_")] = text
    write(os.path.join(A, "lang/en_us.json"), L)


# ---------------------------------------------------------------- data
def data():
    for name, scaling in (("bullet", "never"), ("fragment", "never"), ("backblast", "never"), ("mine", "never")):
        write(os.path.join(D, "damage_type", name + ".json"), {"message_id": f"arsenal.{name}", "exhaustion": 0.1, "scaling": scaling})
    tags = {
        "bypasses_armor": ["arsenal:bullet", "arsenal:fragment", "arsenal:mine"],
        "bypasses_cooldown": ["arsenal:bullet", "arsenal:fragment"],
        "no_knockback": ["arsenal:bullet", "arsenal:fragment"],
        "is_projectile": ["arsenal:bullet"],
        "is_explosion": ["arsenal:backblast"],
    }
    for tag, values in tags.items():
        write(os.path.join(MC, "tags/damage_type", tag + ".json"), {"replace": False, "values": values})

    write(os.path.join(D, "tags/item/repairs_kevlar.json"), {"values": ["arsenal:kevlar_fabric"]})
    write(os.path.join(D, "tags/item/repairs_plates.json"), {"values": ["arsenal:ceramic_plate"]})
    item_tags = {
        "chest_armor": ["arsenal:kevlar_vest", "arsenal:plate_carrier"], "head_armor": ["arsenal:combat_helmet"],
        "enchantable/armor": ["arsenal:kevlar_vest", "arsenal:plate_carrier", "arsenal:combat_helmet"],
        "enchantable/chest_armor": ["arsenal:kevlar_vest", "arsenal:plate_carrier"], "enchantable/head_armor": ["arsenal:combat_helmet"],
        "enchantable/equippable": ["arsenal:kevlar_vest", "arsenal:plate_carrier", "arsenal:combat_helmet"],
        "enchantable/durability": ["arsenal:kevlar_vest", "arsenal:plate_carrier", "arsenal:combat_helmet", "arsenal:combat_knife"],
        "swords": ["arsenal:combat_knife"], "enchantable/sharp_weapon": ["arsenal:combat_knife"], "enchantable/sword": ["arsenal:combat_knife"],
        "enchantable/melee_weapon": ["arsenal:combat_knife"], "enchantable/weapon": ["arsenal:combat_knife"],
    }
    for tag, values in item_tags.items():
        write(os.path.join(MC, "tags/item", tag + ".json"), {"replace": False, "values": values})

    for name in ("claymore", "ap_mine", "at_mine", "c4", "weapon_rack", "gun_rack", "ammo_crate"):
        write(os.path.join(D, "loot_table/blocks", name + ".json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": f"arsenal:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"arsenal:blocks/{name}"})

    def shaped(name, pattern, key, count=1, category="equipment"):
        write(os.path.join(D, "recipe", name + ".json"), {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                                                           "key": key, "result": {"id": f"arsenal:{name}", "count": count}})

    def shapeless(name, ingredients, count=1, category="misc"):
        write(os.path.join(D, "recipe", name + ".json"), {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients,
                                                           "result": {"id": f"arsenal:{name}", "count": count}})

    I, N, G, R, C = "minecraft:iron_ingot", "minecraft:iron_nugget", "minecraft:gunpowder", "minecraft:redstone", "minecraft:copper_ingot"
    B, RC, P, S, E = "arsenal:gun_barrel", "arsenal:gun_receiver", "arsenal:polymer", "arsenal:scope", "arsenal:explosive_charge"
    BR, F, W = "arsenal:brass_casing", "arsenal:kevlar_fabric", "#minecraft:planks"
    # parts
    shapeless("polymer", ["minecraft:coal", "minecraft:slime_ball"], 4)
    shaped("gun_barrel", ["III"], {"I": I}, category="misc")
    shaped("gun_receiver", ["INI", "IRI"], {"I": I, "N": N, "R": R}, category="misc")
    shapeless("scope", ["minecraft:spyglass", I, I])
    shapeless("brass_casing", [C, N], 8)
    shapeless("explosive_charge", [G, G, "minecraft:clay_ball"])
    shaped("kevlar_fabric", ["SSS", "SLS", "SSS"], {"S": "minecraft:string", "L": "minecraft:leather"}, 2, category="misc")
    shaped("ceramic_plate", ["BBB", "BIB"], {"B": "minecraft:brick", "I": I}, 2, category="misc")
    # ammunition
    shapeless("ammo_9mm", [BR, G, N], 16)
    shapeless("ammo_50ae", [BR, BR, G, G, N, N], 8)
    shapeless("ammo_556", [BR, G, G, N, C], 24)
    shapeless("ammo_762", [BR, G, G, N, N, I], 24)
    shapeless("shell_buckshot", ["minecraft:paper", G, N, N, N, BR], 8)
    shapeless("shell_slug", ["minecraft:paper", G, I, BR], 6)
    shapeless("ammo_338", [BR, BR, G, G, G, C], 10)
    shapeless("ammo_50bmg", [BR, BR, BR, G, G, G, G, I], 8)
    shapeless("pg7v", [E, E, G, G, I, I])
    shapeless("javelin_missile", [E, E, E, R, R, "minecraft:gold_ingot", I, I, "minecraft:copper_ingot"])
    # weapons
    shaped("glock17", ["RB", "P "], {"R": RC, "B": B, "P": P})
    shaped("deagle", ["RB", "I "], {"R": RC, "B": B, "I": I})
    shaped("mp5", ["PRB", "P  "], {"P": P, "R": RC, "B": B})
    shaped("m4a1", ["G  ", "PRB", "P  "], {"G": "minecraft:glass_pane", "P": P, "R": RC, "B": B})
    shaped("ak47", ["WRB", "W I"], {"W": W, "R": RC, "B": B, "I": I})
    shaped("r870", ["WRB", " IB"], {"W": W, "R": RC, "B": B, "I": I})
    shaped("m4super90", ["PRB", " PB"], {"P": P, "R": RC, "B": B})
    shaped("awm", ["S  ", "PRB", "P B"], {"S": S, "P": P, "R": RC, "B": B})
    shaped("m82", ["S I", "IRB", "I B"], {"S": S, "I": I, "R": RC, "B": B})
    shaped("rpg7", ["WBB", " R "], {"W": W, "B": B, "R": RC})
    shaped("javelin", ["SRE", "BBB", "PRP"], {"S": S, "R": R, "E": E, "B": B, "P": P})
    shaped("m67", [" N ", "IEI", " I "], {"N": N, "I": I, "E": E}, 2)
    shaped("m84", [" N ", "IGI", " P "], {"N": N, "I": I, "G": "minecraft:glowstone_dust", "P": G}, 2)
    shaped("m18", [" N ", "IKI", " I "], {"N": N, "I": I, "K": "minecraft:coal"}, 2)
    shaped("claymore", ["PEP", "N N"], {"P": P, "E": E, "N": N}, category="redstone")
    shaped("ap_mine", [" P ", "NEN"], {"P": P, "N": N, "E": E}, 2, category="redstone")
    shaped("at_mine", ["III", "EEE", "III"], {"I": I, "E": E}, category="redstone")
    shaped("c4", ["EC", "CE"], {"E": E, "C": "minecraft:clay_ball"}, 2, category="redstone")
    shaped("m57_detonator", ["RP", "IN"], {"R": R, "P": P, "I": I, "N": N}, category="redstone")
    shaped("mine_detector", ["R ", "I ", "IC"], {"R": R, "I": I, "C": C}, category="equipment")
    shaped("combat_knife", ["I", "L"], {"I": I, "L": "minecraft:leather"})
    shaped("kevlar_vest", ["F F", "FFF", "FFF"], {"F": F})
    shaped("plate_carrier", ["F F", "FCF", "FCF"], {"F": F, "C": "arsenal:ceramic_plate"})
    shaped("combat_helmet", ["FFF", "F F"], {"F": F})
    shaped("weapon_rack", ["SSS", "PPP"], {"S": "minecraft:stick", "P": W}, category="building")
    shaped("gun_rack", ["INI", "III"], {"I": I, "N": N}, category="building")
    shapeless("ammo_crate", ["minecraft:chest", "minecraft:green_dye", I], category="building")


def main():
    ammo_icons()
    gear_icons()
    part_icons()
    block_textures()
    armor_layers()
    particle_textures()
    misc_textures()
    icon()
    block_models()
    item_models()
    particles_json()
    sounds_json()
    lang()
    data()
    print("assets written")


if __name__ == "__main__":
    main()
