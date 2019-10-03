#!/usr/bin/env bash

set -e

# shellcheck disable=SC2154
java -jar "${project.build.finalName}.${project.packaging}" $*