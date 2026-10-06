package com.pmis.docket.desktop;

/**
 * Entry point used by the installer. It does not extend Application on purpose:
 * that lets JavaFX start from a normal class path (needed for jpackage without modules).
 */
public final class Launcher {
    private Launcher() { }

    public static void main(String[] args) {
        DocketApp.main(args);
    }
}
