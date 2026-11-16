import argparse
import copy
import re
import struct
import zipfile
from pathlib import Path

from PIL import Image
import marisa_trie
import yaml


def read_english_dictionary(apk):
    with zipfile.ZipFile(apk) as source:
        binary = source.read("assets/dict/en5.bin")
    trie_start, trie_size, _, offset_size, _, record_size = struct.unpack_from("<6I", binary, 12)
    offset_start = trie_start + trie_size
    record_start = offset_start + offset_size
    if binary[trie_start:trie_start + 16] != b"We love Marisa.\0":
        raise ValueError("Unexpected English dictionary trie header")
    trie = marisa_trie.Trie().frombytes(binary[trie_start:offset_start])
    offsets = struct.unpack_from(f"<{len(trie) + 1}I", binary, offset_start)
    if offset_size != len(offsets) * 4 or offsets[-1] != record_size or record_start + record_size > len(binary):
        raise ValueError("Unexpected English dictionary record table")
    return trie, offsets, binary[record_start:record_start + record_size]


def write_profile(apk, directory):
    trie, offsets, records = read_english_dictionary(apk)
    directory.mkdir(parents=True, exist_ok=True)
    entries = {}
    lexical_weights = {}
    key_weights = {}
    word_pattern = re.compile(r"[A-Za-z]+(?:['-][A-Za-z]+)*")
    cost_scale = max(end - start for start, end in zip(offsets, offsets[1:])) + 1
    for word in trie:
        if not word_pattern.fullmatch(word):
            continue
        key = word.lower().replace("'", "").replace("-", "")
        index = trie[word]
        start, end = offsets[index:index + 2]
        best_weight = 0
        cursor = start
        while cursor < end:
            # Lower costs rank first. Use record order to break ties.
            weight = (256 - records[cursor + 3]) * cost_scale + (end - cursor)
            best_weight = max(best_weight, weight)
            flags = records[cursor]
            cursor += 4
            candidate = word
            if (flags & 0x03) == 0x03 or (flags & 0x0C) == 0x0C:
                size = records[cursor]
                cursor += 1
                candidate = records[cursor:cursor + size].decode("utf-8")
                cursor += size
            if word_pattern.fullmatch(candidate):
                entry = (candidate, key)
                entries[entry] = max(entries.get(entry, 0), weight)
                if candidate.lower().replace("'", "").replace("-", "") == key:
                    lexical_weights[entry] = max(lexical_weights.get(entry, 0), weight)
        key_weights[key] = max(key_weights.get(key, 0), best_weight)
    for (candidate, key), weight in lexical_weights.items():
        for length in range(1, len(key)):
            prefix = key[:length]
            # Put completions under the same code so Rime orders them by cost.
            entries.setdefault((candidate, prefix), weight)
            key_weights[prefix] = max(key_weights.get(prefix, 0), weight)
    known_codes = set(key_weights)
    for (candidate, key), weight in lexical_weights.items():
        for index in range(len(key) - 1):
            if key[index] == key[index + 1]:
                continue
            typo = key[:index] + key[index + 1] + key[index] + key[index + 2:]
            if typo in known_codes:
                continue
            entry = (candidate, typo)
            entries[entry] = max(entries.get(entry, 0), weight)
            key_weights[typo] = max(key_weights.get(typo, 0), weight)
    # Horizontal offsets and key widths from CHERRY's 1080 px en_26 layout.
    keys = {
        letter: (row, offset + index * 108)
        for row, (offset, letters) in enumerate(
            ((0, "qwertyuiop"), (54, "asdfghjkl"), (121, "zxcvbnm"))
        )
        for index, letter in enumerate(letters)
    }
    neighbours = {
        letter: [
            other for other, (other_row, other_x) in keys.items()
            if other != letter and abs(other_row - row) <= 1 and abs(other_x - x) <= 108
        ]
        for letter, (row, x) in keys.items()
    }
    substitutions = {}
    lowest_lexical_weight = min(lexical_weights.values())
    for (candidate, key), weight in lexical_weights.items():
        if len(key) < 4:
            continue
        # Reserve the earlier slots for stronger literal completions.
        corrected_weight = weight - 18 * cost_scale - 1
        if corrected_weight < lowest_lexical_weight:
            continue
        for index, letter in enumerate(key):
            for neighbour in neighbours[letter]:
                typo = key[:index] + neighbour + key[index + 1:]
                if (candidate, typo) in entries or corrected_weight <= substitutions.get(typo, (0, ""))[0]:
                    continue
                substitutions[typo] = (corrected_weight, candidate)
    for typo, (weight, candidate) in substitutions.items():
        entries[(candidate, typo)] = weight
        key_weights[typo] = max(key_weights.get(typo, 0), weight)
    for key, weight in key_weights.items():
        # Leave the literal input at the head of its candidate list.
        entries[(key, key)] = weight + 1
    with (directory / "cherry_english.dict.yaml").open("w", encoding="utf-8", newline="\n") as stream:
        stream.write("---\nname: cherry_english\nversion: '5'\nsort: by_weight\nuse_preset_vocabulary: false\n...\n")
        for (candidate, key), weight in entries.items():
            stream.write(f"{candidate}\t{key}\t{weight}\n")

    (directory / "cherry_english.schema.yaml").write_text(
        r"""schema:
  schema_id: cherry_english
  name: English
  version: "1"
switches:
  - name: ascii_mode
    reset: 0
    states: [ English, ASCII ]
engine:
  processors: [ ascii_composer, key_binder, speller, punctuator, selector, navigator, express_editor ]
  segmentors: [ ascii_segmentor, abc_segmentor, punct_segmentor, fallback_segmentor ]
  translators: [ punct_translator, table_translator ]
  filters: [ uniquifier ]
speller:
  alphabet: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
  algebra:
    - derive/^([a-z])([a-z]*)$/\U$1\L$2/
translator:
  dictionary: cherry_english
  enable_completion: false
  enable_sentence: false
  enable_user_dict: true
  comment_format:
    - xform/^.*$//
punctuator:
  import_preset: default
key_binder:
  import_preset: default
""", encoding="utf-8")
    theme_path = directory / "baidu_cherry.trime.yaml"
    if theme_path.is_file():
        theme = yaml.safe_load(theme_path.read_text(encoding="utf-8"))
        configure_keyboard(theme)
        theme_path.write_text(yaml.safe_dump(theme, allow_unicode=True, sort_keys=False), encoding="utf-8")
    pinyin_patch = directory / "luna_pinyin.custom.yaml"
    if pinyin_patch.is_file():
        config = yaml.safe_load(pinyin_patch.read_text(encoding="utf-8"))
        dependencies = config["patch"].setdefault("schema/dependencies/+", [])
        if "cherry_english" not in dependencies:
            dependencies.append("cherry_english")
        pinyin_patch.write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding="utf-8")
    return (len(trie), len(offsets), len(records), len(entries))


