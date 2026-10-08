#!/usr/bin/env python3
"""
Synthesises every sound of the Arsenal mod with numpy (helpers in dsp.py): gunshots built from a supersonic crack,
the muzzle blast, the body of the report, the action cycling and an outdoor echo; the clicks and scrapes of
magazines, bolts and pumps; bullet impacts and fly-bys; grenades, mines, rockets and the flashbang's tinnitus.

Mono 44.1 kHz, encoded with oggenc into src/main/resources/assets/arsenal/sounds/. Deterministic (seeded by name).

Usage: python3 tools/gen_sounds.py [names...]
"""
import os
import sys
import zlib

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dsp import (SR, boom, burst, crackle, encode, env_db, filt, finalize, finalize_loop, friedlander, limiter, make_ir, modal, noise, nrm,
                 ns, place, ramp, reverb, rolls, sat_keep, tax, write_wav, _debris, _explosion_layers)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT = os.path.join(ROOT, "src/main/resources/assets/arsenal/sounds")
TMP = "/tmp/claude-1000/arsenal-sounds"


# ---------------------------------------------------------------- gunshots
def gunshot(rng, dur, *, crack=0.0, blast_t=0.004, blast=1.0, body=(110, 55, 0.04, 0.12), body_amp=0.6, noise_band=(300, 5000),
            noise_tau=14, noise_amp=0.8, mech=0.12, mech_at=0.05, rt=1.4, wet=0.35, echo=(0.35, 0.18), brake=0.0, drive=2.0):
    n = ns(dur)
    t = tax(n)
    x = np.zeros(n)
    t0 = 0.004
    if crack > 0:
        # the bullet's own shock wave: a sharp N-wave just before the blast
        place(x, burst(rng, 3, 0.45, ("hp", 2500, 2)), t0, crack)
    place(x, friedlander(blast_t, 1.0, max(0.12, blast_t * 12)), t0 + 0.0005, blast)
    place(x, burst(rng, noise_tau * 8, noise_tau, ("hp", noise_band[0], 2), ("lp", noise_band[1], 2), color="pink"), t0 + 0.0005, noise_amp)
    lo = np.zeros(n)
    place(lo, boom(min(dur, body[3] * 8), body[0], body[1], body[2], body[3], harm=(1, 0.4, 0.15)), t0, body_amp)
    x += sat_keep(lo, 1.6)
    if brake > 0:
        # a muzzle brake throws the gas sideways: a second, harsher burst
        place(x, burst(rng, 60, 9, ("hp", 600, 2), ("lp", 7000, 2)), t0 + 0.0015, brake)
    if mech > 0:
        place(x, modal(rng, 0.06, [2100, 3900, 6100], [0.012, 0.008, 0.005], [1, 0.5, 0.3], strike=0.4), t0 + mech_at, mech)
    ir = make_ir(rng, rt, predelay=0.02, hf_start=7000, hf_end=900, er=10, er_span=0.12, er_gain=3.0)
    x = reverb(x, ir, wet)
    if echo:
        d, g = echo
        e = filt(x, ("lp", 2500, 2), ("hp", 150, 2))
        place(x, e[:n - ns(d)], d, g)
    x = limiter(x * drive, -6, 0.08)
    return finalize(x, 0.0015, 0.02, tail=min(0.5, dur * 0.4))


