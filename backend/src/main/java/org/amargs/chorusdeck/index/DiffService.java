package org.amargs.chorusdeck.index;

import org.amargs.chorusdeck.config.AppConfig;
import org.amargs.chorusdeck.text.BoldMarkup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only comparison of slide titles and songbook headings. */
public final class DiffService {

    public record Report(
            List<BookOnly> songbookOnly,
            List<SlideOnly> slideOnly,
            List<Near> near,
            List<Ambiguous> ambiguous,
            List<String> missingPrefix,
            List<NoLyrics> noLyrics) {

        public record BookOnly(String bookId, String title) {
        }

        public record SlideOnly(int id, String title) {
        }

        public record Near(String bookId, String bookTitle, int slideId, String slideTitle) {
        }

        public record Ambiguous(List<String> bookIds, int slideId, String title) {
        }

        public record NoLyrics(int slideId, String bookId, String title) {
        }
    }

    static Report diff(List<SongCfg> songs, List<SongbookItem> items) {
        List<Book> books = new ArrayList<>();
        List<String> missingPrefix = new ArrayList<>();
        for (SongbookItem item : items) {
            if (item instanceof SongbookItem.Entry entry) {
                books.add(new Book(entry.bookId(), entry.title(), TitleNormalizer.normalize(entry.title())));
            } else if (item instanceof SongbookItem.MissingPrefix missing) {
                missingPrefix.add(missing.text());
            }
        }
        List<Slide> slides = new ArrayList<>();
        for (SongCfg song : songs) {
            slides.add(new Slide(song.id(), song.title(), TitleNormalizer.normalize(song.title())));
        }

        Map<String, List<Book>> booksByKey = new HashMap<>();
        for (Book book : books) {
            if (!book.key().isEmpty()) {
                booksByKey.computeIfAbsent(book.key(), ignored -> new ArrayList<>()).add(book);
            }
        }
        Map<String, List<Slide>> slidesByKey = new HashMap<>();
        for (Slide slide : slides) {
            if (!slide.key().isEmpty()) {
                slidesByKey.computeIfAbsent(slide.key(), ignored -> new ArrayList<>()).add(slide);
            }
        }

        Set<String> exact = new HashSet<>();
        Set<String> ambiguousKeys = new HashSet<>();
        Set<String> keys = new HashSet<>();
        keys.addAll(booksByKey.keySet());
        keys.addAll(slidesByKey.keySet());
        for (String key : keys) {
            int bookCount = booksByKey.getOrDefault(key, List.of()).size();
            int slideCount = slidesByKey.getOrDefault(key, List.of()).size();
            if (bookCount == 1 && slideCount == 1) {
                exact.add(key);
            } else if (bookCount > 0 && slideCount > 0) {
                ambiguousKeys.add(key);
            }
        }

        List<Book> bookOnly = new ArrayList<>();
        for (Book book : books) {
            if (!book.key().isEmpty() && !exact.contains(book.key()) && !ambiguousKeys.contains(book.key())) {
                bookOnly.add(book);
            }
        }
        List<Slide> slideOnly = new ArrayList<>();
        for (Slide slide : slides) {
            if (!slide.key().isEmpty() && !exact.contains(slide.key()) && !ambiguousKeys.contains(slide.key())) {
                slideOnly.add(slide);
            }
        }

        List<Report.Near> near = new ArrayList<>();
        Set<Book> pairedBooks = new HashSet<>();
        Set<Slide> pairedSlides = new HashSet<>();
        for (NearPair pair : pair(bookOnly, slideOnly)) {
            pairedBooks.add(pair.book());
            pairedSlides.add(pair.slide());
            near.add(new Report.Near(
                    pair.book().bookId(), pair.book().title(), pair.slide().id(), pair.slide().title()));
        }
        bookOnly.removeIf(pairedBooks::contains);
        slideOnly.removeIf(pairedSlides::contains);

        List<Report.BookOnly> songbookOnly = bookOnly.stream()
                .map(book -> new Report.BookOnly(book.bookId(), book.title()))
                .toList();
        List<Report.SlideOnly> slidesOnly = slideOnly.stream()
                .map(slide -> new Report.SlideOnly(slide.id(), slide.title()))
                .toList();
        List<Report.NoLyrics> noLyrics = new ArrayList<>();
        for (SongCfg song : songs) {
            if (!hasLyrics(song)) {
                noLyrics.add(new Report.NoLyrics(song.id(), song.bookId(), song.title()));
            }
        }
        List<Report.Ambiguous> ambiguous = new ArrayList<>();
        for (String key : ambiguousKeys) {
            List<String> bookIds = booksByKey.get(key).stream().map(Book::bookId).toList();
            for (Slide slide : slidesByKey.get(key)) {
                ambiguous.add(new Report.Ambiguous(bookIds, slide.id(), slide.title()));
            }
        }
        return new Report(songbookOnly, slidesOnly, near, ambiguous, missingPrefix, noLyrics);
    }

