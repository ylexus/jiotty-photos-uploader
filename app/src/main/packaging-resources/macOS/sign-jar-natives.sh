#!/usr/bin/env bash
# Signs the macOS native libraries inside the jars in a directory, in place.
#
# jpackage signs every Mach-O file in the app bundle, but not those packed inside jars; Apple's notary service looks inside jars too and rejects the
# submission if any of them is unsigned or lacks the hardened runtime and a secure timestamp.
#
# Usage: sign-jar-natives.sh <jar directory> <signing identity> <entitlements file>
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo >&2 "Usage: $0 <jar directory> <signing identity> <entitlements file>"
  exit 2
fi
jar_dir="$1"
identity="$2"
entitlements="$3"

# "-" signs ad hoc, for testing the packaging without a certificate; an ad-hoc signature cannot carry a timestamp
timestamp_option=(--timestamp)
[[ "${identity}" == "-" ]] && timestamp_option=()
# MAC_SIGNING_KEYCHAIN, if set, is the keychain holding the identity
keychain_option=()
[[ -n "${MAC_SIGNING_KEYCHAIN:-}" ]] && keychain_option=(--keychain "${MAC_SIGNING_KEYCHAIN}")

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

for jar in "${jar_dir}"/*.jar; do
  entries=()
  while IFS= read -r entry; do
    entries+=("${entry}")
  done < <(unzip -Z1 "${jar}" | grep -E '\.(dylib|jnilib)$' || true)
  [[ ${#entries[@]} -eq 0 ]] && continue

  extract_dir="${work_dir}/$(basename "${jar}")"
  mkdir -p "${extract_dir}"
  unzip -q -o "${jar}" "${entries[@]}" -d "${extract_dir}"

  signed=()
  for entry in "${entries[@]}"; do
    if file -b "${extract_dir}/${entry}" | grep -q 'Mach-O'; then
      codesign --force ${timestamp_option[@]+"${timestamp_option[@]}"} ${keychain_option[@]+"${keychain_option[@]}"} --options runtime --entitlements "${entitlements}" --sign "${identity}" "${extract_dir}/${entry}"
      signed+=("${entry}")
    fi
  done
  [[ ${#signed[@]} -eq 0 ]] && continue

  # replaces just these entries, keeping the rest of the jar as it is
  (cd "${extract_dir}" && zip -q "${jar}" -- "${signed[@]}")
  echo "Signed ${#signed[@]} native librar$([[ ${#signed[@]} -eq 1 ]] && echo y || echo ies) in $(basename "${jar}")"
done
