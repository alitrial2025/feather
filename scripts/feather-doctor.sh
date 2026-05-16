#!/bin/bash

echo "🪶 Feather Doctor"
echo "Checking system dependencies for Feather development..."
echo ""

ERRORS=0

# Check Cargo
if ! command -v cargo &> /dev/null; then
    echo "❌ Cargo (Rust) is NOT installed or not in PATH."
    ((ERRORS++))
else
    echo "✅ Cargo is installed."
fi

# Check ADB
if ! command -v adb &> /dev/null; then
    echo "❌ ADB (Android Debug Bridge) is NOT installed or not in PATH."
    ((ERRORS++))
else
    echo "✅ ADB is installed."
fi

# Check xcodebuild (macOS only)
if [[ "$OSTYPE" == "darwin"* ]]; then
    if ! command -v xcodebuild &> /dev/null; then
        echo "❌ xcodebuild is NOT installed. Install Xcode command line tools."
        ((ERRORS++))
    else
        echo "✅ xcodebuild is installed."
    fi
else
    echo "ℹ️  xcodebuild check skipped (not on macOS)"
fi

echo ""
if [ $ERRORS -eq 0 ]; then
    echo "🎉 All required dependencies are installed! You are ready to run Feather."
    exit 0
else
    echo "🚨 Found $ERRORS missing dependencies. Please install them to continue."
    exit 1
fi
