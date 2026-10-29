"""
Staging + scenes for "What If Your Brain Ran 100x Faster?"
Scale contract: floor_y=880-960; figure s=1.0 is ~258px tall;
hip when seated = floor - 56*s; table top = floor - 130; chair seat = floor - 62*s.
"""
import math
from PIL import Image, ImageDraw
from core import (W, H, IW, IH, CREAM, CREAM_WARM, CREAM_D, COOL_BG, COOL_BG2,
                  INK, BLUE, BLUE_LT, RED, YELLOW, WHITE, GREY,
                  lerp, ease, clamp, draw_figure, ground, dust, wall_clock, bus, skyline)


# ------------------------------------------------------------ staging helpers
def stand(f, x, floor, s=1.0, shadow=True, **kw):
    if shadow:
        f.shadow(x, floor, 46 * s, 11 * s, 60)
    return draw_figure(f, x, floor - 102 * s, s, **kw)


def sit(f, x, floor, s=1.0, chair=True, chair_col=INK, **kw):
    hipy = floor - 56 * s
    if chair:
        f.rect(x - 52 * s, hipy + 4 * s, x + 52 * s, hipy + 18 * s, fill=CREAM_D,
               outline=chair_col, w=4, r=4)
        f.line([(x - 44 * s, hipy + 18 * s), (x - 44 * s, floor)], chair_col, 8 * s)
        f.line([(x + 44 * s, hipy + 18 * s), (x + 44 * s, floor)], chair_col, 8 * s)
        f.line([(x + 48 * s, hipy - 6 * s), (x + 54 * s, hipy + 60 * s)], chair_col, 7 * s)
    kw.setdefault("leg_l", (1.45, -1.42))
    kw.setdefault("leg_r", (1.38, -1.36))
    f.shadow(x + 10 * s, floor, 60 * s, 11 * s, 45)
    return draw_figure(f, x, hipy, s, **kw)


def table(f, x0, x1, top, legs=(0.15, 0.85), color=INK, fill=CREAM_D, thick=16):
    f.rect(x0, top, x1, top + thick, fill=fill, outline=color, w=5, r=5)
    for lx in legs:
        px = x0 + (x1 - x0) * lx
        f.line([(px, top + thick), (px + (10 if lx > 0.5 else -10), top + thick + 150)], color, 11)


def desk_row(f, x, floor, w=230, top_off=130, color=INK, fill=CREAM_D):
    f.rect(x - w / 2, floor - top_off, x + w / 2, floor - top_off + 15, fill=fill, outline=color, w=5, r=4)
    f.line([(x - w / 2 + 22, floor - top_off + 15), (x - w / 2 + 22, floor)], color, 9)
    f.line([(x + w / 2 - 22, floor - top_off + 15), (x + w / 2 - 22, floor)], color, 9)


def window_pane(f, x0, y0, x1, y1, color=INK, glass=(255, 250, 232)):
    f.rect(x0, y0, x1, y1, fill=glass, outline=color, w=7, r=5)
    f.line([((x0 + x1) / 2, y0), ((x0 + x1) / 2, y1)], color, 6)
    f.line([(x0, (y0 + y1) / 2), (x1, (y0 + y1) / 2)], color, 6)


def chair_simple(f, x, floor, s=1.0, color=INK):
    f.rect(x - 50 * s, floor - 62 * s, x + 50 * s, floor - 48 * s, fill=CREAM_D, outline=color, w=4, r=4)
    f.line([(x - 42 * s, floor - 48 * s), (x - 42 * s, floor)], color, 8 * s)
    f.line([(x + 42 * s, floor - 48 * s), (x + 42 * s, floor)], color, 8 * s)


def rim(f, x, y, s, rim_col, dark, **kw):
    """Silhouette with a subtle rim light (night scenes)."""
    draw_figure(f, x + 7, y - 5, s, color=rim_col, **kw)
    return draw_figure(f, x, y, s, color=dark, **kw)


# ================================================================ SEGMENT 1
def s1_bedroom(f, u, t, local):
    f.bg(lerp((242, 236, 224), (250, 240, 220), 0.5), CREAM_D)
    f.cam(960, 600, 1.0 + 0.16 * ease(u))
    ground(f, 820, (226, 216, 198))
    f.line([(0, 820), (1920, 820)], (206, 196, 178), 4)
    window_pane(f, 210, 250, 560, 660)
    for i in range(3):
        f.line([(240 + i * 100, 660), (330 + i * 100, 250)], (216, 208, 190), 7)
    f.line([(960, -20), (960, 120)], INK, 9)
    f.circle(960, 148, 22, fill=INK)
    ang = math.radians(local * 7.0)
    for i in range(3):
        a = ang + i * 2.094
        f.line([(960, 148), (960 + math.cos(a) * 160, 148 + math.sin(a) * 150 * 0.28)], INK, 11)
    f.shadow(1000, 815, 330, 16, 40)
    f.rect(640, 745, 1340, 790, fill=(224, 214, 198), outline=INK, w=6, r=12)
    f.rect(640, 790, 1340, 830, fill=(206, 196, 180), outline=INK, w=6, r=8)
    f.rect(700, 700, 850, 756, fill=WHITE, outline=INK, w=5, r=10)
    f.poly([(860, 745), (1340, 745), (1340, 790), (880, 790)], fill=lerp(BLUE, WHITE, 0.55), outline=INK, w=5)
    breathe = math.sin(local * 1.3) * 2.5
    draw_figure(f, 1090, 752 + breathe, 1.22,
                eyes="half", mouth="flat", lean=-0.10, head_tilt=0.12,
                arm_l=(-0.50, -0.30), arm_r=(0.60, 0.25),
                leg_l=(1.46, -1.44), leg_r=(1.40, -1.40), wristband=BLUE)
    dust(f, local, n=22, seed=3, spread=(120, 1800, 200, 760), alpha_col=(255, 255, 250))
    f.vignette(44)


