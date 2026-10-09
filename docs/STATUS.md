# Verification status and known limitations

## Build & test status (2026-10-10) — CI verified ✅

| Item | Result |
|---|---|
| Gradle build / APK | **PASSED** via GitHub Actions ([Android CI #23](https://github.com/shauryasingh9967-ops/niva-launcher/actions/runs/37975253680)). Debug APK built successfully (16.1 MB). |
| Unit tests | **PASSED** — all 59 tests pass, including 9 new `SettingsBackupTest` tests. |
| Lint | **PASSED** — 0 errors. |
| Instrumented tests (inherited, ~46 files) | **Not run** (require device/emulator). |
| Device/emulator testing | **Not performed** — no device or emulator available. |

## What was implemented (2026-10-09)

- **Focus Mode folder leak fixed**: `popupItems()` filters focus-hidden apps; folder editor bypasses filter for management.
- **Backup & restore**: Settings → Advanced → Backup & restore. Versioned JSON export/import via Storage Access Framework. Validates format/version/size; atomic import (settings preserved on failure).
- **Appearance presets**: Settings → Themes → Presets. Save/apply/delete named appearance snapshots (theme, colors, icons, clock, fonts, wallpaper effects).
- **Privacy screen**: Settings → Privacy. Documents permissions, local-only storage, optional integration toggles, platform limits.
- **Launcher-action search**: Search now includes actions (Niva settings, set default launcher, Focus Mode toggle, wallpaper picker, add widget) matched by keywords.
- **Placeholders removed**: "Files / Coming soon" row and icon-designer "effects / coming soon" row deleted.
- **Namespace rebrand**: Application ID and Kotlin namespace are now `com.niva.launcher` (was Grace Launcher's). Upstream GPL-3.0 attribution preserved in LICENSE, NOTICE.md, and docs/UPSTREAM_README_GRACE.md.

## Known risks/limitations

- **No device testing**: The app compiles and passes unit tests, but has never been installed on a device or emulator. UI behavior, performance, and device-specific issues are unverified.
- Breezy Weather may only accept the original Grace package for its provider/notifier; with the new application id weather sharing may not work until Breezy lists `com.niva.launcher`.
- New application id means a fresh install; no migration from Grace data.
- `compileSdk`/`targetSdk` bumped to 37; `minSdk` remains 28 (Android 9+).
