<#
.SYNOPSIS
提交并单独推送记忆/文档更新：不触发构建、不占版本号。

.DESCRIPTION
custom-build.yml 的 paths-ignore 覆盖 `**.md`、`docs/**`、`tools/**`、`.zcode/**`、
`.github/workflows/**`、`.gitignore`。只含这些文件的 push 不会构建，也不消耗 run_number ——
所以每出完一个版本的包，把 `.zcode/memory/` 的更新单独推一次，另一台设备、另一个窗口
`pull` 就能拿到同样的结论。

本脚本只允许上述路径进入提交：发现代码/资源文件被一起暂存就中止并给出提示，
避免不小心把一次记忆推送变成构建（多占一个号）。

.PARAMETER Message
提交信息。默认 `chore(memory): 同步记忆 yyyy-MM-dd HH:mm`。

.PARAMETER Paths
要提交的路径，默认 `.zcode/memory`、`docs`、`AGENTS.md`。

.PARAMETER Branch
推送到哪个分支，默认当前分支。

.PARAMETER PinIp
github.com 直连不稳时固定 IP，例如 -PinIp 20.6.7.17。

.PARAMETER DryRun
只检查会提交哪些文件，不提交、不推送。

.EXAMPLE
pwsh -File tools/push-memory.ps1

.EXAMPLE
pwsh -File tools/push-memory.ps1 -PinIp 20.6.7.17 -DryRun
#>
[CmdletBinding()]
param(
    [string]$Message = "",
    [string[]]$Paths = @('.zcode/memory', 'docs', 'tools', 'AGENTS.md'),
    [string]$Branch = "",
    [string]$PinIp = "",
    [string]$CredentialHelper = "manager",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# paths-ignore 允许的路径（含代码以外的所有内容）
$ignoredPrefixes = @('.zcode/', 'docs/', 'tools/', '.github/workflows/')

function Test-IgnoredPath {
    param([string]$Path)
    $p = $Path -replace '\\', '/'
    if ($p -match '\.md$') { return $true }
    if ($p -eq '.gitignore') { return $true }
    foreach ($prefix in $ignoredPrefixes) {
        if ($p.StartsWith($prefix)) { return $true }
    }
    return $false
}

if (-not $Branch) { $Branch = (& git rev-parse --abbrev-ref HEAD).Trim() }

# ---------- 暂存指定路径 ----------
$existing = $Paths | Where-Object { Test-Path $_ }
if (-not $existing) {
    Write-Host "指定的路径都不存在，无可提交内容。" -ForegroundColor Yellow
    exit 0
}
& git add -- $existing
if ($LASTEXITCODE -ne 0) { throw "git add 失败。" }

$staged = @(& git -c core.quotepath=false diff --cached --name-only)
# 中文文件名在 core.quotepath 开启时会被转成八进制转义并带引号，这里兜底去掉包裹引号
$staged = @($staged | ForEach-Object { $_.Trim('"') } | Where-Object { $_ })
if ($staged.Count -eq 0) {
    Write-Host "没有记忆/文档更新，无需推送。" -ForegroundColor Yellow
    exit 0
}

# ---------- 安全闸门：不许代码文件混入 ----------
$blocked = @($staged | Where-Object { -not (Test-IgnoredPath $_) })
if ($blocked.Count -gt 0) {
    & git reset --quiet -- $blocked
    Write-Host "检测到会触发构建的文件，已从暂存区撤下并中止：" -ForegroundColor Red
    $blocked | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
    Write-Host ""
    Write-Host "记忆推送必须只含paths-ignore覆盖的文件，否则会多占一个版本号。" -ForegroundColor Red
    Write-Host "如果你确实要出包，请走侧分支 + workflow_dispatch。" -ForegroundColor Red
    exit 1
}

Write-Host "==> 将提交 $($staged.Count) 个文件（全部在 paths-ignore 内，不会触发构建）：" -ForegroundColor Cyan
$staged | ForEach-Object { Write-Host "    $_" }

if ($DryRun) {
    Write-Host ""
    Write-Host "-DryRun：已停止，未提交、未推送。" -ForegroundColor Yellow
    exit 0
}

# ---------- 提交 ----------
if (-not $Message) {
    $Message = "chore(memory): 同步记忆 " + (Get-Date -Format 'yyyy-MM-dd HH:mm')
}
& git commit -m $Message
if ($LASTEXITCODE -ne 0) { throw "git commit 失败。" }
$sha = (& git rev-parse --short HEAD).Trim()
Write-Host "    提交 = $sha  「$Message」"

# ---------- 推送（带重试） ----------
$net = @()
if ($PinIp) {
    $net = @('-c', "http.curloptResolve=github.com:443:$PinIp", '-c', 'http.version=HTTP/1.1')
}

$pushed = $false
# 全局 credential.helper 可能指向未登录的 gh；显式走 Windows 凭据管理器
$cred = @()
if ($CredentialHelper) { $cred = @('-c', 'credential.helper=', '-c', "credential.helper=$CredentialHelper") }
for ($attempt = 1; $attempt -le 5; $attempt++) {
    & git @cred @net push origin "$Branch"
    if ($LASTEXITCODE -eq 0) { $pushed = $true; break }
    $delay = [Math]::Min(30, 5 * $attempt)
    Write-Host "推送失败（第 $attempt 次），${delay}s 后重试…" -ForegroundColor Yellow
    Start-Sleep -Seconds $delay
}
if (-not $pushed) { throw "推送连续失败，记忆未同步到远端。" }

Write-Host ""
Write-Host "==> 完成：记忆已推送到 origin/$Branch（$sha）" -ForegroundColor Green
Write-Host "    本次 push 只含文档/记忆，不触发构建、不占版本号。"