def s1_clock(f, u, t, local):
    f.bg((240, 234, 222), (228, 220, 206))
    f.cam(960, 520, 1.0 + 0.05 * ease(u))
    wall_clock(f, 960, 450, 300, hand_deg=6.4 + local * 0.16, hour_deg=8.4)
    for i in range(3):
        a = math.radians(6.4 + local * 0.16 - (i + 1) * 0.30 - 90)
        f.line([(960, 450), (960 + math.cos(a) * 250, 450 + math.sin(a) * 250)], (206, 202, 194), 6)
    f.vignette(38)


def s1_friend(f, u, t, local):
    f.bg(lerp((240, 236, 228), COOL_BG, 0.40), lerp(CREAM_D, COOL_BG2, 0.40))
    f.cam(960, 620, 1.12 + 0.20 * ease(u ** 1.6))
    ground(f, 900, (216, 214, 210))
    f.line([(0, 900), (1920, 900)], (198, 196, 192), 4)
    window_pane(f, 1420, 220, 1800, 620, glass=(228, 236, 244))
    table(f, 830, 1110, 700, legs=(0.10, 0.90))
    f.rect(880, 640, 960, 702, fill=WHITE, outline=INK, w=4, r=8)
    f.line([(962, 656), (992, 670), (962, 686)], INK, 5)
    for i in range(2):
        f.arc(900 + i * 22, 615 - math.sin(local * 1.2 + i) * 5, 16, 22, 200, 340, (206, 202, 194), 4)
    talk = abs(math.sin(local * 1.05))
    stand(f, 610, 900, 1.30, eyes="open", mouth="open", mouth_open=0.35 + talk * 0.75,
          arm_l=(-0.40, -0.50), arm_r=(0.48, 0.62), lean=0.05, head_tilt=-0.10)
    stand(f, 1330, 900, 1.32, eyes="wide", mouth="flat", lean=0.14,
          arm_l=(-0.22, -0.10), arm_r=(0.20, 0.10), wristband=BLUE, head_tilt=0.12)
    for i in range(3):
        ph = (local * 0.35 + i / 3) % 1.0
        f.circle(830 + ph * 120, 700 + i * 26, 5, fill=(178, 184, 194))
    f.vignette(46)


def s1_eyes(f, u, t, local):
    f.bg(lerp(COOL_BG2, (146, 162, 186), ease(min(1, u * 1.4))), (118, 136, 162))
    f.cam(960, 540, 1.0)
    f.circle(960, 640, 430, fill=INK)
    ey = 590
    shrink = 1.0 - 0.35 * ease(clamp((u - 0.35) / 0.5))
    for sgn in (-1, 1):
        cxx = 960 + sgn * 168
        f.ellipse(cxx, ey, 100 * shrink, 128 * shrink, fill=WHITE)
        f.circle(cxx + sgn * 4, ey + 2, 30 * shrink, fill=INK)
        f.circle(cxx - 26 * shrink, ey - 42 * shrink, 9, fill=WHITE)
    ph = (math.sin(local * 2.4) + 1) / 2
    if u > 0.25:
        for sgn in (-1, 1):
            f.glowspot(960 + sgn * 168, ey, 120 + ph * 80, BLUE_LT, int(100 + 80 * ph))
    f.glowspot(960, 620, 430, (86, 136, 200), int(20 + 60 * u))
    k = clamp((u - 0.74) / 0.26)
    if k > 0:
        ov = Image.new("RGB", (IW, IH), (12, 16, 28))
        f.img = Image.blend(f.img, ov, k * 0.8)
        f.d = ImageDraw.Draw(f.img)
    f.vignette(72)


