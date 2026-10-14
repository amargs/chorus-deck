package org.amargs.chorusdeck;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
public final class SourceDiff {

    public record Report(
            List<String> songbookOnly,
            List<String> slideOnly,
            List<String> near,
            List<String> ambiguous,
            List<String> missingPrefix,
            List<String> noLyrics) {
    }

    public void print(AppConfig config) throws IOException {
        Report report = compare(config);
        printSection("Songbook only", report.songbookOnly());
        printSection("Slide only", report.slideOnly());
        printTable("Near matches", report.near(), "songbook id", "songbook title", "slide id", "slide title");
        printSection("Ambiguous", report.ambiguous());
        printSection("Missing prefix", report.missingPrefix());
        printTable("No lyrics", report.noLyrics(), "slide id", "book id", "title");
        if (report.songbookOnly().isEmpty()
                && report.slideOnly().isEmpty()
                && report.near().isEmpty()
                && report.ambiguous().isEmpty()
                && report.missingPrefix().isEmpty()
                && report.noLyrics().isEmpty()) {
            System.out.println("Songbook and slides agree.");
        }
    }

    static Report diff(List<SongCfg> songs, List<SongbookReader.Item> items) {
        List<Book> books = new ArrayList<>();
        List<String> missingPrefix = new ArrayList<>();
        for (SongbookReader.Item item : items) {
            if (item instanceof SongbookReader.Entry entry) {
                books.add(new Book(entry.bookId(), entry.title(), TitleNormalizer.normalize(entry.title())));
            } else if (item instanceof SongbookReader.MissingPrefix missing) {
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

        List<String> near = new ArrayList<>();
        Set<Book> pairedBooks = new HashSet<>();
        Set<Slide> pairedSlides = new HashSet<>();
        for (NearPair pair : pair(bookOnly, slideOnly)) {
            pairedBooks.add(pair.book());
            pairedSlides.add(pair.slide());
            near.add(pair.book().bookId() + "\t" + pair.book().title()
                    + "\t" + pair.slide().id() + "\t" + pair.slide().title());
        }
        bookOnly.removeIf(pairedBooks::contains);
        slideOnly.removeIf(pairedSlides::contains);

        List<String> songbookOnly = bookOnly.stream().map(book -> book.bookId() + "\t" + book.title()).toList();
        List<String> slidesOnly = slideOnly.stream().map(slide -> slide.id() + "\t" + slide.title()).toList();
        List<String> noLyrics = new ArrayList<>();
        for (SongCfg song : songs) {
            if (!hasLyrics(song)) {
                String bookId = song.bookId() == null ? "" : song.bookId();
                noLyrics.add(song.id() + "\t" + bookId + "\t" + song.title());
            }
        }
        List<String> ambiguous = new ArrayList<>();
        for (String key : ambiguousKeys) {
            String bookIds = booksByKey.get(key).stream().map(Book::bookId).reduce((left, right) -> left + "," + right).orElse("");
            for (Slide slide : slidesByKey.get(key)) {
                ambiguous.add(bookIds + "\t" + slide.id() + "\t" + slide.title());
            }
        }
        return new Report(songbookOnly, slidesOnly, near, ambiguous, missingPrefix, noLyrics);
    }

    private static Report compare(AppConfig config) throws IOException {
        List<SongCfg> songs = new ObjectMapper().readValue(
                config.indexFile().toFile(), new TypeReference<List<SongCfg>>() {});
        SongbookReader reader = new SongbookReader();
        List<SongbookReader.Item> items = new ArrayList<>();
        Path directory = config.indexFile().getParent();
        items.addAll(reader.read(directory.resolve("songbook_default.docx")));
        items.addAll(reader.read(directory.resolve("songbook_rites.docx")));
        return diff(songs, items);
    }

    private static void printSection(String heading, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        System.out.println(heading);
        for (String line : lines) {
            System.out.println(line);
        }
        System.out.println();
    }

    private static void printTable(String heading, List<String> rows, String... headers) {
        if (rows.isEmpty()) {
            return;
        }
        System.out.println(heading);
        for (String line : table(rows, headers)) {
            System.out.println(line);
        }
        System.out.println();
    }

    static List<String> table(List<String> rows, String... headers) {
        List<String[]> cells = new ArrayList<>();
        for (String row : rows) {
            cells.add(row.split("\t", -1));
        }
        int[] width = new int[headers.length];
        for (int column = 0; column < headers.length; column++) {
            width[column] = headers[column].length();
        }
        for (String[] row : cells) {
            for (int column = 0; column < headers.length && column < row.length; column++) {
                width[column] = Math.max(width[column], row[column].length());
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(formatRow(headers, width));
        lines.add(formatRule(width));
        for (String[] row : cells) {
            lines.add(formatRow(row, width));
        }
        return lines;
    }

    private static String formatRow(String[] cells, int[] width) {
        StringBuilder line = new StringBuilder("|");
        for (int column = 0; column < width.length; column++) {
            String value = column < cells.length ? cells[column] : "";
            line.append(' ').append(pad(value, width[column])).append(" |");
        }
        return line.toString();
    }

    private static String formatRule(int[] width) {
        StringBuilder line = new StringBuilder("|");
        for (int column : width) {
            line.append("-".repeat(column + 2)).append("|");
        }
        return line.toString();
    }

    private static String pad(String value, int width) {
        return value + " ".repeat(Math.max(0, width - value.length()));
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
