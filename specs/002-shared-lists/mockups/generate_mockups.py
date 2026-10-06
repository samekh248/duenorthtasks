"""Generates shared-lists-mockups.svg for specs/002-shared-lists (light and dark).

Usage: python3 specs/002-shared-lists/mockups/generate_mockups.py specs/002-shared-lists/mockups/shared-lists-mockups.svg

Same drawing conventions as docs/design/generate_mockups.py (Light Panorama, magenta accent).
"""
import sys

W, H, GAP = 390, 844, 48
FONT = "font-family=\"Selawik, 'Segoe UI', 'Open Sans', sans-serif\""

LIGHT = dict(bg="#FFFFFF", fg="#111111", sub="#5C5C5C", bar="#E5E5E5", accent="#B0005E", overdue="#C40000")
DARK = dict(bg="#000000", fg="#FFFFFF", sub="#A6A6A6", bar="#1F1F1F", accent="#F0389A", overdue="#FF6B6B")

out = []
ROW = 0


def _rgb(h):
    h = h.lstrip("#")
    return [int(h[i:i + 2], 16) for i in (0, 2, 4)]


def _lum(h):
    f = lambda v: v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = [f(v / 255) for v in _rgb(h)]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    hi, lo = sorted([_lum(a), _lum(b)], reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def shade(c, step):
    target = "#FFFFFF" if step < 0 else "#000000"
    t = abs(step) * 0.2
    return "#%02X%02X%02X" % tuple(round(x + (y - x) * t) for x, y in zip(_rgb(c["accent"]), _rgb(target)))


def caption_shade(c, step):
    for s in sorted(range(-3, 4), key=lambda k: (abs(k - step), k)):
        if contrast(shade(c, s), c["bg"]) >= 4.5:
            return shade(c, s)
    return c["accent"]


def ink(fill):
    return "#FFFFFF" if contrast("#FFFFFF", fill) >= contrast("#111111", fill) else "#111111"


def text(x, y, s, size, weight=400, fill="#111", extra=""):
    out.append(f'<text x="{x}" y="{y}" {FONT} font-size="{size}" font-weight="{weight}" fill="{fill}" {extra}>{s}</text>')


def rect(x, y, w, h, fill="none", stroke=None, sw=2):
    st = f' stroke="{stroke}" stroke-width="{sw}"' if stroke else ""
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}"{st}/>')


ICONS = {
    "add": "M12 4v16M4 12h16",
    "sync": "M19.5 10A8 8 0 0 0 5 7.5M4.5 14A8 8 0 0 0 19 16.5M5 3v5h5M19 21v-5h-5",
    "settings": "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0M12 3v3M12 18v3M3 12h3M18 12h3",
    "check": "M5 12l5 5l9-11",
    "edit": "M4 20h4L19 9l-4-4L4 16zM13 7l4 4",
    "delete": "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13",
    "sort": "M7 4v16M3 16l4 4l4-4M17 20V4M13 8l4-4l4 4",
    # Two outlined people: the "shared" glyph (new in this feature).
    "people": "M6 9a3 3 0 1 0 6 0a3 3 0 1 0 -6 0M3 20c0-3.5 2.5-5.5 6-5.5s6 2 6 5.5M15 6.2a2.8 2.8 0 1 1 0 5.6M17 14.6c2.6.5 4 2.4 4 5.4",
    "open": "M14 4h6v6M20 4l-9 9M18 14v6H4V6h6",
}


def glyph(x, y, name, color, size=24, sw=2):
    s = size / 24
    out.append(f'<g transform="translate({x},{y}) scale({s})"><path d="{ICONS[name]}" fill="none" '
               f'stroke="{color}" stroke-width="{sw / s}" stroke-linecap="square"/></g>')


def appbar(c, icons):
    y = H - 72
    rect(0, y, W, 72, c["bar"])
    gap = 82
    start = W / 2 - gap * (len(icons) - 1) / 2
    for i, name in enumerate(icons):
        cx, cy = start + i * gap, y + 36
        out.append(f'<circle cx="{cx}" cy="{cy}" r="22" fill="none" stroke="{c["fg"]}" stroke-width="2"/>')
        glyph(cx - 11, cy - 11, name, c["fg"], 22)
    text(W - 40, y + 22, "···", 22, fill=c["fg"])


