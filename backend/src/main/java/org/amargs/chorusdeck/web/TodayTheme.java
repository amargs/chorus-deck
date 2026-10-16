package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.amargs.chorusdeck.liturgy.LiturgyClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * One Parish Companion lookup per calendar day, kept in memory and in a cookie
 * that lasts until local midnight, so a restart the same day does not fetch again.
 * Page chrome only.
 */
public final class TodayTheme {

    public static final String COOKIE = "chorus-today";

    private static final Logger LOG = LogManager.getLogger(TodayTheme.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final Set<String> SEASONS = Set.of(
            "comum", "advento", "quaresma", "rosa", "ramos", "pentecostes", "natal", "pascoa");

    private final Fetcher fetcher;
    private final Object lock = new Object();
    private Snapshot memory;

    public TodayTheme(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    public Answer resolve(LocalDate today, String cookie) {
        String date = today.toString();
        synchronized (lock) {
            Snapshot remembered = memory != null ? memory : parse(cookie);
            if (remembered != null && date.equals(remembered.date())) {
                memory = remembered;
                return new Answer(remembered, true);
            }
            try {
                Snapshot fresh = new Snapshot(date, seasonKey(fetcher.fetch(today)));
                memory = fresh;
                return new Answer(fresh, true);
            } catch (IOException | InterruptedException | RuntimeException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                LOG.warn("Calendar lookup for " + date + " failed.", e);
                if (remembered != null) {
                    return new Answer(remembered, false);
                }
                return new Answer(new Snapshot(date, "comum"), false);
            }
        }
    }

    /**
     * Theme key from a Parish Companion body. Uses {@code day.colour} for rose
     * (Gaudete / Laetare), {@code day.sunday_id} for Palm Sunday and Pentecost,
     * and {@code day.season} otherwise.
     */
    public static String seasonKey(String json) throws IOException {
        JsonNode root = JSON.readTree(json);
        JsonNode day = root.get("day");
        if (day == null || day.isNull()) {
            throw new IOException("The calendar response has no day.");
        }
        String colour = text(day, "colour");
        String sundayId = text(day, "sunday_id");
        if ("rose".equals(colour) || "advent-3".equals(sundayId) || "lent-4".equals(sundayId)) {
            return "rosa";
        }
        if ("palm-sunday".equals(sundayId)) {
            return "ramos";
        }
        if ("pentecost".equals(sundayId)) {
            return "pentecostes";
        }
        String season = text(day, "season");
        if (season == null) {
            throw new IOException("The calendar response has no season.");
        }
        return switch (season) {
            case "advent" -> "advento";
            case "lent", "holy_week" -> "quaresma";
            case "christmas" -> "natal";
            case "easter" -> "pascoa";
            case "ordinary" -> "comum";
            default -> "comum";
        };
    }

    /** A {@code date.season} cookie, or null when the value is missing or not one of the theme keys. */
    public static Snapshot parse(String cookie) {
        if (cookie == null) {
            return null;
        }
        String[] parts = cookie.split("\\.", 2);
        if (parts.length != 2 || !SEASONS.contains(parts[1])) {
            return null;
        }
        try {
            LocalDate.parse(parts[0]);
        } catch (DateTimeParseException e) {
            return null;
        }
        return new Snapshot(parts[0], parts[1]);
    }

    public static String cookieValue(Snapshot snapshot) {
        return snapshot.date() + "." + snapshot.season();
    }

    /** Seconds from {@code now} until the next local midnight, and at least one second. */
    public static int maxAgeSeconds(ZonedDateTime now) {
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
        long seconds = Duration.between(now, midnight).getSeconds();
        if (seconds < 1) {
            return 1;
        }
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    public static String setCookie(Snapshot snapshot, int maxAgeSeconds) {
        return COOKIE + "=" + cookieValue(snapshot)
                + "; Path=/; Max-Age=" + maxAgeSeconds + "; HttpOnly; SameSite=Lax";
    }

    public static String setCookie(Snapshot snapshot) {
        return setCookie(snapshot, maxAgeSeconds(ZonedDateTime.now(ZoneId.systemDefault())));
    }

    static String httpGet(LocalDate date) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(LiturgyClient.url(date)))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(TIMEOUT)
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("calendar returned " + response.statusCode());
        }
        return response.body();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    @FunctionalInterface
    public interface Fetcher {
        String fetch(LocalDate date) throws IOException, InterruptedException;
    }

    public record Snapshot(String date, String season) {
    }

    /** {@code store} is true when the snapshot is a real calendar answer and should be written to the cookie. */
    public record Answer(Snapshot snapshot, boolean store) {
    }
}
