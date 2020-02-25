#!/usr/bin/env bash
PROJECT_DIR="$PWD"

xcrun stapler staple ${PROJECT_DIR}/build/jpackage/*.dmg
