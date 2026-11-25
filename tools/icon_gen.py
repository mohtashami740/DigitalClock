"""Generates the launcher icon (italic 7-segment clock) as Android vector drawables, plus previews.

The app uses option C ("Retro LCD"). To regenerate the icon from the repository root:
    python3 tools/icon_gen.py xml C app/src/main/res
Preview sheet of all options (needs Pillow, and a "current icon" PNG for comparison):
    python3 tools/icon_gen.py preview OUT_DIR CURRENT_ICON_PNG
"""
import math, sys

# ---- geometry (108 x 108 dp adaptive-icon canvas; the visible area is the central ~72 dp) ----
SEGMENTS = ["abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg"]
TIME = "9:30"
D = 24.5                 # digit height (dp); per-option override below
SLANT = math.tan(math.radians(10))


def set_size(d):
    """Digit height in dp; everything else scales with it."""
    global D, W, T, GAP, SPACING, COLON_W
    D = d
    W = 0.52 * D             # digit width
    T = 0.15 * D             # segment thickness (bolder than in the app so it reads at icon size)
    GAP = T * 0.22
    SPACING = 0.17 * D
    COLON_W = 0.16 * D


set_size(D)


def char_w(c):
    return COLON_W if c == ':' else W


def text_width(s):
    return sum(char_w(c) for c in s) + SPACING * (len(s) - 1) + D * SLANT


def hexagon(p0, p1, t, gap):
    h = t / 2
    if p0[1] == p1[1]:
        x0, x1, y = p0[0] + gap, p1[0] - gap, p0[1]
        return [(x0, y), (x0 + h, y - h), (x1 - h, y - h), (x1, y), (x1 - h, y + h), (x0 + h, y + h)]
    y0, y1, x = p0[1] + gap, p1[1] - gap, p0[0]
    return [(x, y0), (x + h, y0 + h), (x + h, y1 - h), (x, y1), (x - h, y1 - h), (x - h, y0 + h)]


def layout(s, cx=54.0, cy=54.0):
    """Returns (lit_polygons, unlit_polygons) for string s centred at (cx, cy)."""
    x = cx - text_width(s) / 2
    top = cy - D / 2
    base = top + D
    skew = lambda p: (p[0] + (base - p[1]) * SLANT, p[1])
    lit, unlit = [], []
    for c in s:
        if c.isdigit():
            l, r = x + T / 2, x + W - T / 2
            tp, m, b = top + T / 2, top + D / 2, top + D - T / 2
            segs = {'a': ((l, tp), (r, tp)), 'g': ((l, m), (r, m)), 'd': ((l, b), (r, b)),
                    'f': ((l, tp), (l, m)), 'b': ((r, tp), (r, m)), 'e': ((l, m), (l, b)), 'c': ((r, m), (r, b))}
            for name, (p0, p1) in segs.items():
                poly = [skew(p) for p in hexagon(p0, p1, T, GAP)]
                (lit if name in SEGMENTS[int(c)] else unlit).append(poly)
        elif c == ':':
            s2 = T * 1.05
            cxx = x + COLON_W / 2
            for fy in (0.30, 0.70):
                yy = top + D * fy
                lit.append([skew(p) for p in [(cxx - s2/2, yy - s2/2), (cxx + s2/2, yy - s2/2),
                                              (cxx + s2/2, yy + s2/2), (cxx - s2/2, yy + s2/2)]])
        x += char_w(c) + SPACING
    return lit, unlit


# ---- the three options ----
OPTIONS = {
    'A': dict(name='Classic LED', size=24.5, bg=('#151515', '#000000'), digit='#FF8A50', ghost=0.13, panel=None),
    'B': dict(name='Night blue', size=24.5, bg=('#16304D', '#060D18'), digit='#D6ECFF', ghost=0.12, panel=None),
    'C': dict(name='Retro LCD', size=19.5, bg=('#3A3A3A', '#1C1C1C'), digit='#1F2A1A', ghost=0.10,
              panel=dict(color='#AFC096', rect=(27, 39, 81, 69), radius=5)),
}


