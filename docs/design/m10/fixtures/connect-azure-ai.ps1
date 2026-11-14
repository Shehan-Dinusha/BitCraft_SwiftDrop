param(
    [Parameter(Mandatory = $true)][string]$Endpoint,
    [Parameter(Mandatory = $true)][string]$DeploymentName,
    # Skips the pre-flight request to Azure. Use only if the pre-flight itself is blocked by a proxy.
    [switch]$SkipPreflight
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$sessionPath = Join-Path $projectRoot 'backend/analytics-ai-service/target-ai/frontend-demo/session.json'
if (-not (Test-Path -LiteralPath $sessionPath)) {
    throw "No demo session at $sessionPath. Start the local demo first (see stages-6-to-11-ai-assistant.md)."
}
$session = Get-Content -LiteralPath $sessionPath -Raw | ConvertFrom-Json

$endpointUri = [Uri]$Endpoint
if ($endpointUri.Scheme -ne 'https' -or $endpointUri.AbsolutePath.TrimEnd('/') -ne '/openai/v1') {
    throw 'Use the Azure HTTPS base endpoint ending in /openai/v1, without /chat/completions.'
}
$baseEndpoint = $Endpoint.TrimEnd('/')

$demoSettings = @{}
$environmentFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $environmentFile) {
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            $demoSettings[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
        }
    }
}
function Get-DemoSetting([string]$Name, [string]$Default) {
    if ($demoSettings.ContainsKey($Name)) { return $demoSettings[$Name] }
    return $Default
}

# Resolve the JAR before stopping anything, so a missing build cannot take the demo down.
$demoApp = $session.processes | Where-Object { $_.service -eq 'analytics-ai-service' } | Select-Object -First 1
$jar = if ($demoApp -and $demoApp.jar) { $demoApp.jar } else {
    @('target-ai-customer', 'target-ai', 'target') | ForEach-Object {
        Join-Path $projectRoot ('backend/analytics-ai-service/' + $_ + '/analytics-ai-service-0.1.0-SNAPSHOT.jar')
    } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $jar -or -not (Test-Path -LiteralPath $jar)) {
    throw 'Analytics JAR not found. Build or start the local demo first.'
}

$modelKey = Read-Host 'Azure model API key (kept in this backend process only)' -AsSecureString
$plainKey = [System.Net.NetworkCredential]::new('', $modelKey).Password
if ([string]::IsNullOrWhiteSpace($plainKey)) { throw 'No API key entered.' }

# Pre-flight: one tiny request that must return a tool call. Proves endpoint, key,
# deployment name, networking and tool-calling support before the running service is touched.
if (-not $SkipPreflight) {
    Write-Output 'Pre-flight: testing Azure endpoint, key, deployment and tool calling...'
    $body = @{
        model                 = $DeploymentName
        messages              = @(@{ role = 'user'; content = 'Call the ping tool.' })
        tools                 = @(@{
            type     = 'function'
            function = @{
                name        = 'ping'
                description = 'Connectivity check.'
                parameters  = @{ type = 'object'; properties = @{}; additionalProperties = $false }
            }
        })
        tool_choice           = 'required'
        max_completion_tokens = 64
    } | ConvertTo-Json -Depth 10
    try {
        $reply = Invoke-RestMethod -Method Post -Uri ($baseEndpoint + '/chat/completions') `
            -Headers @{ 'api-key' = $plainKey } -ContentType 'application/json' -Body $body -TimeoutSec 60
    } catch {
        $status = $null
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        $hint = switch ($status) {
            401 { 'Key does not belong to this resource.' }
            403 { 'Check resource networking and that API-key auth is enabled.' }
            404 { 'Check the deployment name and that the endpoint ends in /openai/v1.' }
            429 { 'Rate limited. Wait a minute or raise deployment capacity.' }
            400 { 'Request rejected. The model may not support tool calling or these parameters.' }
            default { 'See the error above.' }
        }
        $plainKey = $null
        throw "Pre-flight failed (HTTP $status). $hint Your running analytics service was NOT changed."
    }
    $toolCalls = $reply.choices[0].message.tool_calls
    if (-not $toolCalls) {
        $plainKey = $null
        throw 'Pre-flight reached Azure but the model returned no tool call. Choose a model with function calling. Your running analytics service was NOT changed.'
    }
    Write-Output ('Pre-flight passed: model ' + $reply.model + ' answered with a tool call. Tokens used: ' + $reply.usage.total_tokens)
}

$env:AI_PROVIDER = 'azure-openai'
$env:AI_MODEL = $DeploymentName
$env:AI_ENDPOINT = $baseEndpoint
$env:AI_API_KEY = $plainKey
$plainKey = $null
$env:GATEWAY_URL = 'http://localhost:8080'
$env:DB_URL = 'jdbc:postgresql://localhost:5440/swiftdrop_analytics'
$env:DB_USERNAME = Get-DemoSetting 'POSTGRES_USER' 'swiftdrop'
$env:DB_PASSWORD = Get-DemoSetting 'POSTGRES_PASSWORD' 'changeme_pg'
$env:RABBITMQ_HOST = 'localhost'
$env:RABBITMQ_PORT = '5672'
$env:RABBITMQ_USERNAME = Get-DemoSetting 'RABBITMQ_DEFAULT_USER' 'swiftdrop'
$env:RABBITMQ_PASSWORD = Get-DemoSetting 'RABBITMQ_DEFAULT_PASS' 'changeme_rabbit'
$env:SPRING_RABBITMQ_VIRTUAL_HOST = $session.rabbitVirtualHost
$env:SPRING_PROFILES_ACTIVE = ''

if ($demoApp -and $demoApp.pid) {
    $demoProcess = Get-CimInstance Win32_Process -Filter ('ProcessId = ' + [int]$demoApp.pid)
    if ($demoProcess) {
        if ($demoProcess.CommandLine -notlike '*BitCraft_SwiftDrop*analytics-ai-service-0.1.0-SNAPSHOT.jar*') {
            Remove-Item Env:AI_API_KEY -ErrorAction SilentlyContinue
            throw 'Saved process ID no longer belongs to the demo analytics service. Stop its current process manually.'
        }
        Stop-Process -Id $demoApp.pid
        Start-Sleep -Milliseconds 700
    }
}

Write-Output 'Starting analytics with Azure. Keep this terminal running; Ctrl+C stops this instance.'
Write-Output 'Your browser, IAM, gateway, tracking and pricing processes stay running.'
Write-Output 'After the demo: see "Stop or reduce cost" in stage-7-azure-verification.md.'
try {
    & java -Xmx256m -jar $jar --server.port=8089
} finally {
    Remove-Item Env:AI_API_KEY -ErrorAction SilentlyContinue
}
