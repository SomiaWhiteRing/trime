import argparse
import copy
import re
import struct
import zipfile
from pathlib import Path

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
    count = 0
    with (directory / "cherry_english.dict.yaml").open("w", encoding="utf-8", newline="\n") as stream:
        stream.write("---\nname: cherry_english\nversion: '1'\nsort: by_weight\nuse_preset_vocabulary: false\n...\n")
        for word in trie:
            if not re.fullmatch(r"[A-Za-z]+(?:'[A-Za-z]+)*", word):
                continue
            key = word.lower().replace("'", "")
            if key:
                stream.write(f"{word}\t{key}\t1\n")
                count += 1

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
  enable_completion: true
  enable_sentence: false
  enable_user_dict: true
punctuator:
  import_preset: default
key_binder:
  import_preset: default
""", encoding="utf-8")
    theme_path = directory / "baidu_cherry.trime.yaml"
    if theme_path.is_file():
        configure_keyboard(theme_path)
    pinyin_patch = directory / "luna_pinyin.custom.yaml"
    if pinyin_patch.is_file():
        config = yaml.safe_load(pinyin_patch.read_text(encoding="utf-8"))
        dependencies = config["patch"].setdefault("schema/dependencies/+", [])
        if "cherry_english" not in dependencies:
            dependencies.append("cherry_english")
        pinyin_patch.write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding="utf-8")
    return (len(trie), len(offsets), len(records), count)


def configure_keyboard(theme_path):
    theme = yaml.safe_load(theme_path.read_text(encoding="utf-8"))
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
    theme_path.write_text(yaml.safe_dump(theme, allow_unicode=True, sort_keys=False), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Import the English word trie from a local Baidu APK.")
    parser.add_argument("apk", type=Path)
    parser.add_argument("profile", type=Path)
    args = parser.parse_args()
    entries, offsets, records, words = write_profile(args.apk, args.profile)
    print(f"{entries} entries, {offsets} offsets, {records} record bytes, {words} standalone English words")
