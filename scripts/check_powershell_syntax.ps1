# Parse Windows packaging helpers without executing them or requiring Windows.
$ErrorActionPreference = 'Stop'
$files = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File)
if ($files.Count -eq 0) { throw 'No PowerShell helpers found to validate' }
$failed = $false
foreach ($file in $files) {
    $tokens = $null
    $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$tokens, [ref]$parseErrors)
    foreach ($issue in $parseErrors) {
        $failed = $true
        $message = $issue.Message.Replace('%', '%25').Replace("`r", '%0D').Replace("`n", '%0A')
        Write-Host "::error file=scripts/$($file.Name),line=$($issue.Extent.StartLineNumber),title=PowerShell syntax::$message"
    }
}
if ($failed) { exit 1 }
Write-Host "PASS: $($files.Count) PowerShell helpers parsed (syntax only; no MSI execution)"
