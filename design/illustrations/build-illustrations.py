#!/usr/bin/env python3
"""Draws every Flavors illustration as plain SVG: patterns, hero, spot illustrations, stickers.

    python3 design/illustrations/build-illustrations.py

Everything is built from flat shapes in the brand palette (design/foundations/palette.md), so
the set stays consistent and recolors in one place. No text inside any SVG: the files don't
depend on a font. The heart-V is reused from the real logo (../logo/flavors-icon.svg).

Sticker rule: every sticker is its art on top of a thick white outline and a soft shadow, like a
die-cut sticker. Food only, no characters (decided with the team).
"""
import math
import pathlib
import random
import re

HERE = pathlib.Path(__file__).parent
OUT_STICKERS = HERE / "stickers"
OUT_SPOTS = HERE / "spots"
OUT_STICKERS.mkdir(exist_ok=True)
OUT_SPOTS.mkdir(exist_ok=True)

# Palette (same values as tokens). Names, not hex, in the drawing code below.
C = dict(
    paprika="#CC3300", paprika300="#F0907A", paprika50="#FDEBE5", saffron="#E58039", saffron100="#FBE3CF",
    beet="#8E1341", teal400="#009383", teal500="#007F7C", teal600="#00776F", teal700="#006D6B", teal900="#003836",
    teal100="#C2E7E2", teal50="#E6F5F3", teal300="#4DB8AC", cream="#FBF6EE", sand="#F1E9DB", sanddark="#E4D8C4",
    ink="#1B2422", white="#FFFFFF",
)

# ---- the real heart-V from the logo (three traced paths, y-flipped potrace coordinates) ----
_icon = (HERE.parent / "logo" / "flavors-icon.svg").read_text()
_paths = re.findall(r'<path fill="[^"]*" d="([^"]*)"', _icon)
HEART_L, HEART_R, HEART_V = _paths  # leaf, leaf, V
HEART_W, HEART_H = 467, 335          # size of the heart in its own units
HEART_GRADS = (
    '<linearGradient id="hgl" x1="0" y1="0.7" x2="1" y2="0.1"><stop offset="0" stop-color="#009383"/><stop offset="1" stop-color="#006D6B"/></linearGradient>'
    '<linearGradient id="hgr" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#009583"/><stop offset="1" stop-color="#006F6C"/></linearGradient>'
    '<linearGradient id="hgv" x1="1" y1="1" x2="0.45" y2="0"><stop offset="0" stop-color="#E58039"/><stop offset="0.55" stop-color="#B5433D"/><stop offset="1" stop-color="#8E1341"/></linearGradient>'
)


def heart_color(x: float = 0, y: float = 0, w: float = HEART_W, rot: float = 0):
    """The logo heart in full color, scaled to width w, top-left at x,y. Needs HEART_GRADS in <defs>."""
    s = w / HEART_W
    cx, cy = x + w / 2, y + HEART_H * s / 2
    return (f'<g transform="rotate({rot} {cx} {cy}) translate({x} {y}) scale({s})"><g transform="translate(-16,348) scale(0.025,-0.025)">'
            f'<path fill="url(#hgl)" d="{HEART_L}"/><path fill="url(#hgr)" d="{HEART_R}"/><path fill="url(#hgv)" d="{HEART_V}"/></g></g>')


def heart_flat(fill, x: float = 0, y: float = 0, w: float = HEART_W, rot: float = 0, opacity: float = 1):
    s = w / HEART_W
    cx, cy = x + w / 2, y + HEART_H * s / 2
    return (f'<g opacity="{opacity}" fill="{fill}" transform="rotate({rot} {cx} {cy}) translate({x} {y}) scale({s})"><g transform="translate(-16,348) scale(0.025,-0.025)">'
            f'<path d="{HEART_L}"/><path d="{HEART_R}"/><path d="{HEART_V}"/></g></g>')


def svg(w, h, body, defs="", extra=""):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" {extra}><defs>{defs}</defs>{body}</svg>\n'


def write(path, text):
    pathlib.Path(path).write_text(text)


