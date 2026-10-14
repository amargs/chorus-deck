package org.amargs.chorusdeck;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class DeckGenerator {

    public void generate(AppConfig config, List<Integer> ids) throws IOException {
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
            XSLFSlideLayout titleLayout = TemplateLayouts.findLayout(templatePpt, TemplateLayouts.ORDINARY_TITLE);
            XSLFSlideLayout lyricLayout = TemplateLayouts.findLayout(templatePpt, TemplateLayouts.LYRICS);

            int slideNumber = 0;
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
                TemplateLayouts.setShapeText(firstSlide, "Título", song.title());
                TemplateLayouts.setShapeText(firstSlide, "Letra", song.content().get(0));
                slideNumber++;

                for (int i = 1; i < verseCount; i++) {
                    System.out.println("  Verse " + (i + 1) + " of " + verseCount);
                    XSLFSlide slide = templatePpt.createSlide(lyricLayout);
                    TemplateLayouts.setShapeText(slide, "Letra", song.content().get(i));
                    slideNumber++;
                }
            }

            System.out.println("Writing " + config.outputFile().toAbsolutePath() + " (" + slideNumber + " slides)");
            writeShow(templatePpt, config.outputFile());
            System.out.println("Done");
        }
    }

    private static void writeShow(XMLSlideShow templatePpt, Path outputFile) throws IOException {
        try (OutputStream out = Files.newOutputStream(outputFile)) {
            templatePpt.write(out);
        } catch (FileSystemException e) {
            if (outputIsLocked(e)) {
                throw new IllegalArgumentException(
                        "Can't write " + outputFile.toAbsolutePath() + " because it is open. Close it and run generate again.",
                        e);
            }
            throw e;
        }
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
