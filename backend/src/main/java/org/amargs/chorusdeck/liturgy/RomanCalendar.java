package org.amargs.chorusdeck.liturgy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One day from the Parish Companion calendar. It names the Sunday and the reading references. */
public record RomanCalendar(
        String sundayId,
        String cycle,
        String season,
        String name,
        String primeira,
        String segunda,
        String evangelho) {

    public String key() {
        return sundayId + "/" + cycle;
    }

    public static RomanCalendar parse(String json) throws IOException {
        JsonNode root = new ObjectMapper().readTree(json);
        JsonNode day = root.get("day");
        JsonNode readings = root.get("readings");
        if (day == null || day.isNull()) {
            throw new IllegalArgumentException("The calendar response has no day.");
        }
        String cycle = text(day, "sunday_cycle");
        String season = text(day, "season");
        if (cycle == null || season == null) {
            throw new IllegalArgumentException("The calendar response has no day.");
        }
        boolean noReadings = readings == null || readings.isNull();
        return new RomanCalendar(
                text(day, "sunday_id"),
                cycle.toUpperCase(Locale.ROOT),
                season,
                text(day, "name"),
                noReadings ? null : reference(text(readings, "first_reading")),
                noReadings ? null : reference(text(readings, "second_reading")),
                noReadings ? null : reference(text(readings, "gospel")));
    }

    private static final Pattern BOOK = Pattern.compile("^(?:[1-3]\\s+)?\\p{L}+");

    private static final Map<String, String> BOOKS = Map.ofEntries(
            Map.entry("gen", "Gn"), Map.entry("ex", "Ex"), Map.entry("lev", "Lv"), Map.entry("num", "Nm"),
            Map.entry("deut", "Dt"), Map.entry("josh", "Js"), Map.entry("judg", "Jz"), Map.entry("ruth", "Rt"),
            Map.entry("1 sam", "1Sm"), Map.entry("2 sam", "2Sm"), Map.entry("1 kgs", "1Rs"), Map.entry("2 kgs", "2Rs"),
            Map.entry("1 chr", "1Cr"), Map.entry("2 chr", "2Cr"), Map.entry("ezra", "Esd"), Map.entry("neh", "Ne"),
            Map.entry("tob", "Tb"), Map.entry("jdt", "Jdt"), Map.entry("est", "Est"),
            Map.entry("1 macc", "1Mc"), Map.entry("2 macc", "2Mc"),
            Map.entry("job", "Job"), Map.entry("ps", "Sl"), Map.entry("prov", "Pr"), Map.entry("eccl", "Ecl"),
            Map.entry("song", "Ct"), Map.entry("wis", "Sb"), Map.entry("sir", "Sir"), Map.entry("is", "Is"),
            Map.entry("jer", "Jr"), Map.entry("lam", "Lm"), Map.entry("bar", "Br"), Map.entry("ezek", "Ez"),
            Map.entry("dan", "Dn"), Map.entry("hos", "Os"), Map.entry("joel", "Jl"), Map.entry("am", "Am"),
            Map.entry("obad", "Ab"), Map.entry("jonah", "Jn"), Map.entry("mic", "Mq"), Map.entry("nah", "Na"),
            Map.entry("hab", "Hab"), Map.entry("zeph", "Sf"), Map.entry("hag", "Ag"), Map.entry("zech", "Zc"),
            Map.entry("mal", "Ml"),
            Map.entry("mt", "Mt"), Map.entry("mk", "Mc"), Map.entry("lk", "Lc"), Map.entry("jn", "Jo"),
            Map.entry("acts", "At"), Map.entry("rom", "Rm"),
            Map.entry("1 cor", "1Cor"), Map.entry("2 cor", "2Cor"), Map.entry("gal", "Gl"), Map.entry("eph", "Ef"),
            Map.entry("phil", "Flp"), Map.entry("col", "Cl"),
            Map.entry("thes", "Ts"), Map.entry("1 thes", "1Ts"), Map.entry("2 thes", "2Ts"),
            Map.entry("1 tim", "1Tm"), Map.entry("2 tim", "2Tm"), Map.entry("tit", "Tt"), Map.entry("phlm", "Fm"),
            Map.entry("heb", "Hb"), Map.entry("jas", "Tg"),
            Map.entry("1 pet", "1Pe"), Map.entry("2 pet", "2Pe"),
            Map.entry("1 jn", "1Jo"), Map.entry("2 jn", "2Jo"), Map.entry("3 jn", "3Jo"),
            Map.entry("jude", "Jd"), Map.entry("rev", "Ap"));

    static String reference(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace(":", ", ").replaceAll("\\s+", " ").trim();
        Matcher book = BOOK.matcher(text);
        if (!book.find()) {
            return text;
        }
        String mapped = BOOKS.get(book.group().toLowerCase(Locale.ROOT));
        if (mapped == null) {
            return text;
        }
        return mapped + text.substring(book.end());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