# =========================== STICKERS (200 x 200) ===========================
def sticker(art, silhouette):
    """Die-cut look: soft shadow, thick white outline, then the art. silhouette = filled shapes covering the art."""
    out = f'stroke-linejoin="round" stroke-linecap="round"'
    sil_w = f'<g fill="{C["white"]}" stroke="{C["white"]}" stroke-width="18" {out}>{silhouette}</g>'
    sil_s = f'<g fill="{C["ink"]}" stroke="{C["ink"]}" stroke-width="18" opacity="0.13" transform="translate(0 5)" {out}>{silhouette}</g>'
    return sil_s + sil_w + art


def burger():
    art = (
        f'<rect x="40" y="130" width="120" height="22" rx="11" fill="{C["saffron"]}"/>'
        f'<rect x="34" y="110" width="132" height="22" rx="11" fill="{C["beet"]}"/>'
        f'<rect x="38" y="98" width="124" height="13" rx="6.5" fill="{C["paprika"]}"/>'
        f'<path d="M32 99 q10 -13 20 0 t20 0 t20 0 t20 0 t20 0 t20 0 v8 h-120 z" fill="{C["teal400"]}"/>'
        f'<path d="M34 94 C34 56 64 40 100 40 C136 40 166 56 166 94 Z" fill="{C["saffron"]}"/>'
        f'<path d="M50 70 C56 54 74 46 94 45" fill="none" stroke="{C["saffron100"]}" stroke-width="5" stroke-linecap="round" opacity="0.7"/>'
        + "".join(f'<ellipse cx="{x}" cy="{y}" rx="5.5" ry="3" transform="rotate({r} {x} {y})" fill="{C["saffron100"]}"/>'
                  for x, y, r in [(80, 66, -25), (104, 58, 15), (126, 70, 35), (96, 80, -10), (136, 86, 20), (66, 86, 30)])
    )
    return sticker(art, '<rect x="32" y="40" width="136" height="112" rx="40"/>')


def pizza():
    art = (
        f'<path d="M34 62 L166 62 L100 162 Z" fill="{C["saffron100"]}"/>'
        f'<path d="M100 162 L84 138 L116 138 Z" fill="{C["saffron"]}" opacity="0.35"/>'
        f'<rect x="28" y="40" width="144" height="26" rx="13" fill="{C["saffron"]}"/>'
        f'<rect x="38" y="46" width="60" height="5" rx="2.5" fill="{C["saffron100"]}" opacity="0.7"/>'
        + "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{C["paprika"]}"/><circle cx="{x-3}" cy="{y-3}" r="{r*0.3}" fill="{C["paprika300"]}"/>'
                  for x, y, r in [(70, 86, 12), (124, 88, 11), (98, 116, 11)])
        + f'<path d="M92 66 q10 -12 20 0 q-10 8 -20 0Z" fill="{C["teal400"]}"/><path d="M104 138 q8 -10 16 0 q-8 7 -16 0Z" fill="{C["teal400"]}"/>'
    )
    return sticker(art, '<path d="M30 52 L170 52 L100 164 Z"/>')


def cutlery():
    fork = (f'<g transform="rotate(-32 100 100)">'
            + "".join(f'<rect x="{x}" y="30" width="9" height="46" rx="4.5" fill="{C["teal500"]}"/>' for x in (78, 95.5, 113))
            + f'<rect x="78" y="66" width="44" height="22" rx="11" fill="{C["teal500"]}"/>'
            f'<rect x="93" y="80" width="14" height="88" rx="7" fill="{C["paprika"]}"/></g>')
    knife = (f'<g transform="rotate(32 100 100)"><path d="M96 30 Q126 36 120 92 H96 Z" fill="{C["teal500"]}"/>'
             f'<rect x="93" y="86" width="22" height="82" rx="11" fill="{C["saffron"]}"/></g>')
    sil = ('<g transform="rotate(-32 100 100)"><rect x="76" y="30" width="48" height="140" rx="12"/></g>'
           '<g transform="rotate(32 100 100)"><rect x="92" y="30" width="28" height="140" rx="12"/></g>')
    return sticker(fork + knife, sil)


