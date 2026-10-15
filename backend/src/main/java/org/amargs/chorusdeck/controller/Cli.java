package org.amargs.chorusdeck.controller;

import org.amargs.chorusdeck.config.AppConfig;
import org.amargs.chorusdeck.model.Cover;
import org.amargs.chorusdeck.model.SongCfg;
import org.amargs.chorusdeck.model.SongbookItem;
import org.amargs.chorusdeck.model.SourceSnapshot;
import org.amargs.chorusdeck.model.StaleSources;
import org.amargs.chorusdeck.service.DeckService;
import org.amargs.chorusdeck.service.DiffService;
import org.amargs.chorusdeck.service.IndexService;
import org.amargs.chorusdeck.service.SongbookService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Cli {

    private static final Logger LOG = LogManager.getLogger(Cli.class);

    private Cli() {
    }

    public static void run(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException(usage());
        }

        String command = args[0];
        Path configArg = null;
        List<Integer> ids = null;
        List<String> bookIds = null;
        String season = null;
        String cover = null;
        String domingo = null;
        String frase = null;
        String versiculo = null;
        String ano = null;
        String mode = null;
        boolean withCover = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--config" -> {
                    if (configArg != null) {
                        throw new IllegalArgumentException("Duplicate --config.\n" + usage());
                    }
                    configArg = Path.of(requireValue(args, ++i, "--config"));
                }
                case "--ids" -> {
                    requireGenerate(command, "--ids");
                    if (ids != null) {
                        throw new IllegalArgumentException("Duplicate --ids.\n" + usage());
                    }
                    ids = parseIds(requireValue(args, ++i, "--ids"));
                }
                case "--book-ids" -> {
                    requireGenerate(command, "--book-ids");
                    if (bookIds != null) {
                        throw new IllegalArgumentException("Duplicate --book-ids.\n" + usage());
                    }
                    bookIds = parseBookIds(requireValue(args, ++i, "--book-ids"));
                }
                case "--season" -> {
                    requireGenerate(command, "--season");
                    season = single(season, requireValue(args, ++i, "--season"), "--season");
                }
                case "--cover" -> {
                    requireGenerate(command, "--cover");
                    cover = single(cover, requireValue(args, ++i, "--cover"), "--cover");
                }
                case "--domingo" -> {
                    requireGenerate(command, "--domingo");
                    domingo = single(domingo, requireValue(args, ++i, "--domingo"), "--domingo");
                }
                case "--frase" -> {
                    requireGenerate(command, "--frase");
                    frase = single(frase, requireValue(args, ++i, "--frase"), "--frase");
                }
                case "--versiculo" -> {
                    requireGenerate(command, "--versiculo");
                    versiculo = single(versiculo, requireValue(args, ++i, "--versiculo"), "--versiculo");
                }
                case "--ano" -> {
                    requireGenerate(command, "--ano");
                    ano = single(ano, requireValue(args, ++i, "--ano"), "--ano");
                }
                case "--mode" -> {
                    requireGenerate(command, "--mode");
                    mode = single(mode, requireValue(args, ++i, "--mode"), "--mode");
                }
                case "--with-cover" -> {
                    requireGenerate(command, "--with-cover");
                    if (withCover) {
                        throw new IllegalArgumentException("Duplicate --with-cover.\n" + usage());
                    }
                    withCover = true;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + arg + "\n" + usage());
            }
        }

        Path configFile = AppConfig.findConfig(configArg);
        AppConfig config = AppConfig.load(configFile);
        switch (command) {
            case "index" -> printIndex(new IndexService().rebuild(config, configFile));
            case "songbook" -> printSongbooks(configFile.getParent());
            case "diff-sources" -> printDiff(new DiffService().compare(config));
            case "generate" -> {
                if (ids == null && bookIds == null) {
                    throw new IllegalArgumentException("generate requires --ids or --book-ids.\n" + usage());
                }
                if (ids != null && bookIds != null) {
                    throw new IllegalArgumentException("generate takes --ids or --book-ids, not both.\n" + usage());
                }
                boolean songs = "songs".equals(mode);
                if (mode != null && !songs) {
                    throw new IllegalArgumentException("Unknown mode: " + mode + "\n" + usage());
                }
                if (withCover && !songs) {
                    throw new IllegalArgumentException("--with-cover is only valid with --mode songs.\n" + usage());
                }
                boolean includeCover = !songs || withCover;
                if (includeCover && (season == null || domingo == null || frase == null || versiculo == null || ano == null)) {
                    throw new IllegalArgumentException(
                            "generate requires --season, --ano, --domingo, --frase, and --versiculo.\n" + usage());
                }
                if (songs && !withCover && (season != null || cover != null || domingo != null || frase != null || versiculo != null || ano != null)) {
                    throw new IllegalArgumentException(
                            "A song deck without --with-cover does not take cover fields.\n" + usage());
                }
                DeckService.Selection selection = ids != null
                        ? new DeckService.Selection.Ids(ids)
                        : new DeckService.Selection.BookIds(bookIds);
                long staleStart = System.nanoTime();
                printStale(new IndexService().warnIfStale(configFile, config));
                LOG.info("Checked sources (" + (System.nanoTime() - staleStart) / 1_000_000 + " ms)");
                DeckService decks = new DeckService();
                if (songs) {
                    Cover coverFields = withCover
                            ? new Cover(season, cover, domingo, frase, versiculo, year(ano))
                            : null;
                    decks.generateSongs(config, selection, coverFields);
                } else {
                    decks.generate(config, selection, new Cover(
                            season, cover, domingo, frase, versiculo, year(ano)));
                }
            }
            default -> throw new IllegalArgumentException("Unknown command: " + command + "\n" + usage());
        }
    }

    private static String year(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("--ano requires A, B, or C.");
        }
        String year = raw.trim().toUpperCase(Locale.ROOT);
        if (!year.equals("A") && !year.equals("B") && !year.equals("C")) {
            throw new IllegalArgumentException("--ano requires A, B, or C.");
        }
        return year;
    }

    private static void printIndex(IndexService.IndexResult result) {
        if (result.refresh() == SourceSnapshot.Refresh.UNCHANGED) {
            LOG.info("Sources unchanged");
            return;
        }
        if (result.refresh() == SourceSnapshot.Refresh.SONGBOOK) {
            LOG.info("Songbook changed");
        }
        for (IndexService.IndexResult.Notice notice : result.notices()) {
            if (notice instanceof IndexService.IndexResult.Notice.Song song) {
                LOG.info(song.title());
            } else if (notice instanceof IndexService.IndexResult.Notice.Skipped skipped) {
                LOG.info("Skipped " + skipped.title());
            }
        }
        for (SongbookItem.MissingPrefix missing : result.missingPrefixes()) {
            LOG.info(SongbookItem.line(missing));
        }
        for (SongCfg song : result.removed()) {
            LOG.info("Removed " + song.id() + ": " + song.title().trim());
        }
        for (SongCfg song : result.added()) {
            LOG.info("New " + song.id() + ": " + song.title().trim());
        }
        for (String warning : result.warnings()) {
            LOG.info(warning);
        }
        if (result.bookIdCount() != null) {
            LOG.info("bookId on " + result.bookIdCount() + " of " + result.songCount() + " songs");
        }
    }

    private static void printStale(StaleSources stale) {
        if (stale.missing()) {
            LOG.error("Source hashes are missing. Run index before trusting this deck.");
        } else if (!stale.changed().isEmpty()) {
            LOG.error("Changed since last index: " + String.join(", ", stale.changed())
                    + ". Run index before trusting this deck.");
        }
    }

    private static void printDiff(DiffService.Report report) {
        printLines("Songbook only", report.songbookOnly().stream()
                .map(row -> row.bookId() + "\t" + row.title()).toList());
        printLines("Slide only", report.slideOnly().stream()
                .map(row -> row.id() + "\t" + row.title()).toList());
        printTable("Near matches", report.near().stream()
                .map(row -> List.of(row.bookId(), row.bookTitle(), Integer.toString(row.slideId()), row.slideTitle()))
                .toList(), "songbook id", "songbook title", "slide id", "slide title");
        printLines("Ambiguous", report.ambiguous().stream()
                .map(row -> String.join(",", row.bookIds()) + "\t" + row.slideId() + "\t" + row.title()).toList());
        printLines("Missing prefix", report.missingPrefix());
        printTable("No lyrics", report.noLyrics().stream()
                .map(row -> List.of(Integer.toString(row.slideId()), row.bookId() == null ? "" : row.bookId(), row.title()))
                .toList(), "slide id", "book id", "title");
        if (report.songbookOnly().isEmpty()
                && report.slideOnly().isEmpty()
                && report.near().isEmpty()
                && report.ambiguous().isEmpty()
                && report.missingPrefix().isEmpty()
                && report.noLyrics().isEmpty()) {
            LOG.info("Songbook and slides agree.");
        }
    }

    private static void printLines(String heading, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        LOG.info(heading);
        for (String line : lines) {
            LOG.info(line);
        }
        LOG.info("");
    }

    private static void printTable(String heading, List<List<String>> rows, String... headers) {
        if (rows.isEmpty()) {
            return;
        }
        LOG.info(heading);
        for (String line : TextTable.format(rows, headers)) {
            LOG.info(line);
        }
        LOG.info("");
    }

    private static void printSongbooks(Path configDir) throws Exception {
        SongbookService books = new SongbookService();
        for (String name : List.of("songbook_default.docx", "songbook_rites.docx")) {
            Path docx = configDir.resolve("data").resolve(name);
            LOG.info(docx.getFileName());
            for (SongbookItem item : books.read(docx)) {
                LOG.info(SongbookItem.line(item));
            }
            LOG.info("");
        }
    }

    private static void requireGenerate(String command, String flag) {
        if (!"generate".equals(command)) {
            throw new IllegalArgumentException(flag + " is only valid for generate.\n" + usage());
        }
    }

    private static String single(String current, String value, String flag) {
        if (current != null) {
            throw new IllegalArgumentException("Duplicate " + flag + ".\n" + usage());
        }
        return value;
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(flag + " requires a value.\n" + usage());
        }
        return args[index];
    }

    private static List<Integer> parseIds(String raw) {
        String[] parts = raw.split(",");
        List<Integer> ids = new ArrayList<>();
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Empty song id in --ids.\n" + usage());
            }
            try {
                int id = Integer.parseInt(token);
                if (id < 1) {
                    throw new IllegalArgumentException("Song ids start at 1.\n" + usage());
                }
                ids.add(id);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a song id: " + token + "\n" + usage());
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("generate requires --ids or --book-ids.\n" + usage());
        }
        return ids;
    }

    private static List<String> parseBookIds(String raw) {
        String[] parts = raw.split(",");
        List<String> bookIds = new ArrayList<>();
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Empty songbook id in --book-ids.\n" + usage());
            }
            bookIds.add(token);
        }
        if (bookIds.isEmpty()) {
            throw new IllegalArgumentException("generate requires --ids or --book-ids.\n" + usage());
        }
        return bookIds;
    }

    private static String usage() {
        return """
                Usage:
                  index [--config <file>]
                  diff-sources [--config <file>]
                  songbook [--config <file>]
                  generate (--ids <id,id,...> | --book-ids <bookId,bookId,...>) --season <season> --ano <A|B|C> --domingo <text> --frase <text> --versiculo <text> [--cover <layout>] [--config <file>]
                  generate --mode songs (--ids <id,id,...> | --book-ids <bookId,bookId,...>) [--with-cover --season <season> --ano <A|B|C> --domingo <text> --frase <text> --versiculo <text> [--cover <layout>]] [--config <file>]""";
    }
}
