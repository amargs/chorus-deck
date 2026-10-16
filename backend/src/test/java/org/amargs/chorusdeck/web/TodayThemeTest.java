package org.amargs.chorusdeck.web;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        AtomicInteger fetches = new AtomicInteger();
        LocalDate today = LocalDate.of(2026, 10, 16);
        TodayTheme.Fetcher fetcher = date -> {
            fetches.incrementAndGet();
            return day("ordinary", "white", null);
        };
        TodayTheme theme = new TodayTheme(fetcher);
        TodayTheme.Answer first = theme.resolve(today, null);
        assertEquals("comum", first.snapshot().season());
        assertTrue(first.store());
        assertEquals("comum", theme.resolve(today, null).snapshot().season());
        assertEquals(1, fetches.get());

        TodayTheme restarted = new TodayTheme(date -> {
            fetches.incrementAndGet();
            return day("advent", "purple", "advent-1");
        });
        TodayTheme.Answer again = restarted.resolve(today, TodayTheme.cookieValue(first.snapshot()));
        assertEquals("comum", again.snapshot().season());
        assertTrue(again.store());
        assertEquals(1, fetches.get());
    }

    @Test
    void networkFailureKeepsAStaleCookie() {
        TodayTheme theme = new TodayTheme(date -> {
            throw new java.io.IOException("offline");
        });
        TodayTheme.Answer answer = theme.resolve(LocalDate.of(2026, 10, 16), "2026-10-15.advento");
        assertEquals("advento", answer.snapshot().season());
        assertFalse(answer.store());
    }

    @Test
    void aBadCookieIsIgnored() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        TodayTheme theme = new TodayTheme(date -> {
            fetches.incrementAndGet();
            return day("ordinary", "white", null);
        });
        assertNull(TodayTheme.parse(null));
        assertNull(TodayTheme.parse("2026-10-16"));
        assertNull(TodayTheme.parse("2026-10-16.gold"));
        assertNull(TodayTheme.parse("yesterday.comum"));
        assertEquals("comum", theme.resolve(LocalDate.of(2026, 10, 16), "nope").snapshot().season());
        assertEquals(1, fetches.get());
    }

    @Test
    void cookieLastsUntilLocalMidnight() {
        ZoneId lisbon = ZoneId.of("Europe/Lisbon");
        ZonedDateTime evening = ZonedDateTime.of(2026, 10, 16, 21, 0, 0, 0, lisbon);
        assertEquals(3 * 60 * 60, TodayTheme.maxAgeSeconds(evening));
        ZonedDateTime lastSecond = ZonedDateTime.of(2026, 10, 16, 23, 59, 59, 0, lisbon);
        assertEquals(1, TodayTheme.maxAgeSeconds(lastSecond));
        assertEquals(
                "chorus-today=2026-10-16.advento; Path=/; Max-Age=10800; HttpOnly; SameSite=Lax",
                TodayTheme.setCookie(new TodayTheme.Snapshot("2026-10-16", "advento"), 10800));
    }

    private static String day(String season, String colour, String sundayId) {
        String id = sundayId == null ? "null" : "\"" + sundayId + "\"";
        return """
                {"day":{"season":"%s","colour":"%s","sunday_id":%s}}
                """.formatted(season, colour, id);
    }
}
