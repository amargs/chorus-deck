package org.amargs.chorusdeck.deck;

import org.amargs.chorusdeck.config.AppConfig;
import org.amargs.chorusdeck.liturgy.LiturgyDay;
import org.amargs.chorusdeck.text.FitCache;

import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateLayoutsTest {

    private static final List<String> REQUIRED_LAYOUTS = List.of(
            "Capa - Tempo Comum",
            "Capa - Advento",
            "Capa - Natal",
            "Capa - Quaresma",
            "Capa - Domingo III Advento",
            "Capa - Domingo IV Quaresma",
            "Capa - Ramos",
            "Capa - Páscoa",
            "Capa - Pentecostes",
            "Capa - Solenidade",
            "Capa - Solenidade Pedro Paulo",
            TemplateLayouts.ORDINARY_TITLE,
            TemplateLayouts.FESTIVE_TITLE,
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
    void ordinaryTitleKeepsSmallCapsAndFollowsTheHeaderAccent() throws Exception {
        try (XMLSlideShow ppt = openTemplate()) {
            XSLFSlideLayout layout = TemplateLayouts.findLayout(ppt, TemplateLayouts.ORDINARY_TITLE);
            TemplateLayouts.setHeaderScheme(layout, "accent5");
            XSLFSlide slide = ppt.createSlide(layout);
            TemplateLayouts.setShapeText(slide, "Título", "A minh’alma descansa em paz");

            var title = ((CTShape) textShape(slide, "Título").getXmlObject()).getTxBody().getLstStyle().getLvl1PPr().getDefRPr();
            assertEquals("small", title.getCap().toString());
            assertEquals("Livvic SemiBold", title.getLatin().getTypeface());
            String fill = title.getSolidFill().getSchemeClr().xmlText();
            assertTrue(fill.contains("accent5"), fill);
            assertTrue(fill.contains("lumMod"), fill);
            assertTrue(fill.contains("20000"), fill);
            assertTrue(fill.contains("lumOff"), fill);
            assertTrue(fill.contains("80000"), fill);
            assertEquals("accent5", headerScheme(ppt));
        }
    }

    @Test
    void festiveTitleKeepsGoldSmallCaps() throws Exception {
        try (XMLSlideShow ppt = openTemplate()) {
            XSLFSlide slide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.FESTIVE_TITLE));
            TemplateLayouts.setShapeText(slide, "Título", "Adoramus te");

            var title = ((CTShape) textShape(slide, "Título").getXmlObject()).getTxBody().getLstStyle().getLvl1PPr().getDefRPr();
            assertEquals("small", title.getCap().toString());
            assertEquals("Livvic SemiBold", title.getLatin().getTypeface());
            String fill = title.getSolidFill().getSchemeClr().xmlText();
            assertTrue(fill.contains("accent4"), fill);
            assertFalse(fill.contains("lumMod"), fill);
        }
    }

    @Test
    void festiveTitleSlideHasTitleShape() throws Exception {
        try (XMLSlideShow ppt = openTemplate()) {
            XSLFSlide slide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.FESTIVE_TITLE));
            TemplateLayouts.setShapeText(slide, "Título", "Adoramus te");
            assertEquals("Adoramus te", textShape(slide, "Título").getText());
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
            assertEquals(5400, ctShape.getTxBody().getLstStyle().getLvl1PPr().getDefRPr().getSz());
            assertEquals(2, ctShape.getTxBody().sizeOfPArray());
            assertEquals(5_400, fontSize(lyrics));
        }
    }

    @Test
    void emphasisUsesMedium() throws Exception {
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
        }
    }

    @Test
    void boldLinesShrinkMoreThanTheSamePlainLines() throws Exception {
        String line = "nunca estou sozinha ".repeat(12).trim();
        String plain = line;
        String bold = "**" + line + "**";
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
    void fittedSizeIsReusedForTheSameVerseAndLayout() throws Exception {
        Path file = Files.createTempFile("chorus-deck-fit", ".json");
        String verse = "Se me envolve a noite escura\ne caminho sobre abismos de amargura,\nnada temo, porque a luz está comigo. \nNada temo, porque…";
        FitCache first = FitCache.load(file);
        int sized;
        try (XMLSlideShow ppt = openTemplate()) {
            XSLFSlide slide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.ORDINARY_TITLE));
            TemplateLayouts.setShapeText(slide, "Letra", verse, first, 8, 1);
            sized = fontSize(textShape(slide, "Letra"));
        }
        assertEquals(1, first.stored());
        first.save();

        FitCache second = FitCache.load(file);
        try (XMLSlideShow ppt = openTemplate()) {
            XSLFSlide slide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.ORDINARY_TITLE));
            TemplateLayouts.setShapeText(slide, "Letra", verse, second, 8, 1);
            assertEquals(sized, fontSize(textShape(slide, "Letra")));
        }
        assertEquals(1, second.reused());
        assertEquals(0, second.stored());
    }

    @Test
    void generateRendersSongsOnNamedLayouts() throws Exception {
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        AppConfig config = new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out);
        new DeckService().generate(config, new DeckService.Selection.Ids(List.of(1, 2)), cover("comum"));

        try (XMLSlideShow ppt = open(out)) {
            assertEquals(6, ppt.getSlides().size());
            assertEquals("Capa - Tempo Comum", ppt.getSlides().get(0).getSlideLayout().getName());
            assertEquals("XXVIII Domingo", textShape(ppt.getSlides().get(0), "Domingo").getText());
            assertEquals("«frase»", textShape(ppt.getSlides().get(0), "Frase").getText());
            XSLFTextShape domingoShape = textShape(ppt.getSlides().get(0), "Domingo");
            XSLFTextRun domingo = domingoShape.getTextParagraphs().get(0).getTextRuns().get(0);
            assertTrue(domingo.isBold());
            assertEquals("Playfair Display", domingo.getFontFamily());
            assertEquals(6_600, fontSize(domingoShape));
            assertEquals("Tempo Comum · Ano A", textShape(ppt.getSlides().get(0), "Tempo").getText());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(1).getSlideLayout().getName());
            assertEquals(TemplateLayouts.LYRICS, ppt.getSlides().get(2).getSlideLayout().getName());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(5).getSlideLayout().getName());

            String lyrics = textShape(ppt.getSlides().get(1), "Letra").getText();
            assertTrue(lyrics.contains("Sentei-me ali à espera"));
            assertFalse(lyrics.contains("Música"));
            assertEquals("A festa da música", textShape(ppt.getSlides().get(1), "Título").getText());
            assertEquals("A minh’alma descansa em paz", textShape(ppt.getSlides().get(5), "Título").getText());
        }
    }

    @Test
    void coverTitleShrinksWhenTheFreeTextDoesNotFit() throws Exception {
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        AppConfig config = new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out);
        Cover longTitle = new Cover(
                "comum",
                null,
                "Oração de Taizé, Vigília do Crisma na paróquia",
                "frase",
                "Mt 25, 14-30",
                "A");
        new DeckService().generate(config, new DeckService.Selection.Ids(List.of(1)), longTitle);

        try (XMLSlideShow ppt = open(out)) {
            XSLFTextShape domingo = textShape(ppt.getSlides().get(0), "Domingo");
            assertEquals(longTitle.domingo(), domingo.getText());
            assertTrue(fontSize(domingo) < 6_600);
        }
    }

    @Test
    void songDeckPutsABlackSlideBetweenSongsAndNotesOnTheCover() throws Exception {
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        AppConfig config = new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out);
        new DeckService().generateSongs(config, new DeckService.Selection.Ids(List.of(1, 2)), cover("comum"));

        try (XMLSlideShow ppt = open(out)) {
            assertEquals(8, ppt.getSlides().size());
            assertEquals("Capa - Tempo Comum", ppt.getSlides().get(0).getSlideLayout().getName());
            assertEquals(TemplateLayouts.INTERVAL_BLANK, ppt.getSlides().get(1).getSlideLayout().getName());
            assertTrue(ppt.getSlides().get(1).getShapes().isEmpty());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(2).getSlideLayout().getName());
            assertEquals(TemplateLayouts.INTERVAL_BLANK, ppt.getSlides().get(6).getSlideLayout().getName());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(7).getSlideLayout().getName());
            assertEquals("2 - A festa da música\n1 - A minh’alma descansa em paz", notes(ppt.getSlides().get(0)));
            for (XSLFTextParagraph paragraph : notesBody(ppt.getSlides().get(0)).getTextParagraphs()) {
                assertEquals(TextParagraph.TextAlign.CENTER, paragraph.getTextAlign());
            }
        }
    }

    @Test
    void songDeckWithoutACoverHasNoTrailingInterval() throws Exception {
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        AppConfig config = new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out);
        new DeckService().generateSongs(config, new DeckService.Selection.Ids(List.of(1, 2)), null);

        try (XMLSlideShow ppt = open(out)) {
            assertEquals(6, ppt.getSlides().size());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(0).getSlideLayout().getName());
            assertEquals(TemplateLayouts.INTERVAL_BLANK, ppt.getSlides().get(4).getSlideLayout().getName());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(5).getSlideLayout().getName());
        }
    }

    @Test
    void dropboxLogoIsAcceptedAsAnIntervalImage() {
        Path logo = Path.of("..", "assets", "logo_comunhao.png");
        if (!Files.exists(logo)) {
            logo = Path.of("assets", "logo_comunhao.png");
        }
        Interval interval = Interval.parse("imagem", logo, null);
        assertEquals(Interval.Kind.IMAGEM, interval.kind());
    }

    @Test
    void deckFileNamesTheOutputFromTheSundayAndYear() throws Exception {
        Path file = massFile("""
                season: comum
                ano: A
                domingo: Domingo XXIX
                frase: frase
                versiculo: Mt 1, 1
                opcoes:
                  credo: longo
                """);
        assertEquals(file.getParent().resolve("Domingo XXIX Tempo Comum - Ano A.pptx"), DeckSpec.load(file).output());
        assertEquals("2026-08-15 Assunção da Virgem Santa Maria - Ano A.pptx",
                DeckSpec.fileName(LocalDate.of(2026, 8, 15), "Assunção da Virgem Santa Maria", "comum", "A"));
        assertEquals("2026-10-19 2ªf da semana XXIX - Ano A.pptx",
                DeckSpec.fileName(LocalDate.of(2026, 10, 19), "2ªf da semana XXIX", "comum", "A"));

        Path named = massFile("""
                season: comum
                ano: B
                domingo: Domingo III
                frase: frase
                versiculo: Mt 1, 1
                output: data/especial
                opcoes:
                  credo: breve
                """);
        assertEquals(named.getParent().resolve("data").resolve("especial.pptx"), DeckSpec.load(named).output());
    }

    @Test
    void aDatedMassDefersTheSundayUntilThePageIsRead() throws Exception {
        Path file = massFile("""
                date: 2026-10-18
                songs:
                  entrada: "2"
                opcoes:
                  credo: longo
                """);
        DeckSpec.Mass spec = (DeckSpec.Mass) DeckSpec.load(file);
        assertNull(spec.output());
        assertNull(spec.request().cover());
        assertEquals(LocalDate.of(2026, 10, 18), spec.request().date());

        LiturgyDay day = new LiturgyDay(
                LocalDate.of(2026, 10, 18),
                "Domingo XXIX",
                "comum",
                null,
                "A",
                new LiturgyDay.Reading("Is 45, 1.4-6", "Tomei Ciro"),
                new LiturgyDay.Reading("1Ts 1, 1-5b", "Recordamos a vossa fé"),
                "Aclamai o Senhor.",
                new LiturgyDay.Reading("Mt 22, 15-21", "Dai a César o que é de César e a Deus o que é de Deus"),
                null);
        MassRequest ready = spec.request().withLiturgy(day);
        assertEquals("«Dai a César o que é de César e a Deus o que é de Deus»", ready.cover().quotedFrase());
        assertEquals(file.getParent().resolve("2026-10-18 Domingo XXIX Tempo Comum - Ano A.pptx"),
                file.getParent().resolve(DeckSpec.fileName(
                        ready.date(), ready.cover().domingo(), ready.cover().season(), ready.cover().ano())));

        IllegalArgumentException bad = assertThrows(IllegalArgumentException.class, () -> MassRequest.load(massFile("""
                date: 18/10/2026
                opcoes:
                  credo: longo
                """)));
        assertEquals("date must be YYYY-MM-DD.", bad.getMessage());
    }

    @Test
    void aMassFileFillsSentencesTheSundayFileLeftBlank() throws Exception {
        Path yaml = massFile("""
                date: 2026-12-06
                leituras:
                  primeira: "Preparai o caminho do Senhor"
                  salmo: "Mostrai-nos, Senhor, o vosso amor"
                  evangelho: "Arrependei-vos, porque está perto o reino de Deus"
                opcoes:
                  credo: longo
                """);
        LiturgyDay blank = new LiturgyDay(
                LocalDate.of(2026, 12, 6),
                "Domingo II",
                "advento",
                null,
                "B",
                new LiturgyDay.Reading("Is 40, 1-5, 9-11", null),
                new LiturgyDay.Reading("2Pe 3, 8-14", null),
                null,
                new LiturgyDay.Reading("Mc 1, 1-8", null),
                null);
        MassRequest ready = MassRequest.load(yaml).withLiturgy(blank);
        assertEquals("Preparai o caminho do Senhor", ready.liturgy().primeira().citacao());
        assertNull(ready.liturgy().segunda().citacao());
        assertEquals("Mostrai-nos, Senhor, o vosso amor", ready.liturgy().refrao());
        assertEquals("Arrependei-vos, porque está perto o reino de Deus", ready.cover().frase());

        LiturgyDay stored = new LiturgyDay(
                blank.date(), blank.domingo(), blank.seasonKey(), blank.coverLayout(), blank.ano(),
                new LiturgyDay.Reading("Is 40, 1-5, 9-11", "Consolai o meu povo"),
                new LiturgyDay.Reading("2Pe 3, 8-14", "Esperamos novos céus"),
                "Já guardado",
                new LiturgyDay.Reading("Mc 1, 1-8", "Está cumprido"),
                "Verso guardado");
        MassRequest overridden = MassRequest.load(yaml).withLiturgy(stored);
        assertEquals("Preparai o caminho do Senhor", overridden.liturgy().primeira().citacao());
        assertEquals("Esperamos novos céus", overridden.liturgy().segunda().citacao());
        assertEquals("Mostrai-nos, Senhor, o vosso amor", overridden.liturgy().refrao());
        assertEquals("Arrependei-vos, porque está perto o reino de Deus", overridden.cover().frase());
    }

    @Test
    void blankSentencesStillGenerateCitationMarksAndAnEmptyRefrain() throws Exception {
        Path yaml = massFile("""
                date: 2026-12-06
                opcoes:
                  credo: longo
                """);
        LiturgyDay blank = new LiturgyDay(
                LocalDate.of(2026, 12, 6),
                "Domingo II",
                "advento",
                null,
                "B",
                new LiturgyDay.Reading("Is 40, 1-5, 9-11", null),
                new LiturgyDay.Reading("2Pe 3, 8-14", null),
                null,
                new LiturgyDay.Reading("Mc 1, 1-8", null),
                null);
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        new DeckService().generateMass(
                new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out),
                MassRequest.load(yaml).withLiturgy(blank));

        try (XMLSlideShow ppt = open(out)) {
            List<String> layouts = layoutNames(ppt);
            int first = layouts.indexOf(TemplateLayouts.LEITURA_1);
            int psalm = layouts.indexOf(TemplateLayouts.SALMO);
            int gospel = layouts.indexOf(TemplateLayouts.EVANGELHO);
            assertEquals("«»", textShape(ppt.getSlides().get(first), "Citação").getText());
            assertEquals("", textShape(ppt.getSlides().get(psalm), "Citação").getText());
            assertEquals("«»", textShape(ppt.getSlides().get(gospel), "Citação").getText());
            assertEquals("«»", textShape(ppt.getSlides().get(0), "Frase").getText());
            assertFalse(layouts.contains(TemplateLayouts.GLORIA_1));
        }
    }

    @Test
    void quaresmaReplacesTheAlleluiaWithLouvor() throws Exception {
        try (XMLSlideShow templatePpt = new XMLSlideShow(Files.newInputStream(template()))) {
            Assumptions.assumeTrue(hasLayout(templatePpt, TemplateLayouts.LOUVOR),
                    "Layout Louvor a vós is not in the template yet");
        }
        Path yaml = massFile("""
                date: 2026-03-01
                songs:
                  aleluia: "2"
                opcoes:
                  credo: longo
                """);
        LiturgyDay day = new LiturgyDay(
                LocalDate.of(2026, 3, 1),
                "Domingo III",
                "quaresma",
                null,
                "B",
                new LiturgyDay.Reading("Ex 17, 3-7", "Dai-nos água"),
                new LiturgyDay.Reading("Rom 5, 1-2", "A paz"),
                "Aclamai",
                new LiturgyDay.Reading("Jo 4, 5-42", "Água viva"),
                "Deus amou tanto o mundo");
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        new DeckService().generateMass(
                new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out),
                MassRequest.load(yaml).withLiturgy(day));
        try (XMLSlideShow ppt = open(out)) {
            List<String> layouts = layoutNames(ppt);
            int louvor = layouts.indexOf(TemplateLayouts.LOUVOR);
            int gospel = layouts.indexOf(TemplateLayouts.EVANGELHO);
            assertTrue(louvor >= 0);
            assertTrue(louvor < gospel);
            assertEquals("Deus amou tanto o mundo", textShape(ppt.getSlides().get(louvor), "Verso").getText());
            assertFalse(notes(ppt.getSlides().get(0)).contains("2 -"));
            assertFalse(layouts.contains(TemplateLayouts.GLORIA_1));
        }
    }

    @Test
    void datedMassFillsTheScriptureSlidesInMassOrder() throws Exception {
        Path yaml = massFile("""
                date: 2026-10-18
                opcoes:
                  credo: longo
                """);
        LiturgyDay day = new LiturgyDay(
                LocalDate.of(2026, 10, 18),
                "Domingo XXIX",
                "comum",
                null,
                "A",
                new LiturgyDay.Reading("Is 45, 1.4-6", "Tomei Ciro pela mão"),
                new LiturgyDay.Reading("1Ts 1, 1-5b", "Recordamos a vossa fé"),
                "Aclamai a glória do Senhor.",
                new LiturgyDay.Reading("Mt 22, 15-21", "Dai a César o que é de César e a Deus o que é de Deus"),
                null);
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        new DeckService().generateMass(
                new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out),
                MassRequest.load(yaml).withLiturgy(day));

        try (XMLSlideShow ppt = open(out)) {
            List<String> layouts = layoutNames(ppt);
            int first = layouts.indexOf(TemplateLayouts.LEITURA_1);
            int psalm = layouts.indexOf(TemplateLayouts.SALMO);
            int gospel = layouts.indexOf(TemplateLayouts.EVANGELHO);
            int gloria = layouts.indexOf(TemplateLayouts.GLORIA_1);
            assertTrue(gloria < first);
            assertTrue(first < psalm);
            assertEquals(TemplateLayouts.LEITURA_2, layouts.get(psalm + 1));
            assertTrue(psalm < gospel);
            assertEquals(TemplateLayouts.INTERVAL_BLANK, layouts.get(gospel + 1));
            assertEquals("Is 45, 1.4-6", textShape(ppt.getSlides().get(first), "Referência").getText());
            assertEquals("«Tomei Ciro pela mão»", textShape(ppt.getSlides().get(first), "Citação").getText());
            assertEquals("«Aclamai a glória do Senhor.»", textShape(ppt.getSlides().get(psalm), "Citação").getText());
            assertEquals("«Dai a César o que é de César e a Deus o que é de Deus»",
                    textShape(ppt.getSlides().get(gospel), "Citação").getText());
            assertEquals("«Dai a César o que é de César e a Deus o que é de Deus»",
                    textShape(ppt.getSlides().get(0), "Frase").getText());
            Path comum = template().toAbsolutePath().normalize().getParent()
                    .resolve("../../assets/background_comum.jpg").normalize();
            if (Files.isRegularFile(comum)) {
                assertArrayEquals(Files.readAllBytes(comum), TemplateLayouts.scriptureBackground(ppt));
            }
        }
    }

    @Test
    void scriptureBackgroundFollowsTheSeasonIncludingRoseSundays() {
        assertEquals("background_comum.jpg", Season.resolve("comum", null).scriptureBackground());
        assertEquals("background_advento.jpg", Season.resolve("advento", null).scriptureBackground());
        assertEquals("background_advento_rosa.jpg",
                Season.resolve("advento", "Capa - Domingo III Advento").scriptureBackground());
        assertEquals("background_natal.jpg", Season.resolve("natal", null).scriptureBackground());
        assertEquals("background_quaresma.jpg", Season.resolve("quaresma", null).scriptureBackground());
        assertEquals("background_quaresma.jpg", Season.resolve("ramos", null).scriptureBackground());
        assertEquals("background_quaresma_rosa.jpg",
                Season.resolve("quaresma", "Capa - Domingo IV Quaresma").scriptureBackground());
        assertEquals("background_pascoa.jpg", Season.resolve("pascoa", null).scriptureBackground());
        assertEquals("background_pascoa.jpg", Season.resolve("pentecostes", null).scriptureBackground());

        Season white = Season.resolve("comum", "Capa - Solenidade");
        assertEquals("Capa - Solenidade", white.coverLayout());
        assertEquals(TemplateLayouts.FESTIVE_TITLE, white.titleLayout());
        assertNull(white.headerScheme());
        assertEquals("Tempo Comum · Ano A", white.tempo("A"));
        assertEquals("background_comum.jpg", white.scriptureBackground());
        assertFalse(white.omitsGloria());

        Season immaculate = Season.resolve("advento", "Capa - Solenidade");
        assertEquals(TemplateLayouts.FESTIVE_TITLE, immaculate.titleLayout());
        assertEquals("Tempo de Advento · Ano B", immaculate.tempo("B"));
        assertEquals("background_advento.jpg", immaculate.scriptureBackground());
        assertTrue(immaculate.omitsGloria());

        Season apostles = Season.resolve("comum", "Capa - Solenidade Pedro Paulo");
        assertEquals(TemplateLayouts.ORDINARY_TITLE, apostles.titleLayout());
        assertEquals("accent5", apostles.headerScheme());
        assertEquals("Tempo Comum · Ano A", apostles.tempo("A"));
        assertEquals("background_comum.jpg", apostles.scriptureBackground());
    }

    @Test
    void scriptureBackgroundReplacesTheSharedLayoutImage() throws Exception {
        BufferedImage picture = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Path jpeg = Files.createTempFile("background", ".jpg");
        ImageIO.write(picture, "jpg", jpeg.toFile());
        byte[] want = Files.readAllBytes(jpeg);
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(template()))) {
            TemplateLayouts.setScriptureBackground(ppt, want);
            try (OutputStream stream = Files.newOutputStream(out)) {
                ppt.write(stream);
            }
        }
        try (XMLSlideShow ppt = open(out)) {
            assertArrayEquals(want, TemplateLayouts.scriptureBackground(ppt));
            for (String name : new String[]{
                    TemplateLayouts.LEITURA_1, TemplateLayouts.LEITURA_2,
                    TemplateLayouts.SALMO, TemplateLayouts.EVANGELHO}) {
                assertArrayEquals(want, TemplateLayouts.scriptureBackground(ppt, name));
            }
        }
    }

    @Test
    void jsonMassFileUsesASongForEachMoment() throws Exception {
        Path json = massFile("""
                {
                  "mode": "mass",
                  "season": "comum",
                  "ano": "A",
                  "domingo": "XXVIII Domingo",
                  "frase": "frase",
                  "versiculo": "Mt 1, 1",
                  "songs": { "entrada": "2", "final": "1" },
                  "opcoes": { "credo": "breve" }
                }
                """);
        Path renamed = json.resolveSibling("deck.json");
        Files.move(json, renamed, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        DeckSpec spec = DeckSpec.load(renamed);
        MassRequest mass = ((DeckSpec.Mass) spec).request();
        new DeckService().generateMass(new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out), mass);

        try (XMLSlideShow ppt = open(out)) {
            assertEquals("2 - A festa da música\n1 - A minh’alma descansa em paz", notes(ppt.getSlides().get(0)));
            assertTrue(layoutNames(ppt).contains(TemplateLayouts.CREDO_SHORT_1));
        }
    }

    @Test
    void songDeckFileListsBookIdsWithABlankBetweenSongs() throws Exception {
        Path file = massFile("""
                mode: songs
                output: songs.pptx
                bookIds: ["2", "1"]
                """);
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        DeckSpec spec = DeckSpec.load(file);
        DeckSpec.Songs songs = (DeckSpec.Songs) spec;
        new DeckService().generateSongs(
                new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out),
                new DeckService.Selection.BookIds(songs.bookIds()),
                songs.cover(),
                songs.interval());

        try (XMLSlideShow ppt = open(out)) {
            assertEquals(6, ppt.getSlides().size());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(0).getSlideLayout().getName());
            assertEquals(TemplateLayouts.INTERVAL_BLANK, ppt.getSlides().get(4).getSlideLayout().getName());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(5).getSlideLayout().getName());
        }
    }

    @Test
    void massDeckPlacesFixedPrayersAndSkipsAnEmptyPaiNosso() throws Exception {
        Path yaml = massFile("""
                season: comum
                ano: A
                domingo: XXVIII Domingo
                frase: frase
                versiculo: Mt 1, 1
                songs:
                  entrada: "2"
                  final: "1"
                opcoes:
                  credo: longo
                """);
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        new DeckService().generateMass(new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out), MassRequest.load(yaml));

        try (XMLSlideShow ppt = open(out)) {
            List<String> layouts = layoutNames(ppt);
            assertFalse(hasAdjacent(layouts, TemplateLayouts.INTERVAL_BLANK));
            assertTrue(layouts.contains(TemplateLayouts.GLORIA_1));
            assertTrue(layouts.contains(TemplateLayouts.CREDO_LONG_1));
            assertTrue(layouts.contains(TemplateLayouts.CREDO_LONG_6));
            assertTrue(layouts.contains(TemplateLayouts.PREPARACAO));
            assertTrue(layouts.contains(TemplateLayouts.DIALOGO));
            assertTrue(layouts.contains(TemplateLayouts.MEMORIAL));
            assertTrue(layouts.contains(TemplateLayouts.ACLAMACAO));
            assertTrue(layouts.contains(TemplateLayouts.CONVITE));
            assertFalse(layouts.contains(TemplateLayouts.ORACAO));
            assertEquals(layouts.indexOf(TemplateLayouts.MEMORIAL) + 2, layouts.indexOf(TemplateLayouts.ACLAMACAO));
            assertEquals(TemplateLayouts.ORDINARY_TITLE, layouts.get(layouts.size() - 1));
            assertEquals("2 - A festa da música\n1 - A minh’alma descansa em paz", notes(ppt.getSlides().get(0)));
        }
    }

    @Test
    void adventOmitsGloriaAndOracaoUsesTheGivenText() throws Exception {
        Path yaml = massFile("""
                season: advento
                ano: B
                domingo: Domingo III
                frase: frase
                versiculo: Fl 4, 4-5
                songs:
                  paiNosso: P1
                  gloria: "2"
                opcoes:
                  credo: breve
                  oracao: Ouvi-nos, Senhor
                """);
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        new DeckService().generateMass(new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out), MassRequest.load(yaml));

        try (XMLSlideShow ppt = open(out)) {
            List<String> layouts = layoutNames(ppt);
            assertFalse(layouts.contains(TemplateLayouts.GLORIA_1));
            assertTrue(layouts.contains(TemplateLayouts.CREDO_SHORT_1));
            assertTrue(layouts.contains(TemplateLayouts.ORACAO));
            int oracao = layouts.indexOf(TemplateLayouts.ORACAO);
            assertEquals("Ouvi-nos, Senhor", textShape(ppt.getSlides().get(oracao), "Letra").getText());
            int memorial = layouts.indexOf(TemplateLayouts.MEMORIAL);
            assertEquals(TemplateLayouts.ORDINARY_TITLE, layouts.get(memorial + 2));
            assertEquals(TemplateLayouts.ACLAMACAO, layouts.get(memorial + 6));
        }
    }

    @Test
    void songDeckUsesTheSamePictureAndCaptionOnEveryInterval() throws Exception {
        Path image = Files.createTempFile("chorus-deck", ".png");
        Files.write(image, Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="));
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        AppConfig config = new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out);
        Interval interval = Interval.parse("texto+imagem", image, "Primeira comunhão");
        new DeckService().generateSongs(config, new DeckService.Selection.Ids(List.of(2, 2)), null, interval);

        try (XMLSlideShow ppt = open(out)) {
            assertEquals(3, ppt.getSlides().size());
            XSLFSlide between = ppt.getSlides().get(1);
            assertEquals(TemplateLayouts.INTERVAL_TEXT_IMAGE, between.getSlideLayout().getName());
            assertEquals("Primeira comunhão", textShape(between, "Letra").getText());
            assertFalse(textShape(between, "Letra").getText().contains("Texto a apresentar"));
            XSLFPictureShape picture = picture(between);
            assertEquals(Placeholder.PICTURE, picture.getPlaceholder());
            assertEquals(Files.size(image), picture.getPictureData().getData().length);
            double side = picture.getAnchor().getWidth();
            assertEquals(side, picture.getAnchor().getHeight(), 0.5);
            assertTrue(side < picturePlaceholderAnchor(between).getWidth() - 1);
        }
    }

    @Test
    void songDeckNotesUseTheTitleWhenTheBookIdIsMissing() throws Exception {
        Path index = Files.createTempFile("chorus-deck", ".json");
        Files.writeString(index, """
                [
                  {"id":1,"title":"Sem número","content":["verso"]},
                  {"id":2,"bookId":"A1","title":"Com número","content":["verso"]}
                ]
                """, UTF_8);
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        AppConfig config = new AppConfig(Path.of("unused.pptx"), index, template(), out);
        new DeckService().generateSongs(config, new DeckService.Selection.Ids(List.of(1, 2)), cover("comum"));

        try (XMLSlideShow ppt = open(out)) {
            assertEquals("Sem número\nA1 - Com número", notes(ppt.getSlides().get(0)));
            assertEquals(TemplateLayouts.INTERVAL_BLANK, ppt.getSlides().get(1).getSlideLayout().getName());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(4).getSlideLayout().getName());
        }
    }

    @Test
    void generateByBookIdUsesTheSongbookNumber() throws Exception {
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        AppConfig config = new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out);
        new DeckService().generate(config, new DeckService.Selection.BookIds(List.of("2", "1")), cover("comum"));

        try (XMLSlideShow ppt = open(out)) {
            assertEquals("A festa da música", textShape(ppt.getSlides().get(1), "Título").getText());
            assertEquals("A minh’alma descansa em paz", textShape(ppt.getSlides().get(5), "Título").getText());
        }
    }

    @Test
    void coverFollowsSeasonAndSpecialSunday() throws Exception {
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        AppConfig config = new AppConfig(Path.of("unused.pptx"), indexFile(), template(), out);
        new DeckService().generate(config, new DeckService.Selection.Ids(List.of(2)), new Cover(
                "quaresma", "Capa - Domingo IV Quaresma", "IV Domingo da Quaresma", "frase", "Jo 9, 1-41", "B"));

        try (XMLSlideShow ppt = open(out)) {
            XSLFSlide cover = ppt.getSlides().get(0);
            assertEquals("Capa - Domingo IV Quaresma", cover.getSlideLayout().getName());
            assertEquals("Tempo de Quaresma · Ano B", textShape(cover, "Tempo").getText());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(1).getSlideLayout().getName());
            assertEquals("accent3", headerScheme(ppt));
        }

        Path festive = Files.createTempFile("chorus-deck", ".pptx");
        new DeckService().generate(
                new AppConfig(Path.of("unused.pptx"), indexFile(), template(), festive),
                new DeckService.Selection.Ids(List.of(2)),
                cover("pascoa"));
        try (XMLSlideShow ppt = open(festive)) {
            assertEquals("Capa - Páscoa", ppt.getSlides().get(0).getSlideLayout().getName());
            assertEquals("Tempo Pascal · Ano A", textShape(ppt.getSlides().get(0), "Tempo").getText());
            assertEquals(TemplateLayouts.FESTIVE_TITLE, ppt.getSlides().get(1).getSlideLayout().getName());
        }
    }

    @Test
    void generateRejectsUnknownIdAndEmptySong() throws Exception {
        Path out = Files.createTempFile("chorus-deck", ".pptx");
        Path index = Files.createTempFile("chorus-deck", ".json");
        Files.writeString(index, "[{\"id\":1,\"bookId\":\"1\",\"title\":\"Vazio\",\"content\":[]}]", UTF_8);
        AppConfig config = new AppConfig(Path.of("unused.pptx"), index, template(), out);

        assertThrows(IllegalArgumentException.class,
                () -> new DeckService().generate(config, new DeckService.Selection.Ids(List.of(1)), cover("comum")));
        Files.writeString(index, "[{\"id\":1,\"bookId\":\"1\",\"title\":\"Um\",\"content\":[\"verso\"]}]", UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> new DeckService().generate(config, new DeckService.Selection.Ids(List.of(2)), cover("comum")));
        assertThrows(IllegalArgumentException.class,
                () -> new DeckService().generate(config, new DeckService.Selection.BookIds(List.of("9")), cover("comum")));
    }

    private static Cover cover(String season) {
        return new Cover(season, null, "XXVIII Domingo", "frase", "Mt 25, 14-30", "A");
    }

    private static java.awt.geom.Rectangle2D picturePlaceholderAnchor(XSLFSlide slide) {
        for (XSLFShape shape : slide.getSlideLayout().getShapes()) {
            if (shape.getPlaceholder() == Placeholder.PICTURE) {
                return shape.getAnchor();
            }
        }
        throw new IllegalStateException("Picture placeholder not found");
    }

    private static XSLFPictureShape picture(XSLFSlide slide) {
        XSLFPictureShape found = null;
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFPictureShape picture) {
                if (found != null) {
                    throw new IllegalStateException("More than one picture on the interval slide");
                }
                found = picture;
            }
        }
        if (found == null) {
            throw new IllegalStateException("Picture not found");
        }
        return found;
    }

    private static Path massFile(String yaml) throws Exception {
        Path file = Files.createTempFile("chorus-deck", ".yaml");
        Files.writeString(file, yaml, UTF_8);
        return file;
    }

    private static List<String> layoutNames(XMLSlideShow ppt) {
        List<String> names = new java.util.ArrayList<>();
        for (XSLFSlide slide : ppt.getSlides()) {
            names.add(slide.getSlideLayout().getName());
        }
        return names;
    }

    private static boolean hasAdjacent(List<String> layouts, String name) {
        for (int i = 1; i < layouts.size(); i++) {
            if (name.equals(layouts.get(i)) && name.equals(layouts.get(i - 1))) {
                return true;
            }
        }
        return false;
    }

    private static String notes(XSLFSlide slide) {
        return notesBody(slide).getText();
    }

    private static XSLFTextShape notesBody(XSLFSlide slide) {
        XSLFNotes notes = slide.getNotes();
        if (notes == null) {
            throw new IllegalStateException("Notes placeholder not found");
        }
        for (XSLFShape shape : notes.getShapes()) {
            if (shape instanceof XSLFTextShape textShape && textShape.getTextType() == Placeholder.BODY) {
                return textShape;
            }
        }
        throw new IllegalStateException("Notes placeholder not found");
    }

    private static String headerScheme(XMLSlideShow ppt) {
        XSLFSlideLayout layout = TemplateLayouts.findLayout(ppt, TemplateLayouts.ORDINARY_TITLE);
        for (XSLFShape shape : layout.getShapes()) {
            if ("Header".equals(shape.getShapeName()) && shape.getXmlObject() instanceof CTShape ctShape) {
                return ctShape.getSpPr().getSolidFill().getSchemeClr().getVal().toString();
            }
        }
        throw new IllegalStateException("Header not found");
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
        Path data = Path.of("data", "index.json");
        if (Files.isRegularFile(data)) {
            return data;
        }
        Path nested = Path.of("backend", "data", "index.json");
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        throw new IllegalStateException("index.json not found");
    }

    private static boolean hasLayout(XMLSlideShow ppt, String name) {
        try {
            TemplateLayouts.findLayout(ppt, name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
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
