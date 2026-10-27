package org.amargs.chorusdeck.text;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoldMarkupTest {

    @Test
    void marksBoldWordsAndKeepsAFullyBoldChorus() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFTextBox mixed = textBox(ppt);
            addRun(mixed, "Cântico: ", true);
            addRun(mixed, "verso", false);
            assertEquals("**Cântico: **verso", BoldMarkup.mark(mixed));

            XSLFTextBox chorus = textBox(ppt);
            addRun(chorus, "Hoje eu vou celebrar", true);
            assertEquals("**Hoje eu vou celebrar**", BoldMarkup.mark(chorus));
        }
    }

    @Test
    void missingFacesCollapseToOneGoogleFontsPagePerFamily() {
        assertEquals(List.of(), BoldMarkup.fontLinks(List.of()));
        assertEquals(List.of(
                new BoldMarkup.FontLink("IBM Plex Sans", "https://fonts.google.com/specimen/IBM+Plex+Sans"),
                new BoldMarkup.FontLink("Playfair Display", "https://fonts.google.com/specimen/Playfair+Display")),
                BoldMarkup.fontLinks(List.of(
                        "Playfair Display Bold",
                        "IBM Plex Sans SemiBold",
                        "Playfair Display",
                        "not a deck font")));
    }

    @Test
    void italicAndUnderlineStayOnTheRunsAndLeaveThePlainText() throws Exception {
        assertEquals("suave e linha", BoldMarkup.plain("*suave* e __linha__"));
        assertEquals("negrito", BoldMarkup.plain("**negrito**"));
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFTextBox box = textBox(ppt);
            box.addNewTextParagraph().addNewTextRun().setText("x");
            BoldMarkup.apply(box, "antes *suave* e __linha__");
            List<XSLFTextRun> runs = box.getTextParagraphs().get(0).getTextRuns();
            assertEquals("antes ", runs.get(0).getRawText());
            assertEquals("suave", runs.get(1).getRawText());
            assertTrue(runs.get(1).isItalic());
            assertEquals("linha", runs.get(3).getRawText());
            assertTrue(runs.get(3).isUnderlined());
        }
    }

    @Test
    void dropsBoldWhenTheOnlySlideIsFullyBold() {
        assertEquals("Hoje eu vou celebrar", BoldMarkup.plainIfFullyBold("**Hoje eu vou celebrar**"));
        assertEquals("linha um\nlinha dois", BoldMarkup.plainIfFullyBold("**linha um**\n**linha dois**"));
        assertEquals("**Cântico: **verso", BoldMarkup.plainIfFullyBold("**Cântico: **verso"));
    }

    private static XSLFTextBox textBox(XMLSlideShow ppt) {
        XSLFSlide slide = ppt.createSlide();
        return slide.createTextBox();
    }

    private static void addRun(XSLFTextBox box, String text, boolean bold) {
        XSLFTextParagraph paragraph = box.getTextParagraphs().isEmpty()
                ? box.addNewTextParagraph()
                : box.getTextParagraphs().get(0);
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(text);
        run.setBold(bold);
    }
}
