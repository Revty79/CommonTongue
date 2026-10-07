param(
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$Serial = 'emulator-5554',
    [ValidateSet('tiny', 'base')][string]$Asr = 'tiny',
    [ValidateSet('piper', 'espeak')][string]$Fixture = 'piper'
)
$ErrorActionPreference = 'Stop'
$researchRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$adbPath = Join-Path $AndroidSdk 'platform-tools\adb.exe'
$appId = 'com.commontongue.spike.offline'
function Invoke-ResearchAdb {
    # ADB reports successful transfers on stderr; check its exit code instead.
    $ErrorActionPreference = 'Continue'
    & $adbPath -s $Serial @args 2>&1 | ForEach-Object { $_.ToString() }
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $args" }
}
Invoke-ResearchAdb install -r (Join-Path $researchRoot '.local\offline-tools\espeak-1.52.0-signed.apk')
Invoke-ResearchAdb shell am start -n 'com.reecedunn.espeak/.eSpeakActivity'
Invoke-ResearchAdb install -r (Join-Path $researchRoot 'spikes\offline-feasibility\build\outputs\apk\debug\offline-feasibility-spike-debug.apk')
Invoke-ResearchAdb shell svc wifi disable
Invoke-ResearchAdb shell svc data disable
$apiLevel = (Invoke-ResearchAdb shell getprop ro.build.version.sdk | Select-Object -Last 1).Trim()
if ([int]$apiLevel -ge 30) {
    Invoke-ResearchAdb shell cmd connectivity airplane-mode enable
} else {
    # Older Android has no connectivity shell command; both radios are already disabled.
    Invoke-ResearchAdb shell settings put global airplane_mode_on 1
}
& python (Join-Path $PSScriptRoot 'transfer-android.py') --adb $adbPath --serial $Serial --asr $Asr --fixture $Fixture
if ($LASTEXITCODE -ne 0) { throw 'Binary-safe private file transfer failed' }
Invoke-ResearchAdb shell am force-stop $appId
Invoke-ResearchAdb logcat -c
Invoke-ResearchAdb shell am start -n "$appId/.SpikeActivity" --es asr $Asr
Write-Output "Wait for PROOF_RESULT or PROOF_FAILED: adb -s $Serial logcat -s OfflineProof"
Write-Output "Collect evidence: python tools/offline-feasibility/transfer-android.py --adb $adbPath --serial $Serial --asr $Asr --collect .local/android-$Asr.json"
