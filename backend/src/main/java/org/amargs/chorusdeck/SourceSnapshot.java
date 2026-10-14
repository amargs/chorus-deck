package org.amargs.chorusdeck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 of the PowerPoint and both songbooks, stored in config after a successful index. */
public record SourceSnapshot(String pptx, String songbook, String rites) {

    enum Refresh {
        UNCHANGED,
        SONGBOOK,
        FULL
    }

    static SourceSnapshot of(AppConfig config) throws IOException {
        Path directory = config.indexFile().getParent();
        return new SourceSnapshot(
                sha256(config.sourcePptx()),
                sha256(directory.resolve("songbook_default.docx")),
                sha256(directory.resolve("songbook_rites.docx")));
    }

    static SourceSnapshot read(Path configFile) throws IOException {
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

    void write(Path configFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(configFile.toFile());
        ObjectNode hashes = mapper.createObjectNode();
        hashes.put("pptx", pptx);
        hashes.put("songbook", songbook);
        hashes.put("rites", rites);
        root.set("sourceHashes", hashes);
        mapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), root);
    }

    static Refresh decide(SourceSnapshot stored, SourceSnapshot current, boolean indexExists) {
        if (!indexExists || stored == null) {
            return Refresh.FULL;
        }
        if (!stored.pptx.equals(current.pptx)) {
            return Refresh.FULL;
        }
        if (!stored.songbook.equals(current.songbook) || !stored.rites.equals(current.rites)) {
            return Refresh.SONGBOOK;
        }
        return Refresh.UNCHANGED;
    }

    static void warnIfStale(Path configFile, AppConfig config) throws IOException {
        SourceSnapshot stored = read(configFile);
        if (stored == null) {
            System.err.println("Source hashes are missing. Run index before trusting this deck.");
            return;
        }
        SourceSnapshot current = of(config);
        StringBuilder changed = new StringBuilder();
        appendChanged(changed, stored.pptx, current.pptx, config.sourcePptx().getFileName());
        Path directory = config.indexFile().getParent();
        appendChanged(changed, stored.songbook, current.songbook, directory.resolve("songbook_default.docx").getFileName());
        appendChanged(changed, stored.rites, current.rites, directory.resolve("songbook_rites.docx").getFileName());
        if (!changed.isEmpty()) {
            System.err.println("Changed since last index: " + changed + ". Run index before trusting this deck.");
        }
    }

    private static void appendChanged(StringBuilder changed, String stored, String current, Path name) {
        if (stored.equals(current)) {
            return;
        }
        if (!changed.isEmpty()) {
            changed.append(", ");
        }
        changed.append(name);
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
