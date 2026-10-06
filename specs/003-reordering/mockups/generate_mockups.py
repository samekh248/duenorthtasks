"""Generates reordering-mockups.svg for specs/003-reordering (light and dark).

Usage: python3 specs/003-reordering/mockups/generate_mockups.py specs/003-reordering/mockups/reordering-mockups.svg

Same drawing conventions as docs/design/generate_mockups.py (Light Panorama, magenta accent).
"""
import sys

W, H, GAP = 390, 844, 48
FONT = "font-family=\"Selawik, 'Segoe UI', 'Open Sans', sans-serif\""

LIGHT = dict(bg="#FFFFFF", fg="#111111", sub="#5C5C5C", bar="#E5E5E5", accent="#B0005E", menu="#FFFFFF",
             menu_edge="#111111")
DARK = dict(bg="#000000", fg="#FFFFFF", sub="#A6A6A6", bar="#1F1F1F", accent="#F0389A", menu="#1F1F1F",
            menu_edge="#1F1F1F")
DIM = 0.45  # rows that aren't being dragged, like WP8.1 Start in rearrange mode

out = []
ROW = 0


def _rgb(h):
    h = h.lstrip("#")
    return [int(h[i:i + 2], 16) for i in (0, 2, 4)]


def shade(c, step):
    target = "#FFFFFF" if step < 0 else "#000000"
    t = abs(step) * 0.2
    return "#%02X%02X%02X" % tuple(round(x + (y - x) * t) for x, y in zip(_rgb(c["accent"]), _rgb(target)))


def text(x, y, s, size, weight=400, fill="#111", extra=""):
    out.append(f'<text x="{x}" y="{y}" {FONT} font-size="{size}" font-weight="{weight}" fill="{fill}" {extra}>{s}</text>')


def rect(x, y, w, h, fill="none", stroke=None, sw=2, extra=""):
    st = f' stroke="{stroke}" stroke-width="{sw}"' if stroke else ""
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}"{st} {extra}/>')


ICONS = {
    "add": "M12 4v16M4 12h16",
    "search": "M10 4a6 6 0 1 0 0.01 0M14.5 14.5L20 20",
    "sort": "M5 7h14M7 12h10M9 17h6",
    "check": "M5 12l5 5l9-11",
    "edit": "M4 20h4L19 9l-4-4L4 16zM13 7l4 4",
    "delete": "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13",
    # New in this feature: "reorder", two stacked bars with arrows up and down.
    "reorder": "M4 10h16M4 14h16M12 2v5M9 4.5l3-3l3 3M12 22v-5M9 19.5l3 3l3-3",
}


def glyph(x, y, name, color, size=24, sw=2):
    s = size / 24
    out.append(f'<g transform="translate({x},{y}) scale({s})"><path d="{ICONS[name]}" fill="none" '
               f'stroke="{color}" stroke-width="{sw / s}" stroke-linecap="square"/></g>')


def gripper(x, y, color):
    """Three short bars at the row's right edge: the drag handle shown only in reorder mode."""
    for k in range(3):
        out.append(f'<path d="M{x} {y + k * 7}h22" stroke="{color}" stroke-width="2"/>')


def appbar(c, icons, labels=None):
    y = H - 72 - (24 if labels else 0)
    rect(0, y, W, H - y, c["bar"])
    gap = 82
    start = W / 2 - gap * (len(icons) - 1) / 2
    for i, name in enumerate(icons):
        cx, cy = start + i * gap, y + 36
        out.append(f'<circle cx="{cx}" cy="{cy}" r="22" fill="none" stroke="{c["fg"]}" stroke-width="2"/>')
        glyph(cx - 11, cy - 11, name, c["fg"], 22)
        if labels:
            text(cx, cy + 40, labels[i], 13, 400, c["fg"], 'text-anchor="middle"')
    text(W - 40, y + 22, "···", 22, fill=c["fg"])


def phone(i, c):
    ox = i * (W + GAP)
    out.append(f'<svg x="{ox}" y="{ROW}" width="{W}" height="{H}" overflow="hidden">')
    rect(0, 0, W, H, c["bg"])
    return ox


def end_phone(ox, label):
    out.append("</svg>")
    text(ox, ROW + H + 34, label, 16, 600, "#333")


def checkbox(x, y, c):
    rect(x + 1, y + 1, 22, 22, "none", c["fg"])