    public Report compare(AppConfig config) throws IOException {
        List<SongCfg> songs = SongIndex.read(config.indexFile());
        SongbookReader books = new SongbookReader();
        List<SongbookItem> items = new ArrayList<>();
        Path directory = config.indexFile().getParent();
        items.addAll(books.read(directory.resolve("songbook_default.docx")));
        items.addAll(books.read(directory.resolve("songbook_rites.docx")));
        return diff(songs, items);
    }

    private static List<NearPair> pair(List<Book> books, List<Slide> slides) {
        List<NearPair> candidates = new ArrayList<>();
        for (Book book : books) {
            for (Slide slide : slides) {
                int distance = distance(book.key(), slide.key());
                if (distance >= 0) {
                    candidates.add(new NearPair(distance, book, slide));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(NearPair::distance)
                .thenComparing(candidate -> candidate.book().bookId())
                .thenComparingInt(candidate -> candidate.slide().id()));
        Set<Book> usedBooks = new HashSet<>();
        Set<Slide> usedSlides = new HashSet<>();
        List<NearPair> pairs = new ArrayList<>();
        for (NearPair candidate : candidates) {
            if (usedBooks.contains(candidate.book()) || usedSlides.contains(candidate.slide())) {
                continue;
            }
            usedBooks.add(candidate.book());
            usedSlides.add(candidate.slide());
            pairs.add(candidate);
        }
        return pairs;
    }

    /** A titled slide with no verse text cannot be inserted. */
    static boolean hasLyrics(SongCfg song) {
        if (song.content() == null) {
            return false;
        }
        for (String verse : song.content()) {
            if (!BoldMarkup.plain(verse).isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** Distance when the titles are close, or -1 when they are not a near match. */
    static int distance(String normalizedBook, String normalizedSlide) {
        if (normalizedBook.isEmpty() || normalizedSlide.isEmpty() || normalizedBook.equals(normalizedSlide)) {
            return -1;
        }
        String shorter = normalizedBook.length() <= normalizedSlide.length() ? normalizedBook : normalizedSlide;
        String longer = shorter == normalizedBook ? normalizedSlide : normalizedBook;
        if (longer.contains(shorter) && shorter.length() >= 8 && longer.length() - shorter.length() <= 16) {
            return longer.length() - shorter.length();
        }
        return levenshtein(normalizedBook, normalizedSlide, 4);
    }

    private static int levenshtein(String left, String right, int max) {
        if (Math.abs(left.length() - right.length()) > max) {
            return -1;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            int rowBest = current[0];
            for (int column = 1; column <= right.length(); column++) {
                int cost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + cost);
                rowBest = Math.min(rowBest, current[column]);
            }
            if (rowBest > max) {
                return -1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()] <= max ? previous[right.length()] : -1;
    }

    private record Book(String bookId, String title, String key) {
    }

    private record Slide(int id, String title, String key) {
    }

    private record NearPair(int distance, Book book, Slide slide) {
    }
}
