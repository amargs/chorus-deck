package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Chrome or Edge in app mode. The server stays up until that window closes. */
public final class AppWindow {

    private static final Logger LOG = LogManager.getLogger(AppWindow.class);

    private AppWindow() {
    }

    public record Launch(Path executable, List<String> arguments) {
    }

    public static void open(URI uri, Path profile) throws Exception {
        Optional<Launch> launch = select(uri, profile, chromeCandidates(), edgeCandidates());
        if (launch.isEmpty()) {
            AppDialog.error("Instale o Chrome ou o Edge para abrir o eCoral.");
            return;
        }
        Launch chosen = launch.get();
        Path folder = profile.toAbsolutePath().normalize();
        Files.createDirectories(folder);
        disableBackgroundMode(folder);
        List<String> command = new ArrayList<>();
        command.add(chosen.executable().toString());
        command.addAll(chosen.arguments());
        LOG.info("Opening " + uri);
        Process process = new ProcessBuilder(command).start();
        try {
            WindowClose.await(process);
        } finally {
            endTree(process);
            LOG.info("Browser closed");
        }
    }

    /** Chrome stays running after the last window when background mode is on. Turn that off in this profile. */
    static void disableBackgroundMode(Path profile) throws IOException {
        Path prefs = profile.resolve("Default").resolve("Preferences");
        Files.createDirectories(prefs.getParent());
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        if (Files.isRegularFile(prefs) && Files.size(prefs) > 0) {
            try {
                JsonNode stored = mapper.readTree(prefs.toFile());
                if (stored instanceof ObjectNode object) {
                    root = object;
                }
            } catch (IOException e) {
                LOG.warn("Replacing unreadable browser preferences: " + e.getMessage());
            }
        }
        JsonNode mode = root.get("background_mode");
        ObjectNode background = mode instanceof ObjectNode object ? object : root.putObject("background_mode");
        background.put("enabled", false);
        mapper.writerWithDefaultPrettyPrinter().writeValue(prefs.toFile(), root);
    }

    /** Chrome when its executable exists, otherwise Edge. Empty when neither file is there. */
    static Optional<Launch> select(URI uri, Path profile, List<Path> chrome, List<Path> edge) {
        Path executable = firstExisting(chrome);
        if (executable == null) {
            executable = firstExisting(edge);
        }
        if (executable == null) {
            return Optional.empty();
        }
        Path folder = profile.toAbsolutePath().normalize();
        return Optional.of(new Launch(executable, List.of(
                "--app=" + uri,
                "--user-data-dir=" + folder,
                "--disable-background-mode",
                "--disable-component-extensions-with-background-pages",
                "--no-first-run",
                "--no-default-browser-check")));
    }

    /** Stops this launch if anything from it is still running. Another Chrome profile is left alone. */
    private static void endTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    static List<Path> chromeCandidates() {
        return installed("chrome.exe", "Google", "Chrome", "Application", "chrome.exe");
    }

    static List<Path> edgeCandidates() {
        return installed("msedge.exe", "Microsoft", "Edge", "Application", "msedge.exe");
    }

    private static List<Path> installed(String exeName, String... relative) {
        List<Path> paths = new ArrayList<>();
        addInstall(paths, System.getenv("ProgramFiles"), relative);
        addInstall(paths, System.getenv("ProgramFiles(x86)"), relative);
        addInstall(paths, System.getenv("LOCALAPPDATA"), relative);
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (!dir.isBlank()) {
                    paths.add(Path.of(dir).resolve(exeName));
                }
            }
        }
        return paths;
    }

    private static void addInstall(List<Path> paths, String root, String... relative) {
        if (root == null || root.isBlank()) {
            return;
        }
        paths.add(Path.of(root, relative));
    }

    private static Path firstExisting(List<Path> candidates) {
        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
