"""
"What If Your Brain Ran 100x Faster?" — procedural stickman animation renderer.
Flat 2D vector-style frames drawn with Pillow, piped to ffmpeg (x264).
1920x1080, 30fps, 6 x 30s segments = 180s.
"""
import math, sys, os, argparse
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080
SS = 1.5                      # supersample factor
IW, IH = int(W * SS), int(H * SS)
FPS = 30
SEG = 30.0
NSEG = 6

# ---------------------------------------------------------------- palette
CREAM      = (245, 240, 230)
CREAM_WARM = (250, 238, 216)
CREAM_D    = (231, 222, 206)
COOL_BG    = (208, 216, 226)
COOL_BG2   = (188, 200, 214)
NAVY       = (20, 25, 42)
NAVY2      = (30, 38, 62)
INK        = (26, 26, 32)
BLUE       = (58, 110, 165)
BLUE_LT    = (108, 162, 214)
RED        = (181, 72, 61)
YELLOW     = (226, 178, 74)
WHITE      = (255, 255, 255)
GREY       = (170, 168, 162)


_GRAD_CACHE = {}


def _GRAD():
    if "g" not in _GRAD_CACHE:
        import numpy as np
        n = 128
        yy, xx = np.mgrid[0:n, 0:n]
        d = np.sqrt((xx - n / 2) ** 2 + (yy - n / 2) ** 2) / (n / 2)
        a = np.clip(1 - d, 0, 1) ** 1.9 * 255
        _GRAD_CACHE["g"] = Image.fromarray(a.astype("uint8"), "L")
    return _GRAD_CACHE["g"]


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ease(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def clamp(t, a=0.0, b=1.0):
    return max(a, min(b, t))


# ---------------------------------------------------------------- frame
class Frame:
    """Canvas wrapper: supersampled drawing + camera transform + glow layer."""

    def __init__(self):
        self.img = Image.new("RGB", (IW, IH), CREAM)
        self.d = ImageDraw.Draw(self.img)
        self.glow = Image.new("RGBA", (IW, IH), (0, 0, 0, 0))
        self.gd = ImageDraw.Draw(self.glow)
        self.sh = Image.new("RGBA", (IW, IH), (0, 0, 0, 0))
        self.sd = ImageDraw.Draw(self.sh)
        self.cx, self.cy, self.zoom = W / 2, H / 2, 1.0
        self.shake = (0.0, 0.0)

    # camera
    def cam(self, cx=None, cy=None, zoom=None):
        if cx is not None: self.cx = cx
        if cy is not None: self.cy = cy
        if zoom is not None: self.zoom = zoom
        return self

    def P(self, x, y):
        z = self.zoom
        sx = (x - self.cx) * z + W / 2 + self.shake[0]
        sy = (y - self.cy) * z + H / 2 + self.shake[1]
        return (sx * SS, sy * SS)

    def sc(self, v):
        return v * self.zoom * SS

    # primitives (world coords)
    def line(self, pts, color, w, cap=True):
        p = [self.P(*q) for q in pts]
        ww = max(1, int(round(self.sc(w))))
        self.d.line(p, fill=color, width=ww, joint="curve")
        if cap:
            r = ww / 2
            for q in (p[0], p[-1]):
                self.d.ellipse([q[0] - r, q[1] - r, q[0] + r, q[1] + r], fill=color)

    def circle(self, x, y, r, fill=None, outline=None, w=0):
        p = self.P(x, y)
        rr = self.sc(r)
        self.d.ellipse([p[0] - rr, p[1] - rr, p[0] + rr, p[1] + rr],
                       fill=fill, outline=outline, width=max(1, int(self.sc(w))) if w else 0)

    def ellipse(self, x, y, rx, ry, fill=None, outline=None, w=0):
        p = self.P(x, y)
        self.d.ellipse([p[0] - self.sc(rx), p[1] - self.sc(ry),
                        p[0] + self.sc(rx), p[1] + self.sc(ry)],
                       fill=fill, outline=outline, width=max(1, int(self.sc(w))) if w else 0)

    def rect(self, x0, y0, x1, y1, fill=None, outline=None, w=0, r=0):
        a, b = self.P(x0, y0), self.P(x1, y1)
        box = [min(a[0], b[0]), min(a[1], b[1]), max(a[0], b[0]), max(a[1], b[1])]
        rad = int(self.sc(r))
        if rad > 1:
            self.d.rounded_rectangle(box, radius=rad, fill=fill, outline=outline,
                                     width=max(1, int(self.sc(w))) if w else 0)
        else:
            self.d.rectangle(box, fill=fill, outline=outline,
                             width=max(1, int(self.sc(w))) if w else 0)

    def poly(self, pts, fill=None, outline=None, w=0):
        p = [self.P(*q) for q in pts]
        self.d.polygon(p, fill=fill, outline=outline,
                       width=max(1, int(self.sc(w))) if w else 0)

    def arc(self, x, y, rx, ry, a0, a1, color, w):
        p = self.P(x, y)
        self.d.arc([p[0] - self.sc(rx), p[1] - self.sc(ry),
                    p[0] + self.sc(rx), p[1] + self.sc(ry)],
                   a0, a1, fill=color, width=max(1, int(self.sc(w))))

    def glowspot(self, x, y, r, color, alpha):
        """Soft radial glow (true gradient, no visible rings)."""
        if alpha <= 0:
            return
        p = self.P(x, y)
        rr = self.sc(r)
        if rr < 2:
            return
        size = int(2 * rr)
        grad = _GRAD().resize((size, size), Image.BILINEAR)
        col = Image.new("RGBA", (size, size), tuple(color) + (0,))
        col.putalpha(grad.point(lambda v: int(v * alpha / 255)))
        self.glow.alpha_composite(col, (int(p[0] - rr), int(p[1] - rr)))

    def shadow(self, x, y, rx, ry, alpha=55):
        p = self.P(x, y)
        self.sd.ellipse([p[0] - self.sc(rx), p[1] - self.sc(ry),
                         p[0] + self.sc(rx), p[1] + self.sc(ry)],
                        fill=(20, 18, 16, alpha))

    # background
    def bg(self, top, bot=None):
        bot = bot or top
        steps = 24
        for i in range(steps):
            c = lerp(top, bot, i / (steps - 1))
            a, b = self.P(0, (i / steps) * H), self.P(0, ((i + 1) / steps) * H)
            self.d.rectangle([0, a[1], IW, b[1] + 1], fill=c)

    def vignette(self, strength=40):
        v = Image.new("L", (IW, IH), 0)
        vd = ImageDraw.Draw(v)
        pad_x, pad_y = IW * 0.18, IH * 0.18
        vd.ellipse([-pad_x, -pad_y, IW + pad_x, IH + pad_y], fill=255)
        v = v.filter(ImageFilter.GaussianBlur(IW // 12))
        dark = Image.new("RGB", (IW, IH), (0, 0, 0))
        self.img = Image.composite(self.img, dark, v.point(lambda p: 255 - int((255 - p) * strength / 255)))

    def finish(self):
        base = self.img.convert("RGBA")
        base = Image.alpha_composite(base, self.sh)
        base = Image.alpha_composite(base, self.glow)
        self.img = base.convert("RGB")
        return self.img.resize((W, H), Image.LANCZOS)


# ---------------------------------------------------------------- figure
DEFAULT = dict(lean=0.0, head_tilt=0.0, arm_l=(-0.18, -0.12), arm_r=(0.18, 0.12),
               leg_l=(-0.10, 0.05), leg_r=(0.10, -0.05), eyes="open", pupil=(0, 0),
               mouth="flat", mouth_open=0.0, acc=(), color=INK, wristband=None, hair=None,
               head_r=30.0, torso=100.0, arm_len=(48, 46), leg_len=(52, 50), hid=0)


def rot(a):
    return (math.sin(a), math.cos(a))


def draw_figure(f, x, y, s=1.0, **kw):
    """x,y = hip position (world). Stick figure, ~250*s px tall."""
    p = dict(DEFAULT)
    p.update(kw)
    col = p["color"]
    lw = 15.0
    hip = (x, y)
    lean = p["lean"]
    neck = (hip[0] + math.sin(lean) * p["torso"] * s,
            hip[1] - math.cos(lean) * p["torso"] * s)
    shoulder = (hip[0] + math.sin(lean) * p["torso"] * 0.86 * s,
                hip[1] - math.cos(lean) * p["torso"] * 0.86 * s)
    head_r = p["head_r"] * s
    ht = lean + p["head_tilt"]
    head = (neck[0] + math.sin(ht) * (head_r * 0.86),
            neck[1] - math.cos(ht) * (head_r * 0.86))

    # legs
    for side, (a1, a2) in (("l", p["leg_l"]), ("r", p["leg_r"])):
        d1 = rot(a1 + lean * 0.25)
        knee = (hip[0] + d1[0] * p["leg_len"][0] * s, hip[1] + d1[1] * p["leg_len"][0] * s)
        a2t = a1 + a2
        d2 = rot(a2t)
        foot = (knee[0] + d2[0] * p["leg_len"][1] * s, knee[1] + d2[1] * p["leg_len"][1] * s)
        f.line([hip, knee, foot], col, lw * s * 0.92)
        f.line([(foot[0] - 10 * s, foot[1]), (foot[0] + 13 * s, foot[1])], col, lw * s * 0.8)

    # torso
    f.line([hip, neck], col, lw * s)
    # arms
    for side, (a1, a2) in (("l", p["arm_l"]), ("r", p["arm_r"])):
        d1 = rot(a1)
        elbow = (shoulder[0] + d1[0] * p["arm_len"][0] * s,
                 shoulder[1] + d1[1] * p["arm_len"][0] * s)
        d2 = rot(a1 + a2)
        hand = (elbow[0] + d2[0] * p["arm_len"][1] * s,
                elbow[1] + d2[1] * p["arm_len"][1] * s)
        f.line([shoulder, elbow, hand], col, lw * s * 0.86)
        f.circle(hand[0], hand[1], lw * 0.42 * s, fill=col)
        if side == "r" and p["wristband"]:
            wb = (hand[0] * 0.20 + elbow[0] * 0.80, hand[1] * 0.20 + elbow[1] * 0.80)
            f.circle(wb[0], wb[1], lw * 0.62 * s, fill=p["wristband"])

    # head
    f.circle(head[0], head[1], head_r, fill=col)
    if p["hair"]:
        f.circle(head[0] + head_r * 0.72, head[1] - head_r * 0.18, head_r * 0.22, fill=p["hair"])

    # face
    eye_dx = head_r * 0.36
    ey = head[1] - head_r * 0.10
    ex = head_r * 0.055
    px, py = p["pupil"]
    for sgn in (-1, 1):
        cxx = head[0] + sgn * eye_dx
        e = p["eyes"]
        if e == "closed":
            f.line([(cxx - eye_dx * 0.62, ey), (cxx + eye_dx * 0.62, ey)], WHITE, lw * 0.30 * s)
        elif e == "happy":
            f.arc(cxx, ey + eye_dx * 0.10, eye_dx * 0.58, eye_dx * 0.52, 200, 340, WHITE, lw * 0.30 * s)
        elif e == "narrow":
            f.ellipse(cxx, ey, eye_dx * 0.52, eye_dx * 0.30, fill=WHITE)
            f.circle(cxx + px * eye_dx * 0.3, ey, eye_dx * 0.15, fill=INK)
        elif e == "wide":
            f.ellipse(cxx, ey, eye_dx * 0.62, eye_dx * 0.78, fill=WHITE)
            f.circle(cxx + px * eye_dx * 0.36, ey + py * eye_dx * 0.36, eye_dx * 0.17, fill=INK)
        elif e == "half":
            f.ellipse(cxx, ey, eye_dx * 0.55, eye_dx * 0.48, fill=WHITE)
            f.circle(cxx + px * eye_dx * 0.30, ey + eye_dx * 0.08, eye_dx * 0.15, fill=INK)
            f.line([(cxx - eye_dx * 0.58, ey - eye_dx * 0.30), (cxx + eye_dx * 0.58, ey - eye_dx * 0.30)],
                   col, lw * 0.5 * s)
        else:  # open
            f.ellipse(cxx, ey, eye_dx * 0.56, eye_dx * 0.66, fill=WHITE)
            f.circle(cxx + px * eye_dx * 0.32, ey + py * eye_dx * 0.32, eye_dx * 0.16, fill=INK)
        if ex and p["eyes"] in ("open", "wide"):
            f.circle(cxx - eye_dx * 0.22, ey - eye_dx * 0.26, eye_dx * 0.05, fill=WHITE)

    # mouth
    my = head[1] + head_r * 0.44
    mx = head[0] + math.sin(ht) * head_r * 0.05
    m = p["mouth"]
    if m == "open" or p["mouth_open"] > 0.05:
        mo = p["mouth_open"] if p["mouth_open"] > 0 else 0.6
        f.ellipse(mx, my, head_r * 0.15 * (0.7 + mo), head_r * 0.10 * (0.5 + mo), fill=WHITE)
    elif m == "smile":
        f.arc(mx, my - head_r * 0.10, head_r * 0.20, head_r * 0.18, 20, 160, WHITE, lw * 0.28 * s)
    elif m == "frown":
        f.arc(mx, my + head_r * 0.10, head_r * 0.20, head_r * 0.16, 200, 340, WHITE, lw * 0.28 * s)
    elif m == "flat":
        f.line([(mx - head_r * 0.13, my), (mx + head_r * 0.13, my)], WHITE, lw * 0.24 * s)
    return dict(head=head, hip=hip, neck=neck, shoulder=shoulder, head_r=head_r)


# ---------------------------------------------------------------- props
def ground(f, y, color, h=1e4):
    f.rect(-2000, y, 4000, y + h, fill=color)


def dust(f, t, n=26, seed=7, spread=(0, 1920, 0, 780), alpha_col=(255, 255, 255)):
    for i in range(n):
        rx = ((i * 137 + seed * 31) % 1000) / 1000.0
        ry = ((i * 311 + seed * 17) % 1000) / 1000.0
        x = spread[0] + rx * (spread[1] - spread[0])
        y = spread[2] + ry * (spread[3] - spread[2])
        y += math.sin(t * 0.6 + i) * 7
        x += math.cos(t * 0.4 + i * 2) * 5
        r = 2.0 + (i % 3) * 1.6
        f.circle(x, y, r, fill=alpha_col)


def motion_streaks(f, x, y, n=7, length=120, color=(200, 200, 200), spread=90, seed=0):
    for i in range(n):
        off = (i - n / 2) * (spread / n)
        yy = y + off + ((i * 53 + seed) % 20) - 10
        f.line([(x - length, yy), (x - length * 0.15, yy)], color, 4.5)


def wall_clock(f, x, y, r, hand_deg, hour_deg, color=INK, face=WHITE):
    f.circle(x, y, r, fill=face, outline=color, w=6)
    for i in range(12):
        a = math.radians(i * 30 - 90)
        x0, y0 = x + math.cos(a) * r * 0.78, y + math.sin(a) * r * 0.78
        x1, y1 = x + math.cos(a) * r * 0.90, y + math.sin(a) * r * 0.90
        f.line([(x0, y0), (x1, y1)], color, 5)
    a = math.radians(hand_deg - 90)
    f.line([(x, y), (x + math.cos(a) * r * 0.86, y + math.sin(a) * r * 0.86)], color, 7)
    a2 = math.radians(hour_deg - 90)
    f.line([(x, y), (x + math.cos(a2) * r * 0.55, y + math.sin(a2) * r * 0.55)], color, 10)
    f.circle(x, y, r * 0.06, fill=color)


def desk(f, x, y, w=260, h=130, color=INK, top=CREAM_D):
    f.rect(x - w / 2, y, x + w / 2, y + 16, fill=top, outline=color, w=5, r=4)
    f.line([(x - w / 2 + 18, y + 16), (x - w / 2 + 18, y + h)], color, 10)
    f.line([(x + w / 2 - 18, y + 16), (x + w / 2 - 18, y + h)], color, 10)


def paper(f, x, y, w=150, h=110, rot_=0.0, fill=WHITE, edge=GREY):
    import math as m
    pts = []
    for (dx, dy) in [(-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)]:
        rx = dx * m.cos(rot_) - dy * m.sin(rot_)
        ry = dx * m.sin(rot_) + dy * m.cos(rot_)
        pts.append((x + rx, y + ry))
    f.poly(pts, fill=fill, outline=edge, w=3)
    for i in range(4):
        yy = y - h * 0.28 + i * h * 0.18
        f.line([(x - w * 0.32, yy), (x + w * 0.30, yy)], GREY, 3)


def bus(f, x, y, s=1.0, color=INK, glass=(226, 232, 238)):
    f.rect(x, y - 230 * s, x + 520 * s, y, fill=color, r=int(26 * s))
    for i in range(4):
        f.rect(x + 40 * s + i * 118 * s, y - 168 * s, x + 138 * s + i * 118 * s, y - 84 * s, fill=glass, r=int(8 * s))
    f.rect(x + 40 * s, y - 58 * s, x + 470 * s, y - 34 * s, fill=(90, 90, 96), r=int(6 * s))
    for cxo in (110, 430):
        f.circle(x + cxo * s, y - 8 * s, 34 * s, fill=(40, 40, 46))
        f.circle(x + cxo * s, y - 8 * s, 13 * s, fill=(150, 150, 155))


def skyline(f, y, color, seed=3, alpha_w=1.0):
    x = -100
    i = 0
    while x < 2100:
        w = 120 + ((i * 271 + seed * 97) % 150)
        h = 120 + ((i * 173 + seed * 53) % 330)
        f.rect(x, y - h, x + w, y, fill=color)
        for r in range(int(h // 46)):
            for c in range(int(w // 44)):
                if (r + c + i) % 3 == 0:
                    f.rect(x + 14 + c * 44, y - h + 18 + r * 46,
                           x + 14 + c * 44 + 16, y - h + 18 + r * 46 + 22, fill=(240, 226, 170))
        x += w + 22
        i += 1


def bench(f, x, y, color=INK):
    f.rect(x - 210, y - 96, x + 210, y - 78, fill=CREAM_D, outline=color, w=5, r=6)
    f.rect(x - 210, y - 62, x + 210, y - 44, fill=CREAM_D, outline=color, w=5, r=6)
    f.line([(x - 168, y - 44), (x - 168, y)], color, 11)
    f.line([(x + 168, y - 44), (x + 168, y)], color, 11)
    f.line([(x - 190, y - 96), (x - 200, y - 250)], color, 11)
    f.line([(x + 190, y - 96), (x + 200, y - 250)], color, 11)
    f.line([(x - 200, y - 230), (x + 200, y - 248)], color, 11)


