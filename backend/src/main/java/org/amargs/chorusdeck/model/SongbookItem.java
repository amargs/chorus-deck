package org.amargs.chorusdeck.model;

public sealed interface SongbookItem permits SongbookItem.Entry, SongbookItem.MissingPrefix {

    record Entry(String bookId, String title) implements SongbookItem {
    }

    record MissingPrefix(String text) implements SongbookItem {
    }

    static String line(SongbookItem item) {
        if (item instanceof Entry entry) {
            return entry.bookId() + "\t" + entry.title();
        }
        MissingPrefix missing = (MissingPrefix) item;
        return "missing-prefix\t" + missing.text();
    }
}