def configure_keyboard(theme):
    palette = theme["preset_color_schemes"]["baidu_cherry"]
    palette["hilited_candidate_back_color"] = "0x00000000"
    palette["hilited_candidate_text_color"] = "0xaa304e"
    # CHERRY's cand1.cnd assigns 66 px per cell at 1080 px skin width.
    theme["style"]["candidate_padding"] = 11
    theme["style"]["candidate_text_size"] = 19
    presets = theme["preset_keys"]
    presets["CherryAsciiSwitch"] = {"label": "中", "send": "FUNCTION", "command": "cherry_language"}
    presets["CherryReturnChinese"] = {"label": "En", "send": "FUNCTION", "command": "cherry_language"}
    presets["CherryEnglishDirect"] = {"label": "abc", "send": "FUNCTION", "command": "cherry_english_enable"}
    presets["CherryEnglishComposing"] = {
        "label": "a\u0332b\u0332c\u0332", "send": "FUNCTION", "command": "cherry_english_disable",
    }
    direct = theme["preset_keyboards"]["cherry_ascii"]
    direct["label_transform"] = "none"
    for key in direct["keys"]:
        action = key.get("click", "")
        if action in {",", "CherryEnglishDirect"}:
            key.update(click="CherryEnglishDirect", label="abc")
        elif action in {"CherryAsciiSwitch", "CherryReturnChinese"}:
            key.update(click="CherryReturnChinese", label="En")
        elif re.fullmatch("[a-z]", action):
            key.pop("label", None)
    composing = copy.deepcopy(direct)
    composing.update(name="CHERRY English", ascii_mode=0)
    for key in composing["keys"]:
        if key.get("click") == "CherryEnglishDirect":
            key.update(click="CherryEnglishComposing", label=presets["CherryEnglishComposing"]["label"])
    theme["preset_keyboards"]["cherry_english"] = composing


def import_candidate_arrow(reference, directory):
    source = Image.open(reference).convert("RGBA")
    asset = Image.new("RGBA", (180, 123))
    asset.paste(source.crop((1000, 1570, 1052, 1620)), (64, 35))
    assets = directory / "backgrounds" / "baidu_cherry"
    assets.mkdir(parents=True, exist_ok=True)
    asset.save(assets / "candidate-expand.png")


def import_candidate_footer(reference, directory):
    source = Image.open(reference).convert("RGB")
    if source.size != (1116, 825):
        raise ValueError("Expected the expanded CHERRY keyboard crop at 1116 x 825")
    footer = source.crop((0, 666, 1116, 825))
    assets = directory / "backgrounds" / "baidu_cherry"
    assets.mkdir(parents=True, exist_ok=True)
    sidebar = source.crop((0, 0, 195, 666))
    for top, bottom in ((60, 100), (173, 219), (292, 338)):
        for y in range(top, bottom):
            sidebar.paste(sidebar.getpixel((84, y)), (88, y, 120, y + 1))
    sidebar.save(assets / "candidate-sidebar.png")
    for up in (False, True):
        for down in (False, True):
            variant = footer.copy()
            for left, right, factor in ((172, 230, 3.4 if up else 1), (516, 576, 1 if down else 0.29)):
                if factor == 1:
                    continue
                for y in range(42, 79):
                    base = footer.getpixel((left - 3, y))[0]
                    for x in range(left, right):
                        value = footer.getpixel((x, y))[0]
                        value = min(179, round(base + max(0, value - base) * factor))
                        variant.putpixel((x, y), (value, value, value))
            variant.save(assets / f"candidate-footer-{int(up)}{int(down)}.png")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Import the English word trie from a local Baidu APK.")
    parser.add_argument("apk", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--candidate-reference", type=Path, help="CHERRY portrait capture with a visible candidate strip")
    parser.add_argument("--expanded-reference", type=Path, help="1116 x 825 crop of the expanded CHERRY keyboard on its first page")
    args = parser.parse_args()
    entries, offsets, records, words = write_profile(args.apk, args.profile)
    if args.candidate_reference:
        import_candidate_arrow(args.candidate_reference, args.profile)
    if args.expanded_reference:
        import_candidate_footer(args.expanded_reference, args.profile)
    print(f"{entries} entries, {offsets} offsets, {records} record bytes, {words} English candidate mappings")
