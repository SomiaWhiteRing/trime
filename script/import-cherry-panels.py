import argparse
from pathlib import Path
from PIL import Image, ImageChops
import configparser
import io
import zipfile
import re
import runpy
import yaml

parser = argparse.ArgumentParser(description="Import locally captured CHERRY keyboard artwork and key bindings.")
parser.add_argument("profile", type=Path, help="Trime folder containing baidu_cherry.trime.yaml")
parser.add_argument("skin", type=Path, help="Local CHERRY classic black BDS package")
parser.add_argument("number_reference", type=Path, help="1116x2480 portrait capture of the numeric keyboard")
parser.add_argument("symbol_reference", type=Path, help="1116x2480 portrait capture of the Chinese symbol page")
parser.add_argument("translation_reference", type=Path, help="1116x2480 portrait capture of the active translation toolbar")
parser.add_argument("--clipboard-reference", type=Path, help="1116x2480 portrait capture of the open clipboard")
parser.add_argument("--reference-apk", type=Path, help="Local Baidu APK for clipboard and pinyin editor icons")
parser.add_argument("--symbol-return-pressed", type=Path, help="1116 x 146 symbol footer crop from y=2290 with Return held")
parser.add_argument("--symbol-footer-pressed", nargs=2, action="append", default=[], metavar=("KEY", "CAPTURE"),
                    help="1116 x 146 footer crop with a symbol key held. Repeat for each key.")
parser.add_argument("--symbol-page-boundary", nargs=3, action="append", default=[], metavar=("KEY", "IDLE", "HELD"),
                    help="Idle and held 1116 x 146 footer crops for a disabled previous or next key")
parser.add_argument("--clipboard-title-tabs", nargs=2, action="append", default=[], metavar=("SELECTED", "CAPTURE"),
                    help="1116 x 114 title crop from y=1630 with history, phrases, or recommend selected")
parser.add_argument("--clipboard-chooser-tabs", nargs=2, action="append", default=[], metavar=("SELECTED", "CAPTURE"),
                    help="1116 x 184 chooser crop from y=1630 with history, phrases, or recommend selected")
parser.add_argument("--clipboard-filter", nargs=2, action="append", default=[], metavar=("SELECTED", "CAPTURE"),
                    help="1116 x 90 history filter crop from y=1754 with one of all, recent, text, number, link selected")
parser.add_argument("--clipboard-recommendation-footer", nargs=2, metavar=("UNLOCKED", "LOCKED"),
                    help="1116 x 87 recommendation footer captures from y=2369 before and after locking")
parser.add_argument("--clipboard-recommendation-send-controls", nargs=2, metavar=("MANUAL", "AUTOMATIC"),
                    help="1116 x 87 recommendation footer crops from y=2369 in manual and automatic mode")
parser.add_argument("--clipboard-send-mode-panel", nargs=2, metavar=("MANUAL", "AUTOMATIC"),
                    help="1116 x 826 local send-mode panel captures from y=1630")
parser.add_argument("--clipboard-send-random", nargs=2, metavar=("SCROLLED_PANEL", "FOOTER"),
                    help="Native selected random row in the scrolled mode panel, and its 1116 x 87 footer")
parser.add_argument("--clipboard-send-sequential", nargs=4, metavar=("IDLE_PANEL", "ACTIVE_PANEL", "BODY", "FOOTER"),
                    help="Native sequential selector rows, recommendation body and footer captures")
parser.add_argument("--clipboard-send-revoke", type=Path,
                    help="1116 x 702 CHERRY recommendation body with the staged Undo and Send controls")
parser.add_argument("--cherry-alphabet-footers", nargs=2, metavar=("CHINESE", "ENGLISH"),
                    help="1116 x 186 portrait alphabet footer captures from y=2270")
parser.add_argument("--cherry-alphabet-top", type=Path,
                    help="1116 x 136 idle CHERRY toolbar and first thirteen panel rows from y=1631")
args = parser.parse_args()
theme_dir = args.profile
assets = theme_dir / "backgrounds" / "baidu_cherry"
assets.mkdir(parents=True, exist_ok=True)
skin = zipfile.ZipFile(args.skin)

