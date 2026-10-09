param([switch]$FunctionsOnly)
$ErrorActionPreference = 'Stop'
$setupRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$cacheRoot = Join-Path $setupRoot '.cache'
$appPackage = 'com.commontongue.spike.device'
$script:adbPath = $null
$script:deviceRoute = $null

function Assert-ContainedPath([string]$path) {
    $resolved = [IO.Path]::GetFullPath($path)
    if (-not $resolved.StartsWith($setupRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The setup package contains an invalid file path. Download a fresh copy.'
    }
    return $resolved
}

function Assert-File([string]$path, $asset) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return $false }
    if ((Get-Item -LiteralPath $path).Length -ne [long]$asset.bytes) { return $false }
    return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -eq $asset.sha256
}

function Get-PinnedFile($asset) {
    $destination = Assert-ContainedPath (Join-Path $cacheRoot $asset.cache_name)
    if (Assert-File $destination $asset) { return $destination }
    New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
    if ($asset.bundled_file) {
        $bundled = Assert-ContainedPath (Join-Path $setupRoot $asset.bundled_file)
        if (Assert-File $bundled $asset) { Copy-Item -LiteralPath $bundled -Destination $destination -Force; return $destination }
    }
    $partial = $destination + '.partial'
    if ($asset.url -notmatch '^https://(?:huggingface\.co|github\.com|dl\.google\.com)/') {
        throw 'The setup package contains an untrusted download address.'
    }
    Write-Host ('Preparing ' + $asset.display_name + '...')
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $client = New-Object Net.WebClient
    try {
        $client.DownloadFile($asset.url, $partial)
        if (-not (Assert-File $partial $asset)) { throw 'The downloaded file did not match the pinned research asset.' }
        Move-Item -LiteralPath $partial -Destination $destination -Force
    } catch {
        if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force }
        throw 'Model/tool download failed - try again. Keep the setup folder so verified downloads can be reused.'
    } finally { $client.Dispose() }
    return $destination
}

