#!/usr/bin/env bash
# Signs a DMG, has Apple notarize it, staples the ticket and checks that Gatekeeper accepts the result.
#
# The notary credentials are read from a keychain profile, created once with
#   xcrun notarytool store-credentials <profile> --apple-id <Apple ID> --team-id <team ID>
# (it asks for an app-specific password, generated at account.apple.com), or with --key/--key-id/--issuer for an App Store Connect API key.
#
# Usage: notarize-dmg.sh <dmg> <signing identity> <notarytool keychain profile>
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo >&2 "Usage: $0 <dmg> <signing identity> <notarytool keychain profile>"
  exit 2
fi
dmg="$1"
identity="$2"
profile="$3"

codesign --force --timestamp --sign "${identity}" "${dmg}"

echo "Submitting ${dmg} for notarization, this usually takes a few minutes..."
result_plist="$(mktemp)"
trap 'rm -f "${result_plist}"' EXIT
xcrun notarytool submit "${dmg}" --keychain-profile "${profile}" --wait --output-format plist > "${result_plist}"
status="$(plutil -extract status raw "${result_plist}")"
submission_id="$(plutil -extract id raw "${result_plist}")"
if [[ "${status}" != "Accepted" ]]; then
  echo >&2 "Notarization of submission ${submission_id} finished with status '${status}'; the notary log follows"
  xcrun notarytool log "${submission_id}" --keychain-profile "${profile}" >&2 || true
  exit 1
fi
echo "Notarized (submission ${submission_id}), stapling..."

xcrun stapler staple "${dmg}"
spctl --assess --type open --context context:primary-signature --verbose "${dmg}"
echo "Done: ${dmg}"