SHOTS = {
    "glock17": lambda r: gunshot(r, 1.6, blast_t=0.0028, body=(150, 80, 0.03, 0.07), body_amp=0.45, noise_band=(500, 6500), noise_tau=10, mech=0.18,
                                 mech_at=0.035, rt=1.2, wet=0.3),
    "deagle": lambda r: gunshot(r, 2.0, blast_t=0.0045, body=(120, 55, 0.04, 0.11), body_amp=0.75, noise_band=(250, 5000), noise_tau=16, mech=0.2,
                                mech_at=0.045, rt=1.6, wet=0.35),
    "mp5": lambda r: gunshot(r, 1.3, blast_t=0.0024, body=(170, 90, 0.025, 0.06), body_amp=0.4, noise_band=(600, 7000), noise_tau=8, mech=0.22,
                             mech_at=0.03, rt=1.0, wet=0.28, echo=(0.3, 0.12)),
    "m4a1": lambda r: gunshot(r, 1.8, crack=0.8, blast_t=0.0032, body=(140, 70, 0.03, 0.08), body_amp=0.5, noise_band=(400, 8000), noise_tau=11, mech=0.15,
                              mech_at=0.03, rt=1.5, wet=0.33),
    "ak47": lambda r: gunshot(r, 2.0, crack=0.7, blast_t=0.004, body=(115, 52, 0.04, 0.11), body_amp=0.7, noise_band=(250, 6000), noise_tau=14, mech=0.2,
                              mech_at=0.04, rt=1.7, wet=0.36),
    "r870": lambda r: gunshot(r, 2.4, blast_t=0.006, body=(95, 40, 0.05, 0.16), body_amp=0.9, noise_band=(200, 4500), noise_tau=22, mech=0.0, rt=1.9,
                              wet=0.4, echo=(0.42, 0.2)),
    "m4super90": lambda r: gunshot(r, 2.3, blast_t=0.0055, body=(100, 42, 0.05, 0.15), body_amp=0.85, noise_band=(220, 4800), noise_tau=20, mech=0.16,
                                   mech_at=0.05, rt=1.8, wet=0.4, echo=(0.4, 0.2)),
    "awm": lambda r: gunshot(r, 3.0, crack=1.0, blast_t=0.006, body=(90, 38, 0.06, 0.2), body_amp=0.9, noise_band=(180, 6000), noise_tau=22, mech=0.0,
                             rt=2.6, wet=0.45, echo=(0.6, 0.28), brake=0.5),
    "m82": lambda r: gunshot(r, 3.4, crack=1.0, blast_t=0.008, body=(75, 30, 0.07, 0.26), body_amp=1.0, noise_band=(150, 5500), noise_tau=28, mech=0.3,
                             mech_at=0.07, rt=3.0, wet=0.5, echo=(0.7, 0.3), brake=0.9, drive=2.4),
}


def launch_rpg(rng):
    dur = 2.6
    n = ns(dur)
    t = tax(n)
    x = gunshot(rng, dur, blast_t=0.009, body=(80, 35, 0.06, 0.2), body_amp=0.9, noise_band=(150, 4000), noise_tau=30, mech=0.0, rt=2.0, wet=0.4,
                echo=(0.5, 0.2)) * 0.8
    # sustainer ignition and the rocket roaring away
    w = filt(noise(rng, n, "pink"), ("hp", 300, 2), ("lp", 5000, 2))
    env = env_db(t, [(0, -80), (0.08, -80), (0.12, -2), (0.4, -6), (1.2, -20), (2.6, -50)])
    x += 0.8 * w * env * rolls(rng, n, 8, 3)
    x = limiter(x, -6, 0.1)
    return finalize(x, 0.002, 0.03, tail=0.6)


def launch_javelin(rng):
    dur = 3.2
    n = ns(dur)
    t = tax(n)
    x = np.zeros(n)
    place(x, friedlander(0.006, 1.0, 0.1), 0.004, 0.7)
    place(x, burst(rng, 120, 25, ("lp", 1500, 2), color="brown"), 0.004, 0.6)
    roar = filt(noise(rng, n, "pink") + 0.6 * noise(rng, n, "brown"), ("hp", 120, 2), ("lp", 3500, 2))
    env = env_db(t, [(0, -80), (0.3, -80), (0.36, 0), (0.9, -3), (1.8, -16), (3.2, -48)])
    x += 1.0 * roar * env * rolls(rng, n, 5, 4)
    place(x, friedlander(0.01, 1.0, 0.2), 0.3, 0.6)
    ir = make_ir(rng, 2.2, predelay=0.03, hf_start=5000, hf_end=800)
    x = reverb(x, ir, 0.35)
    x = limiter(x, -6, 0.12)
    return finalize(x, 0.002, 0.03, tail=0.6)


# ---------------------------------------------------------------- mechanics
def clack(rng, f, amp=1.0, dur=0.08, damp=1.0):
    return amp * modal(rng, dur, [f, f * 1.83, f * 2.71, f * 4.1], [0.02 * damp, 0.012 * damp, 0.008 * damp, 0.004 * damp], [1, 0.6, 0.4, 0.2], strike=0.5)


def scrape(rng, dur, lo=1500, hi=6000, amp=0.4):
    n = ns(dur)
    t = tax(n)
    x = filt(noise(rng, n), ("hp", lo, 2), ("lp", hi, 2))
    x *= np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 0.7 * rolls(rng, n, 30, 6)
    return amp * x