if args.cherry_alphabet_top:
    reference = Image.open(args.cherry_alphabet_top).convert("RGB")
    if reference.size != (1116, 136):
        raise ValueError("Expected a 1116 x 136 CHERRY toolbar and panel crop")
    for name, left, right in (
        ("menu", 0, 177), ("script", 177, 366), ("translate", 366, 558),
        ("clipboard", 558, 750), ("edit", 750, 939), ("hide", 939, 1116),
    ):
        reference.crop((left, 0, right, 123)).save(assets / f"tool-{name}.png")
    background = Image.new("RGB", (1, 702), (13, 13, 13))
    background.paste(reference.crop((0, 123, 1, 136)), (0, 0))
    background.save(assets / "cherry-alphabet-background.png")


if args.reference_apk:
    with zipfile.ZipFile(args.reference_apk) as apk:
        (assets / "cherry-preedit-pencil.png").write_bytes(apk.read("assets/1080/30.png"))
        editor_tiles = configparser.ConfigParser(interpolation=None)
        editor_tiles.read_string(apk.read("assets/1080/edit_panel.til").decode("utf-8-sig"))
        editor_sheet = Image.open(io.BytesIO(apk.read("assets/1080/edit_panel.png")))
        for name, index in (("left", 3), ("right", 4)):
            x, y, width, height = map(int, editor_tiles[f"IMG{index}"]["source_rect"].split(","))
            editor_sheet.crop((x, y, x + width, y + height)).save(assets / f"cherry-preedit-{name}.png")
        for state, resource_file in (("locked", "kqP.png"), ("unlocked", "WSD.png")):
            (assets / f"clipboard-lock-{state}.png").write_bytes(apk.read(f"res/{resource_file}"))
        (assets / "clipboard-footer-settings.png").write_bytes(apk.read("res/4ST.png"))
        (assets / "clipboard-recommend-empty.png").write_bytes(apk.read("res/Z71.png"))
        (assets / "clipboard-layout-regular.png").write_bytes(apk.read("res/7dz.png"))
        (assets / "clipboard-layout-compact.png").write_bytes(apk.read("res/dW0.png"))
        for action, resource_file in (
            ("segment", "Kh8.png"), ("translate", "N74.png"), ("search", "iRK.png"), ("collect", "GzX.png"), ("edit", "Tsw.png"),
            ("pin", "1VT.png"), ("unpin", "3Jg.png"), ("delete", "GyI.png"), ("multi-select", "6CO.png"),
        ):
            (assets / f"clipboard-action-{action}.png").write_bytes(apk.read(f"res/{resource_file}"))
        for action, resource_file in (("copy", "3BS.png"), ("translate", "GDX.png"), ("search", "96z.png")):
            (assets / f"clipboard-segments-{action}.png").write_bytes(apk.read(f"res/{resource_file}"))
        for state, resource_file in (("off", "73x.png"), ("on", "HNF.png")):
            (assets / f"clipboard-switch-{state}.png").write_bytes(apk.read(f"res/{resource_file}"))

if args.clipboard_reference:
    reference = Image.open(args.clipboard_reference).convert("RGBA")
    for name, visible, bounds in (
        ("back", (19, 1568, 55, 1624), (0, 1541, 111, 1652)),
        ("menu", (1038, 1574, 1087, 1617), (993, 1535, 1116, 1658)),
    ):
        x, y, right, bottom = bounds
        glyph = Image.new("RGBA", (right - x, bottom - y))
        glyph.paste(reference.crop(visible), (visible[0] - x, visible[1] - y))
        if name == "menu":
            glyph.paste(reference.crop((997, 1546, 1002, 1644)), (4, 11))
        glyph.save(assets / f"clipboard-title-{name}.png")


