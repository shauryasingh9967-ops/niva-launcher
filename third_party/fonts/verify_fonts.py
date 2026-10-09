"""Verify vendored font hashes, variable axes and representative script coverage.

Uses only the Python standard library; it never downloads or modifies fonts.
Run from any working directory: python third_party/fonts/verify_fonts.py
"""

from hashlib import sha256
import json
from pathlib import Path
import struct


ROOT = Path(__file__).resolve().parents[2]
SOURCES = Path(__file__).with_name("sources.json")
SAMPLES = {
    "josefinsans": "Grace Launcher Josefin Sans 0123456789 é à ö ắ",
    "notosans": "Ελληνικά Русский Українська",
    "notosansarabic": "العربية فارسی اردو",
    "notosanshebrew": "עברית שָׁלוֹם",
    "notosansdevanagari": "हिन्दी संस्कृतम्",
    "notosansthai": "ภาษาไทย",
}


def uint16(data, offset):
    return struct.unpack_from(">H", data, offset)[0]


def tables(data):
    count = uint16(data, 4)
    result = {}
    for index in range(count):
        tag, _checksum, offset, length = struct.unpack_from(">4sIII", data, 12 + 16 * index)
        result[tag.decode("ascii")] = data[offset:offset + length]
    return result


def variable_axes(table):
    _major, _minor, start, _reserved, count, size, _instances, _instance_size = struct.unpack_from(">8H", table)
    result = {}
    for index in range(count):
        tag, minimum, default, maximum, _flags, _name = struct.unpack_from(">4slllHH", table, start + size * index)
        result[tag.decode("ascii")] = [value / 65536 for value in (minimum, default, maximum)]
    return result


def cmap_codepoints(table):
    result = set()
    for index in range(uint16(table, 2)):
        platform, encoding, offset = struct.unpack_from(">HHI", table, 4 + index * 8)
        if platform != 0 and not (platform == 3 and encoding in (1, 10)):
            continue
        fmt = uint16(table, offset)
        if fmt == 12:
            groups = struct.unpack_from(">I", table, offset + 12)[0]
            for group in range(groups):
                start, end, first_glyph = struct.unpack_from(">III", table, offset + 16 + group * 12)
                result.update(range(start + (first_glyph == 0), end + 1))
        elif fmt == 4:
            segments = uint16(table, offset + 6) // 2
            ends = offset + 14
            starts = ends + segments * 2 + 2
            deltas = starts + segments * 2
            ranges = deltas + segments * 2
            for segment in range(segments):
                start, end = uint16(table, starts + segment * 2), uint16(table, ends + segment * 2)
                delta = struct.unpack_from(">h", table, deltas + segment * 2)[0]
                range_offset = uint16(table, ranges + segment * 2)
                for point in range(start, min(end, 0xFFFE) + 1):
                    if range_offset:
                        glyph = uint16(table, ranges + segment * 2 + range_offset + (point - start) * 2)
                        if glyph:
                            glyph = (glyph + delta) & 0xFFFF
                    else:
                        glyph = (point + delta) & 0xFFFF
                    if glyph:
                        result.add(point)
    return result


def gsub_metadata(table):
    scripts_offset, features_offset = struct.unpack_from(">HH", table, 4)
    features = []
    for index in range(uint16(table, features_offset)):
        features.append(table[features_offset + 2 + index * 6:features_offset + 6 + index * 6].decode("ascii"))
    languages = set()
    for index in range(uint16(table, scripts_offset)):
        script = scripts_offset + uint16(table, scripts_offset + 6 + index * 6)
        for language in range(uint16(table, script + 2)):
            languages.add(table[script + 4 + language * 6:script + 8 + language * 6].decode("ascii").strip())
    return {"features": sorted(set(features)), "languages": sorted(languages)}


def main():
    records = json.loads(SOURCES.read_text(encoding="utf-8"))["fonts"]
    assets = {path.relative_to(ROOT).as_posix() for path in (ROOT / "app/src/main/assets/fonts").glob("*.ttf")}
    assert assets == {record["file"] for record in records}, "Font assets must match the source manifest"
    report = []
    for record in records:
        data = (ROOT / record["file"]).read_bytes()
        assert len(data) == record["bytes"], f"File length mismatch: {record['file']}"
        assert sha256(data).hexdigest() == record["sha256"], f"Checksum mismatch: {record['file']}"
        font_tables = tables(data)
        axes = variable_axes(font_tables["fvar"])
        codepoints = cmap_codepoints(font_tables["cmap"])
        missing = sorted(set(SAMPLES[record["family"]]) - {chr(point) for point in codepoints})
        assert not missing, f"Missing sample characters in {record['file']}: {missing}"
        gsub = gsub_metadata(font_tables["GSUB"])
        report.append({
            "file": record["file"], "bytes": len(data), "axes_min_default_max": axes,
            "unicode_codepoints": len(codepoints), "sample_coverage": "passed", "gsub": gsub,
        })
    print(json.dumps({"verified_fonts": len(report), "total_bytes": sum(row["bytes"] for row in report), "fonts": report}, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