def mech(parts, dur):
    n = ns(dur)
    x = np.zeros(n)
    for sig, at, g in parts:
        place(x, sig, at, g)
    return finalize(x, 0.002, 0.01)


def snd_dry_fire(r):
    return mech([(clack(r, 2600, dur=0.05, damp=0.6), 0.005, 1.0), (clack(r, 1500, dur=0.04, damp=0.5), 0.012, 0.4)], 0.12)


def snd_mag_out(r):
    return mech([(clack(r, 1900, dur=0.06), 0.01, 0.8), (scrape(r, 0.18, 900, 4500), 0.03, 0.8), (clack(r, 900, dur=0.1, damp=1.5), 0.22, 0.4)], 0.4)


def snd_mag_in(r):
    return mech([(scrape(r, 0.12, 900, 4500), 0.0, 0.6), (clack(r, 1400, dur=0.1), 0.13, 1.0), (clack(r, 2600, dur=0.05), 0.135, 0.6)], 0.32)


def snd_charge(r):
    return mech([(scrape(r, 0.12, 1200, 6000), 0.0, 0.7), (clack(r, 2100, dur=0.06), 0.12, 0.6), (clack(r, 1300, dur=0.12, damp=1.4), 0.2, 1.0),
                 (clack(r, 3000, dur=0.04), 0.205, 0.5)], 0.38)


def snd_slide(r):
    return mech([(scrape(r, 0.05, 1500, 7000), 0.0, 0.5), (clack(r, 1700, dur=0.1, damp=1.2), 0.05, 1.0), (clack(r, 3100, dur=0.05), 0.055, 0.5)], 0.22)


def snd_shell_insert(r):
    return mech([(scrape(r, 0.08, 600, 3500, 0.5), 0.0, 0.6), (clack(r, 1100, dur=0.07, damp=0.8), 0.08, 0.9), (clack(r, 2400, dur=0.04), 0.085, 0.4)],
                0.2)


def snd_pump(r):
    return mech([(scrape(r, 0.1, 700, 4000), 0.0, 0.8), (clack(r, 900, dur=0.12, damp=1.5), 0.1, 1.0), (scrape(r, 0.09, 700, 4000), 0.17, 0.7),
                 (clack(r, 1200, dur=0.12, damp=1.4), 0.26, 1.0)], 0.45)


def snd_bolt(r):
    return mech([(clack(r, 1800, dur=0.06), 0.0, 0.7), (scrape(r, 0.12, 1000, 5000), 0.05, 0.8), (clack(r, 1300, dur=0.08), 0.17, 0.6),
                 (scrape(r, 0.11, 1000, 5000), 0.3, 0.8), (clack(r, 1500, dur=0.1, damp=1.3), 0.42, 1.0), (clack(r, 2200, dur=0.06), 0.52, 0.8)], 0.7)


def snd_rocket_load(r):
    return mech([(scrape(r, 0.35, 300, 2500, 0.6), 0.0, 0.8), (clack(r, 500, dur=0.2, damp=2.5), 0.36, 1.0), (clack(r, 1400, dur=0.06), 0.37, 0.4)],
                0.7)


def snd_mode(r):
    return mech([(clack(r, 3400, dur=0.03, damp=0.4), 0.0, 1.0)], 0.06)


def snd_casing(r):
    x = np.zeros(ns(0.6))
    t = 0.0
    for i in range(4):
        f = r.uniform(5200, 7200)
        ping = modal(r, 0.15, [f, f * 1.47, f * 2.13], [0.05, 0.03, 0.02], [1, 0.5, 0.3], strike=0.3)
        place(x, ping, t, 0.8 * 0.55 ** i)
        t += r.uniform(0.07, 0.14) * 0.8 ** i
    return finalize(x, 0.001, 0.02)


# ---------------------------------------------------------------- bullets
def snd_impact_hard(r):
    x = np.zeros(ns(0.35))
    place(x, burst(r, 4, 0.8, ("hp", 2500, 2)), 0.002, 1.0)
    place(x, burst(r, 30, 8, ("bp", 1600, 0.8)), 0.003, 0.5)
    x += 0.25 * _debris(r, len(x), 0.01, 0.25, 6, 0.6)
    if r.random() < 0.5:
        # ricochet whine
        n = ns(0.3)
        t = tax(n)
        f = 3800 * (1500 / 3800) ** (t / 0.3)
        y = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.12)
        place(x, y, 0.01, 0.25)
    return finalize(x, 0.001, 0.02)