# ================================================================ SEGMENT 2
def s2_exam(f, u, t, local):
    f.bg((240, 236, 226), (222, 216, 202))
    if u < 0.34:
        f.cam(960, 620, 1.02)
        ground(f, 880, (216, 210, 196))
        f.line([(0, 880), (1920, 880)], (198, 192, 178), 4)
        window_pane(f, 1500, 180, 1840, 520, glass=(228, 236, 244))
        for x, s in ((330, 0.90), (1500, 0.90)):
            desk_row(f, x, 880, w=250)
            sit(f, x, 880, s, chair=False, eyes="open", mouth="flat", lean=0.20,
                arm_l=(0.75, 0.55), arm_r=(0.65, 0.60))
        blur = math.sin(local * 34) * 7
        desk_row(f, 960, 880, w=420, top_off=140)
        sit(f, 960, 880, 1.24, chair=False, eyes="narrow", mouth="flat", lean=0.22,
            arm_l=(0.95, 0.45 + blur * 0.012), arm_r=(0.88, 0.52 - blur * 0.012),
            wristband=BLUE, head_tilt=0.10)
        f.rect(830, 706, 1130, 748, fill=WHITE, outline=GREY, w=3, r=4)
        for i in range(4):
            f.line([(850, 716 + i * 8), (1000 + (i * 61) % 110, 716 + i * 8)], (150, 148, 142), 3)
        for i in range(3):
            f.line([(1080 + i * 12, 730 + i * 8), (1150 + i * 12, 730 + i * 8)], (206, 202, 194), 4)
    elif u < 0.68:
        k = (u - 0.34) / 0.34
        f.cam(960, 700, 1.45 + 0.15 * k)
        f.rect(640, 470, 1280, 900, fill=WHITE, outline=GREY, w=6, r=6)
        for i in range(9):
            yy = 510 + i * 40
            f.line([(690, yy), (700 + (i * 97) % 520, yy)], (118, 118, 128), 6)
        for i in range(4):
            f.line([(900 + i * 8, 560 + i * 70), (1180, 560 + i * 70)], (176, 176, 186), 4)
        if k > 0.55:
            fl = ease((k - 0.55) / 0.45)
            f.rect(640 + fl * 260, 470 - fl * 120, 1280 + fl * 260, 900 - fl * 120,
                   fill=(252, 250, 245), outline=GREY, w=6, r=6)
    else:
        k = (u - 0.68) / 0.32
        f.cam(960, 640, 1.0 + 0.06 * ease(k))
        ground(f, 880, (214, 208, 194))
        for x, s in ((360, 0.92), (620, 0.92), (1500, 0.92)):
            desk_row(f, x, 880, w=240)
            sit(f, x, 880, s, chair=False, eyes="wide", mouth="open", mouth_open=0.45,
                lean=0.22, arm_l=(0.7, 0.55), arm_r=(0.6, 0.6), head_tilt=-0.12)
        stand(f, 980, 880, 1.30, eyes="narrow", mouth="flat", lean=0.0,
             arm_l=(-0.16, -0.10), arm_r=(0.80, 0.55), wristband=BLUE, head_tilt=-0.06)
        f.rect(1050, 640, 1170, 730, fill=WHITE, outline=GREY, w=3, r=4)
        for i in range(4):
            f.line([(1062, 656 + i * 16), (1150, 656 + i * 16)], (150, 148, 142), 3)
    f.vignette(36)


def s2_chess(f, u, t, local):
    f.bg((236, 230, 218), (214, 206, 190))
    f.cam(960, 640, 1.0 + 0.14 * ease(u))
    ground(f, 880, (204, 198, 186))
    f.line([(0, 880), (1920, 880)], (186, 180, 168), 4)
    f.line([(300, -20), (300, 90)], INK, 6)
    f.poly([(240, 90), (360, 90), (390, 160), (210, 160)], fill=CREAM_D, outline=INK, w=5)
    table(f, 520, 1400, 750, legs=(0.10, 0.90))
    f.rect(830, 640, 1090, 748, fill=(250, 248, 240), outline=INK, w=4)
    for i in range(8):
        for j in range(4):
            if (i + j) % 2 == 0:
                f.rect(832 + i * 32, 642 + j * 25, 832 + (i + 1) * 32, 642 + (j + 1) * 25, fill=(208, 200, 186))
    for i in range(5):
        f.circle(852 + i * 24, 654 + (i % 2) * 9, 8, fill=INK)
        f.circle(1068 - i * 24, 726 - (i % 2) * 9, 8, fill=WHITE, outline=INK, w=3)
    k = clamp((u - 0.62) / 0.18)
    ang = ease(k) * 1.4
    kx, ky = 1068, 717
    f.line([(kx, ky), (kx + math.sin(ang) * 30, ky - math.cos(ang) * 30)], INK, 9)
    f.circle(kx + math.sin(ang) * 34, ky - math.cos(ang) * 34, 8, fill=INK)
    sit(f, 620, 880, 1.16, chair=True, eyes="narrow", mouth="flat", lean=0.26,
        arm_l=(0.55, 0.80), arm_r=(0.42, 0.95), wristband=BLUE, head_tilt=0.16)
    slump = ease(clamp((u - 0.66) / 0.22))
    sit(f, 1310, 880, 1.14, chair=True, eyes="half", mouth="frown",
        lean=-0.26 - slump * 0.22, arm_l=(-0.28, -0.10), arm_r=(0.20, 0.06),
        head_tilt=-0.22 - slump * 0.28)
    f.vignette(40)


def s2_board(f, u, t, local):
    f.bg((238, 234, 224), (220, 214, 200))
    f.cam(960, 660, 1.06 + 0.06 * ease(u))
    ground(f, 980, (210, 204, 190))
    f.rect(120, 150, 1800, 720, fill=(46, 66, 58), outline=INK, w=11, r=8)
    for i in range(5):
        f.line([(220, 250 + i * 70), (640 + (i * 137) % 380, 250 + i * 70)], (198, 212, 200), 6)
    f.line([(1150, 260), (1640, 400)], (198, 212, 200), 6)
    f.line([(1150, 400), (1640, 260)], (198, 212, 200), 6)
    stand(f, 610, 980, 1.24, eyes="wide", mouth="open", mouth_open=0.55, lean=-0.12,
         arm_l=(-0.32, -0.22), arm_r=(2.45, 0.18), head_tilt=0.20)
    stand(f, 940, 980, 1.28, eyes="narrow", mouth="flat", lean=0.05,
         arm_l=(-0.18, -0.12), arm_r=(2.50, 0.05), wristband=BLUE, head_tilt=-0.12)
    turn = ease(clamp((u - 0.30) / 0.45))
    for hx, s in ((250, 1.25), (1560, 1.25), (1660, 1.12)):
        chair_simple(f, hx, 1080, s)
        draw_figure(f, hx, 1080 - 56 * s, s, eyes="wide", mouth="open", mouth_open=0.45,
                    lean=0.12 * turn, arm_l=(0.75, 0.45), arm_r=(0.65, 0.5),
                    head_tilt=-0.40 * turn - 0.05)
    f.vignette(42)


