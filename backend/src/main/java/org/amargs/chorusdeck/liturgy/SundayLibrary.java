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

/** Portuguese sentences for each celebration, kept beside the index and filled in over time. */
public final class SundayLibrary {

    /** Sibling of the song index. Sundays and solemnities share this file. */
    public static final String FILE_NAME = "liturgy.json";

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
        } else {
            fillBlankReferences(sunday, calendar);
            restoreTitle(calendar.sundayId(), sunday);
            restoreSeason(calendar.sundayId(), sunday);
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
        String sundayId = key.asText().substring(0, key.asText().lastIndexOf('/'));
        if (restoreTitle(sundayId, sunday) || restoreSeason(sundayId, sunday)) {
            save();
        }
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

    /** One liturgical Sunday or solemnity. {@code ano} is absent when the celebration is the same every year. */
    public record SundayEntry(
            String key,
            String name,
            String season,
            String ano,
            boolean solemnity,
            boolean lenten,
            boolean incomplete,
            int place,
            String primeiraRef,
            String primeira,
            String segundaRef,
            String segunda,
            String salmo,
            String evangelhoRef,
            String evangelho,
            String louvor) {
    }

    /** The lectionary, with whatever sentences are already stored. Does not call the calendar. */
    public List<SundayEntry> list() throws IOException {
        if (Files.isRegularFile(file)) {
            load();
        }
        List<SundayEntry> listed = new ArrayList<>();
        for (Lectionary.Entry entry : Lectionary.all()) {
            ObjectNode sunday = root == null ? null : sundayNode(entry.key());
            listed.add(entryOf(entry, sunday));
        }
        return listed;
    }

    /**
     * Replaces the sentences of one liturgical Sunday. A null sentence is left as it is.
     * References are not written. {@code louvor} is written only in Lent.
     */
    public SundayEntry replaceSentences(
            String key, String primeira, String segunda, String salmo, String evangelho, String louvor)
            throws IOException {
        Lectionary.Entry entry = Lectionary.find(key);
        if (entry == null) {
            throw new IllegalArgumentException("Este domingo não faz parte do ano litúrgico.");
        }
        load();
        ObjectNode sunday = ensure(entry);
        if (segunda != null && !segunda.isBlank() && !(sunday.get("segunda") instanceof ObjectNode)) {
            sunday.set("segunda", reading(""));
        }
        boolean changed = true;
        changed |= setCitacao(sunday, "primeira", primeira);
        changed |= setCitacao(sunday, "segunda", segunda);
        changed |= setText(sunday, "salmo", salmo);
        changed |= setCitacao(sunday, "evangelho", evangelho);
        if (entry.lenten()) {
            changed |= setText(sunday, "louvor", louvor);
        }
        if (changed) {
            save();
        }
        return entryOf(entry, sunday);
    }

    /** Fills blank sentences and references from a solemnity stored on its own key. */
    public LiturgyDay overlay(LiturgyDay day, String key) throws IOException {
        if (day == null || key == null || !Files.isRegularFile(file)) {
            return day;
        }
        load();
        ObjectNode sunday = sundayNode(key);
        if (sunday == null) {
            return day;
        }
        return new LiturgyDay(
                day.date(),
                day.domingo(),
                day.seasonKey(),
                day.coverLayout() != null ? day.coverLayout() : text(sunday, "cover"),
                day.ano(),
                mergeReading(day.primeira(), sunday.get("primeira")),
                mergeReading(day.segunda(), sunday.get("segunda")),
                mergeText(day.refrao(), text(sunday, "salmo")),
                mergeReading(day.evangelho(), sunday.get("evangelho")),
                mergeText(day.louvor(), text(sunday, "louvor")));
    }

    /**
     * Writes sentences and references that are still blank on a solemnity's own key.
     * A sentence or reference already stored is left as it is. {@code ano} is the year of this date.
     */
    public LiturgyDay rememberKey(
            String key,
            LocalDate date,
            String ano,
            String primeira,
            String segunda,
            String salmo,
            String evangelho,
            String louvor,
            String primeiraRef,
            String segundaRef,
            String evangelhoRef) throws IOException {
        Lectionary.Entry entry = Lectionary.find(key);
        if (entry == null || date == null) {
            return null;
        }
        load();
        ObjectNode sunday = ensure(entry);
        if (!blank(segunda) || !blank(segundaRef)) {
            if (!(sunday.get("segunda") instanceof ObjectNode)) {
                sunday.set("segunda", reading(""));
            }
        }
        boolean changed = false;
        changed |= fillReading(sunday, "primeira", primeira);
        changed |= fillReading(sunday, "segunda", segunda);
        changed |= fillText(sunday, "salmo", salmo);
        changed |= fillReading(sunday, "evangelho", evangelho);
        if (entry.lenten()) {
            changed |= fillText(sunday, "louvor", louvor);
        }
        changed |= fillReference(sunday, "primeira", primeiraRef);
        changed |= fillReference(sunday, "segunda", segundaRef);
        changed |= fillReference(sunday, "evangelho", evangelhoRef);
        if (changed) {
            save();
        }
        String year = entry.ano() == null || entry.ano().isBlank() ? ano : entry.ano();
        return day(date, year == null ? "" : year, sunday);
    }

    /** Writes sentences that are still blank. A sentence already stored is left as it is. */
    public LiturgyDay rememberSentences(
            LocalDate date, String primeira, String segunda, String salmo, String evangelho, String louvor)
            throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        load();
        JsonNode key = dates().get(date.toString());
        if (key == null || !key.isTextual()) {
            return null;
        }
        ObjectNode sunday = (ObjectNode) sundays().get(key.asText());
        if (sunday == null) {
            return null;
        }
        boolean changed = false;
        changed |= fillReading(sunday, "primeira", primeira);
        changed |= fillReading(sunday, "segunda", segunda);
        changed |= fillText(sunday, "salmo", salmo);
        changed |= fillReading(sunday, "evangelho", evangelho);
        changed |= fillText(sunday, "louvor", louvor);
        if (changed) {
            save();
        }
        String cycle = key.asText().substring(key.asText().lastIndexOf('/') + 1);
        return day(date, cycle, sunday);
    }

    private static boolean fillReading(ObjectNode sunday, String name, String sentence) {
        if (sentence == null || sentence.isBlank() || !(sunday.get(name) instanceof ObjectNode reading)) {
            return false;
        }
        if (text(reading, "citacao") != null) {
            return false;
        }
        reading.put("citacao", sentence.trim());
        return true;
    }

    private ObjectNode sundayNode(String key) {
        if (root == null || !(sundays().get(key) instanceof ObjectNode sunday)) {
            return null;
        }
        return sunday;
    }

    private SundayEntry entryOf(Lectionary.Entry entry, ObjectNode sunday) {
        String primeiraRef = "";
        String primeira = "";
        String segundaRef = "";
        String segunda = "";
        String salmo = "";
        String evangelhoRef = "";
        String evangelho = "";
        String louvor = "";
        boolean incomplete = true;
        if (sunday != null) {
            primeiraRef = orEmpty(text(sunday.get("primeira"), "referencia"));
            primeira = orEmpty(text(sunday.get("primeira"), "citacao"));
            if (sunday.get("segunda") instanceof ObjectNode) {
                segundaRef = orEmpty(text(sunday.get("segunda"), "referencia"));
                segunda = orEmpty(text(sunday.get("segunda"), "citacao"));
            }
            salmo = orEmpty(text(sunday, "salmo"));
            evangelhoRef = orEmpty(text(sunday.get("evangelho"), "referencia"));
            evangelho = orEmpty(text(sunday.get("evangelho"), "citacao"));
            louvor = orEmpty(text(sunday, "louvor"));
            LiturgyDay day = day(LocalDate.EPOCH, entry.ano() == null ? "" : entry.ano(), sunday);
            incomplete = !missingSentences(day).isEmpty()
                    || (entry.lenten() && louvor.isBlank());
        }
        return new SundayEntry(
                entry.key(),
                entry.name(),
                entry.season(),
                entry.ano(),
                entry.solemnity(),
                entry.lenten(),
                incomplete,
                entry.place(),
                primeiraRef,
                primeira,
                segundaRef,
                segunda,
                salmo,
                evangelhoRef,
                evangelho,
                louvor);
    }

    private ObjectNode ensure(Lectionary.Entry entry) {
        ObjectNode sunday = sundayNode(entry.key());
        if (sunday == null) {
            sunday = mapper.createObjectNode();
            sunday.put("domingo", entry.domingo());
            sunday.put("season", entry.season());
            if (entry.cover() != null) {
                sunday.put("cover", entry.cover());
            }
            sundays().set(entry.key(), sunday);
        }
        if (!(sunday.get("primeira") instanceof ObjectNode)) {
            sunday.set("primeira", reading(""));
        }
        if (text(sunday, "salmo") == null && !sunday.has("salmo")) {
            sunday.put("salmo", "");
        }
        if (!(sunday.get("evangelho") instanceof ObjectNode)) {
            sunday.set("evangelho", reading(""));
        }
        return sunday;
    }

    private static void fillBlankReferences(ObjectNode sunday, RomanCalendar calendar) {
        fillRef(sunday, "primeira", calendar.primeira());
        if (calendar.segunda() != null) {
            fillRef(sunday, "segunda", calendar.segunda());
        }
        fillRef(sunday, "evangelho", calendar.evangelho());
    }

    private static boolean fillReference(ObjectNode sunday, String name, String referencia) {
        if (referencia == null || referencia.isBlank() || !(sunday.get(name) instanceof ObjectNode reading)) {
            return false;
        }
        if (text(reading, "referencia") != null) {
            return false;
        }
        reading.put("referencia", referencia.trim());
        return true;
    }

    private static void fillRef(ObjectNode sunday, String name, String referencia) {
        if (referencia == null || referencia.isBlank() || !(sunday.get(name) instanceof ObjectNode reading)) {
            return;
        }
        if (text(reading, "referencia") == null) {
            reading.put("referencia", referencia);
        }
    }

    private static LiturgyDay.Reading mergeReading(LiturgyDay.Reading current, JsonNode stored) {
        if (!(stored instanceof ObjectNode node)) {
            return current;
        }
        String ref = text(node, "referencia");
        String citacao = text(node, "citacao");
        if (current == null) {
            if (ref == null && citacao == null) {
                return null;
            }
            return new LiturgyDay.Reading(ref == null ? "" : ref, citacao == null ? "" : citacao);
        }
        String nextRef = blank(current.referencia()) && ref != null ? ref : current.referencia();
        String nextCitacao = blank(current.citacao()) && citacao != null ? citacao : current.citacao();
        if ((nextRef == null ? "" : nextRef).equals(current.referencia() == null ? "" : current.referencia())
                && (nextCitacao == null ? "" : nextCitacao).equals(current.citacao() == null ? "" : current.citacao())) {
            return current;
        }
        return new LiturgyDay.Reading(nextRef == null ? "" : nextRef, nextCitacao == null ? "" : nextCitacao);
    }

    private static String mergeText(String current, String stored) {
        if (blank(current) && stored != null) {
            return stored;
        }
        return current;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean setCitacao(ObjectNode sunday, String name, String sentence) {
        if (!(sunday.get(name) instanceof ObjectNode reading)) {
            return false;
        }
        return setText(reading, "citacao", sentence);
    }

    private static boolean setText(ObjectNode node, String field, String sentence) {
        if (sentence == null) {
            return false;
        }
        String next = sentence.trim();
        String stored = text(node, field);
        if (next.equals(stored == null ? "" : stored)) {
            return false;
        }
        node.put(field, next);
        return true;
    }

    private static boolean lent(String seasonKey) {
        return "quaresma".equals(seasonKey) || "ramos".equals(seasonKey);
    }

    private static boolean fillText(ObjectNode sunday, String name, String sentence) {
        if (sentence == null || sentence.isBlank() || text(sunday, name) != null) {
            return false;
        }
        sunday.put(name, sentence.trim());
        return true;
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
            case "easter-7" -> "Solenidade da Ascensão do Senhor";
            case "trinity" -> "Solenidade da Santíssima Trindade";
            case "ordinary-34" -> "Solenidade de Nosso Senhor Jesus Cristo, Rei do Universo";
            case "holy-family" -> "Festa da Sagrada Família de Jesus, Maria e José";
            case "epiphany" -> "Solenidade da Epifania do Senhor";
            case "baptism-of-the-lord" -> "Solenidade do Batismo do Senhor";
            default -> numbered(sundayId);
        };
    }

    /** A title that was stored as the calendar id is replaced with the Portuguese name. */
    private static boolean restoreTitle(String sundayId, ObjectNode sunday) {
        String title = domingo(sundayId);
        String stored = text(sunday, "domingo");
        if (title.equals(stored) || title.equals(sundayId)) {
            return false;
        }
        String automatic = numbered(sundayId);
        if (sundayId.equals(stored)
                || (automatic.equals(stored) && !automatic.equals(title))
                || ("holy-family".equals(sundayId) && "Domingo da Sagrada Família".equals(stored))) {
            sunday.put("domingo", title);
            return true;
        }
        return false;
    }

    /** Gold accent used for a solemnity whose season cover stays as it is. */
    public static boolean solemnityGold(String domingo) {
        return switch (domingo == null ? "" : domingo) {
            case "Solenidade da Ascensão do Senhor",
                    "Solenidade da Santíssima Trindade",
                    "Solenidade do Santíssimo Corpo e Sangue de Cristo",
                    "Solenidade de Nosso Senhor Jesus Cristo, Rei do Universo" -> true;
            default -> false;
        };
    }

    static final String CORPUS_CHRISTI = "Solenidade do Santíssimo Corpo e Sangue de Cristo";

    /** Holy Family, Epiphany, and the Baptism of the Lord stay on the Christmas cover. */
    private static boolean restoreSeason(String sundayId, ObjectNode sunday) {
        String season = switch (sundayId) {
            case "holy-family", "epiphany", "baptism-of-the-lord" -> "natal";
            default -> null;
        };
        if (season == null || season.equals(text(sunday, "season"))) {
            return false;
        }
        sunday.put("season", season);
        return true;
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
            case "holy-family", "epiphany", "baptism-of-the-lord" -> new SeasonChoice("natal", null);
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
