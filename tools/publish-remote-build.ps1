[CmdletBinding()]
param(
    [string]$Branch = "custom-2.5.6",
    [string]$Workflow = "custom-build.yml",
    [string]$ReleaseTag = "custom-build",
    [string]$ApkPattern = "app-arm64-v8a-dist-r*.apk",
    [int]$PushRetries = 4,
    [int]$PollSeconds = 15,
    [int]$SpeedBytes = 4194304,
    [int]$SpeedTimeoutSeconds = 45,
    [switch]$SkipPush
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Invoke-Retry {
    param(
        [scriptblock]$Action,
        [string]$Name,
        [int]$Retries = 3,
        [int]$DelaySeconds = 5
    )

    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        try {
            & $Action
            if ($LASTEXITCODE -eq 0) { return }
        } catch {
            if ($attempt -eq $Retries) { throw }
        }
        if ($attempt -eq $Retries) {
            throw "$Name failed after $Retries attempts."
        }
        $delay = [Math]::Min(60, $DelaySeconds * [Math]::Pow(2, $attempt - 1))
        Write-Host "$Name failed; retrying in $delay seconds ($attempt/$Retries)..."
        Start-Sleep -Seconds $delay
    }
}

function Get-RunForSha {
    param([string]$Sha)
    $runs = gh run list --workflow $Workflow --branch $Branch --limit 30 --json databaseId,headSha,status,conclusion,url,createdAt | ConvertFrom-Json
    return $runs | Where-Object { $_.headSha -eq $Sha } | Sort-Object createdAt -Descending | Select-Object -First 1
}

function Test-DownloadSpeed {
    param(
        [string]$Url,
        [int]$BytesToTest,
        [int]$TimeoutSeconds
    )
    $rangeEnd = $BytesToTest - 1
    $format = "%{http_code}|%{size_download}|%{speed_download}|%{time_total}"
    $raw = & curl.exe -L --fail --silent --show-error --range "0-$rangeEnd" --max-time $TimeoutSeconds -o NUL -w $format $Url 2>&1
    $exitCode = $LASTEXITCODE
    $line = ($raw | Select-Object -Last 1).ToString()
    $parts = $line -split "\|"
    $speed = 0.0
    $size = 0L
    $seconds = 0.0
    $status = 0
    if ($parts.Count -eq 4) {
        [void][double]::TryParse($parts[2], [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$speed)
        [void][long]::TryParse($parts[1], [Globalization.NumberStyles]::Integer, [Globalization.CultureInfo]::InvariantCulture, [ref]$size)
        [void][double]::TryParse($parts[3], [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$seconds)
        [void][int]::TryParse($parts[0], [ref]$status)
    }
    [pscustomobject]@{
        Url = $Url
        ExitCode = $exitCode
        HttpStatus = $status
        Bytes = $size
        SpeedBytesPerSecond = $speed
        SpeedMiBPerSecond = [Math]::Round($speed / 1MB, 2)
        Seconds = $seconds
    }
}

if (-not $SkipPush) {
    gh auth setup-git | Out-Null
    $push = { git -c protocol.version=0 -c http.version=HTTP/1.1 push origin "HEAD:refs/heads/$Branch" }
    Invoke-Retry -Action $push -Name "Git push" -Retries $PushRetries -DelaySeconds 8
}

$sha = (git rev-parse HEAD).Trim()
Write-Host "Waiting for remote build of $sha..."
$run = $null
for ($i = 0; $i -lt 120; $i++) {
    $run = Get-RunForSha -Sha $sha
    if ($null -ne $run) { break }
    Start-Sleep -Seconds $PollSeconds
}
if ($null -eq $run) { throw "No Actions run found for commit $sha." }

gh run watch $run.databaseId --exit-status

$assets = gh release view $ReleaseTag --json assets | ConvertFrom-Json | Select-Object -ExpandProperty assets
$asset = $assets | Where-Object { $_.name -like $ApkPattern } | Sort-Object {
    $match = [regex]::Match($_.name, "-r(\d+)\.apk$")
    if ($match.Success) { [int]$match.Groups[1].Value } else { -1 }
} -Descending | Select-Object -First 1
if ($null -eq $asset) { throw "No APK matching '$ApkPattern' was found in release '$ReleaseTag'." }

$directUrl = $asset.url
$mirrorUrls = @(
    "https://ghproxy.net/$directUrl",
    "https://gh-proxy.com/$directUrl",
    "https://ghfast.top/$directUrl",
    ($directUrl -replace "https://github.com/", "https://gitclone.com/github.com/")
)
$urls = @($directUrl) + $mirrorUrls | Select-Object -Unique
Write-Host "Testing $($urls.Count) download endpoints for $($asset.name)..."
$results = foreach ($url in $urls) {
    Test-DownloadSpeed -Url $url -BytesToTest $SpeedBytes -TimeoutSeconds $SpeedTimeoutSeconds
}
$usable = $results | Where-Object { $_.ExitCode -eq 0 -and $_.HttpStatus -ge 200 -and $_.HttpStatus -lt 400 -and $_.Bytes -gt 0 } | Sort-Object SpeedBytesPerSecond -Descending

Write-Host "Asset: $($asset.name)"
Write-Host "SHA-256: $($asset.digest)"
if ($usable.Count -gt 0) {
    $fastest = $usable | Select-Object -First 1
    Write-Host ("Fastest URL: {0} ({1} MiB/s, {2:N2} s)" -f $fastest.Url, $fastest.SpeedMiBPerSecond, $fastest.Seconds)
    $usable | Select-Object Url,HttpStatus,SpeedMiBPerSecond,Seconds | Format-Table -AutoSize
} else {
    Write-Warning "No download endpoint completed a speed test. The direct release URL is: $directUrl"
}
