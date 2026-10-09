# Verification status and known limitations

Environment used: Linux (Ubuntu 24.04), OpenJDK 21 (Temurin), Android SDK (cmdline-tools, platform android-36, build-tools 36.1.0) installed at `~/workspace/niva-launcher/android-sdk`. No emulator/device.

## Build & test status (2026-10-09)

| Item | Result |
|---|---|
| Gradle build / APK | **BLOCKED by environment.** Gradle 9.5.0 daemon cannot maintain a stable connection in this VM (only ~500MB RAM available; 6.9GB used by host processes outside our control). Multiple attempts with `--no-daemon`, IPv4 forcing, proxy config all failed at the daemon communication layer before dependency resolution. **No APK was produced.** |
| Unit tests (incl. new `SettingsBackupTest`, `FocusModeTest`) | **Not run** (require Gradle build). New `SettingsBackupTest` (9 tests) written and syntax-checked. |
| Instrumented tests (inherited, ~46 files) | **Not run** (require device/emulator). |
| Kotlin syntax of new/modified files | **Verified** via standalone kotlinc 2.3.21 parser: `SettingsBackup.kt`, `PresetStore.kt`, `BackupSettings.kt`, `PresetSettings.kt`, `PrivacySettings.kt`, `AppSearchScreen.kt`, `SettingsBackupTest.kt`, `LauncherViewModel.kt`, `LauncherActions.kt`, `LauncherSettingsScreen.kt`, `ThemeSettings.kt`, `LabSettings.kt` — no syntax errors. Type checking not performed (requires full Android/Compose classpath). |
| XML resources | `strings.xml` validated as well-formed XML. |
| Focus Mode, backup, presets, search actions on a device | **Unverified**; implemented by code inspection, never executed. |

## What was implemented (2026-10-09)

- **Focus Mode folder leak fixed**: `popupItems()` filters focus-hidden apps; folder editor bypasses filter for management.
- **Backup & restore**: Settings → Advanced → Backup & restore. Versioned JSON export/import via Storage Access Framework. Validates format/version/size; atomic import (settings preserved on failure).
- **Appearance presets**: Settings → Themes → Presets. Save/apply/delete named appearance snapshots (theme, colors, icons, clock, fonts, wallpaper effects).
- **Privacy screen**: Settings → Privacy. Documents permissions, local-only storage, optional integration toggles, platform limits.
- **Launcher-action search**: Search now includes actions (Niva settings, set default launcher, Focus Mode toggle, wallpaper picker, add widget) matched by keywords.
- **Placeholders removed**: "Files / Coming soon" row and icon-designer "effects / coming soon" row deleted.
- **Docs**: `README.zh-CN.md` rewritten for Niva; ~60 new English strings added.

## Known risks/limitations

- **Compile errors are possible** until `./gradlew :app:assembleDebug` succeeds on a machine with sufficient RAM (2GB+). The code was syntax-checked but not type-checked.
- Breezy Weather may only accept the original Grace package for its provider/notifier; with the new application id weather sharing may not work until Breezy lists `com.niva.launcher`.
- New application id means a fresh install; no migration from Grace data.
- Focus Mode uses SharedPreferences, is excluded from Android backup, and does not affect widgets, recents or other launchers.
- Backup does not include widget placements (Android assigns widget IDs; they must be re-added after restore).
- Usage-stats-based suggestions not implemented (would require `PACKAGE_USAGE_STATS` permission with special access flow).
- Most other requested capabilities exist only because Grace already provides them; this fork did not re-test them.
- Translations: only English has the new strings (other locales fall back to English).
