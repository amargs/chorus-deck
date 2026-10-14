package org.amargs.chorusdeck;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

public final class DeckGenerator {

    public void generate(AppConfig config, List<Integer> ids, Cover cover) throws IOException {
        ensureOutputWritable(config.outputFile());

        System.out.println("Reading " + config.indexFile().toAbsolutePath());
        List<SongCfg> songs;
        try (InputStream in = Files.newInputStream(config.indexFile())) {
            songs = new ObjectMapper().readValue(in, new TypeReference<List<SongCfg>>() {});
        }
        System.out.println("Loaded " + songs.size() + " songs");

        Path parent = config.outputFile().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        System.out.println("Opening " + config.template4x3().toAbsolutePath());
        try (InputStream in = Files.newInputStream(config.template4x3());
             XMLSlideShow templatePpt = new XMLSlideShow(in)) {
            Season season = Season.resolve(cover.season(), cover.cover());
            XSLFSlideLayout coverLayout = TemplateLayouts.findLayout(templatePpt, season.coverLayout());
            XSLFSlideLayout titleLayout = TemplateLayouts.findLayout(templatePpt, season.titleLayout());
            XSLFSlideLayout lyricLayout = TemplateLayouts.findLayout(templatePpt, TemplateLayouts.LYRICS);
            if (season.headerScheme() != null) {
                TemplateLayouts.setHeaderScheme(titleLayout, season.headerScheme());
            }

            System.out.println("Cover: " + season.coverLayout());
            XSLFSlide coverSlide = templatePpt.createSlide(coverLayout);
            TemplateLayouts.setShapeText(coverSlide, "Domingo", cover.domingo());
            TemplateLayouts.setShapeText(coverSlide, "Frase", quote(cover.frase()));
            TemplateLayouts.setShapeText(coverSlide, "Versiculo", cover.versiculo());
            TemplateLayouts.setShapeText(coverSlide, "Tempo", season.tempo(cover.ano()));
            int slideNumber = 1;
            for (int id : ids) {
                if (id < 1 || id > songs.size()) {
                    throw new IllegalArgumentException("Unknown song id: " + id);
                }
                SongCfg song = songs.get(id - 1);
                if (song.content() == null || song.content().isEmpty()) {
                    throw new IllegalArgumentException("Song " + id + " has no verses.");
                }

                int verseCount = song.content().size();
                System.out.println("Song " + id + ": " + song.title().trim() + " (" + verseCount + " verses)");

                System.out.println("  Verse 1 of " + verseCount);
                XSLFSlide firstSlide = templatePpt.createSlide(titleLayout);
                TemplateLayouts.setShapeText(firstSlide, "Título", song.title(), config.boldWeight());
                TemplateLayouts.setShapeText(firstSlide, "Letra", song.content().get(0), config.boldWeight());
                slideNumber++;

                for (int i = 1; i < verseCount; i++) {
                    System.out.println("  Verse " + (i + 1) + " of " + verseCount);
                    XSLFSlide slide = templatePpt.createSlide(lyricLayout);
                    TemplateLayouts.setShapeText(slide, "Letra", song.content().get(i), config.boldWeight());
                    slideNumber++;
                }
            }

            System.out.println("Writing " + config.outputFile().toAbsolutePath() + " (" + slideNumber + " slides)");
            writeShow(templatePpt, config.outputFile());
            System.out.println("Done");
        }
    }

    public record Cover(String season, String cover, String domingo, String frase, String versiculo, String ano) {
    }

    static String quote(String frase) {
        String text = frase == null ? "" : frase.trim();
        if (text.startsWith("«") && text.endsWith("»")) {
            return text;
        }
        return "«" + text + "»";
    }

    static void ensureOutputWritable(Path outputFile) throws IOException {
        if (Files.notExists(outputFile)) {
            return;
        }
        try (FileChannel probe = FileChannel.open(outputFile, StandardOpenOption.WRITE)) {
            // Opening for write fails when PowerPoint already has the deck open.
        } catch (FileSystemException e) {
            if (outputIsLocked(e)) {
                throw outputOpen(outputFile, e);
            }
            throw e;
        }
    }

    private static void writeShow(XMLSlideShow templatePpt, Path outputFile) throws IOException {
        try (OutputStream out = Files.newOutputStream(outputFile)) {
            templatePpt.write(out);
        } catch (FileSystemException e) {
            if (outputIsLocked(e)) {
                throw outputOpen(outputFile, e);
            }
            throw e;
        }
    }

    private static IllegalArgumentException outputOpen(Path outputFile, Exception cause) {
        return new IllegalArgumentException(
                "Can't write " + outputFile.toAbsolutePath() + " because it is open. Close it and run generate again.",
                cause);
    }

    static boolean outputIsLocked(FileSystemException e) {
        if (e instanceof NoSuchFileException) {
            return false;
        }
        if (e instanceof AccessDeniedException) {
            return true;
        }
        String reason = e.getReason();
        if (reason == null) {
            return false;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.contains("used by another process")
                || lower.contains("access is denied")
                || lower.contains("text file busy")
                || lower.contains("device or resource busy");
    }
}
