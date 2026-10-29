"""YouTube thumbnail — 16:9. Background glow is baked BEHIND the character
so the stick figure stays pure black with a clean rim light."""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from core import W, H, IW, IH, Frame, lerp, INK, WHITE

rng = np.random.default_rng(7)

# ---------------- background: vertical gradient + radial glow (numpy)
yy, xx = np.mgrid[0:IH, 0:IW].astype(np.float32)
tnorm = yy / IH
top = np.array([8, 12, 34], dtype=np.float32)
bot = np.array([38, 18, 58], dtype=np.float32)
bg = top[None, None, :] + (bot - top)[None, None, :] * tnorm[..., None]

dist = np.sqrt((xx - IW * 0.5) ** 2 + ((yy - IH * 0.46) * 1.15) ** 2) / (IH * 0.62)
glow = np.clip(1 - dist, 0, 1) ** 2.2
glowc = np.array([70, 140, 235], dtype=np.float32)
bg = bg + (glowc[None, None, :] - bg) * (glow[..., None] * 0.75)
bg = np.clip(bg, 0, 255).astype(np.uint8)

f = Frame()
f.img = Image.fromarray(bg, "RGB")
f.d = ImageDraw.Draw(f.img)
f.glow = Image.new("RGBA", (IW, IH), (0, 0, 0, 0))
f.gd = ImageDraw.Draw(f.glow)

# ---------------- city silhouette
x = -80
i = 0
while x < 2050:
    w = 100 + int(rng.integers(50, 170))
    h = 150 + int(rng.integers(80, 360))
    f.rect(x, 840 - h, x + w, 870, fill=(16, 20, 44))
    for r in range(h // 58):
        for cc in range(w // 50):
            if (r * 3 + cc * 5 + i) % 4 == 0:
                f.rect(x + 14 + cc * 50, 840 - h + 18 + r * 58,
                       x + 27 + cc * 50, 840 - h + 36 + r * 58, fill=(84, 132, 196))
    x += w + 18
    i += 1
f.rect(-100, 866, 2100, 1100, fill=(10, 12, 30))

# ---------------- giant head + shoulders (pure black)
CX, CY, R = 960, 455, 330
f.ellipse(CX, 1060, 430, 235, fill=(8, 8, 12))
f.circle(CX, CY, R, fill=(8, 8, 12))

# rim light: smooth stroke arc along the upper-left skull
f.arc(CX, CY, R - 8, R - 8, 158, 322, (70, 120, 190), 20)
f.arc(CX, CY, R - 12, R - 12, 172, 292, (128, 180, 245), 10)
f.arc(CX, CY, R - 18, R - 18, 182, 268, (190, 225, 255), 5)

# ---------------- eyes
ey = CY - 30
for sgn in (-1, 1):
    cx = CX + sgn * 132
    f.ellipse(cx, ey, 88, 102, fill=WHITE)
    f.circle(cx + sgn * 10, ey + 10, 32, fill=INK)
    f.circle(cx - 24, ey - 38, 9, fill=WHITE)

# straining brows (cut into the head as negative space)
f.line([(CX - 206, ey - 172), (CX - 62, ey - 146)], (128, 178, 240), 13)
f.line([(CX + 206, ey - 172), (CX + 62, ey - 146)], (128, 178, 240), 13)

# tense, open mouth
f.ellipse(CX, CY + 172, 64, 34, fill=WHITE)
f.line([(CX - 52, CY + 168), (CX + 52, CY + 168)], (8, 8, 12), 7)

img = f.img.resize((W, H), Image.LANCZOS)

# ---------------- cold eye bloom (small, over the eyes only)
gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
gd = ImageDraw.Draw(gl)
for sgn in (-1, 1):
    gd.ellipse([CX + sgn * 132 - 110, ey - 110, CX + sgn * 132 + 110, ey + 110],
               fill=(120, 195, 255, 55))
gl = gl.filter(ImageFilter.GaussianBlur(34))
img = Image.alpha_composite(img.convert("RGBA"), gl).convert("RGB")

# ---------------- speed streaks
d = ImageDraw.Draw(img, "RGBA")
for i in range(34):
    yy2 = int(rng.integers(60, 880))
    x0 = int(rng.integers(-280, 900))
    ln = int(rng.integers(150, 560))
    d.line([(x0, yy2), (x0 + ln, yy2)], fill=(170, 210, 255, 22), width=int(rng.integers(2, 6)))

# ---------------- headline
font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
txt = "100X FASTER."
fs = 208
font = ImageFont.truetype(font_path, fs)
while d.textlength(txt, font=font) > W - 110:
    fs -= 8
    font = ImageFont.truetype(font_path, fs)
tw = int(d.textlength(txt, font=font))
tx, ty = (W - tw) // 2, H - fs - 52

lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ld = ImageDraw.Draw(lay)
for off in range(22, 0, -2):
    ld.text((tx + off * 0.42, ty + off), txt, font=font, fill=(6, 8, 20, 245))
ld.text((tx, ty), txt, font=font, fill=(255, 212, 58, 255))
img = Image.alpha_composite(img.convert("RGBA"), lay).convert("RGB")
img = img.filter(ImageFilter.UnsharpMask(radius=2, percent=50))

# ---------------- gentle vignette
vig = Image.new("L", (W, H), 0)
vd = ImageDraw.Draw(vig)
vd.ellipse([-W * 0.22, -H * 0.24, W * 1.22, H * 1.24], fill=255)
vig = vig.filter(ImageFilter.GaussianBlur(150))
img = Image.composite(img, Image.new("RGB", (W, H), (0, 0, 0)), vig.point(lambda p: 238 + p * 17 // 255))

img.resize((1280, 720), Image.LANCZOS).save(
    "/home/user/DHUN/production/what-if-brain-100x/thumbnail_100x.jpg", quality=93)
print("thumbnail saved")
