# A disposable hosted-Windows check, NOT an installer to run on a user's PC.
param(
    [string]$Candidate = 'out/dhun-test.msi',
    # On main (the only path that can publish) a SKIPPED install-over is a
    # failure, not a pass. PR/branch builds may still skip, visibly.
    [switch]$RequireInstallOver
)
$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_OS -ne 'Windows' -or $env:RUNNER_ENVIRONMENT -ne 'github-hosted') {
    throw 'This install/uninstall smoke check is restricted to a disposable Windows Actions runner.'
}
. (Join-Path $PSScriptRoot 'msi_helpers.ps1')
$repository = Split-Path $PSScriptRoot -Parent
$logs = Join-Path $repository 'out/installer-check'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
# Result evidence is flat key=value text (no JSON), written in every outcome.
function Write-DhunInstallResult {
    param([System.Collections.IDictionary]$Fields)
    $lines = foreach ($key in $Fields.Keys) { "$key=$($Fields[$key])" }
    Set-Content -LiteralPath (Join-Path $logs 'result.txt') -Value $lines -Encoding ascii
    if ($env:GITHUB_STEP_SUMMARY) {
        $summary = @('### MSI install-over check', '', '```text') + $lines + @('```', '')
        Add-Content -LiteralPath $env:GITHUB_STEP_SUMMARY -Encoding utf8 -Value $summary
    }
}
$baselineDir = Join-Path $env:RUNNER_TEMP 'dhun-msi-baseline'
New-Item -ItemType Directory -Force -Path $baselineDir | Out-Null
$baseline = $null
$baselineSidecar = $null
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

function Save-DhunAppWindow {
    param(
        [Parameter(Mandatory = $true)][System.Diagnostics.Process]$Process,
        [Parameter(Mandatory = $true)][string]$Path
    )
    $Process.Refresh()
    $handle = $Process.MainWindowHandle
    if ($handle -eq [IntPtr]::Zero) { throw 'Cannot capture DHUN without a real main-window handle' }
    Add-Type -AssemblyName System.Drawing
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class DhunWindowNative {
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int command);
}
'@
    [void][DhunWindowNative]::ShowWindow($handle, 9) # SW_RESTORE; minimized windows have no useful frame.
    [void][DhunWindowNative]::SetForegroundWindow($handle)
    Start-Sleep -Seconds 2
    $rect = New-Object DhunWindowNative+RECT
    if (-not [DhunWindowNative]::GetWindowRect($handle, [ref]$rect)) {
        throw 'GetWindowRect failed for the launched DHUN window'
    }
    $width = $rect.Right - $rect.Left
    $height = $rect.Bottom - $rect.Top
    if ($width -lt 200 -or $height -lt 150) {
        throw "Implausible DHUN window bounds ${width}x${height}; refusing to label this an app screenshot"
    }
    $bitmap = $null
    $graphics = $null
    try {
        $bitmap = New-Object System.Drawing.Bitmap -ArgumentList $width, $height
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $graphics.CopyFromScreen($rect.Left, $rect.Top, 0, 0, $bitmap.Size)
        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        if ($null -ne $graphics) { $graphics.Dispose() }
        if ($null -ne $bitmap) { $bitmap.Dispose() }
    }
    if (-not (Test-Path -LiteralPath $Path) -or (Get-Item -LiteralPath $Path).Length -lt 1000) {
        throw 'The DHUN window capture was not written or is implausibly small'
    }
}

