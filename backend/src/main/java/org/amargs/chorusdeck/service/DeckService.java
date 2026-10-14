package org.amargs.chorusdeck.service;

import org.amargs.chorusdeck.config.AppConfig;
import org.amargs.chorusdeck.deck.DeckFile;
import org.amargs.chorusdeck.deck.TemplateLayouts;
import org.amargs.chorusdeck.index.SongIndex;
import org.amargs.chorusdeck.model.Cover;
import org.amargs.chorusdeck.model.Season;
import org.amargs.chorusdeck.model.SongCfg;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DeckService {

    public Generated generate(AppConfig config, List<Integer> ids, Cover cover) throws IOException {
        DeckFile.ensureWritable(config.outputFile());

        List<SongCfg> songs = SongIndex.read(config.indexFile());

        Path parent = config.outputFile().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (InputStream in = Files.newInputStream(config.template4x3());
             XMLSlideShow templatePpt = new XMLSlideShow(in)) {
            Season season = Season.resolve(cover.season(), cover.cover());
            XSLFSlideLayout coverLayout = TemplateLayouts.findLayout(templatePpt, season.coverLayout());
            XSLFSlideLayout titleLayout = TemplateLayouts.findLayout(templatePpt, season.titleLayout());
            XSLFSlideLayout lyricLayout = TemplateLayouts.findLayout(templatePpt, TemplateLayouts.LYRICS);
            if (season.headerScheme() != null) {
                TemplateLayouts.setHeaderScheme(titleLayout, season.headerScheme());
            }

            XSLFSlide coverSlide = templatePpt.createSlide(coverLayout);
            TemplateLayouts.setShapeText(coverSlide, "Domingo", cover.domingo());
            TemplateLayouts.setShapeText(coverSlide, "Frase", cover.quotedFrase());
            TemplateLayouts.setShapeText(coverSlide, "Versiculo", cover.versiculo());
            TemplateLayouts.setShapeText(coverSlide, "Tempo", season.tempo(cover.ano()));
            List<Generated.Song> placed = new ArrayList<>();
            int slideCount = 1;
            for (int id : ids) {
                SongCfg song = find(songs, id);
                if (song.content() == null || song.content().isEmpty()) {
                    throw new IllegalArgumentException("Song " + id + " has no verses.");
                }

                int verseCount = song.content().size();
                placed.add(new Generated.Song(id, song.title().trim(), verseCount));
                XSLFSlide firstSlide = templatePpt.createSlide(titleLayout);
                TemplateLayouts.setShapeText(firstSlide, "Título", song.title(), config.boldWeight());
                TemplateLayouts.setShapeText(firstSlide, "Letra", song.content().get(0), config.boldWeight());
                slideCount++;

                for (int i = 1; i < verseCount; i++) {
                    XSLFSlide slide = templatePpt.createSlide(lyricLayout);
                    TemplateLayouts.setShapeText(slide, "Letra", song.content().get(i), config.boldWeight());
                    slideCount++;
                }
            }

            DeckFile.write(templatePpt, config.outputFile());
            return new Generated(songs.size(), season.coverLayout(), placed, slideCount);
        }
    }

    public record Generated(int loadedSongs, String coverLayout, List<Song> songs, int slides) {
        public record Song(int id, String title, int verses) {
        }
    }

    private static SongCfg find(List<SongCfg> songs, int id) {
        for (SongCfg song : songs) {
            if (song.id() == id) {
                return song;
            }
        }
        throw new IllegalArgumentException("Unknown song id: " + id);
    }
}
