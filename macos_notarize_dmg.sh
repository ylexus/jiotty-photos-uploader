#!/usr/bin/env bash
set -e

PROJECT_DIR="$PWD"
BUILD_DIR="${PROJECT_DIR}/build"
CODESIGN_IDENTITY="Developer ID Application: Alexey Yudichev (J4R72JZQ9P)"
APP_NAME="${1}"
VERSION="${2}"
[[ -z "${APP_NAME}" ]] && { echo "app name missing" ; exit 1; }
[[ -z "${VERSION}" ]] && { echo "version missing" ; exit 1; }

echo "Signing and notarizing $1 version $2"

function sign_jar_internals() {
    local jar_path="$1"
    local tmp_unpacked_path="${BUILD_DIR}/tmp/${jar_path}_unpacked"
    rm -rf "${tmp_unpacked_path}"
    mkdir -p "${tmp_unpacked_path}"
    unzip -q "${jar_path}" -d "${tmp_unpacked_path}"
    find "${tmp_unpacked_path}" \
      -type f \
      -name "*lib" \
      -exec codesign \
      -f \
      --timestamp \
      --entitlements "${PROJECT_DIR}/src/main/packaging-resources/macOS/entitlements.plist" \
      -s "${CODESIGN_IDENTITY}"\
      --prefix net.yudichev.googlephotosupload.ui.\
      --options runtime \
      -vvvv \
      {} \;

    rm -f "${jar_path}"
    (cd "${tmp_unpacked_path}" && zip -q -r "${jar_path}" ./*)
    echo "signed ${jar_path}"
}

sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app/grpc-netty-shaded-1.21.0.jar"
sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app/javafx-graphics-13.0.2-mac.jar"
sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app/javafx-media-13.0.2-mac.jar"
sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app/javafx-web-13.0.2-mac.jar"

find "${BUILD_DIR}/jpackage/${APP_NAME}.app" -type f \
  -not -path "*/Contents/runtime/*" \
  -not -path "*/Contents/MacOS/my-app" \
  -not -path "*libapplauncher.dylib" \
  -exec codesign \
  --timestamp \
  --entitlements "src/main/packaging-resources/macOS/entitlements.plist" \
  -s "${CODESIGN_IDENTITY}"\
  --prefix net.yudichev.googlephotosupload.ui.\
  --options runtime \
  -vvvv \
  {} \;

find "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/runtime" -type f \
  -not -path "*/legal/*" \
  -not -path "*/man/*" \
  -exec codesign \
  -f \
  --timestamp \
  --entitlements "src/main/packaging-resources/macOS/entitlements.plist" \
  -s "${CODESIGN_IDENTITY}" \
  --prefix net.yudichev.googlephotosupload.ui. \
  --options runtime \
  -vvvv \
  {} \;

codesign \
  -f \
  --timestamp \
  --entitlements "src/main/packaging-resources/macOS/entitlements.plist" \
  -s "${CODESIGN_IDENTITY}" \
  --prefix net.yudichev.googlephotosupload.ui. \
  --options runtime \
  -vvvv \
  "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/runtime"

codesign \
  -f \
  --timestamp \
  --entitlements "src/main/packaging-resources/macOS/entitlements.plist" \
  -s "${CODESIGN_IDENTITY}" \
  --prefix net.yudichev.googlephotosupload.ui. \
  --options runtime \
  -vvvv \
  "${BUILD_DIR}/jpackage/${APP_NAME}.app"

/Library/Java/JavaVirtualMachines/jdk-14.jdk/Contents/Home/bin/jpackage \
  --type dmg \
  --dest "${BUILD_DIR}/jpackage" \
  --name "${APP_NAME}" \
  --app-version "${VERSION}" \
  --app-image "${BUILD_DIR}/jpackage/${APP_NAME}.app" \
  --resource-dir "${PROJECT_DIR}/src/main/packaging-resources/macOS/out"

xcrun altool \
  --notarize-app \
  --primary-bundle-id "net.yudichev.jiottyphotosupload.dmg" \
  --username "a@yudichev.net" --password "@keychain:Apple Notarization Tool App Password" \
  --file "${BUILD_DIR}/jpackage/${APP_NAME}-${VERSION}.dmg"