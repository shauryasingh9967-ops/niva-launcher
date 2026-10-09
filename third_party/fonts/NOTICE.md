# Bundled fonts

These six upstream, unmodified TrueType variable font binaries provide offline
launcher typography. They are packaged once each under `app/src/main/assets/fonts/`.
No font download or Google Fonts network call is required at application runtime.

Retrieved on **2026-09-26**. Exact source URLs, original filenames, byte sizes and
SHA-256 checksums are recorded in [sources.json](sources.json). Each font is
distributed under **SIL Open Font License 1.1**; the unmodified notices are in
[licenses](licenses/). The same notices and font provenance are included in the APK
as `assets/fonts/NOTICE.txt`.

## Inventory

| Family | Asset filename | Bytes | Purpose |
| --- | --- | ---: | --- |
| Josefin Sans | `josefin_sans.ttf` | 118,588 | Primary Latin / Latin Extended / Vietnamese typography |
| Noto Sans | `noto_sans.ttf` | 2,049,096 | Latin Extended, Greek, Cyrillic and other upstream-supported characters |
| Noto Sans Arabic | `noto_sans_arabic.ttf` | 844,676 | Arabic, Persian and Urdu script coverage |
| Noto Sans Hebrew | `noto_sans_hebrew.ttf` | 112,640 | Hebrew, including combining marks |
| Noto Sans Devanagari | `noto_sans_devanagari.ttf` | 641,944 | Devanagari, including Hindi / Sanskrit shaping |
| Noto Sans Thai | `noto_sans_thai.ttf` | 218,652 | Thai script coverage |

Total uncompressed binary payload: **3,985,596 bytes (3.801 MiB)**. The four
additional common-script families contribute **1,817,912 bytes (1.734 MiB)**.
The final APK size depends on its packaging/compression settings.

## System fonts for Chinese, Japanese and Korean

CJK text uses Android's `sans-serif` system fallback. The app does not bundle
a CJK font.
Text locale is forwarded to Android shaping so regional glyph selection follows
the operating system. CJK appearance and available weights therefore depend on
the device's installed fonts. No network access is required.

## Variation axes and integration notes

- Josefin Sans: `wght=100..700` (default **100**), no slant axis. Explicitly
  set the desired weight; requests above 700 must use its maximum supported
  weight rather than pretend that heavier masters exist.
- Noto Sans and the four common-script fonts: `wght=100..900`,
  `wdth=62.5..100` (default width 100).
- **Noto Sans Hebrew defaults to weight 100.** Always set
  `wght` explicitly for each requested text weight; the other Noto assets default
  to 400.
- Preserve the full glyph/GSUB/GPOS tables. Avoid text-derived subsetting: installed
  application labels, shortcut names and calendar titles can contain characters
  not present in the project's string resources.
- Basic Noto Sans is not sufficient for CJK or all world scripts; bundled
  common-script fonts and a final system fallback are intentional.
- Merely listing unrelated Compose fonts by weight does not establish per-glyph
  fallback. The application must build a proper Android fallback chain.

## Offline verification

Run:

```powershell
python third_party/fonts/verify_fonts.py
```

The verifier uses only Python's standard library. It checks that the font assets
match the manifest, verifies each file's length and SHA-256, parses variable axes
and checks representative Latin/Greek/Cyrillic/Arabic/Hebrew/Devanagari/Thai
coverage. It only reads local files; it never downloads, executes upstream code,
or modifies fonts. `LauncherFontFallbackTest` checks native Android glyph
selection, including CJK system fallback across locales and weights.
