#!/usr/bin/env bash

export LC_ALL=C
set -e

readonly TIME_START=$(date +%s)

# Absolute path to the directory which this script resides in
readonly DIR_SCRIPTS=$( cd "$( dirname "$0" )" >/dev/null && pwd )

readonly DIR_EXTERNAL="$DIR_SCRIPTS/.."
readonly DIR_JNI="$DIR_EXTERNAL/jni"
readonly DIR_JDBC="$DIR_JNI/sqlite-jdbc"
readonly DIR_PATCHES="$DIR_JNI/patches"
readonly DIR_OUT="$DIR_JNI/out"
readonly DIR_UNSIGNED="$DIR_OUT/unsigned"

# Programs
readonly GIT=$(which git)
readonly MAKE=$(which make)
readonly DOCKER=$(which docker)

if [ -z $GIT ]; then
    echo "git is required to run this script"
    exit 1
fi

if [ -z $MAKE ]; then
    echo "make is required to run this script"
    exit 1
fi

if [ -z $DOCKER ]; then
    echo "docker is required to run this script"
    exit 1
fi

cd "$DIR_EXTERNAL"
${GIT} submodule update --init

git_patches_apply() {
  local PATCH_FILE=
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

source "$DIR_JDBC/VERSION"

rm -rf "$DIR_JDBC/target"
rm -rf "$DIR_OUT/sqlite-jdbc-"*.jar
rm -rf "$DIR_UNSIGNED"
mkdir -p "$DIR_UNSIGNED"

${MAKE} docker-linux32
${MAKE} docker-linux64
${MAKE} native-all package test

cp -R "$DIR_JDBC/src/main/resources/org/sqlite/native/Mac/" "$DIR_UNSIGNED/"
cp -R "$DIR_JDBC/src/main/resources/org/sqlite/native/Windows/" "$DIR_UNSIGNED/"
echo "Mac/Windows unsigned shared libs have been copied to $DIR_UNSIGNED"

for JAR in "$DIR_JDBC/target/sqlite-jdbc-${version}"*.jar; do
  echo "Copying $JAR to $DIR_OUT"
  cp "$JAR" "$DIR_OUT"
done

rm -rf "$DIR_JDBC/target"

TIME_RUN=$(( $(date +%s) - TIME_START ))
echo "
    Script runtime: ${TIME_RUN}s
"
exit 0
