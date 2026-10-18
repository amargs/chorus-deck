package org.amargs.chorusdeck.web;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void theShellWaitsForThePageTitle() {
        assertTrue(AppWindow.pageReady("\"eCoral\""));
        assertFalse(AppWindow.pageReady(""));
        assertFalse(AppWindow.pageReady("\"\""));
        assertFalse(AppWindow.pageReady("eCoral"));
    }

    @Test
    void theLockSitsBesideTheBrowserProfile() {
        Path profile = Path.of("C:/Users/me/AppData/Local/eCoral/browser-profile");
        assertEquals(profile.getParent().resolve("window.lock"), AppWindow.lockFile(profile));
    }

    @Test
    void aSecondLaunchFindsTheWindowAlreadyOpen() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-window");
        Path lock = dir.resolve("window.lock");
        AppWindow.Hold first = AppWindow.Hold.tryAcquire(lock);
        AppWindow.Hold second = AppWindow.Hold.tryAcquire(lock);
        assertTrue(first != null);
        assertNull(second);
        first.close();
        AppWindow.Hold again = AppWindow.Hold.tryAcquire(lock);
        assertTrue(again != null);
        again.close();
    }
}
