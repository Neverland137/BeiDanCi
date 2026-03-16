# 一键安装 Gradle Wrapper（无需 Android Studio / 预装 Gradle）
# 从 Gradle 官方分发包中提取 gradle-wrapper.jar

$ErrorActionPreference = "Stop"
$gradleVersion = "8.2"
$zipUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
$wrapperDir = "$PSScriptRoot\gradle\wrapper"
$tempZip = "$env:TEMP\gradle-$gradleVersion-bin.zip"
$tempExtract = "$env:TEMP\gradle-$gradleVersion"

Write-Host "正在下载 Gradle $gradleVersion..."
Invoke-WebRequest -Uri $zipUrl -OutFile $tempZip -UseBasicParsing

Write-Host "正在解压..."
Expand-Archive -Path $tempZip -DestinationPath $env:TEMP -Force

# Gradle 8.2 分发包中 jar 路径: gradle-8.2/lib/plugins/gradle-wrapper-8.2.jar
$wrapperJarPath = Join-Path $tempExtract "gradle-$gradleVersion\lib\plugins\gradle-wrapper-$gradleVersion.jar"
if (-not (Test-Path $wrapperJarPath)) {
    $found = Get-ChildItem -Path $tempExtract -Recurse -Filter "gradle-wrapper*.jar" | Select-Object -First 1
    if ($found) { $wrapperJarPath = $found.FullName }
}
if (-not (Test-Path $wrapperJarPath)) {
    throw "未找到 gradle-wrapper.jar"
}

New-Item -ItemType Directory -Path $wrapperDir -Force | Out-Null
Copy-Item $wrapperJarPath -Destination "$wrapperDir\gradle-wrapper.jar" -Force

Remove-Item $tempZip -Force -ErrorAction SilentlyContinue
Remove-Item $tempExtract -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "Gradle Wrapper 安装完成！"
Write-Host "现在可运行: .\gradlew.bat assembleDebug"