def cup():
    art = (
        f'<path d="M110 60 L126 18 H150" fill="none" stroke="{C["saffron"]}" stroke-width="10" stroke-linecap="round" stroke-linejoin="round"/>'
        f'<path d="M56 72 H144 L134 168 a10 10 0 0 1 -10 9 H76 a10 10 0 0 1 -10 -9 Z" fill="{C["paprika"]}"/>'
        f'<path d="M60.5 106 H139.5 L136.5 136 H63.5 Z" fill="{C["cream"]}"/>'
        f'<path d="M100 128 c-9 -6 -14 -11 -14 -17 a6.5 6.5 0 0 1 14 -2.6 a6.5 6.5 0 0 1 14 2.6 c0 6 -5 11 -14 17Z" fill="{C["paprika"]}"/>'
        f'<rect x="48" y="56" width="104" height="19" rx="9.5" fill="{C["teal700"]}"/>'
        f'<rect x="56" y="61" width="30" height="4" rx="2" fill="{C["teal300"]}" opacity="0.8"/>'
    )
    return f'<g transform="translate(6 12) scale(0.92)">' + sticker(art, '<path d="M48 56 H152 V72 L134 178 H66 L48 72 Z"/><path d="M110 60 L126 18 H150" fill="none" stroke-width="22"/>') + '</g>'


def fries():
    sticks = [(56, 52, -8), (72, 40, -3), (88, 36, 2), (104, 42, 6), (120, 36, 10), (136, 50, 14), (80, 56, 4), (112, 54, -4)]
    art = (
        "".join(f'<rect x="{x}" y="{y}" width="14" height="76" rx="6" transform="rotate({r} {x+7} {y+60})" fill="{C["saffron"]}"/>' for x, y, r in sticks)
        + "".join(f'<rect x="{x+2}" y="{y+4}" width="3" height="40" rx="1.5" transform="rotate({r} {x+7} {y+60})" fill="{C["saffron100"]}" opacity="0.7"/>' for x, y, r in sticks)
        + f'<path d="M50 100 H150 L138 176 a6 6 0 0 1 -6 5 H68 a6 6 0 0 1 -6 -5 Z" fill="{C["paprika"]}"/>'
        f'<path d="M100 160 c-14 -9 -22 -17 -22 -26 a10 10 0 0 1 22 -4 a10 10 0 0 1 22 4 c0 9 -8 17 -22 26Z" fill="{C["cream"]}"/>'
    )
    return f'<g transform="translate(9 16) scale(0.91)">' + sticker(art, '<path d="M50 30 H152 V100 L138 180 H62 L50 100 Z"/>') + '</g>'


def donut():
    drips = "".join(f'<circle cx="{100+57*math.cos(a)}" cy="{100+57*math.sin(a)}" r="{r}" fill="{C["paprika300"]}"/>'
                    for a, r in [(0.3, 10), (1.1, 9), (2.0, 11), (2.9, 9), (3.8, 10), (4.7, 9), (5.6, 11)])
    spr = [(76, 64, 20, "teal400"), (118, 60, -30, "cream"), (136, 90, 60, "teal700"), (66, 98, -50, "cream"),
           (130, 128, 10, "beet"), (84, 140, 40, "teal400"), (104, 150, -20, "cream"), (60, 128, 70, "teal700"), (112, 82, 30, "beet")]
    art = (
        f'<circle cx="100" cy="100" r="68" fill="{C["saffron"]}"/>'
        f'<circle cx="100" cy="98" r="58" fill="{C["paprika300"]}"/>{drips}'
        + "".join(f'<rect x="{x-7}" y="{y-2.5}" width="14" height="5" rx="2.5" transform="rotate({r} {x} {y})" fill="{C[c]}"/>' for x, y, r, c in spr)
        + f'<circle cx="100" cy="100" r="19" fill="{C["white"]}"/><circle cx="100" cy="100" r="19" fill="none" stroke="{C["saffron"]}" stroke-width="4"/>'
    )
    return sticker(art, '<circle cx="100" cy="100" r="68"/>')


def heart_sticker():
    art = f'<circle cx="100" cy="100" r="74" fill="{C["cream"]}"/>' + heart_color(40, 62, 120)
    return sticker(art, '<circle cx="100" cy="100" r="74"/>'), HEART_GRADS


