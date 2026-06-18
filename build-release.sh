#!/bin/bash
set -e

echo "=== 1. Building Desktop JAR ==="
./gradlew :desktop:clean :desktop:jar

echo "=== 2. Creating Release Directory ==="
mkdir -p release-builds
rm -rf release-builds/*

echo "=== 3. Packaging Standalone Application (App Image) ==="
# This creates a portable folder that can be zipped and shared directly
jpackage \
  --input desktop/build/libs \
  --main-jar desktop-1.0.jar \
  --main-class com.example.desktop.Main \
  --name "PrismCast" \
  --type app-image \
  --icon desktop/icon.png \
  --dest release-builds/portable

echo "=== 4. Packaging Native Debian Package (.deb) ==="
# This creates an installer that adds Prism Cast to the Linux Applications Menu
jpackage \
  --input desktop/build/libs \
  --main-jar desktop-1.0.jar \
  --main-class com.example.desktop.Main \
  --name "prismcast" \
  --app-version "1.0.0" \
  --vendor "PrismCast" \
  --description "Secure screen broadcasting and streaming over local HTTP/MJPEG." \
  --type deb \
  --icon desktop/icon.png \
  --dest release-builds \
  --linux-shortcut \
  --linux-menu-group "Utility"

echo ""
echo "=== Success! Release builds created under release-builds/ ==="
ls -lh release-builds/
echo ""
echo "To install the app and add it to your Apps Menu, run:"
echo "  sudo dpkg -i release-builds/*.deb"
echo ""
echo "Or run the portable version directly from:"
echo "  ./release-builds/portable/PrismCast/bin/PrismCast"
echo "=========================================================="
