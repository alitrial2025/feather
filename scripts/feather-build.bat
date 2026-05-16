@echo off
setlocal

echo 🪶 Feather Build: AOT Mode
echo Compiling Kotlin KSP to generate AOT IR Payload...

cd samples\counter
call gradlew.bat build --no-daemon

echo Packaging IR into Android Bundle...

:: Copy to Android Assets
if not exist "..\..\host\android\app\src\main\assets\feather" mkdir "..\..\host\android\app\src\main\assets\feather"
copy /Y "build\generated\ksp\main\resources\feather\app.feather.json" "..\..\host\android\app\src\main\assets\feather\app.feather.json"

echo Building Android APK...
cd ..\..\host\android
call gradlew.bat assembleRelease

echo ✅ AOT payload generated and packaged successfully.
