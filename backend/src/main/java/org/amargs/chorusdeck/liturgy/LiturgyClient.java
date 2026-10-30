package org.amargs.chorusdeck.liturgy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public final class LiturgyClient {

    private static final Logger LOG = LogManager.getLogger(LiturgyClient.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final CalendarSource source;

    public LiturgyClient() {
        this(LiturgyClient::httpGet);
    }

    LiturgyClient(CalendarSource source) {
        this.source = source;
    }

    public LiturgyDay load(LocalDate date, Path libraryFile) {
        SundayLibrary library = new SundayLibrary(libraryFile);
        if (isCorpusChristi(date)) {
            LiturgyDay corpus = corpusChristi(date);
            return overlay(library, corpus, "corpus-christi/" + corpus.ano());
        }
        RomanCalendar calendar;
        try {
            calendar = RomanCalendar.parse(source.fetch(date));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return withScriptures(offline(date, library), null);
        }
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            if (calendar.sundayId() == null) {
                throw new IllegalArgumentException("The calendar response has no Sunday.");
            }
            LiturgyDay day;
            try {
                day = library.resolve(date, calendar);
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not write " + libraryFile.toAbsolutePath() + ".", e);
            }
            FixedSolemnity solemnity = FixedSolemnity.of(calendar.name());
            if (solemnity != null) {
                LOG.warn(sundaySolemnity(date, day, calendar, solemnity));
            }
            if (date.getMonthValue() == 12 && date.getDayOfMonth() == 25) {
                day = overlay(library, day, "christmas");
            }
            return withScriptures(day, calendar);
        }
        LiturgyDay weekday = withScriptures(unfilled(date, calendar), calendar);
        FixedSolemnity solemnity = FixedSolemnity.on(date);
        if (solemnity != null) {
            weekday = overlay(library, weekday, solemnity.key());
        }
        if (date.getMonthValue() == 12 && date.getDayOfMonth() == 25) {
            weekday = overlay(library, weekday, "christmas");
        }
        return weekday;
    }

    private static LiturgyDay overlay(SundayLibrary library, LiturgyDay day, String key) {
        try {
            return library.overlay(day, key);
        } catch (IOException e) {
            return day;
        }
    }

    public static String url(LocalDate date) {
        return "https://api.parishcompanion.app/calendar/AU?date=" + date;
    }

    /** A Sunday that is also one of the fixed solemnities. Absent on every other day. */
    public record SundaySolemnity(String title, String cover, boolean outranksSunday) {
    }

    /** A weekday that is one of the fixed solemnities. Absent on Sundays and every other day. */
    public record WeekdaySolemnity(String title, String cover) {
    }

    public static SundaySolemnity sundayOverlap(LocalDate date, String seasonKey) {
        if (date == null || date.getDayOfWeek() != DayOfWeek.SUNDAY) {
            return null;
        }
        FixedSolemnity solemnity = FixedSolemnity.on(date);
        if (solemnity == null) {
            return null;
        }
        return new SundaySolemnity(solemnity.title(), solemnity.cover(), !sundayKeepsTheDay(seasonKey));
    }

    public static WeekdaySolemnity weekdaySolemnity(LocalDate date) {
        if (date == null || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return null;
        }
        FixedSolemnity solemnity = FixedSolemnity.on(date);
        if (solemnity == null) {
            return null;
        }
        return new WeekdaySolemnity(solemnity.title(), solemnity.cover());
    }

    /** Corpus Christi is the Thursday after Trinity Sunday, sixty days after Easter. */
    public static LocalDate corpusChristi(int year) {
        return easter(year).plusDays(60);
    }

    static boolean isCorpusChristi(LocalDate date) {
        return date != null && date.equals(corpusChristi(date.getYear()));
    }

    private LiturgyDay corpusChristi(LocalDate date) {
        RomanCalendar calendar = null;
        try {
            calendar = RomanCalendar.parse(source.fetch(date));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        String season = "comum";
        String cycle = liturgicalCycle(date);
        if (calendar != null) {
            if (calendar.cycle() != null && !calendar.cycle().isBlank()) {
                cycle = calendar.cycle();
            }
            try {
                season = SundayLibrary.seasonKey(calendar);
            } catch (RuntimeException ignored) {
                season = "comum";
            }
        }
        LOG.warn(date + " is Corpus Christi. The scriptures were not filled in.");
        return withScriptures(new LiturgyDay(
                date,
                SundayLibrary.CORPUS_CHRISTI,
                season,
                null,
                cycle,
                null,
                null,
                null,
                null,
                null), calendar);
    }

    /** The Sunday-cycle letter for a date. 2026 is A, 2027 is B, 2025 is C. */
    static String liturgicalCycle(LocalDate date) {
        LocalDate advent = LocalDate.of(date.getYear(), 11, 27).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        int year = date.isBefore(advent) ? date.getYear() : date.getYear() + 1;
        return switch (Math.floorMod(year, 3)) {
            case 0 -> "A";
            case 1 -> "B";
            default -> "C";
        };
    }

    private static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    static String sundaySolemnity(LocalDate date, LiturgyDay day, RomanCalendar calendar, FixedSolemnity solemnity) {
        String kept = date + " is also " + solemnity.title() + ". The deck keeps " + day.domingo() + " (" + day.ano() + "). ";
        if (sundayKeepsTheDay(calendar.season())) {
            return kept + "This Sunday outranks the solemnity, so those readings stay.";
        }
        return kept + "The scriptures are that Sunday's, and this solemnity outranks it.";
    }

    /** Sundays of Advent, Lent, and Easter outrank a feast or solemnity that falls on them. */
    private static boolean sundayKeepsTheDay(String season) {
        return switch (season == null ? "" : season) {
            case "advento", "advent", "quaresma", "lent", "ramos", "holy_week", "pascoa", "easter" -> true;
            default -> false;
        };
    }

    private LiturgyDay unfilled(LocalDate date, RomanCalendar calendar) {
        if (calendar.primeira() == null && calendar.evangelho() == null) {
            LOG.warn(date + " has no scriptures from the calendar. The readings stay blank.");
        }
        FixedSolemnity solemnity = FixedSolemnity.of(calendar.name());
        String title = solemnity == null ? caption(date) : solemnity.title();
        String cover = solemnity == null ? null : solemnity.cover();
        return new LiturgyDay(
                date,
                title,
                SundayLibrary.seasonKey(calendar),
                cover,
                calendar.cycle(),
                new LiturgyDay.Reading(blank(calendar.primeira()), ""),
                null,
                "",
                new LiturgyDay.Reading(blank(calendar.evangelho()), ""),
                null);
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    /** A solemnity keeps primeira, segunda, the psalm, and the Gospel, on a weekday as on a Sunday. */
    private static LiturgyDay withScriptures(LiturgyDay day, RomanCalendar calendar) {
        if (!isSolemnity(day)) {
            return day;
        }
        return new LiturgyDay(
                day.date(),
                day.domingo(),
                day.seasonKey(),
                day.coverLayout(),
                day.ano(),
                orBlank(day.primeira(), calendar == null ? null : calendar.primeira()),
                orBlank(day.segunda(), calendar == null ? null : calendar.segunda()),
                day.refrao() == null ? "" : day.refrao(),
                orBlank(day.evangelho(), calendar == null ? null : calendar.evangelho()),
                day.louvor());
    }

    private static boolean isSolemnity(LiturgyDay day) {
        if (day == null) {
            return false;
        }
        if (FixedSolemnity.on(day.date()) != null || isCorpusChristi(day.date())) {
            return true;
        }
        String title = day.domingo();
        return title != null && title.startsWith("Solenidade ");
    }

    private static LiturgyDay.Reading orBlank(LiturgyDay.Reading reading, String referencia) {
        if (reading != null) {
            return reading;
        }
        return new LiturgyDay.Reading(referencia == null ? "" : referencia, "");
    }

    private String caption(LocalDate date) {
        String day = weekday(date.getDayOfWeek());
        LocalDate sunday = date.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        String week = week(sunday);
        if (week == null) {
            LOG.warn("The week number for " + date + " could not be taken from " + sunday + ".");
            return day;
        }
        return day + " da semana " + week;
    }

    private String week(LocalDate sunday) {
        try {
            return SundayLibrary.week(RomanCalendar.parse(source.fetch(sunday)).sundayId());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String weekday(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "2ªf";
            case TUESDAY -> "3ªf";
            case WEDNESDAY -> "4ªf";
            case THURSDAY -> "5ªf";
            case FRIDAY -> "6ªf";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }

    private static LiturgyDay offline(LocalDate date, SundayLibrary library) {
        try {
            LiturgyDay stored = library.stored(date);
            if (stored != null) {
                return stored;
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the Sunday file.", e);
        }
        throw new IllegalArgumentException(
                "Could not look up " + date + ". The calendar did not answer, and this date is not filled in yet.");
    }

    private static String httpGet(LocalDate date) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(date)))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("calendar returned " + response.statusCode());
        }
        return response.body();
    }

    @FunctionalInterface
    interface CalendarSource {
        String fetch(LocalDate date) throws IOException, InterruptedException;
    }
}
