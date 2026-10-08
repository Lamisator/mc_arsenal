"""Signal-processing helpers for the sound generator (taken from the RedButton mod's generator)."""
import os
import subprocess
import sys
import wave
import zlib

import numpy as np

SR = 44100
PEAK = 10 ** (-1.0 / 20)  # -1 dBFS

rfft, irfft, rfftfreq = np.fft.rfft, np.fft.irfft, np.fft.rfftfreq


# ----------------------------------------------------------------------------
# basic helpers
# ----------------------------------------------------------------------------
def ns(sec):
    return int(round(sec * SR))


def tax(n):
    return np.arange(n) / SR


def nextpow2(n):
    return 1 << int(n - 1).bit_length()


def nrm(x):
    m = np.max(np.abs(x))
    return x / m if m > 0 else x


def place(buf, sig, t0, g=1.0):
    i = ns(t0)
    if i >= len(buf) or i < 0:
        return buf
    m = min(len(sig), len(buf) - i)
    buf[i:i + m] += g * sig[:m]
    return buf


def at(arr, tt):
    """Sample a per-sample control array at (frame) times tt."""
    return np.interp(tt, np.arange(len(arr)) / SR, arr)


def env_db(t, pts):
    tp = [p[0] for p in pts]
    dp = [p[1] for p in pts]
    return 10 ** (np.interp(t, tp, dp) / 20)


def ramp(t, t0, t1):
    """0 before t0, raised-cosine to 1 at t1."""
    u = np.clip((t - t0) / max(t1 - t0, 1e-9), 0, 1)
    return 0.5 - 0.5 * np.cos(np.pi * u)


def smooth_rand(rng, n, rate):
    """Zero-mean, unit-std random control signal band-limited to ~rate Hz."""
    N = nextpow2(n)
    X = rfft(rng.standard_normal(N))
    f = rfftfreq(N, 1 / SR)
    X *= np.exp(-(f / rate) ** 2)
    X[0] = 0
    y = irfft(X, N)[:n]
    return y / (np.std(y) + 1e-12)


def rolls(rng, n, rate, depth_db):
    """Slow random gain wobble ("rolls"), bounded to +-1.5*depth_db."""
    return 10 ** (depth_db * 1.5 * np.tanh(smooth_rand(rng, n, rate) / 1.5) / 20)


def noise(rng, n, color='white'):
    slope = {'white': 0.0, 'pink': -1.0, 'brown': -2.0}[color]
    if slope == 0:
        x = rng.standard_normal(n)
        return x / np.std(x)
    N = nextpow2(n)
    X = rfft(rng.standard_normal(N))
    f = rfftfreq(N, 1 / SR)
    g = np.maximum(f, 15.0) ** (slope / 2)
    g[0] = 0
    x = irfft(X * g, N)[:n]
    return x / np.std(x)


# ----------------------------------------------------------------------------
# filters (RBJ biquads evaluated in the frequency domain -> causal IIR via FFT)
# ----------------------------------------------------------------------------
BUTTER_Q = {1: [], 2: [0.7071], 4: [0.5412, 1.3066], 6: [0.5176, 0.7071, 1.9319],
            8: [0.5098, 0.6013, 0.9000, 2.5629]}


def _bq(kind, fc, q=0.7071, gain_db=0.0):
    fc = min(max(fc, 1.0), 0.49 * SR)
    w0 = 2 * np.pi * fc / SR
    cw, sw = np.cos(w0), np.sin(w0)
    al = sw / (2 * q)
    A = 10 ** (gain_db / 40)
    if kind == 'lp':
        b = ((1 - cw) / 2, 1 - cw, (1 - cw) / 2); a = (1 + al, -2 * cw, 1 - al)
    elif kind == 'hp':
        b = ((1 + cw) / 2, -(1 + cw), (1 + cw) / 2); a = (1 + al, -2 * cw, 1 - al)
    elif kind == 'bp':
        b = (al, 0.0, -al); a = (1 + al, -2 * cw, 1 - al)
    elif kind == 'peak':
        b = (1 + al * A, -2 * cw, 1 - al * A); a = (1 + al / A, -2 * cw, 1 - al / A)
    elif kind in ('ls', 'hs'):
        sA = 2 * np.sqrt(A) * al
        if kind == 'ls':
            b = (A * ((A + 1) - (A - 1) * cw + sA), 2 * A * ((A - 1) - (A + 1) * cw),
                 A * ((A + 1) - (A - 1) * cw - sA))
            a = ((A + 1) + (A - 1) * cw + sA, -2 * ((A - 1) + (A + 1) * cw),
                 (A + 1) + (A - 1) * cw - sA)
        else:
            b = (A * ((A + 1) + (A - 1) * cw + sA), -2 * A * ((A - 1) + (A + 1) * cw),
                 A * ((A + 1) + (A - 1) * cw - sA))
            a = ((A + 1) - (A - 1) * cw + sA, 2 * ((A - 1) - (A + 1) * cw),
                 (A + 1) - (A - 1) * cw - sA)
    else:
        raise ValueError(kind)
    return b, a