# ================================================================ SEGMENT 3
def s3_street(f, u, t, local):
    f.bg((238, 234, 224), (226, 230, 234))
    f.cam(720 + local * 34, 620, 1.0)
    skyline(f, 640, (220, 214, 202), seed=5)
    ground(f, 800, (208, 204, 196))
    f.rect(-500, 900, 2500, 1080, fill=(92, 92, 98))
    for i in range(9):
        f.rect(-400 + i * 320, 985, -290 + i * 320, 1000, fill=(230, 228, 218))
    for i in range(6):
        f.rect(430 + i * 130, 872, 470 + i * 130, 962, fill=(242, 240, 232))
    f.rect(-500, 856, 2500, 878, fill=(224, 220, 210))
    bus(f, 2100 - local * 120, 900, 0.95)
    step = ease(clamp(local / 2.4))
    stand(f, 700, 856 + step * 44, 1.24, eyes="narrow", mouth="flat",
         lean=0.06 + step * 0.05,
         leg_l=(-0.34 * step, 0.12), leg_r=(0.46 * step, -0.16),
         arm_l=(-0.28, -0.14), arm_r=(0.36, 0.12), wristband=BLUE, head_tilt=0.04)
    for i in range(4):
        f.line([(470 - i * 40, 700 + i * 42), (610 - i * 40, 700 + i * 42)], (216, 214, 206), 5)
    f.vignette(38)


def s3_macro(f, u, t, local):
    """Rusted bolt, macro — the detail nobody else would notice."""
    f.bg((230, 226, 216), (200, 198, 192))
    f.cam(960, 540, 1.02 + 0.16 * u)
    f.rect(600, 260, 1320, 820, fill=(180, 176, 168), outline=INK, w=10, r=16)
    f.circle(960, 540, 168, fill=(200, 194, 184), outline=INK, w=9)
    f.circle(960, 540, 106, fill=(172, 164, 154), outline=INK, w=7)
    for i in range(6):
        a = math.radians(i * 60 + 15)
        f.line([(960 + math.cos(a) * 110, 540 + math.sin(a) * 110),
                (960 + math.cos(a) * 158, 540 + math.sin(a) * 158)], (122, 114, 106), 9)
    for i in range(34):
        rx = ((i * 197) % 700) / 700.0
        ry = ((i * 331) % 540) / 540.0
        f.circle(620 + rx * 680, 280 + ry * 520, 3 + (i % 4) * 2.8, fill=RED)
    cr = ease(clamp(u * 1.4))
    f.line([(600, 330), (600 + 40 * cr, 300), (620 + 90 * cr, 340), (640 + 150 * cr, 300)],
           (110, 104, 96), 6)
    f.vignette(44)


def s3_sign(f, u, t, local):
    """The sign comes down; Kai is already moving."""
    k = u
    f.bg((234, 230, 222), (212, 210, 204))
    f.cam(880, 620, 1.0 + 0.04 * k)
    skyline(f, 700, (224, 218, 206), seed=9)
    ground(f, 900, (208, 204, 196))
    f.rect(0, 900, 1920, 1080, fill=(98, 98, 104))
    tilt = ease(k) * 0.6
    bx, by = 430, 120
    f.line([(bx, by), (bx + 210, by + 6)], INK, 16)
    sx, sy = 640, 150 + tilt * 150
    f.poly([(sx, sy), (sx + 540, sy + tilt * 60), (sx + 560, sy + 300 + tilt * 40),
            (sx + 20, sy + 290)], fill=(220, 212, 196), outline=INK, w=9)
    for i in range(3):
        f.line([(sx + 60, sy + 70 + i * 70), (sx + 480, sy + 62 + i * 70 + tilt * 50)],
               (144, 138, 128), 9)
    for i in range(10):
        yy = 300 + ((local * 170 + i * 78) % 560)
        f.line([(620 + i * 42, yy), (620 + i * 42, yy + 46)], (154, 148, 138), 5)
    pull = ease(clamp((k - 0.10) / 0.60))
    rx = 1080 + pull * 170
    stand(f, rx, 900, 1.28, eyes="wide", mouth="flat",
         lean=-0.46 - pull * 0.10, arm_l=(-0.25, -0.12), arm_r=(2.28, 0.10),
         leg_l=(-0.62, 0.22), leg_r=(0.52, -0.26), wristband=BLUE)
    stand(f, rx + 250 + pull * 130, 906, 1.08, eyes="wide", mouth="open", mouth_open=0.85,
         lean=-0.30, arm_l=(-1.05, -0.35), arm_r=(-0.85, -0.25),
         leg_l=(-0.55, 0.30), leg_r=(0.50, -0.30))
    for i in range(6):
        f.line([(rx - 300 + i * 14, 660 + i * 36), (rx - 90 + i * 14, 660 + i * 36)],
               (208, 204, 196), 5)
    f.vignette(44)


def s3_crowd(f, u, t, local):
    """They gather. He stands in the middle of it, unbothered."""
    f.bg((230, 226, 216), (206, 204, 198))
    f.cam(960, 640, 1.34 - 0.30 * ease(u))
    skyline(f, 660, (218, 212, 200), seed=11)
    ground(f, 880, (208, 204, 196))
    f.rect(0, 880, 1920, 1080, fill=(102, 102, 108))
    f.glowspot(960, 760, 460, (255, 238, 196), 90)
    for hx, s in ((230, 1.0), (450, 1.06), (1490, 1.06), (1730, 1.0), (720, 0.94), (1240, 0.94)):
        stand(f, hx, 900, s, eyes="open", mouth="open", mouth_open=0.4,
             arm_l=(-0.24, -0.10), arm_r=(0.24, 0.10), lean=0.05)
    stand(f, 960, 920, 1.34, eyes="narrow", mouth="flat", lean=0.0,
         arm_l=(-0.55, -0.85), arm_r=(0.55, 0.85), wristband=BLUE)
    f.vignette(48)


