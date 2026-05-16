#!/usr/bin/env bash
# build_core.sh — Cross-compile feather-core for Android and iOS
#
# Prerequisites:
#   - Rust toolchain with cross-compilation targets:
#       rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
#       rustup target add aarch64-apple-ios x86_64-apple-ios aarch64-apple-ios-sim
#   - Android NDK (set ANDROID_NDK_HOME)
#   - Xcode (for iOS builds, macOS only)
#   - cargo-ndk:  cargo install cargo-ndk
#
# Usage:
#   ./scripts/build_core.sh android    # Build .so files for Android
#   ./scripts/build_core.sh ios        # Build .a / .framework for iOS
#   ./scripts/build_core.sh all        # Build both
#   ./scripts/build_core.sh bindings   # Generate UniFFI bindings only

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CORE_DIR="$PROJECT_ROOT/core"
OUTPUT_DIR="$PROJECT_ROOT/build/native"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()   { echo -e "${GREEN}[feather]${NC} $*"; }
warn()  { echo -e "${YELLOW}[feather]${NC} $*"; }
error() { echo -e "${RED}[feather]${NC} $*" >&2; }

check_rust() {
    if ! command -v rustc &>/dev/null; then
        error "Rust toolchain not found. Install from https://rustup.rs"
        exit 1
    fi
    log "Rust: $(rustc --version)"
}

build_android() {
    log "Building feather-core for Android..."

    if [ -z "${ANDROID_NDK_HOME:-}" ]; then
        error "ANDROID_NDK_HOME not set. Set it to your NDK installation path."
        exit 1
    fi

    if ! command -v cargo-ndk &>/dev/null; then
        warn "cargo-ndk not found. Installing..."
        cargo install cargo-ndk
    fi

    local ANDROID_OUT="$OUTPUT_DIR/android"
    mkdir -p "$ANDROID_OUT"

    cd "$CORE_DIR"

    local TARGETS=("aarch64-linux-android" "armv7-linux-androideabi" "x86_64-linux-android")
    local ABI_NAMES=("arm64-v8a" "armeabi-v7a" "x86_64")

    for i in "${!TARGETS[@]}"; do
        local target="${TARGETS[$i]}"
        local abi="${ABI_NAMES[$i]}"

        log "  Building $abi ($target)..."
        rustup target add "$target" 2>/dev/null || true
        cargo ndk --target "$target" --platform 24 build --release

        local LIB_DIR="$ANDROID_OUT/jniLibs/$abi"
        mkdir -p "$LIB_DIR"
        cp "target/$target/release/libfeather_core.so" "$LIB_DIR/" 2>/dev/null || \
            warn "  No .so produced for $abi (expected for cdylib)"
    done

    log "Android build complete → $ANDROID_OUT"
}

build_ios() {
    log "Building feather-core for iOS..."

    if [[ "$(uname)" != "Darwin" ]]; then
        error "iOS builds require macOS with Xcode."
        exit 1
    fi

    local IOS_OUT="$OUTPUT_DIR/ios"
    mkdir -p "$IOS_OUT"

    cd "$CORE_DIR"

    local TARGETS=("aarch64-apple-ios" "aarch64-apple-ios-sim" "x86_64-apple-ios")

    for target in "${TARGETS[@]}"; do
        log "  Building $target..."
        rustup target add "$target" 2>/dev/null || true
        cargo build --release --target "$target"
    done

    # Create universal static library for simulator (arm64 + x86_64)
    log "  Creating universal simulator library..."
    mkdir -p "$IOS_OUT/sim"
    lipo -create \
        "target/aarch64-apple-ios-sim/release/libfeather_core.a" \
        "target/x86_64-apple-ios/release/libfeather_core.a" \
        -output "$IOS_OUT/sim/libfeather_core.a"

    # Copy device library
    mkdir -p "$IOS_OUT/device"
    cp "target/aarch64-apple-ios/release/libfeather_core.a" "$IOS_OUT/device/"

    # Create XCFramework
    log "  Creating XCFramework..."
    rm -rf "$IOS_OUT/FeatherCore.xcframework"
    xcodebuild -create-xcframework \
        -library "$IOS_OUT/device/libfeather_core.a" \
        -library "$IOS_OUT/sim/libfeather_core.a" \
        -output "$IOS_OUT/FeatherCore.xcframework"

    log "iOS build complete → $IOS_OUT"
}

generate_bindings() {
    log "Generating UniFFI bindings..."

    cd "$CORE_DIR"
    cargo build --release

    local BINDINGS_OUT="$OUTPUT_DIR/bindings"
    mkdir -p "$BINDINGS_OUT/kotlin" "$BINDINGS_OUT/swift"

    # Generate Kotlin bindings
    if cargo run --bin uniffi-bindgen -- generate \
        --library "target/release/libfeather_core.so" \
        --language kotlin \
        --out-dir "$BINDINGS_OUT/kotlin" 2>/dev/null || \
       cargo run --bin uniffi-bindgen -- generate \
        --library "target/release/feather_core.dll" \
        --language kotlin \
        --out-dir "$BINDINGS_OUT/kotlin" 2>/dev/null; then
        log "  Kotlin bindings → $BINDINGS_OUT/kotlin"
    else
        warn "  Could not generate Kotlin bindings (native library not found)"
    fi

    # Generate Swift bindings
    if cargo run --bin uniffi-bindgen -- generate \
        --library "target/release/libfeather_core.dylib" \
        --language swift \
        --out-dir "$BINDINGS_OUT/swift" 2>/dev/null; then
        log "  Swift bindings → $BINDINGS_OUT/swift"
    else
        warn "  Could not generate Swift bindings (requires macOS)"
    fi

    log "Bindings generation complete."
}

# --- Main ---
check_rust

case "${1:-all}" in
    android)
        build_android
        ;;
    ios)
        build_ios
        ;;
    bindings)
        generate_bindings
        ;;
    all)
        build_android
        build_ios
        generate_bindings
        ;;
    *)
        echo "Usage: $0 {android|ios|bindings|all}"
        exit 1
        ;;
esac

log "Done! 🪶"
