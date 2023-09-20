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
. "$DIR_SCRIPT/VERSION"
# shellcheck disable=SC2154
readonly DIR_AMALGAMATION="build/sqlite3mc-$version_sqlite3mc-amalgamation"

# Programs
readonly CURL=$(which curl)
readonly DOCKER=$(which docker)
readonly UNZIP=$(which unzip)
readonly XCRUN=$(which xcrun)

readonly U_ID=$(id -u)
readonly G_ID=$(id -g)

OS_NAME=
OS_TARGET=
DIR_TARGET_BUILD=
DIR_TARGET_LIBS=

CFLAGS=
LIB_NAME="libsqlite3mc.a"

CMD_CC=
CMD_AR=

function build:all:desktop { ## Builds all Linux, all macOS, & all Windows libs
  build:all:linux &
  build:all:macos &
  build:all:mingw
  wait
}

function build:all:ios { ## Builds all iOS libs
  build:ios:arm64 &
  build:ios:simulator_arm64 &
  build:ios:x64
  wait
}

function build:all:linux { ## Builds all Linux libs
  build:linux:x64
  wait
}

function build:all:macos { ## Builds all macOS libs
  build:macos:arm64 &
  build:macos:x64
  wait
}

function build:all:mingw { ## Builds all Windows libs
  build:mingw:x64
  wait
}

function build:all:tvos { ## Builds all tvOS libs
  build:tvos:arm64 &
  build:tvos:simulator_arm64 &
  build:tvos:x64
  wait
}

function build:all:watchos { ## Builds all watchosOS libs
  build:watchos:arm32 &
  build:watchos:arm64 &
  build:watchos:simulator_arm64 &
  build:watchos:x64
  wait
}

function build:ios:arm64 { ## Builds iOS arm64
  OS_NAME="ios"
  OS_TARGET="arm64"
  __build:configure:target
}

function build:ios:simulator_arm64 { ## Builds iOS simulator arm64
  OS_NAME="ios"
  OS_TARGET="simulator_arm64"
  __build:configure:target
}

function build:ios:x64 { ## Builds iOS x64
  OS_NAME="ios"
  OS_TARGET="x64"
  __build:configure:target
}

function build:linux:x64 { ## Builds Linux x64
  OS_NAME="linux"
  OS_TARGET="x64"
  __build:configure:target

  __CFLAGS -v -g -O3 -msse4.2 -maes
  __build:configure:SQLITE_FLAGS

  ${DOCKER} build \
    -f "$DIR_SCRIPT/../docker/Dockerfile.debian-linux_x86_64" \
    -t toxicity-io/debian10-linux-x86_64 \
    .

  __build:configure:CMD_CC gcc
  __build:configure:CMD_AR ar rcs

  ${DOCKER} run --rm \
    -u "$U_ID:$G_ID" \
    -v "$DIR_SCRIPT":/work \
    toxicity-io/debian10-linux-x86_64 \
    bash -c "$CMD_CC; $CMD_AR"

  __build:target:package
}

function build:macos:arm64 { ## Builds macOS arm64
  OS_NAME="macos"
  OS_TARGET="arm64"
  __build:configure:target
}

function build:macos:x64 { ## Builds macOS x64
  OS_NAME="macos"
  OS_TARGET="x64"
  __build:configure:target
}

function build:mingw:x64 { ## Builds Windows x64
  OS_NAME="mingw"
  OS_TARGET="x64"
  __build:configure:target
}

function build:tvos:arm64 { ## Builds tvOS arm64
  OS_NAME="tvos"
  OS_TARGET="arm64"
  __build:configure:target
}

function build:tvos:simulator_arm64 { ## Builds tvOS simulator arm64
  OS_NAME="tvos"
  OS_TARGET="simulator_arm64"
  __build:configure:target
}

function build:tvos:x64 { ## Builds tvOS x64
  OS_NAME="tvos"
  OS_TARGET="x64"
  __build:configure:target
}

function build:watchos:arm32 { ## Builds watchOS arm32
  OS_NAME="watchos"
  OS_TARGET="arm32"
  __build:configure:target
}

function build:watchos:arm64 { ## Builds watchOS arm64
  OS_NAME="watchos"
  OS_TARGET="arm64"
  __build:configure:target
}

function build:watchos:simulator_arm64 { ## Builds watchOS simulator arm64
  OS_NAME="watchos"
  OS_TARGET="simulator_arm64"
  __build:configure:target
}

function build:watchos:x64 { ## Builds watchOS x64
  OS_NAME="watchos"
  OS_TARGET="x64"
  __build:configure:target
}