def phone(i, c):
    ox = i * (W + GAP)
    out.append(f'<svg x="{ox}" y="{ROW}" width="{W}" height="{H}" overflow="hidden">')
    rect(0, 0, W, H, c["bg"])
    return ox


def end_phone(ox, label):
    out.append("</svg>")
    text(ox, ROW + H + 34, label, 16, 600, "#333")


def checkbox(x, y, c, checked=False):
    if checked:
        rect(x, y, 24, 24, c["accent"], c["accent"])
        out.append(f'<path d="M{x+5} {y+12} l5 5 l9 -11" stroke="#FFF" stroke-width="2.5" fill="none"/>')
    else:
        rect(x + 1, y + 1, 22, 22, "none", c["fg"])


def task(x, y, c, title, caption, cap_color):
    checkbox(x, y + 3, c)
    text(x + 38, y + 20, title, 20, 400, c["fg"])
    if caption:
        text(x + 38, y + 42, caption, 13, 400, cap_color)
    return 66


def lists(i, c, label):
    """Home panorama, lists: shared lists get a people glyph on the tile and a 'shared' caption."""
    ox = phone(i, c)
    text(-196, 116, "due north", 118, 300, c["fg"], 'letter-spacing="-4.7"')
    text(-20, 210, "today", 40, 300, c["fg"], 'text-anchor="end"')
    text(20, 210, "lists", 40, 300, c["fg"])
    y = 240
    for name, nxt, n, step, shared in [
        ("Tasks", "next: Renew car registration", 4, 0, None),
        ("Family groceries", "shared · next: Oat milk, 2 cartons", 7, -2, "owner"),
        ("Book club", "shared with you · next: Pick October book", 2, 1, "member"),
        ("Work", "next: Q4 budget review, Thu", 12, 2, None),
        ("Someday", "no dates", 9, -1, None),
    ]:
        tile = shade(c, step)
        rect(20, y, 64, 64, tile)
        text(78, y + 58, str(n), 22, 300, ink(tile), 'text-anchor="end"')
        if shared:
            glyph(25, y + 5, "people", ink(tile), 20)
        text(98, y + 30, name, 24, 300, c["fg"])
        text(98, y + 52, nxt, 13, 400, c["sub"])
        y += 80
    rect(21, y + 1, 62, 62, "none", c["fg"])
    out.append(f'<path d="M52 {y+18}v28M38 {y+32}h28" stroke="{c["fg"]}" stroke-width="2"/>')
    text(98, y + 39, "new list", 20, 400, c["fg"])
    text(370, 210, "done", 40, 300, c["fg"])
    appbar(c, ["add", "sync", "settings"])
    end_phone(ox, label)


def list_page(i, c, label):
    """A list shared with you (you are not the owner)."""
    ox = phone(i, c)
    cap = caption_shade(c, 1)
    text(20, 48, "DUE NORTH · LISTS", 13, 600, c["fg"], 'letter-spacing="0.8"')
    text(16, 110, "book club", 56, 300, c["fg"])
    glyph(20, 128, "people", cap, 20)
    text(48, 145, "shared with you · details", 15, 400, cap)
    rect(21, 170, 348, 40, "none", c["sub"])
    text(32, 196, "add a task", 17, 400, c["sub"])
    y = 232
    for title, caption, col in [
        ("Pick October book", "due friday", cap),
        ("Book the back room at Rosie's", "due october 14", cap),
        ("Collect $10 from everyone", "", c["sub"]),
        ("Send reading schedule", "", c["sub"]),
    ]:
        y += task(20, y, c, title, caption, col)
    text(20, y + 16, "completed (3)", 14, 400, c["sub"])
    appbar(c, ["add", "people", "sort"])
    end_phone(ox, label)


