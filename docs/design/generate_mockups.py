"""Generates metro-mockups.svg: the Light Panorama design (light and dark).

Usage: python3 docs/design/generate_mockups.py docs/design/metro-mockups.svg
"""
import sys

W, H, GAP = 390, 844, 48
FONT = "font-family=\"Selawik, 'Segoe UI', 'Open Sans', sans-serif\""

LIGHT = dict(bg="#FFFFFF", fg="#111111", sub="#5C5C5C", bar="#E5E5E5", accent="#B0005E", overdue="#C40000")
DARK = dict(bg="#000000", fg="#FFFFFF", sub="#A6A6A6", bar="#1F1F1F", accent="#F0389A", overdue="#FF6B6B")

out = []


def text(x, y, s, size, weight=400, fill="#111", extra=""):
    out.append(f'<text x="{x}" y="{y}" {FONT} font-size="{size}" font-weight="{weight}" fill="{fill}" {extra}>{s}</text>')


def rect(x, y, w, h, fill="none", stroke=None, sw=2):
    st = f' stroke="{stroke}" stroke-width="{sw}"' if stroke else ""
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}"{st}/>')


def checkbox(x, y, c, checked=False):
    if checked:
        rect(x, y, 24, 24, c["accent"], c["accent"])
        out.append(f'<path d="M{x+5} {y+12} l5 5 l9 -11" stroke="#FFF" stroke-width="2.5" fill="none"/>')
    else:
        rect(x + 1, y + 1, 22, 22, "none", c["fg"])


ICONS = {
    "add": "M12 4v16M4 12h16",
    "sync": "M19.5 10A8 8 0 0 0 5 7.5M4.5 14A8 8 0 0 0 19 16.5M5 3v5h5M19 21v-5h-5",
    "search": "M4 10a6 6 0 1 0 12 0a6 6 0 1 0 -12 0M14.5 14.5L20 20",
    "settings": "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0M12 3v3M12 18v3M3 12h3M18 12h3",
    "check": "M5 12l5 5l9-11",
    "save": "M5 4h11l3 3v13H5zM8 4v5h7V4M8 20v-6h8v6",
    "delete": "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13",
}


def appbar(ox, c, icons):
    y = H - 72
    rect(ox, y, W, 72, c["bar"])
    gap = 82
    start = ox + W / 2 - gap * (len(icons) - 1) / 2
    for i, name in enumerate(icons):
        cx, cy = start + i * gap, y + 36
        out.append(f'<circle cx="{cx}" cy="{cy}" r="22" fill="none" stroke="{c["fg"]}" stroke-width="2"/>')
        out.append(f'<g transform="translate({cx-11},{cy-11}) scale(0.92)"><path d="{ICONS[name]}" fill="none" stroke="{c["fg"]}" stroke-width="2"/></g>')
    text(ox + W - 40, y + 22, "···", 22, fill=c["fg"])


def phone(i, c, label):
    ox = i * (W + GAP)
    out.append(f'<svg x="{ox}" y="0" width="{W}" height="{H}" overflow="hidden">')
    rect(0, 0, W, H, c["bg"])
    return ox


def end_phone(ox, c, label):
    out.append("</svg>")
    text(ox, H + 34, label, 16, 600, "#333")


def task(x, y, c, title, caption, cap_color, checked=False):
    checkbox(x, y + 3, c, checked)
    deco = 'text-decoration="line-through" fill-opacity="0.55"' if checked else ""
    text(x + 38, y + 20, title, 20, 400, c["fg"], deco)
    if caption:
        text(x + 38, y + 42, caption, 13, 400, cap_color)


