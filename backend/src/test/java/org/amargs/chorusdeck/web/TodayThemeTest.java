package org.amargs.chorusdeck.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TodayThemeTest {

    @Test
    void mapsColourSeasonAndSundayId() throws Exception {
        assertEquals("comum", TodayTheme.seasonKey(day("ordinary", "white", null)));
        assertEquals("advento", TodayTheme.seasonKey(day("advent", "purple", "advent-1")));
        assertEquals("quaresma", TodayTheme.seasonKey(day("lent", "purple", null)));
        assertEquals("quaresma", TodayTheme.seasonKey(day("holy_week", "purple", null)));
        assertEquals("rosa", TodayTheme.seasonKey(day("advent", "rose", "advent-3")));
        assertEquals("rosa", TodayTheme.seasonKey(day("lent", "rose", "lent-4")));
        assertEquals("natal", TodayTheme.seasonKey(day("christmas", "white", null)));
        assertEquals("pascoa", TodayTheme.seasonKey(day("easter", "white", "easter-sunday")));
        assertEquals("ramos", TodayTheme.seasonKey(day("holy_week", "red", "palm-sunday")));
        assertEquals("pentecostes", TodayTheme.seasonKey(day("easter", "red", "pentecost")));
    }

    @Test
    void secondCallTheSameDayDoesNotFetch() throws Exception {
        Path cache = Files.createTempDirectory("today-theme").resolve("calendar-cache.json");
        AtomicInteger fetches = new AtomicInteger();
        LocalDate today = LocalDate.of(2026, 10, 16);
        TodayTheme.Fetcher fetcher = date -> {
            fetches.incrementAndGet();
            return day("ordinary", "white", null);
        };
        TodayTheme theme = new TodayTheme(fetcher, cache);
        assertEquals("comum", theme.resolve(today).season());
        assertEquals("comum", theme.resolve(today).season());
        assertEquals(1, fetches.get());

        TodayTheme restarted = new TodayTheme(date -> {
            fetches.incrementAndGet();
            return day("advent", "purple", "advent-1");
        }, cache);
        assertEquals("comum", restarted.resolve(today).season());
        assertEquals(1, fetches.get());
    }

    @Test
    void networkFailureKeepsAStaleCache() throws Exception {
        Path cache = Files.createTempDirectory("today-theme").resolve("calendar-cache.json");
        Files.writeString(cache, "{\"date\":\"2026-10-15\",\"season\":\"advento\"}");
        TodayTheme theme = new TodayTheme(date -> {
            throw new java.io.IOException("offline");
        }, cache);
        assertEquals("advento", theme.resolve(LocalDate.of(2026, 10, 16)).season());
    }

    private static String day(String season, String colour, String sundayId) {
        String id = sundayId == null ? "null" : "\"" + sundayId + "\"";
        return """
                {"day":{"season":"%s","colour":"%s","sunday_id":%s}}
                """.formatted(season, colour, id);
    }
}
