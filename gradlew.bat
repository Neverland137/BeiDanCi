<<<<<<< HEAD
@rem Gradle 启动脚本 (Windows)
@rem 需先运行 setup-gradle-wrapper.ps1 生成 gradle-wrapper.jar

@if "%DEBUG%"=="" @echo off

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem 查找 Java
if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute
echo 错误: 未找到 Java。请安装 JDK 17 并设置 JAVA_HOME
pause
exit /b 1

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" goto execute
echo 错误: JAVA_HOME 指向的 Java 不存在
pause
exit /b 1

:execute
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
if not exist "%CLASSPATH%" (
    echo 错误: 未找到 gradle-wrapper.jar
    echo 请先运行: powershell -ExecutionPolicy Bypass -File setup-gradle-wrapper.ps1
    pause
    exit /b 1
)

"%JAVA_EXE%" -Dorg.gradle.appname=%APP_BASE_NAME% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
=======
@rem Gradle 启动脚本 (Windows)
@rem 需先运行 setup-gradle-wrapper.ps1 生成 gradle-wrapper.jar

@if "%DEBUG%"=="" @echo off

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem 查找 Java
if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute
echo 错误: 未找到 Java。请安装 JDK 17 并设置 JAVA_HOME
pause
exit /b 1

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" goto execute
echo 错误: JAVA_HOME 指向的 Java 不存在
pause
exit /b 1

:execute
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
if not exist "%CLASSPATH%" (
    echo 错误: 未找到 gradle-wrapper.jar
    echo 请先运行: powershell -ExecutionPolicy Bypass -File setup-gradle-wrapper.ps1
    pause
    exit /b 1
)

"%JAVA_EXE%" -Dorg.gradle.appname=%APP_BASE_NAME% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
>>>>>>> f6e29ecc53d18f73879112d823be8df23faeea30
