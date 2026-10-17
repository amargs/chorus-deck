package org.amargs.chorusdeck.web;

import java.nio.file.Path;

/** Per-user state for the song deck. The shared index stays wherever the user chose it. */
public final class AppState {

    static final String FOLDER = "eCoral";

    private AppState() {
    }

    /** {@code %LOCALAPPDATA%\eCoral}, or the same folder under the user home when that variable is unset. */
    public static Path directory() {
        return directory(System.getenv("LOCALAPPDATA"), System.getProperty("user.home"));
    }

    static Path directory(String localAppData, String userHome) {
        Path base;
        if (localAppData != null && !localAppData.isBlank()) {
            base = Path.of(localAppData.trim());
        } else {
            base = Path.of(userHome, "AppData", "Local");
        }
        return base.resolve(FOLDER);
    }
}
