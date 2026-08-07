@echo off
rem ModPorter Win32 frontend build script (run on Windows).
rem Messages are ASCII-only on purpose: cmd.exe would show mojibake for UTF-8 text.
rem
rem Usage:
rem   build.bat          auto-detect MSVC (cl) or MinGW (g++)
rem   build.bat msvc     force MSVC  (run from "x64 Native Tools Command Prompt for VS 2022")
rem   build.bat mingw    force MinGW-w64 g++
setlocal

set MODE=%1
if "%MODE%"=="" (
    where cl >nul 2>nul && set MODE=msvc
)
if "%MODE%"=="" (
    where g++ >nul 2>nul && set MODE=mingw
)
if "%MODE%"=="" (
    echo [ERROR] Neither cl nor g++ found. Install VS Build Tools or MinGW-w64 first.
    exit /b 1
)

if /i "%MODE%"=="msvc" goto build_msvc
if /i "%MODE%"=="mingw" goto build_mingw
echo [ERROR] Unknown mode: %MODE%
exit /b 1

:build_msvc
echo === Building with MSVC ===
rc /nologo /fo ModPorter.res ModPorter.rc || exit /b 1
rem /utf-8 is required: sources contain Chinese literals; without it MSVC uses the
rem system ANSI codepage and the UI text becomes garbage.
cl /nologo /EHsc /W4 /O2 /MT /utf-8 /DUNICODE /D_UNICODE ^
   main.cpp util.cpp settings.cpp backend.cpp json.cpp ^
   page_convert.cpp page_report.cpp page_extensions.cpp page_settings.cpp ^
   ModPorter.res ^
   /Fe:ModPorter.exe ^
   /link /SUBSYSTEM:WINDOWS comctl32.lib shell32.lib comdlg32.lib ole32.lib user32.lib gdi32.lib advapi32.lib || exit /b 1
del *.obj ModPorter.res >nul 2>nul
echo === Done: ModPorter.exe ===
exit /b 0

:build_mingw
echo === Building with MinGW-w64 ===
windres ModPorter.rc -O coff -o ModPorter.res.o || exit /b 1
g++ -std=c++11 -O2 -Wall -municode -mwindows -DUNICODE -D_UNICODE ^
    -finput-charset=UTF-8 -fexec-charset=UTF-8 ^
    main.cpp util.cpp settings.cpp backend.cpp json.cpp ^
    page_convert.cpp page_report.cpp page_extensions.cpp page_settings.cpp ^
    ModPorter.res.o ^
    -o ModPorter.exe ^
    -static -static-libgcc -static-libstdc++ ^
    -lcomctl32 -lshell32 -lcomdlg32 -lole32 -luser32 -lgdi32 -ladvapi32 || exit /b 1
del ModPorter.res.o >nul 2>nul
echo === Done: ModPorter.exe ===
exit /b 0
