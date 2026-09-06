# Embedded in the unsigned MSI as an upgrade-only custom action. It bridges
# older jpackage packages whose unconditional directory cleaner predates
# DHUN's UPGRADINGPRODUCTCODE guard. Touch only DHUN's own HKCU cleanup values;
# never move/delete userdata, change another product, or use Win32_Product.
$ErrorActionPreference = 'Stop'
$installer = $null
$products = $null
$removed = @()
try {
    $installer = New-Object -ComObject WindowsInstaller.Installer
    $products = $installer.GetType().InvokeMember(
        'RelatedProducts', [Reflection.BindingFlags]::GetProperty, $null,
        $installer, @('{31DDB86B-9666-4071-B11C-45F16FA4682D}')
    )
    $count = [int]$products.GetType().InvokeMember('Count', [Reflection.BindingFlags]::GetProperty, $null, $products, $null)
    for ($i = 0; $i -lt $count; $i++) {
        $code = [string]$products.GetType().InvokeMember('Item', [Reflection.BindingFlags]::GetProperty, $null, $products, @($i))
        $version = [string]$installer.GetType().InvokeMember(
            'ProductInfo', [Reflection.BindingFlags]::GetProperty, $null, $installer, @($code, 'VersionString')
        )
        if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Unexpected related DHUN version' }
        $location = [string]$installer.GetType().InvokeMember(
            'ProductInfo', [Reflection.BindingFlags]::GetProperty, $null, $installer, @($code, 'InstallLocation')
        )
        if ([string]::IsNullOrWhiteSpace($location)) { throw 'Related DHUN has no registered install directory' }
        $location = [IO.Path]::GetFullPath($location).TrimEnd('\')
        if (-not (Test-Path -LiteralPath (Join-Path $location 'userdata'))) { continue }
        if (-not (Test-Path -LiteralPath (Join-Path $location 'DHUN.exe'))) { throw 'Related DHUN executable not found beside userdata' }
        $key = 'HKCU:\Software\DHUN\DHUN\' + $version
        if (-not (Test-Path -LiteralPath $key)) { throw 'Related DHUN cleanup registration was not found in HKCU' }
        $values = Get-ItemProperty -LiteralPath $key
        $marker = $values.PSObject.Properties['UpgradePreservesUserdata']
        if ($null -ne $marker -and $marker.Value -eq 1) { continue }
        foreach ($value in $values.PSObject.Properties) {
            if (-not $value.Name.StartsWith('RM_RF', [StringComparison]::OrdinalIgnoreCase)) { continue }
            if ($value.Value -isnot [string] -or [string]::IsNullOrWhiteSpace($value.Value)) { continue }
            $registered = [IO.Path]::GetFullPath($value.Value).TrimEnd('\')
            if (-not $registered.Equals($location, [StringComparison]::OrdinalIgnoreCase)) {
                throw 'DHUN cleanup registration does not match its installed location'
            }
            $removed += @{ Key = $key; Name = $value.Name; Value = $value.Value }
            Remove-ItemProperty -LiteralPath $key -Name $value.Name -ErrorAction Stop
        }
    }
} catch {
    # If preparation itself fails, restore everything changed so far and
    # abort before RemoveExistingProducts. User files are never touched.
    foreach ($value in $removed) {
        try { Set-ItemProperty -LiteralPath $value.Key -Name $value.Name -Value $value.Value -ErrorAction Stop }
        catch { Write-Error 'Could not restore a DHUN legacy cleanup value' -ErrorAction Continue }
    }
    Write-Error $_ -ErrorAction Continue
    exit 1
} finally {
    if ($null -ne $products) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($products) }
    if ($null -ne $installer) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer) }
}
