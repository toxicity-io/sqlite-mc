#!/bin/sh
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

readonly TIME_START=$(date +%s)

# Absolute path to the directory which this script resides in
readonly DIR_SCRIPT=$( cd "$( dirname "$0" )" >/dev/null && pwd )

readonly DIR_JDBC="$DIR_SCRIPT/sqlite-jdbc"
readonly DIR_PATCHES="$DIR_SCRIPT/patches"
readonly DIR_LIBS="$DIR_SCRIPT/libs"
readonly DIR_UNSIGNED="$DIR_LIBS/unsigned"

# Programs
readonly GIT=$(which git)
readonly MAKE=$(which make)
readonly DOCKER=$(which docker)

if [ -z "$GIT" ]; then
    echo "git is required to run this script"
    exit 1
fi

if [ -z "$MAKE" ]; then
    echo "make is required to run this script"
    exit 1
fi

if [ -z "$DOCKER" ]; then
    echo "docker is required to run this script"
    exit 1
fi

cd "$DIR_SCRIPT"
${GIT} submodule update --init

git_patches_apply() {
  for PATCH_FILE in "$DIR_PATCHES"/*.patch; do
    echo "Applying git patch $PATCH_FILE"
    ${GIT} apply "$PATCH_FILE"
    sleep 0.25
  done
}

git_stash_drop() {
  cd "$DIR_JDBC"
  ${GIT} add --all
  ${GIT} stash
  ${GIT} stash drop
}

cd "$DIR_JDBC"
trap git_stash_drop EXIT
git_patches_apply

if ! . "$DIR_JDBC/VERSION"; then
  echo "Failed to source VERSION file"
  exit 1
fi

rm -rf "$DIR_JDBC/target"
rm -rf "$DIR_LIBS/sqlite-jdbc-"*.jar
rm -rf "$DIR_UNSIGNED"
mkdir -p "$DIR_UNSIGNED"

${MAKE} docker-linux32
${MAKE} docker-linux64
${MAKE} native-all package test

cp -R "$DIR_JDBC/src/main/resources/org/sqlite/native/Mac/" "$DIR_UNSIGNED/"
cp -R "$DIR_JDBC/src/main/resources/org/sqlite/native/Windows/" "$DIR_UNSIGNED/"
echo "Mac/Windows unsigned shared libs have been copied to $DIR_UNSIGNED"

# shellcheck disable=SC2154
for JAR in "$DIR_JDBC/target/sqlite-jdbc-${version}"*.jar; do
  echo "Copying $JAR to $DIR_LIBS"
  cp "$JAR" "$DIR_LIBS"
done

TIME_RUN=$(( $(date +%s) - TIME_START ))
echo "
    Script runtime: ${TIME_RUN}s
"
exit 0