tab_artwork = {
    "history": (117, 168, 140, 266),
    "phrases": (297, 168, 320, 446),
    "recommend": (477, 126, 497, 582),
}
for selected, capture in args.clipboard_title_tabs:
    if selected not in tab_artwork:
        parser.error(f"unknown clipboard title tab {selected!r}")
    reference = Image.open(capture).convert("RGB")
    if reference.size != (1116, 114):
        raise ValueError("Expected a CHERRY title crop at 1116 x 114")
    left, right = {"history": (89, 315), "phrases": (268, 494), "recommend": (447, 632)}[selected]
    contour = reference.crop((left, 11, right, 111))
    _, _, glyph_left, glyph_right = tab_artwork[selected]
    contour.paste((54, 54, 54), (glyph_left - left, 29, glyph_right - left, 73))
    contour.save(assets / f"clipboard-tab-{selected}-contour.png")
    for name, size, origin, bounds in (
        ("back", 111, (0, 8), (25, 41, 50, 84)),
        ("menu", 123, (993, 2), (1045, 46, 1081, 77)),
    ):
        left, top, right, bottom = bounds
        patch = reference.crop(bounds)
        background = Image.new("RGB", patch.size, (67, 67, 67))
        mask = ImageChops.difference(patch, background).convert("L").point(lambda value: 255 if value else 0)
        control = Image.new("RGBA", (size, size))
        control.paste(patch, (left - origin[0], top - origin[1]), mask)
        control.save(assets / f"clipboard-title-{name}-reference.png")
    reference.crop((991, 11, 1001, 111)).save(assets / "clipboard-title-divider-reference.png")
    for name, (tab_left, width, glyph_left, glyph_right) in tab_artwork.items():
        tile = Image.new("RGBA", (width, 105))
        tile.paste(reference.crop((glyph_left, 40, glyph_right, 84)), (glyph_left - tab_left, 30))
        state = "active" if selected == name else "idle"
        tile.save(assets / f"clipboard-tab-{name}-{state}.png")


chooser_bounds = {"history": (48, 246), "phrases": (264, 462), "recommend": (480, 636)}
for selected, capture in args.clipboard_chooser_tabs:
    if selected not in chooser_bounds:
        parser.error(f"unknown clipboard chooser tab {selected!r}")
    reference = Image.open(capture).convert("RGB")
    if reference.size != (1116, 184):
        raise ValueError("Expected a CHERRY chooser crop at 1116 x 184")
    if selected == "phrases":
        # These margins show the undisturbed dimmed pane (43) under the gray shell (67).
        for side, left, right in (("left", 0, 60), ("right", 1056, 1116)):
            corner = reference.crop((left, 120, right, 180)).convert("L")
            mask = corner.point(lambda level: max(0, min(255, round((level - 43) * 255 / 24))))
            artwork = Image.new("RGBA", corner.size, (67, 67, 67))
            artwork.putalpha(mask)
            artwork.save(assets / f"clipboard-chooser-corner-{side}.png")
    for name, (left, right) in chooser_bounds.items():
        state = "active" if selected == name else "idle"
        reference.crop((left, 42, right, 123)).save(assets / f"clipboard-chooser-{name}-{state}.png")


filters = ("all", "recent", "text", "number", "link")
for selected, capture in args.clipboard_filter:
    if selected not in filters:
        parser.error(f"unknown clipboard filter {selected!r}")
    reference = Image.open(capture).convert("RGB")
    if reference.size != (1116, 90):
        raise ValueError("Expected a CHERRY clipboard filter crop at 1116 x 90")
    for index, name in enumerate(filters):
        left = 24 + 135 * index
        state = "active" if name == selected else "idle"
        reference.crop((left, 0, left + 135, 90)).save(assets / f"clipboard-filter-{name}-{state}.png")


if args.clipboard_recommendation_footer:
    for state, capture in zip(("unlocked", "locked"), args.clipboard_recommendation_footer):
        reference = Image.open(capture).convert("RGB")
        if reference.size != (1116, 87):
            raise ValueError("Expected a CHERRY recommendation footer crop at 1116 x 87")
        reference.crop((0, 0, 183, 87)).save(assets / f"clipboard-recommend-lock-{state}.png")
        if state == "unlocked":
            reference.crop((288, 0, 420, 87)).save(assets / "clipboard-recommend-settings.png")


if args.clipboard_recommendation_send_controls:
    for state, capture in zip(("manual", "automatic"), args.clipboard_recommendation_send_controls):
        reference = Image.open(capture).convert("RGB")
        if reference.size != (1116, 87):
            raise ValueError("Expected a CHERRY recommendation footer crop at 1116 x 87")
        reference.crop((615, 0, 783, 87)).save(assets / f"clipboard-recommend-send-{state}.png")


