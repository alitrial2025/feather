# build_core.ps1 — Cross-compile feather-core for Android on Windows
#
# Prerequisites:
#   - Rust toolchain: https://rustup.rs
#   - Android NDK (set $env:ANDROID_NDK_HOME)
#   - cargo-ndk: cargo install cargo-ndk
#
# Usage:
#   .\scripts\build_core.ps1              # Build Android + bindings
#   .\scripts\build_core.ps1 -Target android
#   .\scripts\build_core.ps1 -Target bindings

param(
    [ValidateSet("android", "bindings", "all")]
    [string]$Target = "all"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$CoreDir = Join-Path $ProjectRoot "core"
$OutputDir = Join-Path $ProjectRoot "build\native"

function Log($msg) { Write-Host "[feather] $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "[feather] $msg" -ForegroundColor Yellow }
function Err($msg) { Write-Host "[feather] $msg" -ForegroundColor Red }

# Check Rust
$rustVersion = & rustc --version 2>$null
if (-not $rustVersion) {
    Err "Rust not found. Install from https://rustup.rs"
    exit 1
}
Log "Rust: $rustVersion"

function Build-Android {
    Log "Building feather-core for Android..."

    if (-not $env:ANDROID_NDK_HOME) {
        Err "ANDROID_NDK_HOME not set."
        exit 1
    }

    $cargoNdk = Get-Command cargo-ndk -ErrorAction SilentlyContinue
    if (-not $cargoNdk) {
        Warn "Installing cargo-ndk..."
        cargo install cargo-ndk
    }

    $androidOut = Join-Path $OutputDir "android"
    New-Item -ItemType Directory -Force -Path $androidOut | Out-Null

    Push-Location $CoreDir

    $targets = @(
        @{ rust = "aarch64-linux-android"; abi = "arm64-v8a" },
        @{ rust = "armv7-linux-androideabi"; abi = "armeabi-v7a" },
        @{ rust = "x86_64-linux-android"; abi = "x86_64" }
    )

    foreach ($t in $targets) {
        Log "  Building $($t.abi) ($($t.rust))..."
        rustup target add $t.rust 2>$null
        cargo ndk --target $t.rust --platform 24 build --release

        $libDir = Join-Path $androidOut "jniLibs\$($t.abi)"
        New-Item -ItemType Directory -Force -Path $libDir | Out-Null

        $soFile = Join-Path "target\$($t.rust)\release" "libfeather_core.so"
        if (Test-Path $soFile) {
            Copy-Item $soFile $libDir
        } else {
            Warn "  No .so for $($t.abi)"
        }
    }

    Pop-Location
    Log "Android build complete -> $androidOut"
}

function Generate-Bindings {
    Log "Generating UniFFI bindings..."

    Push-Location $CoreDir
    cargo build --release

    $bindingsOut = Join-Path $OutputDir "bindings\kotlin"
    New-Item -ItemType Directory -Force -Path $bindingsOut | Out-Null

    $dll = Join-Path "target\release" "feather_core.dll"
    if (Test-Path $dll) {
        cargo run --bin uniffi-bindgen -- generate `
            --library $dll `
            --language kotlin `
            --out-dir $bindingsOut
        Log "  Kotlin bindings -> $bindingsOut"
    } else {
        Warn "  feather_core.dll not found. Build may have failed."
    }

    Pop-Location
    Log "Bindings generation complete."
}

switch ($Target) {
    "android"  { Build-Android }
    "bindings" { Generate-Bindings }
    "all"      { Build-Android; Generate-Bindings }
}

Log "Done!"
