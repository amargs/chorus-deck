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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Cli {

    private Cli() {
    }

    public static void run(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException(usage());
        }

        String command = args[0];
        Path configArg = null;
        List<Integer> ids = null;
        String season = null;
        String cover = null;
        String domingo = null;
        String frase = null;
        String versiculo = null;
        String ano = null;

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
                if (ids == null) {
                    throw new IllegalArgumentException("generate requires --ids.\n" + usage());
                }
                if (season == null || domingo == null || frase == null || versiculo == null || ano == null) {
                    throw new IllegalArgumentException(
                            "generate requires --season, --ano, --domingo, --frase, and --versiculo.\n" + usage());
                }
                printStale(new IndexService().warnIfStale(configFile, config));
                printGenerated(config, new DeckService().generate(config, ids, new Cover(
                        season, cover, domingo, frase, versiculo, year(ano))));
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
            System.out.println("Sources unchanged");
            return;
        }
        if (result.refresh() == SourceSnapshot.Refresh.SONGBOOK) {
            System.out.println("Songbook changed");
        }
        for (IndexService.IndexResult.Notice notice : result.notices()) {
            if (notice instanceof IndexService.IndexResult.Notice.Song song) {
                System.out.println(song.title());
            } else if (notice instanceof IndexService.IndexResult.Notice.Skipped skipped) {
                System.out.println("Skipped " + skipped.title());
            }
        }
        for (SongbookItem.MissingPrefix missing : result.missingPrefixes()) {
            System.out.println(SongbookItem.line(missing));
        }
        for (SongCfg song : result.removed()) {
            System.out.println("Removed " + song.id() + ": " + song.title().trim());
        }
        for (SongCfg song : result.added()) {
            System.out.println("New " + song.id() + ": " + song.title().trim());
        }
        for (String warning : result.warnings()) {
            System.out.println(warning);
        }
        if (result.bookIdCount() != null) {
            System.out.println("bookId on " + result.bookIdCount() + " of " + result.songCount() + " songs");
        }
    }

    private static void printStale(StaleSources stale) {
        if (stale.missing()) {
            System.err.println("Source hashes are missing. Run index before trusting this deck.");
        } else if (!stale.changed().isEmpty()) {
            System.err.println("Changed since last index: " + String.join(", ", stale.changed())
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
            System.out.println("Songbook and slides agree.");
        }
    }

    private static void printGenerated(AppConfig config, DeckService.Generated generated) {
        System.out.println("Reading " + config.indexFile().toAbsolutePath());
        System.out.println("Loaded " + generated.loadedSongs() + " songs");
        System.out.println("Opening " + config.template4x3().toAbsolutePath());
        System.out.println("Cover: " + generated.coverLayout());
        for (DeckService.Generated.Song song : generated.songs()) {
            System.out.println("Song " + song.id() + ": " + song.title() + " (" + song.verses() + " verses)");
            for (int verse = 1; verse <= song.verses(); verse++) {
                System.out.println("  Verse " + verse + " of " + song.verses());
            }
        }
        System.out.println("Writing " + config.outputFile().toAbsolutePath() + " (" + generated.slides() + " slides)");
        System.out.println("Done");
    }

    private static void printLines(String heading, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        System.out.println(heading);
        for (String line : lines) {
            System.out.println(line);
        }
        System.out.println();
    }

    private static void printTable(String heading, List<List<String>> rows, String... headers) {
        if (rows.isEmpty()) {
            return;
        }
        System.out.println(heading);
        for (String line : TextTable.format(rows, headers)) {
            System.out.println(line);
        }
        System.out.println();
    }

    private static void printSongbooks(Path configDir) throws Exception {
        SongbookService books = new SongbookService();
        for (String name : List.of("songbook_default.docx", "songbook_rites.docx")) {
            Path docx = configDir.resolve("data").resolve(name);
            System.out.println(docx.getFileName());
            for (SongbookItem item : books.read(docx)) {
                System.out.println(SongbookItem.line(item));
            }
            System.out.println();
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
            throw new IllegalArgumentException("generate requires --ids.\n" + usage());
        }
        return ids;
    }

    private static String usage() {
        return """
                Usage:
                  index [--config <file>]
                  diff-sources [--config <file>]
                  songbook [--config <file>]
                  generate --ids <id,id,...> --season <season> --ano <A|B|C> --domingo <text> --frase <text> --versiculo <text> [--cover <layout>] [--config <file>]""";
    }
}