def verified():
    pts = []
    for i in range(24):
        r = 84 if i % 2 == 0 else 72
        a = math.pi * i / 12
        pts.append(f"{100+r*math.cos(a):.1f},{100+r*math.sin(a):.1f}")
    art = (f'<polygon points="{" ".join(pts)}" fill="{C["teal500"]}"/><circle cx="100" cy="100" r="62" fill="none" stroke="{C["teal100"]}" stroke-width="4"/>'
           f'<path d="M68 102 l22 22 l44 -50" fill="none" stroke="{C["cream"]}" stroke-width="15" stroke-linecap="round" stroke-linejoin="round"/>')
    return sticker(art, f'<polygon points="{" ".join(pts)}"/>')


def qr_modules(n=11, seed=7):
    random.seed(seed)
    g = [[random.random() < 0.5 for _ in range(n)] for _ in range(n)]
    for ox, oy in [(0, 0), (n - 3, 0), (0, n - 3)]:
        for y in range(-1, 4):
            for x in range(-1, 4):
                X, Y = ox + x, oy + y
                if 0 <= X < n and 0 <= Y < n:
                    g[Y][X] = 0 <= x <= 2 and 0 <= y <= 2 and not (x == 1 and y == 1)
    return g


def qr_art(x, y, size, n=11, fill=None, seed=7):
    fill = fill or C["teal900"]
    m = size / n
    g = qr_modules(n, seed)
    rects = "".join(f'<rect x="{x+i*m:.2f}" y="{y+j*m:.2f}" width="{m+0.2:.2f}" height="{m+0.2:.2f}"/>' for j in range(n) for i in range(n) if g[j][i])
    return f'<g fill="{fill}">{rects}</g>'


def qr_sticker():
    art = (f'<rect x="30" y="30" width="140" height="140" rx="22" fill="{C["cream"]}"/>'
           + qr_art(46, 46, 108) +
           f'<circle cx="100" cy="100" r="19" fill="{C["cream"]}"/><path d="M100 118 c-14 -9 -22 -17 -22 -26 a10 10 0 0 1 22 -4 a10 10 0 0 1 22 4 c0 9 -8 17 -22 26Z" fill="{C["paprika"]}"/>')
    return sticker(art, '<rect x="30" y="30" width="140" height="140" rx="22"/>')


def sparkle():
    star = lambda cx, cy, r: f'M{cx} {cy-r} Q{cx} {cy} {cx+r} {cy} Q{cx} {cy} {cx} {cy+r} Q{cx} {cy} {cx-r} {cy} Q{cx} {cy} {cx} {cy-r}Z'
    art = f'<path d="{star(100,100,70)}" fill="{C["saffron"]}"/><path d="{star(150,52,26)}" fill="{C["teal400"]}"/><path d="{star(52,148,20)}" fill="{C["paprika"]}"/>'
    sil = f'<path d="{star(100,100,70)}"/><path d="{star(150,52,26)}"/><path d="{star(52,148,20)}"/>'
    return sticker(art, sil)


STICKERS = {
    "burger": burger, "pizza": pizza, "cutlery": cutlery, "cup": cup, "fries": fries, "donut": donut,
    "verified": verified, "qr": qr_sticker, "sparkle": sparkle,
}


def sticker_file(name, body, defs=""):
    write(OUT_STICKERS / f"{name}.svg", svg(200, 200, body, defs))


def place(body, x, y, scale=1.0, rot=0):
    """Position a 200x200 sticker body inside a bigger scene."""
    return f'<g transform="translate({x} {y}) rotate({rot} {100*scale} {100*scale}) scale({scale})">{body}</g>'