# ================================================================ SEGMENT 4
def s4_dinner(f, u, t, local):
    f.bg((238, 232, 220), lerp((226, 220, 206), (186, 198, 214), 0.55))
    f.cam(960, 670, 1.10 + 0.08 * ease(u))
    ground(f, 880, (204, 202, 198))
    f.line([(0, 880), (1920, 880)], (188, 186, 182), 4)
    window_pane(f, 130, 240, 470, 620, glass=(206, 216, 230))
    f.line([(960, -20), (960, 150)], INK, 6)
    f.poly([(900, 150), (1020, 150), (1050, 220), (870, 220)], fill=(228, 220, 204), outline=INK, w=5)
    f.glowspot(960, 300, 300, (255, 236, 190), 55)
    table(f, 430, 1490, 750, legs=(0.08, 0.92))
    for px in (740, 960, 1180):
        f.ellipse(px, 748, 62, 14, fill=WHITE, outline=INK, w=4)
    talk = math.sin(local * 2.0) > 0
    sit(f, 560, 880, 1.14, chair=True, eyes="open", mouth="open", mouth_open=0.5 if talk else 0.12,
        lean=0.14, arm_l=(-0.30, -0.38), arm_r=(0.40, 0.48), head_tilt=-0.06)
    reciting = math.sin((local + 1.7) * 2.0) > 0
    sit(f, 1250, 880, 1.20, chair=True, eyes="narrow", mouth="open", mouth_open=0.42 if reciting else 0.0,
        lean=0.10, arm_l=(0.55, 0.70), arm_r=(0.48, 0.72), wristband=BLUE, head_tilt=0.08)
    # foreground: a glass and a fork on the near edge, out of focus
    f.rect(196, 980, 236, 1092, fill=(232, 236, 240), outline=(150, 156, 164), w=3, r=6)
    f.line([(300, 1000), (300, 1080)], (62, 62, 70), 6)
    for i in range(3):
        f.line([(292 + i * 8, 968), (292 + i * 8, 1002)], (62, 62, 70), 4)
    ov = Image.new("RGB", (IW, IH), (140, 160, 190))
    f.img = Image.blend(f.img, ov, 0.05 + 0.10 * u)
    f.d = ImageDraw.Draw(f.img)
    f.vignette(46)


def s4_hall(f, u, t, local):
    f.bg((200, 208, 222), (176, 188, 206))
    f.cam(960, 620, 1.0)
    f.rect(0, 0, 1920, 620, fill=(206, 214, 226))
    f.poly([(-300, 1080), (2200, 1080), (1150, 620), (800, 620)], fill=(168, 178, 196))
    f.line([(0, 620), (1920, 620)], (140, 152, 172), 6)
    for i in range(6):
        yy = 640 + i * 88
        w = 60 + i * 90
        f.line([(960 - w - 340, yy), (960 + w + 340, yy)], (178, 188, 204), 3)
    f.ellipse(960, 1030, 300, 40, fill=(150, 162, 182))
    f.poly([(0, 0), (800, 620), (800, 620), (0, 620)], fill=(206, 214, 226))
    f.poly([(1920, 0), (1150, 620), (1150, 620), (1920, 620)], fill=(202, 210, 222))
    for i in range(4):
        x0 = 40 + i * 150
        h = 330 - i * 46
        f.rect(x0, 640 - h, x0 + 130, 900 - i * 40, fill=(206, 214, 228), outline=(120, 130, 148), w=4, r=3)
        f.rect(x0 + 14, 660 - h, x0 + 116, 676 - h, fill=(180, 190, 206))
        f.line([(x0 + 106, 710 - i * 20), (x0 + 106, 800 - i * 26)], (110, 120, 138), 5)
    for i in range(4):
        x1 = 1880 - i * 150
        h = 330 - i * 46
        f.rect(x1 - 130, 640 - h, x1, 900 - i * 40, fill=(208, 216, 230), outline=(120, 130, 148), w=4, r=3)
        f.rect(x1 - 116, 660 - h, x1 - 14, 676 - h, fill=(182, 192, 208))
        f.line([(x1 - 106, 710 - i * 20), (x1 - 106, 800 - i * 26)], (110, 120, 138), 5)
    for hx, s in ((300, 1.10), (1620, 1.10), (560, 0.96), (1420, 0.96)):
        wob = math.sin(local * 0.4 + hx) * 2
        stand(f, hx + wob, 930, s, eyes="open", mouth="flat", lean=0.05,
             arm_l=(-0.20, -0.10), arm_r=(0.22, 0.12))
    kx = 120 + ease(clamp(local / 12.5)) * 1520
    step = math.sin(local * 6.5)
    stand(f, kx, 960, 1.26, eyes="narrow", mouth="flat", lean=0.12,
         arm_l=(-0.55 * step, -0.18), arm_r=(0.55 * step, 0.18),
         leg_l=(0.55 * step, 0.28 * max(0, step)), leg_r=(-0.55 * step, 0.28 * max(0, -step)),
         wristband=BLUE)
    for i in range(4):
        f.line([(kx - 130 - i * 34, 700 + i * 46), (kx - 50 - i * 34, 700 + i * 46)],
               (200, 208, 220), 5)
    f.vignette(52)


