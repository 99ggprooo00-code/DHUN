# Windows-only, read-only MSI metadata access. No product installation here.
function Get-DhunMsiProperties {
    param([Parameter(Mandatory = $true)][string]$Path)
    $installer = $null
    $database = $null
    try {
        $installer = New-Object -ComObject WindowsInstaller.Installer
        $database = $installer.GetType().InvokeMember(
            'OpenDatabase', [Reflection.BindingFlags]::InvokeMethod, $null,
            $installer, @((Resolve-Path -LiteralPath $Path).Path, 0)
        )
        $properties = @{}
        foreach ($name in @('ProductVersion', 'ProductCode', 'UpgradeCode', 'ProductName')) {
            $view = $null
            $record = $null
            try {
                $query = 'SELECT `Value` FROM `Property` WHERE `Property` = ''' + $name + ''''
                $view = $database.GetType().InvokeMember(
                    'OpenView', [Reflection.BindingFlags]::InvokeMethod, $null, $database, @($query)
                )
                [void]$view.GetType().InvokeMember('Execute', [Reflection.BindingFlags]::InvokeMethod, $null, $view, $null)
                $record = $view.GetType().InvokeMember('Fetch', [Reflection.BindingFlags]::InvokeMethod, $null, $view, $null)
                if ($null -eq $record) { throw "MSI property missing: $name" }
                $properties[$name] = [string]$record.GetType().InvokeMember(
                    'StringData', [Reflection.BindingFlags]::GetProperty, $null, $record, @(1)
                )
            } finally {
                if ($null -ne $view) {
                    [void]$view.GetType().InvokeMember('Close', [Reflection.BindingFlags]::InvokeMethod, $null, $view, $null)
                }
                if ($null -ne $record) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($record) }
                if ($null -ne $view) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($view) }
            }
        }
        return $properties
    } finally {
        if ($null -ne $database) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) }
        if ($null -ne $installer) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer) }
    }
}
