Jiotty Photos Uploader is a simple desktop Google Photos media uploader that creates albums according to your directory structure and can resume failed uploads.
See [Wiki](https://github.com/ylexus/jiotty-photos-uploader/wiki) for more information, including how to download and install.

Released for Windows (x64) and macOS (Apple Silicon and Intel). It may build and run elsewhere, but nothing else is tested.

## Building from source

1. Follow [these steps](https://github.com/ylexus/jiotty-photos-uploader/wiki#using-your-own-google-api-client-secret) to generate your own Google API client
   secret file.
2. Install **BellSoft Liberica JDK 25, "Full" package** (it bundles JavaFX): https://bell-sw.com/pages/downloads/. The build asks Gradle for a BellSoft
   Java 25 toolchain, and packaging fails early if the JDK has no JavaFX modules.
3. Clone this repository and `cd jiotty-photos-uploader`.

To run from sources:

    ./gradlew -DCLIENT_SECRET_PATH=/path/to/google-API-client-secret.json run

To build the self-contained application and installer for the current platform (unsigned):

    ./gradlew -DCLIENT_SECRET_PATH=/path/to/google-API-client-secret.json -DVERSION=0.0.0 jpackage

The application image and installer appear in `app/build/jpackage`. `fullpackage` builds release packages; see [RELEASING.md](RELEASING.md).
