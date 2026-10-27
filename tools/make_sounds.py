"""Generates the app's short sound effects (16-bit mono WAV, 22.05 kHz) from pure Python, no samples used."""
import math, random, struct, wave, os, sys

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'sounds')

def env(i, n, attack, release):
    a = min(1.0, i / max(1, attack))
    r = min(1.0, (n - i) / max(1, release))
    return min(a, r)

def tone(freq, seconds, vol=0.5, attack=0.01, release=0.08, shape='sine', glide=0.0):
    n = int(RATE * seconds)
    out = []
    phase = 0.0
    for i in range(n):
        f = freq * (1 + glide * i / n)
        phase += 2 * math.pi * f / RATE
        if shape == 'sine': s = math.sin(phase)
        elif shape == 'tri': s = 2 / math.pi * math.asin(math.sin(phase))
        else: s = 1.0 if math.sin(phase) > 0 else -1.0
        out.append(s * vol * env(i, n, attack * RATE, release * RATE))
    return out

def noise(seconds, vol=0.4, attack=0.005, release=0.05, lowpass=0.3, seed=1):
    rnd = random.Random(seed)
    n = int(RATE * seconds)
    out = []
    last = 0.0
    for i in range(n):
        v = rnd.uniform(-1, 1)
        last = last + lowpass * (v - last)
        out.append(last * vol * env(i, n, attack * RATE, release * RATE))
    return out

def mix(*parts):
    n = max(len(p) for p in parts)
    out = [0.0] * n
    for p in parts:
        for i, v in enumerate(p): out[i] += v
    return out

def delay(part, seconds):
    return [0.0] * int(RATE * seconds) + part

def write(name, samples):
    peak = max(1e-6, max(abs(s) for s in samples))
    k = 0.9 / peak if peak > 0.9 else 1.0
    os.makedirs(OUT, exist_ok=True)
    with wave.open(os.path.join(OUT, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, s * k)) * 32767)) for s in samples))

# dice_throw: three quick wooden rattles
write('dice_throw', mix(noise(0.05, 0.5, lowpass=0.5, seed=1), delay(noise(0.04, 0.4, lowpass=0.6, seed=2), 0.08), delay(noise(0.05, 0.45, lowpass=0.45, seed=3), 0.17)))
# dice_land: a clack with a short wooden ring
write('dice_land', mix(noise(0.03, 0.7, lowpass=0.8, seed=4), tone(620, 0.09, 0.25, 0.002, 0.07), tone(1240, 0.05, 0.1, 0.002, 0.04)))
# takeoff: rising airy whoosh
write('takeoff', mix(noise(0.32, 0.35, attack=0.08, release=0.15, lowpass=0.12, seed=5), tone(420, 0.3, 0.12, 0.05, 0.15, glide=0.6)))
# hop: soft tick
write('hop', mix(tone(880, 0.05, 0.25, 0.003, 0.04), noise(0.02, 0.15, lowpass=0.7, seed=6)))
# jump: a quick rising two-note
write('jump', mix(tone(523, 0.1, 0.3, 0.005, 0.06), delay(tone(784, 0.14, 0.3, 0.005, 0.1), 0.09)))
# fly: longer whoosh with a sweep
write('fly', mix(noise(0.6, 0.3, attack=0.15, release=0.3, lowpass=0.1, seed=7), tone(330, 0.55, 0.1, 0.1, 0.3, glide=1.2)))
# capture: a descending blip, friendly not punishing
write('capture', mix(tone(660, 0.12, 0.3, 0.005, 0.08, glide=-0.35), delay(tone(440, 0.16, 0.25, 0.005, 0.12, glide=-0.3), 0.1)))
# home: two warm chime notes
write('home', mix(tone(659, 0.25, 0.3, 0.005, 0.2), delay(tone(988, 0.35, 0.3, 0.005, 0.3), 0.12), delay(tone(1319, 0.3, 0.15, 0.005, 0.25), 0.24)))
# win: a little fanfare
write('win', mix(tone(523, 0.18, 0.3, 0.005, 0.1), delay(tone(659, 0.18, 0.3, 0.005, 0.1), 0.16), delay(tone(784, 0.18, 0.3, 0.005, 0.1), 0.32), delay(tone(1047, 0.5, 0.35, 0.005, 0.4), 0.48)))
# tap: UI click
write('tap', mix(tone(1200, 0.03, 0.25, 0.002, 0.02), noise(0.015, 0.2, lowpass=0.9, seed=8)))
print('wrote', sorted(os.listdir(OUT)))
