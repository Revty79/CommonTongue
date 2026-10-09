$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$ndkBin = Join-Path $sdkRoot 'ndk/27.1.12297006/toolchains/llvm/prebuilt/windows-x86_64/bin'
$env:CARGO_HOME = Join-Path $projectRoot '.local/quality/cargo'
$env:CARGO_TARGET_DIR = Join-Path $projectRoot '.local/local-ai/t5-build'
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = Join-Path $ndkBin 'aarch64-linux-android26-clang.cmd'
$env:CC_aarch64_linux_android = $env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER
$env:AR_aarch64_linux_android = Join-Path $ndkBin 'llvm-ar.exe'
$env:RUSTFLAGS = '-C link-arg=-Wl,-soname,libtrial_t5.so -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384'
$ErrorActionPreference = 'Continue' # Windows PowerShell treats redirected native stderr as errors.
& cargo +1.91.0 build --offline --locked --release --manifest-path "$PSScriptRoot/t5/Cargo.toml" --target aarch64-linux-android
$ErrorActionPreference = 'Stop'
if ($LASTEXITCODE) { throw 'Locked native runtime build failed.' }
$output = Join-Path $projectRoot '.local/local-ai/native/arm64-v8a'
New-Item -ItemType Directory -Path $output -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $env:CARGO_TARGET_DIR 'aarch64-linux-android/release/libtrial_t5.so') -Destination $output