def snd_impact_soft(r):
    x = np.zeros(ns(0.25))
    place(x, burst(r, 40, 10, ("lp", 900, 2), color="brown"), 0.002, 1.0)
    place(x, boom(0.12, 140, 70, 0.02, 0.04), 0.002, 0.6)
    place(x, burst(r, 10, 2, ("hp", 1500, 2)), 0.002, 0.3)
    return finalize(x, 0.001, 0.02)


def snd_impact_flesh(r):
    x = np.zeros(ns(0.25))
    place(x, burst(r, 50, 12, ("lp", 600, 2), color="brown"), 0.002, 1.0)
    place(x, boom(0.1, 110, 60, 0.02, 0.035), 0.002, 0.7)
    place(x, burst(r, 25, 6, ("bp", 1100, 1.2), color="pink"), 0.006, 0.35)
    return finalize(x, 0.001, 0.02)


def snd_impact_metal(r):
    x = np.zeros(ns(0.6))
    f = r.uniform(1800, 2600)
    place(x, modal(r, 0.55, [f, f * 1.62, f * 2.37, f * 3.4], [0.16, 0.1, 0.06, 0.03], [1, 0.6, 0.4, 0.2], strike=0.8), 0.002, 1.0)
    place(x, burst(r, 3, 0.6, ("hp", 3000, 2)), 0.001, 0.6)
    return finalize(x, 0.001, 0.03)


def snd_armor_hit(r):
    x = np.zeros(ns(0.3))
    place(x, burst(r, 60, 14, ("lp", 700, 2), color="brown"), 0.002, 1.0)
    place(x, boom(0.12, 160, 80, 0.02, 0.05), 0.002, 0.6)
    ck = filt(crackle(r, ns(0.06), 1500, (0.05, 0.3)), ("hp", 2500, 2))
    place(x, nrm(ck), 0.004, 0.35)
    return finalize(x, 0.001, 0.02)


def snd_crack(r):
    x = np.zeros(ns(0.5))
    place(x, burst(r, 3, 0.4, ("hp", 1800, 2)), 0.004, 1.0)
    place(x, -0.8 * burst(r, 2, 0.3, ("hp", 1800, 2)), 0.0055, 1.0)
    ir = make_ir(r, 0.6, predelay=0.01, hf_start=8000, hf_end=2000)
    x = reverb(x, ir, 0.25)
    return finalize(x, 0.0005, 0.02)


def snd_whiz(r):
    dur = 0.4
    n = ns(dur)
    t = tax(n)
    f = 2400 * (800 / 2400) ** (t / dur)
    nb = filt(noise(r, n), ("lp", 400, 2))
    nb /= np.std(nb) + 1e-9
    ph = 2 * np.pi * np.cumsum(f) / SR
    y = (0.4 * np.sin(ph) + nb * np.sin(ph + 1.1)) * np.exp(-0.5 * ((t - 0.15) / 0.06) ** 2)
    return finalize(y, 0.002, 0.02)


# ---------------------------------------------------------------- grenades, mines, explosions
def snd_pin(r):
    return mech([(scrape(r, 0.08, 2500, 8000, 0.5), 0.0, 0.7), (modal(r, 0.3, [4300, 6900, 9100], [0.08, 0.05, 0.03], [1, 0.5, 0.3]), 0.08, 0.7)], 0.45)


def snd_bounce(r):
    f = r.uniform(600, 800)
    x = np.zeros(ns(0.3))
    place(x, modal(r, 0.25, [f, f * 2.1, f * 3.3], [0.04, 0.025, 0.015], [1, 0.5, 0.3], strike=0.6), 0.002, 1.0)
    place(x, burst(r, 30, 8, ("lp", 800, 2), color="brown"), 0.002, 0.6)
    return finalize(x, 0.001, 0.02)