def hex_rgb(h):
    h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))


def render(opt, px, mask):
    """Renders the icon at px pixels, clipped to a launcher mask shape."""
    from PIL import Image, ImageDraw
    S = 4 * px
    k = S / 108.0
    img = Image.new('RGBA', (S, S))
    top, bot = hex_rgb(opt['bg'][0]), hex_rgb(opt['bg'][1])
    d = ImageDraw.Draw(img)
    for y in range(S):  # vertical gradient background
        f = y / (S - 1)
        d.line([(0, y), (S, y)], fill=tuple(int(top[i] + (bot[i] - top[i]) * f) for i in range(3)) + (255,))
    set_size(opt['size'])
    if opt['panel']:
        x0, y0, x1, y1 = opt['panel']['rect']
        d.rounded_rectangle([x0*k, y0*k, x1*k, y1*k], radius=opt['panel']['radius']*k, fill=hex_rgb(opt['panel']['color']))
    lit, unlit = layout(TIME)
    col = hex_rgb(opt['digit'])
    ghost = Image.new('RGBA', (S, S)); gd = ImageDraw.Draw(ghost)
    for poly in unlit:
        gd.polygon([(x*k, y*k) for x, y in poly], fill=col + (int(255 * opt['ghost']),))
    img = Image.alpha_composite(img, ghost)
    d = ImageDraw.Draw(img)
    for poly in lit:
        d.polygon([(x*k, y*k) for x, y in poly], fill=col + (255,))
    # Launcher masks show roughly the central 72 dp of the 108 dp canvas.
    crop = int(18 * k)
    img = img.crop((crop, crop, S - crop, S - crop)).resize((px, px), Image.LANCZOS)
    m = Image.new('L', (px * 4, px * 4), 0); md = ImageDraw.Draw(m)
    if mask == 'circle':
        md.ellipse([0, 0, px*4 - 1, px*4 - 1], fill=255)
    elif mask == 'rounded':
        md.rounded_rectangle([0, 0, px*4 - 1, px*4 - 1], radius=px*4*0.22, fill=255)
    else:  # squircle
        n = 4.0; r = px * 2
        pts = [(r + r * math.copysign(abs(math.cos(a))**(2/n), math.cos(a)),
                r + r * math.copysign(abs(math.sin(a))**(2/n), math.sin(a))) for a in [i * 2 * math.pi / 400 for i in range(400)]]
        md.polygon(pts, fill=255)
    m = m.resize((px, px), Image.LANCZOS)
    out = Image.new('RGBA', (px, px)); out.paste(img, (0, 0), m)
    return out


