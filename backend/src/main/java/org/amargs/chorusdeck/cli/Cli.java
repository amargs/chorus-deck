package org.amargs.chorusdeck.cli;

import org.amargs.chorusdeck.config.AppConfig;
import org.amargs.chorusdeck.deck.Cover;
import org.amargs.chorusdeck.deck.DeckService;
import org.amargs.chorusdeck.deck.DeckSpec;
import org.amargs.chorusdeck.deck.Interval;
import org.amargs.chorusdeck.deck.MassRequest;
import org.amargs.chorusdeck.index.DiffService;
import org.amargs.chorusdeck.index.IndexService;
import org.amargs.chorusdeck.index.SongbookItem;
import org.amargs.chorusdeck.index.SongbookReader;
import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.index.SourceSnapshot;
import org.amargs.chorusdeck.index.StaleSources;
import org.amargs.chorusdeck.liturgy.LiturgyClient;
import org.amargs.chorusdeck.liturgy.LiturgyDay;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
        String interval = null;
        Path intervalImage = null;
        String intervalText = null;
        Path massFile = null;
        String date = null;

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
                case "--interval" -> {
                    requireGenerate(command, "--interval");
                    interval = single(interval, requireValue(args, ++i, "--interval"), "--interval");
                }
                case "--interval-image" -> {
                    requireGenerate(command, "--interval-image");
                    if (intervalImage != null) {
                        throw new IllegalArgumentException("Duplicate --interval-image.\n" + usage());
                    }
                    intervalImage = Path.of(requireValue(args, ++i, "--interval-image"));
                }
                case "--date" -> {
                    requireGenerate(command, "--date");
                    date = single(date, requireValue(args, ++i, "--date"), "--date");
                }
                case "--mass" -> {
                    requireGenerate(command, "--mass");
                    if (massFile != null) {
                        throw new IllegalArgumentException("Duplicate --mass.\n" + usage());
                    }
                    massFile = Path.of(requireValue(args, ++i, "--mass"));
                }
                case "--interval-text" -> {
                    requireGenerate(command, "--interval-text");
                    intervalText = single(intervalText, requireValue(args, ++i, "--interval-text"), "--interval-text");
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
                LocalDate liturgyDate = date == null ? null : parseDate(date);
                if (liturgyDate != null && massFile == null) {
                    throw new IllegalArgumentException("--date is used with --mass.\n" + usage());
                }
                if (massFile != null) {
                    if (ids != null || bookIds != null || mode != null || withCover || season != null || cover != null
                            || domingo != null || frase != null || versiculo != null || ano != null
                            || interval != null || intervalImage != null || intervalText != null) {
                        throw new IllegalArgumentException("--mass is used instead of the other generate flags.\n" + usage());
                    }
                    printStale(new IndexService().warnIfStale(configFile, config));
                    LOG.info("Checked sources");
                    DeckSpec spec = DeckSpec.load(massFile);
                    DeckService decks = new DeckService();
                    if (spec instanceof DeckSpec.Mass mass) {
                        MassRequest request = mass.request();
                        LocalDate day = liturgyDate != null ? liturgyDate : request.date();
                        if (day != null) {
                            LiturgyDay liturgy = new LiturgyClient().load(day, config.indexFile().resolveSibling("sundays.json"));
                            request = request.withLiturgy(liturgy);
                            LOG.info("Read " + liturgy.domingo() + " (" + liturgy.ano() + ")");
                        }
                        Path output = mass.output();
                        if (output == null) {
                            output = massFile.toAbsolutePath().getParent()
                                    .resolve(DeckSpec.fileName(
                                            day,
                                            request.cover().domingo(),
                                            request.cover().season(),
                                            request.cover().ano()))
                                    .normalize();
                        }
                        decks.generateMass(config.withOutput(output), request);
                    } else {
                        if (liturgyDate != null) {
                            throw new IllegalArgumentException("A song deck does not take a date.\n" + usage());
                        }
                        config = config.withOutput(spec.output());
                        DeckSpec.Songs songs = (DeckSpec.Songs) spec;
                        DeckService.Selection selection = songs.ids() != null
                                ? new DeckService.Selection.Ids(songs.ids())
                                : new DeckService.Selection.BookIds(songs.bookIds());
                        decks.generateSongs(config, selection, songs.cover(), songs.interval());
                    }
                    break;
                }
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
                if (!songs && (interval != null || intervalImage != null || intervalText != null)) {
                    throw new IllegalArgumentException("Interval options are only valid with --mode songs.\n" + usage());
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
                printStale(new IndexService().warnIfStale(configFile, config));
                LOG.info("Checked sources");
                if (config.outputFile() == null) {
                    if (!includeCover) {
                        throw new IllegalArgumentException(
                                "Set outputFile in config.json for a song deck without a Sunday.\n" + usage());
                    }
                    config = config.withOutput(configFile.getParent().resolve(
                            DeckSpec.fileName(null, domingo, season, year(ano))).normalize());
                }
                DeckService decks = new DeckService();
                if (songs) {
                    Cover coverFields = withCover
                            ? new Cover(season, cover, domingo, frase, versiculo, year(ano))
                            : null;
                    decks.generateSongs(config, selection, coverFields, Interval.parse(interval, intervalImage, intervalText));
                } else {
                    decks.generate(config, selection, new Cover(
                            season, cover, domingo, frase, versiculo, year(ano)));
                }
            }
            default -> throw new IllegalArgumentException("Unknown command: " + command + "\n" + usage());
        }
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("--date requires YYYY-MM-DD.");
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
        SongbookReader books = new SongbookReader();
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
                  generate --mode songs (--ids <id,id,...> | --book-ids <bookId,bookId,...>) [--interval vazio|imagem|texto|texto+imagem] [--interval-image <file>] [--interval-text <text>] [--with-cover --season <season> --ano <A|B|C> --domingo <text> --frase <text> --versiculo <text> [--cover <layout>]] [--config <file>]
                  generate --mass <file.json|file.yaml> [--date YYYY-MM-DD] [--config <file>]""";
    }
}
