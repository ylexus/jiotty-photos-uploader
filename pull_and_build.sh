#!/usr/bin/env bash

VERSION="$1"
[[ -z "${VERSION}" ]] && {
  echo "Missing version argument"
  exit 1
}

cd "$(dirname "${0}")" || exit 2
mkdir -p build
LOG="pull_and_build.log"
rm "${LOG}"
{
  git fetch --all ; git reset --hard origin/master;
  echo "Version=${VERSION}";
  ./gradlew clean fullPackage "-DVERSION=${VERSION}" "-DCLIENT_SECRET_PATH=$HOME/java/clientSecret.json";
} >>"${LOG}" 2>&1