def _onepole(kind, fc, f):
    # bilinear one-pole (first order Butterworth)
    k = np.tan(np.pi * min(fc, 0.49 * SR) / SR)
    z1 = np.exp(-2j * np.pi * f / SR)
    if kind == 'lp':
        return (k * (1 + z1)) / ((1 + k) + (k - 1) * z1)
    return (1 - z1) / ((1 + k) + (k - 1) * z1)


def response(specs, f):
    """specs: ('lp'|'hp', fc, order) Butterworth, ('lpq'|'hpq', fc, q),
    ('bp', fc, q) 0 dB peak, ('peak', fc, gain_db, q), ('ls'|'hs', fc, gain_db)."""
    z1 = np.exp(-2j * np.pi * f / SR)
    z2 = z1 * z1
    H = np.ones(len(f), dtype=complex)

    def bq(b, a):
        return (b[0] + b[1] * z1 + b[2] * z2) / (a[0] + a[1] * z1 + a[2] * z2)

    for s in specs:
        k = s[0]
        if k in ('lp', 'hp'):
            order = s[2] if len(s) > 2 else 2
            if order % 2:
                H *= _onepole(k, s[1], f)
            for q in BUTTER_Q[order - (order % 2)]:
                H *= bq(*_bq(k, s[1], q))
        elif k in ('lpq', 'hpq'):
            H *= bq(*_bq(k[:2], s[1], s[2]))
        elif k == 'bp':
            H *= bq(*_bq('bp', s[1], s[2]))
        elif k == 'peak':
            H *= bq(*_bq('peak', s[1], s[3], gain_db=s[2]))
        elif k in ('ls', 'hs'):
            H *= bq(*_bq(k, s[1], 0.7071, gain_db=s[2]))
        else:
            raise ValueError(k)
    return H


def filt(x, *specs, pad=0.6):
    n = len(x)
    N = nextpow2(n + ns(pad))
    H = response(specs, rfftfreq(N, 1 / SR))
    return irfft(rfft(x, N) * H, N)[:n]


# magnitude responses for the time-varying (STFT) filter
def m_lp(f, fc, order=2):
    return 1 / np.sqrt(1 + (f / fc) ** (2 * order))


def m_hp(f, fc, order=2):
    return 1 / np.sqrt(1 + (fc / np.maximum(f, 1e-3)) ** (2 * order))


def m_bp(f, fc, q):
    ff = np.maximum(f, 1e-3)
    return 1 / np.sqrt(1 + q * q * (ff / fc - fc / ff) ** 2)


def tv_filter(x, gain_fn, nfft=2048):
    """STFT overlap-add filter; gain_fn(t[frames,1], f[1,bins]) -> magnitude."""
    hop = nfft // 4
    n = len(x)
    win = np.sqrt(0.5 - 0.5 * np.cos(2 * np.pi * np.arange(nfft) / nfft))
    pad = nfft
    total = n + 2 * pad
    nfr = int(np.ceil((total - nfft) / hop)) + 1
    xp = np.zeros((nfr - 1) * hop + nfft)
    xp[pad:pad + n] = x
    idx = hop * np.arange(nfr)[:, None] + np.arange(nfft)[None, :]
    F = rfft(xp[idx] * win, axis=1)
    tc = (hop * np.arange(nfr) + nfft / 2 - pad) / SR
    f = rfftfreq(nfft, 1 / SR)
    G = gain_fn(tc[:, None], f[None, :])
    Y = irfft(F * G, nfft, axis=1) * win
    y = np.zeros(len(xp) + nfft)
    R = nfft // hop
    for r in range(R):
        blk = Y[r::R].reshape(-1)
        y[r * hop:r * hop + len(blk)] += blk
    return y[pad:pad + n] / (nfft / (2 * hop))


