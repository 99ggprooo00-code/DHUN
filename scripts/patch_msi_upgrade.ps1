# Finalize DHUN's unsigned jpackage MSI before hashing/signing/distribution.
# Older jpackage installers recursively remove INSTALLDIR on major upgrade;
# userdata lives there. Preserve it on upgrade, retain cleanup on real uninstall.
param([Parameter(Mandatory = $true)][string]$Path)
$ErrorActionPreference = 'Stop'
$path = (Resolve-Path -LiteralPath $Path).Path
if ((Get-AuthenticodeSignature -LiteralPath $path).Status -ne 'NotSigned') {
    throw 'Upgrade policy must be applied before MSI signing; refusing to modify a signed/unknown package'
}
$installer = $null
$database = $null
function Invoke-DhunMsiSql {
    param([object]$Database, [string]$Sql, [switch]$Scalar)
    $view = $null
    $record = $null
    try {
        $view = $Database.GetType().InvokeMember('OpenView', [Reflection.BindingFlags]::InvokeMethod, $null, $Database, @($Sql))
        [void]$view.GetType().InvokeMember('Execute', [Reflection.BindingFlags]::InvokeMethod, $null, $view, $null)
        if ($Scalar) {
            $record = $view.GetType().InvokeMember('Fetch', [Reflection.BindingFlags]::InvokeMethod, $null, $view, $null)
            if ($null -ne $record) {
                return [string]$record.GetType().InvokeMember('StringData', [Reflection.BindingFlags]::GetProperty, $null, $record, @(1))
            }
        }
    } finally {
        if ($null -ne $view) { [void]$view.GetType().InvokeMember('Close', [Reflection.BindingFlags]::InvokeMethod, $null, $view, $null) }
        if ($null -ne $record) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($record) }
        if ($null -ne $view) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($view) }
    }
}
function Quote-DhunMsiSql([string]$Value) { return "'" + $Value.Replace("'", "''") + "'" }
try {
    $installer = New-Object -ComObject WindowsInstaller.Installer
    $database = $installer.GetType().InvokeMember('OpenDatabase', [Reflection.BindingFlags]::InvokeMethod, $null, $installer, @($path, 1))
    $existing = Invoke-DhunMsiSql $database 'SELECT `Value` FROM `Property` WHERE `Property` = ''DHUN_UPGRADE_DATA_POLICY''' -Scalar
    if ($existing -eq '1') { Write-Host 'MSI upgrade data policy already applied'; return }
    if ($existing) { throw "Unknown MSI upgrade data policy $existing" }
    $upgrade = Invoke-DhunMsiSql $database 'SELECT `Value` FROM `Property` WHERE `Property` = ''UpgradeCode''' -Scalar
    if ($upgrade.Trim('{}') -ne '31ddb86b-9666-4071-b11c-45f16fa4682d') { throw 'Refusing to modify an unrelated MSI' }

    $property = Invoke-DhunMsiSql $database 'SELECT `Property` FROM `WixRemoveFolderEx`' -Scalar
    if ($property -notmatch '^RM_RF[A-Z0-9_]+$') { throw 'Unrecognized jpackage directory-cleaner property' }
    $component = Invoke-DhunMsiSql $database ('SELECT `Component_` FROM `Registry` WHERE `Name` = ' + (Quote-DhunMsiSql $property)) -Scalar
    if (-not $component) { throw 'Directory-cleaner registry component not found' }
    $appSearch = [int](Invoke-DhunMsiSql $database 'SELECT `Sequence` FROM `InstallExecuteSequence` WHERE `Action` = ''AppSearch''' -Scalar)
    $cleaner = [int](Invoke-DhunMsiSql $database 'SELECT `Sequence` FROM `InstallExecuteSequence` WHERE `Action` = ''WixRemoveFoldersEx''' -Scalar)
    $validate = [int](Invoke-DhunMsiSql $database 'SELECT `Sequence` FROM `InstallExecuteSequence` WHERE `Action` = ''InstallValidate''' -Scalar)
    $initialize = [int](Invoke-DhunMsiSql $database 'SELECT `Sequence` FROM `InstallExecuteSequence` WHERE `Action` = ''InstallInitialize''' -Scalar)
    if ($cleaner -le $appSearch + 1 -or $validate -le 0 -or $initialize -le $validate + 3) {
        throw 'Unsupported MSI action ordering; cannot place upgrade guards safely'
    }

    # Future versions: clear only this uninstall session's cleaner property.
    # The persistent registry value remains available for an ordinary uninstall.
    $guardTarget = Quote-DhunMsiSql $property
    Invoke-DhunMsiSql $database ("INSERT INTO ``CustomAction`` (``Action``, ``Type``, ``Source``, ``Target``) VALUES ('DhunKeepDataOnUpgrade', 51, $guardTarget, '')")
    Invoke-DhunMsiSql $database ("INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Condition``, ``Sequence``) VALUES ('DhunKeepDataOnUpgrade', 'UPGRADINGPRODUCTCODE', " + ($cleaner - 1) + ')')

    # A marker allows the legacy bridge to leave already-safe packages alone.
    $registryComponent = Quote-DhunMsiSql $component
    Invoke-DhunMsiSql $database ("INSERT INTO ``Registry`` (``Registry``, ``Root``, ``Key``, ``Name``, ``Value``, ``Component_``) VALUES ('DhunUpgradePolicyMarker', 1, 'Software\DHUN\DHUN\[ProductVersion]', 'UpgradePreservesUserdata', '#1', $registryComponent)")

    # Legacy packages cannot be changed retroactively. Before removing an old
    # product, suppress ONLY its matching HKCU recursive-cleanup value. Normal
    # installed-file removal still runs; userdata is never moved or deleted.
    $script = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'prepare_legacy_msi_upgrade.ps1') -Raw
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($script))
    $arguments = Quote-DhunMsiSql ('-NoLogo -NoProfile -NonInteractive -EncodedCommand ' + $encoded)
    Invoke-DhunMsiSql $database 'INSERT INTO `CustomAction` (`Action`, `Type`, `Source`, `Target`) VALUES (''DhunSetUpgradePowerShell'', 51, ''DHUN_UPGRADE_POWERSHELL'', ''[System64Folder]WindowsPowerShell\v1.0\powershell.exe'')'
    Invoke-DhunMsiSql $database ("INSERT INTO ``CustomAction`` (``Action``, ``Type``, ``Source``, ``Target``) VALUES ('DhunPrepareLegacyUpgrade', 50, 'DHUN_UPGRADE_POWERSHELL', $arguments)")
    $condition = Quote-DhunMsiSql 'JP_UPGRADABLE_FOUND AND NOT Installed'
    Invoke-DhunMsiSql $database ("INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Condition``, ``Sequence``) VALUES ('DhunSetUpgradePowerShell', $condition, " + ($validate + 1) + ')')
    Invoke-DhunMsiSql $database ("INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Condition``, ``Sequence``) VALUES ('DhunPrepareLegacyUpgrade', $condition, " + ($validate + 2) + ')')
    # Supported MSI major-upgrade slot: directories have been resolved, but
    # the new product's installation transaction has not started.
    Invoke-DhunMsiSql $database ('UPDATE `InstallExecuteSequence` SET `Sequence` = ' + ($validate + 3) + ' WHERE `Action` = ''RemoveExistingProducts''')
    Invoke-DhunMsiSql $database 'INSERT INTO `Property` (`Property`, `Value`) VALUES (''DHUN_UPGRADE_DATA_POLICY'', ''1'')'
    [void]$database.GetType().InvokeMember('Commit', [Reflection.BindingFlags]::InvokeMethod, $null, $database, $null)
    Write-Host "::notice title=MSI upgrade policy applied::Policy=1; legacy HKCU cleanup bridge; future upgrade-only cleaner guard; explicit uninstall cleanup retained. Native smoke test still required."
} finally {
    if ($null -ne $database) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) }
    if ($null -ne $installer) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer) }
}
