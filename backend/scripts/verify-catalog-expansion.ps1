param([string]$Server = '.\SQLEXPRESS')
$ErrorActionPreference = 'Stop'
$backendRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$suffix = [Guid]::NewGuid().ToString('N')
$database = 'catshop_catalog_it_' + $suffix
if ($database -notmatch '^catshop_catalog_it_[a-f0-9]{32}$') { throw 'Invalid isolated database name.' }
function Invoke-CatalogSql([string]$Query) {
    $Query | & sqlcmd -S $Server -E -No -I -l 10 -b -W -w 120 -f 65001
    if ($LASTEXITCODE -ne 0) { throw 'Catalog SQL verification failed.' }
}
$created = $false
try {
    Invoke-CatalogSql "CREATE DATABASE [$database]; EXEC [$database].sys.sp_addextendedproperty @name=N'CatshopCatalogVerification', @value=N'$suffix';"
    $created = $true
    $migrations = Get-ChildItem -LiteralPath (Join-Path $backendRoot 'src/main/resources/db/migration') -Filter 'V*__*.sql' | Sort-Object { [int]($_.BaseName.Split('__')[0].Substring(1)) }
    $parts = @("USE [$database];", 'GO', 'SET NOCOUNT ON; SET XACT_ABORT ON;')
    foreach ($migration in $migrations) { $parts += [IO.File]::ReadAllText($migration.FullName, [Text.Encoding]::UTF8); $parts += 'GO' }
    # Replaying the new migration must preserve existing rows and avoid duplicates.
    $parts += [IO.File]::ReadAllText((Join-Path $backendRoot 'src/main/resources/db/migration/V14__expanded_lifestyle_catalog.sql'), [Text.Encoding]::UTF8)
    $parts += 'GO'
    $parts += @"
IF (SELECT COUNT(*) FROM product) <> 34 THROW 51000, 'Expected 34 products after replay', 1;
IF (SELECT COUNT(*) FROM category) <> 15 THROW 51000, 'Expected 15 categories after replay', 1;
IF (SELECT COUNT(*) FROM category WHERE parent_id IS NULL) <> 10 THROW 51000, 'Expected 10 root categories', 1;
IF (SELECT COUNT(*) FROM product_sku) <> 37 THROW 51000, 'Expected 37 purchasable SKUs', 1;
IF (SELECT COUNT(*) FROM product_image WHERE is_primary=1) <> 34 THROW 51000, 'Expected one primary image per product', 1;
IF EXISTS(SELECT 1 FROM product p WHERE NOT EXISTS(SELECT 1 FROM product_sku s WHERE s.product_id=p.id AND s.stock>0 AND s.is_active=1)) THROW 51000, 'Product missing purchasable SKU', 1;
IF NOT EXISTS(SELECT 1 FROM product WHERE slug=N'royal-canin-kitten-dry' AND base_price=890) THROW 51000, 'Original product was changed', 1;
IF (SELECT COUNT(*) FROM member) <> 2 THROW 51000, 'Original members were changed', 1;
SELECT COUNT(*) AS verified_products FROM product;
SELECT COUNT(*) AS verified_categories FROM category;
SELECT COUNT(*) AS verified_skus FROM product_sku;
"@
    Invoke-CatalogSql ($parts -join "`n")
    Write-Output 'PASS catalog migration, complete SKU/image coverage, existing data preservation and replay.'
} finally {
    if ($created) {
        Invoke-CatalogSql @"
IF NOT EXISTS(SELECT 1 FROM [$database].sys.extended_properties WHERE class=0 AND name=N'CatshopCatalogVerification' AND CONVERT(nvarchar(128),value)=N'$suffix') THROW 51000, 'Ownership mismatch; refusing cleanup', 1;
ALTER DATABASE [$database] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
DROP DATABASE [$database];
"@
        Write-Output 'Removed owned isolated catalog verification database.'
    }
}
