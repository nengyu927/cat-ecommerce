param([ValidateSet('Prepare','VerifyAndClean')][string]$Mode, [int]$Port = 18081)
$ErrorActionPreference = 'Stop'
if ($Port -lt 1024 -or $Port -gt 65535 -or $Port -eq 8080) { throw 'Use a separate local verification port, not the live preview port.' }
$localRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../.local'))
$state = Import-Clixml -LiteralPath (Join-Path $localRoot 'preview-state.xml')
if ($state.Id -notmatch '^[a-f0-9]{32}$' -or $state.Database -cne ('catshop_persist_' + $state.Id)) { throw 'Invalid owned database.' }
$proofPath = Join-Path $localRoot 'restart-proof.xml'
$base = 'http://127.0.0.1:' + $Port + '/api'
function Request([string]$Method, [string]$Path, $Payload = $null) {
    $parameters = @{ Uri=$base+$Path; Method=$Method; Headers=$script:headers }
    if ($null -ne $Payload) { $parameters.ContentType='application/json'; $parameters.Body=ConvertTo-Json $Payload -Compress }
    $response = Invoke-RestMethod @parameters
    if (!$response.success) { throw ('API failed: ' + $Path) }
    return $response.data
}
function Sql([string]$Query) {
    $ownerId = $state.Id; $database = $state.Database
    $guard = "IF NOT EXISTS (SELECT 1 FROM sys.extended_properties WHERE class=0 AND name=N'CatshopPersistentPreview' AND CONVERT(nvarchar(128),value)=N'$ownerId') THROW 51000, 'Ownership mismatch', 1;"
    ($guard + "`n" + $Query + "`nGO`n") | & sqlcmd -S $state.Server -E -C -l 10 -b -d $database
    if ($LASTEXITCODE -ne 0) { throw 'Persistence proof SQL failed.' }
}
$script:headers = @{}
if ($Mode -eq 'Prepare') {
    if (Test-Path -LiteralPath $proofPath) { throw 'Existing proof retained. Verify and clean it before preparing another.' }
    $email = 'persist-' + [Guid]::NewGuid().ToString('N') + '@example.com'
    $registered = Request 'Post' '/auth/register' @{email=$email; password='persistence-test-123'; name='Persistence proof'; phone='0912345678'}
    $script:headers = @{ Authorization='Bearer '+$registered.token }
    $product = Request 'Get' '/products/1'
    $sku = $product.skus | Where-Object { $_.stock -gt 1 } | Select-Object -First 1
    if (!$sku) { throw 'Proof requires seeded product 1 with sufficient stock.' }
    $proof = [pscustomobject]@{
        Database=$state.Database; Email=$email; MemberId=[long]$registered.member.id; SkuId=[long]$sku.id
        Token=(ConvertTo-SecureString $registered.token -AsPlainText -Force)
        OriginalPrice=[decimal]$product.basePrice; ChangedPrice=([decimal]$product.basePrice + 1); OrderId=0L
    }
    # Save before mutations so a failed test can be inspected/recovered without losing fixture identity.
    $proof | Export-Clixml -LiteralPath $proofPath
    Request 'Put' '/wishlist/1' | Out-Null
    Request 'Post' '/cart/items' @{skuId=$proof.SkuId; quantity=1} | Out-Null
    $order = Request 'Post' '/orders' @{receiverName='Persistence proof'; receiverPhone='0912345678'; shippingAddress='Persistence test address'; paymentMethod='COD'}
    $proof.OrderId=[long]$order.id; $proof | Export-Clixml -LiteralPath $proofPath
    Request 'Post' '/cart/items' @{skuId=$proof.SkuId; quantity=2} | Out-Null
    $price = $proof.ChangedPrice.ToString([Globalization.CultureInfo]::InvariantCulture)
    Sql "UPDATE product SET base_price=$price WHERE id=1;"
    Write-Output 'PREPARE PASS: member, order, cart, wishlist, product price and encrypted pre-restart JWT prepared. Restart the backend with the same saved state.'
} elseif ($Mode -eq 'VerifyAndClean') {
    $proof = Import-Clixml -LiteralPath $proofPath
    if ($proof.Database -cne $state.Database -or $proof.Email -notmatch '^persist-[a-f0-9]{32}@example\.com$' -or $proof.MemberId -lt 1 -or $proof.OrderId -lt 1) { throw 'Invalid proof identity; inspect manually.' }
    $token = [Net.NetworkCredential]::new('', $proof.Token).Password
    $script:headers = @{Authorization='Bearer '+$token}
    $me = Request 'Get' '/members/me'; $order = Request 'Get' ('/orders/'+$proof.OrderId)
    $cart = Request 'Get' '/cart'; $ids = @(Request 'Get' '/wishlist/ids'); $product = Request 'Get' '/products/1'
    if ($me.id -ne $proof.MemberId -or $me.email -ne $proof.Email -or $order.id -ne $proof.OrderId -or $order.status -ne 'PENDING' -or $cart.totalQuantity -ne 2 -or $ids -notcontains 1 -or [decimal]$product.basePrice -ne $proof.ChangedPrice) { throw 'Restart persistence assertion failed; proof retained for inspection.' }
    Request 'Post' ('/orders/'+$proof.OrderId+'/cancel') | Out-Null
    $memberId = [long]$proof.MemberId; $orderId = [long]$proof.OrderId; $email = $proof.Email
    $originalPrice = $proof.OriginalPrice.ToString([Globalization.CultureInfo]::InvariantCulture)
    # Delete only this generated fixture. Never reset the persistent database or other members.
    Sql @"
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS (SELECT 1 FROM member WHERE id=$memberId AND email=N'$email') THROW 51000, 'Fixture ownership mismatch', 1;
IF NOT EXISTS (SELECT 1 FROM orders WHERE id=$orderId AND member_id=$memberId) THROW 51000, 'Order fixture ownership mismatch', 1;
DELETE FROM order_item WHERE order_id=$orderId;
DELETE FROM orders WHERE id=$orderId AND member_id=$memberId;
DELETE FROM cart_item WHERE cart_id IN (SELECT id FROM cart WHERE member_id=$memberId);
DELETE FROM cart WHERE member_id=$memberId;
DELETE FROM wishlist_item WHERE member_id=$memberId;
DELETE FROM auth_session WHERE member_id=$memberId;
DELETE FROM member WHERE id=$memberId AND email=N'$email';
UPDATE product SET base_price=$originalPrice WHERE id=1 AND base_price=$($proof.ChangedPrice.ToString([Globalization.CultureInfo]::InvariantCulture));
COMMIT;
"@
    Remove-Item -LiteralPath $proofPath
    $token = $null; $script:headers = @{}
    Write-Output 'RESTART PASS: member/order/cart/wishlist/product changes and pre-restart JWT retained. Only the generated proof member/order/cart/wishlist were removed; stock/price restored.'
} else { throw 'Specify -Mode Prepare or VerifyAndClean.' }