function Quote-Argument([string]$value) {
    # Windows argv quoting. No Invoke-Expression, shell interpolation, or logs
    # containing the ephemeral ADB device route.
    return '"' + [regex]::Replace([regex]::Replace($value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}

function Invoke-Phone([string[]]$arguments, [int]$timeoutSeconds = 120, [switch]$Global) {
    $all = if ($Global) { $arguments } else { @('-s', $script:deviceRoute) + $arguments }
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $script:adbPath
    $start.Arguments = ($all | ForEach-Object { Quote-Argument $_ }) -join ' '
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $start
    try {
        $null = $process.Start()
        $outTask = $process.StandardOutput.ReadToEndAsync()
        $errTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($timeoutSeconds * 1000)) { $process.Kill(); throw 'The phone did not respond. Reconnect the USB cable and try again.' }
        $out = $outTask.GetAwaiter().GetResult()
        $diagnostic = $errTask.GetAwaiter().GetResult() # Raw device identifiers are never displayed or saved.
        if ($process.ExitCode -ne 0) {
            if (($out + $diagnostic) -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') { throw 'A different research app version is already installed. Ask the test coordinator to update it; your existing research data has been preserved.' }
            if (($out + $diagnostic) -match 'INSTALL_FAILED_INSUFFICIENT_STORAGE') { throw 'Not enough free storage. Free space on the phone and try again.' }
            if (($out + $diagnostic) -match 'INSTALL_FAILED_VERSION_DOWNGRADE') { throw 'A newer app version is already installed. Ask the test coordinator for the matching research package.' }
            throw 'A phone setup step failed. Unlock the phone, check the USB cable, and try again.'
        }
        return $out.Trim()
    } finally { $process.Dispose() }
}

function Find-Phone {
    while ($true) {
        $text = Invoke-Phone -arguments @('devices') -Global
        $devices = @($text -split "`r?`n" | Where-Object { $_ -match '^\S+\s+(device|unauthorized|offline)$' -and $_ -notmatch '^emulator-' })
        $authorized = @($devices | Where-Object { $_ -match '\sdevice$' })
        if ($authorized.Count -eq 1) { $script:deviceRoute = ($authorized[0] -split '\s+')[0]; return }
        if ($authorized.Count -gt 1) { Write-Host 'More than one phone found - disconnect the phones you are not testing.' }
        elseif ($devices -match '\sunauthorized$') { Write-Host 'Please approve USB debugging on your phone.' }
        elseif ($devices.Count) { Write-Host 'Unlock your phone and reconnect its USB cable.' }
        else {
            Write-Host 'No phone found - connect your phone with USB.'
            Write-Host 'On Samsung: Settings > About phone > Software information > tap Build number 7 times; then enable USB debugging in Developer options.'
            Write-Host 'Use a USB cable that supports data. If Windows still cannot see the phone, its manufacturer USB driver may be needed.'
        }
        $null = Read-Host 'After the phone is connected and authorized, press Enter (or close this window to stop)'
    }
}

function Copy-PrivateFile([string]$source, [string]$relative, $asset) {
    if ($relative -notmatch '^files/(?:models|audio|trial-plan\.json|provision\.json)(?:/[A-Za-z0-9_.-]+)*$' -or $relative.Contains('..')) { throw 'Invalid research file location.' }
    try {
        $existing = (Invoke-Phone -arguments @('shell', 'run-as', $appPackage, 'sha256sum', $relative)).Split(' ')[0]
        if ($existing -eq $asset.sha256) { return }
    } catch { }
    $staging = '/data/local/tmp/common-tongue-pass5-' + [Guid]::NewGuid().ToString('N')
    $partial = $relative + '.partial'
    $directory = $relative.Substring(0, $relative.LastIndexOf('/'))
    $null = Invoke-Phone -arguments @('shell', 'run-as', $appPackage, 'mkdir', '-p', $directory)
    try {
        $null = Invoke-Phone -arguments @('push', $source, $staging) -timeoutSeconds 900
        $null = Invoke-Phone -arguments @('shell', 'chmod', '644', $staging)
        $null = Invoke-Phone -arguments @('shell', 'run-as', $appPackage, 'cp', $staging, $partial) -timeoutSeconds 900
        $digest = (Invoke-Phone -arguments @('shell', 'run-as', $appPackage, 'sha256sum', $partial) -timeoutSeconds 900).Split(' ')[0]
        if ($digest -ne $asset.sha256) { throw 'A model transfer could not be verified. Reconnect the phone and try again.' }
        $null = Invoke-Phone -arguments @('shell', 'run-as', $appPackage, 'mv', $partial, $relative)
    } finally {
        try { $null = Invoke-Phone -arguments @('shell', 'rm', '-f', $staging) } catch { }
        try { $null = Invoke-Phone -arguments @('shell', 'run-as', $appPackage, 'rm', '-f', $partial) } catch { }
    }
}

function Install-Trial {
    Write-Host 'Common Tongue - authorized research device setup'
    Write-Host 'Keep this PC online during setup. The phone will run offline after setup.'
    Write-Host 'First setup downloads about 2 GB and can take several minutes. Keep this window open; verified files are reused next time.'
    $manifest = Get-Content -LiteralPath (Join-Path $setupRoot 'setup-manifest.json') -Raw | ConvertFrom-Json
    $apk = Assert-ContainedPath (Join-Path $setupRoot $manifest.apk.file)
    if (-not (Assert-File $apk $manifest.apk)) { throw 'The research APK is missing or damaged. Extract the complete setup ZIP and try again.' }
    $platform = Get-PinnedFile $manifest.platform_tools
    $toolRoot = Assert-ContainedPath (Join-Path $cacheRoot 'tools')
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $toolsVerified = $true
    foreach ($tool in $manifest.adb_files) {
        if (-not (Assert-File (Join-Path $toolRoot ('platform-tools/' + $tool.file)) $tool)) { $toolsVerified = $false }
    }
    if (-not $toolsVerified) {
        New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null
        $zip = [IO.Compression.ZipFile]::OpenRead($platform)
        try {
            foreach ($entry in $zip.Entries) {
                $target = Assert-ContainedPath (Join-Path $toolRoot $entry.FullName)
                if ($entry.FullName.EndsWith('/')) { New-Item -ItemType Directory -Path $target -Force | Out-Null }
                else { New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null; [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true) }
            }
        } finally { $zip.Dispose() }
    }
    foreach ($tool in $manifest.adb_files) {
        if (-not (Assert-File (Join-Path $toolRoot ('platform-tools/' + $tool.file)) $tool)) { throw 'Phone connection tools failed verification. Extract a fresh setup package.' }
    }
    $script:adbPath = Join-Path $toolRoot 'platform-tools/adb.exe'
    Find-Phone
    if ((Invoke-Phone -arguments @('shell', 'getprop', 'ro.kernel.qemu')) -eq '1') { throw 'Please connect a physical phone over USB; an emulator cannot satisfy this trial.' }
    $manufacturer = Invoke-Phone -arguments @('shell', 'getprop', 'ro.product.manufacturer')
    $model = Invoke-Phone -arguments @('shell', 'getprop', 'ro.product.model')
    Write-Host ('Phone found: ' + $manufacturer + ' ' + $model)
    $abi = Invoke-Phone -arguments @('shell', 'getprop', 'ro.product.cpu.abilist')
    $api = Invoke-Phone -arguments @('shell', 'getprop', 'ro.build.version.sdk')
    if ($abi -notmatch '(?:^|,)arm64-v8a(?:,|$)' -or [int]$api -lt 26) { throw 'This research build needs a 64-bit Android phone running Android 8 or newer. This device is incompatible.' }
    $disk = (Invoke-Phone -arguments @('shell', 'df', '-k', '/data')) -split "`r?`n"
    $columns = $disk[-1].Trim() -split '\s+'
    $free = [long]$columns[$columns.Length - 3] * 1024
    $total = [long](($manifest.models | Measure-Object -Property bytes -Sum).Sum)
    $largest = [long](($manifest.models | Measure-Object -Property bytes -Maximum).Maximum)
    if ($free -lt $total + $largest + 512MB) { throw 'Not enough free storage. Free at least 4.5 GB on the phone, then try again.' }
    $memory = Invoke-Phone -arguments @('shell', 'cat', '/proc/meminfo')
    $available = if ($memory -match 'MemAvailable:\s+([0-9]+)') { [long]$Matches[1] * 1024 } else { 0 }
    if ($available -lt 3584MB) {
        Write-Host 'This phone has limited available memory. Setup can finish, but MADLAD may not load.'
        Write-Host 'Allow one controlled model-load attempt only. If it fails, stop and report the failure; use the OPUS research control when directed.'
    }
    $assets = @()
    foreach ($asset in $manifest.models) { $assets += @{ definition = $asset; file = Get-PinnedFile $asset } }
    $tts = Get-PinnedFile $manifest.tts
    Write-Host 'Installing the research translator and local research voice...'
    $null = Invoke-Phone -arguments @('install', '-r', $apk)
    # A conflicting pre-existing eSpeak signature is reported; never uninstall it
    # or remove unrelated user settings/data to force an installation.
    $null = Invoke-Phone -arguments @('install', '-r', $tts)
    Write-Host 'Transferring verified models. Keep the phone connected and unlocked...'
    $records = @()
    foreach ($entry in $assets) {
        Copy-PrivateFile $entry.file $entry.definition.destination $entry.definition
        $records += @{ file = $entry.definition.destination; bytes = [long]$entry.definition.bytes; sha256 = $entry.definition.sha256; verified_on_device = $true }
    }
    foreach ($fixture in $manifest.fixtures) {
        $path = Assert-ContainedPath (Join-Path $setupRoot $fixture.file)
        if (-not (Assert-File $path $fixture)) { throw 'A research test file is damaged. Download a fresh setup package.' }
        Copy-PrivateFile $path $fixture.destination $fixture
    }
    $receipt = @{ schema_version = 1; verified_on_device = $true; translator = 'madlad'; asr = 'base'; files = $records; active_asset_bytes = $total }
    $receiptFile = Assert-ContainedPath (Join-Path $cacheRoot 'provision.json')
    [IO.File]::WriteAllText($receiptFile, ($receipt | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding $false))
    $receiptAsset = @{ bytes = (Get-Item -LiteralPath $receiptFile).Length; sha256 = (Get-FileHash -LiteralPath $receiptFile -Algorithm SHA256).Hash.ToLowerInvariant() }
    Copy-PrivateFile $receiptFile 'files/provision.json' $receiptAsset
    $null = Invoke-Phone -arguments @('shell', 'am', 'start', '-n', ($appPackage + '/.TrialActivity'))
    Write-Host ''
    Write-Host 'Installation complete.'
    Write-Host 'Tap Start Testing, allow the microphone, and wait for Ready to Speak.'
    Write-Host 'Radios may stay on. Airplane Mode is only for the offline-proof test.'
    Write-Host 'Open Common Tongue Device Trial, tap Load selected models, then hold either language button to speak.'
    Write-Host 'Allow microphone access when the phone asks. Release the button to hear the translation.'
    Write-Host 'The OPUS option is a research comparison, not a finished low-resource quality tier.'
}

if (-not $FunctionsOnly) {
    try { Install-Trial }
    catch { Write-Host ''; Write-Host ('Setup stopped: ' + $_.Exception.Message); exit 1 }
}
