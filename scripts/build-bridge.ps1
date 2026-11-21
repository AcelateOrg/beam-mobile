# Builds beam-bridge/mobile into app/libs/beam-bridge.aar with gomobile.
# Needs: Go, gomobile (go install golang.org/x/mobile/cmd/gomobile@latest; gomobile init),
# and an Android NDK under the SDK (ANDROID_NDK_HOME or $SDK/ndk/<version>).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not $env:ANDROID_NDK_HOME) {
    $ndk = Get-ChildItem (Join-Path $env:ANDROID_HOME "ndk") -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name | Select-Object -Last 1
    if (-not $ndk) { throw "No Android NDK found under $env:ANDROID_HOME\ndk" }
    $env:ANDROID_NDK_HOME = $ndk.FullName
}
$libs = New-Item -ItemType Directory -Force (Join-Path $root "app\libs")
# gomobile turns the relative replace paths in go.mod into absolute ones and Go writes them into the
# library's build info, so the folder name would ship inside every APK. Build from a temporary drive
# letter mapped onto the folder that holds the repos: the binary then only sees "X:\beam-bridge".
$workspace = Split-Path -Parent $root
$drive = [char[]](90..68) | Where-Object { -not (Test-Path "${_}:\") } | Select-Object -First 1
if (-not $drive) { throw "No free drive letter for subst" }
subst "${drive}:" $workspace
if ($LASTEXITCODE -ne 0) { throw "subst failed" }
Push-Location "${drive}:\beam-bridge"
try {
    # arm64 covers current phones, arm old 32-bit ones, amd64 the emulator. The app ships one APK per ABI.
    gomobile bind "-target=android/arm64,android/arm,android/amd64" -androidapi 26 "-ldflags=-s -w" -trimpath -o (Join-Path $libs "beam-bridge.aar") ./mobile
    if ($LASTEXITCODE -ne 0) { throw "gomobile bind failed" }
} finally {
    Pop-Location
    subst "${drive}:" /D | Out-Null
}