# ----------------------------------------------------------------------------
# reverb
# ----------------------------------------------------------------------------
def convolve(x, h):
    N = nextpow2(len(x) + len(h))
    return irfft(rfft(x, N) * rfft(h, N), N)[:len(x) + len(h) - 1]


def make_ir(rng, rt60, predelay=0.015, hf_start=9000.0, hf_end=1500.0, er=8,
            er_span=0.05, er_gain=4.0, lowcut=50.0, length=None):
    L = ns(length if length else rt60)
    t = tax(L)
    ir = rng.standard_normal(L) * 10 ** (-3 * t / rt60)
    fcs = hf_start * (hf_end / hf_start) ** (t / rt60)
    ir = tv_filter(ir, lambda tt, ff: m_lp(ff, at(fcs, tt), 1), nfft=1024)
    pd = ns(predelay)
    ir[:pd] = 0
    ir *= ramp(t, predelay, predelay + 0.03)  # diffuse field builds up
    for _ in range(er):
        d = predelay + rng.uniform(0.0, er_span)
        ir[ns(d)] += rng.choice([-1, 1]) * rng.uniform(0.3, 1.0) * er_gain
    ir = filt(ir, ('hp', lowcut, 2))
    return ir / np.sqrt(np.sum(ir ** 2))


def reverb(x, ir, wet, dry=1.0):
    return dry * x + wet * convolve(x, ir)[:len(x)]


# ----------------------------------------------------------------------------
# dynamics / colour
# ----------------------------------------------------------------------------
def sat(x, drive=2.0, asym=0.0):
    x = nrm(x)
    return np.tanh(drive * (x + asym)) - np.tanh(drive * asym)


def sat_keep(x, drive=1.5):
    """tanh saturation that keeps the original peak scale."""
    m = np.max(np.abs(x)) + 1e-12
    return m * np.tanh(drive * x / m) / np.tanh(drive)


def limiter(x, thresh_db=-6.0, release=0.1, block=64):
    """Block-based look-ahead peak limiter (instant attack, exp. release)."""
    x = nrm(x)
    n = len(x)
    th = 10 ** (thresh_db / 20)
    nb = int(np.ceil(n / block))
    a = np.zeros(nb * block)
    a[:n] = np.abs(x)
    bmax = a.reshape(nb, block).max(axis=1)
    bmax = np.maximum(bmax, np.concatenate([bmax[1:], bmax[-1:]]))  # look-ahead 1 block
    bmax = np.maximum(bmax, np.concatenate([bmax[:1], bmax[:-1]]))
    tgt = np.minimum(1.0, th / np.maximum(bmax, 1e-9))
    rel = np.exp(-block / (release * SR))
    g = np.empty(nb)
    cur = 1.0
    for i in range(nb):
        v = tgt[i]
        cur = v if v < cur else v + (cur - v) * rel
        g[i] = cur
    centers = (np.arange(nb) + 0.5) * block
    return x * np.interp(np.arange(n), centers, g)


# ----------------------------------------------------------------------------
# sound building blocks
# ----------------------------------------------------------------------------
def friedlander(T, b=1.0, dur=None, rise_ms=0.06):
    """Blast-wave overpressure: instant rise, positive phase T, negative phase."""
    n = ns(dur if dur else 8 * T)
    t = tax(n)
    p = (1 - t / T) * np.exp(-b * t / T)
    r = ns(rise_ms / 1000)
    if r > 1:
        p[:r] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(r) / r)
    return p


def boom(dur, f0, f1, glide_tau, decay, attack=0.002, harm=(1.0,)):
    n = ns(dur)
    t = tax(n)
    f = f1 + (f0 - f1) * np.exp(-t / glide_tau)
    ph = 2 * np.pi * np.cumsum(f) / SR
    y = np.zeros(n)
    for k, a in enumerate(harm):
        y += a * np.sin((k + 1) * ph)
    return y * (1 - np.exp(-t / attack)) * np.exp(-t / decay)


