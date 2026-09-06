# A disposable hosted-Windows check, NOT an installer to run on a user's PC.
param([string]$Candidate = 'out/dhun-test.msi')
$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_OS -ne 'Windows' -or $env:RUNNER_ENVIRONMENT -ne 'github-hosted') {
    throw 'This install/uninstall smoke check is restricted to a disposable Windows Actions runner.'
}
. (Join-Path $PSScriptRoot 'msi_helpers.ps1')
$repository = Split-Path $PSScriptRoot -Parent
$logs = Join-Path $repository 'out/installer-check'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$baselineDir = Join-Path $env:RUNNER_TEMP 'dhun-msi-baseline'
New-Item -ItemType Directory -Force -Path $baselineDir | Out-Null
$baseline = Join-Path $baselineDir 'dhun-test.msi'
$candidatePath = (Resolve-Path -LiteralPath $Candidate).Path

function Invoke-MsiCheck {
    param([string]$Operation, [string]$Product, [string]$Log, [switch]$Cleanup, [string]$Properties = '')
    $arguments = "$Operation `"$Product`" /qn /norestart /L*V `"$Log`" $Properties"
    $process = Start-Process -FilePath (Join-Path $env:WINDIR 'System32/msiexec.exe') -ArgumentList $arguments -PassThru
    if (-not $process.WaitForExit(180000)) {
        $process.Kill()
        throw "MSI operation timed out; log: $Log"
    }
    $code = $process.ExitCode
    if ($code -eq 0 -or $code -eq 3010 -or ($Cleanup -and $code -eq 1605)) { return $code }
    # Preserve full logs, plus a short failure context in the check API when
    # Actions archive download is unavailable from the development sandbox.
    if (Test-Path -LiteralPath $Log) {
        $context = (Select-String -LiteralPath $Log -Pattern 'Return value 3' -Context 6,1 | Select-Object -First 1 | Out-String).Trim()
        if ($context) {
            $context = $context.Substring(0, [Math]::Min(900, $context.Length)).Replace('%', '%25').Replace("`r", '%0D').Replace("`n", '%0A')
            Write-Host "::error title=MSI failure context::$context"
        }
    }
    throw "msiexec $Operation failed with exit $code; log: $Log"
}