def explosion(rng, dur, size):
    n = ns(dur)
    t = tax(n)
    t0 = 0.006
    crk = 360 * np.exp(-np.maximum(t - t0, 0) / (0.12 + 0.1 * size)) * (t > t0 + 0.004)
    x = _explosion_layers(
        rng, n, t, t0, T=0.006 + 0.012 * size, boom_f=(120 - 40 * size, 50 - 15 * size, 0.08 + 0.12 * size), boom_decay=0.2 + 0.35 * size,
        crack=(5, 1.0, 1.0),
        low_lp=160, low_env=[(t0, 0), (0.2, -4), (0.8, -14), (dur * 0.6, -30), (dur, -55)],
        mid_band=(220, 3000), mid_env=[(t0, 0), (0.08, -3), (0.4, -11), (1.0, -24), (dur, -60)],
        roll_env=[(t0, -20), (0.05, -7), (0.3, -2), (1.0, -12), (dur * 0.7, -30), (dur, -50)],
        roll_fc=lambda tt: 900 * (220 / 900) ** np.clip(tt / dur, 0, 1),
        crk_rate=crk, crk_amp=0.3, low_amp=0.32, mid_amp=0.5, roll_amp=0.18, boom_amp=0.7, blast_amp=1.0)
    x += _debris(rng, n, 0.25, dur * 0.7, int(10 + 20 * size), 0.1)
    ir = make_ir(rng, 1.6 + 1.2 * size, predelay=0.03, hf_start=6000, hf_end=800, er=10, er_span=0.09)
    x = reverb(x, ir, 0.32)
    x = limiter(x, -8, 0.12)
    return finalize(x, 0.003, 0.015, tail=0.5)


def snd_flashbang(r):
    dur = 1.8
    n = ns(dur)
    x = np.zeros(n)
    place(x, burst(r, 4, 0.7, ("hp", 1200, 2)), 0.003, 1.0)
    place(x, friedlander(0.003, 1.0, 0.06), 0.003, 1.0)
    place(x, burst(r, 80, 14, ("hp", 500, 2), ("lp", 9000, 2), color="pink"), 0.004, 0.8)
    place(x, boom(0.4, 140, 60, 0.03, 0.08), 0.003, 0.5)
    ir = make_ir(r, 1.4, predelay=0.02, hf_start=8000, hf_end=1500)
    x = reverb(x, ir, 0.4)
    x = limiter(x * 3, -4, 0.06)
    return finalize(x, 0.0005, 0.03, tail=0.4)


def snd_tinnitus(r):
    dur = 8.0
    n = ns(dur)
    t = tax(n)
    y = np.sin(2 * np.pi * 6200 * t) + 0.5 * np.sin(2 * np.pi * 4100 * t + 0.7) + 0.2 * np.sin(2 * np.pi * 8900 * t)
    y *= 1 + 0.15 * np.sin(2 * np.pi * 0.7 * t)
    y *= env_db(t, [(0, -6), (0.3, 0), (2.0, -4), (5.0, -16), (8.0, -60)])
    return finalize(y, 0.05, 0.3)


def snd_smoke(r):
    dur = 2.2
    n = ns(dur)
    t = tax(n)
    y = filt(noise(r, n, "pink"), ("hp", 1200, 2), ("lp", 9000, 2)) * rolls(r, n, 6, 3)
    y *= env_db(t, [(0, -30), (0.15, 0), (1.8, -3), (2.2, -30)])
    return finalize(y, 0.02, 0.1)


def snd_mine_click(r):
    return mech([(clack(r, 2300, dur=0.06, damp=0.7), 0.0, 1.0), (modal(r, 0.15, [3100, 4700], [0.04, 0.03], [1, 0.4]), 0.01, 0.4)], 0.2)


def snd_mine_arm(r):
    n = ns(0.3)
    t = tax(n)
    beep = np.sin(2 * np.pi * 2400 * t) * (t < 0.06) * np.sin(np.pi * np.clip(t / 0.06, 0, 1))
    x = np.zeros(n)
    place(x, clack(r, 2000, dur=0.04), 0.0, 0.8)
    place(x, beep, 0.1, 0.5)
    return finalize(x, 0.001, 0.02)


def snd_detonator(r):
    return mech([(clack(r, 900, dur=0.08, damp=1.3), 0.0, 1.0), (clack(r, 1500, dur=0.06), 0.09, 0.7), (clack(r, 1100, dur=0.06), 0.2, 0.6)], 0.32)


def tone(f, dur, rough=0.0, trem=0.0):
    n = ns(dur)
    t = tax(n)
    y = np.sin(2 * np.pi * f * t)
    if rough:
        y = np.tanh(3 * y) * (1 - rough) + rough * np.sign(y) * 0.8
    if trem:
        y *= 0.6 + 0.4 * np.sin(2 * np.pi * trem * t)
    y *= np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 0.3
    return y


