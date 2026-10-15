package org.amargs.chorusdeck.index;

import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNum;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads song titles from a songbook {@code .docx}. Titles use the {@code Nome} paragraph style.
 * A Word list on that style supplies {@code bookId} ({@code "1"}, {@code "2"}, …). When the list
 * is off, {@code bookId} is the code typed at the start of the heading ({@code "A1"}, {@code "RP3"}).
 */
public final class SongbookReader {

    static final String TITLE_STYLE = "Nome";
    private static final Pattern RITE_CODE = Pattern.compile("^([A-Za-z]+\\d+)\\.\\s*(.*)$", Pattern.DOTALL);

    public List<SongbookItem> read(Path docx) throws IOException {
        if (!Files.isRegularFile(docx)) {
            throw new IllegalArgumentException("Songbook not found: " + docx);
        }
        try (InputStream in = Files.newInputStream(docx);
             XWPFDocument document = new XWPFDocument(in)) {
            return read(document);
        }
    }

    static List<SongbookItem> read(XWPFDocument document) {
        List<SongbookItem> items = new ArrayList<>();
        Counters counters = new Counters();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (!TITLE_STYLE.equals(paragraph.getStyle())) {
                continue;
            }
            String text = paragraph.getText().replace('\u00A0', ' ').trim();
            NumRef numbering = effectiveNumbering(paragraph, document);
            if (text.isEmpty()) {
                continue;
            }
            if (numbering != null && numbering.active()) {
                String bookId = Integer.toString(counters.next(
                        numbering.numId(),
                        numbering.level(),
                        startAt(document, numbering.numId(), numbering.level())));
                items.add(new SongbookItem.Entry(bookId, text));
            } else {
                Matcher rite = RITE_CODE.matcher(text);
                if (!rite.matches()) {
                    items.add(new SongbookItem.MissingPrefix(text));
                } else {
                    items.add(new SongbookItem.Entry(rite.group(1), rite.group(2).trim()));
                }
            }
        }
        return items;
    }

    private static NumRef effectiveNumbering(XWPFParagraph paragraph, XWPFDocument document) {
        CTNumPr direct = paragraphNumPr(paragraph);
        NumRef style = styleNumbering(document, paragraph.getStyle());
        BigInteger numId = numId(direct);
        Integer ilvl = ilvl(direct);
        if (style != null) {
            if (numId == null) {
                numId = style.numId();
            }
            if (ilvl == null) {
                ilvl = style.ilvl();
            }
        }
        return new NumRef(numId, ilvl);
    }

    private static CTNumPr paragraphNumPr(XWPFParagraph paragraph) {
        if (paragraph.getCTP().getPPr() != null && paragraph.getCTP().getPPr().isSetNumPr()) {
            return paragraph.getCTP().getPPr().getNumPr();
        }
        return null;
    }

    private static NumRef styleNumbering(XWPFDocument document, String styleId) {
        XWPFStyles styles = document.getStyles();
        String current = styleId;
        while (current != null && styles != null) {
            XWPFStyle style = styles.getStyle(current);
            if (style == null || style.getCTStyle() == null) {
                return null;
            }
            CTStyle ctStyle = style.getCTStyle();
            if (ctStyle.getPPr() != null && ctStyle.getPPr().isSetNumPr()) {
                CTNumPr numPr = ctStyle.getPPr().getNumPr();
                return new NumRef(numId(numPr), ilvl(numPr));
            }
            current = ctStyle.isSetBasedOn() ? ctStyle.getBasedOn().getVal() : null;
        }
        return null;
    }

    private static BigInteger numId(CTNumPr numPr) {
        if (numPr != null && numPr.isSetNumId() && numPr.getNumId().getVal() != null) {
            return numPr.getNumId().getVal();
        }
        return null;
    }

    private static Integer ilvl(CTNumPr numPr) {
        if (numPr != null && numPr.isSetIlvl() && numPr.getIlvl().getVal() != null) {
            return numPr.getIlvl().getVal().intValue();
        }
        return null;
    }

    private static int startAt(XWPFDocument document, BigInteger numId, int ilvl) {
        XWPFNumbering numbering = document.getNumbering();
        if (numbering == null) {
            throw new IllegalArgumentException("Songbook heading uses a list but the document has no numbering part.");
        }
        XWPFNum num = numbering.getNum(numId);
        if (num == null) {
            throw new IllegalArgumentException("Songbook heading uses missing list id " + numId + ".");
        }
        for (CTNumLvl override : num.getCTNum().getLvlOverrideList()) {
            if (override.getIlvl() != null
                    && override.getIlvl().intValue() == ilvl
                    && override.isSetStartOverride()
                    && override.getStartOverride().getVal() != null) {
                return override.getStartOverride().getVal().intValue();
            }
        }
        BigInteger abstractNumId = num.getCTNum().getAbstractNumId().getVal();
        XWPFAbstractNum abstractNum = numbering.getAbstractNum(abstractNumId);
        if (abstractNum == null || abstractNum.getCTAbstractNum() == null) {
            throw new IllegalArgumentException("Songbook list " + numId + " has no abstract numbering.");
        }
        for (CTLvl lvl : abstractNum.getCTAbstractNum().getLvlList()) {
            if (lvl.getIlvl() != null && lvl.getIlvl().intValue() == ilvl && lvl.isSetStart() && lvl.getStart().getVal() != null) {
                return lvl.getStart().getVal().intValue();
            }
        }
        return 1;
    }

    private record NumRef(BigInteger numId, Integer ilvl) {
        boolean active() {
            return numId != null && numId.signum() != 0;
        }

        int level() {
            return ilvl == null ? 0 : ilvl;
        }
    }

    private static final class Counters {
        private final Map<String, boolean[]> used = new HashMap<>();
        private final Map<String, int[]> values = new HashMap<>();

        int next(BigInteger numId, int ilvl, int start) {
            if (ilvl < 0 || ilvl > 8) {
                throw new IllegalArgumentException("List level out of range: " + ilvl);
            }
            String key = numId.toString();
            boolean[] seen = used.computeIfAbsent(key, id -> new boolean[9]);
            int[] current = values.computeIfAbsent(key, id -> new int[9]);
            if (!seen[ilvl]) {
                current[ilvl] = start;
                seen[ilvl] = true;
            } else {
                current[ilvl]++;
            }
            for (int deeper = ilvl + 1; deeper < seen.length; deeper++) {
                seen[deeper] = false;
            }
            return current[ilvl];
        }
    }
}
