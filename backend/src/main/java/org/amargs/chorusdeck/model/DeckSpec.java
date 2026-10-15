package org.amargs.chorusdeck.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public sealed interface DeckSpec permits DeckSpec.Mass, DeckSpec.Songs {

    Path output();

    record Mass(MassRequest request, Path output) implements DeckSpec {
    }

    record Songs(List<Integer> ids, List<String> bookIds, Cover cover, Interval interval, Path output) implements DeckSpec {
    }

    static DeckSpec load(Path file) throws IOException {
        JsonNode node = read(file);
        String mode = text(node, "mode");
        if (mode == null && node.get("songs") != null && node.get("songs").isArray()) {
            mode = "songs";
        }
        if (mode == null || mode.equals("mass")) {
            MassRequest request = MassRequest.from(node, file);
            return new Mass(request, resolveOutput(node, file, request.cover().domingo(), request.cover().ano()));
        }
        if (!mode.equals("songs")) {
            throw new IllegalArgumentException("mode must be mass or songs.");
        }
        return songs(node, file);
    }

    static JsonNode read(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Deck file not found: " + file);
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        ObjectMapper mapper = name.endsWith(".json")
                ? new ObjectMapper()
                : new ObjectMapper(new YAMLFactory());
        JsonNode node = mapper.readTree(file.toFile());
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Deck file must be a JSON or YAML mapping: " + file);
        }
        return node;
    }

    private static Songs songs(JsonNode node, Path file) {
        if (node.get("songs") != null && !node.get("songs").isArray()) {
            throw new IllegalArgumentException("In a song deck, songs is a list of book ids.");
        }
        List<Integer> ids = integers(node.get("ids"));
        List<String> bookIds = strings(node.get("bookIds"));
        if (bookIds == null) {
            bookIds = strings(node.get("songs"));
        }
        if ((ids == null) == (bookIds == null)) {
            throw new IllegalArgumentException("A song deck needs ids or bookIds, not both.");
        }
        boolean coverFields = text(node, "season") != null || text(node, "domingo") != null
                || text(node, "frase") != null || text(node, "versiculo") != null || text(node, "ano") != null;
        Cover cover = null;
        if (coverFields) {
            String ano = text(node, "ano");
            if (ano == null || text(node, "season") == null || text(node, "domingo") == null
                    || text(node, "frase") == null || text(node, "versiculo") == null) {
                throw new IllegalArgumentException("A song deck cover requires season, ano, domingo, frase, and versiculo.");
            }
            ano = ano.toUpperCase(Locale.ROOT);
            if (!ano.equals("A") && !ano.equals("B") && !ano.equals("C")) {
                throw new IllegalArgumentException("ano must be A, B, or C.");
            }
            cover = new Cover(text(node, "season"), text(node, "cover"), text(node, "domingo"),
                    text(node, "frase"), text(node, "versiculo"), ano);
        }
        Path base = file.toAbsolutePath().getParent();
        String image = text(node, "intervalImage");
        Path imagePath = image == null ? null : base.resolve(image).normalize();
        String domingo = cover == null ? null : cover.domingo();
        String ano = cover == null ? null : cover.ano();
        return new Songs(ids, bookIds, cover,
                Interval.parse(text(node, "interval"), imagePath, text(node, "intervalText")),
                resolveOutput(node, file, domingo, ano));
    }

    static Path resolveOutput(JsonNode node, Path file, String domingo, String ano) {
        Path base = file.toAbsolutePath().getParent();
        String given = text(node, "output");
        if (given != null) {
            String name = given.toLowerCase(Locale.ROOT).endsWith(".pptx") ? given : given + ".pptx";
            Path path = Path.of(name);
            if (!path.isAbsolute()) {
                path = base.resolve(path);
            }
            return path.normalize();
        }
        if (domingo == null || ano == null) {
            throw new IllegalArgumentException("This deck needs an output name, or a Sunday and year.");
        }
        return base.resolve(fileName(domingo, ano)).normalize();
    }

    public static String fileName(String domingo, String ano) {
        String label = domingo.trim().replaceAll("[\\\\/:*?\"<>|]", "");
        if (!label.regionMatches(true, 0, "Domingo", 0, "Domingo".length())) {
            label = "Domingo " + label;
        }
        return label + "_" + ano.toUpperCase(Locale.ROOT) + ".pptx";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException("Song ids must be a non-empty list.");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String text = item.asText().trim();
            if (text.isEmpty()) {
                throw new IllegalArgumentException("Song ids must be a non-empty list.");
            }
            values.add(text);
        }
        return values;
    }

    private static List<Integer> integers(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException("Song ids must be a non-empty list.");
        }
        List<Integer> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isIntegralNumber() || item.intValue() < 1) {
                throw new IllegalArgumentException("Song ids start at 1.");
            }
            values.add(item.intValue());
        }
        return values;
    }
}
