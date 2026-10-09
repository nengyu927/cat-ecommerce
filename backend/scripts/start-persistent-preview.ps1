param(
    [string]$Server = 'tcp:127.0.0.1,1433',
    [string]$JdbcHost = '127.0.0.1',
    [int]$JdbcPort = 1433,
    [int]$Port = 8080,
    [string]$StatePath = '',
    [switch]$ProvisionOnly
)
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'This launcher requires Windows DPAPI. Use explicit environment configuration elsewhere.' }
if ($JdbcHost -notin @('127.0.0.1', 'localhost') -or $Server -notmatch '^(tcp:)?(127\.0\.0\.1|localhost)(,\d{1,5})?$') { throw 'Only local SQL Server endpoints are allowed.' }
if ($JdbcPort -lt 1 -or $JdbcPort -gt 65535 -or $Port -lt 1024 -or $Port -gt 65535) { throw 'Invalid port.' }
$backendRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!(Test-Path -LiteralPath (Join-Path $backendRoot 'pom.xml'))) { throw 'Backend pom.xml not found.' }
if (!$StatePath) { $StatePath = Join-Path $backendRoot '../.local/preview-state.xml' }
$StatePath = [IO.Path]::GetFullPath($StatePath)
$stateDirectory = [IO.Path]::GetDirectoryName($StatePath)
New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
# Prevent simultaneous provisioning/launching using the same saved state.
$stateLock = [IO.File]::Open($StatePath + '.lock', [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
$previousEnvironment = @{}
try {
    if (Test-Path -LiteralPath $StatePath) {
        try { $state = Import-Clixml -LiteralPath $StatePath }
        catch { throw 'Cannot decrypt preview state. Use the Windows user and machine that created it; do not delete or replace it.' }
    } else {
        $suffix = [Guid]::NewGuid().ToString('N')
        $state = [pscustomobject]@{
            Version = 1; Id = $suffix; Database = 'catshop_persist_' + $suffix; Login = 'catshop_persist_login_' + $suffix
            Server = $Server; JdbcHost = $JdbcHost; JdbcPort = $JdbcPort
            Password = ConvertTo-SecureString ('Demo!9' + [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')) -AsPlainText -Force
            JwtSecret = ConvertTo-SecureString ([Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')) -AsPlainText -Force
        }
        # SecureString serialization is encrypted by Windows for the current user/machine.
        $state | Export-Clixml -LiteralPath $StatePath
    }
    if ($state.Version -ne 1 -or $state.Id -notmatch '^[a-f0-9]{32}$' -or $state.Database -cne ('catshop_persist_' + $state.Id) -or $state.Login -cne ('catshop_persist_login_' + $state.Id)) { throw 'Invalid persistent state identifiers.' }
    if ($state.Server -ne $Server -or $state.JdbcHost -ne $JdbcHost -or $state.JdbcPort -ne $JdbcPort) { throw 'Endpoint differs from saved state. Refusing to create another database.' }
    if ($state.Password -isnot [Security.SecureString] -or $state.JwtSecret -isnot [Security.SecureString]) { throw 'Saved credentials are not encrypted SecureStrings.' }
    $database = $state.Database; $login = $state.Login; $ownerId = $state.Id
    $password = [Net.NetworkCredential]::new('', $state.Password).Password
    $jwtSecret = [Net.NetworkCredential]::new('', $state.JwtSecret).Password
    if ($password -notmatch '^Demo!9[a-f0-9]{64}$' -or $jwtSecret -notmatch '^[a-f0-9]{64}$') { throw 'Invalid saved credentials.' }
    # No DROP DATABASE/LOGIN or data reset here, including on failure and Ctrl+C.
    # Existing databases must carry the matching ownership marker; never adopt arbitrary DBs.
    $provisionSql = @"
SET NOCOUNT ON;
USE [master];
IF DB_ID(N'$database') IS NULL
BEGIN
    IF SUSER_ID(N'$login') IS NOT NULL THROW 51000, 'Login exists without owned database; manual inspection required', 1;
    CREATE DATABASE [$database];
    EXEC [$database].sys.sp_addextendedproperty @name=N'CatshopPersistentPreview', @value=N'$ownerId';
END;
GO
IF NOT EXISTS (SELECT 1 FROM [$database].sys.extended_properties WHERE class=0 AND name=N'CatshopPersistentPreview' AND CONVERT(nvarchar(128),value)=N'$ownerId')
    THROW 51000, 'Database ownership marker mismatch; refusing changes', 1;
IF SUSER_ID(N'$login') IS NULL
BEGIN
    IF EXISTS (SELECT 1 FROM [$database].sys.database_principals WHERE name=N'$login')
        THROW 51000, 'Orphaned database user; manual inspection required', 1;
    SET XACT_ABORT ON;
    BEGIN TRANSACTION;
    CREATE LOGIN [$login] WITH PASSWORD=N'$password', CHECK_POLICY=ON;
    USE [$database];
    CREATE USER [$login] FOR LOGIN [$login];
    ALTER ROLE db_owner ADD MEMBER [$login];
    COMMIT;
END;
IF NOT EXISTS (SELECT 1 FROM [$database].sys.database_principals WHERE name=N'$login' AND sid=SUSER_SID(N'$login'))
    THROW 51000, 'Database user/login mapping mismatch', 1;
"@
    # Password goes through stdin, never process arguments or console output.
    ($provisionSql + "`nGO`n") | & sqlcmd -S $Server -E -C -l 10 -b
    if ($LASTEXITCODE -ne 0) { throw 'Persistent preview provisioning failed. State retained for inspection.' }
    Write-Output ('PERSISTENT DATABASE: ' + $database)
    Write-Output ('Encrypted local state: ' + $StatePath)
    Write-Output 'Data is retained when the backend exits. Do not delete the state file.'
    if (!$ProvisionOnly) {
        $environment = @{
            SPRING_DATASOURCE_URL = "jdbc:sqlserver://${JdbcHost}:${JdbcPort};databaseName=$database;encrypt=true;trustServerCertificate=true"
            SPRING_DATASOURCE_USERNAME = $login; SPRING_DATASOURCE_PASSWORD = $password
            APP_JWT_SECRET = $jwtSecret; SERVER_PORT = $Port
            SPRING_JPA_SHOW_SQL = 'false'; LOGGING_LEVEL_ORG_HIBERNATE_SQL = 'warn'; LOGGING_LEVEL_COM_CATSHOP = 'info'
        }
        foreach ($key in $environment.Keys) {
            $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
            [Environment]::SetEnvironmentVariable($key, $environment[$key], 'Process')
        }
        Push-Location -LiteralPath $backendRoot
        try {
            & mvn.cmd -B spring-boot:run
            if ($LASTEXITCODE -ne 0) { throw 'Backend stopped with a non-zero exit code. Persistent data was not removed.' }
        } finally { Pop-Location }
    }
} finally {
    foreach ($key in $previousEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process') }
    $password = $null; $jwtSecret = $null; $provisionSql = $null; $environment = $null
    $stateLock.Dispose()
}
