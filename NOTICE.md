# Notice and attribution

Niva Launcher is a modified version of **Grace Launcher** (https://github.com/Galaxy-rio/GraceLauncher),
Copyright the Grace Launcher authors, distributed under the GNU General Public License v3.0 (see `LICENSE`).

Modifications made by the Niva fork (2026-10-09 baseline):
- Renamed product to Niva Launcher; changed `applicationId` to `com.niva.launcher`; version `1.2.0-niva`.
- Replaced launcher icon (original geometric "N") and its background colour; removed legacy Grace PNG launcher icons.
- Replaced "Grace" with "Niva" in user-visible strings of all bundled locales.
- Added Focus Mode (`data/FocusMode.kt`, preferences, ViewModel state, settings page, tests).
- Set `android:allowBackup="false"`.
- Added README, NOTICE and docs.

Inherited code, internal class/package names (`com.galaxyrio.gracelauncher`) and third-party assets
(fonts under OFL, Material Symbols under Apache-2.0, weather icons) keep their original notices under `third_party/` and `app/src/main/assets/`.
Anyone distributing builds must provide corresponding source under GPL-3.0 and keep these notices.
"Niagara Launcher" is a trademark of its owner; this project is not affiliated with it.
