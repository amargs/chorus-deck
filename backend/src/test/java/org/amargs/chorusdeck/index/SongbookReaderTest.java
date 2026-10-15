package org.amargs.chorusdeck.index;

import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongbookReaderTest {

    @Test
    void numberedNomeStyleIgnoresVersesAndEmptyHeadings() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            BigInteger numId = decimalList(doc);
            nomeStyle(doc, numId);
            nome(doc, "A minh’alma descansa em paz");
            verse(doc, "A minh’alma descansa em paz, no Senhor,");
            nome(doc, "");
            nome(doc, "A festa da música");

            List<SongbookItem> items = SongbookReader.read(doc);

            assertEquals(2, items.size());
            assertEquals(new SongbookItem.Entry("1", "A minh’alma descansa em paz"), items.get(0));
            assertEquals(new SongbookItem.Entry("2", "A festa da música"), items.get(1));
            assertEquals("1\tA minh’alma descansa em paz", SongbookItem.line(items.get(0)));
        }
    }

    @Test
    void riteHeadingsUseTheTypedCodeAndDoNotNumberBlanks() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            nomeStyle(doc, BigInteger.ZERO);
            nome(doc, "RP1. Kyrie eleison (1)");
            nome(doc, "");
            nome(doc, "A12. Preparai os caminhos do Senhor");
            nome(doc, "Sem código");

            List<SongbookItem> items = SongbookReader.read(doc);

            assertEquals(3, items.size());
            assertEquals(new SongbookItem.Entry("RP1", "Kyrie eleison (1)"), items.get(0));
            assertEquals(new SongbookItem.Entry("A12", "Preparai os caminhos do Senhor"), items.get(1));
            assertEquals(new SongbookItem.MissingPrefix("Sem código"), items.get(2));
            assertEquals("missing-prefix\tSem código", SongbookItem.line(items.get(2)));
        }
    }

    @Test
    void realSongbooksMatchWordHeadings() throws Exception {
        Path dir = Path.of("data");
        if (!Files.isRegularFile(dir.resolve("songbook_default.docx"))) {
            dir = Path.of("backend", "data");
        }
        Path regular = dir.resolve("songbook_default.docx");
        Path rites = dir.resolve("songbook_rites.docx");
        assertTrue(Files.isRegularFile(regular), "songbook_default.docx not found from " + Path.of("").toAbsolutePath());
        assertTrue(Files.isRegularFile(rites));

        SongbookReader reader = new SongbookReader();
        List<SongbookItem> songs = reader.read(regular);
        assertInstanceOf(SongbookItem.Entry.class, songs.get(0));
        assertEquals(new SongbookItem.Entry("1", "A minh’alma descansa em paz"), songs.get(0));
        assertTrue(songs.contains(new SongbookItem.Entry("5", "Acreditar")));
        assertTrue(songs.contains(new SongbookItem.Entry("6", "Adeste fideles")));
        assertTrue(songs.contains(new SongbookItem.Entry("135", "Passa por mim")));
        assertTrue(songs.contains(new SongbookItem.Entry("136", "Passo a passo")));
        assertTrue(songs.contains(new SongbookItem.Entry("147", "Quero ser como tu")));
        assertEquals(0, songs.stream().filter(item -> item instanceof SongbookItem.MissingPrefix).count());

        List<SongbookItem> riteItems = reader.read(rites);
        assertEquals(new SongbookItem.Entry("RP1", "Kyrie eleison (1)"), riteItems.get(0));
        assertTrue(riteItems.contains(new SongbookItem.Entry("A1", "Aleluia (7)")));
        assertTrue(riteItems.contains(new SongbookItem.Entry("S1", "Santo (Antigo)")));
        assertTrue(riteItems.contains(new SongbookItem.Entry("C8", "Cordeiro (nós, Senhor)")));
        assertEquals(0, riteItems.stream().filter(item -> item instanceof SongbookItem.MissingPrefix).count());
    }

    private static BigInteger decimalList(XWPFDocument doc) {
        CTAbstractNum abstractNum = CTAbstractNum.Factory.newInstance();
        abstractNum.setAbstractNumId(BigInteger.ZERO);
        CTLvl lvl = abstractNum.addNewLvl();
        lvl.setIlvl(BigInteger.ZERO);
        lvl.addNewStart().setVal(BigInteger.ONE);
        lvl.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
        lvl.addNewLvlText().setVal("%1.");
        XWPFNumbering numbering = doc.createNumbering();
        BigInteger abstractNumId = numbering.addAbstractNum(new XWPFAbstractNum(abstractNum));
        return numbering.addNum(abstractNumId);
    }

    private static void nomeStyle(XWPFDocument doc, BigInteger numId) {
        XWPFStyles styles = doc.createStyles();
        CTStyle ctStyle = CTStyle.Factory.newInstance();
        ctStyle.setStyleId(SongbookReader.TITLE_STYLE);
        ctStyle.setType(STStyleType.PARAGRAPH);
        ctStyle.addNewName().setVal("Nome");
        ctStyle.addNewBasedOn().setVal("Heading1");
        CTPPrGeneral pPr = ctStyle.addNewPPr();
        pPr.addNewNumPr().addNewNumId().setVal(numId);
        styles.addStyle(new XWPFStyle(ctStyle));
    }

    private static void nome(XWPFDocument doc, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setStyle(SongbookReader.TITLE_STYLE);
        if (!text.isEmpty()) {
            paragraph.createRun().setText(text);
        }
    }

    private static void verse(XWPFDocument doc, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setStyle("Estrofe");
        paragraph.createRun().setText(text);
    }
}