def s4_alone(f, u, t, local):
    f.bg((26, 34, 58), (14, 18, 32))
    f.cam(960, 640, 1.02 + 0.14 * ease(u))
    f.poly([(960, 40), (640, 1000), (1280, 1000)], fill=(60, 70, 100))
    f.glowspot(960, 260, 260, (255, 240, 200), 150)
    f.ellipse(960, 900, 470, 66, fill=(70, 82, 112))
    f.rect(280, 780, 1640, 822, fill=(52, 62, 88), outline=(22, 28, 46), w=4, r=6)
    f.line([(400, 822), (400, 980)], (22, 28, 46), 14)
    f.line([(1520, 822), (1520, 980)], (22, 28, 46), 14)
    f.line([(960, 0), (960, 110)], (116, 126, 152), 6)
    f.poly([(900, 110), (1020, 110), (1052, 176), (868, 176)], fill=(30, 38, 60), outline=(120, 130, 156), w=4)
    for cx in (520, 1400):
        chair_simple(f, cx, 940, 1.0, color=(58, 68, 94))
    breathe = math.sin(local * 1.05) * 2.5
    rim(f, 960, 786 + breathe, 1.2, (86, 100, 138), (16, 16, 22),
        eyes="half", mouth="frown", lean=0.24, arm_l=(0.85, 0.55), arm_r=(0.80, 0.55),
        wristband=(44, 62, 100), head_tilt=0.18)
    f.vignette(92)


# ================================================================ SEGMENT 5
def s5_night(f, u, t, local):
    f.bg((30, 36, 58), (17, 21, 36))
    f.cam(980, 640, 1.02 + 0.09 * ease(u))
    ground(f, 900, (30, 36, 56))
    f.line([(1300, 150), (1300, 300)], (92, 98, 122), 5)
    f.poly([(1250, 300), (1350, 300), (1374, 358), (1226, 358)], fill=(58, 56, 64), outline=(152, 142, 122), w=3)
    f.glowspot(1300, 360, 300, (255, 216, 154), 120)
    f.rect(700, 760, 1560, 812, fill=(52, 58, 84), outline=(26, 32, 52), w=6, r=12)
    f.rect(700, 812, 1560, 852, fill=(42, 48, 72), outline=(26, 32, 52), w=6, r=8)
    f.rect(1466, 660, 1560, 764, fill=(62, 68, 96), outline=(26, 32, 52), w=5, r=10)
    f.rect(760, 700, 920, 762, fill=(108, 114, 138), outline=(52, 58, 78), w=4, r=10)
    f.ellipse(1140, 856, 300, 18, fill=(44, 52, 78))
    swing = math.sin(local * 2.3) * 0.20
    draw_figure(f, 1210, 756, 1.16, eyes="open", mouth="flat", lean=-0.04,
                arm_l=(-0.28, -0.18), arm_r=(0.28, 0.18),
                leg_l=(1.44, -1.38 + swing), leg_r=(1.38, -1.34 - swing),
                hair=RED, head_tilt=0.14, color=(22, 22, 30))
    rim(f, 452, 918, 1.95, (86, 100, 138), (12, 14, 22),
        eyes="open", mouth="flat", lean=0.04, arm_l=(-0.13, -0.07), arm_r=(0.15, 0.09),
        leg_l=(-0.09, 0.04), leg_r=(0.11, -0.05), wristband=(52, 74, 116))
    f.vignette(96)


def s5_wait(f, u, t, local):
    f.bg((28, 34, 54), (16, 20, 34))
    f.cam(1180, 680, 1.14 + 0.13 * ease(u))
    f.line([(1310, 130), (1310, 290)], (90, 96, 120), 5)
    f.poly([(1260, 290), (1360, 290), (1384, 350), (1236, 350)], fill=(56, 54, 62), outline=(152, 142, 122), w=3)
    f.glowspot(1310, 350, 300, (255, 216, 154), 130)
    f.rect(700, 760, 1620, 812, fill=(52, 58, 84), outline=(26, 32, 52), w=6, r=12)
    f.rect(700, 812, 1620, 852, fill=(42, 48, 72), outline=(26, 32, 52), w=6, r=8)
    f.ellipse(1180, 856, 320, 18, fill=(44, 52, 78))
    breathe = math.sin(local * 0.9) * 2
    draw_figure(f, 1190, 756 + breathe, 1.20, eyes="wide", mouth="flat", lean=-0.02,
                arm_l=(-0.25, -0.14), arm_r=(0.25, 0.14),
                leg_l=(1.44, -1.38), leg_r=(1.38, -1.34), hair=RED, head_tilt=0.05,
                color=(22, 22, 30))
    rim(f, 620, 918, 1.70, (78, 92, 128), (11, 13, 20),
        eyes="open", mouth="flat", lean=0.02, arm_l=(-0.12, -0.06), arm_r=(0.14, 0.08),
        leg_l=(-0.09, 0.04), leg_r=(0.11, -0.05), wristband=(52, 74, 116))
    f.vignette(94)


