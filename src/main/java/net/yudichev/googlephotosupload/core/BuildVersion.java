package net.yudichev.googlephotosupload.core;

import java.util.Optional;

public final class BuildVersion {
    private static final String IMPLEMENTATION_VERSION = Optional
            .ofNullable(BuildVersion.class.getPackage().getImplementationVersion())
            .orElse("DEVELOPMENT");

    private BuildVersion() {
    }

    public static String buildVersion() {
        return IMPLEMENTATION_VERSION;
    }
}