package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32Util;
import org.amargs.chorusdeck.index.SongIndex;
import org.amargs.chorusdeck.liturgy.SundayLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** This PC's library choice, stored in {@code library.json}. The working pair lives elsewhere. */
public final class LibraryStore {

    static final String FILE_NAME = "library.json";
    static final String MODE_LOCAL = "local";
    static final String MODE_FOLDER = "folder";
    static final String INDEX_NAME = "index.json";

    private final Path file;
    private final LibrarySeed seed;
    private final ObjectMapper json = new ObjectMapper();

    public LibraryStore(Path appDirectory) {
        this(appDirectory, PackagedSeed.INSTANCE);
    }

    LibraryStore(Path appDirectory, LibrarySeed seed) {
        this.file = appDirectory.toAbsolutePath().normalize().resolve(FILE_NAME);
        this.seed = seed;
    }

    /** Working pair for "Só neste computador", outside the program files. */
    public Path localDirectory() {
        return file.getParent().resolve("data");
    }

    /** Windows Documents when that folder exists, otherwise Documents under the user home. */
    public static Path documents() {
        Path known = windowsDocuments();
        if (known != null) {
            return known;
        }
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return null;
        }
        Path documents = Path.of(home, "Documents");
        if (Files.isDirectory(documents)) {
            return documents.toAbsolutePath().normalize();
        }
        Path user = Path.of(home);
        return Files.isDirectory(user) ? user.toAbsolutePath().normalize() : null;
    }

    public Status status() throws IOException {
        if (!Files.isRegularFile(file)) {
            return new Status(null, false, null, null, null);
        }
        JsonNode node = json.readTree(file.toFile());
        Path output = existingDirectory(node.get("outputDirectory"));
        String mode = savedMode(node);
        if (mode == null) {
            return new Status(null, false, output, null, null);
        }
        Path index = savedIndex(node);
        boolean missing = index == null || !Files.isRegularFile(index);
        Path folder = savedPath(node.get("folder"));
        if (folder == null && MODE_FOLDER.equals(mode) && !missing && index != null) {
            folder = index.getParent();
        }
        return new Status(missing ? null : index, missing, output, mode, folder);
    }

    /**
     * Uses the app-local pair. A library already open is copied there, so this PC keeps what it was editing.
     * With nothing open yet, a missing file is filled from the packaged pair.
     */
    public Status useLocal() throws IOException {
        Path local = localDirectory().toAbsolutePath().normalize();
        Path keep = status().folder();
        Path source = currentDirectory();
        if (source != null && !source.equals(local)) {
            keep = source;
        }
        if (source != null && !source.equals(local)) {
            copyFile(source.resolve(INDEX_NAME), local.resolve(INDEX_NAME));
            Path liturgy = source.resolve(SundayLibrary.FILE_NAME);
            if (Files.isRegularFile(liturgy)) {
                copyFile(liturgy, local.resolve(SundayLibrary.FILE_NAME));
            } else if (!Files.isRegularFile(local.resolve(SundayLibrary.FILE_NAME))) {
                seed.copyLiturgy(local.resolve(SundayLibrary.FILE_NAME));
            }
            validateIndex(local.resolve(INDEX_NAME));
        } else {
            placeMissing(local, null);
        }
        remember(local.resolve(INDEX_NAME), MODE_LOCAL, keep);
        return status();
    }

    /**
     * Reads {@code index.json} and {@code liturgy.json} in {@code directory}.
     * A file already there is kept. A missing file is filled from the pair in use, or from the packaged pair.
     */
    public Status useFolder(Path directory) throws IOException {
        Path folder = requireFolder(directory);
        Path source = currentDirectory();
        if (source == null || !source.equals(folder)) {
            placeMissing(folder, source);
        }
        remember(folder.resolve(INDEX_NAME), MODE_FOLDER, folder);
        return status();
    }

    /**
     * Replaces the pair in use with the files in {@code directory}.
     * The mode and the sync folder stay as they are. A missing liturgy file leaves the current one.
     */
    public Status importFrom(Path directory) throws IOException {
        Path folder = requireFolder(directory);
        Path sourceIndex = folder.resolve(INDEX_NAME);
        if (!Files.isRegularFile(sourceIndex)) {
            throw new IllegalArgumentException("Essa pasta não tem um índice de cânticos.");
        }
        validateIndex(sourceIndex);
        Path destination = requireIndex();
        copyFile(sourceIndex, destination);
        Path sourceLiturgy = folder.resolve(SundayLibrary.FILE_NAME);
        if (Files.isRegularFile(sourceLiturgy)) {
            copyFile(sourceLiturgy, destination.resolveSibling(SundayLibrary.FILE_NAME));
        }
        return status();
    }

    /** Copies the current pair into {@code directory} and leaves the mode unchanged. */
    public Path exportTo(Path directory) throws IOException {
        Path folder = requireFolder(directory);
        Path index = requireIndex();
        copyFile(index, folder.resolve(INDEX_NAME));
        Path liturgy = index.resolveSibling(SundayLibrary.FILE_NAME);
        if (Files.isRegularFile(liturgy)) {
            copyFile(liturgy, folder.resolve(SundayLibrary.FILE_NAME));
        }
        return folder;
    }

    public Path saveOutput(Path directory) throws IOException {
        Path folder = LocalFiles.folder(directory.toString(), directory);
        ObjectNode body = readBody();
        body.put("outputDirectory", folder.toString());
        write(body);
        return folder;
    }

    public Path requireIndex() throws IOException {
        Status status = status();
        if (status.index() == null) {
            throw new IllegalArgumentException("Escolha onde guardar a biblioteca.");
        }
        return status.index();
    }

    private void placeMissing(Path directory, Path source) throws IOException {
        Path index = directory.resolve(INDEX_NAME);
        Path liturgy = directory.resolve(SundayLibrary.FILE_NAME);
        if (!Files.isRegularFile(index)) {
            Path from = source == null ? null : source.resolve(INDEX_NAME);
            if (from != null && Files.isRegularFile(from)) {
                copyFile(from, index);
            } else {
                seed.copyIndex(index);
            }
        }
        validateIndex(index);
        if (!Files.isRegularFile(liturgy)) {
            Path from = source == null ? null : source.resolve(SundayLibrary.FILE_NAME);
            if (from != null && Files.isRegularFile(from)) {
                copyFile(from, liturgy);
            } else {
                seed.copyLiturgy(liturgy);
            }
        }
    }

    private Path currentDirectory() throws IOException {
        Status status = status();
        if (status.index() == null) {
            return null;
        }
        Path parent = status.index().getParent();
        return parent == null ? null : parent.toAbsolutePath().normalize();
    }

    private void remember(Path index, String mode, Path folder) throws IOException {
        ObjectNode body = readBody();
        body.put("indexFile", index.toAbsolutePath().normalize().toString());
        body.put("mode", mode);
        if (folder == null) {
            body.remove("folder");
        } else {
            body.put("folder", folder.toAbsolutePath().normalize().toString());
        }
        write(body);
    }

    private ObjectNode readBody() throws IOException {
        if (!Files.isRegularFile(file)) {
            return json.createObjectNode();
        }
        JsonNode node = json.readTree(file.toFile());
        if (node instanceof ObjectNode object) {
            return object;
        }
        return json.createObjectNode();
    }

    private void write(ObjectNode body) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        json.writeValue(file.toFile(), body);
    }

    private static void validateIndex(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Index file not found: " + path);
        }
        try {
            SongIndex.read(path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Esse ficheiro não é um índice de cânticos.");
        }
    }

    private static void copyFile(Path from, Path to) throws IOException {
        Path source = from.toAbsolutePath().normalize();
        Path destination = to.toAbsolutePath().normalize();
        if (source.equals(destination)) {
            return;
        }
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path requireFolder(Path directory) {
        Path folder = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Essa pasta não existe.");
        }
        return folder;
    }

    private static Path windowsDocuments() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return null;
        }
        try {
            String path = Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Documents);
            if (path == null || path.isBlank()) {
                return null;
            }
            Path folder = Path.of(path);
            return Files.isDirectory(folder) ? folder.toAbsolutePath().normalize() : null;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return null;
        }
    }

    private static Path savedIndex(JsonNode node) {
        return savedPath(node.get("indexFile"));
    }

    private static String savedMode(JsonNode node) {
        JsonNode mode = node.get("mode");
        if (mode == null || !mode.isTextual()) {
            return null;
        }
        String text = mode.asText();
        if (MODE_LOCAL.equals(text) || MODE_FOLDER.equals(text)) {
            return text;
        }
        return null;
    }

    private static Path savedPath(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        return Path.of(node.asText()).toAbsolutePath().normalize();
    }

    private static Path existingDirectory(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        Path path = Path.of(node.asText()).toAbsolutePath().normalize();
        return Files.isDirectory(path) ? path : null;
    }

    public record Status(Path index, boolean missing, Path outputDirectory, String mode, Path folder) {
    }
}