def s5_choose(f, u, t, local):
    warm = ease(u)
    f.bg(lerp((28, 34, 54), (250, 238, 216), warm), lerp((16, 20, 34), (232, 222, 204), warm))
    f.cam(1080, 680, 1.10 - 0.05 * warm)
    lx = 1240
    f.line([(lx, 130), (lx, 300)], lerp((90, 96, 120), INK, warm), 5)
    f.poly([(lx - 44, 300), (lx + 44, 300), (lx + 66, 360), (lx - 66, 360)],
           fill=lerp((56, 54, 62), (244, 230, 200), warm),
           outline=lerp((152, 142, 122), INK, warm), w=3)
    f.glowspot(lx, 400, 300 + 120 * warm, (255, 224, 168), int(130 - 30 * warm))
    f.rect(700, 760, 1620, 812, fill=lerp((52, 58, 84), (228, 218, 200), warm),
           outline=lerp((26, 32, 52), INK, warm), w=6, r=12)
    f.rect(700, 812, 1620, 854, fill=lerp((42, 48, 72), (212, 202, 184), warm),
           outline=lerp((26, 32, 52), INK, warm), w=6, r=8)
    f.rect(1500, 660, 1620, 764, fill=lerp((62, 68, 96), CREAM_D, warm),
           outline=lerp((26, 32, 52), INK, warm), w=5, r=10)
    ink = lerp((22, 22, 30), (26, 26, 32), warm)
    swing = math.sin(local * 1.7) * 0.09
    draw_figure(f, 1330, 756, 1.18, eyes="open", mouth="smile" if warm > 0.7 else "flat",
                lean=-0.03, arm_l=(-0.28, -0.18), arm_r=(0.28, 0.18),
                leg_l=(1.44, -1.36 + swing), leg_r=(1.38, -1.32 - swing),
                hair=RED, head_tilt=0.10, color=ink)
    drop = ease(clamp(local / 3.0))
    draw_figure(f, 1030, 756 - 24 * (1 - drop), 1.24, eyes="half", mouth="flat",
                lean=0.16 + 0.05 * drop, arm_l=(0.72, 0.34), arm_r=(0.60, 0.30),
                leg_l=(1.44, -1.42), leg_r=(1.38, -1.36), wristband=BLUE,
                head_tilt=-0.10 * drop, color=ink)
    f.vignette(int(92 - 48 * warm))


# ================================================================ SEGMENT 6
def s6_bench(f, u, t, local):
    f.bg((252, 246, 230), (240, 232, 214))
    f.cam(960, 690, 1.42 + 0.08 * ease(u))
    ground(f, 880, (224, 216, 198))
    f.line([(0, 880), (1920, 880)], (208, 200, 182), 4)
    # background tree
    f.circle(1740, 250, 158, fill=(216, 208, 188))
    f.rect(1714, 250, 1766, 700, fill=(202, 194, 174))
    # path
    f.ellipse(960, 890, 620, 26, fill=(216, 208, 190))
    sK, sM = 0.98, 0.90
    seat = 880 - 62 * sK
    # backrest (behind figures)
    f.line([(700, seat + 4), (672, seat - 118)], INK, 11)
    f.line([(1222, seat + 4), (1250, seat - 118)], INK, 11)
    for i in range(2):
        f.rect(672, seat - 118 + i * 34, 1250, seat - 100 + i * 34, fill=CREAM_D, outline=INK, w=5, r=6)
    # seat + legs
    f.shadow(960, 886, 300, 13, 45)
    f.rect(690, seat, 1230, seat + 22, fill=CREAM_D, outline=INK, w=5, r=6)
    f.line([(752, seat + 22), (744, 880)], INK, 11)
    f.line([(1168, seat + 22), (1176, 880)], INK, 11)
    f.line([(700, seat), (690, 880)], INK, 9)
    f.line([(1220, seat), (1230, 880)], INK, 9)
    hipK, hipM = 880 - 56 * sK, 880 - 56 * sM
    swing = math.sin(local * 2.1)
    # Min: talking with both hands
    sit(f, 1120, 880, sM, chair=False, eyes="open",
        mouth="open", mouth_open=0.32 + 0.28 * abs(swing), lean=-0.04,
        arm_l=(-0.70 - 0.22 * swing, -0.45), arm_r=(0.68 + 0.22 * swing, 0.45),
        hair=RED, head_tilt=0.12)
    # Kai: listening, nodding, smiling
    nod = math.sin(local * 1.4) * 0.05
    sit(f, 880, 880, sK, chair=False, eyes="open", mouth="smile", lean=0.14,
        arm_l=(0.70, 0.50), arm_r=(0.60, 0.45), wristband=BLUE, head_tilt=nod)
    # tree shadow / birds
    for i in range(3):
        bx = 300 + ((local * 26 + i * 190) % 900)
        f.arc(bx, 200 + i * 26, 16, 8, 200, 340, (188, 184, 176), 4)
    f.vignette(38)


def s6_roof(f, u, t, local):
    f.bg(lerp((192, 210, 228), (226, 228, 226), 0.6), (216, 212, 200))
    f.cam(960, 620, 1.38 - 0.36 * ease(u))
    skyline(f, 740, (198, 200, 204), seed=4)
    ground(f, 760, (184, 182, 178))
    f.line([(80, 720), (1840, 720)], INK, 9)
    for i in range(9):
        f.line([(120 + i * 210, 720), (120 + i * 210, 880)], INK, 7)
    f.rect(-500, 876, 2500, 900, fill=(170, 168, 164))
    for i in range(6):
        cx = ((local * 70 + i * 340) % 2200) - 120
        f.rect(cx, 830, cx + 56, 846, fill=(72, 74, 82), r=4)
    stand(f, 960, 900, 1.26, eyes="open", mouth="flat", lean=0.0,
         arm_l=(-0.10, -0.05), arm_r=(0.10, 0.05), wristband=BLUE, head_tilt=0.05)
    f.vignette(36)


