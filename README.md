Jiotty Photos Uploader is a simple desktop Google Photos media uploader that creates albums according to your directory structure and can resume failed uploads.
See [Wiki](https://github.com/ylexus/jiotty-photos-uploader/wiki) for more information, including how to download and install.

For enthusiasts, to build or run from sources:

1. Install JDK
    1. for a desktop, use any JDK version 14 or higher.
    2. for a Raspberry Pi, use Liberica OpenJDK (because it contains RPi-compatible javafx). Go to https://bell-sw.com/pages/downloads/, select latest version,
       32-bit, Linux, package: Full JDK, and install via `.deb`.
2. Clone this repository.
3. `cd jiotty-photos-uploader`

Then, to compile and run locally:

1. on a desktop: `./gradlew --no-daemon -DCLIENT_SECRET_PATH=/path/to/google-API-client-secret.json run`
2. on Raspberry Pi: `./gradlew --no-daemon -DCLIENT_SECRET_PATH=/path/to/google-API-client-secret.json -PraspberryPi run`

Or to build the self-contained native binary and installer for the current platform:

1. Run the following, replacing `0.0.0` with the version you want:
    1. on a desktop: `./gradlew --no-daemon -DCLIENT_SECRET_PATH=/path/to/google-API-client-secret.json fullPackage -DVERSION=0.0.0`
    2. on Raspberry Pi: `./gradlew --no-daemon -PraspberryPi -DCLIENT_SECRET_PATH=/path/to/google-API-client-secret.json fullPackage -DVERSION=0.0.0`
3. look for the executable bundle in `app/build/jpackage` and for the installer in `app/build/fullpackage`

Note: if doing repeated builds, to improve build speed, replace `--no-daemon` with `--build-cache`, but only if you understand what you're doing and are
prepared to maintain gradle cache and daemons.