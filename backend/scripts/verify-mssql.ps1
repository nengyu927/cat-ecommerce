param([string]$Server = '.\SQLEXPRESS')
$ErrorActionPreference = 'Stop'
$verificationDatabase = 'catshop_verify_' + [Guid]::NewGuid().ToString('N')
if ($verificationDatabase -notmatch '^catshop_verify_[a-f0-9]{32}$') { throw 'Invalid test database name' }
$created = $false
function Invoke-Sql([string]$Query, [string]$Database = 'master', [string]$File = '') {
    $arguments = @('-S', $Server, '-E', '-C', '-l', '10', '-b', '-W', '-w', '200', '-d', $Database)
    if ($File) { $arguments += @('-f', '65001', '-i', $File) } else { $arguments += @('-Q', $Query) }
    & sqlcmd @arguments
    if ($LASTEXITCODE -ne 0) { throw 'SQL Server verification failed' }
}
try {
    Invoke-Sql "SELECT CONVERT(nvarchar(50), SERVERPROPERTY('ProductVersion')) AS version, CONVERT(nvarchar(80), SERVERPROPERTY('Edition')) AS edition;"
    Invoke-Sql "CREATE DATABASE [$verificationDatabase];"
    $created = $true
    $migrationRoot = Join-Path $PSScriptRoot '..\src\main\resources\db\migration'
    Get-ChildItem -LiteralPath $migrationRoot -Filter 'V*.sql' | Sort-Object Name | ForEach-Object {
        Write-Output ('Apply ' + $_.Name)
        Invoke-Sql -Database $verificationDatabase -File $_.FullName -Query ''
    }
    Invoke-Sql -Database $verificationDatabase -Query @"
IF (SELECT COUNT(*) FROM sys.tables WHERE name IN ('member','address','category','product','product_image','product_sku','cart','cart_item','orders','order_item')) <> 10 THROW 51000, 'Expected 10 tables', 1;
IF (SELECT COUNT(*) FROM product) <> 10 THROW 51000, 'Expected 10 seed products', 1;
IF (SELECT COUNT(*) FROM product_sku) <> 13 THROW 51000, 'Expected 13 seed SKUs', 1;
IF COL_LENGTH('product_sku','version') IS NULL THROW 51000, 'Missing SKU version', 1;
IF COL_LENGTH('orders','version') IS NULL THROW 51000, 'Missing order version', 1;
IF COL_LENGTH('orders','payment_method') IS NULL THROW 51000, 'Missing payment method', 1;
IF COL_LENGTH('product','ingredients') IS NULL THROW 51000, 'Missing product information', 1;
IF OBJECT_ID('auth_session', 'U') IS NULL THROW 51000, 'Missing auth sessions', 1;
IF COL_LENGTH('auth_session','previous_hash') IS NULL THROW 51000, 'Missing rotation hash', 1;
IF NOT EXISTS (SELECT 1 FROM category WHERE name = NCHAR(39164) + NCHAR(26009)) THROW 51000, 'Unicode seed failed', 1;
BEGIN TRANSACTION;
UPDATE product_sku SET stock = stock - 1, version = version + 1 WHERE id = 1 AND version = 0;
IF @@ROWCOUNT <> 1 THROW 51000, 'First versioned update failed', 1;
UPDATE product_sku SET stock = stock - 1, version = version + 1 WHERE id = 1 AND version = 0;
IF @@ROWCOUNT <> 0 THROW 51000, 'Stale version update was not rejected', 1;
ROLLBACK;
BEGIN TRY
    UPDATE product_sku SET stock = -1 WHERE id = 1;
    THROW 51000, 'Negative stock constraint failed', 1;
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() <> 547 THROW;
END CATCH;
SELECT 'MSSQL migration and seed verification passed' AS result;
"@
} finally {
    if ($created) {
        Invoke-Sql "ALTER DATABASE [$verificationDatabase] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$verificationDatabase];"
        Write-Output 'Removed isolated verification database.'
    }
}
