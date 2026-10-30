package org.amargs.chorusdeck.deck;

import com.fasterxml.jackson.databind.JsonNode;
import org.amargs.chorusdeck.index.SongCfg;

import java.util.List;

/** Which language one deck shows for a song. The slide title is always the main title. */
public record SlideChoice(String title, String lyrics) {

    public SlideChoice {
        if (title != null && !title.equals("title") && !title.equals("titleAlt")) {
            throw new IllegalArgumentException("title must be title or titleAlt.");
        }
        title = null;
        if (lyrics != null && !lyrics.equals("content") && !lyrics.equals("contentAlt")) {
            throw new IllegalArgumentException("lyrics must be content or contentAlt.");
        }
    }

    public boolean isDefault() {
        return lyrics == null;
    }

    public static SlideChoice from(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("A song choice must be an object.");
        }
        SlideChoice choice = new SlideChoice(text(node, "title"), text(node, "lyrics"));
        return choice.isDefault() ? null : choice;
    }

    /** The verses the slides use, under the main title. The library song is left as it was. */
    public SongCfg show(SongCfg song) {
        if (isDefault()) {
            return song.prepared();
        }
        boolean second = "contentAlt".equals(lyrics)
                || (lyrics == null && "contentAlt".equals(song.defaultLyrics()));
        List<String> verses;
        if (second) {
            if (song.contentAlt() == null || song.contentAlt().isEmpty()) {
                throw new IllegalArgumentException("Song " + song.id() + " has no second language.");
            }
            verses = song.contentAlt();
        } else {
            verses = song.content() == null ? List.of() : song.content();
        }
        verses = List.copyOf(verses);
        if (verses.isEmpty()) {
            throw new IllegalArgumentException("Song " + song.id() + " has no verses.");
        }
        return new SongCfg(song.id(), song.title(), verses, song.bookId(), song.titleAlt(), null);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " must be text.");
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
