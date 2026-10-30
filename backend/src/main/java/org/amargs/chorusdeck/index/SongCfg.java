package org.amargs.chorusdeck.index;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SongCfg(
        int id,
        String title,
        List<String> content,
        String bookId,
        String titleAlt,
        List<String> contentAlt,
        String lang,
        String langAlt,
        String defaultLyrics) {

    public static final Set<String> LANGUAGES = Set.of("pt", "en", "es", "fr", "la");

    public SongCfg {
        if (titleAlt != null) {
            titleAlt = titleAlt.strip();
            if (titleAlt.isEmpty()) {
                titleAlt = null;
            }
        }
        if (contentAlt != null && contentAlt.isEmpty()) {
            contentAlt = null;
        }
        if (contentAlt == null) {
            lang = null;
            langAlt = null;
            defaultLyrics = null;
        } else {
            if (lang == null || !LANGUAGES.contains(lang)) {
                lang = "pt";
            }
            if (langAlt == null || !LANGUAGES.contains(langAlt) || langAlt.equals(lang)) {
                langAlt = "la".equals(lang) ? "pt" : "la";
            }
            if (!"contentAlt".equals(defaultLyrics)) {
                defaultLyrics = null;
            }
        }
    }

    public SongCfg(int id, String title, List<String> content) {
        this(id, title, content, null, null, null, null, null, null);
    }

    public SongCfg(int id, String title, List<String> content, String bookId) {
        this(id, title, content, bookId, null, null, null, null, null);
    }

    public SongCfg(
            int id,
            String title,
            List<String> content,
            String bookId,
            String titleAlt,
            List<String> contentAlt) {
        this(id, title, content, bookId, titleAlt, contentAlt, null, null, null);
    }

    public SongCfg(
            int id,
            String title,
            List<String> content,
            String bookId,
            String titleAlt,
            List<String> contentAlt,
            String lang,
            String langAlt) {
        this(id, title, content, bookId, titleAlt, contentAlt, lang, langAlt, null);
    }

    /** The verses a deck uses when the request does not choose a language. */
    public SongCfg prepared() {
        if (!"contentAlt".equals(defaultLyrics) || contentAlt == null || contentAlt.isEmpty()) {
            return this;
        }
        return new SongCfg(id, title, List.copyOf(contentAlt), bookId, titleAlt, null);
    }
}