def preview(out_dir, old_icon):
    from PIL import Image, ImageDraw, ImageFont
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 30)
    small = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 22)
    cols = ['current'] + list(OPTIONS)
    W_, H_ = 1500, 1000
    sheet = Image.new('RGB', (W_, H_), (0, 0, 0))
    # A soft "wallpaper" so the icons are seen as on a home screen.
    wd = ImageDraw.Draw(sheet)
    for y in range(H_):
        f = y / H_
        wd.line([(0, y), (W_, y)], fill=(int(70 + 40 * f), int(90 + 20 * f), int(120 - 30 * f)))
    d = ImageDraw.Draw(sheet)
    colw = W_ // len(cols)
    for i, key in enumerate(cols):
        x0 = i * colw
        title = 'Current' if key == 'current' else f"{key}: {OPTIONS[key]['name']}"
        d.text((x0 + colw / 2, 50), title, fill=(255, 255, 255), font=font, anchor='mm')
        for j, mask in enumerate(['circle', 'squircle', 'rounded']):
            y = 110 + j * 230
            if key == 'current':
                ic = Image.open(old_icon).convert('RGBA').resize((192, 192), Image.LANCZOS)
                m = Image.new('L', (768, 768), 0); md = ImageDraw.Draw(m)
                if mask == 'circle': md.ellipse([0, 0, 767, 767], fill=255)
                elif mask == 'rounded': md.rounded_rectangle([0, 0, 767, 767], radius=768*0.22, fill=255)
                else: md.rounded_rectangle([0, 0, 767, 767], radius=768*0.32, fill=255)
                m = m.resize((192, 192), Image.LANCZOS); tmp = Image.new('RGBA', (192, 192)); tmp.paste(ic, (0, 0), m); ic = tmp
            else:
                ic = render(OPTIONS[key], 192, mask)
            sheet.paste(ic, (int(x0 + colw / 2 - 96), y), ic)
        # Real launcher size (48 dp at ~2.6x ≈ 126 px is large; show 64 px for a phone-like look) with label.
        y = 830
        small_ic = (render(OPTIONS[key], 72, 'circle') if key != 'current'
                    else Image.open(old_icon).convert('RGBA').resize((72, 72), Image.LANCZOS))
        if key == 'current':
            m = Image.new('L', (72, 72), 0); ImageDraw.Draw(m).ellipse([0, 0, 71, 71], fill=255)
            tmp = Image.new('RGBA', (72, 72)); tmp.paste(small_ic, (0, 0), m); small_ic = tmp
        sheet.paste(small_ic, (int(x0 + colw / 2 - 36), y), small_ic)
        d.text((x0 + colw / 2, y + 95), 'Clock', fill=(255, 255, 255), font=small, anchor='mm')
    sheet.save(f'{out_dir}/icon_options.png')


# ---- Android vector output ----
def path_data(polys):
    out = []
    for poly in polys:
        out.append('M' + ' L'.join(f'{x:.2f},{y:.2f}' for x, y in poly) + ' Z')
    return ' '.join(out)


def write_xml(key, res):
    opt = OPTIONS[key]
    set_size(opt['size'])
    lit, unlit = layout(TIME)
    ghost_alpha = f"{opt['ghost']:.2f}"
    panel = ''
    if opt['panel']:
        x0, y0, x1, y1 = opt['panel']['rect']; r = opt['panel']['radius']
        panel = (f'    <path android:fillColor="{opt["panel"]["color"]}"\n'
                 f'        android:pathData="M{x0+r},{y0} H{x1-r} A{r},{r} 0 0 1 {x1},{y0+r} V{y1-r} '
                 f'A{r},{r} 0 0 1 {x1-r},{y1} H{x0+r} A{r},{r} 0 0 1 {x0},{y1-r} V{y0+r} A{r},{r} 0 0 1 {x0+r},{y0} Z"/>\n')
    head = ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n')
    fg = (head + panel +
          f'    <!-- unlit segments -->\n    <path android:fillColor="{opt["digit"]}" android:fillAlpha="{ghost_alpha}"\n'
          f'        android:pathData="{path_data(unlit)}"/>\n'
          f'    <!-- lit segments: {TIME} -->\n    <path android:fillColor="{opt["digit"]}"\n'
          f'        android:pathData="{path_data(lit)}"/>\n</vector>\n')
    bg = (head +
          '    <path android:pathData="M0,0 H108 V108 H0 Z">\n'
          '        <aapt:attr name="android:fillColor">\n'
          '            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108">\n'
          f'                <item android:offset="0" android:color="{opt["bg"][0]}"/>\n'
          f'                <item android:offset="1" android:color="{opt["bg"][1]}"/>\n'
          '            </gradient>\n        </aapt:attr>\n    </path>\n</vector>\n')
    mono = (head + f'    <path android:fillColor="#FFFFFFFF"\n        android:pathData="{path_data(lit)}"/>\n</vector>\n')
    open(f'{res}/drawable/ic_launcher_foreground.xml', 'w').write(fg)
    open(f'{res}/drawable/ic_launcher_background.xml', 'w').write(bg)
    open(f'{res}/drawable/ic_launcher_monochrome.xml', 'w').write(mono)


if __name__ == '__main__':
    if sys.argv[1] == 'preview':
        preview(sys.argv[2], sys.argv[3])
    else:
        write_xml(sys.argv[2], sys.argv[3])
