$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'start-persistent-preview.ps1'
$localRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../.local'))
$source = Import-Clixml -LiteralPath (Join-Path $localRoot 'preview-state.xml')
$suffix = [Guid]::NewGuid().ToString('N')
$database = 'catshop_persist_' + $suffix
$login = 'catshop_persist_login_' + $suffix
if ($source.Database -eq $database -or $database -notmatch '^catshop_persist_[a-f0-9]{32}$') { throw 'Unsafe guard-test identifier.' }
$statePath = Join-Path $localRoot ('guard-' + $suffix + '.xml')
$created = $false
function Sql([string]$Query) {
    ($Query + "`nGO`n") | & sqlcmd -S $source.Server -E -C -l 10 -b
    if ($LASTEXITCODE -ne 0) { throw 'Guard-test SQL failed.' }
}
function AssertRejected([string]$ExpectedMessage) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher -StatePath $statePath -ProvisionOnly 2>&1
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($exitCode -eq 0) { throw 'Unsafe state was unexpectedly accepted.' }
    if (($output | Out-String) -notlike ('*'+$ExpectedMessage+'*')) { throw 'Launcher failed for an unexpected reason.' }
}
try {
    $state = [pscustomobject]@{Version=1; Id=$suffix; Database=$database; Login=$login; Server=$source.Server; JdbcHost=$source.JdbcHost; JdbcPort=$source.JdbcPort; Password=$source.Password; JwtSecret=$source.JwtSecret}
    $state | Export-Clixml -LiteralPath $statePath
    $xml = Get-Content -LiteralPath $statePath -Raw
    $password = [Net.NetworkCredential]::new('', $state.Password).Password
    $jwt = [Net.NetworkCredential]::new('', $state.JwtSecret).Password
    if ($xml.Contains($password) -or $xml.Contains($jwt)) { throw 'Plaintext credentials found.' }
    $password = $null; $jwt = $null
    Write-Output 'PASS: serialized credentials are encrypted, not plaintext.'
    $state.Database='catshop'; $state | Export-Clixml -LiteralPath $statePath
    AssertRejected 'Invalid persistent state identifiers'
    Write-Output 'PASS: malformed database identifiers rejected before SQL changes.'
    $state.Database=$database; $state.Server='tcp:localhost,1433'; $state | Export-Clixml -LiteralPath $statePath
    AssertRejected 'Endpoint differs from saved state'
    Write-Output 'PASS: saved endpoint mismatch rejected.'
    $state.Server=$source.Server; $state | Export-Clixml -LiteralPath $statePath
    Sql "CREATE DATABASE [$database];"
    $created=$true
    Sql "USE [$database]; CREATE TABLE guard_sentinel(id int NOT NULL); INSERT INTO guard_sentinel VALUES(17);"
    # Same legal name, but deliberately missing the preview ownership marker.
    AssertRejected 'Database ownership marker mismatch'
    Sql "IF SUSER_ID(N'$login') IS NOT NULL THROW 51000, 'Login created for unowned DB', 1; IF (SELECT COUNT(*) FROM [$database].dbo.guard_sentinel WHERE id=17)<>1 THROW 51000, 'Unowned DB changed', 1;"
    Write-Output 'PASS: unowned database not adopted; sentinel retained and no login created.'
} finally {
    if ($created) { Sql "ALTER DATABASE [$database] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$database];"; Write-Output 'Removed only the uniquely generated guard-test database.' }
    foreach ($file in @($statePath, $statePath+'.lock')) { if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file } }
}
