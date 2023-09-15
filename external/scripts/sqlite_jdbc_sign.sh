#!/usr/bin/env bash

export LC_ALL=C
set -e

readonly TIME_START=$(date +%s)

# Absolute path to the directory which this script resides in
readonly DIR_SCRIPTS=$( cd "$( dirname "$0" )" >/dev/null && pwd )

readonly DIR_EXTERNAL="$DIR_SCRIPTS/.."
readonly DIR_JNI="$DIR_EXTERNAL/jni"
readonly DIR_OUT="$DIR_JNI/out"
readonly DIR_UNSIGNED="$DIR_OUT/unsigned"
readonly DIR_SIGNED="$DIR_OUT/signed"

# Programs
readonly RCODESIGN=$(which rcodesign)
readonly OSSLSIGNCODE=$(which osslsigncode)

if [ -z $RCODESIGN ]; then
    echo "rcodesign is required to run this script"
    exit 1
fi

if [ -z $OSSLSIGNCODE ]; then
    echo "osslsigncode is required to run this script"
    exit 1
fi

sign_macos() {
  cd "$DIR_OUT"
  cp -R "$DIR_UNSIGNED/Mac" "$DIR_SIGNED"

  # Read in .p12 key file path
  local PATH_KEY_P12=
  printf "Path to .p12 key file (e.g. /home/user/dir/key.p12): "
  read -r PATH_KEY_P12

  if [ ! -f "$PATH_KEY_P12" ]; then
    echo "File does not exist >> $PATH_KEY_P12"
    exit 1
  fi

  # Read in App Store Connect apikey.json file path
  local PATH_KEY_API=
  printf "Path to App Store Connect api-key json file (e.g. /home/user/dir/api_key.json): "
  read -r PATH_KEY_API

  if [ ! -f "$PATH_KEY_API" ]; then
    echo "File does not exist >> $PATH_KEY_API"
    exit 1
  fi

  local DIR_ARCH=
  local BUNDLE=
  local BUNDLE_MACOS=
  local BUNDLE_LIB=
  for DIR_ARCH in "$DIR_SIGNED/Mac"/*; do
    cd "$DIR_ARCH"
    BUNDLE="$DIR_ARCH/SQLiteMCDriver.app"
    BUNDLE_MACOS="$BUNDLE/Contents/MacOS"
    BUNDLE_LIB="$BUNDLE_MACOS/sqlite-jdbc"

    rm -rf "$BUNDLE"
    mkdir -p "$BUNDLE_LIB"
    touch "$BUNDLE/Contents/Info.plist"
    echo '<?xml version="1.0" encoding="UTF-8"?>
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>sqlite-mc-driver.program</string>
    <key>CFBundleIdentifier</key>
    <string>io.matthewnelson</string>
    <key>LSUIElement</key>
    <true/>
</dict>
</plist>' > "$BUNDLE/Contents/Info.plist"

    cp "$DIR_ARCH/libsqlitejdbc.dylib" "$BUNDLE_MACOS/sqlite-mc-driver.program"
    mv "$DIR_ARCH/libsqlitejdbc.dylib" "$BUNDLE_LIB/"

    if ! ${RCODESIGN} sign --p12-file "$PATH_KEY_P12" --code-signature-flags runtime "$BUNDLE"; then
      exit 1
    fi

    sleep 1

    if ! ${RCODESIGN} notary-submit --api-key-path "$PATH_KEY_API" --staple "$BUNDLE"; then
      exit 1
    fi

    mv "$BUNDLE_LIB/libsqlitejdbc.dylib" "$DIR_ARCH"
    rm -rf "$BUNDLE"
  done
}

sign_mingw() {
  cd "$DIR_OUT"

  local PATH_KEY=
  printf "Path to .key file (e.g. /home/user/dir/my_key.key): "
  read -r PATH_KEY

  if [ ! -f "$PATH_KEY" ]; then
    echo "File does not exist >> $PATH_KEY"
    exit 1
  fi

  local PATH_CERT=
  printf "Path to cert file (e.g. /home/user/dir/cert.cer): "
  read -r PATH_CERT

  if [ ! -f "$PATH_CERT" ]; then
    echo "File does not exist >> $PATH_CERT"
    exit 1
  fi

  local DIR_ARCH=
  local ARCH_NAME=
  for DIR_ARCH in "$DIR_UNSIGNED/Windows"/*; do
    cd "$DIR_ARCH"
    ARCH_NAME=$(echo "$DIR_ARCH" | rev | cut -d '/' -f 1 | rev)
    mkdir -p "$DIR_SIGNED/Windows/$ARCH_NAME"

    if ! ${OSSLSIGNCODE} sign -certs "$PATH_CERT" \
           -key "$PATH_KEY" \
           -t "http://timestamp.comodoca.com" \
           -in "$DIR_ARCH/sqlitejdbc.dll" \
           -out "$DIR_SIGNED/Windows/$ARCH_NAME/sqlitejdbc.dll"; then
      exit 1
    fi
  done
}

rm -rf "$DIR_SIGNED"
mkdir -p "$DIR_SIGNED"

sign_macos
sign_mingw

TIME_RUN=$(( $(date +%s) - TIME_START ))
echo "
    Script runtime: ${TIME_RUN}s
"
exit 0
