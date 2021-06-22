Jiotty Photos Uploader is a simple desktop Google Photos media uploader that creates albums according to your directory structure and can resume failed uploads.
See [Wiki](https://github.com/ylexus/jiotty-photos-uploader/wiki) for more information.

To build from sources and run locally:

1. Install JDK 14
2. run `./gradlew -DCLIENT_SECRET_PATH=/path/to/google-API-client-secret.json run`

If build the native binary for the current platform from sources:

1. Install JDK 14
2. Run the following, replacing `0.0.0` with the version you want:
    1. on a desktop: `./gradlew --no-daemon -DCLIENT_SECRET_PATH=/path/to/google-API-client-secret.json fullPackage -DVERSION=0.0.0`
    2. on Raspberry Pi:
       `./gradlew --no-daemon -PjpackageInstallerType=deb -DCLIENT_SECRET_PATH=/path/to/google-API-client-secret.json fullPackage -DVERSION=0.0.0`
3. look for the executable bundle in `app/build/jpackage` and for the installer in `app/build/fullpackage`
