"""Génère icônes et badge de l'app à partir du logo détouré (logo-detour.jpg à la racine du projet)."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"

# Badge détouré fourni par l'auteur (logo-detour.jpg : badge sur fond noir pur).
# Le noir devient transparent : opaque dans le cercle (le ciel sombre du décor ne doit pas
# devenir transparent), « couleur vers transparence » à l'extérieur pour garder le halo néon.
import numpy as np

detour = Image.open(ROOT / "logo-detour.jpg").convert("RGB")
CX, CY = 705, 384
RING = 351  # rayon de l'anneau néon : tout ce qui est à l'intérieur reste opaque


def detoured(radius: int) -> Image.Image:
    """Badge centré, cadré sur un carré de demi-côté [radius], noir rendu transparent."""
    rgb = np.asarray(detour.crop((CX - radius, CY - radius, CX + radius, CY + radius)), dtype=np.float32)
    yy, xx = np.mgrid[0:2 * radius, 0:2 * radius]
    distance = np.hypot(xx - radius + 0.5, yy - radius + 0.5)
    inside = np.clip((RING - distance) / 14, 0, 1)           # 1 dans l'anneau, fondu sur 14 px
    glow = np.clip(rgb.max(axis=2) / 255 * 1.4, 0, 1)        # le noir devient transparent
    alpha = np.maximum(inside, glow)
    unmixed = np.clip(rgb / np.maximum(alpha, 1e-3)[..., None], 0, 255)  # retire le noir mélangé au halo
    color = np.where(inside[..., None] >= 1, rgb, unmixed)
    return Image.fromarray(np.dstack([color, alpha * 255]).astype(np.uint8), "RGBA")


# Dans l'app : cadrage carré qui contient toutes les lettres (elles débordent de l'anneau)
badge = detoured(400)
# Icônes : Android les découpe en cercle ; le pied du K est à ~421 px du centre, d'où ce cadrage
icon_badge = detoured(440)
BACKGROUND = (11, 6, 32)  # nuit du logo


def round_badge(size: int, source: Image.Image | None = None) -> Image.Image:
    """Badge détouré (transparence propre, halo compris) à la taille voulue ; icônes par défaut."""
    return (source or icon_badge).resize((size, size), Image.LANCZOS)


# Icônes classiques (carrée sur fond nuit, et ronde) + premier plan des icônes adaptatives
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for name, factor in DENSITIES.items():
    folder = RES / f"mipmap-{name}"
    folder.mkdir(parents=True, exist_ok=True)
    icon = int(48 * factor)
    square = Image.new("RGBA", (icon, icon), BACKGROUND + (255,))
    inner = int(icon * 0.94)
    square.alpha_composite(round_badge(inner), ((icon - inner) // 2, (icon - inner) // 2))
    square.save(folder / "ic_launcher.webp", "WEBP", lossless=True)
    round_badge(icon).save(folder / "ic_launcher_round.webp", "WEBP", lossless=True)
    # Adaptatif : 108 dp, zone toujours visible de 66 dp au centre
    canvas = int(108 * factor)
    foreground = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    inner = int(canvas * 66 / 108)
    foreground.alpha_composite(round_badge(inner), ((canvas - inner) // 2, (canvas - inner) // 2))
    foreground.save(folder / "ic_launcher_foreground.webp", "WEBP", lossless=True)

# Images affichées dans l'app
nodpi = RES / "drawable-nodpi"
nodpi.mkdir(parents=True, exist_ok=True)
round_badge(720, badge).save(nodpi / "logo_badge.webp", "WEBP", quality=92)
if len(__import__("sys").argv) > 1:  # aperçus de contrôle sur fonds clair et violet
    for name, bg in (("clair", (200, 200, 205)), ("violet", (60, 30, 110))):
        preview = Image.new("RGBA", badge.size, bg + (255,))
        preview.alpha_composite(badge)
        preview.convert("RGB").resize((360, 360)).save(Path(__import__("sys").argv[1]) / f"badge_{name}.png")
for f in sorted(RES.glob("*/ic_launcher*.webp")) + sorted(nodpi.glob("logo_*.webp")):
    print(f"{f.relative_to(RES)} {f.stat().st_size // 1024} Ko")
