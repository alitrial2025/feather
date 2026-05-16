#!/bin/bash

echo "🪶 Feather Build: AOT Mode"
echo "Compiling Kotlin KSP to generate AOT IR Payload..."

# Compile KSP to generate app.feather.json
cd samples/counter
./gradlew build --no-daemon

echo "Packaging IR into iOS and Android Bundles..."

# Copy to Android Assets
mkdir -p ../../host/android/app/src/main/assets/feather
cp build/generated/ksp/main/resources/feather/app.feather.json ../../host/android/app/src/main/assets/feather/app.feather.json

# Copy to iOS Bundle
mkdir -p ../../host/ios/FeatherHost/Assets
cp build/generated/ksp/main/resources/feather/app.feather.json ../../host/ios/FeatherHost/Assets/app.feather.json

echo "Building Android APK..."
cd ../../host/android
./gradlew assembleRelease

echo "Building iOS App..."
cd ../ios
if [[ "$OSTYPE" == "darwin"* ]]; then
    xcodebuild -workspace FeatherHost.xcworkspace -scheme FeatherHost -configuration Release build
else
    echo "⚠️ Skipping iOS build (macOS required for xcodebuild)"
fi

echo "✅ AOT payload generated and packaged successfully."
