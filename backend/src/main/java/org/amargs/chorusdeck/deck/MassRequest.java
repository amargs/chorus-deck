package org.amargs.chorusdeck.deck;

import com.fasterxml.jackson.databind.JsonNode;
import org.amargs.chorusdeck.liturgy.LiturgyDay;
import org.amargs.chorusdeck.liturgy.SundayLibrary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record MassRequest(
        Cover cover,
        Interval interval,
        boolean longCreed,
        String oracao,
        String entrada,
        String kyrie,
        String gloria,
        String aleluia,
        String dons,
        String santo,
        String paiNosso,
        String cordeiro,
        String comunhao,
        String acaoDeGracas,
        String finalSong,
        String primeira,
        String segunda,
        String salmo,
        String evangelho,
        String louvor,
        LocalDate date,
        LiturgyDay liturgy) {

    private static final Logger LOG = LogManager.getLogger(MassRequest.class);

    private static final Set<String> FIELDS = Set.of(
            "mode", "season", "ano", "domingo", "frase", "versiculo", "cover", "date", "output",
            "songs", "leituras", "opcoes");

    private static final Set<String> MOMENTS = Set.of(
            "entrada", "kyrie", "gloria", "aleluia", "dons", "santo", "paiNosso", "cordeiro",
            "comunhao", "acaoDeGracas", "final");

    private static final Set<String> LEITURAS = Set.of("primeira", "segunda", "salmo", "evangelho", "louvor");

    private static final Set<String> OPCOES = Set.of(
            "credo", "oracao", "interval", "intervalImage", "intervalText");

    private static final Map<String, String> GROUPS = Map.ofEntries(
            Map.entry("entrada", "songs"), Map.entry("kyrie", "songs"), Map.entry("gloria", "songs"),
            Map.entry("aleluia", "songs"), Map.entry("dons", "songs"), Map.entry("santo", "songs"),
            Map.entry("paiNosso", "songs"), Map.entry("cordeiro", "songs"), Map.entry("comunhao", "songs"),
            Map.entry("acaoDeGracas", "songs"), Map.entry("final", "songs"),
            Map.entry("primeira", "leituras"), Map.entry("segunda", "leituras"),
            Map.entry("salmo", "leituras"), Map.entry("evangelho", "leituras"),
            Map.entry("louvor", "leituras"),
            Map.entry("credo", "opcoes"), Map.entry("oracao", "opcoes"),
            Map.entry("interval", "opcoes"), Map.entry("intervalImage", "opcoes"),
            Map.entry("intervalText", "opcoes"));

    public static MassRequest load(Path file) throws IOException {
        return from(DeckSpec.read(file), file);
    }

    static MassRequest from(JsonNode node, Path file) {
        node.fieldNames().forEachRemaining(field -> {
            if (FIELDS.contains(field)) {
                return;
            }
            String group = GROUPS.get(field);
            if (group != null) {
                throw new IllegalArgumentException("Put " + field + " under " + group + ".");
            }
            throw new IllegalArgumentException("Unknown field: " + field);
        });
        JsonNode songs = group(node, "songs", MOMENTS, "moment");
        JsonNode leituras = group(node, "leituras", LEITURAS, "leitura");
        JsonNode opcoes = group(node, "opcoes", OPCOES, "option");
        if (opcoes == null) {
            throw new IllegalArgumentException("Mass file requires opcoes.");
        }
        String credo = required(opcoes, "credo");
        boolean longCreed = switch (credo) {
            case "longo" -> true;
            case "breve" -> false;
            default -> throw new IllegalArgumentException("credo must be longo or breve.");
        };
        Path base = file.toAbsolutePath().getParent();
        String image = text(opcoes, "intervalImage");
        Path imagePath = image == null ? null : base.resolve(image).normalize();
        LocalDate date = date(node);
        return new MassRequest(
                date == null ? cover(node) : null,
                Interval.parse(text(opcoes, "interval"), imagePath, text(opcoes, "intervalText")),
                longCreed,
                text(opcoes, "oracao"),
                text(songs, "entrada"),
                text(songs, "kyrie"),
                text(songs, "gloria"),
                text(songs, "aleluia"),
                text(songs, "dons"),
                text(songs, "santo"),
                text(songs, "paiNosso"),
                text(songs, "cordeiro"),
                text(songs, "comunhao"),
                text(songs, "acaoDeGracas"),
                text(songs, "final"),
                text(leituras, "primeira"),
                text(leituras, "segunda"),
                text(leituras, "salmo"),
                text(leituras, "evangelho"),
                text(leituras, "louvor"),
                date,
                null);
    }

    public MassRequest withLiturgy(LiturgyDay day) {
        LiturgyDay filled = day.withSentences(primeira, segunda, salmo, evangelho, louvor);
        if (filled.primeira() == null && filled.evangelho() == null) {
            return copy(filled);
        }
        logSentence(day, "primeira.citacao", primeira, day.primeira());
        if (day.segunda() != null) {
            logSentence(day, "segunda.citacao", segunda, day.segunda());
        }
        logSentence(day, "salmo", salmo, day.refrao());
        logSentence(day, "evangelho.citacao", evangelho, day.evangelho());
        if (isLent(day)) {
            logSentence(day, "louvor", louvor, day.louvor());
            if (filled.louvor() == null || filled.louvor().isBlank()) {
                LOG.warn(filled.domingo() + " (" + filled.ano() + ") has no verse for louvor. "
                        + "The refrain stays and Verso is left empty.");
            }
        }
        List<String> missing = SundayLibrary.missingSentences(filled);
        if (!missing.isEmpty()) {
            LOG.warn(filled.domingo() + " (" + filled.ano() + ") has no Portuguese sentence for "
                    + String.join(", ", missing) + ". Citations use «» and the refrain is left empty.");
        }
        return copy(filled);
    }

    private MassRequest copy(LiturgyDay filled) {
        return new MassRequest(
                Cover.from(filled), interval, longCreed, oracao, entrada, kyrie, gloria, aleluia, dons, santo,
                paiNosso, cordeiro, comunhao, acaoDeGracas, finalSong, primeira, segunda, salmo, evangelho, louvor,
                filled.date(), filled);
    }

    private static Cover cover(JsonNode node) {
        String ano = required(node, "ano").toUpperCase(Locale.ROOT);
        if (!ano.equals("A") && !ano.equals("B") && !ano.equals("C")) {
            throw new IllegalArgumentException("ano must be A, B, or C.");
        }
        return new Cover(
                required(node, "season"),
                text(node, "cover"),
                required(node, "domingo"),
                required(node, "frase"),
                required(node, "versiculo"),
                ano);
    }

    private static LocalDate date(JsonNode node) {
        JsonNode value = node.get("date");
        if (value == null || value.isNull()) {
            return null;
        }
        String raw = value.asText().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date must be YYYY-MM-DD.");
        }
    }

    private static boolean isLent(LiturgyDay day) {
        return "quaresma".equals(day.seasonKey()) || "ramos".equals(day.seasonKey());
    }

    private static void logSentence(LiturgyDay day, String field, String manual, LiturgyDay.Reading stored) {
        logSentence(day, field, manual, stored == null ? null : stored.citacao());
    }

    private static void logSentence(LiturgyDay day, String field, String manual, String stored) {
        String label = day.domingo() + " (" + day.ano() + ") " + field;
        if (manual != null && !manual.isBlank()) {
            LOG.info(label + " from the mass file");
        } else if (stored != null && !stored.isBlank()) {
            LOG.info(label + " from sundays.json");
        }
    }

    private static JsonNode group(JsonNode node, String name, Set<String> allowed, String kind) {
        JsonNode group = node.get(name);
        if (group == null || group.isNull()) {
            return null;
        }
        if (!group.isObject()) {
            throw new IllegalArgumentException(name + " must be a mapping.");
        }
        group.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("Unknown " + kind + ": " + field);
            }
        });
        return group;
    }

    private static String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException("Mass file requires " + field + ".");
        }
        return value;
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
