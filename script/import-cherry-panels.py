import argparse
from pathlib import Path
from PIL import Image
import configparser
import io
import zipfile
import re
import yaml

parser = argparse.ArgumentParser(description="Import locally captured CHERRY keyboard artwork and key bindings.")
parser.add_argument("profile", type=Path, help="Trime folder containing baidu_cherry.trime.yaml")
parser.add_argument("skin", type=Path, help="Local CHERRY classic black BDS package")
parser.add_argument("number_reference", type=Path, help="1116x2480 portrait capture of the numeric keyboard")
parser.add_argument("symbol_reference", type=Path, help="1116x2480 portrait capture of the Chinese symbol page")
parser.add_argument("translation_reference", type=Path, help="1116x2480 portrait capture of the active translation toolbar")
parser.add_argument("--clipboard-reference", type=Path, help="1116x2480 portrait capture of the open clipboard")
parser.add_argument("--reference-apk", type=Path, help="Local Baidu APK for clipboard footer icons")
args = parser.parse_args()
theme_dir = args.profile
assets = theme_dir / "backgrounds" / "baidu_cherry"
assets.mkdir(parents=True, exist_ok=True)
skin = zipfile.ZipFile(args.skin)

if args.reference_apk:
    with zipfile.ZipFile(args.reference_apk) as apk:
        for state, resource_file in (("locked", "kqP.png"), ("unlocked", "WSD.png")):
            (assets / f"clipboard-lock-{state}.png").write_bytes(apk.read(f"res/{resource_file}"))
        (assets / "clipboard-footer-settings.png").write_bytes(apk.read("res/4ST.png"))
        (assets / "clipboard-recommend-empty.png").write_bytes(apk.read("res/Z71.png"))
        for action, resource_file in (
            ("segment", "Kh8.png"), ("collect", "GzX.png"), ("edit", "Tsw.png"),
            ("pin", "1VT.png"), ("unpin", "3Jg.png"), ("delete", "GyI.png"),
        ):
            (assets / f"clipboard-action-{action}.png").write_bytes(apk.read(f"res/{resource_file}"))
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


def resource(file):
    ini=configparser.ConfigParser(interpolation=None,strict=False)
    ini.read_string(skin.read('1080/'+file).decode('utf-8-sig'))
    return ini

def sprite(sheet,index):
    x,y,w,h=map(int,resource(f'res/{sheet}.til')[f'IMG{index}']['source_rect'].split(','))
    im=Image.open(io.BytesIO(skin.read(f'1080/res/{sheet}.png')))
    return im.crop((x,y,x+w,y+h))

config=resource('port/res/default.css')
for filename, style in [('cherry-number-enter',242)]:
    sheet,index=config[f'STYLE{style}']['nm_img'].split(',')
    sprite(sheet,int(index)).save(assets/(filename+'.png'))

def erase_labels(image, box, sample_x):
    left,top,right,bottom=box
    image.paste(image.crop((sample_x,top,sample_x+1,bottom)).resize((right-left,bottom-top)),(left,top))

number=Image.open(args.number_reference).convert('RGB').crop((0,1658,1116,2360))
sidebar=number.crop((9,32,186,667))
erase_labels(sidebar,(26,0,150,sidebar.height),149)
sidebar.save(assets/'cherry-number-sidebar.png')
number.save(assets/'cherry-number-panel.png')

symbol=Image.open(args.symbol_reference).convert('RGB').crop((0,1535,1116,2360))
sidebar=symbol.crop((16,13,185,622))
# Use the normal first row as a repeating list background, keeping the frame.
row=sidebar.crop((0,8,169,128))
erase_labels(row,(22,0,156,120),155)
for i in range(5):
    sidebar.paste(row,(0,8+i*120))
sidebar.save(assets/'cherry-symbol-sidebar.png')
symbol.paste(symbol.crop((200,0,201,650)).resize((916,650)),(200,0))
symbol.save(assets/'cherry-symbol-panel.png')

theme_path=theme_dir/'baidu_cherry.trime.yaml'
theme=yaml.safe_load(theme_path.read_text(encoding='utf-8'))
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
        if end<1080: ordered[-1]['width']+=(1080-end)*100/1080
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

theme_path.write_text(yaml.safe_dump(theme, allow_unicode=True, sort_keys=False), encoding="utf-8")
