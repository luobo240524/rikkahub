# 在中文路径下跑 Gradle 的兼容入口。
#
# 背景:JDK 25 在 zh-CN Windows 上 sun.jnu.encoding=GBK,而 Gradle 把测试 worker 的
# classpath 写成 UTF-8 的 @argfile;JVM 用 GBK 解码后中文路径变乱码,
# 报 "找不到或无法加载主类 GradleWorkerMain"(普通编译不受影响,只有 test 任务会中招)。
# 这里用 subst 给「项目目录」和「GRADLE_USER_HOME」各挂一个纯 ASCII 盘符再跑,
# 退出时自动删除映射;路径本来就是 ASCII 时直接原样执行。
#
# 用法(在仓库根目录;系统默认禁止跑 .ps1 时加 -ExecutionPolicy Bypass):
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradlew-ascii.ps1 :app:testDebugUnitTest
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradlew-ascii.ps1 :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.readtogether.*"
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradlew-ascii.ps1 :app:compileDistKotlin -x :web:buildWebUi

$ErrorActionPreference = 'Stop'

$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
if (-not (Test-Path $gradleHome)) { New-Item -ItemType Directory -Path $gradleHome | Out-Null }

function Test-Ascii([string]$s) { return ($s -notmatch '[^\x00-\x7F]') }

$script:createdDrives = @()
function New-AsciiDrive([string]$path, [string]$label) {
    if (Test-Ascii $path) { return $path }
    foreach ($letter in 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z') {
        $drive = "${letter}:"
        $root = "${letter}:\"
        if (-not (Test-Path $root)) {
            # subst 不接受带尾反斜杠的盘符参数
            & subst.exe $drive $path | Out-Null
            if ($LASTEXITCODE -ne 0) { continue }
            $script:createdDrives += $drive
            return $root
        }
    }
    throw "找不到可用盘符来挂载 $label($path)"
}

# 没有 param 块:外部参数(含 -Pxxx / --tests)原样进 $args
$gradleArgs = $args

try {
    $projectDrive = New-AsciiDrive $project '项目路径'
    $gradleDrive = New-AsciiDrive $gradleHome 'GRADLE_USER_HOME'
    $env:GRADLE_USER_HOME = $gradleDrive

    if (-not $env:JAVA_HOME) {
        $jbr = 'C:\Program Files\Android\Android Studio\jbr'
        if (Test-Path $jbr) { $env:JAVA_HOME = $jbr }
    }

    Push-Location $projectDrive
    & (Join-Path $projectDrive 'gradlew.bat') @gradleArgs
    $code = $LASTEXITCODE
} finally {
    Pop-Location
    foreach ($drive in $script:createdDrives) {
        & subst.exe $drive /d | Out-Null
    }
}

exit $code
