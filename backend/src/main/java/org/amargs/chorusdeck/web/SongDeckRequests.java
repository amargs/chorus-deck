package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import org.amargs.chorusdeck.deck.Cover;
import org.amargs.chorusdeck.deck.Season;
import org.amargs.chorusdeck.index.SongCfg;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The song-deck page's view of the index, and the generate body it posts. */
public final class SongDeckRequests {

    private SongDeckRequests() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SongView(int id, String title, String bookId, boolean hasVerses) {
    }

    public record Ready(List<Integer> ids, Path output, Cover cover) {
    }

    public static List<SongView> songs(List<SongCfg> songs) {
        List<SongView> views = new ArrayList<>();
        for (SongCfg song : songs) {
            String bookId = song.bookId();
            if (bookId != null && bookId.isBlank()) {
                bookId = null;
            }
            boolean hasVerses = song.content() != null && !song.content().isEmpty();
            views.add(new SongView(song.id(), song.title(), bookId, hasVerses));
        }
        return views;
    }

    public static Ready parse(JsonNode body, List<SongCfg> songs, Path directory) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("The generate request must be an object.");
        }
        List<Integer> ids = ids(body.get("ids"), songs);
        Cover cover = cover(body.get("cover"));
        Path folder = LocalFiles.folder(text(body, "directory"), directory);
        return new Ready(ids, LocalFiles.deckFile(text(body, "output"), folder), cover);
    }

    private static List<Integer> ids(JsonNode node, List<SongCfg> songs) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException("A song deck needs at least one song.");
        }
        List<Integer> ids = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isIntegralNumber() || item.intValue() < 1) {
                throw new IllegalArgumentException("Song ids start at 1.");
            }
            int id = item.intValue();
            SongCfg song = find(songs, id);
            if (song.content() == null || song.content().isEmpty()) {
                throw new IllegalArgumentException("Song " + id + " has no verses.");
            }
            ids.add(id);
        }
        return ids;
    }

    private static SongCfg find(List<SongCfg> songs, int id) {
        for (SongCfg song : songs) {
            if (song.id() == id) {
                return song;
            }
        }
        throw new IllegalArgumentException("Unknown song id: " + id);
    }

    private static Cover cover(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("The cover must be an object.");
        }
        String kind = text(node, "kind");
        if ("outro".equals(kind)) {
            return other(node);
        }
        if (kind != null && !"celebracao".equals(kind)) {
            throw new IllegalArgumentException("Cover kind must be celebracao or outro.");
        }
        String season = required(node, "season");
        String ano = required(node, "ano").toUpperCase(Locale.ROOT);
        String domingo = required(node, "domingo");
        String frase = optional(node, "frase");
        String versiculo = optional(node, "versiculo");
        if (!ano.equals("A") && !ano.equals("B") && !ano.equals("C")) {
            throw new IllegalArgumentException("ano must be A, B, or C.");
        }
        String layout = text(node, "layout");
        Season.resolve(season, layout);
        return new Cover(season, layout, domingo, frase, versiculo, ano);
    }

    private static Cover other(JsonNode node) {
        String layout = required(node, "layout", "An other cover requires layout, domingo, and header.");
        if (!Season.coverLayouts().contains(layout)) {
            throw new IllegalArgumentException("Unknown cover: " + layout);
        }
        String domingo = required(node, "domingo", "An other cover requires layout, domingo, and header.");
        String header = required(node, "header", "An other cover requires layout, domingo, and header.");
        String frase = optional(node, "frase");
        String versiculo = optional(node, "versiculo");
        Season.resolve("comum", layout);
        return new Cover("comum", layout, domingo, frase, versiculo, null, header);
    }

    private static String required(JsonNode node, String field) {
        return required(node, field, "A song deck cover requires season, ano, and domingo.");
    }

    private static String required(JsonNode node, String field, String message) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String optional(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? "" : value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
