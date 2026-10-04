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
    "edit": "M4 20h4L19 9l-4-4L4 16zM13 7l4 4",
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


ROW = 0  # current row offset in px


def phone(i, c, label):
    ox = i * (W + GAP)
    out.append(f'<svg x="{ox}" y="{ROW}" width="{W}" height="{H}" overflow="hidden">')
    rect(0, 0, W, H, c["bg"])
    return ox


def end_phone(ox, c, label):
    out.append("</svg>")
    text(ox, ROW + H + 34, label, 16, 600, "#333")


def task(x, y, c, title, caption, cap_color, checked=False, details=()):
    checkbox(x, y + 3, c, checked)
    deco = 'text-decoration="line-through" fill-opacity="0.55"' if checked else ""
    text(x + 38, y + 20, title, 20, 400, c["fg"], deco)
    for k, line in enumerate(details):
        text(x + 38, y + 42 + k * 19, line, 14, 400, c["sub"])
    if caption:
        text(x + 38, y + 42 + len(details) * 19, caption, 13, 400, cap_color)
    return 66 + len(details) * 19


def today(i, c, label):
    ox = phone(i, c, label)
    text(-8, 116, "due north", 118, 300, c["fg"], 'letter-spacing="-4.7"')
    text(20, 210, "today", 40, 300, c["fg"])
    text(20, 240, "Sunday, October 4", 14, 400, c["sub"])
    rect(21, 257, 348, 40, "none", c["sub"])
    text(32, 283, "add a task", 17, 400, c["sub"])
    y = 316
    for title, cap, col, det in [
        ("Renew car registration", "Errands · overdue since Friday", c["overdue"], ()),
        ("Return library books", "Errands · today", c["accent"], ("Due back Tuesday. The two in the", "car, plus the one on the shelf…")),
        ("Pick up dry cleaning", "Errands · today", c["accent"], ()),
        ("Oat milk, 2 cartons", "Groceries", c["sub"], ()),
    ]:
        y += task(20, y, c, title, cap, col, details=det)
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
    text(20, 48, "DUE NORTH · ERRANDS", 13, 600, c["fg"], 'letter-spacing="0.8"')
    text(18, 100, "return library books", 38, 300, c["fg"])
    text(20, 132, "due tuesday, october 6", 16, 400, c["accent"])
    text(20, 180, "details", 14, 400, c["sub"])
    for k, line in enumerate([
        "Due back Tuesday. The two in the car,",
        "plus the one on the shelf by the door.",
        "Renew online if not done:",
    ]):
        text(20, 206 + k * 24, line, 16, 400, c["fg"])
    text(20, 278, "library.example.org/renew", 16, 400, c["accent"], 'text-decoration="underline"')
    text(20, 330, "due", 14, 400, c["sub"])
    rect(21, 341, W - 42, 42, "none", c["sub"])
    text(32, 368, "tuesday, october 6", 18, 400, c["fg"])
    text(20, 416, "list", 14, 400, c["sub"])
    rect(21, 427, W - 42, 42, "none", c["sub"])
    text(32, 454, "Errands", 18, 400, c["fg"])
    appbar(0, c, ["check", "edit", "delete"])
    end_phone(ox, c, label)


ACCENTS = [
    ("magenta", "#B0005E", "#F0389A"),
    ("light orange", "#A85400", "#FFA552"),
    ("coral", "#B8402A", "#FF8A6E"),
    ("lime", "#5A6E00", "#A4C400"),
    ("green", "#3C7A0E", "#60A917"),
    ("emerald", "#007A00", "#2DB52D"),
    ("teal", "#00787A", "#00ABA9"),
    ("cyan", "#0B6FA4", "#1BA1E2"),
    ("cobalt", "#0050EF", "#4D8BFF"),
    ("indigo", "#6A00FF", "#9A5CFF"),
    ("violet", "#8A00D4", "#C25CFF"),
    ("pink", "#B0308F", "#F472D0"),
    ("crimson", "#A20025", "#FF5C7A"),
    ("red", "#C41100", "#FF5C4D"),
    ("orange", "#B34A00", "#FA6800"),
    ("amber", "#8F5F00", "#F0A30A"),
    ("yellow", "#7A6A00", "#E3C800"),
    ("brown", "#825A2C", "#C8955A"),
    ("olive", "#566B4F", "#93AD89"),
    ("steel", "#576778", "#8EA2B8"),
    ("mauve", "#76608A", "#A891BE"),
    ("taupe", "#6E6240", "#B5A577"),
]


def theme(i, c, label, dark):
    ox = phone(i, c, label)
    text(20, 48, "SETTINGS", 13, 600, c["fg"], 'letter-spacing="0.8"')
    text(17, 108, "theme", 52, 300, c["fg"])
    text(200, 108, "sync account", 52, 300, c["sub"])
    text(20, 160, "background", 14, 400, c["sub"])
    rect(21, 171, W - 42, 42, "none", c["sub"])
    text(32, 198, "follow phone", 18, 400, c["fg"])
    text(20, 250, "accent color", 14, 400, c["sub"])
    size, gap = 62, 8
    for k, (name, light, darkv) in enumerate(ACCENTS):
        col, row = k % 5, k // 5
        x, y = 20 + col * (size + gap), 262 + row * (size + gap)
        fill = darkv if dark or name in ("light orange", "coral") else light
        rect(x, y, size, size, fill)
        if name == "light orange":
            rect(x + 3, y + 3, size - 6, size - 6, "none", c["fg"], 3)
    y = 262 + 5 * (size + gap) + 26
    text(20, y, "light orange", 20, 300, c["fg"])
    text(20, y + 24, "new: light orange and coral sit next to magenta", 13, 400, c["sub"])
    end_phone(ox, c, label)


for row, (c, mode) in enumerate([(LIGHT, "light"), (DARK, "dark")]):
    ROW = row * (H + 80)
    today(0, c, f"home: today ({mode})")
    lists(1, c, f"home: lists ({mode})")
    detail(2, c, f"task detail ({mode})")
    theme(3, c, f"settings: theme ({mode})", mode == "dark")

TW = 4 * W + 3 * GAP
TH = 2 * H + 80
svg = (
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-24 -24 {TW+48} {TH+84}" width="{TW+48}" height="{TH+84}">'
    f'<rect x="-24" y="-24" width="{TW+48}" height="{TH+84}" fill="#F2F2F2"/>'
    + "\n".join(out)
    + "</svg>"
)
open(sys.argv[1], "w").write(svg)
