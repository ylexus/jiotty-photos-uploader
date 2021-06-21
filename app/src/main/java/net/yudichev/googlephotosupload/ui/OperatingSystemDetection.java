package net.yudichev.googlephotosupload.ui;


import java.util.Locale;

/**
 * helper class to check the operating system this Java VM runs in
 * <p>
 * please keep the notes below as a pseudo-license
 * <p>
 * http://stackoverflow.com/questions/228477/how-do-i-programmatically-determine-operating-system-in-java
 * compare to http://svn.terracotta.org/svn/tc/dso/tags/2.6.4/code/base/common/src/com/tc/util/runtime/Os.java
 * http://www.docjar.com/html/api/org/apache/commons/lang/SystemUtils.java.html
 */
public final class OperatingSystemDetection {
    // cached result of OS detection
    private static final OSType detectedOS;

    static {
        var os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        //noinspection IfStatementWithTooManyBranches
        if ((os.contains("mac")) || (os.contains("darwin"))) {
            detectedOS = OSType.MacOS;
        } else if (os.contains("win")) {
            detectedOS = OSType.Windows;
        } else if (os.contains("nux")) {
            detectedOS = OSType.Linux;
        } else {
            detectedOS = OSType.Other;
        }
    }

    /**
     * @return the operating system detected
     */
    public static OSType getOperatingSystemType() {
        return detectedOS;
    }

    /**
     * types of Operating Systems
     */
    public enum OSType {
        Windows, MacOS, Linux, Other
    }
}