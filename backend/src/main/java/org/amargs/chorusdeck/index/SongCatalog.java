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
        JsonNode verses = body.get("content");
        if (verses == null || !verses.isArray()) {
            throw new IllegalArgumentException("O cântico precisa das estrofes.");
        }
        List<String> content = new ArrayList<>();
        for (JsonNode verse : verses) {
            if (!verse.isTextual()) {
                throw new IllegalArgumentException("Cada estrofe é texto.");
            }
            String text = verse.asText().strip();
            if (!text.isEmpty()) {
                content.add(text);
            }
        }
        String bookId = text(body.get("bookId"));
        List<SongCfg> next = new ArrayList<>(songs == null ? List.of() : songs);
        JsonNode idNode = body.get("id");
        if (idNode == null || idNode.isNull()) {
            int id = nextId(next);
            SongCfg song = new SongCfg(id, title, content, bookId);
            next.add(song);
            return new Saved(next, song);
        }
        if (!idNode.isIntegralNumber() || idNode.intValue() < 1) {
            throw new IllegalArgumentException("O número do cântico começa em 1.");
        }
        int id = idNode.intValue();
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id() == id) {
                SongCfg song = new SongCfg(id, title, content, bookId);
                next.set(i, song);
                return new Saved(next, song);
            }
        }
        throw new IllegalArgumentException("Cântico desconhecido.");
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
            throw new IllegalArgumentException("O título e o número são texto.");
        }
        String text = node.asText().strip();
        return text.isEmpty() ? null : text;
    }
}
