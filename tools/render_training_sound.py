"""Render the original three-hit Peerless training cue; requires NumPy."""
from pathlib import Path
import wave
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
RATE = 44100
LENGTH = 2.3
rng = np.random.default_rng(407)
mono = np.zeros(round(RATE * LENGTH), dtype=np.float64)

for start, strength, sustain in [(0.0, 0.58, 0.14), (0.33, 0.66, 0.16), (0.72, 1.0, 0.42)]:
    offset = round(start * RATE)
    t = np.arange(len(mono) - offset) / RATE
    attack = 1.0 - np.exp(-t / 0.006)
    brass = np.zeros_like(t)
    # D minor voiced above a low D impact, with a longer final chord.
    for fundamental, weight in [(146.832, 1.0), (220.0, 0.52), (349.228, 0.24)]:
        for harmonic in range(1, 9):
            envelope = np.exp(-t * (1.0 + harmonic * 0.13) / sustain)
            brass += weight * envelope * np.sin(2 * np.pi * fundamental * harmonic * t) / harmonic**1.45
    thump_phase = 2 * np.pi * (49 * t + 2.8 * (1 - np.exp(-t * 18)))
    thump = 0.8 * np.sin(thump_phase) * np.exp(-t / 0.13)
    impact = 0.07 * rng.standard_normal(len(t)) * np.exp(-t / 0.018)
    mono[offset:] += strength * attack * (brass + thump + impact)

stereo = np.column_stack((mono, mono))
for channel, delays in enumerate([[(0.049, .11), (.127, .07)], [(.071, .11), (.163, .07)]]):
    for seconds, gain in delays:
        delay = round(seconds * RATE)
        stereo[delay:, channel] += mono[:-delay] * gain
fade = round(0.08 * RATE)
stereo[-fade:] *= np.linspace(1, 0, fade)[:, None]
stereo *= 0.78 / np.max(np.abs(stereo))
pcm = np.round(stereo * 32767).astype("<i2")
destination = ROOT / "sounds/peerless/training_complete.wav"
destination.parent.mkdir(parents=True, exist_ok=True)
with wave.open(str(destination), "wb") as output:
    output.setnchannels(2)
    output.setsampwidth(2)
    output.setframerate(RATE)
    output.writeframes(pcm.tobytes())
print(f"Rendered {destination}: {LENGTH}s, stereo PCM, 44.1 kHz")