# =========================== CONFETTI ===========================
def confetti(w, h, seed=3, n=26, avoid=None):
    """Scatter of the 'delicious chaos': dots, squiggles, plus-signs, sparkles. avoid = list of (x0,y0,x1,y1) kept clear."""
    random.seed(seed)
    cols = [C["saffron"], C["paprika"], C["teal400"], C["cream"], C["teal300"], C["paprika300"]]
    out = []
    tries = 0
    while len(out) < n and tries < 2000:
        tries += 1
        x, y = random.uniform(20, w - 20), random.uniform(20, h - 20)
        if avoid and any(a <= x <= c and b <= y <= d for a, b, c, d in avoid):
            continue
        col, kind, r = random.choice(cols), random.choice(["dot", "squig", "plus", "spark", "dash"]), random.uniform(5, 11)
        rot = random.uniform(0, 360)
        if kind == "dot":
            out.append(f'<circle cx="{x:.0f}" cy="{y:.0f}" r="{r*0.6:.1f}" fill="{col}"/>')
        elif kind == "squig":
            out.append(f'<path d="M-14 0 q7 -9 14 0 t14 0" transform="translate({x:.0f} {y:.0f}) rotate({rot:.0f})" fill="none" stroke="{col}" stroke-width="4" stroke-linecap="round"/>')
        elif kind == "plus":
            out.append(f'<path d="M{x-r:.0f} {y:.0f} h{2*r:.0f} M{x:.0f} {y-r:.0f} v{2*r:.0f}" stroke="{col}" stroke-width="4" stroke-linecap="round"/>')
        elif kind == "dash":
            out.append(f'<rect x="{x-9:.0f}" y="{y-2.5:.0f}" width="18" height="5" rx="2.5" transform="rotate({rot:.0f} {x:.0f} {y:.0f})" fill="{col}"/>')
        else:
            out.append(f'<path d="M{x:.0f} {y-r*1.4:.0f} Q{x:.0f} {y:.0f} {x+r*1.4:.0f} {y:.0f} Q{x:.0f} {y:.0f} {x:.0f} {y+r*1.4:.0f} Q{x:.0f} {y:.0f} {x-r*1.4:.0f} {y:.0f} Q{x:.0f} {y:.0f} {x:.0f} {y-r*1.4:.0f}Z" fill="{col}"/>')
    return "".join(out)


# =========================== PATTERNS ===========================
def pattern(name, bg, heart_fill, opacity, tile=140, scale_w=64):
    """Tileable heart-V pattern: two hearts per tile, offset like bricks, one tilted each way."""
    hw = scale_w
    body = (f'<rect width="{tile}" height="{tile}" fill="{bg}"/>'
            + heart_flat(heart_fill, tile * 0.25 - hw / 2, tile * 0.25 - hw * 0.36, hw, -12, opacity)
            + heart_flat(heart_fill, tile * 0.75 - hw / 2, tile * 0.75 - hw * 0.36, hw, 12, opacity)
            # wrap-around copies so the edges tile seamlessly
            + heart_flat(heart_fill, tile * 0.25 - hw / 2 + tile, tile * 0.25 - hw * 0.36, hw, -12, opacity)
            + heart_flat(heart_fill, tile * 0.75 - hw / 2 - tile, tile * 0.75 - hw * 0.36, hw, 12, opacity)
            + heart_flat(heart_fill, tile * 0.75 - hw / 2, tile * 0.75 - hw * 0.36 - tile, hw, 12, opacity)
            + heart_flat(heart_fill, tile * 0.25 - hw / 2, tile * 0.25 - hw * 0.36 + tile, hw, -12, opacity))
    write(HERE / f"{name}.svg", svg(tile, tile, body, extra=f'width="{tile}" height="{tile}"'))


# =========================== HERO (1200 x 600) ===========================
def hero():
    W, H = 1200, 600
    pat_tile = 140
    pat = (f'<pattern id="pt" width="{pat_tile}" height="{pat_tile}" patternUnits="userSpaceOnUse">'
           + heart_flat(C["teal400"], 18, 22, 60, -12, 0.16) + heart_flat(C["teal400"], 88, 86, 60, 12, 0.16) + '</pattern>')
    grad = (f'<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{C["teal600"]}"/><stop offset="1" stop-color="{C["teal900"]}"/></linearGradient>')
    body = (f'<rect width="{W}" height="{H}" rx="36" fill="url(#bg)"/><rect width="{W}" height="{H}" rx="36" fill="url(#pt)"/>'
            f'<g>{confetti(W, H, seed=11, n=40, avoid=[(380, 120, 820, 480)])}</g>'
            # big heart in the middle
            f'<circle cx="600" cy="305" r="190" fill="{C["cream"]}"/>'
            + heart_color(430, 190, 340)
            + place(sticker_body("burger"), 70, 70, 1.15, -10)
            + place(sticker_body("pizza"), 900, 40, 1.15, 9)
            + place(sticker_body("cup"), 930, 330, 1.2, -7)
            + place(sticker_body("fries"), 60, 330, 1.2, 8)
            + place(sticker_body("donut"), 300, 400, 0.85, -14)
            + place(sticker_body("verified"), 760, 400, 0.8, 12)
            + place(sticker_body("qr"), 290, 40, 0.75, 8)
            + place(sticker_body("cutlery"), 790, 30, 0.7, -6))
    write(HERE / "hero.svg", svg(W, H, body, pat + grad + HEART_GRADS))


