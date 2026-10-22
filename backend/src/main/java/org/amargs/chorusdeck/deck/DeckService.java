package org.amargs.chorusdeck.deck;

import org.amargs.chorusdeck.config.AppConfig;
import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.index.SongIndex;
import org.amargs.chorusdeck.liturgy.LiturgyDay;
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
        BoldMarkup.requireDeckFonts();
        LOG.info("Found " + BoldMarkup.SEMIBOLD + ", " + BoldMarkup.TITLE + ", " + BoldMarkup.PLAYFAIR + ", " + BoldMarkup.PLAYFAIR_BOLD);
        DeckFile.ensureWritable(config.outputFile());
        LOG.info("Checked the output file is writable");

        List<SongCfg> songs = SongIndex.read(config.indexFile());
        LOG.info("Read index (" + songs.size() + " songs)");

        Path parent = config.outputFile().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        FitCache fits = FitCache.load(config.indexFile().resolveSibling("fit.json"));
        LOG.info("Loaded fit cache");
        try (InputStream in = Files.newInputStream(config.template4x3());
             XMLSlideShow templatePpt = new XMLSlideShow(in)) {
            LOG.info("Opened template");
            Season season = Season.resolve(cover.season(), cover.cover());
            XSLFSlideLayout coverLayout = TemplateLayouts.findLayout(templatePpt, season.coverLayout());
            XSLFSlideLayout titleLayout = TemplateLayouts.findLayout(templatePpt, season.titleLayout());
            XSLFSlideLayout lyricLayout = TemplateLayouts.findLayout(templatePpt, TemplateLayouts.LYRICS);
            if (season.headerScheme() != null) {
                TemplateLayouts.setHeaderScheme(titleLayout, season.headerScheme());
            }
            LOG.info("Resolved layouts (" + season.coverLayout() + ", " + season.titleLayout() + ")");

            XSLFSlide coverSlide = templatePpt.createSlide(coverLayout);
            fillCover(coverSlide, cover, season);
            LOG.info("Filled cover text");
            List<Generated.Song> placed = new ArrayList<>();
            int slideCount = 1;
            for (Picked picked : pick(songs, selection)) {
                slideCount += appendSong(templatePpt, titleLayout, lyricLayout, fits, picked, placed);
            }

            DeckFile.write(templatePpt, config.outputFile());
            LOG.info("Wrote " + config.outputFile().toAbsolutePath() + " (" + slideCount + " slides)");
            LOG.info("Fit cache: " + fits.reused() + " reused, " + fits.stored() + " new");
            LOG.info("Generate finished");
            return new Generated(songs.size(), season.coverLayout(), placed, slideCount);
        } finally {
            fits.save();
        }
    }

    public Generated generateSongs(AppConfig config, Selection selection, Cover cover) throws IOException {
        return generateSongs(config, selection, cover, Interval.blank());
    }

    public Generated generateSongs(AppConfig config, Selection selection, Cover cover, Interval interval) throws IOException {
        BoldMarkup.requireDeckFonts();
        LOG.info("Found " + BoldMarkup.SEMIBOLD + ", " + BoldMarkup.TITLE + ", " + BoldMarkup.PLAYFAIR + ", " + BoldMarkup.PLAYFAIR_BOLD);
        DeckFile.ensureWritable(config.outputFile());
        LOG.info("Checked the output file is writable");

        List<SongCfg> songs = SongIndex.read(config.indexFile());
        LOG.info("Read index (" + songs.size() + " songs)");
        List<Picked> picked = pick(songs, selection);
        if (picked.isEmpty()) {
            throw new IllegalArgumentException("A song deck needs at least one song.");
        }

        Path parent = config.outputFile().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        FitCache fits = FitCache.load(config.indexFile().resolveSibling("fit.json"));
        LOG.info("Loaded fit cache");
        try (InputStream in = Files.newInputStream(config.template4x3());
             XMLSlideShow templatePpt = new XMLSlideShow(in)) {
            LOG.info("Opened template");
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

            DeckFile.write(templatePpt, config.outputFile());
            LOG.info("Wrote " + config.outputFile().toAbsolutePath() + " (" + slideCount + " slides)");
            LOG.info("Fit cache: " + fits.reused() + " reused, " + fits.stored() + " new");
            LOG.info("Generate finished");
            String coverLayout = season == null ? null : season.coverLayout();
            return new Generated(songs.size(), coverLayout, placed, slideCount);
        } finally {
            fits.save();
        }
    }

    public Generated generateMass(AppConfig config, MassRequest mass) throws IOException {
        BoldMarkup.requireDeckFonts();
        LOG.info("Found " + BoldMarkup.SEMIBOLD + ", " + BoldMarkup.TITLE + ", " + BoldMarkup.PLAYFAIR + ", " + BoldMarkup.PLAYFAIR_BOLD);
        DeckFile.ensureWritable(config.outputFile());
        LOG.info("Checked the output file is writable");
        List<SongCfg> songs = SongIndex.read(config.indexFile());
        LOG.info("Read index (" + songs.size() + " songs)");
        Path parent = config.outputFile().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        FitCache fits = FitCache.load(config.indexFile().resolveSibling("fit.json"));
        LOG.info("Loaded fit cache");
        try (InputStream in = Files.newInputStream(config.template4x3());
             XMLSlideShow templatePpt = new XMLSlideShow(in)) {
            LOG.info("Opened template");
            if (mass.cover() == null) {
                throw new IllegalArgumentException("Load the liturgy for " + mass.date() + " before generating.");
            }
            Season season = Season.resolve(mass.cover().season(), mass.cover().cover());
            if (mass.accent() != null && !mass.accent().equals(season.slideAccent())) {
                season = season.withHeaderScheme(mass.accent());
            }
            XSLFSlideLayout titleLayout = TemplateLayouts.findLayout(templatePpt, season.titleLayout());
            XSLFSlideLayout lyricLayout = TemplateLayouts.findLayout(templatePpt, TemplateLayouts.LYRICS);
            if (season.headerScheme() != null) {
                TemplateLayouts.setHeaderScheme(titleLayout, season.headerScheme());
                if (season.usesLentenAcclamation()) {
                    TemplateLayouts.setHeaderScheme(
                            TemplateLayouts.findLayout(templatePpt, TemplateLayouts.LOUVOR), season.headerScheme());
                }
            }
            LOG.info("Resolved layouts (" + season.coverLayout() + ", " + season.titleLayout() + ")");
            applyScriptureBackground(templatePpt, config.template4x3(), season);
            XSLFPictureData picture = mass.interval().needsImage()
                    ? templatePpt.addPicture(Files.readAllBytes(mass.interval().image()), mass.interval().pictureType())
                    : null;
            MassAssembler deck = new MassAssembler(
                    templatePpt, titleLayout, lyricLayout, fits, songs, mass.interval(), picture);
            XSLFSlide coverSlide = templatePpt.createSlide(TemplateLayouts.findLayout(templatePpt, season.coverLayout()));
            fillCover(coverSlide, mass.cover(), season);
            deck.slides++;
            deck.interval();
            deck.song(mass.entrada(), true);
            deck.song(mass.kyrie(), true);
            if (!season.omitsGloria()) {
                if (mass.gloria() != null) {
                    deck.song(mass.gloria(), true);
                } else {
                    deck.layouts(TemplateLayouts.GLORIA);
                    deck.interval();
                }
            }
            if (mass.liturgy() != null) {
                boolean primeira = filled(mass.liturgy().primeira());
                boolean segunda = filled(mass.liturgy().segunda());
                boolean salmo = mass.liturgy().refrao() != null && !mass.liturgy().refrao().isBlank();
                if (primeira) {
                    deck.reading(TemplateLayouts.LEITURA_1, mass.liturgy().primeira());
                }
                if (primeira || salmo) {
                    deck.refrain(mass.liturgy().refrao());
                }
                if (segunda) {
                    deck.reading(TemplateLayouts.LEITURA_2, mass.liturgy().segunda());
                }
            }
            if (season.usesLentenAcclamation()) {
                if (mass.liturgy() == null || mass.liturgy().louvor() == null || mass.liturgy().louvor().isBlank()) {
                    if (mass.liturgy() == null) {
                        LOG.warn("Quaresma has no verse for louvor. The refrain stays and Verso is left empty.");
                    }
                }
                deck.louvor(mass.liturgy() == null ? null : mass.liturgy().louvor());
            } else {
                deck.song(mass.aleluia(), false);
            }
            if (mass.liturgy() != null && mass.liturgy().evangelho() != null) {
                deck.gospel(mass.liturgy().evangelho());
            }
            deck.interval();
            deck.layouts(mass.longCreed() ? TemplateLayouts.CREDO_LONG : TemplateLayouts.CREDO_SHORT);
            deck.interval();
            if (mass.oracao() != null) {
                XSLFSlide oracao = TemplateLayouts.addSlide(templatePpt, TemplateLayouts.ORACAO);
                TemplateLayouts.setShapeText(oracao, "Letra", mass.oracao(), false);
                deck.slides++;
                deck.markContent();
                deck.interval();
            }
            deck.song(mass.dons(), true);
            deck.layouts(TemplateLayouts.PREPARACAO);
            deck.interval();
            deck.layouts(TemplateLayouts.DIALOGO);
            deck.interval();
            deck.song(mass.santo(), true);
            deck.layouts(TemplateLayouts.MEMORIAL);
            deck.interval();
            deck.song(mass.paiNosso(), true);
            deck.layouts(TemplateLayouts.ACLAMACAO);
            deck.interval();
            deck.song(mass.cordeiro(), true);
            deck.layouts(TemplateLayouts.CONVITE);
            deck.interval();
            deck.song(mass.comunhao(), true);
            deck.song(mass.acaoDeGracas(), true);
            deck.song(mass.finalSong(), false);
            TemplateLayouts.setNotes(coverSlide, notes(deck.placedSongs));
            DeckFile.write(templatePpt, config.outputFile());
            LOG.info("Wrote " + config.outputFile().toAbsolutePath() + " (" + deck.slides + " slides)");
            LOG.info("Fit cache: " + fits.reused() + " reused, " + fits.stored() + " new");
            LOG.info("Generate finished");
            return new Generated(songs.size(), season.coverLayout(), deck.placed, deck.slides);
        } finally {
            fits.save();
        }
    }

    private static boolean filled(LiturgyDay.Reading reading) {
        if (reading == null) {
            return false;
        }
        return (reading.referencia() != null && !reading.referencia().isBlank())
                || (reading.citacao() != null && !reading.citacao().isBlank());
    }

    private static final class MassAssembler {
        private final XMLSlideShow ppt;
        private final XSLFSlideLayout titleLayout;
        private final XSLFSlideLayout lyricLayout;
        private final FitCache fits;
        private final List<SongCfg> songs;
        private final Interval intervalChoice;
        private final XSLFPictureData picture;
        private final List<Picked> placedSongs = new ArrayList<>();
        private final List<Generated.Song> placed = new ArrayList<>();
        private int slides;
        private boolean intervalOpen;

        private MassAssembler(
                XMLSlideShow ppt,
                XSLFSlideLayout titleLayout,
                XSLFSlideLayout lyricLayout,
                FitCache fits,
                List<SongCfg> songs,
                Interval intervalChoice,
                XSLFPictureData picture) {
            this.ppt = ppt;
            this.titleLayout = titleLayout;
            this.lyricLayout = lyricLayout;
            this.fits = fits;
            this.songs = songs;
            this.intervalChoice = intervalChoice;
            this.picture = picture;
        }

        private void song(String bookId, boolean trailingInterval) throws IOException {
            if (bookId == null) {
                return;
            }
            Picked picked = new Picked(bookId, findBook(songs, bookId));
            slides += appendSong(ppt, titleLayout, lyricLayout, fits, picked, placed);
            placedSongs.add(picked);
            markContent();
            if (trailingInterval) {
                interval();
            }
        }

        private void reading(String layout, LiturgyDay.Reading reading) {
            citation(layout, reading.referencia(), reading.citacao());
        }

        private void gospel(LiturgyDay.Reading reading) {
            citation(TemplateLayouts.EVANGELHO, reading.referencia(), reading.citacao());
        }

        private void citation(String layout, String referencia, String citacao) {
            XSLFSlide slide = TemplateLayouts.addSlide(ppt, layout);
            TemplateLayouts.setShapeText(slide, "Referência", referencia, false);
            TemplateLayouts.setShapeText(slide, "Citação", quoted(citacao), true);
            slides++;
            markContent();
        }

        private void louvor(String verse) {
            XSLFSlide slide = TemplateLayouts.addSlide(ppt, TemplateLayouts.LOUVOR);
            String body = verse == null ? "" : verse;
            TemplateLayouts.setShapeText(slide, "Verso", body, !body.isEmpty());
            slides++;
            markContent();
        }

        private void refrain(String text) {
            XSLFSlide slide = TemplateLayouts.addSlide(ppt, TemplateLayouts.SALMO);
            String body = text == null || text.isBlank() ? "" : quoted(text);
            TemplateLayouts.setShapeText(slide, "Citação", body, !body.isEmpty());
            slides++;
            markContent();
        }

        private void layouts(String... names) {
            for (String name : names) {
                TemplateLayouts.addSlide(ppt, name);
                slides++;
            }
            markContent();
        }

        private void interval() throws IOException {
            if (intervalOpen) {
                return;
            }
            addInterval(ppt, intervalChoice, picture);
            slides++;
            intervalOpen = true;
        }

        private void markContent() {
            intervalOpen = false;
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

    private static String quoted(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("«") && trimmed.endsWith("»")) {
            return trimmed;
        }
        return "«" + trimmed + "»";
    }

    private static void applyScriptureBackground(XMLSlideShow ppt, Path template, Season season) throws IOException {
        Path image = template.toAbsolutePath().normalize().getParent()
                .resolve("../../assets")
                .resolve(season.scriptureBackground())
                .normalize();
        if (!Files.isRegularFile(image)) {
            LOG.info("Scripture background " + image.getFileName() + " is not in assets yet");
            return;
        }
        TemplateLayouts.setScriptureBackground(ppt, Files.readAllBytes(image));
        LOG.info("Scripture background " + image.getFileName());
    }

    private static void fillCover(XSLFSlide coverSlide, Cover cover, Season season) {
        TemplateLayouts.setShapeText(coverSlide, "Domingo", cover.domingo(), true);
        TemplateLayouts.setShapeText(coverSlide, "Frase", cover.quotedFrase(), true);
        TemplateLayouts.setShapeText(coverSlide, "Versiculo", cover.versiculo(), false);
        TemplateLayouts.setShapeText(coverSlide, "Tempo", tempoLine(cover, season), false);
    }

    private static String tempoLine(Cover cover, Season season) {
        if (cover.header() != null && !cover.header().isBlank()) {
            return cover.header().trim();
        }
        return season.tempo(cover.ano());
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
        int verseCount = song.content().size();
        placed.add(new Generated.Song(picked.key(), song.title().trim(), verseCount));
        XSLFSlide firstSlide = ppt.createSlide(titleLayout);
        TemplateLayouts.setShapeText(firstSlide, "Título", song.title(), fits, song.id(), 1);
        TemplateLayouts.setShapeText(firstSlide, "Letra", song.content().get(0), fits, song.id(), 1);
        for (int i = 1; i < verseCount; i++) {
            XSLFSlide slide = ppt.createSlide(lyricLayout);
            TemplateLayouts.setShapeText(slide, "Letra", song.content().get(i), fits, song.id(), i + 1);
        }
        LOG.info("Song " + picked.key() + ": " + song.title().trim() + " (" + verseCount + " verses)");
        return verseCount;
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
