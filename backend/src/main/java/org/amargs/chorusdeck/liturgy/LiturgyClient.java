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
        RomanCalendar calendar;
        try {
            calendar = RomanCalendar.parse(source.fetch(date));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return offline(date, library);
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
            return day;
        }
        return unfilled(date, calendar);
    }

    public static String url(LocalDate date) {
        return "https://api.parishcompanion.app/calendar/AU?date=" + date;
    }

    static String sundaySolemnity(LocalDate date, LiturgyDay day, RomanCalendar calendar, FixedSolemnity solemnity) {
        String kept = date + " is also " + solemnity.title() + ". The deck keeps " + day.domingo() + " (" + day.ano() + "). ";
        if ("advent".equals(calendar.season())) {
            return kept + "This Sunday of Advent outranks the solemnity, so those readings stay.";
        }
        return kept + "The scriptures are that Sunday's, and this solemnity outranks it.";
    }

    private LiturgyDay unfilled(LocalDate date, RomanCalendar calendar) {
        if (calendar.primeira() == null && calendar.evangelho() == null) {
            LOG.warn(date + " has no scriptures. The liturgy was not filled in.");
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
                null,
                null,
                null,
                null,
                null);
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
