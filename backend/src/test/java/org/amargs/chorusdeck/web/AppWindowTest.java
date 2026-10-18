package org.amargs.chorusdeck.web;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppWindowTest {

    private static final URI APP = URI.create("http://127.0.0.1:7480/");

    @Test
    void thePageStaysOnThisServer() {
        assertTrue(AppWindow.staysInApp("http://127.0.0.1:7480/", APP));
        assertTrue(AppWindow.staysInApp("http://127.0.0.1:7480/assets/index.js", APP));
        assertTrue(AppWindow.staysInApp("about:blank", APP));
        assertFalse(AppWindow.staysInApp("https://fonts.google.com/specimen/Livvic", APP));
        assertFalse(AppWindow.staysInApp("http://127.0.0.1:9/", APP));
    }

    @Test
    void aPopupOnlyStaysWhenItLoadsThisServer() {
        assertTrue(AppWindow.popupStays("http://127.0.0.1:7480/", APP));
        assertFalse(AppWindow.popupStays("", APP));
        assertFalse(AppWindow.popupStays("about:blank", APP));
        assertFalse(AppWindow.popupStays("https://fonts.google.com/specimen/Livvic", APP));
    }

    @Test
    void theRuntimeCountsWhenItsExecutableOrRegistryClientIsPresent() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-webview");
        Path executable = dir.resolve("msedgewebview2.exe");
        assertFalse(AppWindow.runtimeInstalled(List.of(dir.resolve("missing.exe")), false));
        assertTrue(AppWindow.runtimeInstalled(List.of(dir.resolve("missing.exe")), true));
        Files.writeString(executable, "");
        assertTrue(AppWindow.runtimeInstalled(List.of(executable), false));
    }
}
