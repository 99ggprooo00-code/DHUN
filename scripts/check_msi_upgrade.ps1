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

function Find-DhunMainWindow {
    param([int[]]$ExcludedProcessIds = @())
    # jpackage's native launcher can remain alive while a child DHUN.exe owns
    # the Compose window. Watch the normal application window, not whichever
    # PID Start-Process returned. A startup-error window is never acceptance.
    $candidates = @(Get-Process -Name 'DHUN' -ErrorAction SilentlyContinue |
        Where-Object { $_.Id -notin $ExcludedProcessIds })
    foreach ($process in $candidates) {
        try {
            $process.Refresh()
            if (-not $process.HasExited -and
                $process.MainWindowHandle -ne [IntPtr]::Zero -and
                $process.MainWindowTitle -eq 'DHUN' -and
                $process.Responding) {
                return $process
            }
        } catch { continue }
    }
    return $null
}

function Stop-DhunLaunchedProcess {
    param([System.Diagnostics.Process]$Process)
    if ($null -eq $Process) { return }
    try {
        $Process.Refresh()
        if (-not $Process.HasExited) {
            [void]$Process.CloseMainWindow()
            if (-not $Process.WaitForExit(15000)) {
                $Process.Kill()
                $Process.WaitForExit()
            }
        }
    } finally {
        $Process.Dispose()
    }
}

function Write-DhunSafeLaunchDiagnostics {
    param(
        [System.Diagnostics.Process]$Launcher,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string[]]$StartupLogCandidates
    )
    $processes = @()
    foreach ($process in Get-Process) {
        try {
            $title = $process.MainWindowTitle
            if ($process.ProcessName -notmatch '^(DHUN|java|javaw)$' -and $title -notlike 'DHUN*') { continue }
            $processes += [PSCustomObject]@{
                name = $process.ProcessName
                id = $process.Id
                mainWindowHandle = $process.MainWindowHandle.ToInt64()
                mainWindowTitle = ([string]$title -replace '[\r\n;=]', ' ')
                responding = $process.Responding
            }
        } catch { continue }
    }
    $startup = @()
    foreach ($candidate in $StartupLogCandidates) {
        if (-not (Test-Path -LiteralPath $candidate)) { continue }
        $text = [IO.File]::ReadAllText($candidate)
        $classes = @([regex]::Matches($text, 'FAILURE:\s+([A-Za-z0-9_.$]+)') | ForEach-Object { $_.Groups[1].Value })
        $startup += [PSCustomObject]@{
            location = if ($candidate.StartsWith($userdata, [StringComparison]::OrdinalIgnoreCase)) { 'userdata' } else { 'temp' }
            bytes = (Get-Item -LiteralPath $candidate).Length
            lastWriteUtc = (Get-Item -LiteralPath $candidate).LastWriteTimeUtc.ToString('o')
            mainStarted = $text.Contains('DHUN main starting')
            jdbcProbeAvailable = $text.Contains('DHUN probe: java.sql.Driver available')
            failureClasses = $classes
        }
    }
    $launcherInfo = $null
    if ($null -ne $Launcher) {
        $Launcher.Refresh()
        $launcherInfo = [PSCustomObject]@{
            id = $Launcher.Id
            exited = $Launcher.HasExited
            mainWindowHandle = $Launcher.MainWindowHandle.ToInt64()
            mainWindowTitle = ([string]$Launcher.MainWindowTitle -replace '[\r\n;=]', ' ')
        }
    }
    $lines = @('note=Metadata only: no command lines, paths, log messages, URLs or stack traces.')
    if ($null -ne $launcherInfo) {
        $lines += "launcher=id=$($launcherInfo.id);exited=$($launcherInfo.exited);handle=$($launcherInfo.mainWindowHandle);title=$($launcherInfo.mainWindowTitle)"
    }
    foreach ($process in $processes) {
        $lines += "process=name=$($process.name);id=$($process.id);handle=$($process.mainWindowHandle);responding=$($process.responding);title=$($process.mainWindowTitle)"
    }
    foreach ($entry in $startup) {
        $lines += "startup=location=$($entry.location);bytes=$($entry.bytes);lastWriteUtc=$($entry.lastWriteUtc);mainStarted=$($entry.mainStarted);jdbcProbeAvailable=$($entry.jdbcProbeAvailable);failureClasses=$($entry.failureClasses -join ',')"
    }
    $lines | Set-Content -LiteralPath $Path -Encoding utf8
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
    $windowProcess = $null
    try {
        $executable = Join-Path $installDirectory 'DHUN.exe'
        $preLaunchProcessIds = @(Get-Process -Name 'DHUN' -ErrorAction SilentlyContinue |
            ForEach-Object { $_.Id })
        $app = Start-Process -FilePath $executable -WorkingDirectory $installDirectory -PassThru
        $windowDeadline = [DateTime]::UtcNow.AddSeconds(90)
        do {
            Start-Sleep -Seconds 2
            $windowProcess = Find-DhunMainWindow -ExcludedProcessIds $preLaunchProcessIds
        } while ($null -eq $windowProcess -and [DateTime]::UtcNow -lt $windowDeadline)
        if ($null -eq $windowProcess) {
            throw 'Installed DHUN.exe did not expose a normal main window within 90 seconds'
        }
        Start-Sleep -Seconds 15
        $appShot = Join-Path $logs 'app-launch.png'
        Save-DhunAppWindow -Process $windowProcess -Path $appShot
        $appLaunchResult = 'passed: installed DHUN.exe opened a native main window'
        $audioVisualResult = 'window PNG captured; visual review and audible playback not tested'
        Write-Host "::notice title=MSI installed-app launch PASS::Native DHUN.exe window opened after upgrade; captured app-launch.png. Audible playback and visual review are not claimed."
    } catch {
        $appLaunchResult = 'failed'
        $audioVisualResult = 'not tested'
        try {
            Write-DhunSafeLaunchDiagnostics `
                -Launcher $app `
                -Path (Join-Path $logs 'app-diagnostics.txt') `
                -StartupLogCandidates @((Join-Path $userdata 'dhun-startup.log'), (Join-Path $env:TEMP 'dhun-startup.log'))
        } catch {
            Write-Host '::warning title=MSI launch diagnostics::Safe metadata could not be written; preserving the original launch failure.'
        }
        Write-DhunInstallResult ([ordered]@{
            status = 'failed'
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
            futureUpgradeRemoval = 'not tested'
            uninstallCleanup = 'not tested'
            appLaunch = $appLaunchResult
            audioAndVisuals = $audioVisualResult
        })
        throw
    } finally {
        $windowProcessId = if ($null -ne $windowProcess) { $windowProcess.Id } else { -1 }
        Stop-DhunLaunchedProcess -Process $windowProcess
        if ($null -eq $app -or $app.Id -ne $windowProcessId) {
            Stop-DhunLaunchedProcess -Process $app
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
