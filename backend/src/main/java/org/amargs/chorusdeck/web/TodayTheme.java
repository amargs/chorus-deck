package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.amargs.chorusdeck.liturgy.LiturgyClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

/**
 * One Parish Companion lookup per calendar day, kept in memory and in a small file
 * so a restart the same day does not fetch again. Page chrome only.
 */
public final class TodayTheme {

    private static final Logger LOG = LogManager.getLogger(TodayTheme.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final Fetcher fetcher;
    private final Path cacheFile;
    private final Object lock = new Object();
    private Snapshot memory;

    public TodayTheme(Fetcher fetcher, Path cacheFile) {
        this.fetcher = fetcher;
        this.cacheFile = cacheFile;
    }

    public Snapshot resolve(LocalDate today) {
        String date = today.toString();
        synchronized (lock) {
            Snapshot cached = current();
            if (cached != null && date.equals(cached.date())) {
                return cached;
            }
            try {
                Snapshot fresh = new Snapshot(date, seasonKey(fetcher.fetch(today)));
                memory = fresh;
                write(fresh);
                return fresh;
            } catch (IOException | InterruptedException | RuntimeException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                LOG.warn("Calendar lookup for " + date + " failed.", e);
                if (cached != null) {
                    return cached;
                }
                return new Snapshot(date, "comum");
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

    private Snapshot current() {
        if (memory != null) {
            return memory;
        }
        memory = read();
        return memory;
    }

    private Snapshot read() {
        if (cacheFile == null || !Files.isRegularFile(cacheFile)) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(cacheFile.toFile());
            String date = text(node, "date");
            String season = text(node, "season");
            if (date == null || season == null) {
                return null;
            }
            return new Snapshot(date, season);
        } catch (IOException e) {
            LOG.warn("Could not read " + cacheFile + ".", e);
            return null;
        }
    }

    private void write(Snapshot snapshot) {
        if (cacheFile == null) {
            return;
        }
        try {
            Path parent = cacheFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ObjectNode node = JSON.createObjectNode();
            node.put("date", snapshot.date());
            node.put("season", snapshot.season());
            JSON.writeValue(cacheFile.toFile(), node);
        } catch (IOException e) {
            LOG.warn("Could not write " + cacheFile + ".", e);
        }
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
}
