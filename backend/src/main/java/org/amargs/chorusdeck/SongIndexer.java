package org.amargs.chorusdeck;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SongIndexer {

    public void rebuild(AppConfig config) throws IOException {
        ZipSecureFile.setMaxFileCount(10000);
        List<SongCfg> fresh;
        try (InputStream in = Files.newInputStream(config.sourcePptx());
             XMLSlideShow ppt = new XMLSlideShow(in)) {
            fresh = index(ppt);
        }
        IdMatch matched = assignIds(fresh, readIfExists(config.indexFile()));
        List<String> warnings = new ArrayList<>();
        List<SongCfg> songs = attachBookIds(matched.songs(), readSongbooks(config.indexFile().getParent()), warnings);
        for (SongCfg song : matched.removed()) {
            System.out.println("Removed " + song.id() + ": " + song.title().trim());
        }
        for (SongCfg song : matched.added()) {
            System.out.println("New " + song.id() + ": " + song.title().trim());
        }
        for (String warning : warnings) {
            System.out.println(warning);
        }
        long withBookId = songs.stream().filter(song -> song.bookId() != null).count();
        System.out.println("bookId on " + withBookId + " of " + songs.size() + " songs");
        write(config.indexFile(), songs);
    }

    static List<SongCfg> index(XMLSlideShow ppt) {
        int idx = 0;
        List<SongCfg> songs = new ArrayList<>();

        for (XSLFSlide slide : ppt.getSlides()) {
            String slideTitle = slide.getTitle();

            if (slideTitle != null && !slideTitle.isEmpty()) {
                String title = songTitle(slideTitle);
                System.out.println(title);
                XSLFTextShape bodyShape = (XSLFTextShape) slide.getShapes().get(1);
                String body = BoldMarkup.mark(bodyShape);

                List<String> content = new ArrayList<>();
                content.add(body);
                songs.add(new SongCfg(++idx, title, content));
            } else {
                if (idx > 0 && !slideIsEmpty(slide)) {
                    XSLFTextShape bodyShape = (XSLFTextShape) slide.getShapes().get(0);
                    String body = BoldMarkup.mark(bodyShape);
                    songs.get(idx - 1).content().add(body);
                }
            }
        }
        stripUniformBoldOnSingleSlideSongs(songs);
        return songs;
    }

    static String songTitle(String slideTitle) {
        int breakAt = slideTitle.indexOf('\n');
        String title = breakAt >= 0 ? slideTitle.substring(breakAt + 1) : slideTitle;
        return title.replace('\u00A0', ' ').strip();
    }

    static void stripUniformBoldOnSingleSlideSongs(List<SongCfg> songs) {
        for (SongCfg song : songs) {
            if (song.content() != null && song.content().size() == 1) {
                song.content().set(0, BoldMarkup.plainIfFullyBold(song.content().get(0)));
            }
        }
    }

    static boolean slideIsEmpty(XSLFSlide slide) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                String text = ((XSLFTextShape) shape).getText();
                if (text != null && !text.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    record IdMatch(List<SongCfg> songs, List<SongCfg> removed, List<SongCfg> added) {
    }

    static IdMatch assignIds(List<SongCfg> fresh, List<SongCfg> previous) {
        List<SongCfg> prior = previous == null ? List.of() : previous;
        Map<String, List<SongCfg>> byExact = new HashMap<>();
        Map<String, List<SongCfg>> byNormalized = new HashMap<>();
        int maxId = 0;
        for (SongCfg song : prior) {
            maxId = Math.max(maxId, song.id());
            byExact.computeIfAbsent(exactKey(song.title()), key -> new ArrayList<>()).add(song);
            byNormalized.computeIfAbsent(TitleNormalizer.normalize(song.title()), key -> new ArrayList<>()).add(song);
        }

        Set<Integer> used = new HashSet<>();
        List<SongCfg> songs = new ArrayList<>();
        List<SongCfg> added = new ArrayList<>();
        for (SongCfg song : fresh) {
            SongCfg match = takeUnused(byExact.get(exactKey(song.title())), used);
            if (match == null) {
                List<SongCfg> normalized = byNormalized.get(TitleNormalizer.normalize(song.title()));
                if (normalized != null && normalized.size() == 1) {
                    match = takeUnused(normalized, used);
                }
            }
            int id;
            String bookId;
            if (match != null) {
                id = match.id();
                bookId = match.bookId();
                used.add(id);
            } else {
                id = ++maxId;
                bookId = null;
            }
            SongCfg assigned = new SongCfg(id, song.title(), song.content(), bookId);
            songs.add(assigned);
            if (match == null) {
                added.add(assigned);
            }
        }

        List<SongCfg> removed = new ArrayList<>();
        for (SongCfg song : prior) {
            if (!used.contains(song.id())) {
                removed.add(song);
            }
        }
        return new IdMatch(songs, removed, added);
    }

    static List<SongCfg> attachBookIds(
            List<SongCfg> songs, List<SongbookReader.Entry> entries, List<String> warnings) {
        Map<String, List<SongbookReader.Entry>> byNormalized = new LinkedHashMap<>();
        for (SongbookReader.Entry entry : entries) {
            String key = TitleNormalizer.normalize(entry.title());
            if (key.isEmpty()) {
                continue;
            }
            byNormalized.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }
        Set<String> ambiguousBooks = new HashSet<>();
        for (List<SongbookReader.Entry> group : byNormalized.values()) {
            if (group.size() > 1) {
                ambiguousBooks.add(TitleNormalizer.normalize(group.get(0).title()));
                warnings.add("Ambiguous songbook title: " + group.get(0).title());
            }
        }

        Map<String, Integer> songCounts = new HashMap<>();
        for (SongCfg song : songs) {
            String key = TitleNormalizer.normalize(song.title());
            if (!key.isEmpty()) {
                songCounts.merge(key, 1, Integer::sum);
            }
        }
        Set<String> warnedSlides = new HashSet<>();
        List<SongCfg> attached = new ArrayList<>();
        for (SongCfg song : songs) {
            String key = TitleNormalizer.normalize(song.title());
            String bookId = null;
            if (!key.isEmpty() && songCounts.getOrDefault(key, 0) == 1 && !ambiguousBooks.contains(key)) {
                List<SongbookReader.Entry> found = byNormalized.get(key);
                if (found != null && found.size() == 1) {
                    bookId = found.get(0).bookId();
                }
            } else if (!key.isEmpty() && songCounts.getOrDefault(key, 0) > 1 && warnedSlides.add(key)) {
                warnings.add("Ambiguous slide title: " + song.title());
            }
            attached.add(new SongCfg(song.id(), song.title(), song.content(), bookId));
        }
        return attached;
    }

    private static List<SongCfg> readIfExists(Path indexFile) throws IOException {
        if (!Files.isRegularFile(indexFile)) {
            return List.of();
        }
        try (InputStream in = Files.newInputStream(indexFile)) {
            List<SongCfg> songs = new ObjectMapper()
                    .readValue(in, new TypeReference<List<SongCfg>>() {});
            return songs == null ? List.of() : songs;
        }
    }

    private static List<SongbookReader.Entry> readSongbooks(Path directory) throws IOException {
        SongbookReader reader = new SongbookReader();
        List<SongbookReader.Entry> entries = new ArrayList<>();
        for (String name : List.of("songbook_default.docx", "songbook_rites.docx")) {
            Path docx = directory.resolve(name);
            for (SongbookReader.Item item : reader.read(docx)) {
                if (item instanceof SongbookReader.Entry entry) {
                    entries.add(entry);
                } else if (item instanceof SongbookReader.MissingPrefix missing) {
                    System.out.println(SongbookReader.line(missing));
                }
            }
        }
        return entries;
    }

    private static SongCfg takeUnused(List<SongCfg> candidates, Set<Integer> used) {
        if (candidates == null) {
            return null;
        }
        for (SongCfg song : candidates) {
            if (!used.contains(song.id())) {
                return song;
            }
        }
        return null;
    }

    private static String exactKey(String title) {
        if (title == null) {
            return "";
        }
        return title.replace('\u00A0', ' ').trim();
    }

    private static void write(Path indexFile, List<SongCfg> songs) throws IOException {
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