function clean { ## Cleans build directory of old versions and log files
  if [ ! -d "build" ]; then
    return 0
  fi

  if [ -z "$(ls -A "build")" ]; then
    return 0
  fi

  local location=
  for location in "build"/*; do
    if [ "$(echo "$location" | cut -d '-' -f 2)" != "$version_sqlite3mc" ]; then
      rm -rfv "$DIR_SCRIPT/$location"
    elif [ "$(echo "$location" | rev | cut -d '.' -f 1 | rev)" = "log" ]; then
      rm -rfv "$DIR_SCRIPT/$location"
    fi
  done
}

function help { ## THIS MENU
  # shellcheck disable=SC2154
  echo "
    $0
    Copyright (C) 2023 Toxicity

    Build static libs for SQLite3MultipleCiphers v$version_sqlite3mc (based on SQLite v$version_sqlite)

    Location: $DIR_SCRIPT
    Syntax: $0 [task]

    Tasks:
$(
    # function names + comments & colorization
    grep -E '^function .* {.*?## .*$$' "$0" |
    grep -v "^function __" |
    sed -e 's/function //' |
    sort |
    awk 'BEGIN {FS = "{.*?## "}; {printf "        \033[93m%-30s\033[92m %s\033[0m\n", $1, $2}'
)

    Example: $0 build:all:desktop
  "
}

function __init {
  # Ensure always start in the external/native directory
  cd "$DIR_SCRIPT"
  __build:configure:init "$1"
  ${1}
}

function __build:configure:init {
  if ! echo "$1" | grep -q "^build"; then
    return 0
  fi

  mkdir -p "$DIR_AMALGAMATION"

  # Download amalgamations (if needed)
  if [ ! -f "$DIR_AMALGAMATION.zip" ]; then
    __require:cmd "$CURL" "curl"
    echo "Downloading amalgamations..."
    ${CURL} -L -f -o "$DIR_AMALGAMATION.zip" \
      "https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v$version_sqlite3mc/sqlite3mc-$version_sqlite3mc-sqlite-$version_sqlite-amalgamation.zip"
  fi

  # Unzip amalgamation so they can be copied into build target directory
  __require:cmd "$UNZIP" "unzip"
  # shellcheck disable=SC2115
  rm -rf "$DIR_AMALGAMATION/"*
  ${UNZIP} -q "$DIR_AMALGAMATION.zip" -d "$DIR_AMALGAMATION"
}

function __build:configure:target {
  __require:var_set "$OS_NAME" "OS_NAME"
  __require:var_set "$OS_TARGET" "OS_TARGET"

  case "$OS_NAME" in
    "ios"|"tvos"|"watchos")
      __require:cmd "$XCRUN" "xcrun (Xcode CLI tool on macOS machine)"
      ;;
    *)
      __require:cmd "$DOCKER" "docker"
      __require:var_set "$U_ID" "U_ID"
      __require:var_set "$G_ID" "G_ID"
      if [ "$U_ID" = "0" ] || [ "$G_ID" = "0" ]; then
        echo 1>&2 "ERROR: U_ID[$U_ID] or G_ID[$G_ID] unacceptable value (0). Unable to specify non-root docker user."
        exit 3
      fi
      ;;
  esac

  DIR_TARGET_BUILD="build/sqlite3mc-$version_sqlite3mc-$OS_NAME-$OS_TARGET"
  DIR_TARGET_LIBS="libs/$OS_NAME/$OS_TARGET"
  local log_file="$DIR_TARGET_BUILD.log"

  mkdir -p "$DIR_TARGET_BUILD"
  mkdir -p "$DIR_TARGET_LIBS"

  # Prepare logging for target
  echo "LOGS >> $DIR_SCRIPT/$log_file"
  exec 3>&1 4>&2
  trap 'exec 2>&4 1>&3' 0 1 2 3
  exec 1>"$log_file" 2>&1
  # Also pipe formatted errors to >&4
  trap 'echo "ERROR[$OS_NAME-$OS_TARGET]: $0: line[${LINENO}] - command[${BASH_COMMAND}] - code[$?]" >&4' ERR
  trap 'echo "TERMINATED by Ctrl + C"; __log_target_time' SIGINT
  trap '__log_target_time' EXIT
  __log_target_time

  # Prepare target's build directory
  # shellcheck disable=SC2115
  rm -rfv "$DIR_TARGET_BUILD/"*
  cp -Rv "$DIR_AMALGAMATION/"* "$DIR_TARGET_BUILD/"

  # Prepare target's libs directory
  # shellcheck disable=SC2115
  rm -rfv "$DIR_TARGET_LIBS/"*
}

# shellcheck disable=SC2120
function __build:configure:SQLITE_FLAGS {
  __require:var_set "$OS_TARGET" "OS_TARGET"
  __require:var_set "$DIR_TARGET_BUILD" "DIR_TARGET_BUILD"
  __require:var_set "$DIR_TARGET_LIBS" "DIR_TARGET_LIBS"

  __CFLAGS -I"$DIR_TARGET_BUILD" -I"$DIR_TARGET_LIBS"

  __CFLAGS \
    -DSQLITE_HAVE_ISNAN=1 \
    -DHAVE_USLEEP=1 \
    -DSQLITE_ENABLE_COLUMN_METADATA=1 \
    -DSQLITE_CORE=1 \
    -DSQLITE_ENABLE_FTS3=1 \
    -DSQLITE_ENABLE_FTS3_PARENTHESIS=1 \
    -DSQLITE_ENABLE_FTS5=1 \
    -DSQLITE_ENABLE_RTREE=1 \
    -DSQLITE_ENABLE_STAT4=1 \
    -DSQLITE_ENABLE_DBSTAT_VTAB=1 \
    -DSQLITE_ENABLE_MATH_FUNCTIONS=1 \
    -DSQLITE_THREADSAFE=1 \
    -DSQLITE_DEFAULT_MEMSTATUS=0 \
    -DSQLITE_DEFAULT_FILE_PERMISSIONS=0666 \
    -DSQLITE_MAX_VARIABLE_NUMBER=250000 \
    -DSQLITE_MAX_MMAP_SIZE=1099511627776 \
    -DSQLITE_MAX_LENGTH=2147483647 \
    -DSQLITE_MAX_COLUMN=32767 \
    -DSQLITE_MAX_SQL_LENGTH=1073741824 \
    -DSQLITE_MAX_FUNCTION_ARG=127 \
    -DSQLITE_MAX_ATTACHED=125 \
    -DSQLITE_MAX_PAGE_COUNT=4294967294 \
    -DSQLITE_DQS=0 \
    -DCODEC_TYPE=CODEC_TYPE_CHACHA20 \
    -DSQLITE_ENABLE_EXTFUNC=1 \
    -DSQLITE_ENABLE_REGEXP=1 \
    -DSQLITE_TEMP_STORE=2 \
    -DSQLITE_USE_URI=1 \
    "$*"
}

function __build:configure:CMD_CC {
  __require:not_empty "$1" "Compiler argument must be passed (e.g. clang, gcc, ...)"
  __require:var_set "$CFLAGS" "CFLAGS"
  __require:var_set "$DIR_TARGET_BUILD" "DIR_TARGET_BUILD"
  __require:var_set "$DIR_TARGET_LIBS" "DIR_TARGET_LIBS"

  CMD_CC="$* $CFLAGS -c $DIR_TARGET_BUILD/sqlite3mc_amalgamation.c -o $DIR_TARGET_BUILD/sqlite3.o"
  echo "CMD_CC: $CMD_CC"
}

function __build:configure:CMD_AR {
  __require:not_empty "$1" "Archiver argument must be passed (e.g. ar, libtool, ...)"
  __require:var_set "$DIR_TARGET_BUILD" "DIR_TARGET_BUILD"
  __require:var_set "$DIR_TARGET_LIBS" "DIR_TARGET_LIBS"
  __require:var_set "$LIB_NAME" "LIB_NAME"

  CMD_AR="$* $DIR_TARGET_LIBS/$LIB_NAME $DIR_TARGET_BUILD/sqlite3.o"
  echo "CMD_AR: $CMD_AR"
}

function __build:target:package {
  __require:var_set "$DIR_TARGET_BUILD" "DIR_TARGET_BUILD"
  __require:var_set "$DIR_TARGET_LIBS" "DIR_TARGET_LIBS"
  __require:var_set "$LIB_NAME" "LIB_NAME"

  echo ""
  chmod -v 755 "$DIR_TARGET_LIBS/$LIB_NAME"
  mkdir -pv "$DIR_TARGET_LIBS/include"
  cp -v "$DIR_TARGET_BUILD/sqlite3mc_amalgamation.h" "$DIR_TARGET_LIBS/include/sqlite3mc.h"
}

function __CFLAGS {
  CFLAGS+=" $*"
}

function __require:cmd {
  __require:not_empty "$1" "$2 is required to run this script"
}

function __require:var_set {
  __require:not_empty "$1" "$2 must be set"
}

function __require:not_empty {
  if [ -n "$1" ]; then
    return 0
  fi

  echo 1>&2 "ERROR: $2"
  exit 3
}

function __log_target_time {
  if [ -z "$OS_NAME" ] || [ -z "$OS_TARGET" ]; then
    return 0
  fi

  echo "-- [$(date '+%Y-%m-%d %H:%M:%S')] -- $OS_NAME-$OS_TARGET"
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
  __init "$1"
fi
