param(
    [Parameter(Mandatory = $true)][string]$Endpoint,
    [Parameter(Mandatory = $true)][string]$DeploymentName
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$sessionPath = Join-Path $projectRoot 'backend/analytics-ai-service/target-ai/frontend-demo/session.json'
$session = Get-Content -LiteralPath $sessionPath | ConvertFrom-Json
$endpointUri = [Uri]$Endpoint
if ($endpointUri.Scheme -ne 'https' -or $endpointUri.AbsolutePath.TrimEnd('/') -ne '/openai/v1') {
    throw 'Use the Azure HTTPS base endpoint ending in /openai/v1, without /chat/completions.'
}

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

$modelKey = Read-Host 'Azure model API key (kept in this backend process only)' -AsSecureString
$env:AI_PROVIDER = 'azure-openai'
$env:AI_MODEL = $DeploymentName
$env:AI_ENDPOINT = $Endpoint.TrimEnd('/')
$env:AI_API_KEY = [System.Net.NetworkCredential]::new('', $modelKey).Password
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
$demoApp = $session.processes | Where-Object { $_.service -eq 'analytics-ai-service' }
$jar = if ($demoApp.jar) { $demoApp.jar } else {
    @('target-ai-customer', 'target-ai', 'target') | ForEach-Object {
        Join-Path $projectRoot ('backend/analytics-ai-service/' + $_ + '/analytics-ai-service-0.1.0-SNAPSHOT.jar')
    } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
$demoProcess = Get-CimInstance Win32_Process -Filter ('ProcessId = ' + $demoApp.pid)
if ($demoProcess) {
    if ($demoProcess.CommandLine -notlike '*BitCraft_SwiftDrop*analytics-ai-service-0.1.0-SNAPSHOT.jar*') {
        throw 'Saved process ID no longer belongs to the demo analytics service. Stop its current process manually.'
    }
    Stop-Process -Id $demoApp.pid
    Start-Sleep -Milliseconds 700
}

Write-Output 'Starting analytics with Azure. Keep this terminal running; Ctrl+C stops this instance.'
Write-Output 'Your browser, IAM, gateway, tracking and pricing processes stay running.'
try {
    & java -Xmx256m -jar $jar --server.port=8089
} finally {
    Remove-Item Env:AI_API_KEY -ErrorAction SilentlyContinue
}
