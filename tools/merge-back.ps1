<#
.SYNOPSIS
把侧分支合回主线，并按「整合推送不占号」的约定给 merge commit 打 [skip ci]。

.DESCRIPTION
两条线并行时的约定：
  - 侧分支用 workflow_dispatch 出包拿号（只监听 custom-2.5.6 的 push，侧分支 push 不触发构建）；
  - 合回 custom-2.5.6 时如果再触发一次构建，同一份代码就占两个号。

GitHub 只看被推送的 HEAD commit 里有没有 skip 关键字，所以这里固定用 merge --no-ff 造一个
merge commit 当 HEAD，把 [skip ci] 写进去；fast-forward 没有提交可以承载关键字，不适用。

冲突时**不写** [skip ci] 并中止：冲突处理可能改坏代码，那种情况必须让它真构建一次。

.PARAMETER Branch
要合并的侧分支，默认当前分支。

.PARAMETER Target
主线分支，默认 custom-2.5.6。

.PARAMETER PinIp
github.com 直连不稳时，用 -PinIp 140.82.121.4 之类固定 IP 拉取/推送。

.PARAMETER Version
版本号（如 r372）。给了就按双线口径的惯例把 merge commit 写成
"chore(memory): r372 并入 [skip ci]"；不给则写 "merge: <分支> [skip ci]"。

.PARAMETER NoSkipCi
强制让这次合并触发构建（改了共用代码、需要出包验证时用）。

.PARAMETER KeepBranch
合并后留在主线（默认行为）；加此开关则合并完切回侧分支。

.EXAMPLE
pwsh -File tools/merge-back.ps1

.EXAMPLE
pwsh -File tools/merge-back.ps1 -Branch feat/memory-auto-recall -Version r372 -PinIp 20.6.7.17
# 侧分支已出包 r372，合并回主线时写成 "chore(memory): r372 并入 [skip ci]"，不重复占号。
#>
[CmdletBinding()]
param(
    [string]$Branch = "",
    [string]$Target = "custom-2.5.6",
    [string]$Version = "",
    [string]$PinIp = "",
    [switch]$NoSkipCi,
    [switch]$KeepBranch
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Write-Step([string]$Text) { Write-Host "==> $Text" -ForegroundColor Cyan }
function Write-Warn([string]$Text) { Write-Host "!!  $Text" -ForegroundColor Yellow }

function Get-NetArgs {
    if (-not $PinIp) { return @() }
    return @('-c', "http.curloptResolve=github.com:443:$PinIp", '-c', 'http.version=HTTP/1.1')
}

function Invoke-GitRetry {
    param(
        [string[]]$GitArgs,
        [string]$Name,
        [int]$Retries = 4
    )
    $net = Get-NetArgs
    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        & git @net @GitArgs
        if ($LASTEXITCODE -eq 0) { return }
        if ($attempt -eq $Retries) { throw "$Name 连续失败 $Retries 次，放弃。" }
        $delay = [Math]::Min(30, 5 * $attempt)
        Write-Warn "$Name 失败（第 $attempt 次），${delay}s 后重试…"
        Start-Sleep -Seconds $delay
    }
}

# ---------- 前置检查 ----------
$current = (& git rev-parse --abbrev-ref HEAD).Trim()
if (-not $Branch) { $Branch = $current }
if ($Branch -eq $Target) {
    throw "当前就在 $Target 上。请先切到要合并的侧分支，或用 -Branch 指定。"
}

$dirty = (& git status --porcelain)
if ($dirty) {
    throw "工作区不干净，先提交或 stash 再合并：`n$($dirty -join "`n")"
}

Write-Step "合并 $Branch → $Target"

# ---------- 同步远端 ----------
Invoke-GitRetry -GitArgs @('fetch', 'origin', $Target) -Name "fetch origin $Target"

$remoteTip = (& git rev-parse --short "origin/$Target").Trim()
Write-Host "    origin/$Target = $remoteTip"

$branchTip = (& git rev-parse --short $Branch).Trim()
$behindCommits = (& git rev-list --count "origin/$Target..$Branch").Trim()
if ($behindCommits -eq '0') {
    Write-Step "$Branch 相对 origin/$Target 没有新提交，无需合并。"
    exit 0
}
Write-Host "    $Branch = $branchTip（领先 $behindCommits 个提交）"

# ---------- 切主线并快进 ----------
& git switch $Target
if ($LASTEXITCODE -ne 0) { throw "切到 $Target 失败。" }

& git merge --ff-only "origin/$Target"
if ($LASTEXITCODE -ne 0) {
    Write-Warn "本地 $Target 与 origin/$Target 分叉，未能快进。请先手工处理本地主线。"
    exit 1
}

# ---------- 合并（冲突即中止） ----------
if ($NoSkipCi) {
    $mergeMessage = "merge: $Branch"
    Write-Warn "-NoSkipCi：这次合并会触发构建并占一个号。"
} elseif ($Version) {
    # 与双线口径一致：最顶上那笔带 [skip ci]，习惯写法见 facts.md
    $mergeMessage = "chore(memory): $Version 并入 [skip ci]"
} else {
    $mergeMessage = "merge: $Branch [skip ci]"
}

& git merge --no-ff --no-edit -m $mergeMessage $Branch
if ($LASTEXITCODE -ne 0) {
    & git merge --abort
    Write-Host ""
    Write-Host "合并有冲突，已中止（主线未改动）。" -ForegroundColor Red
    Write-Host "手工解决后请重新合并；**冲突解决过就不要加 [skip ci]** —— 让 CI 真编译一次。" -ForegroundColor Red
    exit 1
}

$mergeSha = (& git rev-parse --short HEAD).Trim()
Write-Host "    合并提交 = $mergeSha  「$mergeMessage」"

# ---------- 推送 ----------
Invoke-GitRetry -GitArgs @('push', 'origin', $Target) -Name "push origin $Target"

Write-Host ""
Write-Step "完成"
Write-Host "    $Target 现在指向 $mergeSha"
if ($NoSkipCi) {
    Write-Host "    这次合并会触发 CI 构建（占号）。"
} else {
    Write-Host "    [skip ci] 生效：本次推送不构建、不占号；代码已进主线，下一版真推送时会一起打包。"
}

if ($KeepBranch) {
    & git switch $Branch
    Write-Host "    已切回 $Branch" 
}
