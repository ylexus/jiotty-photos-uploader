#!/usr/bin/env bash
set -e

NOTARIZATION_TIMEOUT_SEC=600
NOTARIZATION_TIME_INCREMENT_SEC=5
PROJECT_DIR="$PWD"
BUILD_DIR="${PROJECT_DIR}/build"
CODESIGN_IDENTITY="Developer ID Application: Alexey Yudichev (J4R72JZQ9P)"
APP_NAME="${1}"
VERSION="${2}"
[[ -z "${APP_NAME}" ]] && {
  echo "app name missing"
  exit 1
}
[[ -z "${VERSION}" ]] && {
  echo "version missing"
  exit 1
}

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
    -s "${CODESIGN_IDENTITY}" \
    --prefix net.yudichev.googlephotosupload.ui. \
    --options runtime \
    -vvvv \
    {} \;

  rm -f "${jar_path}"
  (cd "${tmp_unpacked_path}" && zip -q -r "${jar_path}" ./*)
  echo "signed ${jar_path}"
}

sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app/grpc-netty-shaded-1.21.0.jar"
sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app/javafx-graphics-14-mac.jar"
sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app/javafx-media-14-mac.jar"
sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app/javafx-web-14-mac.jar"

find "${BUILD_DIR}/jpackage/${APP_NAME}.app" -type f \
  -not -path "*/Contents/runtime/*" \
  -not -path "*/Contents/MacOS/my-app" \
  -not -path "*libapplauncher.dylib" \
  -exec codesign \
  --timestamp \
  --entitlements "${PROJECT_DIR}/src/main/packaging-resources/macOS/entitlements.plist" \
  -s "${CODESIGN_IDENTITY}" \
  --prefix net.yudichev.googlephotosupload.ui. \
  --options runtime \
  -vvvv \
  {} \;

find "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/runtime" -type f \
  -not -path "*/legal/*" \
  -not -path "*/man/*" \
  -exec codesign \
  -f \
  --timestamp \
  --entitlements "${PROJECT_DIR}/src/main/packaging-resources/macOS/entitlements.plist" \
  -s "${CODESIGN_IDENTITY}" \
  --prefix net.yudichev.googlephotosupload.ui. \
  --options runtime \
  -vvvv \
  {} \;

codesign \
  -f \
  --timestamp \
  --entitlements "${PROJECT_DIR}/src/main/packaging-resources/macOS/entitlements.plist" \
  -s "${CODESIGN_IDENTITY}" \
  --prefix net.yudichev.googlephotosupload.ui. \
  --options runtime \
  -vvvv \
  "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/runtime"

codesign \
  -f \
  --timestamp \
  --entitlements "${PROJECT_DIR}/src/main/packaging-resources/macOS/entitlements.plist" \
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

echo "Uploading package for notarization..."
notarize_output="$(xcrun altool \
  --notarize-app \
  --primary-bundle-id "net.yudichev.jiottyphotosupload.dmg" \
  --username "a@yudichev.net" --password "@keychain:Apple Notarization Tool App Password" \
  --file "${BUILD_DIR}/jpackage/${APP_NAME}-${VERSION}.dmg")"

request_uuid="$(echo "${notarize_output}" | grep "RequestUUID = " | awk '{print $3}')"
if [[ -z "${request_uuid}" ]]; then
  echo >&2 "Notarization command failed, output was:"
  echo "${notarize_output}"
  exit 1
fi
echo "Notarization command success, request ID ${request_uuid}, full output was:"
echo "${notarize_output}"

notarization_waited_sec=0
echo "Waiting for a max of ${NOTARIZATION_TIMEOUT_SEC} seconds for the package to be notarized..."
while [[ $notarization_waited_sec -lt $NOTARIZATION_TIMEOUT_SEC ]] && [[ "${notaizaiton_status}" != "success" ]]; do
  sleep $NOTARIZATION_TIME_INCREMENT_SEC
  notarization_waited_sec=$((notarization_waited_sec + NOTARIZATION_TIME_INCREMENT_SEC))
  notaizaiton_status_str="$(xcrun altool --notarization-history 0 --username "a@yudichev.net" --password "@keychain:Apple Notarization Tool App Password" |
    grep "${request_uuid}")"
  echo "${notaizaiton_status_str}"
  notaizaiton_status="$(echo "${notaizaiton_status_str}" | awk '{print $5}')"
done
if [[ "${notaizaiton_status}" != "success" ]]; then
  echo >&2 "Failed to notarize"
  exit 1
fi

echo "Notarized, stapling..."
xcrun stapler staple "${BUILD_DIR}/jpackage/${APP_NAME}-${VERSION}.dmg"

echo "All done"