if args.clipboard_send_mode_panel:
    for selected, capture in zip(("manual", "automatic"), args.clipboard_send_mode_panel):
        reference = Image.open(capture).convert("RGB")
        if reference.size != (1116, 826):
            raise ValueError("Expected a CHERRY send-mode panel crop at 1116 x 826")
        reference.crop((0, 1, 1116, 107)).save(assets / "clipboard-send-mode-title.png")
        for name, top in (("manual", 116), ("automatic", 327)):
            state = "active" if selected == name else "idle"
            reference.crop((0, top, 1116, top + 211)).save(assets / f"clipboard-send-mode-{name}-{state}.png")
        if selected == "manual":
            reference.crop((0, 538, 1116, 749)).save(assets / "clipboard-send-mode-random-idle.png")

if args.clipboard_send_random:
    panel, footer = [Image.open(path).convert("RGB") for path in args.clipboard_send_random]
    if panel.size != (1116, 826) or footer.size != (1116, 87):
        raise ValueError("Expected 1116 x 826 mode panel and 1116 x 87 footer captures")
    panel.crop((0, 380, 1116, 591)).save(assets / "clipboard-send-mode-random-active.png")
    footer.crop((615, 0, 783, 87)).save(assets / "clipboard-recommend-send-random.png")



def recommendation_capsule(body, start):
    plane = body.crop((start, 408, 1116, 528)).convert("RGBA")
    for y in range(plane.height):
        left = next(x for x in range(plane.width) if min(plane.getpixel((x, y))[:3]) >= 220)
        for x in range(left):
            gray = plane.getpixel((x, y))[0]
            alpha = max(0, round((gray - 67) * 255 / 188)) if x >= left - 2 else 0
            plane.putpixel((x, y), (255, 255, 255, alpha))
    return plane


if args.clipboard_send_sequential:
    idle, active, body, footer = [Image.open(path).convert("RGB") for path in args.clipboard_send_sequential]
    if any(im.size != (1116, 826) for im in (idle, active)) or body.size != (1116, 702) or footer.size != (1116, 87):
        raise ValueError("Unexpected CHERRY sequential panel dimensions")
    for name, panel in (("idle", idle), ("active", active)):
        panel.crop((0, 591, 1116, 802)).save(assets / f"clipboard-send-mode-sequential-{name}.png")
    footer.crop((615, 0, 783, 87)).save(assets / "clipboard-recommend-send-sequential.png")
    recommendation_capsule(body, 951).save(assets / "clipboard-recommend-send-plane.png")

if args.clipboard_send_revoke:
    body = Image.open(args.clipboard_send_revoke).convert("RGB")
    if body.size != (1116, 702):
        raise ValueError("Expected staged CHERRY recommendation body at 1116 x 702")
    controls = recommendation_capsule(body, 819)
    controls.crop((0, 0, 132, 120)).save(assets / "clipboard-recommend-revoke.png")
    controls.crop((132, 0, 297, 120)).save(assets / "clipboard-recommend-staged-plane.png")


alphabet_glyphs = {
    "F6": ("123", "number"), "F1": ("符", "symbols"), "F39": ("前往", "enter-go"),
    "，": ("，", "comma-zh"), "。": ("。", "period-zh"), ".": (".", "period-en"),
}
if args.cherry_alphabet_footers:
    chinese, english = [Image.open(path).convert("RGB") for path in args.cherry_alphabet_footers]
    if chinese.size != (1116, 186) or english.size != (1116, 186):
        raise ValueError("Expected 1116 x 186 CHERRY alphabet footer captures")
    for name, source, key_left, key_right, glyph in (
        ("number", chinese, 0, 112, (21, 63, 92, 102)),
        ("symbols", chinese, 845, 957, (875, 59, 928, 106)),
        ("enter-go", chinese, 957, 1115, (991, 59, 1081, 107)),
        ("comma-zh", chinese, 223, 335, (271, 80, 287, 101)),
        ("period-zh", chinese, 734, 845, (776, 73, 803, 100)),
        ("period-en", english, 734, 845, (782, 86, 796, 99)),
    ):
        tile = Image.new("RGBA", (key_right - key_left, 172))
        left, top, right, bottom = glyph
        for y in range(top, bottom):
            low = source.getpixel((left - 1, y))[1]
            high = source.getpixel((right, y))[1]
            for x in range(left, right):
                background = low + (high - low) * (x - left) / (right - left)
                contrast = source.getpixel((x, y))[1] - background
                if contrast > 2:
                    alpha = min(255, round(255 * contrast / (179 - background)))
                    tile.putpixel((x - key_left, y - 12), (179, 179, 179, alpha))
        tile.save(assets / f"cherry-alphabet-{name}.png")


