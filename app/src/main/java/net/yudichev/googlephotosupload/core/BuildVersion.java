package net.yudichev.googlephotosupload.core;

import static java.util.Objects.requireNonNullElse;

public final class BuildVersion {
    private static final String IMPLEMENTATION_VERSION = requireNonNullElse(BuildVersion.class.getPackage().getImplementationVersion(), "0.DEV");

    BuildVersion() {
    }

    public static String buildVersion() {
        return IMPLEMENTATION_VERSION;
    }
}