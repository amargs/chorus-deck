package org.amargs.chorusdeck.text;

import org.amargs.chorusdeck.deck.TemplateLayouts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextBody;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;

import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFitTest {

    @Test
    void shortWrappedChunkMovesUpWithinTenPercent() throws Exception {
        int size = fit("alegria do teu Senhor", 440, 120, 4_800);
        assertEquals(4_500, size);
    }

    @Test
    void elevenCharactersCanFallToTheTenPercentFloor() throws Exception {
        assertEquals(4_400, fit("ab abcdefghijk", 300, 120, 4_800));
    }

    @Test
    void chunkOfTwelveCharactersStays() throws Exception {
        assertEquals(4_800, fit("do teu abcdefghijkl", 400, 120, 4_800));
    }

    @Test
    void dropPastTenPercentStaysAtTheFittedSize() throws Exception {
        assertEquals(4_800, fit("ab abcdefghijk", 286, 120, 4_800));
        assertEquals(1_600, fit("ab abcdefghijk", 93, 80, 1_600));
    }

    @Test
    void sixteenPointSizeCanFallByTwoPoints() throws Exception {
        assertEquals(1_400, fit("ab abcdefghijk", 100, 80, 1_600));
    }

    @Test
    void minhaVidaShrinksSoSenhorStaysOnItsLine() throws Exception {
        Path template = Path.of("data", "template_4x3.pptx");
        if (!Files.isRegularFile(template)) {
            template = Path.of("backend", "data", "template_4x3.pptx");
        }
        String verse = """
                A minha vida é para Ti, Senhor.
                A minha vida é para Ti, Senhor.
                A minha vida é para Ti, Senhor.
                A minha vida é para Ti.""".stripIndent();
        try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(template))) {
            XSLFSlide slide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.LYRICS));
            TemplateLayouts.setShapeText(slide, "Letra", verse, true);
            XSLFTextShape letra = null;
            for (XSLFShape shape : slide.getShapes()) {
                if (shape instanceof XSLFTextShape text && "Letra".equals(text.getShapeName())) {
                    letra = text;
                }
            }
            assertEquals(4_800, fontSize(letra));
        }
    }

    @Test
    void nomeOnTheThirdVerseMovesUpWithinTenPercent() throws Exception {
        Path template = Path.of("data", "template_4x3.pptx");
        if (!Files.isRegularFile(template)) {
            template = Path.of("backend", "data", "template_4x3.pptx");
        }
        String verse = "Viveremos a tua paz, Senhor\nSemeando a palavra, teu nome,\ntua verdade\nComo sal e luz \nCantando o teu louvor";
        try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(template))) {
            XSLFSlide slide = ppt.createSlide(TemplateLayouts.findLayout(ppt, TemplateLayouts.LYRICS));
            TemplateLayouts.setShapeText(slide, "Letra", verse, true);
            XSLFTextShape letra = null;
            for (XSLFShape shape : slide.getShapes()) {
                if (shape instanceof XSLFTextShape text && "Letra".equals(text.getShapeName())) {
                    letra = text;
                }
            }
            int size = fontSize(letra);
            System.out.println("FITTED " + size);
            assertEquals(4_600, size);
        }
    }

    private static int fit(String text, double widthPt, double heightPt, int hundredths) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTextBox box = slide.createTextBox();
            box.setAnchor(new Rectangle2D.Double(0, 0, widthPt, heightPt));
            box.setText(text);
            CTShape ct = (CTShape) box.getXmlObject();
            CTTextBody body = ct.getTxBody();
            body.getBodyPr().setLIns(0);
            body.getBodyPr().setRIns(0);
            body.getBodyPr().setTIns(0);
            body.getBodyPr().setBIns(0);
            if (!body.getBodyPr().isSetNormAutofit()) {
                body.getBodyPr().addNewNormAutofit();
            }
            var list = body.isSetLstStyle() ? body.getLstStyle() : body.addNewLstStyle();
            var level = list.isSetLvl1PPr() ? list.getLvl1PPr() : list.addNewLvl1PPr();
            CTTextCharacterProperties defaults = level.isSetDefRPr() ? level.getDefRPr() : level.addNewDefRPr();
            defaults.setSz(hundredths);
            if (defaults.isSetLatin()) {
                defaults.getLatin().setTypeface("No Such Font");
            } else {
                defaults.addNewLatin().setTypeface("No Such Font");
            }
            TextFit.apply(box);
            return fontSize(box);
        }
    }

    private static int fontSize(XSLFTextShape shape) {
        Object size = ((CTShape) shape.getXmlObject()).getTxBody().getLstStyle().getLvl1PPr().getDefRPr().getSz();
        if (size instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(size.toString());
    }
}
