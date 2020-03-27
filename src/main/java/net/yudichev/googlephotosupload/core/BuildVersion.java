package net.yudichev.googlephotosupload.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class BuildVersion {
    private static final Logger logger = LoggerFactory.getLogger(BuildVersion.class);

    private static final String IMPLEMENTATION_VERSION = Optional
            .ofNullable(BuildVersion.class.getPackage().getImplementationVersion())
            .orElse("DEVELOPMENT");

    static {
        logger.info("Version {}", IMPLEMENTATION_VERSION);
    }

    BuildVersion() {
    }

    public static String buildVersion() {
        return IMPLEMENTATION_VERSION;
    }
}