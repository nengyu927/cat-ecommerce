param(
    [string]$Server = 'tcp:127.0.0.1,1433',
    [string]$JdbcHost = '127.0.0.1',
    [int]$JdbcPort = 1433,
    [switch]$AllowOtherVersion,
    [switch]$Browser
)
$ErrorActionPreference = 'Stop'
$projectDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!(Test-Path -LiteralPath (Join-Path $projectDirectory 'pom.xml'))) { throw 'Backend pom.xml not found' }
if ($JdbcHost -notmatch '^[a-zA-Z0-9.-]+$' -or $JdbcPort -lt 1 -or $JdbcPort -gt 65535) { throw 'Invalid JDBC endpoint' }
$testSuffix = [Guid]::NewGuid().ToString('N')
$testDatabase = 'catshop_it_' + $testSuffix
$testLogin = 'catshop_it_login_' + $testSuffix
if ($testDatabase -notmatch '^catshop_it_[a-f0-9]{32}$' -or $testLogin -notmatch '^catshop_it_login_[a-f0-9]{32}$') {
    throw 'Invalid isolated test identifiers'
}
$testPassword = 'It!9' + [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
$databaseCreated = $false
$loginCreated = $false
$environmentKeys = @('CATSHOP_IT_JDBC_URL', 'CATSHOP_IT_DB_USER', 'CATSHOP_IT_DB_PASSWORD', 'CATSHOP_IT_ALLOW_OTHER_VERSION', 'CATSHOP_IT_BROWSER')
$previousEnvironment = @{}
foreach ($key in $environmentKeys) { $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
function Invoke-IsolatedSql([string]$Query) {
    # Pass provisioning SQL through stdin; never put the temporary password in process arguments.
    ($Query + "`nGO`n") | & sqlcmd -S $Server -E -C -l 10 -b -W -w 120
    if ($LASTEXITCODE -ne 0) { throw 'Isolated SQL provisioning or cleanup failed' }
}
Push-Location -LiteralPath $projectDirectory
try {
    Invoke-IsolatedSql "CREATE LOGIN [$testLogin] WITH PASSWORD = N'$testPassword', CHECK_POLICY = ON;"
    $loginCreated = $true
    Invoke-IsolatedSql "CREATE DATABASE [$testDatabase];"
    $databaseCreated = $true
    Invoke-IsolatedSql "USE [$testDatabase]; CREATE USER [$testLogin] FOR LOGIN [$testLogin]; ALTER ROLE db_owner ADD MEMBER [$testLogin];"
    [Environment]::SetEnvironmentVariable('CATSHOP_IT_JDBC_URL', "jdbc:sqlserver://${JdbcHost}:${JdbcPort};databaseName=$testDatabase;encrypt=true;trustServerCertificate=true;loginTimeout=10", 'Process')
    [Environment]::SetEnvironmentVariable('CATSHOP_IT_DB_USER', $testLogin, 'Process')
    [Environment]::SetEnvironmentVariable('CATSHOP_IT_DB_PASSWORD', $testPassword, 'Process')
    [Environment]::SetEnvironmentVariable('CATSHOP_IT_ALLOW_OTHER_VERSION', $AllowOtherVersion.IsPresent.ToString().ToLowerInvariant(), 'Process')
    [Environment]::SetEnvironmentVariable('CATSHOP_IT_BROWSER', $Browser.IsPresent.ToString().ToLowerInvariant(), 'Process')
    Write-Output "Verify application against isolated database $testDatabase"
    & mvn.cmd -B -Psqlserver-it verify
    if ($LASTEXITCODE -ne 0) { throw 'Application SQL Server integration verification failed' }
} finally {
    foreach ($key in $environmentKeys) { [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process') }
    # Only exact, generated identifiers are used; do not enumerate or remove user databases/logins.
    try {
        if ($databaseCreated) {
            Invoke-IsolatedSql "ALTER DATABASE [$testDatabase] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$testDatabase];"
            Write-Output 'Removed isolated test database.'
        }
    } finally {
        try {
            if ($loginCreated) {
                Invoke-IsolatedSql "DROP LOGIN [$testLogin];"
                Write-Output 'Removed isolated test login.'
            }
        } finally {
            $testPassword = $null
            Pop-Location
        }
    }
}
