# Local model/device-free Windows smoke checks for the installer's argv boundary.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'setup/setup.ps1') -FunctionsOnly
$script:adbPath = (Get-Command python.exe).Source
$received = Invoke-Phone -Global -arguments @('-c', 'import sys,json; print(json.dumps(sys.argv[1:]))', 'path with spaces', 'literal"quote', 'trailing\')
$values = $received | ConvertFrom-Json
if ($values.Count -ne 3 -or $values[0] -ne 'path with spaces' -or $values[1] -ne 'literal"quote' -or $values[2] -ne 'trailing\') { throw 'Windows argument transport failed.' }
$rejected = $false
try { Assert-ContainedPath (Join-Path $PSScriptRoot '../../../../outside-setup') | Out-Null } catch { $rejected = $true }
if (-not $rejected) { throw 'Setup path escape was accepted.' }
Write-Host 'Windows setup argument/path smoke checks passed; no phone, model or network used.'
