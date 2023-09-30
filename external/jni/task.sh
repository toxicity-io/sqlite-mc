#!/usr/bin/env bash
# Copyright (c) 2023 Toxicity
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
export LC_ALL=C
set -e

readonly DIR_SCRIPT=$( cd "$( dirname "$0" )" >/dev/null && pwd )

readonly DIR_JDBC="$DIR_SCRIPT/sqlite-jdbc"
readonly DIR_LIBS="$DIR_SCRIPT/libs"
readonly DIR_PATCHES="$DIR_SCRIPT/patches"
readonly DIR_SIGNED="$DIR_LIBS/signed"
readonly DIR_UNSIGNED="$DIR_LIBS/unsigned"

# Programs
readonly DOCKER=$(which docker)
readonly GIT=$(which git)
readonly MAKE=$(which make)
readonly OSSLSIGNCODE=$(which osslsigncode)
readonly RCODESIGN=$(which rcodesign)

function build { ## Build sqlite-jdbc native libs and package .jar file
  __require:cmd "$DOCKER" "docker"
  __require:cmd "$GIT" "git"
  __require:cmd "$MAKE" "make"

  ${GIT} submodule update --init

  cd "$DIR_JDBC"
  trap '__build:git:stash' EXIT SIGINT
  __build:git:apply_patches

  . "$DIR_JDBC/VERSION"

  __build:clean
  mkdir -p "$DIR_UNSIGNED"

  ${DOCKER} build \
    -f "$DIR_SCRIPT/../docker/Dockerfile.ubuntu16-linux-x86" \
    -t toxicity-io/ubuntu16-linux-x86 \
    .
  ${DOCKER} build \
    -f "$DIR_SCRIPT/../docker/Dockerfile.ubuntu16-linux-x86_64" \
    -t toxicity-io/ubuntu16-linux-x86_64 \
    .
  ${MAKE} native-all package test

  cp -Rv "$DIR_JDBC/src/main/resources/org/sqlite/native/Mac/" "$DIR_UNSIGNED/"
  cp -Rv "$DIR_JDBC/src/main/resources/org/sqlite/native/Windows/" "$DIR_UNSIGNED/"

  local jar_jdbc=
  # shellcheck disable=SC2154
  for jar_jdbc in "$DIR_JDBC/target/sqlite-jdbc-${version}"*.jar; do
    cp -v "$jar_jdbc" "$DIR_LIBS"
  done

  # Also copy over amalgamations to library/driver so everything is in sync version wise
  local dir_sqlite3mc="$DIR_SCRIPT/../../library/driver/sqlite3mc"
  rm -rf "$dir_sqlite3mc"
  mkdir -p "$dir_sqlite3mc"

  local dir_amal=
  for dir_amal in "$DIR_JDBC/target/sqlite-amalgamation-"*; do
    cp -v "$dir_amal/sqlite3mc_amalgamation.c" "$dir_sqlite3mc/sqlite3mc.c"
    cp -v "$dir_amal/sqlite3mc_amalgamation.h" "$dir_sqlite3mc/sqlite3mc.h"
    break
  done
}