def burst(rng, dur_ms, tau_ms, *specs, color='white'):
    """Short noise burst (crack / transient), peak-normalised."""
    n = max(4, ns(dur_ms / 1000))
    k = np.arange(n)
    x = noise(rng, n + ns(0.02), color)
    if specs:
        x = filt(x, *specs, pad=0.05)
    x = x[:n] * np.exp(-k / (tau_ms / 1000 * SR))
    x[:3] *= np.array([0.3, 0.7, 0.9])[:min(3, n)]
    return nrm(x)


def modal(rng, dur, freqs, decays, amps, strike=0.3, strike_ms=1.5):
    """Struck object: sum of exponentially decaying partials + strike noise."""
    n = ns(dur)
    t = tax(n)
    y = np.zeros(n)
    for f, d, a in zip(freqs, decays, amps):
        if f < 0.45 * SR:
            y += a * np.exp(-t / d) * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi))
    if strike > 0:
        L = max(2, ns(strike_ms / 1000))
        k = np.arange(L)
        y[:L] += strike * max(amps) * rng.standard_normal(L) * np.exp(-k / (L / 4))
    r = 4
    y[:r] *= np.linspace(0, 1, r + 1)[1:]
    return y


def crackle(rng, n, rate, glen_ms=(0.08, 1.2), alpha=2.0, amp_max=6.0, mode='spike'):
    """Poisson impulse train. rate: scalar or per-sample array (events/s)."""
    if np.isscalar(rate):
        rate = np.full(n, float(rate))
    idx = np.nonzero(rng.random(n) < rate / SR)[0]
    out = np.zeros(n + ns(0.08))
    for i in idx:
        L = max(2, ns(rng.uniform(*glen_ms) / 1000))
        k = np.arange(L)
        if mode == 'spike':  # skewed, mostly-positive spikes (shock-like crackle)
            g = np.exp(-k / (0.25 * L)) * (1 + 0.25 * rng.standard_normal(L))
            s = 1.0 if rng.random() < 0.8 else -1.0
        else:
            g = rng.standard_normal(L) * np.exp(-k / (0.3 * L))
            s = 1.0
        a = min(rng.pareto(alpha) + 0.2, amp_max)
        out[i:i + L] += s * a * g
    return out[:n]


def additive(f_arr, amp_fn, max_f=15000.0, rng=None):
    """Band-limited additive oscillator following a frequency curve."""
    ph = 2 * np.pi * np.cumsum(f_arr) / SR
    y = np.zeros(len(f_arr))
    K = int(max_f / max(np.min(f_arr), 20.0)) + 1
    for k in range(1, K + 1):
        a = amp_fn(k)
        if a == 0:
            continue
        g = np.clip((max_f - k * f_arr) / (0.15 * max_f), 0, 1)
        if not g.any():
            continue
        p0 = rng.uniform(0, 2 * np.pi) if rng is not None else 0.0
        y += a * g * np.sin(k * ph + p0)
    return y


# ----------------------------------------------------------------------------
# finishing
# ----------------------------------------------------------------------------
def fade(x, fin=0.005, fout=0.012):
    x = x.copy()
    a, b = ns(fin), ns(fout)
    if a > 0:
        x[:a] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(a) / a)
    if b > 0:
        x[-b:] *= 0.5 + 0.5 * np.cos(np.pi * (np.arange(b) + 1) / b)
    return x


def finalize(x, fin=0.005, fout=0.012, tail=0.0):
    """HP 25 Hz (DC), optional design tail (cos^2 decay over the last `tail` s so
    reverb tails die naturally), 5-15 ms anti-click fades, peak -1 dBFS."""
    x = filt(x, ('hp', 25, 2))
    if tail > 0:
        m = ns(tail)
        x[-m:] *= np.cos(0.5 * np.pi * np.arange(m) / m) ** 2
    x = fade(x, fin, fout)
    x = x - np.mean(x) * np.hanning(len(x)) / np.mean(np.hanning(len(x)))  # residual DC
    x = fade(x, fin, fout)
    return nrm(x) * PEAK