def resource(file):
    ini=configparser.ConfigParser(interpolation=None,strict=False)
    ini.read_string(skin.read('1080/'+file).decode('utf-8-sig'))
    return ini

def sprite(sheet,index):
    x,y,w,h=map(int,resource(f'res/{sheet}.til')[f'IMG{index}']['source_rect'].split(','))
    im=Image.open(io.BytesIO(skin.read(f'1080/res/{sheet}.png')))
    return im.crop((x,y,x+w,y+h))

def save_keycap(style, width, foreground):
    properties = config[f"STYLE{style}"]
    for state, field in (("normal", "nm_img"), ("pressed", "hl_img")):
        sheet, index = properties[field].split(",")
        tiles = resource(f"res/{sheet}.til")
        tile = tiles[f"IMG{index}"]
        source_x, _, source_width, _ = map(int, tile["source_rect"].split(","))
        inner_x, _, inner_width, _ = map(int, tile["inner_rect"].split(","))
        left = inner_x - source_x
        right = left + inner_width
        original = sprite(sheet, int(index)).convert("RGBA")
        if width != source_width:
            middle_width = width - (source_width - inner_width)
            cap = Image.new("RGBA", (width, original.height))
            cap.paste(original.crop((0, 0, left, original.height)), (0, 0))
            middle = original.crop((left, 0, right, original.height))
            cap.paste(middle.resize((middle_width, original.height), Image.Resampling.BILINEAR), (left, 0))
            cap.paste(original.crop((right, 0, source_width, original.height)), (left + middle_width, 0))
        else:
            cap = original
        if foreground is not None:
            sheet, index = config[f"STYLE{foreground}"][field].split(",")
            glyph = sprite(sheet, int(index)).convert("RGBA")
            cap = cap.copy()
            cap.alpha_composite(glyph, ((width - glyph.width) // 2, (cap.height - glyph.height) // 2 - 9))
        rgb = cap.convert("RGB").point([value * 7 // 10 for value in range(256)] * 3)
        rgb.putalpha(cap.getchannel("A"))
        rgb.save(assets / f"cherry-keycap-{style}-{width}-{state}.png")


config=resource('port/res/default.css')
keycaps = {
    (item["back_style"], int(item["view_rect"].split(",")[2]), "253" if item["center"] == "F38" else None)
    for layout in ("py_26", "en_26")
    for name, item in resource(f"port/{layout}.ini").items()
    if re.fullmatch(r"KEY\d+", name)
}
for style_id, key_width, foreground in sorted(keycaps):
    save_keycap(style_id, key_width, foreground)
Image.new("RGB", (1, 1), (13, 13, 13)).save(assets / "cherry-keys-background.png")

for filename, style in (
    ("cherry-shift-off", 105), ("cherry-shift-on", 131), ("cherry-shift-marker", 250),
    ("cherry-language-chinese", 148), ("cherry-language-english", 108),
    ("cherry-abc-direct", 109), ("cherry-abc-composing", 110),
):
    sheet,index=config[f'STYLE{style}']['nm_img'].split(',')
    sprite(sheet,int(index)).save(assets/(filename+'.png'))

def erase_labels(image, box, sample_x):
    left,top,right,bottom=box
    image.paste(image.crop((sample_x,top,sample_x+1,bottom)).resize((right-left,bottom-top)),(left,top))

number=Image.open(args.number_reference).convert('RGB').crop((0,1658,1116,2360))
enter = number.crop((892, 177, 1116, 352))
for y in range(54, 107):
    left = enter.getpixel((66, y))
    right = enter.getpixel((162, y))
    for x in range(67, 162):
        weight = (x - 66) / 96
        enter.putpixel((x, y), tuple(round(a + (b - a) * weight) for a, b in zip(left, right)))
enter.save(assets / "cherry-number-enter.png")

def render_pressed_numeric_key(name, idle, style, preserve_label=True):
    sheet, index = config[f"STYLE{style}"]["hl_img"].split(",")
    tile = resource(f"res/{sheet}.til")[f"IMG{index}"]
    source_x, source_y, _, _ = map(int, tile["source_rect"].split(","))
    inner_x, inner_y, inner_width, inner_height = map(int, tile["inner_rect"].split(","))
    sprite_image = sprite(sheet, int(index)).convert("RGBA")
    inner_x -= source_x
    inner_y -= source_y
    xs = (0, inner_x, inner_x + inner_width, sprite_image.width)
    ys = (0, inner_y, inner_y + inner_height, sprite_image.height)
    scale = number.width / 1080
    dest_xs = (0, round(xs[1] * scale), idle.width - round((xs[3] - xs[2]) * scale), idle.width)
    dest_ys = (0, round(ys[1] * scale), idle.height - round((ys[3] - ys[2]) * scale), idle.height)
    highlighted = idle.convert("RGBA")
    for row in range(3):
        for col in range(3):
            patch = sprite_image.crop((xs[col], ys[row], xs[col + 1], ys[row + 1])).resize(
                (dest_xs[col + 1] - dest_xs[col], dest_ys[row + 1] - dest_ys[row]),
                Image.Resampling.BILINEAR,
            )
            highlighted.alpha_composite(patch, (dest_xs[col], dest_ys[row]))
    if preserve_label:
        red, green, blue = idle.split()
        label = ImageChops.darker(red, ImageChops.darker(green, blue))
        label = label.point(lambda level: max(0, min(255, (level - 100) * 6)))
        if name == "space":
            logo_bounds = (52, 84, 174, 112)
            logo = ImageChops.subtract(red, green).crop(logo_bounds)
            logo = logo.point(lambda level: max(0, min(255, (level - 115) * 8)))
            label.paste(logo, logo_bounds)
        highlighted.paste(idle, mask=label)
    highlighted.save(assets / f"cherry-number-{name}-pressed.png")


render_pressed_numeric_key("enter", enter, 242, preserve_label=False)
for index in range(9):
    x, y = 200 + 230 * (index % 3), 2 + 175 * (index // 3)
    render_pressed_numeric_key(str(index + 1), number.crop((x, y, x + 220, y + 175)), 246 if index < 3 else 241)
for name, x, y, width, style in (
    ("period", 200, 527, 220, 241),
    ("0", 430, 527, 220, 241),
    ("symbols", 660, 527, 220, 241),
    ("backspace", 890, 2, 226, 252),
    ("space", 890, 352, 226, 242),
    ("return", 890, 527, 226, 242),
):
    render_pressed_numeric_key(name, number.crop((x, y, x + width, y + 175)), style)
sidebar=number.crop((9,32,186,667))
erase_labels(sidebar,(26,0,150,sidebar.height),149)
sidebar.save(assets/'cherry-number-sidebar.png')
number.save(assets/'cherry-number-panel.png')

symbol=Image.open(args.symbol_reference).convert('RGB').crop((0,1535,1116,2360))
unlocked = symbol.crop((20, 675, 242, 796)).convert("RGBA")
erase_labels(unlocked, (88, 16, 149, 86), 84)
sheet, index = config["STYLE151"]["nm_img"].split(",")
icon = sprite(sheet, int(index)).convert("RGBA")
icon = icon.resize((91, 63), Image.Resampling.LANCZOS)
unlocked.paste((179, 179, 179, 255), (73, 19, 164, 82), icon.getchannel("A"))
unlocked.save(assets / "cherry-symbol-lock-unlocked.png")
for name, left, right, style, x, y, height, shade in (
    ("previous-enabled", 243, 446, 202, 318, 711, 31, 179),
    ("next-disabled", 447, 661, 201, 532, 710, 32, 87),
):
    arrow = symbol.crop((left, 675, right, 796)).convert("RGBA")
    dx, dy = x - left, y - 675
    erase_labels(arrow, (dx - 3, dy - 3, dx + 58, dy + height + 3), dx - 5)
    sheet, index = config[f"STYLE{style}"]["nm_img"].split(",")
    mask = sprite(sheet, int(index)).convert("RGBA").getchannel("A")
    mask = mask.resize((55, height), Image.Resampling.LANCZOS)
    arrow.paste((shade, shade, shade, 255), (dx, dy, dx + 55, dy + height), mask)
    arrow.save(assets / f"cherry-symbol-{name}.png")
sidebar=symbol.crop((16,13,185,622))
# Use the normal first row as a repeating list background, keeping the frame.
row=sidebar.crop((0,8,169,128))
erase_labels(row,(22,0,156,120),155)
for i in range(5):
    sidebar.paste(row,(0,8+i*120))
sidebar.save(assets/'cherry-symbol-sidebar.png')
symbol.paste(symbol.crop((200,0,201,650)).resize((916,650)),(200,0))
symbol.save(assets/'cherry-symbol-panel.png')
footer_bounds = {
    "lock-locked": (20, 242), "lock-unlocked": (20, 242),
    "previous": (243, 446), "next": (447, 664), "backspace": (662, 872),
    "return": (873, 1099),
}
if args.symbol_return_pressed:
    args.symbol_footer_pressed.append(("return", args.symbol_return_pressed))
for key, capture in args.symbol_footer_pressed:
    if key not in footer_bounds:
        parser.error(f"unknown symbol footer key {key!r}")
    pressed_footer = Image.open(capture).convert("RGB")
    if pressed_footer.size != (1116, 146):
        raise ValueError("Expected the held CHERRY symbol footer at 1116 x 146")
    left, right = footer_bounds[key]
    pressed_footer.crop((left, 16, right, 137)).save(assets / f"cherry-symbol-{key}-pressed.png")
for key, idle, held in args.symbol_page_boundary:
    if key not in ("previous", "next"):
        parser.error(f"unknown paging key {key!r}")
    left, right = footer_bounds[key]
    for name, capture in ((f"{key}-disabled", idle), (f"{key}-disabled-pressed", held)):
        footer = Image.open(capture).convert("RGB")
        if footer.size != (1116, 146):
            raise ValueError("Expected a CHERRY symbol footer at 1116 x 146")
        footer.crop((left, 16, right, 137)).save(assets / f"cherry-symbol-{name}.png")

theme_path=theme_dir/'baidu_cherry.trime.yaml'
theme=yaml.safe_load(theme_path.read_text(encoding='utf-8'))
theme['style']['keyboard_padding_bottom'] = 8
theme['preset_color_schemes']['baidu_cherry']['keyboard_back_color'] = 'cherry-keys-background.png'
keys=theme['preset_keys']
keys['CherryQuickSymbols']={'label':'符','send':'FUNCTION','command':'liquid_keyboard','option':'emoji'}
keys['CherryDeleteWord']={'label':'','send':'Control+BackSpace'}
keys['CherryTone']={'label':'','send':'VOICE_ASSIST'}
keys['CherryAsciiSwitch']={'label':'','toggle':'ascii_mode','send':'SWITCH_CHARSET','states':['中','英']}

names={'F1':'CherrySymbols','F4':'Keyboard_default','F6':'CherryNumber','F7':'CherryQuickSymbols','F10':'CherryShift','F11':'CherryShift',
       'F15':'CherryAsciiSwitch','F16':'CherryAsciiSwitch','F25':',','F36':'CherryDelete','F38':'CherrySpace','F39':'Return',
       'F40':'CherryDeleteWord','F51':'Left','F52':'Right','F72':'CherryTone'}

def action(name):
    return names.get(name,name)

def qwerty_bounds(item):
    x, y, w, h = map(int, item["view_rect"].split(","))
    # The original renderer starts the keycaps five skin units below the panel origin.
    top = int((y + 5) * 702 / 647)
    return {
        "left": x / 1080,
        "top": top / 702,
        "right": (x + w) / 1080,
        "bottom": (top + round((h + 2) * 702 / 647)) / 702,
    }


def populate(layout, builder, english=False):
    original=resource(f'port/{layout}.ini')
    rows={}
    available={}
    for n in original.sections():
        if not re.fullmatch(r'KEY\d+',n):continue
        item=original[n]
        x,y,w,h=map(int,item['view_rect'].split(','))
        available.setdefault(y,[]).append((x,w,item))
    for y,row in sorted(available.items()):
        end=0
        ordered=[]
        for x,w,item in sorted(row):
            if x>end: ordered.append({'width': (x-end)*100/1080})
            existing=builder.get(action(item['center']),{})
            key=dict(existing,click=action(item['center']),width=w*100/1080)
            key["bounds"] = qwerty_bounds(item)
            if args.cherry_alphabet_footers and item["center"] in alphabet_glyphs:
                label, image = alphabet_glyphs[item["center"]]
                key["foreground_artwork"] = {label: f"cherry-alphabet-{image}.png"}
            if item["center"] in {"，", "。", "."}:
                key["label_symbol"] = " "
            style_id = item["back_style"]
            key["key_back_color"] = f"cherry-keycap-{style_id}-{w}-normal.png"
            key["hilited_key_back_color"] = f"cherry-keycap-{style_id}-{w}-pressed.png"
            for target in ('long_click','swipe_up','swipe_down','swipe_left','swipe_right'):
                key.pop(target,None)
            for source,target in [('up','swipe_up'),('down','swipe_down'),('left','swipe_left'),('right','swipe_right'),('hold','long_click')]:
                if source in item: key[target]=action(item[source])
            if 'up' in item and 'hold' not in item:
                key['long_click']=action(item['up'])
            if item['center'] in {'F15','F16'}:
                key['label']='英' if english else '中'
                key['label_symbol']=' '
            if item['center']=='F1':
                key['label_symbol']=' '
            if item['center']=='F38':
                key['label_symbol']=' '
            if len(item['center'])==1 and 'fore_style' in item:
                if item['center'].isascii() and item['center'].isalpha():
                    key['label']=item['center'].upper()
            ordered.append(key)
            end=x+w
        if end < 1079:
            ordered.append({'width': (1080 - end) * 100 / 1080})
        rows[y]=ordered
    return [k for row in rows.values() for k in row]

style=theme['preset_keyboards']['qwerty']
builder={k['click']:k for k in style['keys'] if 'click' in k}
builder.setdefault('CherryAsciiSwitch', builder.get('Mode_switch', {}))
pinyin=populate('py_26',builder)
ascii_keys=populate('en_26',builder,True)
for id in ('default','qwerty'):
    theme['preset_keyboards'][id]=dict(style,keys=pinyin)
theme['preset_keyboards']['cherry_ascii']=dict(style,keys=ascii_keys,ascii_mode=1)
def popup_action(character):
    action_id = "CherryHold" + "".join(f"U{ord(char):X}" for char in character)
    keys[action_id] = {"label": character, "commit": character}
    return action_id


for keyboard_id in ("default", "qwerty", "cherry_ascii"):
    for key in theme["preset_keyboards"][keyboard_id]["keys"]:
        primary = key.get("click", "")
        upper = key.get("swipe_up", "")
        if len(primary) != 1 or len(upper) != 1:
            continue
        if primary.isascii() and primary.isalpha():
            alternatives = [primary.upper(), primary] if primary in "qa" else [primary, primary.upper()]
        else:
            alternatives = [primary]
        key["popup"] = [popup_action(char) for char in [upper, *alternatives]]

theme["style"].update(popup_width=32, popup_height=102, popup_key_height=50, popup_text_size=30, popup_radius=4)
theme["preset_color_schemes"]["baidu_cherry"].update(
    popup_back_color="0xf2242424",
    hilited_popup_back_color="0xf2242424",
    popup_text_color="0x666666",
    hilited_popup_text_color="0xbfbfbf",
)
Image.open(args.translation_reference).crop((366,1535,558,1658)).save(assets/"tool-translate-on.png")
keys["CherryTranslate"] = {
    "label": "翻译", "send": "FUNCTION", "command": "cherry_translation", "toggle": "cherry_translation",
}
for button in theme["tool_bar"]["buttons"]:
    if button["action"] == "CherryTranslate":
        button["foreground"]["option_styles"] = ["tool-translate.png", "tool-translate-on.png"]

if "cherry_english" in theme["preset_keyboards"]:
    english_importer = runpy.run_path(str(Path(__file__).with_name("import-cherry-english.py")))
    english_importer["configure_keyboard"](theme)

theme_path.write_text(yaml.safe_dump(theme, allow_unicode=True, sort_keys=False), encoding="utf-8")