def snd_detector(r):
    return finalize(tone(1200, 0.07), 0.004, 0.01)


def snd_seek(r):
    return finalize(tone(320, 0.45, rough=0.5, trem=14), 0.01, 0.03)


def snd_lock(r):
    return finalize(tone(1150, 0.3, rough=0.2), 0.005, 0.02)


def snd_rocket_loop(r):
    loop, xf = 2.0, 0.25
    n = ns(loop + xf)
    roar = filt(noise(r, n, "pink") + 0.7 * noise(r, n, "brown"), ("hp", 90, 2), ("lp", 2800, 2)) * rolls(r, n, 7, 3)
    hiss = filt(noise(r, n), ("hp", 3000, 2), ("lp", 9000, 2))
    return finalize_loop(roar + 0.25 * hiss, loop, xf)


SOUNDS = []
for gid, fn in SHOTS.items():
    SOUNDS.append((f"shot/{gid}", fn))
SOUNDS += [
    ("shot/rpg7", launch_rpg), ("shot/javelin", launch_javelin),
    ("gun/dry_fire", snd_dry_fire), ("gun/mag_out", snd_mag_out), ("gun/mag_in", snd_mag_in), ("gun/charge", snd_charge), ("gun/slide", snd_slide),
    ("gun/shell_insert", snd_shell_insert), ("gun/pump", snd_pump), ("gun/bolt", snd_bolt), ("gun/rocket_load", snd_rocket_load), ("gun/mode", snd_mode),
    ("gun/casing_0", snd_casing), ("gun/casing_1", snd_casing),
    ("bullet/impact_hard_0", snd_impact_hard), ("bullet/impact_hard_1", snd_impact_hard), ("bullet/impact_hard_2", snd_impact_hard),
    ("bullet/impact_soft_0", snd_impact_soft), ("bullet/impact_soft_1", snd_impact_soft), ("bullet/impact_soft_2", snd_impact_soft),
    ("bullet/impact_flesh_0", snd_impact_flesh), ("bullet/impact_flesh_1", snd_impact_flesh),
    ("bullet/impact_metal_0", snd_impact_metal), ("bullet/impact_metal_1", snd_impact_metal), ("bullet/armor_hit", snd_armor_hit),
    ("bullet/crack_0", snd_crack), ("bullet/crack_1", snd_crack), ("bullet/whiz_0", snd_whiz), ("bullet/whiz_1", snd_whiz),
    ("grenade/pin", snd_pin), ("grenade/bounce_0", snd_bounce), ("grenade/bounce_1", snd_bounce),
    ("explosion/grenade", lambda r: explosion(r, 2.6, 0.2)), ("explosion/rocket", lambda r: explosion(r, 3.2, 0.6)),
    ("explosion/c4", lambda r: explosion(r, 3.8, 1.0)),
    ("flashbang/bang", snd_flashbang), ("flashbang/ring", snd_tinnitus), ("smoke/hiss", snd_smoke),
    ("mine/click", snd_mine_click), ("mine/arm", snd_mine_arm), ("detonator/click", snd_detonator), ("detector/beep", snd_detector),
    ("rocket/loop", snd_rocket_loop), ("javelin/seek", snd_seek), ("javelin/lock", snd_lock),
]


def main(argv):
    names = [a for a in argv if not a.startswith("--")]
    os.makedirs(TMP, exist_ok=True)
    total = 0
    for name, fn in SOUNDS:
        if names and not any(name.startswith(n) for n in names):
            continue
        rng = np.random.default_rng(zlib.crc32(name.encode()))
        x = fn(rng)
        assert np.all(np.isfinite(x)), name
        wav = os.path.join(TMP, name.replace("/", "_") + ".wav")
        ogg = os.path.join(OUT, name + ".ogg")
        os.makedirs(os.path.dirname(ogg), exist_ok=True)
        write_wav(wav, x)
        encode(wav, ogg, name)
        total += os.path.getsize(ogg)
        print(f"{name:24s} {len(x) / SR:5.2f} s  {os.path.getsize(ogg) / 1024:6.1f} KiB")
    print(f"total {total / 1024:.0f} KiB")


if __name__ == "__main__":
    main(sys.argv[1:])