def reorder_row(y, c, title, caption, cap_color, opacity=1.0, lifted=False, box=True):
    """One compact row in reorder mode: title, one caption line, gripper on the right."""
    g = f'<g opacity="{opacity}"' + (f' transform="translate(195,{y + 30}) scale(1.05) translate(-195,{-(y + 30)})"' if lifted else "") + ">"
    out.append(g)
    if lifted:
        # Flat accent edge marks the picked-up row. No shadow: Metro has no elevation.
        rect(0, y - 4, W, 66, c["bg"])
        rect(0, y - 4, 4, 66, c["accent"])
    if box:
        checkbox(20, y + 6, c)
    tx = 58 if box else 20
    text(tx, y + 24, title, 20, 400, c["fg"])
    if caption:
        text(tx, y + 46, caption, 13, 400, cap_color)
    gripper(W - 46, y + 18, c["fg"])
    out.append("</g>")


def tasks_reorder(i, c, label):
    """Google mode, list page in reorder mode, a task mid-drag."""
    ox = phone(i, c)
    text(20, 48, "DUE NORTH", 13, 600, c["fg"], 'letter-spacing="0.8"')
    text(16, 110, "errands", 56, 300, c["fg"])
    text(20, 148, "drag a task to move it", 16, 400, c["sub"])
    y = 180
    rows = [
        ("Pick up dry cleaning", "tomorrow"),
        ("Get stamps", ""),
        None,  # the open slot the dragged row will drop into
        ("Buy birthday card for Sam", "friday"),
        ("Drop off donations", ""),
        ("Mail the rent check", "october 9"),
    ]
    for r in rows:
        if r is None:
            rect(20, y + 4, W - 40, 58, "none", c["accent"], 2, 'stroke-dasharray="6 6"')
        else:
            reorder_row(y, c, r[0], r[1], c["accent"], DIM)
        y += 70
    # Dragged row (it was first), drawn last, lifted under the finger over the open slot.
    reorder_row(180 + 2 * 70 - 14, c, "Return library books", "today", c["accent"], 1.0, lifted=True)
    text(20, y + 22, "completed tasks stay where they are", 14, 400, c["sub"])
    appbar(c, ["check"], ["done"])
    end_phone(ox, label)


def lists_reorder(i, c, label):
    """'reorder lists' page, opened from the lists section's ••• menu (the panorama scrolls sideways)."""
    ox = phone(i, c)
    text(20, 48, "DUE NORTH", 13, 600, c["fg"], 'letter-spacing="0.8"')
    text(16, 110, "lists", 56, 300, c["fg"])
    text(20, 148, "drag a list to move it", 16, 400, c["sub"])
    y = 176
    data = [("Inbox", 0, "default list", DIM), ("Work", 2, "", 1.0), ("Errands", -1, "", DIM),
            ("Home", 1, "", DIM), ("Someday", -2, "", DIM)]
    for k, (name, step, cap, op) in enumerate(data):
        lifted = name == "Work"
        yy = y + k * 84 + (-36 if lifted else 0)
        if k == 1:
            rect(20, y + 84 + 6, W - 40, 72, "none", c["accent"], 2, 'stroke-dasharray="6 6"')
            continue
        out.append(f'<g opacity="{op}">')
        tile = shade(c, step)
        rect(20, yy, 64, 64, tile)
        text(98, yy + 34, name, 24, 300, c["fg"])
        if cap:
            text(98, yy + 54, cap, 13, 400, c["sub"])
        gripper(W - 46, yy + 26, c["fg"])
        out.append("</g>")
    # Work, lifted, hovering over the slot below Inbox.
    yy = y + 84 - 14
    out.append(f'<g transform="translate(195,{yy + 32}) scale(1.05) translate(-195,{-(yy + 32)})">')
    rect(0, yy - 8, W, 80, c["bg"])
    rect(0, yy - 8, 4, 80, c["accent"])
    rect(20, yy, 64, 64, shade(c, 2))
    text(98, yy + 40, "Work", 24, 300, c["fg"])
    gripper(W - 46, yy + 26, c["fg"])
    out.append("</g>")
    text(20, y + 5 * 84 + 10, "this order is kept on this phone", 14, 400, c["sub"])
    appbar(c, ["check"], ["done"])
    end_phone(ox, label)


