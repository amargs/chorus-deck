package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.amargs.chorusdeck.index.SongIndex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The song index the page remembers, stored in the directory it is given. */
public final class LibraryStore {

    static final String FILE_NAME = "library.json";

    private final Path file;
    private final ObjectMapper json = new ObjectMapper();

    public LibraryStore(Path appDirectory) {
        this.file = appDirectory.toAbsolutePath().normalize().resolve(FILE_NAME);
    }

    public Status status() throws IOException {
        if (!Files.isRegularFile(file)) {
            return new Status(null, false);
        }
        JsonNode node = json.readTree(file.toFile());
        JsonNode index = node.get("indexFile");
        if (index == null || !index.isTextual() || index.asText().isBlank()) {
            return new Status(null, false);
        }
        Path path = Path.of(index.asText()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            return new Status(null, true);
        }
        return new Status(path, false);
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
        ObjectNode body = json.createObjectNode();
        body.put("indexFile", path.toString());
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        json.writeValue(file.toFile(), body);
        return path;
    }

    public Path requireIndex() throws IOException {
        Status status = status();
        if (status.index() == null) {
            throw new IllegalArgumentException("Choose the song index first.");
        }
        return status.index();
    }

    public record Status(Path index, boolean missing) {
    }
}
