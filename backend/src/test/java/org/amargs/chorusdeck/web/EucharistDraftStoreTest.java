package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EucharistDraftStoreTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void aDraftRoundTripsUntilItIsCleared() throws Exception {
        Path dir = Files.createTempDirectory("eucharist-draft");
        EucharistDraftStore store = new EucharistDraftStore(dir);
        assertNull(store.read());

        ObjectNode body = JSON.createObjectNode();
        body.put("date", "2026-11-01");
        body.putObject("form").put("frase", "A festa");
        store.save(body);

        assertEquals("A festa", store.read().path("form").path("frase").asText());
        assertTrue(Files.isRegularFile(dir.resolve("eucharist-draft.json")));

        store.clear();
        assertNull(store.read());
        assertFalse(Files.exists(dir.resolve("eucharist-draft.json")));
        store.clear();
    }

    @Test
    void aDraftNeedsADate() throws Exception {
        EucharistDraftStore store = new EucharistDraftStore(Files.createTempDirectory("eucharist-draft"));
        ObjectNode body = JSON.createObjectNode();
        body.put("date", "domingo");
        assertThrows(IllegalArgumentException.class, () -> store.save(body));
        assertNull(store.read());
    }
}
