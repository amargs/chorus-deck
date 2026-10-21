package org.amargs.chorusdeck.web;

import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.WinUser;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Output path checks for the local page. Launching Explorer is separate so tests can skip it. */
public final class LocalFiles {

    public enum OpenKind {
        FILE,
        SELECT,
        FOLDER
    }

    public record OpenPlan(OpenKind kind, Path path) {
    }

    private LocalFiles() {
    }

    public static Path folder(String chosen, Path fallback) {
        if (chosen == null || chosen.isBlank()) {
            return fallback.toAbsolutePath().normalize();
        }
        return requireDirectory(Path.of(chosen));
    }

    public static Path deckFile(String name, Path directory) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("The deck needs a file name.");
        }
        String file = name.trim();
        if (!file.toLowerCase(Locale.ROOT).endsWith(".pptx")) {
            file = file + ".pptx";
        }
        if (file.equals(".pptx") || file.indexOf('/') >= 0 || file.indexOf('\\') >= 0 || file.contains("..")) {
            throw new IllegalArgumentException("The file name must be a single .pptx name.");
        }
        Path folder = directory.toAbsolutePath().normalize();
        Path path = folder.resolve(file).normalize();
        if (path.getParent() == null || !path.getParent().equals(folder)) {
            throw new IllegalArgumentException("The file name must be a single .pptx name.");
        }
        return path;
    }

    /** Allows the last generated file, or its folder. Does not start a process. */
    public static OpenPlan plan(Path requested, String what, Path lastFile) {
        if (lastFile == null) {
            throw new IllegalArgumentException("There is no generated file to open.");
        }
        if (!"file".equals(what) && !"folder".equals(what)) {
            throw new IllegalArgumentException("Open what must be file or folder.");
        }
        Path last = lastFile.toAbsolutePath().normalize();
        Path path = requested.toAbsolutePath().normalize();
        boolean isFile = path.equals(last);
        boolean isParent = last.getParent() != null && path.equals(last.getParent());
        if (!isFile && !isParent) {
            throw new IllegalArgumentException("That path cannot be opened.");
        }
        if ("file".equals(what)) {
            if (!isFile || !Files.isRegularFile(path)) {
                throw new IllegalArgumentException("The generated file is missing.");
            }
            return new OpenPlan(OpenKind.FILE, path);
        }
        if (isFile && Files.isRegularFile(path)) {
            return new OpenPlan(OpenKind.SELECT, path);
        }
        Path folder = isParent ? path : path.getParent();
        if (folder == null || !Files.isDirectory(folder)) {
            throw new IllegalArgumentException("The output folder is missing.");
        }
        return new OpenPlan(OpenKind.FOLDER, folder);
    }

    public static void launch(OpenPlan plan) throws IOException {
        try {
            switch (plan.kind()) {
                case FILE -> Desktop.getDesktop().open(plan.path().toFile());
                case SELECT -> reveal(plan.path());
                case FOLDER -> new ProcessBuilder("explorer.exe", plan.path().toString()).start();
            }
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            String message = e.getMessage();
            throw new IOException(message == null || message.isBlank() ? "Could not open the file." : message, e);
        }
    }

    /** Explorer only treats this as “select the file” when the path is quoted and the switch is not. */
    static String selectParameters(Path file) {
        return "/select,\"" + file.toString() + "\"";
    }

    private static void reveal(Path file) throws IOException {
        long opened = Shell32.INSTANCE.ShellExecute(
                null, "open", "explorer.exe", selectParameters(file), null, WinUser.SW_SHOWNORMAL).longValue();
        if (opened <= 32) {
            throw new IOException("Could not open the folder.");
        }
    }

    private static Path requireDirectory(Path path) {
        Path folder = path.toAbsolutePath().normalize();
        if (!Files.exists(folder)) {
            throw new IllegalArgumentException("The output folder does not exist.");
        }
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("The output folder must be a directory.");
        }
        return folder;
    }
}