def steps_reorder(i, c, label):
    """Task page with steps in reorder mode."""
    ox = phone(i, c)
    text(20, 48, "DUE NORTH · INBOX", 13, 600, c["fg"], 'letter-spacing="0.8"')
    text(18, 104, "Call the vet", 46, 300, c["fg"])
    text(20, 138, "due tomorrow", 20, 400, c["accent"])
    text(20, 186, "steps", 14, 400, c["sub"])
    text(W - 20, 186, "drag to reorder", 14, 400, c["sub"], 'text-anchor="end"')
    y = 200
    steps = ["Find the vaccination card", None, "Check Thursday afternoon", "Book the appointment"]
    for k, s in enumerate(steps):
        if k == 1:
            rect(20, y + 4, W - 40, 50, "none", c["accent"], 2, 'stroke-dasharray="6 6"')
        else:
            out.append(f'<g opacity="{DIM}">')
            checkbox(20, y + 14, c)
            text(62, y + 34, s, 18, 400, c["fg"])
            gripper(W - 46, y + 22, c["fg"])
            out.append("</g>")
        y += 60
    yy = 200 + 60 - 14
    out.append(f'<g transform="translate(195,{yy + 30}) scale(1.05) translate(-195,{-(yy + 30)})">')
    rect(0, yy, W, 60, c["bg"])
    rect(0, yy, 4, 60, c["accent"])
    checkbox(20, yy + 18, c)
    text(62, yy + 38, "Ask about the booster", 18, 400, c["fg"])
    gripper(W - 46, yy + 24, c["fg"])
    out.append("</g>")
    out.append(f'<g opacity="{DIM}">')
    text(20, y + 30, "due date", 14, 400, c["sub"])
    rect(21, y + 44, 120, 40, "none", c["sub"])
    text(34, y + 70, "tomorrow", 16, 400, c["fg"])
    out.append("</g>")
    appbar(c, ["check"], ["done"])
    end_phone(ox, label)


def context_menu(i, c, label):
    """Long-press a task: the Metro context menu gains one entry, "reorder"."""
    ox = phone(i, c)
    text(20, 48, "DUE NORTH", 13, 600, c["fg"], 'letter-spacing="0.8"')
    text(16, 110, "errands", 56, 300, c["fg"])
    rect(21, 134, 348, 40, "none", c["sub"])
    text(32, 160, "add a task", 17, 400, c["sub"])
    y = 196
    for k, (t, cap) in enumerate([("Return library books", "today"), ("Pick up dry cleaning", "tomorrow"),
                                  ("Get stamps", ""), ("Buy birthday card for Sam", "friday")]):
        op = 1.0 if k == 2 else DIM
        out.append(f'<g opacity="{op}">')
        checkbox(20, y + 4, c)
        text(58, y + 22, t, 20, 400, c["fg"])
        if cap:
            text(58, y + 44, cap, 13, 400, c["accent"])
        out.append("</g>")
        y += 66
    # WP8.1 context menu: full-width flat panel below the pressed row, lowercase items.
    my = 196 + 3 * 66 - 8
    rect(0, my, W, 3 * 50 + 16, c["menu"], c["menu_edge"], 2)
    for k, item in enumerate(["reorder", "move to list...", "delete"]):
        text(24, my + 42 + k * 50, item, 22, 300, c["fg"])
    appbar(c, ["add", "sort", "reorder"])
    end_phone(ox, label)


def microsoft_note(i, c, label):
    """Microsoft mode, first time in reorder mode: one honest line, once per install."""
    ox = phone(i, c)
    text(20, 48, "DUE NORTH", 13, 600, c["fg"], 'letter-spacing="0.8"')
    text(16, 110, "groceries", 56, 300, c["fg"])
    text(20, 148, "drag a task to move it", 16, 400, c["sub"])
    y = 180
    for t in ["Oat milk, 2 cartons", "Coffee beans", "Bananas", "Dish soap"]:
        reorder_row(y, c, t, "", c["accent"], DIM)
        y += 62
    # Metro message dialog: full-width band, dims the page, two lowercase buttons.
    rect(0, 0, W, H, "#000000", extra='opacity="0.6"')
    rect(0, 120, W, 300, c["menu"])
    text(20, 172, "order stays on this phone", 26, 300, c["fg"])
    for k, line in enumerate([
        "Microsoft To Do doesn't let other apps",
        "read or change the order of tasks, lists",
        "or steps. The order you set here is kept",
        "on this phone and doesn't show in To Do.",
    ]):
        text(20, 212 + k * 24, line, 16, 400, c["fg"])
    rect(21, 336, 168, 50, "none", c["fg"], 2)
    text(105, 368, "got it", 17, 400, c["fg"], 'text-anchor="middle"')
    end_phone(ox, label)


for row, (c, mode) in enumerate([(LIGHT, "light"), (DARK, "dark")]):
    ROW = row * (H + 80)
    context_menu(0, c, f"long-press: reorder entry ({mode})")
    tasks_reorder(1, c, f"tasks: dragging in reorder mode ({mode})")
    steps_reorder(2, c, f"steps: dragging in reorder mode ({mode})")
    lists_reorder(3, c, f"reorder lists page ({mode})")
    microsoft_note(4, c, f"microsoft: one-time note ({mode})")

N = 5
TW = N * W + (N - 1) * GAP
TH = 2 * H + 80
svg = (
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-24 -24 {TW+48} {TH+84}" width="{TW+48}" height="{TH+84}">'
    f'<rect x="-24" y="-24" width="{TW+48}" height="{TH+84}" fill="#F2F2F2"/>'
    + "\n".join(out)
    + "</svg>"
)
open(sys.argv[1], "w").write(svg)