def sticker_body(name):
    return STICKERS[name]()


# =========================== SPOT ILLUSTRATIONS (240 x 200) ===========================
def spot(name, scene, extra_defs=""):
    blob = (f'<circle cx="120" cy="104" r="86" fill="{C["teal50"]}"/>'
            f'<g>{confetti(240, 200, seed=hash(name) % 97 + 1, n=9, avoid=[(40, 20, 200, 185)])}</g>')
    write(OUT_SPOTS / f"{name}.svg", svg(240, 200, blob + scene, HEART_GRADS + extra_defs))



def coin_stack(cx, base_y, n):
    """n stacked coins, bottom first: saffron side, darker rim line, light top face."""
    out = []
    for i in range(n):
        y = base_y - i * 12
        out.append(f'<path d="M{cx-26} {y} v-8 a26 9 0 0 1 52 0 v8 a26 9 0 0 1 -52 0Z" fill="{C["saffron"]}"/>'
                   f'<path d="M{cx-26} {y-8} a26 9 0 0 0 52 0" fill="none" stroke="#A94E0C" stroke-width="2.5"/>'
                   f'<ellipse cx="{cx}" cy="{y-8}" rx="26" ry="9" fill="{C["saffron100"]}"/>'
                   f'<ellipse cx="{cx}" cy="{y-8}" rx="15" ry="4.5" fill="none" stroke="{C["saffron"]}" stroke-width="2.5"/>')
    return "".join(out)

