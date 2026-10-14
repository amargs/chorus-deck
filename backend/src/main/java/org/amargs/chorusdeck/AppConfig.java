package org.amargs.chorusdeck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record AppConfig(Path sourcePptx, Path indexFile, Path template4x3, Path outputFile, String boldWeight) {

    public AppConfig(Path sourcePptx, Path indexFile, Path template4x3, Path outputFile) {
        this(sourcePptx, indexFile, template4x3, outputFile, "semibold");
    }

    public static AppConfig load(Path configFile) throws IOException {
        if (!Files.isRegularFile(configFile)) {
            throw new IllegalArgumentException("Config file not found: " + configFile);
        }
        JsonNode node = new ObjectMapper().readTree(configFile.toFile());
        Path base = configFile.toAbsolutePath().normalize().getParent();
        return new AppConfig(
                resolve(base, text(node, "sourcePptx")),
                resolve(base, text(node, "indexFile")),
                resolve(base, text(node, "template4x3")),
                resolve(base, text(node, "outputFile")),
                weight(node));
    }

    public static Path findConfig(Path explicit) {
        if (explicit != null) {
            Path path = explicit.isAbsolute()
                    ? explicit
                    : Path.of(System.getProperty("user.dir")).resolve(explicit);
            return path.normalize();
        }
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path here = cwd.resolve("config.json");
        if (Files.isRegularFile(here)) {
            return here.normalize();
        }
        Path nested = cwd.resolve("backend").resolve("config.json");
        if (Files.isRegularFile(nested)) {
            return nested.normalize();
        }
        throw new IllegalArgumentException(
                "config.json not found in " + cwd + ". Pass --config <file>.");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Config is missing a non-empty \"" + field + "\" string.");
        }
        return value.asText();
    }

    private static String weight(JsonNode node) {
        JsonNode value = node.get("boldWeight");
        if (value == null || value.isNull()) {
            return "semibold";
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Config \"boldWeight\" must be a non-empty string such as semibold or bold.");
        }
        return value.asText().trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static Path resolve(Path base, String value) {
        Path path = Path.of(value);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return base.resolve(path).normalize();
    }
}
