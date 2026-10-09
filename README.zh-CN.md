<div align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="128" alt="Niva Launcher 图标">

  <h1>Niva Launcher</h1>

  <p><a href="README.md">English</a> | 简体中文</p>

  <p><strong>安静、私密的列表式 Android 桌面。</strong></p>
</div>

Niva Launcher 是 [Grace Launcher](https://github.com/Galaxy-rio/GraceLauncher)（作者 Galaxy-rio）的一个分支，遵循 **GPL-3.0** 许可证。
它保留了 Grace 的引擎（Kotlin、Jetpack Compose、Room），并加入了 Niva 品牌、全新图标与专注模式。
Niva 的交互灵感来自 Niagara Launcher 的公开设计；不包含 Niagara 的任何代码或素材。

## 功能

继承自 Grace（存在于源码中）：
常用应用主屏幕、带字母索引的应用列表、搜索（应用、可选的联系人）、文件夹、隐藏应用、图标包、
逐应用图标设计器、自定义字体、自定义时钟、浅色/深色/动态取色/纯黑主题、壁纸变暗与模糊、
可配置手势与按钮动作、应用快捷方式与通知预览弹窗、日历日程、媒体控制、主屏幕小组件
（AppWidgetHost：添加、移动、配置、调整大小、删除）、工作资料与私人空间支持、可选的 Breezy Weather 天气集成。

Niva 新增：
- **专注模式**（设置 → 生产力）：选择容易分心的应用并开启专注模式；在关闭之前，这些应用会从常用、应用列表、文件夹和搜索中消失。
  这只是桌面端的过滤，不会卸载、停用或屏蔽任何应用，选择保存在本地偏好设置中。
- **设置备份与恢复**（设置 → 高级 → 备份与恢复）：将设置、常用、文件夹、隐藏应用和专注模式导出为 JSON 文件，或从备份文件恢复（会校验文件，损坏的备份不会覆盖当前设置）。
- **外观预设**（设置 → 主题 → 预设）：将当前主题、颜色、图标、时钟、字体和壁纸效果保存为命名预设，随时一键切换；只改变外观，不动应用、常用和文件夹。
- **隐私**（设置 → 隐私）：集中说明 Niva 申请的权限、可选集成需要什么、哪些数据只保存在本机，以及各功能的开关。
- **搜索动作**：在搜索中直接执行常用操作，如打开 Niva 设置、设为默认桌面、开关专注模式、更换壁纸、添加小组件。
- Niva 名称、原创 "N" 图标（同时用作 Android 13+ 主题图标），以及全部内置语言中更新后的用户可见文案。
- 默认关闭 `allowBackup`，桌面数据不会被复制到云备份。

## 构建

需要 OpenJDK 21、Android SDK Platform 36 和 Build Tools 36.1.0（见 `app/build.gradle.kts`），以及可访问 Google/Maven Central 的网络（用于下载依赖）。

```shell
./gradlew :app:assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest  # JVM 单元测试
./gradlew :app:connectedDebugAndroidTest  # 插桩测试（需要设备或模拟器）
```

发布版：创建 keystore，在构建脚本中添加 `signingConfigs`（密钥不要提交到 git），然后运行 `./gradlew :app:assembleRelease`
（已启用 R8 优化）。分发前请阅读 [NOTICE.md](NOTICE.md)。

最低 Android 版本：9（API 28）。应用 ID：`com.niva.launcher`（为减小分支差异、保证安全，Kotlin 命名空间仍为 `com.galaxyrio.gracelauncher`）。

## 隐私与权限

无 `INTERNET` 权限，无广告，无统计。每个权限、导出组件及其用途见 [docs/PERMISSIONS.md](docs/PERMISSIONS.md)。

## 许可证

GPL-3.0-or-later；见 [LICENSE](LICENSE) 和 [NOTICE.md](NOTICE.md)。上游 Grace 的原始 README 保留在 `docs/UPSTREAM_README_GRACE.md`。
