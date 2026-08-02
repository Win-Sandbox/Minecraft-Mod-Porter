@echo off
rem ModPorter CLI launcher for Windows.
rem Sets the console to UTF-8 and forces the JVM to emit UTF-8, so Chinese
rem output shows correctly instead of mojibake.
rem
rem Usage:  modporter.bat versions
rem         modporter.bat port -f 1.12.2 -t 1.19.2 -i "C:\in" -o "C:\out"
setlocal

rem Switch the console code page to UTF-8 (65001); harmless if already set.
chcp 65001 >nul 2>nul

set "JAR="
rem 1) jar sitting next to this script (deployed layout)
if exist "%~dp0modporter.jar" set "JAR=%~dp0modporter.jar"
rem 2) Gradle build output (source tree layout)
if not defined JAR (
    for %%F in ("%~dp0build\libs\*.jar") do set "JAR=%%F"
)
if not defined JAR (
    echo [ERROR] modporter.jar not found.
    echo         Run "gradle jar" first, or put modporter.jar next to this script.
    exit /b 1
)

rem Default mappings dir via env var, so an explicit --mappings on the command
rem line still wins (the backend checks the option first, then this variable).
if exist "%~dp0mappings" set "MODPORTER_MAPPINGS=%~dp0mappings"

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 ^
     -jar "%JAR%" %*
