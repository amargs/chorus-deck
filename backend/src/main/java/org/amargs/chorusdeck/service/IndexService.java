package org.amargs.chorusdeck.service;

import org.amargs.chorusdeck.config.AppConfig;
import org.amargs.chorusdeck.index.BookIds;
import org.amargs.chorusdeck.index.SlideSongs;
import org.amargs.chorusdeck.index.SongIds;
import org.amargs.chorusdeck.index.SongIndex;
import org.amargs.chorusdeck.index.SongbookReader;
import org.amargs.chorusdeck.index.SourceHashes;
import org.amargs.chorusdeck.model.SongCfg;
import org.amargs.chorusdeck.model.SongbookItem;
import org.amargs.chorusdeck.model.SourceSnapshot;
import org.amargs.chorusdeck.model.StaleSources;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xslf.usermodel.XMLSlideShow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Rebuilds {@code index.json} when the master deck or a songbook changes. */
public final class IndexService {

    public IndexResult rebuild(AppConfig config, Path configFile) throws IOException {
        SourceSnapshot current = SourceHashes.of(config);
        SourceSnapshot.Refresh refresh = SourceSnapshot.decide(
                SourceHashes.read(configFile), current, Files.isRegularFile(config.indexFile()));
        IndexResult result = switch (refresh) {
            case UNCHANGED -> IndexResult.unchanged();
            case SONGBOOK -> {
                IndexResult attached = reattach(config);
                yield attached == null ? rebuildFromPptx(config) : attached;
            }
            case FULL -> rebuildFromPptx(config);
        };
        if (refresh != SourceSnapshot.Refresh.UNCHANGED) {
            SourceHashes.write(configFile, current);
        }
        return result.withRefresh(refresh);
    }

    public StaleSources warnIfStale(Path configFile, AppConfig config) throws IOException {
        return SourceHashes.check(configFile, config);
    }

    private IndexResult rebuildFromPptx(AppConfig config) throws IOException {
        ZipSecureFile.setMaxFileCount(10000);
        SlideSongs.Indexed indexed;
        try (InputStream in = Files.newInputStream(config.sourcePptx());
             XMLSlideShow ppt = new XMLSlideShow(in)) {
            indexed = SlideSongs.index(ppt);
        }
        SongIds.IdMatch matched = SongIds.assign(indexed.songs(), SongIndex.readIfExists(config.indexFile()));
        List<String> warnings = new ArrayList<>();
        ReadBooks books = readSongbooks(config.indexFile().getParent());
        List<SongCfg> songs = BookIds.attach(matched.songs(), books.entries(), warnings);
        SongIndex.write(config.indexFile(), songs);
        long withBookId = songs.stream().filter(song -> song.bookId() != null).count();
        return new IndexResult(
                null,
                notes(indexed.notices()),
                books.missing(),
                matched.removed(),
                matched.added(),
                warnings,
                (int) withBookId,
                songs.size());
    }

    private IndexResult reattach(AppConfig config) throws IOException {
        List<SongCfg> existing = SongIndex.readIfExists(config.indexFile());
        if (existing.isEmpty()) {
            return null;
        }
        List<String> warnings = new ArrayList<>();
        ReadBooks books = readSongbooks(config.indexFile().getParent());
        List<SongCfg> songs = BookIds.attach(existing, books.entries(), warnings);
        SongIndex.write(config.indexFile(), songs);
        long withBookId = songs.stream().filter(song -> song.bookId() != null).count();
        return new IndexResult(
                null,
                List.of(),
                books.missing(),
                List.of(),
                List.of(),
                warnings,
                (int) withBookId,
                songs.size());
    }

    private static ReadBooks readSongbooks(Path directory) throws IOException {
        SongbookReader reader = new SongbookReader();
        List<SongbookItem.Entry> entries = new ArrayList<>();
        List<SongbookItem.MissingPrefix> missing = new ArrayList<>();
        for (String name : List.of("songbook_default.docx", "songbook_rites.docx")) {
            Path docx = directory.resolve(name);
            for (SongbookItem item : reader.read(docx)) {
                if (item instanceof SongbookItem.Entry entry) {
                    entries.add(entry);
                } else if (item instanceof SongbookItem.MissingPrefix prefix) {
                    missing.add(prefix);
                }
            }
        }
        return new ReadBooks(entries, missing);
    }

    private static List<IndexResult.Notice> notes(List<SlideSongs.Notice> notices) {
        List<IndexResult.Notice> mapped = new ArrayList<>();
        for (SlideSongs.Notice notice : notices) {
            if (notice instanceof SlideSongs.Notice.Song song) {
                mapped.add(new IndexResult.Notice.Song(song.title()));
            } else if (notice instanceof SlideSongs.Notice.Skipped skipped) {
                mapped.add(new IndexResult.Notice.Skipped(skipped.title()));
            }
        }
        return mapped;
    }

    private record ReadBooks(List<SongbookItem.Entry> entries, List<SongbookItem.MissingPrefix> missing) {
    }

    public record IndexResult(
            SourceSnapshot.Refresh refresh,
            List<IndexResult.Notice> notices,
            List<SongbookItem.MissingPrefix> missingPrefixes,
            List<SongCfg> removed,
            List<SongCfg> added,
            List<String> warnings,
            Integer bookIdCount,
            Integer songCount) {

        public sealed interface Notice permits Notice.Song, Notice.Skipped {
            record Song(String title) implements Notice {
            }

            record Skipped(String title) implements Notice {
            }
        }

        static IndexResult unchanged() {
            return new IndexResult(
                    SourceSnapshot.Refresh.UNCHANGED, List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
        }

        IndexResult withRefresh(SourceSnapshot.Refresh refresh) {
            return new IndexResult(refresh, notices, missingPrefixes, removed, added, warnings, bookIdCount, songCount);
        }
    }
}
