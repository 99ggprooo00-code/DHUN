param(
    [Parameter(Mandatory = $true)][string]$ExpectedVersion,
    [Parameter(Mandatory = $true)][ValidateSet('true', 'false')][string]$BuildOnly
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'msi_helpers.ps1')
$repository = Split-Path $PSScriptRoot -Parent
$tree = Join-Path $repository 'app-desktop/build/compose'
$files = @(Get-ChildItem -LiteralPath $tree -Recurse -Filter '*.msi' -File)
if ($files.Count -ne 1) {
    Get-ChildItem -LiteralPath $tree -Recurse | Select-Object -First 100 | ForEach-Object { Write-Host $_.FullName }
    throw "Expected one MSI under $tree; found $($files.Count)"
}
$msi = $files[0]
$properties = Get-DhunMsiProperties -Path $msi.FullName
$upgradeCode = $properties['UpgradeCode'].Trim('{}').ToLowerInvariant()
if ($properties['ProductVersion'] -ne $ExpectedVersion) {
    throw "MSI ProductVersion '$($properties['ProductVersion'])' does not match requested '$ExpectedVersion'"
}
if ($upgradeCode -ne '31ddb86b-9666-4071-b11c-45f16fa4682d') {
    throw "MSI upgrade identity changed: $upgradeCode"
}
if ($properties['ProductName'] -ne 'DHUN') { throw 'Unexpected MSI product name' }
$out = Join-Path $repository 'out'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$staged = Join-Path $out 'dhun-test.msi'
Copy-Item -LiteralPath $msi.FullName -Destination $staged
Copy-Item -LiteralPath (Join-Path $repository 'docs/verification/windows-candidate.md') -Destination (Join-Path $out 'READ-ME-FIRST.md')
python (Join-Path $PSScriptRoot 'stage_artifact.py') $staged --build-only $BuildOnly --installer-version $properties['ProductVersion'] --upgrade-code $upgradeCode
if ($LASTEXITCODE -ne 0) { throw 'Artifact provenance generation failed' }
Write-Host "::notice title=MSI database verified::ProductVersion=$($properties['ProductVersion']) UpgradeCode=$upgradeCode ProductCode=$($properties['ProductCode'])"
