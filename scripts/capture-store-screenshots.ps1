[CmdletBinding()]
param(
    [string]$Device = 'emulator-5554',
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$Scenes = ''
)

$ErrorActionPreference = 'Stop'
$captureProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $SdkRoot) { $SdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $SdkRoot) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$captureAdb = Join-Path $SdkRoot 'platform-tools/adb.exe'
$captureOriginalSerial = $env:ANDROID_SERIAL
Push-Location $captureProjectRoot
try {
    $env:ANDROID_SERIAL = $Device
    # Requires English UI, installed Monocons/Gmail/Chrome and one local song with cover art.
    # Each scene changes the emulator's real wallpaper; Material You follows its colors.
    $captureArguments = @(':app:connectedDebugAndroidTest',
        '-Pandroid.testInstrumentationRunnerArguments.class=com.niva.launcher.StoreScreenshotTest',
        '-Pandroid.testInstrumentationRunnerArguments.storeScreenshots=true')
    if ($Scenes) { $captureArguments += "-Pandroid.testInstrumentationRunnerArguments.storeScreenshotScenes=$Scenes" }
    & "$captureProjectRoot/gradlew.bat" @captureArguments
    if ($LASTEXITCODE -ne 0) { throw 'Screenshot capture failed; existing store images were not changed.' }

    $captureDestination = Join-Path $captureProjectRoot 'fastlane/metadata/android/en-US/images/phoneScreenshots'
    New-Item -ItemType Directory -Path $captureDestination -Force | Out-Null
    $captureNames = @('01-home.png', '02-agenda.png', '03-music.png', '04-app-list-c.png',
        '05-gmail-shortcuts.png', '06-home-widget.png', '07-themes.png', '08-clock-style.png',
        '09-icon-packs.png', '10-icon-designer.png')
    if ($Scenes) { $captureNames = @($captureNames | Where-Object { $_.Substring(0, 2) -in $Scenes.Split(',') }) }
    foreach ($captureName in $captureNames) {
        & $captureAdb -s $Device pull "/sdcard/Download/niva-launcher-store-screenshots/$captureName" (Join-Path $captureDestination $captureName)
        if ($LASTEXITCODE -ne 0) { throw "Could not retrieve $captureName" }
    }
    Write-Output "Saved $($captureNames.Count) English screenshots to $captureDestination"
} finally {
    $env:ANDROID_SERIAL = $captureOriginalSerial
    Pop-Location
}
