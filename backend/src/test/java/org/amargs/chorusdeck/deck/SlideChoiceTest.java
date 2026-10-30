package org.amargs.chorusdeck.deck;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.amargs.chorusdeck.config.AppConfig;
import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.index.SongIndex;
import org.amargs.chorusdeck.web.SongDeckRequests;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.sl.usermodel.Placeholder;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlideChoiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void theSlidesShowOnlyTheChosenTitleLanguageAndVerses() throws Exception {
        Path dir = Files.createTempDirectory("slide-choice");
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(new SongCfg(
                1,
                "Pelos caminhos",
                List.of("um", "dois", "três"),
                "12",
                "Per viam",
                List.of("unus", "duo"))));
        String before = Files.readString(index);
        Path out = dir.resolve("canticos.pptx");
        AppConfig config = new AppConfig(Path.of("unused.pptx"), index, template(), out);
        SlideChoice choice = new SlideChoice("titleAlt", "contentAlt");
        new DeckService().generateSongs(
                config,
                new DeckService.Selection.Ids(List.of(1), List.of(choice)),
                null);

        try (XMLSlideShow ppt = open(out)) {
            assertEquals(2, ppt.getSlides().size());
            assertEquals("Pelos caminhos", textShape(ppt.getSlides().get(0), "Título").getText());
            assertEquals("unus", textShape(ppt.getSlides().get(0), "Letra").getText());
            assertTrue(slideText(ppt).contains("duo"));
            assertFalse(slideText(ppt).contains("Per viam"));
        }
        assertEquals(before, Files.readString(index));

        Path noted = dir.resolve("noted.pptx");
        new DeckService().generateSongs(
                new AppConfig(Path.of("unused.pptx"), index, template(), noted),
                new DeckService.Selection.Ids(List.of(1), List.of(choice)),
                new Cover("comum", null, "Domingo", "frase", "Jo 1, 1", "A"));
        try (XMLSlideShow ppt = open(noted)) {
            assertEquals("12 - Pelos caminhos (Per viam)", notes(ppt.getSlides().get(0)));
            assertEquals("Pelos caminhos", textShape(ppt.getSlides().get(2), "Título").getText());
        }

        Path massOut = dir.resolve("missa.pptx");
        Path yaml = dir.resolve("missa.yaml");
        Files.writeString(yaml, """
                season: comum
                ano: A
                domingo: Domingo
                frase: frase
                versiculo: Jo 1, 1
                output: missa.pptx
                songs:
                  entrada: "12"
                opcoes:
                  credo: breve
                  interval: vazio
                """);
        new DeckService().generateMass(
                new AppConfig(Path.of("unused.pptx"), index, template(), massOut),
                MassRequest.load(yaml),
                java.util.Map.of("entrada", new SlideChoice("title", "content")));
        try (XMLSlideShow ppt = open(massOut)) {
            String text = slideText(ppt);
            assertTrue(text.contains("Pelos caminhos"));
            assertTrue(text.contains("um"));
            assertTrue(text.contains("dois"));
            assertTrue(text.contains("três"));
            assertFalse(text.contains("Per viam"));
            assertFalse(text.contains("unus"));
        }
        assertEquals(before, Files.readString(index));
    }

    @Test
    void aSongRequestCarriesTheChoiceAndUsesTheDefaultLanguage() throws Exception {
        List<SongCfg> songs = List.of(new SongCfg(
                1, "Pelos caminhos", List.of("um", "dois"), "12", "Per viam", List.of("unus")));
        Path dir = Files.createTempDirectory("slide-choice");
        SongDeckRequests.Ready ready = SongDeckRequests.parse(JSON.readTree("""
                {"ids":[{"id":1,"title":"titleAlt","lyrics":"contentAlt"}],"output":"a.pptx"}
                """), songs, dir);
        assertEquals(List.of(1), ready.ids());
        assertEquals(List.of(new SlideChoice("titleAlt", "contentAlt")), ready.choices());

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> SongDeckRequests.parse(JSON.readTree("""
                {"ids":[1],"output":"a.pptx"}
                """), List.of(new SongCfg(1, "Só latim", List.of(), "A1", null, List.of("unus"))), dir));
        assertTrue(missing.getMessage().contains("no verses"));

        SongCfg latinFirst = new SongCfg(
                1, "Pelos caminhos", List.of("um", "dois"), "12", "Per viam", List.of("unus"), "pt", "la", "contentAlt");
        SongDeckRequests.Ready bare = SongDeckRequests.parse(JSON.readTree("""
                {"ids":[1],"output":"a.pptx"}
                """), List.of(latinFirst), dir);
        assertNull(bare.choices().get(0));
        assertEquals(List.of("unus"), latinFirst.prepared().content());
        assertEquals("Pelos caminhos", latinFirst.prepared().title());
        assertEquals("Per viam", latinFirst.prepared().titleAlt());
        assertEquals(List.of("um", "dois"), new SlideChoice(null, "content").show(latinFirst).content());
        assertEquals(List.of("unus"), new SlideChoice(null, null).show(latinFirst).content());
    }

    private static String notes(XSLFSlide slide) {
        XSLFNotes notes = slide.getNotes();
        if (notes == null) {
            throw new IllegalStateException("Notes placeholder not found");
        }
        for (XSLFShape shape : notes.getShapes()) {
            if (shape instanceof XSLFTextShape textShape && textShape.getTextType() == Placeholder.BODY) {
                return textShape.getText();
            }
        }
        throw new IllegalStateException("Notes placeholder not found");
    }

    private static String slideText(XMLSlideShow ppt) {
        StringBuilder text = new StringBuilder();
        for (XSLFSlide slide : ppt.getSlides()) {
            for (XSLFShape shape : slide.getShapes()) {
                if (shape instanceof XSLFTextShape textShape) {
                    text.append(textShape.getText()).append('\n');
                }
            }
        }
        return text.toString();
    }

    private static XSLFTextShape textShape(XSLFSlide slide, String name) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape textShape && name.equals(textShape.getShapeName())) {
                return textShape;
            }
        }
        throw new IllegalStateException("Shape not found: " + name);
    }

    private static XMLSlideShow open(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            return new XMLSlideShow(in);
        }
    }

    private static Path template() {
        Path direct = Path.of("template_4x3.pptx");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path data = Path.of("data", "template_4x3.pptx");
        if (Files.isRegularFile(data)) {
            return data;
        }
        Path nested = Path.of("backend", "data", "template_4x3.pptx");
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        throw new IllegalStateException("template_4x3.pptx not found");
    }
}
