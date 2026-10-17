package org.amargs.chorusdeck.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppStateTest {

    @Test
    void stateLivesUnderLocalAppData() {
        Path local = Path.of("C:/Users/ada/AppData/Local");
        assertEquals(local.resolve("eCoral"), AppState.directory(local.toString(), "C:/Users/ada"));
    }

    @Test
    void aMissingVariableUsesTheSameFolderUnderTheUserHome() {
        assertEquals(
                Path.of("C:/Users/ada/AppData/Local/eCoral"),
                AppState.directory("  ", "C:/Users/ada"));
        assertEquals(
                Path.of("C:/Users/ada/AppData/Local/eCoral"),
                AppState.directory(null, "C:/Users/ada"));
    }
}
