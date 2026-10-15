package org.amargs.chorusdeck.liturgy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Portuguese sentences for each Sunday, kept beside the index and filled in over time. */
public final class SundayLibrary {

    private static final String[] ROMAN = {
            "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
            "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
            "XXI", "XXII", "XXIII", "XXIV", "XXV", "XXVI", "XXVII", "XXVIII", "XXIX", "XXX",
            "XXXI", "XXXII", "XXXIII", "XXXIV"
    };

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;
    private ObjectNode root;

    public SundayLibrary(Path file) {
        this.file = file;
    }

    public LiturgyDay resolve(LocalDate date, RomanCalendar calendar) throws IOException {
        if (calendar.sundayId() == null) {
            throw new IllegalArgumentException("The calendar response has no Sunday.");
        }
        load();
        String key = calendar.key();
        dates().put(date.toString(), key);
        ObjectNode sunday = (ObjectNode) sundays().get(key);
        if (sunday == null) {
            sunday = stub(calendar);
            sundays().set(key, sunday);
        }
        ensureLouvor(key, sunday);
        save();
        requireReferences(key, sunday);
        return day(date, calendar.cycle(), sunday);
    }

    public LiturgyDay stored(LocalDate date) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        load();
        JsonNode key = dates().get(date.toString());
        if (key == null || !key.isTextual()) {
            return null;
        }
        ObjectNode sunday = (ObjectNode) sundays().get(key.asText());
        if (sunday == null || !missingReferences(sunday).isEmpty()) {
            return null;
        }
        ensureLouvor(key.asText(), sunday);
        String cycle = key.asText().substring(key.asText().lastIndexOf('/') + 1);
        return day(date, cycle, sunday);
    }

    private ObjectNode stub(RomanCalendar calendar) {
        ObjectNode sunday = mapper.createObjectNode();
        sunday.put("domingo", domingo(calendar.sundayId()));
        sunday.put("season", season(calendar).key());
        String cover = season(calendar).cover();
        if (cover != null) {
            sunday.put("cover", cover);
        }
        sunday.set("primeira", reading(calendar.primeira()));
        if (calendar.segunda() != null) {
            sunday.set("segunda", reading(calendar.segunda()));
        }
        sunday.put("salmo", "");
        sunday.set("evangelho", reading(calendar.evangelho()));
        return sunday;
    }

    private ObjectNode reading(String referencia) {
        ObjectNode reading = mapper.createObjectNode();
        reading.put("referencia", referencia == null ? "" : referencia);
        reading.put("citacao", "");
        return reading;
    }

    private LiturgyDay day(LocalDate date, String cycle, ObjectNode sunday) {
        ObjectNode segunda = (ObjectNode) sunday.get("segunda");
        return new LiturgyDay(
                date,
                text(sunday, "domingo"),
                text(sunday, "season"),
                text(sunday, "cover"),
                cycle,
                readingOf(sunday.get("primeira")),
                segunda == null ? null : readingOf(segunda),
                text(sunday, "salmo"),
                readingOf(sunday.get("evangelho")),
                text(sunday, "louvor"));
    }

    private void ensureLouvor(String key, ObjectNode sunday) {
        if (text(sunday, "louvor") != null) {
            return;
        }
        String verse = LentenAcclamation.verse(file.resolveSibling("louvor.json"), key);
        if (verse != null) {
            sunday.put("louvor", verse);
        }
    }

    private static LiturgyDay.Reading readingOf(JsonNode node) {
        return new LiturgyDay.Reading(text(node, "referencia"), text(node, "citacao"));
    }

    private void requireReferences(String key, ObjectNode sunday) {
        List<String> missing = missingReferences(sunday);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    key + " needs references in " + file.toAbsolutePath() + ": " + String.join(", ", missing) + ".");
        }
    }

    private static List<String> missingReferences(ObjectNode sunday) {
        return blanks(sunday).stream().filter(item -> item.endsWith(".referencia")).toList();
    }

    public static List<String> missingSentences(LiturgyDay day) {
        List<String> blanks = new ArrayList<>();
        if (day.primeira() == null || blank(day.primeira().citacao())) {
            blanks.add("primeira.citacao");
        }
        if (day.segunda() != null && blank(day.segunda().citacao())) {
            blanks.add("segunda.citacao");
        }
        if (blank(day.refrao())) {
            blanks.add("salmo");
        }
        if (day.evangelho() == null || blank(day.evangelho().citacao())) {
            blanks.add("evangelho.citacao");
        }
        return blanks;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static List<String> blanks(ObjectNode sunday) {
        List<String> blanks = new ArrayList<>();
        blank(blanks, "primeira", sunday.get("primeira"));
        if (sunday.get("segunda") != null) {
            blank(blanks, "segunda", sunday.get("segunda"));
        }
        if (text(sunday, "salmo") == null) {
            blanks.add("salmo");
        }
        blank(blanks, "evangelho", sunday.get("evangelho"));
        return blanks;
    }

    private static void blank(List<String> blanks, String name, JsonNode reading) {
        if (reading == null || text(reading, "referencia") == null) {
            blanks.add(name + ".referencia");
        }
        if (reading == null || text(reading, "citacao") == null) {
            blanks.add(name + ".citacao");
        }
    }

    static String domingo(String sundayId) {
        return switch (sundayId) {
            case "palm-sunday" -> "Domingo de Ramos";
            case "pentecost" -> "Domingo de Pentecostes";
            case "easter-sunday" -> "Domingo de Páscoa";
            default -> numbered(sundayId);
        };
    }

    private static String numbered(String sundayId) {
        int dash = sundayId.lastIndexOf('-');
        if (dash < 0) {
            return sundayId;
        }
        try {
            int number = Integer.parseInt(sundayId.substring(dash + 1));
            if (number >= 1 && number < ROMAN.length) {
                return "Domingo " + ROMAN[number];
            }
        } catch (NumberFormatException ignored) {
            return sundayId;
        }
        return sundayId;
    }

    private record SeasonChoice(String key, String cover) {
    }

    static String seasonKey(RomanCalendar calendar) {
        return season(calendar).key();
    }

    /** Roman numeral of a numbered Sunday (`ordinary-29` → `XXIX`). Other ids have none. */
    static String week(String sundayId) {
        if (sundayId == null) {
            return null;
        }
        int dash = sundayId.lastIndexOf('-');
        if (dash < 1) {
            return null;
        }
        String kind = sundayId.substring(0, dash);
        if (!kind.equals("ordinary") && !kind.equals("advent") && !kind.equals("lent") && !kind.equals("easter")) {
            return null;
        }
        try {
            int number = Integer.parseInt(sundayId.substring(dash + 1));
            if (number >= 1 && number < ROMAN.length) {
                return ROMAN[number];
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private static SeasonChoice season(RomanCalendar calendar) {
        String sundayId = calendar.sundayId();
        if (sundayId == null) {
            return fromSeason(calendar.season());
        }
        return switch (sundayId) {
            case "advent-3" -> new SeasonChoice("advento", "Capa - Domingo III Advento");
            case "lent-4" -> new SeasonChoice("quaresma", "Capa - Domingo IV Quaresma");
            case "palm-sunday" -> new SeasonChoice("ramos", null);
            case "pentecost" -> new SeasonChoice("pentecostes", null);
            default -> fromSeason(calendar.season());
        };
    }

    private static SeasonChoice fromSeason(String season) {
        return switch (season) {
            case "advent" -> new SeasonChoice("advento", null);
            case "lent", "holy_week" -> new SeasonChoice("quaresma", null);
            case "easter" -> new SeasonChoice("pascoa", null);
            case "christmas" -> new SeasonChoice("natal", null);
            case "ordinary" -> new SeasonChoice("comum", null);
            default -> throw new IllegalArgumentException("Unknown season from the calendar: " + season);
        };
    }

    private void load() throws IOException {
        if (root != null) {
            return;
        }
        if (Files.isRegularFile(file)) {
            JsonNode node = mapper.readTree(file.toFile());
            if (!(node instanceof ObjectNode object)) {
                throw new IllegalArgumentException(file + " must be a JSON object.");
            }
            root = object;
        } else {
            root = mapper.createObjectNode();
        }
        if (!(root.get("dates") instanceof ObjectNode)) {
            root.set("dates", mapper.createObjectNode());
        }
        if (!(root.get("sundays") instanceof ObjectNode)) {
            root.set("sundays", mapper.createObjectNode());
        }
        sundays().properties().forEach(entry -> {
            if (entry.getValue() instanceof ObjectNode sunday) {
                renameRefrao(sunday);
            }
        });
    }

    private static void renameRefrao(ObjectNode sunday) {
        if (!sunday.has("salmo") && sunday.has("refrao")) {
            sunday.set("salmo", sunday.get("refrao"));
            sunday.remove("refrao");
        }
    }

    private ObjectNode dates() {
        return (ObjectNode) root.get("dates");
    }

    private ObjectNode sundays() {
        return (ObjectNode) root.get("sundays");
    }

    private void save() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
