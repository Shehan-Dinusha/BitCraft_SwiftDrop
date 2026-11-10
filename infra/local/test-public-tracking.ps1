param(
    [string]$TrackingNumber,
    [switch]$CreateOrder,
    [string]$Gateway = 'http://localhost:8080',
    [string]$OrderService = 'http://localhost:8082',
    [string]$RabbitApi = 'http://localhost:15672',
    [string]$RabbitUser = 'swiftdrop',
    [string]$RabbitPassword = 'changeme_rabbit'
)

$ErrorActionPreference = 'Stop'
$orderId = [Guid]::NewGuid().ToString()
$customerId = [Guid]::NewGuid().ToString()

if ($CreateOrder) {
    $payload = @{
        merchantId = [Guid]::NewGuid().ToString()
        externalRef = "TEST-$(Get-Date -Format 'yyyyMMddHHmmssfff')"
        pickupAddressLine1 = 'No. 42, Galle Road'
        pickupCity = 'Colombo 03'
        pickupDistrict = 'COLOMBO'
        pickupPostalCode = '00300'
        pickupContactName = 'Nimal Perera'
        pickupContactPhone = '+94771234567'
        deliveryAddressLine1 = 'No. 18, Peradeniya Road'
        deliveryCity = 'Kandy'
        deliveryDistrict = 'KANDY'
        deliveryPostalCode = '20000'
        recipientName = 'Kasun Silva'
        recipientPhone = '0719876543'
        actualWeightKg = 2.4
        speed = 'STANDARD'
        itemDescription = 'Test parcel'
        prohibitedItemsDeclared = $true
    } | ConvertTo-Json

    $created = Invoke-RestMethod -Method Post -Uri "$OrderService/internal/v1/orders" `
        -ContentType 'application/json' -Body $payload
    $TrackingNumber = $created.data.trackingNumber
    $orderId = $created.data.orderId
    $customerId = $created.data.customerId
    Write-Host "Created order $orderId with tracking $TrackingNumber"
}

if (-not $TrackingNumber) {
    $TrackingNumber = "SD-TEST-$(Get-Random -Minimum 1000 -Maximum 9999)"
    Write-Warning "Event-only test: $TrackingNumber has no order row, so masked contacts will be null."
}

$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${RabbitUser}:${RabbitPassword}"))
$subStatusMap = @{
    'PICKUP_ASSIGNED' = 'PICKUP_ASSIGNED'
    'PICKED_UP' = 'PICKED_UP'
    'IN_TRANSIT' = 'AT_HUB'
    'OUT_FOR_DELIVERY' = 'OUT_FOR_DELIVERY'
    'DELIVERED' = 'DELIVERED'
}

function Send-StatusEvent {
    param([string]$Previous, [string]$Next)

    $msg = @{
        eventId = [Guid]::NewGuid().ToString()
        correlationId = [Guid]::NewGuid().ToString()
        trackingNumber = $TrackingNumber
        orderId = $orderId
        customerId = $customerId
        previousPublicStatus = $Previous
        newPublicStatus = $Next
        internalSubStatus = $subStatusMap[$Next]
        district = 'COLOMBO'
        destinationDistrict = 'KANDY'
        failureReason = $null
        timestamp = [DateTime]::UtcNow.ToString('o')
    }
    $payload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($msg | ConvertTo-Json -Compress)))
    $body = @{
        properties = @{}
        routing_key = "parcel.status.changed.$TrackingNumber"
        payload = $payload
        payload_encoding = 'base64'
    } | ConvertTo-Json -Depth 10

    $res = Invoke-RestMethod -Method Post `
        -Uri "$RabbitApi/api/exchanges/%2f/swiftdrop.events.exchange/publish" `
        -Headers @{ Authorization = "Basic $basic"; 'Content-Type' = 'application/json' } `
        -Body $body

    Write-Host ("  {0,-18} routed={1}" -f $Next, $res.routed)
    if (-not $res.routed) {
        Write-Warning 'No queue received the event. Is the gateway running and bound to parcel.status.changed.#?'
    }
}

@(
    @('CREATED', 'PICKUP_ASSIGNED'),
    @('PICKUP_ASSIGNED', 'PICKED_UP'),
    @('PICKED_UP', 'IN_TRANSIT'),
    @('IN_TRANSIT', 'OUT_FOR_DELIVERY'),
    @('OUT_FOR_DELIVERY', 'DELIVERED')
) | ForEach-Object {
    Send-StatusEvent -Previous $_[0] -Next $_[1]
    Start-Sleep -Milliseconds 800
}

$view = $null
for ($i = 0; $i -lt 10 -and -not $view; $i++) {
    try { $view = Invoke-RestMethod -Uri "$Gateway/api/v1/public/track/$TrackingNumber" } catch { Start-Sleep -Milliseconds 500 }
}
if (-not $view) { throw 'Tracking view was not available after 5 seconds' }

Write-Host "`nGateway response:"
$view | ConvertTo-Json -Depth 10

Write-Host "`nMasked fields:"
"  originCity          = $($view.originCity)"
"  destinationCity     = $($view.destinationCity)"
"  senderNameMasked    = $($view.senderNameMasked)"
"  senderPhoneMasked   = $($view.senderPhoneMasked)"
"  receiverNameMasked  = $($view.receiverNameMasked)"
"  receiverPhoneMasked = $($view.receiverPhoneMasked)"

Write-Host "`nFrontend: http://localhost:5173/track/$TrackingNumber"
