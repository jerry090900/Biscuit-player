"""Resize/pad the supplied artwork; never redraw, recolor, crop or synthesize it.

Developer-only helper: python scripts/prepare_biscuit_branding.py
Requires Pillow and DejaVu Sans Bold (neither is an Android dependency).
"""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = Image.open(ROOT / "artwork/biscuit/source.jpg").convert("RGB")


def padded(size, height_fraction):
    canvas = Image.new("RGB", (size, size), "black")
    height = round(size * height_fraction)
    width = round(height * SOURCE.width / SOURCE.height)
    image = SOURCE.resize((width, height), Image.Resampling.LANCZOS)
    canvas.paste(image, ((size - width) // 2, (size - height) // 2))
    return canvas


# The full biscuit (including its original black margin) stays inside the
# adaptive icon's 66dp safe circle: visible biscuit about 54dp x 37dp,
# diagonal < 66dp on a 108dp canvas. No source pixels are cropped.
adaptive = RES / "drawable-xxxhdpi"
adaptive.mkdir(exist_ok=True)
padded(432, 70 / 108).save(adaptive / "biscuit_launcher_foreground.png")

for density, size in [("ldpi", 36), ("mdpi", 48), ("hdpi", 72),
                      ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    folder = RES / f"mipmap-{density}"
    # Use the same safe composition for legacy round and square launchers.
    for name in ["ic_launcher", "ic_launcher_round"]:
        padded(size, 0.9).save(folder / f"{name}.webp", lossless=True)

for name in ["ic_launcher-web.png", "ic_launcher-playstore.png"]:
    padded(512, 0.9).save(ROOT / "app/src/main" / name)

# A dedicated splash composition keeps the caption below the image on both
# AndroidX's pre-31 splash and Android 12+ (no second timed splash Activity).
# All artwork + caption fit inside the 192dp safe circle on a 288dp canvas.
splash = Image.new("RGB", (1152, 1152), "black")
height = 540  # 135dp, full source retained
width = round(height * SOURCE.width / SOURCE.height)
splash.paste(SOURCE.resize((width, height), Image.Resampling.LANCZOS),
             ((1152 - width) // 2, 4 * 42))
draw = ImageDraw.Draw(splash)
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 44)
draw.text((576, 4 * 190), "BISCUIT PLAYER", font=font, fill="white", anchor="mt")
splash.save(adaptive / "biscuit_splash.png")
