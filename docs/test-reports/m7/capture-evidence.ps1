param(
    [string]$VerificationCommand = ''
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$moduleRoot = Join-Path $repoRoot 'backend/merchant-b2b-service'
$testSource = Join-Path $moduleRoot 'target/surefire-reports'
$coverageSource = Join-Path $moduleRoot 'target/site/jacoco'
if (!(Test-Path $testSource) -or !(Test-Path (Join-Path $coverageSource 'jacoco.csv'))) {
    throw 'Run merchant tests and the JaCoCo report goal first; no evidence was fabricated.'
}
$sourceReports = @(Get-ChildItem -LiteralPath $testSource -Filter 'TEST-*.xml')
if ($sourceReports.Count -eq 0) { throw 'No Surefire XML reports found.' }
$snapshotRoot = Join-Path $PSScriptRoot 'snapshot'
$testDestination = Join-Path $snapshotRoot 'surefire-reports'
$coverageDestination = Join-Path $snapshotRoot 'jacoco-html'
New-Item -ItemType Directory -Force -Path $testDestination, $coverageDestination | Out-Null
$suites = @()
Get-ChildItem -LiteralPath $testSource -Filter 'TEST-*.xml' | Sort-Object Name | ForEach-Object {
    [xml]$report = Get-Content -LiteralPath $_.FullName -Raw
    $suite = $report.testsuite
    $suites += [pscustomobject]@{
        Suite = [string]$suite.name
        Tests = [int]$suite.tests
        Failures = [int]$suite.failures
        Errors = [int]$suite.errors
        Skipped = [int]$suite.skipped
        SourceReportUtc = $_.LastWriteTimeUtc.ToString('yyyy-MM-ddTHH:mm:ssZ')
    }
    # Preserve test results, remove machine properties and captured application output.
    foreach ($entry in @($report.SelectNodes('//properties|//system-out|//system-err'))) {
        $entry.ParentNode.RemoveChild($entry) | Out-Null
    }
    $report.Save((Join-Path $testDestination $_.Name))
}
Get-ChildItem -LiteralPath $testSource -Filter '*.txt' | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $testDestination
}
Get-ChildItem -LiteralPath $coverageSource | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $coverageDestination -Recurse -Force
}
$suites | Export-Csv -NoTypeInformation -Encoding UTF8 -LiteralPath (Join-Path $snapshotRoot 'test-summary.csv')
$coverage = Import-Csv -LiteralPath (Join-Path $coverageSource 'jacoco.csv')
function Coverage-Row([string]$Label, $Rows) {
    $missed = [int](($Rows | Measure-Object LINE_MISSED -Sum).Sum)
    $covered = [int](($Rows | Measure-Object LINE_COVERED -Sum).Sum)
    $percent = if (($missed + $covered) -gt 0) { [Math]::Round(100 * $covered / ($missed + $covered), 2) } else { 0 }
    [pscustomobject]@{ Scope = $Label; LinesCovered = $covered; LinesMissed = $missed; LinePercent = $percent }
}
$coverageRows = @(
    Coverage-Row 'Entire merchant module' $coverage
    Coverage-Row 'Controllers' @($coverage | Where-Object PACKAGE -eq 'com.swiftdrop.merchant.controller')
    Coverage-Row 'Services' @($coverage | Where-Object PACKAGE -eq 'com.swiftdrop.merchant.service')
    Coverage-Row 'Controllers + services' @($coverage | Where-Object { $_.PACKAGE -in @('com.swiftdrop.merchant.controller','com.swiftdrop.merchant.service') })
)
$coverageRows | Export-Csv -NoTypeInformation -Encoding UTF8 -LiteralPath (Join-Path $snapshotRoot 'coverage-summary.csv')
$manifest = [ordered]@{
    capturedUtc = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    repositoryHeadAtCapture = (git -C $repoRoot rev-parse HEAD)
    provenance = 'Copies existing local reports; this script does not execute tests. Consult verificationCommand and source timestamps for the preceding run.'
    verificationCommand = $VerificationCommand
    trackedChangesAtCapture = @(git -C $repoRoot diff --name-only HEAD)
    coverageSourceUtc = (Get-Item (Join-Path $coverageSource 'jacoco.csv')).LastWriteTimeUtc.ToString('yyyy-MM-ddTHH:mm:ssZ')
    tests = [int](($suites | Measure-Object Tests -Sum).Sum)
    failures = [int](($suites | Measure-Object Failures -Sum).Sum)
    errors = [int](($suites | Measure-Object Errors -Sum).Sum)
    skipped = [int](($suites | Measure-Object Skipped -Sum).Sum)
    coverage = $coverageRows
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $snapshotRoot 'manifest.json')

# Use the same local PNG/SVG primitives as the diagrams; this is a data summary,
# not a browser screenshot of the JaCoCo HTML report.
. (Join-Path $repoRoot 'docs/design/m7/render-diagrams.ps1')
$script:outputDirectory = $snapshotRoot
Start-Diagram 'coverage-summary' 1300 650
Text 40 30 'Member 7 | Recorded backend evidence' 32
Text 40 90 "Tests: $($manifest.tests)   Failures: $($manifest.failures)   Errors: $($manifest.errors)   Skipped: $($manifest.skipped)" 24
Text 40 140 'Line coverage extracted from JaCoCo CSV; run command and timestamps are in manifest.json.' 21
$y = 205
foreach ($row in $coverageRows) {
    Box 40 $y 1220 58 ("{0}: {1}%  |  {2} covered, {3} missed" -f $row.Scope, $row.LinePercent, $row.LinesCovered, $row.LinesMissed)
    $y += 75
}
Text 40 530 'Guide target: aim for 70%+ on service/controller code. Consult package results separately.' 21
Text 40 570 'Source timestamps and capture context: manifest.json. Full coverage report: jacoco-html/index.html.' 20
Save-Diagram
Write-Output ($manifest | ConvertTo-Json -Depth 5)
