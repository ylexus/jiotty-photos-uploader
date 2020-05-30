#!/usr/bin/env bash

NOTARIZATION_TIMEOUT_SEC=600
NOTARIZATION_TIME_INCREMENT_SEC=5
PROJECT_DIR="$PWD"
BUILD_DIR="${PROJECT_DIR}/build"
CODESIGN_IDENTITY="Developer ID Application: Alexey Yudichev (J4R72JZQ9P)"
APP_NAME="${1}"
[[ -z "${APP_NAME}" ]] && {
  echo "app name missing"
  exit 1
}
VERSION="${2}"
[[ -z "${VERSION}" ]] && {
  echo "version missing"
  exit 1
}
JAVA_HOME="${3}"
[[ -z "${JAVA_HOME}" ]] && {
  echo "java home missing"
  exit 1
}

echo "Signing and notarizing $1 version $2"

function sign_jar_internals() {
  local jar_dir="$1"
  local jar_file_pattern="$2"
  local jar_path
  jar_path="$(find "${jar_dir}" -name "${jar_file_pattern}")"
  local tmp_unpacked_path="${BUILD_DIR}/tmp/${jar_path}_unpacked"

  rm -rf "${tmp_unpacked_path}"
  if [[ $? -ne 0 ]]; then
    echo >&2 "Failed to delete ${tmp_unpacked_path}"
    exit $?
  fi

  mkdir -p "${tmp_unpacked_path}"
  if [[ $? -ne 0 ]]; then
    echo >&2 "Failed to create ${tmp_unpacked_path}"
    exit $?
  fi

  unzip -q "${jar_path}" -d "${tmp_unpacked_path}"
  if [[ $? -ne 0 ]]; then
    echo >&2 "Failed to unzip"
    exit $?
  fi

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
  if [[ $? -ne 0 ]]; then
    echo >&2 "Failed to sign"
    exit $?
  fi

  rm -f "${jar_path}"
  if [[ $? -ne 0 ]]; then
    echo >&2 "Failed to delete ${jar_path}"
    exit $?
  fi

  (cd "${tmp_unpacked_path}" && zip -q -r "${jar_path}" ./*)
  if [[ $? -ne 0 ]]; then
    echo >&2 "Failed to zip"
    exit $?
  fi

  echo "signed ${jar_path}"
}

sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app" "grpc-netty-shaded-*.jar"
sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app" "javafx-graphics-*-mac.jar"
sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app" "javafx-media-*-mac.jar"
sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app" "javafx-web-*-mac.jar"
sign_jar_internals "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/app" "conscrypt-openjdk-uber-*.jar"

find "${BUILD_DIR}/jpackage/${APP_NAME}.app" -type f \
  -not -path "*/Contents/runtime/*" \
  -not -path "*/Contents/MacOS/${APP_NAME}" \
  -not -path "*libapplauncher.dylib" \
  -exec codesign \
  --timestamp \
  --entitlements "${PROJECT_DIR}/src/main/packaging-resources/macOS/entitlements.plist" \
  -s "${CODESIGN_IDENTITY}" \
  --prefix net.yudichev.googlephotosupload.ui. \
  --options runtime \
  -vvvv \
  {} \;
if [[ $? -ne 0 ]]; then
  echo >&2 "Failed to sign"
  exit $?
fi

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
if [[ $? -ne 0 ]]; then
  echo >&2 "Failed to sign"
  exit $?
fi

codesign \
  -f \
  --timestamp \
  --entitlements "${PROJECT_DIR}/src/main/packaging-resources/macOS/entitlements.plist" \
  -s "${CODESIGN_IDENTITY}" \
  --prefix net.yudichev.googlephotosupload.ui. \
  --options runtime \
  -vvvv \
  "${BUILD_DIR}/jpackage/${APP_NAME}.app/Contents/runtime"
if [[ $? -ne 0 ]]; then
  echo >&2 "Failed to sign"
  exit $?
fi

codesign \
  -f \
  --timestamp \
  --entitlements "${PROJECT_DIR}/src/main/packaging-resources/macOS/entitlements.plist" \
  -s "${CODESIGN_IDENTITY}" \
  --prefix net.yudichev.googlephotosupload.ui. \
  --options runtime \
  -vvvv \
  "${BUILD_DIR}/jpackage/${APP_NAME}.app"
if [[ $? -ne 0 ]]; then
  echo >&2 "Failed to sign"
  exit $?
fi

"$JAVA_HOME/bin/jpackage" \
  --type dmg \
  --dest "${BUILD_DIR}/jpackage" \
  --name "${APP_NAME}" \
  --app-version "${VERSION}" \
  --app-image "${BUILD_DIR}/jpackage/${APP_NAME}.app" \
  --resource-dir "${PROJECT_DIR}/src/main/packaging-resources/macOS/out"
if [[ $? -ne 0 ]]; then
  echo >&2 "jpackage failed"
  exit $?
fi

echo "Uploading package for notarization..."
notarize_output="$(xcrun altool \
  --notarize-app \
  --primary-bundle-id "net.yudichev.jiottyphotosupload.dmg" \
  --username "a@yudichev.net" --password "@keychain:Apple Notarization Tool App Password" \
  --file "${BUILD_DIR}/jpackage/${APP_NAME}-${VERSION}.dmg" 2>&1)"
if [[ $? -ne 0 ]]; then
  echo >&2 "failed to invoke notarization:"
  echo >&2 "${notarize_output}"
  exit $?
fi

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
while [[ $notarization_waited_sec -lt $NOTARIZATION_TIMEOUT_SEC ]] && [[ "${notarization_status}" != "success" ]]; do
  sleep $NOTARIZATION_TIME_INCREMENT_SEC
  notarization_waited_sec=$((notarization_waited_sec + NOTARIZATION_TIME_INCREMENT_SEC))
  notarization_status_str="$(xcrun altool --notarization-history 0 --username "a@yudichev.net" --password "@keychain:Apple Notarization Tool App Password" 2>&1)"
  if [[ $? -ne 0 ]]; then
    echo >&2 "WARN: failed to check notarization history, output was: ${notarization_status_str}"
  else
    notarization_status_str="$(echo "${notarization_status_str}" | grep "${request_uuid}")"
  fi
  echo "${notarization_status_str}"
  notarization_status="$(echo "${notarization_status_str}" | awk '{print $5}')"
done
if [[ "${notarization_status}" != "success" ]]; then
  echo >&2 "Failed to notarize"
  exit 1
fi

echo "Notarized, stapling..."
xcrun stapler staple "${BUILD_DIR}/jpackage/${APP_NAME}-${VERSION}.dmg"
if [[ $? -ne 0 ]]; then
  echo >&2 "Failed to staple"
  exit $?
fi

echo "All done"
