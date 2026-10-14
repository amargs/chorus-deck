package org.amargs.chorusdeck;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateLayoutsTest {

    private static final List<String> REQUIRED_LAYOUTS = List.of(
            "Capa - Tempo Comum",
            "Capa - Tempo de Advento",
            "Capa - Tempo de Natal",
            "Capa - Tempo de Quaresma",
            "Capa - Domingo III Advento",
            "Capa - Domingo IV Advento",
            "Título e letra - Tempo comum",
            "Título e letra - Tempo Advento",
            "Letra");

    @Test
    void templateDeclaresStage1Layouts() throws Exception {
        try (XMLSlideShow ppt = openTemplate()) {
            for (String name : REQUIRED_LAYOUTS) {
                assertEquals(name, TemplateLayouts.findLayout(ppt, name).getName());
            }
            assertThrows(IllegalArgumentException.class, () -> TemplateLayouts.findLayout(ppt, "Not a layout"));
        }
    }

    @Test
    void lyricPlaceholderKeepsLayoutFontAndReplacesPrompt() throws Exception {
        try (XMLSlideShow ppt = openTemplate()) {
            XSLFSlide slide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.ORDINARY_TITLE));
            TemplateLayouts.setShapeText(slide, "Título", "A minh’alma descansa em paz");
            TemplateLayouts.setShapeText(slide, "Letra", "linha um\nlinha dois");

            XSLFTextShape lyrics = textShape(slide, "Letra");
            assertEquals("linha um\nlinha dois", lyrics.getText());
            assertFalse(lyrics.getText().contains("Música"));
            assertEquals("A minh’alma descansa em paz", textShape(slide, "Título").getText());
            assertTrue(slide.getSlideLayout().getShapes().stream().anyMatch(shape -> "Header".equals(shape.getShapeName())));

            CTShape ctShape = (CTShape) lyrics.getXmlObject();
            assertEquals(6000, ctShape.getTxBody().getLstStyle().getLvl1PPr().getDefRPr().getSz());
            assertEquals(2, ctShape.getTxBody().sizeOfPArray());
            assertEquals(6_000, fontSize(lyrics));
        }
    }

    @Test
    void emphasisUsesSemiBoldAndBoldIsTheFallback() throws Exception {
        try (XMLSlideShow ppt = openTemplate()) {
            XSLFSlide slide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.LYRICS));
            TemplateLayouts.setShapeText(slide, "Letra", "**Cântico:**\nverso");
            XSLFTextShape lyrics = textShape(slide, "Letra");
            assertEquals("Cântico:\nverso", lyrics.getText());
            XSLFTextRun emphasis = lyrics.getTextParagraphs().get(0).getTextRuns().get(0);
            assertFalse(emphasis.isBold());
            assertEquals("IBM Plex Sans SemiBold", emphasis.getFontFamily());
            XSLFTextRun verse = lyrics.getTextParagraphs().get(1).getTextRuns().get(0);
            assertFalse(verse.isBold());
            assertEquals("verso", verse.getRawText());

            XSLFSlide boldSlide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.LYRICS));
            TemplateLayouts.setShapeText(boldSlide, "Letra", "**Cântico:**", "bold");
            assertTrue(textShape(boldSlide, "Letra").getTextParagraphs().get(0).getTextRuns().get(0).isBold());

            XSLFSlide fallback = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.LYRICS));
            TemplateLayouts.setShapeText(fallback, "Letra", "**Cântico:**", "notafontweight");
            assertTrue(textShape(fallback, "Letra").getTextParagraphs().get(0).getTextRuns().get(0).isBold());
        }
    }

    @Test
    void boldLinesShrinkMoreThanTheSamePlainLines() throws Exception {
        String line = "nunca estou sozinha.";
        String plain = (line + "\n").repeat(4);
        String bold = ("**" + line + "**\n").repeat(4);
        try (XMLSlideShow ppt = openTemplate()) {
            XSLFSlide plainSlide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.LYRICS));
            TemplateLayouts.setShapeText(plainSlide, "Letra", plain);
            XSLFSlide boldSlide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.LYRICS));
            TemplateLayouts.setShapeText(boldSlide, "Letra", bold);

            int plainSize = fontSize(textShape(plainSlide, "Letra"));
            int boldSize = fontSize(textShape(boldSlide, "Letra"));
            assertTrue(boldSize < plainSize, "plain=" + plainSize + " bold=" + boldSize);
            XSLFTextRun emphasis = textShape(boldSlide, "Letra").getTextParagraphs().get(0).getTextRuns().get(0);
            assertFalse(emphasis.isBold());
            assertEquals("IBM Plex Sans SemiBold", emphasis.getFontFamily());
        }
    }

    @Test
    void song98FirstVerseShrinksBelow48() throws Exception {
        try (XMLSlideShow ppt = openTemplate()) {
            XSLFSlide slide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.ORDINARY_TITLE));
            String verse = "Se me envolve a noite escura\ne caminho sobre abismos de amargura,\nnada temo, porque a luz está comigo. \nNada temo, porque…";
            TemplateLayouts.setShapeText(slide, "Letra", verse);
            int hundredths = fontSize(textShape(slide, "Letra"));
            assertTrue(hundredths < 4_800, "sz=" + hundredths);
            assertTrue(hundredths > 2_000, "sz=" + hundredths);
        }
    }

    @Test
    void chorusStaysInsideTheLyricBox() throws Exception {
        try (XMLSlideShow ppt = openTemplate()) {
            XSLFSlide slide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.LYRICS));
            String verse = "Nada temo porque nunca estou sozinha\nNada temo, tenho Deus no meu caminho (x2)\nNada temo…";
            TemplateLayouts.setShapeText(slide, "Letra", verse);
            int hundredths = fontSize(textShape(slide, "Letra"));
            assertTrue(hundredths < 5_800, "sz=" + hundredths);
        }
    }

    @Test
    void continuationSlideShrinksALittleFurther() throws Exception {
        try (XMLSlideShow ppt = openTemplate()) {
            XSLFSlide slide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.LYRICS));
            String verse = "Se me colhe a tempestade\ne Jesus vai a dormir na minha barca,\nnada temo porque a Paz está comigo. \nNada temo porque…";
            TemplateLayouts.setShapeText(slide, "Letra", verse);
            int hundredths = fontSize(textShape(slide, "Letra"));
            assertTrue(hundredths < 5_200, "sz=" + hundredths);
            assertTrue(hundredths > 2_000, "sz=" + hundredths);
        }
    }

    @Test
    void longLyricsShrinkInsideThePlaceholder() throws Exception {
        try (XMLSlideShow ppt = openTemplate()) {
            XSLFSlide slide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.ORDINARY_TITLE));
            String verse = "Sentei-me ali à espera, de um abraço doce\n".repeat(12);
            TemplateLayouts.setShapeText(slide, "Letra", verse);
            TemplateLayouts.setShapeText(slide, "Título", "A festa da música");

            int lyricsSize = fontSize(textShape(slide, "Letra"));
            assertTrue(lyricsSize < 6_000, "sz=" + lyricsSize);
            assertTrue(lyricsSize >= 800, "sz=" + lyricsSize);
            assertEquals(3_400, fontSize(textShape(slide, "Título")));
        }
    }

    @Test
    void generateRendersSongsOnNamedLayouts() throws Exception {
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        AppConfig config = new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out);
        new DeckGenerator().generate(config, List.of(1, 2));

        try (XMLSlideShow ppt = open(out)) {
            assertEquals(5, ppt.getSlides().size());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(0).getSlideLayout().getName());
            assertEquals(TemplateLayouts.LYRICS, ppt.getSlides().get(1).getSlideLayout().getName());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(4).getSlideLayout().getName());

            String lyrics = textShape(ppt.getSlides().get(0), "Letra").getText();
            assertTrue(lyrics.contains("Sentei-me ali à espera"));
            assertFalse(lyrics.contains("Música"));
            assertEquals("A festa da música", textShape(ppt.getSlides().get(0), "Título").getText());
            assertEquals("A minh’alma descansa em paz", textShape(ppt.getSlides().get(4), "Título").getText());
        }
    }

    @Test
    void generateRejectsUnknownIdAndEmptySong() throws Exception {
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        Path index = Files.createTempFile("chorus-deck", ".json");
        Files.writeString(index, "[{\"id\":1,\"title\":\"Vazio\",\"content\":[]}]", UTF_8);
        AppConfig config = new AppConfig(Path.of("unused.pptx"), index, template(), out);

        assertThrows(IllegalArgumentException.class, () -> new DeckGenerator().generate(config, List.of(1)));
        Files.writeString(index, "[{\"id\":1,\"title\":\"Um\",\"content\":[\"verso\"]}]", UTF_8);
        assertThrows(IllegalArgumentException.class, () -> new DeckGenerator().generate(config, List.of(2)));
    }

    private static XMLSlideShow openTemplate() throws Exception {
        return open(template());
    }

    private static XMLSlideShow open(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            return new XMLSlideShow(in);
        }
    }

    private static Path indexFile() {
        Path direct = Path.of("index.json");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path nested = Path.of("backend", "index.json");
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        throw new IllegalStateException("index.json not found");
    }

    private static Path template() {
        Path direct = Path.of("template_4x3.pptx");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path nested = Path.of("backend", "template_4x3.pptx");
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        throw new IllegalStateException("template_4x3.pptx not found");
    }

    private static int fontSize(XSLFTextShape shape) {
        CTShape ctShape = (CTShape) shape.getXmlObject();
        Object size = ctShape.getTxBody().getLstStyle().getLvl1PPr().getDefRPr().getSz();
        if (size instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(size.toString());
    }

    private static XSLFTextShape textShape(XSLFSlide slide, String name) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape textShape && name.equals(textShape.getShapeName())) {
                return textShape;
            }
        }
        throw new IllegalStateException("Shape not found: " + name);
    }
}