function sign:macos { ## Sign and notarize macOS libs. 2 ARGS - [1]: /path/to/key.p12  [2]: /path/to/app/store/connect/api_key.json
  # shellcheck disable=SC2128
  if [ $# -ne 2 ]; then
    echo 1>&2 "Usage: $0 $FUNCNAME 2 ARGS - [1]: /path/to/key.p12 [2]: /path/to/app/store/connect/api_key.json"
    exit 3
  fi

  local file_key_p12="$1"
  local file_key_api="$2"
  __require:file_exists "$file_key_p12"
  __require:file_exists "$file_key_api"
  __require:cmd "$RCODESIGN" "rcodesign"

  mkdir -p "$DIR_SIGNED"
  rm -rf "$DIR_SIGNED/Mac"
  trap 'rm -rf "$DIR_SIGNED/Mac"' SIGINT ERR
  cp -R "$DIR_UNSIGNED/Mac" "$DIR_SIGNED"

  local dir_arch dir_bundle dir_bundle_macos dir_bundle_libs
  for dir_arch in "$DIR_SIGNED/Mac"/*; do
    dir_bundle="$dir_arch/SQLiteMCDriver.app"
    dir_bundle_macos="$dir_bundle/Contents/MacOS"
    dir_bundle_libs="$dir_bundle_macos/sqlite-jdbc"

    rm -rf "$dir_bundle"
    mkdir -p "$dir_bundle_libs"
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
</plist>' > "$dir_bundle/Contents/Info.plist"

    cp "$dir_arch/libsqlitejdbc.dylib" "$dir_bundle_macos/sqlite-mc-driver.program"
    mv "$dir_arch/libsqlitejdbc.dylib" "$dir_bundle_libs/"

    ${RCODESIGN} sign --p12-file "$file_key_p12" --code-signature-flags runtime "$dir_bundle"

    sleep 1

    ${RCODESIGN} notary-submit --api-key-path "$file_key_api" --staple "$dir_bundle"

    mv -v "$dir_bundle_libs/libsqlitejdbc.dylib" "$dir_arch"
    rm -rf "$dir_bundle"
  done
}

function sign:mingw { ## Sign Windows libs.            2 ARGS - [1]: /path/to/file.key [2]: /path/to/cert.cer
  # shellcheck disable=SC2128
  if [ $# -ne 2 ]; then
    echo 1>&2 "Usage: $0 $FUNCNAME 2 ARGS - [1]: /path/to/file.key [2]: /path/to/cert.cer"
    exit 3
  fi

  local file_key="$1"
  local file_cert="$2"
  __require:file_exists "$file_key"
  __require:file_exists "$file_cert"
  __require:cmd "$OSSLSIGNCODE" "osslsigncode"

  mkdir -p "$DIR_SIGNED"
  rm -rf "$DIR_SIGNED/Windows"
  trap 'rm -rf "$DIR_SIGNED/Windows"' SIGINT ERR

  local dir_arch arch_name
  for dir_arch in "$DIR_UNSIGNED/Windows"/*; do
    arch_name=$(echo "$dir_arch" | rev | cut -d '/' -f 1 | rev)

    mkdir -p "$DIR_SIGNED/Windows/$arch_name"

    ${OSSLSIGNCODE} sign -certs "$file_cert" \
      -key "$file_key" \
      -t "http://timestamp.comodoca.com" \
      -in "$dir_arch/sqlitejdbc.dll" \
      -out "$DIR_SIGNED/Windows/$arch_name/sqlitejdbc.dll"
  done
}

function help { ## THIS MENU
  # shellcheck disable=SC2154
  echo "
    $0
    Copyright (C) 2023 Toxicity

    Build, package, and codesign sqlite-jdbc

    Location: $DIR_SCRIPT
    Syntax: $0 [task] [args]

    Tasks:
$(
    # function comments + colorization
    grep -E '^function .* {.*?## .*$$' "$0" |
    grep -v "^function __" |
    sed -e 's/function //' |
    sort |
    awk 'BEGIN {FS = "{.*?## "}; {printf "        \033[93m%-30s\033[92m %s\033[0m\n", $1, $2}'
)

    Example: $0 sign:macos ~/path/to/key.p12 ~/path/to/app/store/connect/api_key.json
  "
}

function __build:clean {
  rm -rf "$DIR_JDBC/target"
  rm -rf "$DIR_LIBS/sqlite-jdbc-"*.jar
  rm -rf "$DIR_UNSIGNED"
}

function __build:git:apply_patches {
  cd "$DIR_JDBC"
  local patch_file=
  for patch_file in "$DIR_PATCHES"/*.patch; do
    echo "Applying git patch $patch_file"
    ${GIT} apply "$patch_file"
    sleep 0.25
  done
}

function __build:git:stash {
  cd "$DIR_JDBC"
  ${GIT} add --all
  ${GIT} stash
  ${GIT} stash drop
}

function __require:cmd {
  if [ -n "$1" ]; then
    return 0
  fi

  echo 1>&2 "ERROR: $2 is required to run this script"
  exit 3
}

function __require:file_exists {
  if [ -f "$1" ]; then
    return 0
  fi

  echo 1>&2 "File does not exist $1"
  exit 3
}

# Run
if [ -z "$1" ] || [ "$1" = "help" ] || echo "$1" | grep -q "^__"; then
  help
elif ! grep -qE "^function $1 {" "$0"; then
  echo 1>&2 "

    ERROR: Unknown task '$1'
  "
  help
else
  # Ensure always start in the external/jni directory
  cd "$DIR_SCRIPT"
  TIMEFORMAT="Task '$1' completed in %3lR"
  time "$@"
fi
