#Requires -Version 7.0
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Username = 'admin',
    [string]$Password = 'admin123',
    [string]$Sku = 'SKU-DEMO-001'
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

function Invoke-Check {
    param([string]$Method, [string]$Path, [string]$Token = '', [object]$Body = $null,
          [int]$ExpectedStatus = 200, [string]$ExpectedCode = '0')
    $parameters = @{ Uri = "$BaseUrl$Path"; Method = $Method; TimeoutSec = 15; SkipHttpErrorCheck = $true }
    if ($Token) { $parameters.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = ConvertTo-Json -InputObject $Body -Compress
    }
    $response = Invoke-WebRequest @parameters
    if ([int]$response.StatusCode -ne $ExpectedStatus) {
        throw "$Method $Path expected HTTP $ExpectedStatus, received $($response.StatusCode): $($response.Content)"
    }
    if ($ExpectedCode -eq '') { return $null }
    $payload = $response.Content | ConvertFrom-Json
    if ($payload.code -ne $ExpectedCode) { throw "$Method $Path returned unexpected code $($payload.code)" }
    return $payload.data
}

# This script creates demo purchase and sales orders; it does not delete existing data.
$null = Invoke-Check GET '/api/v1/inventory/sku' -ExpectedStatus 401 -ExpectedCode ''
$login = Invoke-Check POST '/api/v1/auth/login' -Body @{ username = $Username; password = $Password }
$token = $login.accessToken
if (-not $token) { throw 'Login returned no access token' }
$before = Invoke-Check GET "/api/v1/inventory/$Sku" -Token $token
if ($null -eq $before) { throw "Seed inventory $Sku is missing" }
if ($before.availableQuantity -lt 2) { throw 'At least two available units are required for this smoke run' }

$null = Invoke-Check POST '/api/v1/orders' -Token $token -Body @{ sku = $Sku } -ExpectedStatus 400 -ExpectedCode '400'
$order = Invoke-Check POST '/api/v1/orders' -Token $token -Body @{ sku = $Sku; quantity = 2 }
$paid = Invoke-Check POST "/api/v1/orders/$($order.orderId)/pay" -Token $token
$paidAgain = Invoke-Check POST "/api/v1/orders/$($order.orderId)/pay" -Token $token
if ($paid.paymentStatus -ne 'PAID' -or $paid.fulfillmentStatus -ne 'FULFILLING' -or
        $paidAgain.fulfillmentStatus -ne 'FULFILLING') { throw 'Payment/fulfillment state mismatch' }
$afterPayment = Invoke-Check GET "/api/v1/inventory/$Sku" -Token $token
if ($afterPayment.availableQuantity -ne $before.availableQuantity - 2 -or
        $afterPayment.reservedQuantity -ne $before.reservedQuantity + 2) { throw 'Payment retry changed stock more than once' }
$null = Invoke-Check POST '/api/v1/inventory/reservations' -Token $token -Body @{
    orderId = $order.orderId; sku = $Sku; quantity = 3
} -ExpectedStatus 409 -ExpectedCode 'INV-409'

$purchase = Invoke-Check POST '/api/v1/purchases' -Token $token -Body @{
    supplierCode = 'SMOKE-SUPPLIER'; sku = $Sku; quantity = 5
}
if (-not $purchase.id) { throw 'Purchase returned no generated id' }
$received = Invoke-Check POST "/api/v1/purchases/$($purchase.id)/receive" -Token $token
$receivedAgain = Invoke-Check POST "/api/v1/purchases/$($purchase.id)/receive" -Token $token
if ($received.status -ne 'RECEIVED' -or $receivedAgain.status -ne 'RECEIVED') { throw 'Receipt state mismatch' }
$null = Invoke-Check POST '/api/v1/inventory/receipts' -Token $token -Body @{
    purchaseId = $purchase.id; sku = $Sku; quantity = 6
} -ExpectedStatus 409 -ExpectedCode 'INV-409'
$afterReceipt = Invoke-Check GET "/api/v1/inventory/$Sku" -Token $token
if ($afterReceipt.availableQuantity -ne $afterPayment.availableQuantity + 5 -or
        $afterReceipt.reservedQuantity -ne $afterPayment.reservedQuantity) { throw 'Receipt retry changed stock more than once' }

$null = Invoke-Check POST '/api/v1/auth/logout' -Token $token
$null = Invoke-Check GET "/api/v1/inventory/$Sku" -Token $token -ExpectedStatus 401 -ExpectedCode ''
$freshLogin = Invoke-Check POST '/api/v1/auth/login' -Body @{ username = $Username; password = $Password }
$null = Invoke-Check GET "/api/v1/inventory/$Sku" -Token $freshLogin.accessToken
$null = Invoke-Check POST '/api/v1/auth/logout' -Token $freshLogin.accessToken

[pscustomobject]@{
    Result = 'PASS'
    OrderId = $order.orderId
    PurchaseId = $purchase.id
    Payment = $paid.paymentStatus
    Fulfillment = $paid.fulfillmentStatus
    AvailableBefore = $before.availableQuantity
    AvailableAfter = $afterReceipt.availableQuantity
    ReservedAfter = $afterReceipt.reservedQuantity
    LogoutRejectsOldToken = $true
}
