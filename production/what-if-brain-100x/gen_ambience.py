"""Soft ambient bed (filtered noise + low drone) for the 180s film."""
import numpy as np, wave
sr, dur = 48000, 180.0
n = int(sr * dur)
rng = np.random.default_rng(3)
white = rng.standard_normal(n).astype(np.float32)
X = np.fft.rfft(white)
f = np.fft.rfftfreq(n, 1 / sr)
b = np.fft.irfft(X * (1.0 / np.maximum(f, 20.0) ** 0.9), n).astype(np.float32)
b /= (np.abs(b).max() + 1e-9)
t = np.arange(n) / sr
lfo = 0.6 + 0.4 * np.sin(2 * np.pi * 0.045 * t + 0.7) ** 2
mix = b * lfo * 0.055 + (0.035 * np.sin(2 * np.pi * 55 * t) + 0.02 * np.sin(2 * np.pi * 82.4 * t)) * lfo
mix = (mix / max(1.0, np.abs(mix).max() / 0.12)).astype(np.float32)
pcm = (np.clip(np.stack([mix, mix], 1), -1, 1) * 32767).astype('<i2')
with wave.open('audio/ambience.wav', 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(sr); w.writeframes(pcm.tobytes())
print('ambience written')
