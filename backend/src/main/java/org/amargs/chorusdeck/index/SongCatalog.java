package org.amargs.chorusdeck.index;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Creates or replaces one song in the index the app is using. */
public final class SongCatalog {

    private SongCatalog() {
    }

    public record Saved(List<SongCfg> songs, SongCfg song) {
    }

    public static SongCfg find(List<SongCfg> songs, int id) {
        for (SongCfg song : songs) {
            if (song.id() == id) {
                return song;
            }
        }
        return null;
    }

    public static Saved save(List<SongCfg> songs, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("O cântico tem de ser um objeto.");
        }
        String title = text(body.get("title"));
        if (title == null) {
            throw new IllegalArgumentException("O cântico precisa de um título.");
        }
        List<String> content = verses(body.get("content"), true);
        String bookId = text(body.get("bookId"));
        String titleAlt = text(body.get("titleAlt"));
        List<String> contentAlt = verses(body.get("contentAlt"), false);
        String lang = language(body.get("lang"));
        String langAlt = language(body.get("langAlt"));
        String defaultLyrics = defaultLyrics(body.get("defaultLyrics"));
        if (contentAlt != null && lang != null && lang.equals(langAlt)) {
            throw new IllegalArgumentException("As duas línguas têm de ser diferentes.");
        }
        List<SongCfg> next = new ArrayList<>(songs == null ? List.of() : songs);
        JsonNode idNode = body.get("id");
        if (idNode == null || idNode.isNull()) {
            int id = nextId(next);
            SongCfg song = new SongCfg(id, title, content, bookId, titleAlt, contentAlt, lang, langAlt, defaultLyrics);
            next.add(song);
            return new Saved(next, song);
        }
        if (!idNode.isIntegralNumber() || idNode.intValue() < 1) {
            throw new IllegalArgumentException("O número do cântico começa em 1.");
        }
        int id = idNode.intValue();
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id() == id) {
                SongCfg song = new SongCfg(id, title, content, bookId, titleAlt, contentAlt, lang, langAlt, defaultLyrics);
                next.set(i, song);
                return new Saved(next, song);
            }
        }
        throw new IllegalArgumentException("Cântico desconhecido.");
    }

    private static List<String> verses(JsonNode node, boolean required) {
        if (node == null || node.isNull()) {
            if (required) {
                throw new IllegalArgumentException("O cântico precisa das estrofes.");
            }
            return null;
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("O cântico precisa das estrofes.");
        }
        List<String> content = new ArrayList<>();
        for (JsonNode verse : node) {
            if (!verse.isTextual()) {
                throw new IllegalArgumentException("Cada estrofe é texto.");
            }
            String text = verse.asText().strip();
            if (!text.isEmpty()) {
                content.add(text);
            }
        }
        if (content.isEmpty() && !required) {
            return null;
        }
        return content;
    }

    private static int nextId(List<SongCfg> songs) {
        int next = 1;
        for (SongCfg song : songs) {
            if (song.id() >= next) {
                next = song.id() + 1;
            }
        }
        return next;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("O título, o título alternativo e o número são texto.");
        }
        String text = node.asText().strip();
        return text.isEmpty() ? null : text;
    }

    private static String language(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException(
                    "A língua tem de ser português, inglês, espanhol, francês ou latim.");
        }
        String code = node.asText().strip();
        if (code.isEmpty()) {
            return null;
        }
        if (!SongCfg.LANGUAGES.contains(code)) {
            throw new IllegalArgumentException(
                    "A língua tem de ser português, inglês, espanhol, francês ou latim.");
        }
        return code;
    }

    private static String defaultLyrics(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("A língua por omissão tem de ser uma das línguas do cântico.");
        }
        String value = node.asText().strip();
        if (value.isEmpty() || value.equals("content")) {
            return null;
        }
        if (value.equals("contentAlt")) {
            return value;
        }
        throw new IllegalArgumentException("A língua por omissão tem de ser uma das línguas do cântico.");
    }
}