def s6_kitchen(f, u, t, local):
    f.bg(lerp((250, 244, 226), CREAM, ease(u)), lerp((238, 230, 212), CREAM_WARM, ease(u)))
    f.cam(980, 700, 1.22 + 0.10 * ease(u))
    ground(f, 880, (226, 218, 200))
    f.line([(0, 880), (1920, 880)], (210, 202, 184), 4)
    window_pane(f, 1370, 240, 1700, 560, glass=(236, 240, 244))
    f.rect(280, 750, 1640, 792, fill=CREAM_D, outline=INK, w=6, r=6)
    f.rect(280, 792, 1640, 900, fill=(234, 226, 208), outline=INK, w=5, r=4)
    for dx in (520, 860, 1200):
        f.rect(dx, 812, dx + 220, 890, fill=(242, 234, 216), outline=INK, w=4, r=6)
        f.circle(dx + 180, 840, 7, fill=INK)
    f.rect(370, 672, 460, 752, fill=(208, 200, 186), outline=INK, w=5, r=12)
    f.line([(370, 700), (336, 684)], INK, 8)
    for i in range(2):
        f.arc(396 + i * 16, 648 - math.sin(local * 1.6 + i) * 5, 15, 24, 200, 340, (212, 208, 200), 4)
    passing = ease(clamp(local / 3.0))
    s_min, a1, a2 = 1.06, 2.30, 0.18
    hip = 880 - 102 * s_min
    sh_y = hip - 0.86 * 100 * s_min
    sh_x = 1250
    ex = sh_x + math.sin(a1) * 48 * s_min
    ey = sh_y + math.cos(a1) * 48 * s_min
    hx = ex + math.sin(a1 + a2) * 46 * s_min
    hy = ey + math.cos(a1 + a2) * 46 * s_min
    stand(f, sh_x, 880, s_min, eyes="happy", mouth="smile", lean=-0.08,
         arm_l=(-0.20, -0.10), arm_r=(a1, a2), hair=RED, head_tilt=0.12)
    cxx = hx * (1 - passing) + 1030 * passing
    cyy = hy * (1 - passing) + 640 * passing
    f.rect(cxx - 26, cyy - 4, cxx + 26, cyy + 46, fill=WHITE, outline=INK, w=4, r=8)
    f.circle(cxx + 50, cyy + 20, 9, fill=None, outline=INK, w=6)
    stand(f, 800, 880, 1.24, eyes="happy", mouth="smile", lean=0.06,
         arm_l=(-0.14, -0.08), arm_r=(0.62, 0.55), wristband=BLUE, head_tilt=-0.04)
    f.glowspot(960, 660, 460, (255, 232, 190), 60)
    f.vignette(32)
    k = clamp((u - 0.90) / 0.10)
    if k > 0:
        ov = Image.new("RGB", (IW, IH), CREAM)
        f.img = Image.blend(f.img, ov, k)


# ================================================================ extra beats
def s2_hero(f, u, t, local):
    """'You are untouchable.' — low angle, slow push, empty desks behind him."""
    f.bg((236, 232, 222), (208, 202, 190))
    f.cam(960, 620, 1.10 + 0.22 * ease(u))
    ground(f, 940, (206, 200, 188))
    # blurred desks receding
    for i, (dx, s) in enumerate(((250, 0.7), (470, 0.8), (1560, 0.8), (1740, 0.7))):
        desk_row(f, dx, 900, w=200 * s)
        sit(f, dx, 900, s, chair=False, eyes="open", mouth="flat", lean=0.2,
            arm_l=(0.7, 0.5), arm_r=(0.6, 0.55))
    f.glowspot(960, 520, 520, (120, 165, 215), 80)
    stand(f, 960, 1000, 1.55, eyes="narrow", mouth="flat", lean=0.0,
         arm_l=(-0.10, -0.05), arm_r=(0.10, 0.05), wristband=BLUE, head_tilt=-0.02)
    f.vignette(52)


def s3_crowd_close(f, u, t, local):
    """'You start believing you were built for this.' — crowd in the foreground, Kai beyond them."""
    f.bg((234, 232, 226), (206, 206, 204))
    f.cam(960, 560, 1.18 + 0.12 * ease(u))
    skyline(f, 700, (214, 212, 206), seed=21)
    ground(f, 830, (188, 188, 186))
    f.rect(-500, 830, 2500, 1080, fill=(178, 178, 176))
    f.glowspot(960, 640, 520, (255, 240, 208), 70)
    # Kai: hands behind back, chin level, looking straight through the camera
    ph = (math.sin(local * 2.0) + 1) / 2
    draw_figure(f, 960, 856, 1.34, eyes="narrow", mouth="flat", lean=0.0,
                arm_l=(-0.34, -0.78), arm_r=(0.34, 0.78),
                wristband=BLUE, head_tilt=0.04)
    f.glowspot(960, 520, 150 + ph * 40, (120, 180, 240), int(24 + 16 * ph))
    # crowd from behind: cropped head + shoulder silhouettes across the near edge
    for cx, s2, tone in ((120, 2.9, (126, 126, 126)), (420, 3.2, (142, 142, 142)),
                         (1500, 3.2, (142, 142, 142)), (1810, 2.9, (126, 126, 126)),
                         (770, 3.6, (108, 108, 108)), (1160, 3.6, (108, 108, 108))):
        hr = 30 * s2
        hy = 1180 - hr * 1.1
        f.circle(cx, hy, hr, fill=tone)
        f.ellipse(cx, hy + hr * 1.6, hr * 2.4, hr * 1.5, fill=tone)
    f.vignette(58)
