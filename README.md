<div align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="128" alt="Niva Launcher icon">

  <h1>Niva Launcher</h1>

  <p><strong>A calm, private, list-based Android home screen.</strong></p>
</div>

Niva Launcher is a fork of [Grace Launcher](https://github.com/Galaxy-rio/GraceLauncher) by Galaxy-rio, licensed under **GPL-3.0**.
It keeps Grace's engine (Kotlin, Jetpack Compose, Room) and adds Niva branding, a new icon and Focus Mode.
The name Niva is inspired by Nisha and Vinay. Niva takes interaction inspiration from Niagara Launcher's publicly visible design; it contains no Niagara code or assets.

> **Status: unbuilt in this delivery.** The environment this fork was prepared in had no Android SDK and no network access, so
> the project has **not been compiled, installed, or tested on a device or emulator**. See [docs/STATUS.md](docs/STATUS.md) before relying on it.

## Features

Inherited from Grace (present in the source, not re-verified by this fork):
favorites home screen, alphabetical app list with rail, search (apps, optional contacts), folders, hide apps, icon packs,
per-app Icon Designer, custom fonts, custom clock, light/dark/dynamic/AMOLED themes, wallpaper dim/blur, configurable gestures and
"button" actions, app shortcuts and notification previews in pop-ups, calendar agenda, media controls, home widget (AppWidgetHost: add, move,
configure, resize, remove), work profile and Private space support, optional Breezy Weather integration.

Added in Niva:
- **Focus Mode** (Settings → Productivity): pick distracting apps and toggle Focus Mode; they disappear from favorites, the app list and search
  until you turn it off. It is a launcher-side filter only. It does not uninstall, suspend or block anything, and the choice is stored locally in app preferences.
- Niva name, original "N" icon (also used as the Android 13+ themed icon), and updated user-visible strings in all bundled languages.
- `allowBackup` disabled so launcher data is not copied to cloud backup by default.

Not implemented (requested but out of what could be done and checked here): settings backup/restore file, customization presets,
a dedicated privacy-controls screen, usage-statistics based suggestions, launcher-action search. See [docs/STATUS.md](docs/STATUS.md).

## Build

Requires OpenJDK 21, Android SDK Platform 37 and Build Tools 36.1.0 (as configured in `app/build.gradle.kts`) and network access to Google/Maven Central for dependencies.

```shell
./gradlew :app:assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest  # JVM unit tests
./gradlew :app:connectedDebugAndroidTest  # instrumented tests (device/emulator required)
```

Release: create a keystore, add a `signingConfigs` entry (keep secrets out of git), then `./gradlew :app:assembleRelease`
(R8 optimization is already enabled). Review [NOTICE.md](NOTICE.md) before distributing.

Minimum Android: 9 (API 28). Application id: `com.niva.launcher` (the Kotlin namespace remains `com.galaxyrio.gracelauncher` to keep the fork's diff small and safe).

## Privacy and permissions

No `INTERNET` permission, no ads, no analytics. See [docs/PERMISSIONS.md](docs/PERMISSIONS.md) for every permission, exported component and why it exists.

## License

GPL-3.0-or-later; see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). The original Grace README is kept in `docs/UPSTREAM_README_GRACE.md`.