def today(i, c, label):
    ox = phone(i, c, label)
    text(-8, 116, "due north", 118, 300, c["fg"], 'letter-spacing="-4.7"')
    text(20, 210, "today", 40, 300, c["fg"])
    text(20, 240, "Sunday, October 4", 14, 400, c["sub"])
    y = 264
    for title, cap, col in [
        ("Renew car registration", "Errands · overdue since Friday", c["overdue"]),
        ("Pick up dry cleaning", "Errands · today", c["accent"]),
        ("Call Mom about dinner", "Personal · today", c["accent"]),
        ("Oat milk, 2 cartons", "Groceries", c["sub"]),
    ]:
        task(20, y, c, title, cap, col)
        y += 66
    text(20, y + 14, "tomorrow", 14, 400, c["sub"])
    task(20, y + 34, c, "Book dentist cleaning", "Personal", c["sub"])
    # next section peeks in
    text(370, 210, "lists", 40, 300, c["fg"])
    appbar(0, c, ["add", "sync", "search"])
    end_phone(ox, c, label)


def lists(i, c, label):
    ox = phone(i, c, label)
    text(-196, 116, "due north", 118, 300, c["fg"], 'letter-spacing="-4.7"')
    text(-20, 210, "today", 40, 300, c["fg"], 'text-anchor="end"')
    text(20, 210, "lists", 40, 300, c["fg"])
    y = 240
    for name, nxt, n, tile in [
        ("Errands", "next: Renew car registration", 2, c["accent"]),
        ("Groceries", "next: Oat milk, 2 cartons", 7, c["accent"]),
        ("Work", "next: Q4 budget review, Thu", 12, c["accent"]),
        ("Personal", "next: Call Mom about dinner", 3, c["accent"]),
        ("Someday", "no dates", 9, c["sub"]),
    ]:
        rect(20, y, 64, 64, tile)
        text(78, y + 58, str(n), 22, 300, "#FFFFFF", 'text-anchor="end"')
        text(98, y + 30, name, 24, 300, c["fg"])
        text(98, y + 52, nxt, 13, 400, c["sub"])
        y += 80
    rect(21, y + 1, 62, 62, "none", c["fg"])
    out.append(f'<path d="M52 {y+18}v28M38 {y+32}h28" stroke="{c["fg"]}" stroke-width="2"/>')
    text(98, y + 39, "new list", 20, 400, c["fg"])
    text(370, 210, "done", 40, 300, c["fg"])
    appbar(0, c, ["add", "sync", "settings"])
    end_phone(ox, c, label)


def detail(i, c, label):
    ox = phone(i, c, label)
    text(20, 48, "GROCERIES", 13, 600, c["fg"], 'letter-spacing="0.8"')
    text(17, 108, "oat milk", 52, 300, c["fg"])
    text(20, 160, "notes", 14, 400, c["sub"])
    rect(20, 170, W - 40, 64, c["bar"])
    text(30, 198, "2 cartons, the barista kind", 16, 400, c["fg"])
    text(20, 272, "due", 14, 400, c["sub"])
    rect(21, 283, W - 42, 42, "none", c["sub"])
    text(32, 310, "sunday, october 4", 18, 400, c["fg"])
    text(20, 366, "steps", 14, 400, c["sub"])
    task(20, 380, c, "check the fridge first", "", c["sub"], checked=True)
    task(20, 426, c, "bring a bag", "", c["sub"])
    text(58, 494, "+ add step", 18, 400, c["accent"])
    appbar(0, c, ["check", "save", "delete"])
    end_phone(ox, c, label)


today(0, LIGHT, "1. home panorama: today")
lists(1, LIGHT, "2. home panorama: lists")
detail(2, LIGHT, "3. task detail")
today(3, DARK, "4. today, dark theme")

TW = 4 * W + 3 * GAP
svg = (
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-24 -24 {TW+48} {H+84}" width="{TW+48}" height="{H+84}">'
    f'<rect x="-24" y="-24" width="{TW+48}" height="{H+84}" fill="#F2F2F2"/>'
    + "\n".join(out)
    + "</svg>"
)
open(sys.argv[1], "w").write(svg)
