param([string]$AndroidSdk = $env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
$researchRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$cmakePath = Join-Path $AndroidSdk 'cmake\3.22.1\bin\cmake.exe'
$whisperSource = Join-Path $researchRoot '.local\offline-sources\whisper'
$whisperBuild = Join-Path $researchRoot '.local\offline-build\whisper-desktop'
& $cmakePath -S $whisperSource -B $whisperBuild -G 'Visual Studio 17 2022' -A x64 '-DWHISPER_BUILD_TESTS=OFF' '-DGGML_NATIVE=OFF' '-DGGML_OPENMP=OFF' '-DWHISPER_CURL=OFF'
if ($LASTEXITCODE -ne 0) { throw 'Whisper configure failed' }
& $cmakePath --build $whisperBuild --config Release --target whisper-cli -j 4
if ($LASTEXITCODE -ne 0) { throw 'Whisper build failed' }
$espeakSource = Join-Path $researchRoot '.local\offline-sources\espeak'
$espeakBuild = Join-Path $researchRoot '.local\offline-build\espeak-desktop'
& $cmakePath -S $espeakSource -B $espeakBuild -G 'Visual Studio 17 2022' -A x64 '-DBUILD_SHARED_LIBS=OFF' '-DUSE_ASYNC=OFF' '-DUSE_LIBPCAUDIO=OFF' '-DUSE_LIBSONIC=OFF' '-DUSE_MBROLA=OFF' '-DUSE_SPEECHPLAYER=OFF'
if ($LASTEXITCODE -ne 0) { throw 'eSpeak configure failed' }
& $cmakePath --build $espeakBuild --config Release -j 4
if ($LASTEXITCODE -ne 0) { throw 'eSpeak build failed' }
