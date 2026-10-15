package org.amargs.chorusdeck.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
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
        String finalSong) {

    private static final Set<String> FIELDS = Set.of(
            "mode", "season", "ano", "domingo", "frase", "versiculo", "cover", "credo", "oracao",
            "interval", "intervalImage", "intervalText", "output", "songs", "bookIds", "ids",
            "entrada", "kyrie", "gloria", "aleluia", "dons", "santo", "paiNosso", "cordeiro",
            "comunhao", "acaoDeGracas", "final");

    private static final Set<String> MOMENTS = Set.of(
            "entrada", "kyrie", "gloria", "aleluia", "dons", "santo", "paiNosso", "cordeiro",
            "comunhao", "acaoDeGracas", "final");

    public static MassRequest load(Path file) throws IOException {
        return from(DeckSpec.read(file), file);
    }

    static MassRequest from(JsonNode node, Path file) {
        node.fieldNames().forEachRemaining(field -> {
            if (!FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unknown field: " + field);
            }
        });
        JsonNode songs = node.get("songs");
        if (songs != null && !songs.isObject()) {
            throw new IllegalArgumentException("In a mass deck, songs is a mapping of moment to book id.");
        }
        if (songs != null) {
            songs.fieldNames().forEachRemaining(moment -> {
                if (!MOMENTS.contains(moment)) {
                    throw new IllegalArgumentException("Unknown moment: " + moment);
                }
            });
            for (String moment : MOMENTS) {
                if (text(node, moment) != null) {
                    throw new IllegalArgumentException("Put " + moment + " under songs, not next to it.");
                }
            }
        }
        String credo = required(node, "credo");
        boolean longCreed = switch (credo) {
            case "longo" -> true;
            case "breve" -> false;
            default -> throw new IllegalArgumentException("credo must be longo or breve.");
        };
        Path base = file.toAbsolutePath().getParent();
        String image = text(node, "intervalImage");
        Path imagePath = image == null ? null : base.resolve(image).normalize();
        String ano = required(node, "ano").toUpperCase(Locale.ROOT);
        if (!ano.equals("A") && !ano.equals("B") && !ano.equals("C")) {
            throw new IllegalArgumentException("ano must be A, B, or C.");
        }
        return new MassRequest(
                new Cover(
                        required(node, "season"),
                        text(node, "cover"),
                        required(node, "domingo"),
                        required(node, "frase"),
                        required(node, "versiculo"),
                        ano),
                Interval.parse(text(node, "interval"), imagePath, text(node, "intervalText")),
                longCreed,
                text(node, "oracao"),
                moment(node, "entrada"),
                moment(node, "kyrie"),
                moment(node, "gloria"),
                moment(node, "aleluia"),
                moment(node, "dons"),
                moment(node, "santo"),
                moment(node, "paiNosso"),
                moment(node, "cordeiro"),
                moment(node, "comunhao"),
                moment(node, "acaoDeGracas"),
                moment(node, "final"));
    }

    private static String moment(JsonNode node, String field) {
        JsonNode songs = node.get("songs");
        if (songs != null && songs.isObject()) {
            return text(songs, field);
        }
        return text(node, field);
    }

    private static String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException("Mass file requires " + field + ".");
        }
        return value;
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
