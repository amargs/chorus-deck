package org.amargs.chorusdeck.index;

import org.amargs.chorusdeck.model.SongCfg;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The song list stored in {@code index.json}. */
public final class SongIndex {

    private SongIndex() {
    }

    public static List<SongCfg> read(Path indexFile) throws IOException {
        try (InputStream in = Files.newInputStream(indexFile)) {
            List<SongCfg> songs = new ObjectMapper().readValue(in, new TypeReference<List<SongCfg>>() {});
            return songs == null ? List.of() : songs;
        }
    }

    public static List<SongCfg> readIfExists(Path indexFile) throws IOException {
        if (!Files.isRegularFile(indexFile)) {
            return List.of();
        }
        return read(indexFile);
    }

    public static void write(Path indexFile, List<SongCfg> songs) throws IOException {
        Path parent = indexFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try (OutputStream out = Files.newOutputStream(indexFile)) {
            mapper.writeValue(out, songs);
        }
    }
}
