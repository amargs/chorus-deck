package org.amargs.chorusdeck.liturgy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Gospel acclamation verses for Quaresma, read from data/louvor.json. The refrain stays on the layout. */
public final class LentenAcclamation {

    private static final Map<Path, Map<String, String>> CACHE = new HashMap<>();

    private LentenAcclamation() {
    }

    public static String verse(Path file, String sundayKey) {
        if (file == null || sundayKey == null || sundayKey.isBlank()) {
            return null;
        }
        String text = load(file).get(sundayKey);
        return text == null || text.isBlank() ? null : text;
    }

    private static Map<String, String> load(Path file) {
        Path key = file.toAbsolutePath().normalize();
        synchronized (CACHE) {
            Map<String, String> cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            JsonNode root = new ObjectMapper().readTree(file.toFile());
            if (!root.isObject()) {
                throw new IllegalArgumentException(file.toAbsolutePath() + " must be a JSON object.");
            }
            Map<String, String> verses = new HashMap<>();
            root.fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual()) {
                    String text = entry.getValue().asText().trim();
                    if (!text.isEmpty()) {
                        verses.put(entry.getKey(), text);
                    }
                }
            });
            Map<String, String> frozen = Map.copyOf(verses);
            synchronized (CACHE) {
                CACHE.put(key, frozen);
            }
            return frozen;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read " + file.toAbsolutePath() + ".", e);
        }
    }
}
