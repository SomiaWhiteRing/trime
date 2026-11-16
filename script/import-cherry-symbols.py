import argparse
import json
import struct
import zipfile
from pathlib import Path


def read_symbols(apk):
    with zipfile.ZipFile(apk) as source:
        data = source.read("assets/dict/ipt3sym_sys.bin")
    count, cursor = struct.unpack_from("<II", data, 0x20)
    groups = []
    for expected_index in range(count):
        units, index = struct.unpack_from("<HH", data, cursor)
        if index != expected_index:
            raise ValueError(f"Unexpected symbol group index at {cursor:#x}")
        cursor += 4
        label = data[cursor:cursor + units * 2].decode("utf-16-le")
        cursor += units * 2
        entries = []
        ordinal = 999
        while cursor + 4 <= len(data):
            units, index = struct.unpack_from("<HH", data, cursor)
            if index != ordinal:
                break
            cursor += 4
            end = cursor + units * 2
            if end > len(data):
                raise ValueError(f"Truncated symbol at {cursor:#x}")
            entries.append(data[cursor:end].decode("utf-16-le"))
            cursor = end
            ordinal -= 1
        groups.append(dict(label=label, entries=entries))
    if data[cursor:] != b"\x00\x00":
        raise ValueError("Unexpected symbol table trailer")
    return groups


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Read symbol groups from a local Baidu Input Method APK.")
    parser.add_argument("apk", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    groups = read_symbols(args.apk)
    args.output.write_text(json.dumps(groups, ensure_ascii=False), encoding="utf-8")
    print(f"{len(groups)} groups, {sum(len(group['entries']) for group in groups)} entries")