def finalize_loop(x, loop_len, xfade):
    """x has length >= loop_len + xfade.  Equal-power crossfade of the tail into
    the head -> exactly loop_len samples that wrap seamlessly.  No fades."""
    x = filt(x, ('hp', 25, 2))
    L, X = ns(loop_len), ns(xfade)
    out = x[:L].copy()
    u = (np.arange(X) + 0.5) / X
    out[:X] = x[:X] * np.sin(0.5 * np.pi * u) + x[L:L + X] * np.cos(0.5 * np.pi * u)
    out -= np.mean(out)
    return nrm(out) * PEAK


# ============================================================================
def _explosion_layers(rng, n, t, t0, *, T, boom_f, boom_decay, crack, low_lp, low_env,
                      mid_band, mid_env, roll_env, roll_fc, crk_rate, crk_amp, low_amp,
                      mid_amp, roll_amp, boom_amp, blast_amp):
    x = np.zeros(n)
    lo = np.zeros(n)
    place(x, friedlander(T, 1.0, max(0.3, 10 * T)), t0, blast_amp)
    place(x, burst(rng, crack[0], crack[1], ('hp', 1500, 2)), t0, crack[2])
    place(x, burst(rng, 30, 8, ('bp', 1100, 0.7), color='pink'), t0 + 0.001, crack[2] * 0.6)
    place(lo, boom(min(3.0, 8 * boom_decay), boom_f[0], boom_f[1], boom_f[2], boom_decay,
                   harm=(1, 0.35, 0.12)), t0, boom_amp)
    on = ramp(t, t0, t0 + 0.004)
    low = filt(noise(rng, n, 'brown'), ('lp', low_lp, 2), ('peak', 50, 3, 0.8))
    lo += low_amp * low * env_db(t, low_env) * on * rolls(rng, n, 2.5, 4)
    mid = filt(noise(rng, n, 'pink'), ('hp', mid_band[0], 2), ('lp', mid_band[1], 2))
    x += mid_amp * mid * env_db(t, mid_env) * on * rolls(rng, n, 6, 3)
    rr = noise(rng, n, 'pink') + 0.8 * noise(rng, n, 'brown')
    fc = roll_fc(t) * 2 ** (0.8 * smooth_rand(rng, n, 1.5))
    rr = tv_filter(rr, lambda tt, ff: m_lp(ff, at(fc, tt), 2), nfft=4096)
    lo += roll_amp * nrm(rr) * 4 * env_db(t, roll_env) * on * rolls(rng, n, 1.8, 6)
    x += sat_keep(lo, 1.8)
    ck = filt(crackle(rng, n, crk_rate, (0.05, 0.8)), ('hp', 700, 2), ('lp', 7000, 2))
    ck = np.tanh(ck / (3 * np.std(ck) + 1e-12)) * np.sqrt(crk_rate / np.max(crk_rate))
    x += crk_amp * ck
    return x


def _debris(rng, n, t0, t1, count, amp, lp_t=None):
    x = np.zeros(n)
    for _ in range(count):
        tc = t0 + (t1 - t0) * rng.random() ** 1.6
        a = amp * rng.uniform(0.3, 1.0) * np.exp(-3.0 * (tc - t0) / (t1 - t0))
        if rng.random() < 0.55:  # dull thud of falling earth/chunks
            g = burst(rng, rng.uniform(25, 90), rng.uniform(8, 25), ('lp', rng.uniform(250, 600), 2),
                      color='brown')
        else:  # gravel / small metal
            g = burst(rng, rng.uniform(4, 20), rng.uniform(1, 5),
                      ('bp', rng.uniform(1500, 5000), 1.2))
            a *= 0.5
        place(x, g, tc, a)
    return x


def write_wav(path, x):
    pcm = np.round(np.clip(x, -1, 1) * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def read_wav(path):
    with wave.open(path, 'rb') as w:
        assert w.getnchannels() == 1 and w.getsampwidth() == 2 and w.getframerate() == SR
        return np.frombuffer(w.readframes(w.getnframes()), '<i2').astype(np.float64) / 32768


def encode(wav, ogg, name):
    serial = zlib.crc32(name.encode()) & 0x7fffffff
    subprocess.run(['oggenc', '-Q', '-q', '5', '-s', str(serial), '-o', ogg, wav], check=True)