def spots():
    # 1. no campaigns yet: a menu card, a plus badge, a little burger
    menu = (f'<g transform="rotate(-6 120 100)"><rect x="76" y="34" width="96" height="132" rx="12" fill="{C["ink"]}" opacity="0.12" transform="translate(3 5)"/>'
            f'<rect x="76" y="34" width="96" height="132" rx="12" fill="{C["cream"]}" stroke="{C["teal700"]}" stroke-width="4"/>'
            f'<path d="M76 46 a12 12 0 0 1 12 -12 h72 a12 12 0 0 1 12 12 v22 h-96 Z" fill="{C["teal500"]}"/>'
            + heart_color(100, 42, 48)
            + "".join(f'<rect x="92" y="{y}" width="{w}" height="7" rx="3.5" fill="{C["sanddark"]}"/><rect x="152" y="{y}" width="12" height="7" rx="3.5" fill="{C["saffron"]}"/>'
                      for y, w in [(84, 48), (102, 40), (120, 52), (138, 34)])
            + '</g>'
            f'<circle cx="170" cy="50" r="20" fill="{C["paprika"]}"/><path d="M170 40 v20 M160 50 h20" stroke="{C["white"]}" stroke-width="5" stroke-linecap="round"/>'
            + place(sticker_body("burger"), 38, 112, 0.36, -10))
    spot("empty-campaigns", menu)
    # 2. no redemptions yet: phone showing a QR, pizza on the side
    phone = (f'<rect x="84" y="26" width="76" height="148" rx="16" fill="{C["ink"]}" opacity="0.12" transform="translate(3 5)"/>'
             f'<rect x="84" y="26" width="76" height="148" rx="16" fill="{C["teal900"]}"/><rect x="91" y="40" width="62" height="120" rx="9" fill="{C["cream"]}"/>'
             f'<rect x="108" y="32" width="28" height="4" rx="2" fill="{C["teal700"]}"/>'
             + qr_art(99, 62, 46, 11) +
             f'<rect x="99" y="118" width="46" height="8" rx="4" fill="{C["paprika"]}"/><rect x="99" y="132" width="30" height="6" rx="3" fill="{C["sanddark"]}"/>'
             + place(sticker_body("pizza"), 130, 110, 0.4, 12))
    spot("empty-redemptions", phone)
    # 3. escrow: shield with a check, coins
    shield = (f'<path d="M120 28 L58 54 V100 C58 138 84 162 120 176 C156 162 182 138 182 100 V54 Z" fill="{C["ink"]}" opacity="0.12" transform="translate(3 5)"/>'
              f'<path d="M120 28 L58 54 V100 C58 138 84 162 120 176 C156 162 182 138 182 100 V54 Z" fill="{C["teal500"]}"/>'
              f'<path d="M120 28 L120 176 C156 162 182 138 182 100 V54 Z" fill="{C["teal700"]}" opacity="0.55"/>'
              f'<path d="M90 102 l22 22 l40 -46" fill="none" stroke="{C["cream"]}" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>'
              + coin_stack(186, 170, 3))
    spot("escrow", shield)
    # 4. creators: a camera with the heart-V as the lens
    cam = (f'<rect x="56" y="62" width="128" height="92" rx="18" fill="{C["ink"]}" opacity="0.12" transform="translate(3 5)"/>'
           f'<path d="M92 62 l8 -16 h40 l8 16" fill="{C["teal700"]}"/>'
           f'<rect x="56" y="62" width="128" height="92" rx="18" fill="{C["teal500"]}"/><rect x="56" y="62" width="128" height="22" rx="18" fill="{C["teal700"]}" opacity="0.6"/>'
           f'<circle cx="120" cy="110" r="34" fill="{C["saffron"]}"/><circle cx="120" cy="110" r="28" fill="{C["cream"]}"/>'
           + heart_color(100, 96, 40) +
           f'<circle cx="164" cy="78" r="5" fill="{C["saffron"]}"/>'
           + place(sticker_body("donut"), 150, 112, 0.42, 14))
    spot("empty-creators", cam)
    # 5. success: big check and confetti
    ok = (f'<circle cx="123" cy="107" r="54" fill="{C["ink"]}" opacity="0.12"/><circle cx="120" cy="102" r="54" fill="{C["teal500"]}"/>'
          f'<circle cx="120" cy="102" r="44" fill="none" stroke="{C["teal300"]}" stroke-width="3"/>'
          f'<path d="M94 104 l19 19 l36 -42" fill="none" stroke="{C["cream"]}" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>'
          + place(sticker_body("sparkle"), 150, 20, 0.3, 0) + place(sticker_body("sparkle"), 44, 120, 0.26, 0))
    spot("success", ok)
    # 6. not found: an empty plate (nothing here), cutlery beside it
    spill = (f'<circle cx="135" cy="108" r="64" fill="{C["ink"]}" opacity="0.12" transform="translate(3 5)"/>'
             f'<circle cx="135" cy="104" r="64" fill="{C["cream"]}"/><circle cx="135" cy="104" r="64" fill="none" stroke="{C["teal300"]}" stroke-width="5"/>'
             f'<circle cx="135" cy="104" r="42" fill="{C["white"]}" stroke="{C["sanddark"]}" stroke-width="3"/>'
             + heart_color(117, 91, 36)
             + place(sticker_body("cutlery"), 14, 74, 0.5, -6))
    spot("not-found", spill)


# =========================== MAIN ===========================
if __name__ == "__main__":
    for n in STICKERS:
        sticker_file(n, STICKERS[n]())
    hs, hd = heart_sticker()
    sticker_file("heart", hs, hd)
    pattern("pattern-teal", C["teal700"], C["teal400"], 0.35)       # tone on tone, for dark sections and banners
    pattern("pattern-cream", C["cream"], C["sanddark"], 0.9)        # barely-there texture on the page background
    pattern("pattern-sand", C["sand"], C["white"], 0.6)
    hero()
    spots()
    write(HERE / "confetti.svg", svg(600, 300, confetti(600, 300, seed=5, n=40)))
    print("wrote", len(list(HERE.rglob("*.svg"))), "svg files")
