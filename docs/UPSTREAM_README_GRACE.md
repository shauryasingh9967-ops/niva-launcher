<div align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="128" alt="Grace Launcher icon">

  <h1>Grace Launcher</h1>

  <p>English | <a href="README.zh-CN.md">简体中文</a></p>

  <p><strong>Your home screen, your design.</strong></p>
  <div>
    <a href="https://github.com/Galaxy-rio/GraceLauncher/stargazers"><img width="268" src="https://m3-markdown-badges.vercel.app/stars/7/2/Galaxy-rio/GraceLauncher" alt="GitHub stars"></a>
    <a href="https://github.com/Galaxy-rio/GraceLauncher/issues"><img width="266" src="https://m3-markdown-badges.vercel.app/issues/9/2/Galaxy-rio/GraceLauncher" alt="Open issues"></a>
  </div>
  <div>
    <a href="https://developer.android.com/about/versions/pie"><img height="30" src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Android/android2.svg" alt="Android 9 or later"></a>
    <a href="https://developer.android.com/studio"><img height="30" src="https://ziadoua.github.io/m3-Markdown-Badges/badges/AndroidStudio/androidstudio3.svg" alt="Android Studio"></a>
    <a href="https://kotlinlang.org/"><img height="30" src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Kotlin/kotlin2.svg" alt="Kotlin"></a>
    <a href="LICENSE"><img height="30" src="https://ziadoua.github.io/m3-Markdown-Badges/badges/LicenceGPLv3/licencegplv32.svg" alt="GPL-3.0 license"></a>
  </div>
  <p>
    <a href="https://github.com/Galaxy-rio/GraceLauncher/releases"><img width="153" align="middle" src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Github/github2.svg" alt="GitHub Releases"></a>
    <a href="https://f-droid.org/packages/com.galaxyrio.gracelauncher/"><img height="60" align="middle" src="https://f-droid.org/badge/get-it-on.svg" alt="Get it on F-Droid"></a>
  </p>
  <p>A free and open-source Android launcher with a simple app list, customizable icons and fonts, a clock widget you can design yourself, and no tracking.</p>
</div>

## Features

- **Find apps quickly** — keep favorites on your home screen, organize apps into folders, and browse an alphabetical app list.
- **Choose your icon packs and fonts** — combine installed icon packs, set their priority, and import custom fonts for the launcher and clock.
- **Icon Designer** — redesign every app icon individually using an icon pack or a custom image, with control over shape, colors, size, and position.
- **Clock Widget** — design your own home screen clock widget with custom layouts, fonts, weight, size, spacing, and shadows.
- **Explore more customization** — use Material You and dynamic colors, choose light or dark themes, and control background blur strength.
- **Customize app shortcuts and widgets** — edit app shortcut popups with your choice of apps and shortcuts, place widgets inside them, or add and resize a widget on the home screen.
- **Keep your day in view** — read notifications on the home screen and check an integrated calendar agenda with weather forecasts.
- **Control your music** — manage active media playback directly from the home screen.
- **Stay private** — no advertising, tracking, or network permission; launcher data stays on your device.

Built with Kotlin, Jetpack Compose, and Material You. The list-based layout takes inspiration from [Niagara Launcher](https://niagaralauncher.app/).

Weather data is provided through [Breezy Weather](https://github.com/breezy-weather/breezy-weather), which must be installed and configured separately.

## Screenshots

| Home | Agenda & weather | Music controls | App list | App shortcuts |
| --- | --- | --- | --- | --- |
| <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01-home.png" alt="Home screen with Monocons icons and a teal gradient wallpaper" width="160"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02-agenda.png" alt="Calendar events and the Shanghai weather forecast" width="160"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03-music.png" alt="Music playback controls with the song's album cover" width="160"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04-app-list-c.png" alt="Alphabetical app list with the letter C selected" width="160"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05-gmail-shortcuts.png" alt="Gmail popup with sample notifications and app shortcuts" width="160"> |

| Home widget | Themes | Clock style | Icon packs | Icon Designer |
| --- | --- | --- | --- | --- |
| <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/06-home-widget.png" alt="A Chrome search widget added to the home screen" width="160"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/07-themes.png" alt="Theme settings with wallpaper-based dynamic colors" width="160"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/08-clock-style.png" alt="Clock style editor with the Sacramento preset selected" width="160"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/09-icon-packs.png" alt="Icon pack settings with Monocons enabled" width="160"> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10-icon-designer.png" alt="Icon Designer editing the Gmail icon" width="160"> |

## Download

Official builds are available from [GitHub Releases](https://github.com/Galaxy-rio/GraceLauncher/releases) and [F-Droid](https://f-droid.org/packages/com.galaxyrio.gracelauncher/).

Grace Launcher requires Android 9 (API 28) or later.

## Build from source

Install OpenJDK 21, Android SDK Platform 37, and Build Tools 36.1.0, then build the debug APK with the included Gradle wrapper.

Linux or macOS:

```shell
./gradlew :app:assembleDebug
```

Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Privacy

Grace Launcher does not request Android's `INTERNET` permission and contains no advertising or tracking. All launcher data is stored locally on your device.

Calendar permission and notification access are optional. Calendar permission enables the agenda; notification access enables home screen notifications and controls for active media sessions. Weather is supplied by the separately installed Breezy Weather app.

## Contributing

Contributions are welcome. Feel free to open an issue to report a bug or suggest an improvement, or submit a pull request with your changes.

Translations are managed through [Weblate](https://hosted.weblate.org/engage/grace_launcher/). You can help translate Grace Launcher into your language there.

Keep all UI strings in one `strings.xml` per language: `app/src/main/res/values/strings.xml` for the source text and `values-<locale>/strings.xml` for translations. Use section comments instead of splitting strings into feature-specific resource files.

Code is grouped by responsibility: `data` contains models and storage, `platform` integrates Android services, and `ui` contains feature screens with reusable elements in `ui/components`. Keep related small helpers with their feature rather than adding extra layers.

Generated build outputs, temporary captures, local release packages and `.local` maintainer drafts are ignored by Git. Only shared code styles and inspections from `.idea` are versioned; device selections and other local IDE state stay on your machine.

<a href="https://hosted.weblate.org/engage/grace_launcher/">
  <img src="https://hosted.weblate.org/widget/grace_launcher/multi-auto.svg" alt="Translation status">
</a>

## Acknowledgements

Special thanks to [Niagara Launcher](https://niagaralauncher.app/) for the design inspiration.

Grace Launcher is an independent project and is not affiliated with or endorsed by Niagara Launcher.

## License

Grace Launcher is licensed under the [GNU General Public License v3.0](LICENSE).
