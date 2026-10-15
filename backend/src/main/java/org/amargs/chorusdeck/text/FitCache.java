package org.amargs.chorusdeck.text;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fitted font sizes from earlier runs, one entry per song id and slide. */
public final class FitCache {

    private static final Logger LOG = LogManager.getLogger(FitCache.class);
    private static final int VERSION = 1;

    private final Path file;
    private final Map<Integer, SongBody> songs = new LinkedHashMap<>();
    private int reused;
    private int stored;
    private boolean dirty;

    private FitCache(Path file) {
        this.file = file;
    }

    public static FitCache load(Path file) throws IOException {
        FitCache cache = new FitCache(file);
        if (!Files.isRegularFile(file)) {
            return cache;
        }
        try (InputStream in = Files.newInputStream(file)) {
            FileContents contents = new ObjectMapper().readValue(in, FileContents.class);
            if (contents == null || contents.version != VERSION || contents.songs == null) {
                return cache;
            }
            for (Map.Entry<String, SongBody> entry : contents.songs.entrySet()) {
                if (entry.getValue() == null || entry.getValue().slides == null) {
                    continue;
                }
                cache.songs.put(Integer.valueOf(entry.getKey()), entry.getValue());
            }
        } catch (IOException | NumberFormatException e) {
            LOG.warn("Ignoring unreadable fit cache " + file.toAbsolutePath());
        }
        return cache;
    }

    /** Short id of the verse text. The verse itself stays in {@code index.json}. */
    public static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Remembered size in hundredths of a point, or 0 when the layout size already fits. A miss returns null. */
    public Integer recall(int songId, int slide, String shape, String box, String hash) {
        ShapeBody found = find(songId, slide, shape);
        if (found == null || !box.equals(found.box) || !hash.equals(found.hash)) {
            return null;
        }
        reused++;
        return found.size;
    }

    public void remember(int songId, int slide, String layout, String shape, String box, String hash, int size) {
        SongBody song = songs.computeIfAbsent(songId, id -> new SongBody());
        if (song.slides == null) {
            song.slides = new ArrayList<>();
        }
        SlideBody body = song.slides.stream().filter(item -> item.slide == slide).findFirst().orElseGet(() -> {
            SlideBody created = new SlideBody();
            created.slide = slide;
            created.shapes = new ArrayList<>();
            song.slides.add(created);
            return created;
        });
        body.layout = layout;
        if (body.shapes == null) {
            body.shapes = new ArrayList<>();
        }
        ShapeBody found = body.shapes.stream()
                .filter(item -> shape.equals(item.shape))
                .findFirst()
                .orElseGet(() -> {
                    ShapeBody created = new ShapeBody();
                    created.shape = shape;
                    body.shapes.add(created);
                    return created;
                });
        found.box = box;
        found.hash = hash;
        found.size = size;
        stored++;
        dirty = true;
    }

    public int reused() {
        return reused;
    }

    public int stored() {
        return stored;
    }

    public void save() throws IOException {
        if (!dirty) {
            return;
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Map<String, SongBody> ordered = new LinkedHashMap<>();
        songs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (entry.getValue().slides != null) {
                        entry.getValue().slides.sort(Comparator.comparingInt(slide -> slide.slide));
                    }
                    ordered.put(Integer.toString(entry.getKey()), entry.getValue());
                });
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try (OutputStream out = Files.newOutputStream(file)) {
            mapper.writeValue(out, new FileContents(VERSION, ordered));
        }
        dirty = false;
    }

    private ShapeBody find(int songId, int slide, String shape) {
        SongBody song = songs.get(songId);
        if (song == null || song.slides == null) {
            return null;
        }
        for (SlideBody body : song.slides) {
            if (body.slide != slide || body.shapes == null) {
                continue;
            }
            for (ShapeBody item : body.shapes) {
                if (shape.equals(item.shape)) {
                    return item;
                }
            }
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class FileContents {
        public int version;
        public Map<String, SongBody> songs;

        public FileContents() {
        }

        private FileContents(int version, Map<String, SongBody> songs) {
            this.version = version;
            this.songs = songs;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class SongBody {
        public List<SlideBody> slides;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class SlideBody {
        public int slide;
        public String layout;
        public List<ShapeBody> shapes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ShapeBody {
        public String shape;
        public String box;
        public String hash;
        public int size;
    }
}
