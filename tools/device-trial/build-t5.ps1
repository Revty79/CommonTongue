param([switch]$Fetch)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$ndkBin = Join-Path $sdkRoot 'ndk/27.1.12297006/toolchains/llvm/prebuilt/windows-x86_64/bin'
$clang = Join-Path $ndkBin 'aarch64-linux-android26-clang.cmd'
if (-not (Test-Path -LiteralPath $clang)) { throw 'Install the pinned NDK 27.1.12297006 first.' }
$env:CARGO_HOME = Join-Path $projectRoot '.local/quality/cargo'
$env:CARGO_TARGET_DIR = Join-Path $projectRoot '.local/device/t5-build'
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = $clang
$env:CC_aarch64_linux_android = $clang
$env:AR_aarch64_linux_android = Join-Path $ndkBin 'llvm-ar.exe'
$env:RUSTFLAGS = '-C link-arg=-Wl,-soname,libtrial_t5.so -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384'
$manifest = Join-Path $PSScriptRoot 't5/Cargo.toml'
if ($Fetch) {
    & cargo +1.91.0 fetch --locked --manifest-path $manifest --target aarch64-linux-android
    if ($LASTEXITCODE) { throw 'Cargo preparation failed.' }
}
& cargo +1.91.0 build --offline --locked --release --manifest-path $manifest --target aarch64-linux-android
if ($LASTEXITCODE) { throw 'Candle Android build failed.' }
$output = Join-Path $projectRoot '.local/device/native/arm64-v8a'
New-Item -ItemType Directory -Path $output -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $env:CARGO_TARGET_DIR 'aarch64-linux-android/release/libtrial_t5.so') -Destination $output
Get-FileHash -LiteralPath (Join-Path $output 'libtrial_t5.so') -Algorithm SHA256
