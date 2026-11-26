"""Génère la référence de test du portage Kotlin de l'empreinte Shazam (algorithme SongRec).

Signal synthétique déterministe (glissandos + bruit, aucune musique protégée), empreinte
calculée par shazamio_core (le code Rust de SongRec), puis pics décodés en JSON.
"""
import asyncio, base64, json, math, random, struct, sys, wave
from pathlib import Path
from shazamio_core import Recognizer
from shazamio.signature import DecodedMessage

RATE, SECONDS = 16_000, 8
out = Path(sys.argv[1])
rng = random.Random(42)
samples = []
for n in range(RATE * SECONDS):
    t = n / RATE
    value = 0.0
    for k, (f0, f1) in enumerate([(300, 480), (700, 1300), (1600, 3200), (3700, 5200)]):
        f = f0 + (f1 - f0) * (t % 2) / 2          # glissando qui recommence toutes les 2 s
        value += math.sin(2 * math.pi * f * t + k) * (0.18 if (int(t * 4) + k) % 3 else 0.05)
    value += rng.uniform(-0.04, 0.04)
    samples.append(max(-32768, min(32767, int(value * 32767 * 0.9))))

wav = out / "synthetic.wav"
with wave.open(str(wav), "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
    w.writeframes(struct.pack(f"<{len(samples)}h", *samples))

async def fingerprint(path: Path):
    return await Recognizer().recognize_path(str(path))


signature = asyncio.run(fingerprint(wav))
uri = signature.signature.uri
message = DecodedMessage.decode_from_binary(base64.b64decode(uri.split(",", 1)[1]))
golden = {
    "numberSamples": message.number_samples,
    "samplems": signature.signature.samples,
    "bands": {
        str(int(band)): [[p.fft_pass_number, p.peak_magnitude, p.corrected_peak_frequency_bin] for p in peaks]
        for band, peaks in sorted(message.frequency_band_to_sound_peaks.items())
    },
}
(out / "golden_peaks.json").write_text(json.dumps(golden), encoding="utf-8")
print("échantillons", message.number_samples, "| samplems", signature.signature.samples,
      "| pics par bande", {k: len(v) for k, v in golden["bands"].items()})
