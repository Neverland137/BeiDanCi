# 无 Android Studio 构建 APK 脚本
# 前置条件：已安装 JDK 17、Android SDK 命令行工具，并设置 ANDROID_HOME

param(
    [switch]$Setup  # 加 -Setup 仅安装 Gradle Wrapper，不构建
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot

# 检查 Java
$javaVersion = & java -version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误: 未找到 Java。请安装 JDK 17 并确保 java 在 PATH 中。" -ForegroundColor Red
    exit 1
}

# 检查 Android SDK
if (-not $env:ANDROID_HOME) {
    Write-Host "错误: 未设置 ANDROID_HOME 环境变量。" -ForegroundColor Red
    Write-Host ""
    Write-Host "请按以下步骤安装 Android SDK 命令行工具（无需 Android Studio）：" -ForegroundColor Yellow
    Write-Host "1. 打开 https://developer.android.com/studio#command-line-tools-only"
    Write-Host "2. 下载 Windows 版 commandlinetools"
    Write-Host "3. 解压到例如 C:\Android\cmdline-tools\latest"
    Write-Host "4. 运行: sdkmanager ""platform-tools"" ""platforms;android-34"" ""build-tools;34.0.0"""
    Write-Host "5. 设置环境变量: ANDROID_HOME=C:\Android (或你的 SDK 根目录)"
    Write-Host ""
    exit 1
}

# 安装 Gradle Wrapper（若不存在）
$wrapperJar = "$projectRoot\gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $wrapperJar)) {
    Write-Host "正在安装 Gradle Wrapper..." -ForegroundColor Cyan
    & powershell -ExecutionPolicy Bypass -File "$projectRoot\setup-gradle-wrapper.ps1"
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

if ($Setup) {
    Write-Host "Gradle Wrapper 已就绪。运行 .\build-apk.ps1 即可构建。" -ForegroundColor Green
    exit 0
}

# 构建
Write-Host "正在构建 APK..." -ForegroundColor Cyan
Push-Location $projectRoot
try {
    & .\gradlew.bat assembleDebug --no-daemon
    if ($LASTEXITCODE -eq 0) {
        $apkPath = "$projectRoot\app\build\outputs\apk\debug\app-debug.apk"
        Write-Host ""
        Write-Host "构建成功！APK 位置: $apkPath" -ForegroundColor Green
    } else {
        exit 1
    }
} finally {
    Pop-Location
}
