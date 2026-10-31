package org.amargs.chorusdeck.deck;

import com.fasterxml.jackson.databind.JsonNode;
import org.amargs.chorusdeck.index.SongCfg;

import java.util.ArrayList;
import java.util.List;

/** Which language, and which text boxes, one deck shows for a song. The slide title is always the main title. */
public record SlideChoice(String title, String lyrics, List<Integer> slides) {

    public SlideChoice(String title, String lyrics) {
        this(title, lyrics, null);
    }

    public SlideChoice {
        if (title != null && !title.equals("title") && !title.equals("titleAlt")) {
            throw new IllegalArgumentException("title must be title or titleAlt.");
        }
        title = null;
        if (lyrics != null && !lyrics.equals("content") && !lyrics.equals("contentAlt")) {
            throw new IllegalArgumentException("lyrics must be content or contentAlt.");
        }
        if (slides != null) {
            if (slides.isEmpty()) {
                throw new IllegalArgumentException("slides must list the text boxes to keep.");
            }
            int previous = 0;
            for (Integer slide : slides) {
                if (slide == null || slide <= previous) {
                    throw new IllegalArgumentException("slides must be increasing positions starting at 1.");
                }
                previous = slide;
            }
            slides = List.copyOf(slides);
        }
    }

    public boolean isDefault() {
        return lyrics == null && slides == null;
    }

    public static SlideChoice from(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("A song choice must be an object.");
        }
        SlideChoice choice = new SlideChoice(text(node, "title"), text(node, "lyrics"), slides(node));
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
        verses = keep(song.id(), verses, slides);
        if (verses.isEmpty()) {
            throw new IllegalArgumentException("Song " + song.id() + " has no verses.");
        }
        return new SongCfg(song.id(), song.title(), verses, song.bookId(), song.titleAlt(), null);
    }

    private static List<String> keep(int id, List<String> verses, List<Integer> slides) {
        if (slides == null) {
            return List.copyOf(verses);
        }
        List<String> kept = new ArrayList<>();
        for (int slide : slides) {
            if (slide > verses.size()) {
                throw new IllegalArgumentException("Song " + id + " has no verse " + slide + ".");
            }
            kept.add(verses.get(slide - 1));
        }
        return List.copyOf(kept);
    }

    private static List<Integer> slides(JsonNode node) {
        JsonNode value = node.get("slides");
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray() || value.isEmpty()) {
            throw new IllegalArgumentException("slides must list the text boxes to keep.");
        }
        List<Integer> slides = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isIntegralNumber()) {
                throw new IllegalArgumentException("slides must be increasing positions starting at 1.");
            }
            slides.add(item.intValue());
        }
        return slides;
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