# Prefer the newest published fixed public release. Until v1.00.002 exists,
# the published v1.00.001 release (whose actual asset is `dhun.msi`) is used.
# Accept its older versioned filename too if the asset is renamed back. A
# public test release used to be the fallback; the current `test` is a private
# draft and can be unreadable by the read-scoped job. Never weaken this gate
# to use an unverified/local MSI just because a download is unavailable.
# A missing baseline is SKIPPED (and a failure on main); contradictory bytes
# or checksums fail outright below.
$baselineOptions = @(
    @{ Tag = 'v1.00.002'; Name = 'dhun-v1.00.002.msi' },
    @{ Tag = 'v1.00.001'; Name = 'dhun.msi' },
    @{ Tag = 'v1.00.001'; Name = 'dhun-v1.00.001.msi' },
    @{ Tag = 'test'; Name = 'dhun-test.msi' }
)
$baselineReleaseTag = $null
$downloadFailures = @()
foreach ($option in $baselineOptions) {
    $tag = $option.Tag
    $assetName = $option.Name
    $assetPath = Join-Path $baselineDir $assetName
    $sidecarPath = "$assetPath.sha256"
    Remove-Item -LiteralPath $assetPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $sidecarPath -Force -ErrorAction SilentlyContinue
    gh release download $tag --repo $env:GITHUB_REPOSITORY --pattern $assetName --pattern "$assetName.sha256" --dir $baselineDir
    $downloadExit = $LASTEXITCODE
    if ($downloadExit -ne 0) {
        $downloadFailures += "${tag}: gh release download exited $downloadExit (absent, private/draft, or not readable)"
        continue
    }
    if (-not (Test-Path -LiteralPath $assetPath) -or -not (Test-Path -LiteralPath $sidecarPath)) {
        throw "gh release download $tag reported success but the baseline MSI/checksum is missing"
    }
    if ([string]::IsNullOrWhiteSpace((Get-Content -LiteralPath $sidecarPath -Raw))) {
        throw "The $tag baseline checksum sidecar is empty"
    }
    $baseline = $assetPath
    $baselineSidecar = $sidecarPath
    $baselineReleaseTag = $tag
    break
}
$baselineReason = $null
if (-not $baselineReleaseTag) {
    $baselineReason = "No readable MSI baseline. Tried: $($downloadFailures -join '; ')"
}
if ($baselineReason) {
    Write-DhunInstallResult ([ordered]@{
        status = 'skipped'
        requiredForThisBuild = [string][bool]$RequireInstallOver
        sourceSha = $env:GITHUB_SHA
        baselineTag = 'unavailable'
        baselineVersion = 'unavailable'
        baselineSha256 = 'unavailable'
        candidateVersion = 'not read'
        perUserInstall = 'not tested'
        installOver = "skipped: $baselineReason"
        userdataSentinel = 'not tested'
        cacheSentinel = 'not tested'
        futureUpgradeRemoval = 'not tested'
        uninstallCleanup = 'not tested'
        appLaunch = 'not tested'
        audioAndVisuals = 'not tested'
    })
    if ($RequireInstallOver) {
        throw "MSI install-over is REQUIRED for this build but was SKIPPED: $baselineReason"
    }
    Write-Host "::warning title=MSI install-over SKIPPED::$baselineReason. The candidate MSI is build-verified only - the in-place upgrade, sentinel preservation, future-upgrade guard and uninstall checks did NOT run."
    Write-Host '::notice title=MSI install-over NOT RUN (build-verified only)::No readable baseline, so no install or uninstall was attempted. Never report this run as an install-over pass.'
    exit 0
}
$expectedHash = ((Get-Content -LiteralPath $baselineSidecar -Raw).Trim() -split '\s+')[0]
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
$appLaunchResult = 'not tested'
$audioVisualResult = 'not tested'
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
    Write-Host "::notice title=MSI upgrade smoke PASS::Hosted Windows: $($baselineReleaseTag) $($old['ProductVersion']) -> $($new['ProductVersion']); per-user install and userdata/cache sentinels preserved. Baseline SHA256=$baselineHash. Installed-app launch is checked separately."

    # Launch the actual upgraded per-user package, not a Compose preview. A live
    # native window and PNG establish startup only; audible playback and visual
    # acceptance still require their own evidence.
    $app = $null
    try {
        $executable = Join-Path $installDirectory 'DHUN.exe'
        $app = Start-Process -FilePath $executable -WorkingDirectory $installDirectory -PassThru
        $windowDeadline = [DateTime]::UtcNow.AddSeconds(90)
        do {
            Start-Sleep -Seconds 2
            $app.Refresh()
            if ($app.HasExited) { throw "Installed DHUN.exe exited before opening a window (code $($app.ExitCode)); see dhun-startup.log" }
        } while ($app.MainWindowHandle -eq [IntPtr]::Zero -and [DateTime]::UtcNow -lt $windowDeadline)
        if ($app.MainWindowHandle -eq [IntPtr]::Zero) { throw 'Installed DHUN.exe did not expose a main window within 90 seconds' }
        Start-Sleep -Seconds 15
        $appShot = Join-Path $logs 'app-launch.png'
        Save-DhunAppWindow -Process $app -Path $appShot
        $appLaunchResult = 'passed: installed DHUN.exe opened a native main window'
        $audioVisualResult = 'window PNG captured; visual review and audible playback not tested'
        Write-Host "::notice title=MSI installed-app launch PASS::Native DHUN.exe window opened after upgrade; captured app-launch.png. Audible playback and visual review are not claimed."
    } finally {
        if ($null -ne $app) {
            $app.Refresh()
            if (-not $app.HasExited) {
                [void]$app.CloseMainWindow()
                if (-not $app.WaitForExit(15000)) { $app.Kill(); $app.WaitForExit() }
            }
            $app.Dispose()
        }
    }

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
    Write-Host '::notice title=MSI uninstall smoke PASS::Hosted Windows uninstall removed test userdata. Installed-app startup was checked separately; audible playback and visual acceptance are not claimed.'
    Write-DhunInstallResult ([ordered]@{
        status = 'passed'
        requiredForThisBuild = [string][bool]$RequireInstallOver
        sourceSha = $env:GITHUB_SHA
        baselineTag = $baselineReleaseTag
        baselineVersion = $old['ProductVersion']
        baselineSha256 = $baselineHash
        candidateVersion = $new['ProductVersion']
        perUserInstall = 'passed'
        installOver = 'passed'
        userdataSentinel = 'preserved'
        cacheSentinel = 'preserved'
        futureUpgradeRemoval = 'preserved both sentinels'
        uninstallCleanup = 'passed'
        appLaunch = $appLaunchResult
        audioAndVisuals = $audioVisualResult
    })
} finally {
    # Product GUIDs were read from these two verified DHUN packages. Do not
    # query Win32_Product (which can trigger repairs of unrelated software).
    $productCodes = @($new['ProductCode'], $old['ProductCode']) | Select-Object -Unique
    foreach ($code in $productCodes) {
        try { [void](Invoke-MsiCheck '/x' $code (Join-Path $logs ("cleanup-" + $code.Trim('{}') + '.log')) -Cleanup) }
        catch { Write-Warning "Disposable-runner cleanup: $_" }
    }
}
