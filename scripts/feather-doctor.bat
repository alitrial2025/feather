@echo off
setlocal enabledelayedexpansion

echo 🪶 Feather Doctor
echo Checking system dependencies for Feather development...
echo.

set ERRORS=0

:: Check Cargo
where cargo >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ Cargo (Rust) is NOT installed or not in PATH.
    set /a ERRORS+=1
) else (
    echo ✅ Cargo is installed.
)

:: Check ADB
where adb >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ ADB (Android Debug Bridge) is NOT installed or not in PATH.
    set /a ERRORS+=1
) else (
    echo ✅ ADB is installed.
)

:: Note: xcodebuild is not available on Windows
echo ℹ️  xcodebuild check skipped (macOS only)

echo.
if !ERRORS! == 0 (
    echo 🎉 All required dependencies are installed! You are ready to run Feather.
    exit /b 0
) else (
    echo 🚨 Found !ERRORS! missing dependencies. Please install them to continue.
    exit /b 1
)
