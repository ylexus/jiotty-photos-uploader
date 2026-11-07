# Releasing

A release consists of three macOS/Windows packages built in two places, attached to a GitHub release, plus a Microsoft Store submission:

| Package                                        | Built on                                     | Signed                                           |
|------------------------------------------------|----------------------------------------------|--------------------------------------------------|
| `macOS-arm64-jiotty-photos-uploader-X.Y.Z.dmg` | a Mac, natively                              | Developer ID, notarized                          |
| `macOS-x64-jiotty-photos-uploader-X.Y.Z.dmg`   | the same Mac, under Rosetta, with an x64 JDK | Developer ID, notarized                          |
| `Windows-jiotty-photos-uploader-X.Y.Z.msi`     | GitHub Actions, `Windows package` workflow   | no (SmartScreen warns; Smart App Control blocks) |
| `Windows-jiotty-photos-uploader-X.Y.Z.msix`    | the same workflow                            | by Microsoft, on Store submission                |

The Google OAuth client secret is baked into every package: locally it comes from `CLIENT_SECRET_PATH`, on GitHub Actions from the
`GOOGLE_CLIENT_SECRET_JSON` repository secret.

## One-time setup

### macOS signing and notarization

1. A **Developer ID Application** certificate in the login keychain. Only the developer team's Account Holder can create it: Xcode > Settings > Apple
   Accounts > (team) > Manage Certificates > + > Developer ID Application. Check with `security find-identity -v -p codesigning`.
2. A notarytool keychain profile. Create an app-specific password at https://account.apple.com (Sign-In and Security), then:

       xcrun notarytool store-credentials jiotty-notary --apple-id <Apple ID> --team-id <team ID>

3. For the Intel package, an **x64** Liberica JDK 25 *Full* (`bellsoft-jdk25...-macos-amd64-full.tar.gz` from https://bell-sw.com/pages/downloads/),
   unpacked somewhere outside `/Library/Java/JavaVirtualMachines` so that Gradle doesn't pick it for native builds, e.g. `/path/to/jdk-25-x64`.
   Rosetta must be installed (`softwareupdate --install-rosetta`).

### Windows / Microsoft Store

1. Repository secret `GOOGLE_CLIENT_SECRET_JSON`: the client secret JSON file's contents.
2. Repository variables `MSIX_IDENTITY_NAME`, `MSIX_PUBLISHER`, `MSIX_PUBLISHER_DISPLAY_NAME`: the values on Partner Center's *Product management >
   Product identity* page (`Package/Identity/Name`, `Package/Identity/Publisher`, `Package/Properties/PublisherDisplayName`). Without them the MSIX
   gets placeholder values that the Store rejects.

## Each release

Use the version `X.Y.Z` throughout; the build rejects anything else.

1. **macOS, Apple Silicon:**

       export CLIENT_SECRET_PATH=/path/to/client_secret.json
       export MAC_SIGNING_IDENTITY="Developer ID Application: <name> (<team ID>)"
       export MAC_NOTARY_PROFILE=jiotty-notary
       ./gradlew clean fullpackage -DVERSION=X.Y.Z

   `fullpackage` runs the tests, signs the native libraries inside jars (which notarization inspects), lets jpackage sign the app with
   `app/src/main/packaging-resources/macOS/entitlements.plist`, builds the DMG, signs and notarizes it, staples the ticket and checks it with Gatekeeper.
   Copy `app/build/fullpackage/macOS-arm64-*.dmg` somewhere safe before the next step, which starts with `clean`.

2. **macOS, Intel** (same environment variables):

       X64_JDK=/path/to/jdk-25-x64/<jdk directory>.jdk
       arch -x86_64 env JAVA_HOME=$X64_JDK ./gradlew clean fullpackage -DVERSION=X.Y.Z \
           -Porg.gradle.java.installations.auto-detect=false -Porg.gradle.java.installations.paths=$X64_JDK

   Gradle itself must run as x64: the JavaFX artifacts and the runtime image follow the architecture of the JVM running the build. The result is
   `app/build/fullpackage/macOS-x64-*.dmg`; it runs on Apple Silicon too, under Rosetta, which is the only testing it gets without an Intel Mac.

3. **Windows:** GitHub > Actions > *Windows package* > Run workflow, with version `X.Y.Z`. It runs the tests, builds the MSI and the MSIX, and attaches
   both to the run as the `windows-packages-X.Y.Z` artifact.

4. **Test** the DMGs on a Mac and the MSI on Windows. To try the MSIX before submitting it, sign it with a self-signed
   certificate whose subject equals `MSIX_PUBLISHER` and trust that certificate on the test machine; the Store build itself is submitted unsigned.

5. **GitHub release:** tag `X.Y.Z`, attach the two DMGs and the MSI, publish. The app's update check offers any newer `X.Y.Z` release to existing users.

6. **Microsoft Store:** Partner Center > the app > new submission, upload the MSIX. Certification usually takes a few days.
