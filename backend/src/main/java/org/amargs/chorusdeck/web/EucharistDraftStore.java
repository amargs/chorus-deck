package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
        ObjectNode incoming = checked(body);
        incoming.remove("exported");
        JsonNode current = read();
        if (current != null && current.path("exported").asBoolean(false) && sameWork(current, incoming)) {
            incoming.put("exported", true);
        }
        write(incoming);
    }

    /** After a mass file is written, drop this draft on the next visit unless it changes first. */
    public void markExportedIfUnchanged(JsonNode snapshot) throws IOException {
        if (snapshot == null || !snapshot.isObject()) {
            return;
        }
        JsonNode current = read();
        if (current == null || current.path("exported").asBoolean(false) || !sameWork(snapshot, current)) {
            return;
        }
        ObjectNode copy = ((ObjectNode) current).deepCopy();
        copy.put("exported", true);
        write(copy);
    }

    /** A new visit. An exported draft that was left as-is is removed. */
    public JsonNode readNewSession() throws IOException {
        JsonNode draft = read();
        if (draft != null && draft.path("exported").asBoolean(false)) {
            clear();
            return null;
        }
        return draft;
    }

    public void clear() throws IOException {
        Files.deleteIfExists(file);
    }

    private ObjectNode checked(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("The draft must be an object.");
        }
        JsonNode date = body.get("date");
        if (date == null || !date.isTextual() || !date.asText().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("The draft needs a date.");
        }
        return ((ObjectNode) body).deepCopy();
    }

    private void write(JsonNode body) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        json.writeValue(file.toFile(), body);
    }

    private static boolean sameWork(JsonNode left, JsonNode right) {
        return withoutExported(left).equals(withoutExported(right));
    }

    private static JsonNode withoutExported(JsonNode node) {
        if (!(node instanceof ObjectNode object)) {
            return node;
        }
        ObjectNode copy = object.deepCopy();
        copy.remove("exported");
        return copy;
    }
}
