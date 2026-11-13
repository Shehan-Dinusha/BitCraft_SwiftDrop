param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$script:outputDirectory = $PSScriptRoot

function Start-Diagram([string]$Name, [int]$Width, [int]$Height) {
    $script:diagramName = $Name
    $script:bitmap = New-Object System.Drawing.Bitmap($Width, $Height)
    $script:graphics = [System.Drawing.Graphics]::FromImage($script:bitmap)
    $script:graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $script:graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $script:graphics.Clear([System.Drawing.Color]::White)
    $script:svg = [System.Collections.Generic.List[string]]::new()
    $script:svg.Add("<svg xmlns='http://www.w3.org/2000/svg' width='$Width' height='$Height' viewBox='0 0 $Width $Height'><rect width='100%' height='100%' fill='white'/>")
}
function Text([float]$X, [float]$Y, [string]$Value, [int]$Size = 20, [string]$Color = '#16324f') {
    $font = New-Object System.Drawing.Font('Segoe UI', $Size, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $brush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml($Color))
    $lines = $Value -split "`n"
    for ($i = 0; $i -lt $lines.Length; $i++) {
        $lineY = $Y + $i * ($Size + 7)
        $script:graphics.DrawString($lines[$i], $font, $brush, $X, $lineY)
        $escaped = [System.Security.SecurityElement]::Escape($lines[$i])
        $baseline = $lineY + $Size
        $script:svg.Add("<text x='$X' y='$baseline' font-family='Segoe UI,Arial,sans-serif' font-size='$Size' fill='$Color'>$escaped</text>")
    }
    $font.Dispose(); $brush.Dispose()
}
function Box([float]$X, [float]$Y, [float]$W, [float]$H, [string]$Label, [string]$Fill = '#edf4fc') {
    $brush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml($Fill))
    $pen = New-Object System.Drawing.Pen([System.Drawing.ColorTranslator]::FromHtml('#adc3db'), 2)
    $script:graphics.FillRectangle($brush, $X, $Y, $W, $H)
    $script:graphics.DrawRectangle($pen, $X, $Y, $W, $H)
    $script:svg.Add("<rect x='$X' y='$Y' width='$W' height='$H' rx='4' fill='$Fill' stroke='#adc3db' stroke-width='2'/>")
    Text ($X + 14) ($Y + 12) $Label 20
    $brush.Dispose(); $pen.Dispose()
}
function Line([float]$X1, [float]$Y1, [float]$X2, [float]$Y2, [bool]$Arrow = $true) {
    $pen = New-Object System.Drawing.Pen([System.Drawing.ColorTranslator]::FromHtml('#507298'), 2)
    $script:graphics.DrawLine($pen, $X1, $Y1, $X2, $Y2)
    $script:svg.Add("<line x1='$X1' y1='$Y1' x2='$X2' y2='$Y2' stroke='#507298' stroke-width='2'/>")
    if ($Arrow) {
        $angle = [Math]::Atan2($Y2 - $Y1, $X2 - $X1)
        foreach ($offset in @(-0.5, 0.5)) {
            $ax = $X2 - 12 * [Math]::Cos($angle + $offset)
            $ay = $Y2 - 12 * [Math]::Sin($angle + $offset)
            $script:graphics.DrawLine($pen, $X2, $Y2, [float]$ax, [float]$ay)
            $script:svg.Add("<line x1='$X2' y1='$Y2' x2='$ax' y2='$ay' stroke='#507298' stroke-width='2'/>")
        }
    }
    $pen.Dispose()
}
function Save-Diagram {
    $script:svg.Add('</svg>')
    [System.IO.File]::WriteAllLines((Join-Path $script:outputDirectory "$script:diagramName.svg"), $script:svg)
    $script:bitmap.Save((Join-Path $script:outputDirectory "$script:diagramName.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $script:graphics.Dispose(); $script:bitmap.Dispose()
}

Start-Diagram 'b2b-order-ingestion' 1800 1510
Text 40 25 'SwiftDrop | Member 7' 20 '#507298'
Text 40 60 'B2B order ingestion' 36
Text 40 112 'Integration contract: Member 1 must enable B2B gateway routing and API-key/JWT filter coordination.' 22 '#9c4b15'
$positions = @(150, 450, 750, 1050, 1350, 1650)
$labels = @("E-commerce`nplatform", "Gateway`nMember 1", "Merchant service`nMember 7", "Order service`nMember 3", "RabbitMQ`nevents", "Dispatch`nMember 5")
for ($i = 0; $i -lt $positions.Length; $i++) {
    Box ($positions[$i] - 115) 170 230 80 $labels[$i]
    Line $positions[$i] 250 $positions[$i] 1290 $false
}
function Message([int]$From, [int]$To, [int]$Y, [string]$Label) {
    $x = [Math]::Min($positions[$From], $positions[$To]) + 12
    Text $x ($Y - 31) $Label 20
    Line $positions[$From] $Y $positions[$To] $Y
}
Message 0 1 300 '1. POST /api/v1/b2b/orders + X-API-Key'
Message 1 2 375 '2. Internal key validation (cache miss)'
Message 2 1 450 '3. Key hash/expiry + approval check; verified identity'
Box 40 478 1720 57 'Invalid/missing key: gateway returns 401. Redis validation cache may delay revocation enforcement.' '#fff5e9'
Message 1 2 595 '4. Verified merchant/role headers + correlation ID'
Box 620 620 700 88 "5. Merchant DB: lock merchant; require APPROVED (else 403).`nNormalize payload; lookup merchantId + externalRef receipt."
Box 40 735 1720 57 'Receipt found: return existing IDs (HTTP 200); skip order-service call. Invalid payload: reject.' '#f0f8f4'
Message 2 3 855 '6. POST /internal/v1/orders (new local reference)'
Box 920 880 800 86 "7. Order DB: idempotent order creation; pricing + tracking number.`nExisting downstream order: return it without another creation event."
Message 3 4 1025 '8. order.created (new order only)'
Message 4 5 1095 '9. Event delivered to dispatch'
Message 3 2 1165 '10. New/existing orderId + trackingNumber'
Message 2 0 1250 '11. Save merchant receipt; respond via gateway (201 new / 200 existing)'
Box 40 1320 1720 135 "Each service owns its own database. Remote order commit and local receipt commit are separate.`nRetry protection exists at both services. Merchant ingestion does not call its legacy order.created publisher.`nBulk: 1-500 items, independent item results; correlation ID is preserved. See Mermaid source for branches." '#edf4fc'
Save-Diagram

Start-Diagram 'merchant-verification-workflow' 1500 1530
Text 40 25 'SwiftDrop | Member 7' 20 '#507298'
Text 40 60 'Merchant verification workflow' 36
Text 40 115 'Registration and approval are authenticated; API keys and ingestion require APPROVED.' 22
Box 460 170 580 78 "MERCHANT: submit /merchant/register`nBusiness details; owner from gateway user ID"
Line 750 248 750 280
Box 460 280 580 78 "Persist PENDING_APPROVAL`nKeys and order ingestion blocked" '#fff5e9'
Line 750 358 750 390
Box 460 390 580 78 "SYSTEM_ADMIN: review /ops/merchants`nApprove/reject pending applications only"
Box 45 385 365 105 "Gateway dependency`nAdmin merchant routes still required;`nnon-admin admin calls return 403." '#fff5e9'
Line 750 468 750 510 $false
Line 750 510 390 510 $false
Line 390 510 390 555
Line 750 510 1110 510 $false
Line 1110 510 1110 555
Text 220 518 'APPROVE: owner required' 20
Text 930 518 'REJECT: reason required' 20
Box 100 555 580 88 "APPROVED`nSave approvedBy and approvedAt; clear rejection" '#f0f8f4'
Box 820 555 580 88 "REJECTED`nSave rejection reason; clear approval audit" '#fff1f1'
Line 390 643 390 690
Line 1110 643 1110 690
Box 100 690 580 78 "After commit: publish merchant.approved`nmerchantId + ownerUserId + event metadata"
Box 820 690 580 78 "Keys and order ingestion remain blocked`nNo rejection-to-approval action in this controller" '#fff1f1'
Line 390 768 390 815
Box 100 815 580 78 "IAM consumes approval event`nLinks merchantId to owner user"
Line 390 893 390 940
Box 100 940 580 78 "Merchant signs in again`nRefresh verified merchant claim/profile"
Line 390 1018 390 1065
Box 100 1065 580 78 "Generate API key`nSecret shown once; SHA-256 hash stored"
Line 390 1143 390 1190
Box 100 1190 580 78 "Create B2B orders and view dashboard`nTracking number and public delivery status" '#f0f8f4'
Box 820 855 580 110 "SUSPENDED is a blocked enum value.`nNo suspend endpoint is implemented`nin the inspected approval workflow." '#fff5e9'
Box 820 1010 580 150 "Broker publication is AFTER_COMMIT.`nThis is not a durable outbox guarantee.`nCheck IAM event consumption and token`nrefresh during live integration testing." '#edf4fc'
Box 45 1330 1355 125 "Review scope: Member 7 merchant lifecycle; gateway and IAM remain their owning members' modules.`nApprove/reject UI and copied API examples require gateway integration before the demo can pass.`nRendered diagrams describe the contract, not a claim that the live distributed flow was verified." '#edf4fc'
Save-Diagram
Write-Output 'Rendered both Member 7 diagrams as PNG and SVG.'
