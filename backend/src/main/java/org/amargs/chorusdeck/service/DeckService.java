package org.amargs.chorusdeck.service;

import org.amargs.chorusdeck.config.AppConfig;
import org.amargs.chorusdeck.deck.DeckFile;
import org.amargs.chorusdeck.deck.TemplateLayouts;
import org.amargs.chorusdeck.index.SongIndex;
import org.amargs.chorusdeck.model.Cover;
import org.amargs.chorusdeck.model.Interval;
import org.amargs.chorusdeck.model.Season;
import org.amargs.chorusdeck.model.SongCfg;
import org.amargs.chorusdeck.text.BoldMarkup;
import org.amargs.chorusdeck.text.FitCache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DeckService {

    private static final Logger LOG = LogManager.getLogger(DeckService.class);

    public Generated generate(AppConfig config, Selection selection, Cover cover) throws IOException {
        long started = System.nanoTime();
        BoldMarkup.requireDeckFonts();
        long mark = step(started, "Found " + BoldMarkup.SEMIBOLD + ", " + BoldMarkup.PLAYFAIR + ", " + BoldMarkup.PLAYFAIR_BOLD);
        DeckFile.ensureWritable(config.outputFile());
        mark = step(mark, "Checked the output file is writable");

        List<SongCfg> songs = SongIndex.read(config.indexFile());
        mark = step(mark, "Read index (" + songs.size() + " songs)");

        Path parent = config.outputFile().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        long loadFits = System.nanoTime();
        FitCache fits = FitCache.load(config.indexFile().resolveSibling("fit.json"));
        mark = step(loadFits, "Loaded fit cache");
        try (InputStream in = Files.newInputStream(config.template4x3());
             XMLSlideShow templatePpt = new XMLSlideShow(in)) {
            mark = step(mark, "Opened template");
            Season season = Season.resolve(cover.season(), cover.cover());
            XSLFSlideLayout coverLayout = TemplateLayouts.findLayout(templatePpt, season.coverLayout());
            XSLFSlideLayout titleLayout = TemplateLayouts.findLayout(templatePpt, season.titleLayout());
            XSLFSlideLayout lyricLayout = TemplateLayouts.findLayout(templatePpt, TemplateLayouts.LYRICS);
            if (season.headerScheme() != null) {
                TemplateLayouts.setHeaderScheme(titleLayout, season.headerScheme());
            }
            mark = step(mark, "Resolved layouts (" + season.coverLayout() + ", " + season.titleLayout() + ")");

            XSLFSlide coverSlide = templatePpt.createSlide(coverLayout);
            long coverText = System.nanoTime();
            fillCover(coverSlide, cover, season);
            mark = step(coverText, "Filled cover text");
            List<Generated.Song> placed = new ArrayList<>();
            int slideCount = 1;
            for (Picked picked : pick(songs, selection)) {
                slideCount += appendSong(templatePpt, titleLayout, lyricLayout, fits, picked, placed);
            }

            mark = System.nanoTime();
            DeckFile.write(templatePpt, config.outputFile());
            step(mark, "Wrote " + config.outputFile().toAbsolutePath() + " (" + slideCount + " slides)");
            LOG.info("Fit cache: " + fits.reused() + " reused, " + fits.stored() + " new");
            LOG.info("Generate finished (" + ms(System.nanoTime() - started) + " ms)");
            return new Generated(songs.size(), season.coverLayout(), placed, slideCount);
        } finally {
            fits.save();
        }
    }

    public Generated generateSongs(AppConfig config, Selection selection, Cover cover) throws IOException {
        return generateSongs(config, selection, cover, Interval.blank());
    }

    public Generated generateSongs(AppConfig config, Selection selection, Cover cover, Interval interval) throws IOException {
        long started = System.nanoTime();
        BoldMarkup.requireDeckFonts();
        long mark = step(started, "Found " + BoldMarkup.SEMIBOLD + ", " + BoldMarkup.PLAYFAIR + ", " + BoldMarkup.PLAYFAIR_BOLD);
        DeckFile.ensureWritable(config.outputFile());
        mark = step(mark, "Checked the output file is writable");

        List<SongCfg> songs = SongIndex.read(config.indexFile());
        mark = step(mark, "Read index (" + songs.size() + " songs)");
        List<Picked> picked = pick(songs, selection);
        if (picked.isEmpty()) {
            throw new IllegalArgumentException("A song deck needs at least one song.");
        }

        Path parent = config.outputFile().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        long loadFits = System.nanoTime();
        FitCache fits = FitCache.load(config.indexFile().resolveSibling("fit.json"));
        mark = step(loadFits, "Loaded fit cache");
        try (InputStream in = Files.newInputStream(config.template4x3());
             XMLSlideShow templatePpt = new XMLSlideShow(in)) {
            mark = step(mark, "Opened template");
            Season season = cover == null ? null : Season.resolve(cover.season(), cover.cover());
            String titleName = season == null ? TemplateLayouts.ORDINARY_TITLE : season.titleLayout();
            XSLFSlideLayout titleLayout = TemplateLayouts.findLayout(templatePpt, titleName);
            XSLFSlideLayout lyricLayout = TemplateLayouts.findLayout(templatePpt, TemplateLayouts.LYRICS);
            if (season != null && season.headerScheme() != null) {
                TemplateLayouts.setHeaderScheme(titleLayout, season.headerScheme());
            }

            List<Generated.Song> placed = new ArrayList<>();
            int slideCount = 0;
            XSLFPictureData picture = interval.needsImage()
                    ? templatePpt.addPicture(Files.readAllBytes(interval.image()), interval.pictureType())
                    : null;
            if (cover != null) {
                XSLFSlide coverSlide = templatePpt.createSlide(TemplateLayouts.findLayout(templatePpt, season.coverLayout()));
                fillCover(coverSlide, cover, season);
                TemplateLayouts.setNotes(coverSlide, notes(picked));
                slideCount++;
                addInterval(templatePpt, interval, picture);
                slideCount++;
            }
            for (int i = 0; i < picked.size(); i++) {
                slideCount += appendSong(templatePpt, titleLayout, lyricLayout, fits, picked.get(i), placed);
                if (i < picked.size() - 1) {
                    addInterval(templatePpt, interval, picture);
                    slideCount++;
                }
            }

            mark = System.nanoTime();
            DeckFile.write(templatePpt, config.outputFile());
            step(mark, "Wrote " + config.outputFile().toAbsolutePath() + " (" + slideCount + " slides)");
            LOG.info("Fit cache: " + fits.reused() + " reused, " + fits.stored() + " new");
            LOG.info("Generate finished (" + ms(System.nanoTime() - started) + " ms)");
            String coverLayout = season == null ? null : season.coverLayout();
            return new Generated(songs.size(), coverLayout, placed, slideCount);
        } finally {
            fits.save();
        }
    }

    private static void addInterval(XMLSlideShow ppt, Interval interval, XSLFPictureData picture) throws IOException {
        XSLFSlide slide = TemplateLayouts.addSlide(ppt, intervalLayout(interval));
        if (interval.needsText()) {
            TemplateLayouts.setShapeText(slide, "Letra", interval.text(), false);
        }
        if (interval.needsImage()) {
            TemplateLayouts.setIntervalPicture(slide, picture);
        }
    }

    private static String intervalLayout(Interval interval) {
        return switch (interval.kind()) {
            case VAZIO -> TemplateLayouts.INTERVAL_BLANK;
            case IMAGEM -> TemplateLayouts.INTERVAL_IMAGE;
            case TEXTO -> TemplateLayouts.INTERVAL_TEXT;
            case TEXTO_IMAGEM -> TemplateLayouts.INTERVAL_TEXT_IMAGE;
        };
    }

    private static void fillCover(XSLFSlide coverSlide, Cover cover, Season season) {
        TemplateLayouts.setShapeText(coverSlide, "Domingo", cover.domingo(), false);
        TemplateLayouts.setShapeText(coverSlide, "Frase", cover.quotedFrase(), true);
        TemplateLayouts.setShapeText(coverSlide, "Versiculo", cover.versiculo(), false);
        TemplateLayouts.setShapeText(coverSlide, "Tempo", season.tempo(cover.ano()), false);
    }

    private static String notes(List<Picked> picked) {
        StringBuilder lines = new StringBuilder();
        for (Picked pickedSong : picked) {
            if (!lines.isEmpty()) {
                lines.append('\n');
            }
            SongCfg song = pickedSong.song();
            String title = song.title() == null ? "" : song.title().trim();
            if (song.bookId() == null || song.bookId().isBlank()) {
                lines.append(title);
            } else {
                lines.append(song.bookId()).append(" - ").append(title);
            }
        }
        return lines.toString();
    }

    private static int appendSong(
            XMLSlideShow ppt,
            XSLFSlideLayout titleLayout,
            XSLFSlideLayout lyricLayout,
            FitCache fits,
            Picked picked,
            List<Generated.Song> placed) {
        SongCfg song = picked.song();
        if (song.content() == null || song.content().isEmpty()) {
            throw new IllegalArgumentException("Song " + picked.key() + " has no verses.");
        }
        long songStart = System.nanoTime();
        int verseCount = song.content().size();
        placed.add(new Generated.Song(picked.key(), song.title().trim(), verseCount));
        XSLFSlide firstSlide = ppt.createSlide(titleLayout);
        long textStart = System.nanoTime();
        TemplateLayouts.setShapeText(firstSlide, "Título", song.title(), fits, song.id(), 1);
        TemplateLayouts.setShapeText(firstSlide, "Letra", song.content().get(0), fits, song.id(), 1);
        long text = System.nanoTime() - textStart;
        for (int i = 1; i < verseCount; i++) {
            XSLFSlide slide = ppt.createSlide(lyricLayout);
            textStart = System.nanoTime();
            TemplateLayouts.setShapeText(slide, "Letra", song.content().get(i), fits, song.id(), i + 1);
            text += System.nanoTime() - textStart;
        }
        LOG.info("Song " + picked.key() + ": " + song.title().trim()
                + " (" + verseCount + " verses, " + ms(System.nanoTime() - songStart) + " ms, text " + ms(text) + " ms)");
        return verseCount;
    }

    private static long step(long since, String message) {
        long now = System.nanoTime();
        LOG.info(message + " (" + ms(now - since) + " ms)");
        return now;
    }

    private static long ms(long nanos) {
        return nanos / 1_000_000;
    }

    public record Generated(int loadedSongs, String coverLayout, List<Song> songs, int slides) {
        public record Song(String key, String title, int verses) {
        }
    }

    public sealed interface Selection permits Selection.Ids, Selection.BookIds {
        record Ids(List<Integer> ids) implements Selection {
        }

        record BookIds(List<String> bookIds) implements Selection {
        }
    }

    private record Picked(String key, SongCfg song) {
    }

    private static List<Picked> pick(List<SongCfg> songs, Selection selection) {
        List<Picked> picked = new ArrayList<>();
        if (selection instanceof Selection.Ids byId) {
            for (int id : byId.ids()) {
                picked.add(new Picked(Integer.toString(id), findId(songs, id)));
            }
            return picked;
        }
        Selection.BookIds byBook = (Selection.BookIds) selection;
        for (String bookId : byBook.bookIds()) {
            picked.add(new Picked(bookId, findBook(songs, bookId)));
        }
        return picked;
    }

    private static SongCfg findId(List<SongCfg> songs, int id) {
        for (SongCfg song : songs) {
            if (song.id() == id) {
                return song;
            }
        }
        throw new IllegalArgumentException("Unknown song id: " + id);
    }

    private static SongCfg findBook(List<SongCfg> songs, String bookId) {
        SongCfg found = null;
        for (SongCfg song : songs) {
            if (bookId.equals(song.bookId())) {
                if (found != null) {
                    throw new IllegalArgumentException("Songbook id " + bookId + " matches more than one song.");
                }
                found = song;
            }
        }
        if (found == null) {
            throw new IllegalArgumentException("Unknown songbook id: " + bookId);
        }
        return found;
    }
}