# Read-only download of the existing rolling baseline; never change a release.
gh release download test --repo $env:GITHUB_REPOSITORY --pattern dhun-test.msi --pattern dhun-test.msi.sha256 --dir $baselineDir
if ($LASTEXITCODE -ne 0) { throw 'Could not download the published MSI baseline/checksum' }
$expectedHash = ((Get-Content -LiteralPath (Join-Path $baselineDir 'dhun-test.msi.sha256') -Raw).Trim() -split '\s+')[0]
$baselineHash = (Get-FileHash -LiteralPath $baseline -Algorithm SHA256).Hash.ToLowerInvariant()
if ($baselineHash -ne $expectedHash.ToLowerInvariant()) { throw 'Published baseline MSI checksum did not match' }
$old = Get-DhunMsiProperties -Path $baseline
$new = Get-DhunMsiProperties -Path $candidatePath
if ($old['UpgradeCode'] -ne $new['UpgradeCode']) { throw 'Candidate and baseline do not share an upgrade identity' }
if ([version]$new['ProductVersion'] -le [version]$old['ProductVersion']) {
    throw "Candidate $($new['ProductVersion']) is not newer than baseline $($old['ProductVersion']); do not attempt a downgrade"
}
$installDirectory = Join-Path $env:LOCALAPPDATA 'DHUN'
$userdata = Join-Path $installDirectory 'userdata'
$sentinel = Join-Path $userdata 'upgrade-sentinel.txt'
$cacheSentinel = Join-Path $userdata 'cache/audio/upgrade-sentinel.txt'
try {
    [void](Invoke-MsiCheck '/i' $baseline (Join-Path $logs 'baseline-install.log'))
    if (-not (Test-Path -LiteralPath (Join-Path $installDirectory 'DHUN.exe'))) {
        throw "Baseline did not install to expected per-user directory $installDirectory"
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $cacheSentinel -Parent) | Out-Null
    [IO.File]::WriteAllText($sentinel, 'preserve-userdata')
    [IO.File]::WriteAllText($cacheSentinel, 'preserve-cache')

    [void](Invoke-MsiCheck '/i' $candidatePath (Join-Path $logs 'candidate-upgrade.log'))
    if (-not (Test-Path -LiteralPath (Join-Path $installDirectory 'DHUN.exe'))) { throw 'Candidate executable missing after upgrade' }
    if (-not (Test-Path -LiteralPath $sentinel) -or [IO.File]::ReadAllText($sentinel) -ne 'preserve-userdata') {
        throw 'In-place MSI upgrade removed/changed existing userdata'
    }
    if (-not (Test-Path -LiteralPath $cacheSentinel) -or [IO.File]::ReadAllText($cacheSentinel) -ne 'preserve-cache') {
        throw 'In-place MSI upgrade removed/changed existing cache data'
    }
    Write-Host "::notice title=MSI upgrade smoke PASS::Hosted Windows: $($old['ProductVersion']) -> $($new['ProductVersion']); per-user install and userdata/cache sentinels preserved. Baseline SHA256=$baselineHash. App playback/visuals not tested."

    # Exercise the installed candidate's future-upgrade removal path too.
    # This is the same public flag RemoveExistingProducts gives the old MSI;
    # it is not a simulation of sound/GUI or a separate published package.
    [void](Invoke-MsiCheck '/x' $new['ProductCode'] (Join-Path $logs 'future-upgrade-remove.log') -Properties 'UPGRADINGPRODUCTCODE={757885D5-5101-4F7E-A611-487E5F68B8B1}')
    if (-not (Test-Path -LiteralPath $sentinel) -or [IO.File]::ReadAllText($sentinel) -ne 'preserve-userdata') {
        throw 'Candidate upgrade-removal path removed userdata'
    }
    if (-not (Test-Path -LiteralPath $cacheSentinel) -or [IO.File]::ReadAllText($cacheSentinel) -ne 'preserve-cache') {
        throw 'Candidate upgrade-removal path removed cache data'
    }
    Write-Host '::notice title=MSI future-upgrade guard PASS::Upgrade-removal flag preserved both sentinels; ordinary uninstall is checked separately.'
    [void](Invoke-MsiCheck '/i' $candidatePath (Join-Path $logs 'candidate-reinstall.log'))
    [void](Invoke-MsiCheck '/x' $new['ProductCode'] (Join-Path $logs 'candidate-uninstall.log'))
    if (Test-Path -LiteralPath $userdata) { throw 'MSI uninstall left the test userdata directory behind' }
    Write-Host '::notice title=MSI uninstall smoke PASS::Hosted Windows uninstall removed test userdata. No app launch, audio or visual acceptance claimed.'
    @{
        sourceSha = $env:GITHUB_SHA
        baselineVersion = $old['ProductVersion']
        baselineSha256 = $baselineHash
        candidateVersion = $new['ProductVersion']
        perUserInstall = 'passed'
        installOver = 'passed'
        userdataSentinel = 'preserved'
        cacheSentinel = 'preserved'
        futureUpgradeRemoval = 'preserved both sentinels'
        uninstallCleanup = 'passed'
        appLaunch = 'not tested'
        audioAndVisuals = 'not tested'
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logs 'result.json') -Encoding utf8
} finally {
    # Product GUIDs were read from these two verified DHUN packages. Do not
    # query Win32_Product (which can trigger repairs of unrelated software).
    $productCodes = @($new['ProductCode'], $old['ProductCode']) | Select-Object -Unique
    foreach ($code in $productCodes) {
        try { [void](Invoke-MsiCheck '/x' $code (Join-Path $logs ("cleanup-" + $code.Trim('{}') + '.log')) -Cleanup) }
        catch { Write-Warning "Disposable-runner cleanup: $_" }
    }
}