def sharing_page(i, c, label):
    """'sharing' page opened from the people button or the shared caption."""
    ox = phone(i, c)
    text(20, 48, "BOOK CLUB", 13, 600, c["fg"], 'letter-spacing="0.8"')
    text(17, 108, "sharing", 52, 300, c["fg"])
    text(20, 160, "status", 14, 400, c["sub"])
    glyph(20, 172, "people", c["fg"], 24)
    text(54, 191, "shared with you", 22, 300, c["fg"])
    text(54, 214, "someone else owns this list", 14, 400, c["sub"])

    text(20, 262, "who's in it", 14, 400, c["sub"])
    for k, line in enumerate([
        "Microsoft To Do doesn't tell other apps who a",
        "list is shared with, so the names aren't here.",
    ]):
        text(20, 288 + k * 22, line, 16, 400, c["fg"])
    glyph(20, 330, "open", c["accent"], 20)
    text(48, 346, "see people in microsoft to do", 17, 400, c["accent"])

    text(20, 398, "what you can do here", 14, 400, c["sub"])
    for k, (ok, line) in enumerate([
        (True, "add, edit, complete and delete tasks"),
        (True, "pick this list's shade (only on this phone)"),
        (False, "rename or delete the list: only the owner"),
        (False, "invite people or leave: in microsoft to do"),
    ]):
        yy = 412 + k * 34
        if ok:
            out.append(f'<path d="M22 {yy+12} l5 5 l9 -11" stroke="{c["fg"]}" stroke-width="2" fill="none"/>')
        else:
            out.append(f'<path d="M23 {yy+14} h12" stroke="{c["sub"]}" stroke-width="2"/>')
        text(46, yy + 20, line, 16, 400, c["fg"] if ok else c["sub"])

    text(20, 578, "list shade", 14, 400, c["sub"])
    for k, step in enumerate(range(-3, 4)):
        x = 20 + k * 50
        rect(x, 590, 44, 44, shade(c, step))
        if step == 1:
            rect(x + 2, 592, 40, 40, "none", c["fg"], 3)
    end_phone(ox, label)


def assigned(i, c, label):
    """Google mode: a task assigned to you from a Google Doc or Chat space."""
    ox = phone(i, c)
    text(20, 48, "DUE NORTH · MY TASKS", 13, 600, c["fg"], 'letter-spacing="0.8"')
    text(18, 100, "review section 3 edits", 38, 300, c["fg"])
    text(20, 132, "due wednesday, october 7", 16, 400, c["accent"])
    text(20, 180, "assigned to you", 14, 400, c["sub"])
    rect(21, 192, W - 42, 92, "none", c["sub"])
    glyph(34, 206, "people", c["fg"], 24)
    text(68, 225, "from a google doc", 20, 300, c["fg"])
    glyph(68, 244, "open", c["accent"], 18)
    text(92, 259, "open in google docs", 16, 400, c["accent"])
    for k, line in enumerate([
        "Google doesn't say who assigned it. Edits",
        "here sync back to the doc's task.",
    ]):
        text(20, 312 + k * 22, line, 15, 400, c["sub"])
    text(20, 380, "details", 14, 400, c["sub"])
    text(20, 406, "Tighten the pricing table and check the", 16, 400, c["fg"])
    text(20, 430, "figures against the Q3 report.", 16, 400, c["fg"])
    appbar(c, ["check", "edit", "delete"])
    end_phone(ox, label)


for row, (c, mode) in enumerate([(LIGHT, "light"), (DARK, "dark")]):
    ROW = row * (H + 80)
    lists(0, c, f"home: lists, shared marked ({mode})")
    list_page(1, c, f"shared list you don't own ({mode})")
    sharing_page(2, c, f"sharing page ({mode})")
    assigned(3, c, f"google: assigned task ({mode})")

TW = 4 * W + 3 * GAP
TH = 2 * H + 80
svg = (
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-24 -24 {TW+48} {TH+84}" width="{TW+48}" height="{TH+84}">'
    f'<rect x="-24" y="-24" width="{TW+48}" height="{TH+84}" fill="#F2F2F2"/>'
    + "\n".join(out)
    + "</svg>"
)
open(sys.argv[1], "w").write(svg)
