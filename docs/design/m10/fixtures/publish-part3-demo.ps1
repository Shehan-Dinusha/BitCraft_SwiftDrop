param(
    [ValidatePattern('^m10-part3-[a-z0-9-]+$')]
    [string]$VirtualHost = 'm10-part3-demo',
    [datetime]$ReportDate = ([DateTime]::UtcNow.AddMinutes(330).Date),
    [string]$BrokerUrl = 'http://localhost:15672',
    [pscredential]$Credential
)

$ErrorActionPreference = 'Stop'
if ($null -eq $Credential) {
    $Credential = Get-Credential -UserName swiftdrop -Message 'Local RabbitMQ demo credentials'
}
$dateKey = $ReportDate.ToString('yyyy-MM-dd')
$publishUrl = $BrokerUrl.TrimEnd('/') + '/api/exchanges/' + [uri]::EscapeDataString($VirtualHost) + '/swiftdrop.events.exchange/publish'

function Get-DemoId([string]$Name) {
    $hasher = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = $hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes('m10-part3|' + $dateKey + '|' + $Name))
        return [guid]::new([byte[]]$bytes[0..15]).ToString()
    } finally { $hasher.Dispose() }
}

function Get-DemoTime([int]$Hour, [int]$Minute = 0) {
    $localTime = [DateTimeOffset]::new($ReportDate.Year, $ReportDate.Month, $ReportDate.Day,
        $Hour, $Minute, 0, [TimeSpan]::FromMinutes(330))
    return $localTime.UtcDateTime.ToString('o')
}

function Send-DemoEvent([string]$Key, [string]$Name, [string]$Order, [hashtable]$Fields) {
    $event = @{
        eventId = Get-DemoId $Name
        correlationId = Get-DemoId ('trace-' + $Order)
        orderId = Get-DemoId ('order-' + $Order)
    }
    foreach ($field in $Fields.Keys) { $event[$field] = $Fields[$field] }
    $request = @{
        properties = @{ content_type = 'application/json' }
        routing_key = $Key
        payload = ($event | ConvertTo-Json -Compress)
        payload_encoding = 'string'
    } | ConvertTo-Json -Depth 6
    $response = Invoke-RestMethod -Method Post -Uri $publishUrl -Credential $Credential -ContentType 'application/json' -Body $request
    if (-not $response.routed) { throw "Event $Name was not routed. Check the demo vhost and analytics application." }
}

foreach ($order in @('A','B','C','D')) {
    Send-DemoEvent 'order.created' ('created-' + $order) $order @{
        timestamp = Get-DemoTime 8
        trackingNumber = 'SD-M10-DEMO-' + $order
        pickupDistrict = 'COLOMBO'
        deliveryDistrict = 'KANDY'
    }
    $courier = if ($order -eq 'C') { 'C2' } else { 'C1' }
    Send-DemoEvent 'dispatch.courier.assigned' ('assigned-' + $order) $order @{
        timestamp = Get-DemoTime 8 10
        assignedAt = Get-DemoTime 8 10
        courierId = Get-DemoId $courier
        courierName = 'Synthetic Demo ' + $courier
        estimatedPickupMinutes = 10
    }
}
Send-DemoEvent 'dispatch.courier.assigned' 'reassigned-D' 'D' @{
    timestamp = Get-DemoTime 8 20
    assignedAt = Get-DemoTime 8 20
    courierId = Get-DemoId 'C2'
    courierName = 'Synthetic Demo C2'
    estimatedPickupMinutes = 10
}
foreach ($order in @('A','B','C','D')) {
    $state = switch ($order) { 'A' { 'DELIVERED' }; 'B' { 'DELIVERED' }; 'C' { 'FAILED' }; 'D' { 'IN_TRANSIT' } }
    $hour = switch ($order) { 'A' { 9 }; 'B' { 10 }; 'C' { 11 }; 'D' { 9 } }
    Send-DemoEvent 'parcel.status.changed' ('status-' + $order) $order @{
        timestamp = Get-DemoTime $hour
        trackingNumber = 'SD-M10-DEMO-' + $order
        newPublicStatus = $state
        district = 'KANDY'
    }
}
# Different event ID for the same delivered fact, followed by an older status.
Send-DemoEvent 'parcel.status.changed' 'A-delivered-again' 'A' @{
    timestamp = Get-DemoTime 9
    trackingNumber = 'SD-M10-DEMO-A'
    newPublicStatus = 'DELIVERED'
    district = 'KANDY'
}
Send-DemoEvent 'parcel.status.changed' 'A-delayed-transit' 'A' @{
    timestamp = Get-DemoTime 8 30
    trackingNumber = 'SD-M10-DEMO-A'
    newPublicStatus = 'IN_TRANSIT'
    district = 'COLOMBO'
}
Write-Output "Published 15 synthetic events for $dateKey. Re-running for this date reuses the same event IDs."
Write-Output ('C1: ' + (Get-DemoId 'C1'))
Write-Output ('C2: ' + (Get-DemoId 'C2'))
