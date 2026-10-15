package org.amargs.chorusdeck.index;

import org.amargs.chorusdeck.config.AppConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Reads and writes the source hashes stored in config. */
public final class SourceHashes {

    private SourceHashes() {
    }

    public static SourceSnapshot of(AppConfig config) throws IOException {
        Path directory = config.indexFile().getParent();
        return new SourceSnapshot(
                sha256(config.sourcePptx()),
                sha256(directory.resolve("songbook_default.docx")),
                sha256(directory.resolve("songbook_rites.docx")));
    }

    public static SourceSnapshot read(Path configFile) throws IOException {
        JsonNode hashes = new ObjectMapper().readTree(configFile.toFile()).get("sourceHashes");
        if (hashes == null || !hashes.isObject()) {
            return null;
        }
        String pptx = text(hashes, "pptx");
        String songbook = text(hashes, "songbook");
        String rites = text(hashes, "rites");
        if (pptx == null || songbook == null || rites == null) {
            return null;
        }
        return new SourceSnapshot(pptx, songbook, rites);
    }

    public static void write(Path configFile, SourceSnapshot snapshot) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(configFile.toFile());
        ObjectNode hashes = mapper.createObjectNode();
        hashes.put("pptx", snapshot.pptx());
        hashes.put("songbook", snapshot.songbook());
        hashes.put("rites", snapshot.rites());
        root.set("sourceHashes", hashes);
        mapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), root);
    }

    public static StaleSources check(Path configFile, AppConfig config) throws IOException {
        SourceSnapshot stored = read(configFile);
        if (stored == null) {
            return new StaleSources(true, List.of());
        }
        SourceSnapshot current = of(config);
        List<String> changed = new ArrayList<>();
        if (!stored.pptx().equals(current.pptx())) {
            changed.add(config.sourcePptx().getFileName().toString());
        }
        Path directory = config.indexFile().getParent();
        if (!stored.songbook().equals(current.songbook())) {
            changed.add(directory.resolve("songbook_default.docx").getFileName().toString());
        }
        if (!stored.rites().equals(current.rites())) {
            changed.add(directory.resolve("songbook_rites.docx").getFileName().toString());
        }
        return new StaleSources(false, changed);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    private static String sha256(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Source file not found: " + path);
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
