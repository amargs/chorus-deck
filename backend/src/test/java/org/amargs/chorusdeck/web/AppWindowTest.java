package org.amargs.chorusdeck.web;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppWindowTest {

    @Test
    void chromeOpensWhenItIsInstalled() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-browser");
        Path chrome = dir.resolve("chrome.exe");
        Path edge = dir.resolve("msedge.exe");
        Files.writeString(chrome, "");
        Files.writeString(edge, "");
        Path profile = dir.resolve("browser-profile");

        Optional<AppWindow.Launch> launch = AppWindow.select(
                URI.create("http://127.0.0.1:7480/"), profile, List.of(chrome), List.of(edge));

        assertTrue(launch.isPresent());
        assertEquals(chrome, launch.get().executable());
        assertEquals(List.of(
                "--app=http://127.0.0.1:7480/",
                "--user-data-dir=" + profile.toAbsolutePath().normalize(),
                "--disable-background-mode",
                "--disable-component-extensions-with-background-pages",
                "--no-first-run",
                "--no-default-browser-check"), launch.get().arguments());
    }

    @Test
    void backgroundModeIsTurnedOffInTheBrowserProfile() throws Exception {
        Path profile = Files.createTempDirectory("chorus-deck-profile");
        Path prefs = profile.resolve("Default").resolve("Preferences");
        Files.createDirectories(prefs.getParent());
        Files.writeString(prefs, "{\"homepage\":\"http://example\"}");

        AppWindow.disableBackgroundMode(profile);

        String stored = Files.readString(prefs);
        assertTrue(stored.contains("\"enabled\" : false"));
        assertTrue(stored.contains("example"));
    }

    @Test
    void edgeOpensWhenChromeIsMissing() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-browser");
        Path chrome = dir.resolve("missing-chrome.exe");
        Path edge = dir.resolve("msedge.exe");
        Files.writeString(edge, "");

        Optional<AppWindow.Launch> launch = AppWindow.select(
                URI.create("http://127.0.0.1:9/"), dir.resolve("profile"), List.of(chrome), List.of(edge));

        assertTrue(launch.isPresent());
        assertEquals(edge, launch.get().executable());
    }

    @Test
    void neitherBrowserProducesACommand() {
        Path dir = Path.of("C:/missing");
        Optional<AppWindow.Launch> launch = AppWindow.select(
                URI.create("http://127.0.0.1:9/"),
                dir,
                List.of(dir.resolve("chrome.exe")),
                List.of(dir.resolve("msedge.exe")));
        assertTrue(launch.isEmpty());
    }
}
