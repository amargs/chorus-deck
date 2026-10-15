package org.amargs.chorusdeck.liturgy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

public final class LiturgyClient {

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
        String json;
        try {
            json = source.fetch(date);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return offline(date, library);
        }
        RomanCalendar calendar;
        try {
            calendar = RomanCalendar.parse(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("The calendar response for " + date + " could not be read.", e);
        }
        try {
            return library.resolve(date, calendar);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not write " + libraryFile.toAbsolutePath() + ".", e);
        }
    }

    public static String url(LocalDate date) {
        return "https://api.parishcompanion.app/calendar/AU?date=" + date;
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
