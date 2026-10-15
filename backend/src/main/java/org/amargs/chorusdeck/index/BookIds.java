package org.amargs.chorusdeck.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Songbook numbers attached by a normalized title. An ambiguous title is left without a number. */
public final class BookIds {

    private BookIds() {
    }

    public static List<SongCfg> attach(
            List<SongCfg> songs, List<SongbookItem.Entry> entries, List<String> warnings) {
        Map<String, List<SongbookItem.Entry>> byNormalized = new LinkedHashMap<>();
        for (SongbookItem.Entry entry : entries) {
            String key = TitleNormalizer.normalize(entry.title());
            if (key.isEmpty()) {
                continue;
            }
            byNormalized.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }
        Set<String> ambiguousBooks = new HashSet<>();
        for (List<SongbookItem.Entry> group : byNormalized.values()) {
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
                List<SongbookItem.Entry> found = byNormalized.get(key);
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
}
