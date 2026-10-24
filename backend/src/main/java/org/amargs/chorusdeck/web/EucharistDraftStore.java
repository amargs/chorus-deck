package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The unfinished Eucaristia screen, kept on this PC beside {@code library.json}. */
public final class EucharistDraftStore {

    static final String FILE_NAME = "eucharist-draft.json";

    private final Path file;
    private final ObjectMapper json = new ObjectMapper();

    public EucharistDraftStore(Path appDirectory) {
        this.file = appDirectory.toAbsolutePath().normalize().resolve(FILE_NAME);
    }

    public JsonNode read() throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        JsonNode node = json.readTree(file.toFile());
        return node != null && node.isObject() ? node : null;
    }

    public void save(JsonNode body) throws IOException {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("The draft must be an object.");
        }
        JsonNode date = body.get("date");
        if (date == null || !date.isTextual() || !date.asText().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("The draft needs a date.");
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        json.writeValue(file.toFile(), body);
    }

    public void clear() throws IOException {
        Files.deleteIfExists(file);
    }
}
