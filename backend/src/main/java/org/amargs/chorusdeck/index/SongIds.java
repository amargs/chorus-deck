package org.amargs.chorusdeck.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps each song id when the master deck is indexed again. */
public final class SongIds {

    private SongIds() {
    }

    public record IdMatch(List<SongCfg> songs, List<SongCfg> removed, List<SongCfg> added) {
    }

    public static IdMatch assign(List<SongCfg> fresh, List<SongCfg> previous) {
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
}
