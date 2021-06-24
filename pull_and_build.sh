#!/usr/bin/env bash
while ! ping -c 1 -W 1 github.com; do
  echo "Waiting for github.com - network interface might be down..."
  sleep 1
done

set -e
VERSION="$1"
[[ -z "${VERSION}" ]] && {
  echo "Missing version argument"
  exit 1
}

cd "$(dirname "${0}")" || exit 2
mkdir -p build
LOG="pull_and_build.log"
rm -f "${LOG}"
[[ "$(uname -a)" != *raspberrypi* ]] && export JAVA_HOME="$HOME/java/jdk"
{
  git fetch --all
  git reset --hard origin/master
  echo "Version=${VERSION}"
  if [[ "$(uname -a)" == *raspberrypi* ]]; then
    RPI_ARG="-PraspberryPi"
    echo "Building on Raspberry PI"
  else
    RPI_ARG=""
  fi
  ./gradlew clean fullPackage "${RPI_ARG}" "-DVERSION=${VERSION}" "-DCLIENT_SECRET_PATH=$HOME/java/clientSecret.json"
} >>"${LOG}" 2>&1
