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
readonly DIR_AMAL_ZIP="build/sqlite3mc-$version_sqlite3mc-amalgamation"

# Programs
readonly CURL=$(which curl)
readonly DOCKER=$(which docker)
readonly UNZIP=$(which unzip)
readonly XCRUN=$(which xcrun)

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
  local os_name="ios"
  local os_target="arm64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:ios:simulator_arm64 { ## Builds iOS simulator arm64
  local os_name="ios"
  local os_target="simulator_arm64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:ios:x64 { ## Builds iOS x64
  local os_name="ios"
  local os_target="x64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:linux:x64 { ## Builds Linux x64
  local os_name="linux"
  local os_target="x64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:macos:arm64 { ## Builds macOS arm64
  local os_name="macos"
  local os_target="arm64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:macos:x64 { ## Builds macOS x64
  local os_name="macos"
  local os_target="x64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:mingw:x64 { ## Builds Windows x64
  local os_name="mingw"
  local os_target="x64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:tvos:arm64 { ## Builds tvOS arm64
  local os_name="tvos"
  local os_target="arm64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:tvos:simulator_arm64 { ## Builds tvOS simulator arm64
  local os_name="tvos"
  local os_target="simulator_arm64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:tvos:x64 { ## Builds tvOS x64
  local os_name="tvos"
  local os_target="x64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:watchos:arm32 { ## Builds watchOS arm32
  local os_name="watchos"
  local os_target="arm32"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:watchos:arm64 { ## Builds watchOS arm64
  local os_name="watchos"
  local os_target="arm64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:watchos:simulator_arm64 { ## Builds watchOS simulator arm64
  local os_name="watchos"
  local os_target="simulator_arm64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
  __build:configure:target
}

function build:watchos:x64 { ## Builds watchOS x64
  local os_name="watchos"
  local os_target="x64"
  local dir_target_build dir_target_libs
  trap '__log_target_time' RETURN
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
    elif [ "$(echo "$location" | rev | cut -d '.' -f 1)" = "gol" ]; then
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
    # function comments + colorization
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
  "$1"
}

function __build:configure:init {
  if ! echo "$1" | grep -q "^build"; then
    return 0
  fi

  mkdir -p "$DIR_AMAL_ZIP"

  # Download amalgamations (if needed)
  if [ ! -f "$DIR_AMAL_ZIP.zip" ]; then
    __require:cmd "$CURL" "curl"
    echo "Downloading amalgamations..."
    ${CURL} -L -f -o "$DIR_AMAL_ZIP.zip" \
      "https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v$version_sqlite3mc/sqlite3mc-$version_sqlite3mc-sqlite-$version_sqlite-amalgamation.zip"
  fi

  # Unzip amalgamation so they can be copied into build target directory
  __require:cmd "$UNZIP" "unzip"
  # shellcheck disable=SC2115
  rm -rf "$DIR_AMAL_ZIP/"*
  ${UNZIP} -q "$DIR_AMAL_ZIP.zip" -d "$DIR_AMAL_ZIP"
}

function __build:configure:target {
  if [ -z "$os_name" ] || [ -z "$os_target" ]; then
    echo "ERROR: os_name & os_target must be set locally from function caller"
    exit 1
  fi

  case "$os_name" in
    "ios"|"tvos"|"watchos")
      __require:cmd "$XCRUN" "xcrun (Xcode CLI tool on macOS machine)"
      ;;
    *)
      __require:cmd "$DOCKER" "docker"
      ;;
  esac

  dir_target_build="build/sqlite3mc-$version_sqlite3mc-$os_name-$os_target"
  dir_target_libs="libs/$os_name/$os_target"
  local log_file="$dir_target_build.log"

  mkdir -p "$dir_target_build"
  mkdir -p "$dir_target_libs"

  # Prepare logging for target
  echo "LOGS >> $DIR_SCRIPT/$log_file"
  exec 3>&1 4>&2
  trap 'exec 2>&4 1>&3' 0 1 2 3
  exec 1>"$log_file" 2>&1
  # Also pipe formatted errors to >&4
  trap 'echo "ERROR[$os_name-$os_target]: $0: line[${LINENO}] - command[${BASH_COMMAND}] - code[$?]" >&4' ERR
  trap 'echo "TERMINATED by Ctrl + C"; __log_target_time' SIGINT
  __log_target_time

  # Prepare target's build directory
  # shellcheck disable=SC2115
  rm -rfv "$dir_target_build/"*
  cp -Rv "$DIR_AMAL_ZIP/"* "$dir_target_build/"

  # Prepare target's libs directory
  # shellcheck disable=SC2115
  rm -rfv "$dir_target_libs/"*

  # TODO: Remove
  sleep "$(shuf -i 2-14 -n 1)"

  # TODO: Flags
}

function __require:cmd {
  if [ -n "$1" ]; then
    return 0
  fi

  echo 1>&2 "ERROR: $2 is required to run this script"
  exit 3
}

function __log_target_time {
  if [ -z "$os_name" ] || [ -z "$os_target" ]; then
    return 0
  fi

  echo "-- [$(date '+%Y-%m-%d %H:%M:%S')] -- $os_name-$os_target"
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
