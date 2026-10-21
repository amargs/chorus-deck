package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32Util;
import org.amargs.chorusdeck.index.SongIndex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** The song index and export folder the page remembers, stored in the directory it is given. */
public final class LibraryStore {

    static final String FILE_NAME = "library.json";

    private final Path file;
    private final ObjectMapper json = new ObjectMapper();

    public LibraryStore(Path appDirectory) {
        this.file = appDirectory.toAbsolutePath().normalize().resolve(FILE_NAME);
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
            return new Status(null, false, null);
        }
        JsonNode node = json.readTree(file.toFile());
        Path index = savedIndex(node);
        boolean missing = index != null && !Files.isRegularFile(index);
        return new Status(missing ? null : index, missing, existingDirectory(node.get("outputDirectory")));
    }

    public Path save(Path index) throws IOException {
        Path path = index.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Index file not found: " + path);
        }
        try {
            SongIndex.read(path);
        } catch (IOException e) {
            throw new IllegalArgumentException("That file is not a song index.");
        }
        ObjectNode body = readBody();
        body.put("indexFile", path.toString());
        write(body);
        return path;
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
            throw new IllegalArgumentException("Choose the song index first.");
        }
        return status.index();
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
        JsonNode index = node.get("indexFile");
        if (index == null || !index.isTextual() || index.asText().isBlank()) {
            return null;
        }
        return Path.of(index.asText()).toAbsolutePath().normalize();
    }

    private static Path existingDirectory(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        Path path = Path.of(node.asText()).toAbsolutePath().normalize();
        return Files.isDirectory(path) ? path : null;
    }

    public record Status(Path index, boolean missing, Path outputDirectory) {
    }
}
