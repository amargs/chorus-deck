package org.amargs.chorusdeck.text;

import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextBody;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextBodyProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextNormalAutofit;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraph;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraphProperties;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PowerPoint does not run "shrink text on overflow" until someone edits the shape.
 * The fitted size is written into the run and style {@code sz} so the slide opens at that size.
 */
public final class TextFit {

    private static final int FULL_SCALE = 100_000;
    private static final int MIN_HUNDREDTHS = 800;
    private static final int POINT_HUNDREDTHS = 100;
    /** A wrapped line shorter than this may move up when a smaller size removes that line. */
    private static final int SHORT_CHUNK_CHARACTERS = 12;
    /**
     * Java measures a real font a little narrower than PowerPoint. A pull is accepted only when the
     * word still fits after giving back this much of an em, so a line that fills the box is not kept.
     */
    private static final double PULL_SLACK_EM = 0.12;
    private static final double EMU_PER_POINT = 12_700;
    private static final double DEFAULT_INSET_X = 91_440;
    private static final double DEFAULT_INSET_Y = 45_720;
    private static final FontRenderContext RENDER_CONTEXT = new FontRenderContext(null, true, true);
    /** PowerPoint draws lowercase as capitals at this fraction of the font size when {@code cap="small"}. */
    private static final double SMALL_CAPS_SCALE = 0.8;

    private TextFit() {
    }

    public static void apply(XSLFTextShape shape) {
        apply(shape, null, null, 0, 0);
    }

    public static void apply(XSLFTextShape shape, String marked, FitCache cache, int songId, int slide) {
        if (!(shape.getXmlObject() instanceof CTShape ctShape) || ctShape.getTxBody() == null) {
            return;
        }
        CTTextBody txBody = ctShape.getTxBody();
        CTTextBodyProperties bodyPr = txBody.getBodyPr();
        if (bodyPr == null || !bodyPr.isSetNormAutofit()) {
            return;
        }

        Rectangle2D anchor = shape.getAnchor();
        double widthPt = anchor.getWidth() - insetPoints(bodyPr.isSetLIns(), bodyPr.getLIns(), DEFAULT_INSET_X)
                - insetPoints(bodyPr.isSetRIns(), bodyPr.getRIns(), DEFAULT_INSET_X);
        double heightPt = anchor.getHeight() - insetPoints(bodyPr.isSetTIns(), bodyPr.getTIns(), DEFAULT_INSET_Y)
                - insetPoints(bodyPr.isSetBIns(), bodyPr.getBIns(), DEFAULT_INSET_Y);
        if (widthPt <= 1 || heightPt <= 1) {
            return;
        }

        double textWidth = widthPt;
        double targetHeight = heightPt;
        TextStyle style = textStyle(shape, txBody);
        String box = box(textWidth, targetHeight, style.fontPt());
        String hash = FitCache.hash(BoldMarkup.SEMIBOLD + "\0" + style.typeface() + "\0"
                + (style.smallCaps() ? "small" : "") + "\0" + marked);
        Integer remembered = cache == null ? null : cache.recall(songId, slide, shape.getShapeName(), box, hash);
        int hundredths = remembered != null
                ? remembered
                : fittedHundredths(shape, style, textWidth, targetHeight);
        if (remembered == null && cache != null) {
            cache.remember(songId, slide, layoutName(shape), shape.getShapeName(), box, hash, hundredths);
        }
        CTTextNormalAutofit autofit = bodyPr.getNormAutofit();
        autofit.setFontScale(FULL_SCALE);
        autofit.setLnSpcReduction(0);
        if (hundredths > 0) {
            applyFontSize(txBody, hundredths);
        }
    }

    private static int fittedHundredths(XSLFTextShape shape, TextStyle style, double textWidth, double targetHeight) {
        List<XSLFTextParagraph> paragraphs = shape.getTextParagraphs();
        int max = (int) Math.round(style.fontPt() * 100.0);
        int chosen = MIN_HUNDREDTHS;
        for (int hundredths = max; hundredths >= MIN_HUNDREDTHS; hundredths -= POINT_HUNDREDTHS) {
            if (fits(paragraphs, style, textWidth, targetHeight, hundredths / 100.0)) {
                chosen = hundredths;
                break;
            }
        }
        chosen = pullShortChunk(paragraphs, style, textWidth, targetHeight, chosen);
        if (chosen >= max) {
            return 0;
        }
        return chosen;
    }

    /**
     * When the size that fits leaves a wrapped line of fewer than 12 characters, use a smaller size
     * if that pulls the chunk onto the previous line. The drop is at most 10% of that size,
     * rounded up to a whole point (48 can fall to 43, 16 can fall to 14). If a short chunk remains,
     * the same limit is applied again from the size just chosen.
     */
    private static int pullShortChunk(
            List<XSLFTextParagraph> paragraphs, TextStyle style, double widthPt, double heightPt, int chosen) {
        while (true) {
            Wrap atSize = measure(paragraphs, style, widthPt, chosen / 100.0);
            if (!atSize.shortChunk()) {
                return chosen;
            }
            int maxDrop = (int) Math.ceil((chosen / POINT_HUNDREDTHS) * 0.10) * POINT_HUNDREDTHS;
            int pulled = chosen;
            for (int drop = POINT_HUNDREDTHS; drop <= maxDrop; drop += POINT_HUNDREDTHS) {
                int smaller = chosen - drop;
                if (smaller < MIN_HUNDREDTHS) {
                    break;
                }
                double trial = smaller / 100.0;
                double pullWidth = style.font() == null ? widthPt : Math.max(1, widthPt - trial * PULL_SLACK_EM);
                Wrap reduced = measure(paragraphs, style, pullWidth, trial);
                if (reduced.lines() < atSize.lines() && fits(paragraphs, style, widthPt, heightPt, trial)) {
                    pulled = smaller;
                    break;
                }
            }
            if (pulled == chosen) {
                return chosen;
            }
            chosen = pulled;
        }
    }

    private static String layoutName(XSLFTextShape shape) {
        if (shape.getSheet() instanceof org.apache.poi.xslf.usermodel.XSLFSlide slide && slide.getSlideLayout() != null) {
            String name = slide.getSlideLayout().getName();
            return name == null ? "" : name;
        }
        return "";
    }

    private static String box(double textWidth, double targetHeight, double fontPt) {
        return String.format(Locale.ROOT, "%.2f|%.2f|%.2f", textWidth, targetHeight, fontPt);
    }

    private static CTTextParagraphProperties level(CTTextBody txBody) {
        return txBody.getLstStyle() == null ? null : txBody.getLstStyle().getLvl1PPr();
    }

    private static void applyFontSize(CTTextBody txBody, int hundredths) {
        if (txBody.getLstStyle() != null && txBody.getLstStyle().isSetLvl1PPr()) {
            CTTextParagraphProperties level = txBody.getLstStyle().getLvl1PPr();
            if (!level.isSetDefRPr()) {
                level.addNewDefRPr();
            }
            level.getDefRPr().setSz(hundredths);
        }
        for (CTTextParagraph paragraph : txBody.getPArray()) {
            for (CTRegularTextRun run : paragraph.getRArray()) {
                if (!run.isSetRPr()) {
                    run.addNewRPr();
                }
                run.getRPr().setSz(hundredths);
            }
        }
    }

    private static double insetPoints(boolean set, Object value, double fallbackEmu) {
        return (set ? number(value) : fallbackEmu) / EMU_PER_POINT;
    }

    private static double number(Object value) {
        if (value instanceof Number numeric) {
            return numeric.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private static boolean fits(List<XSLFTextParagraph> paragraphs, TextStyle style, double widthPt, double heightPt, double fontPt) {
        double linePt = style.exactLinePt() > 0 ? style.exactLinePt() : singleSpacing(style, fontPt) * style.linePct();
        Wrap measured = measure(paragraphs, style, widthPt, fontPt);
        double total = 0;
        for (XSLFTextParagraph paragraph : paragraphs) {
            total += style.spaceBeforePt() + style.spaceAfterPt();
        }
        total += measured.lines() * linePt;
        return total <= heightPt + 0.5;
    }

    /** Percentage spacing is a fraction of the font's own line spacing, not of the point size. */
    private static double singleSpacing(TextStyle style, double fontPt) {
        if (style.font() == null) {
            return fontPt;
        }
        double height = Math.max(lineBox(style.font(), fontPt), lineBox(style.boldFont(), fontPt));
        return height > 0 ? height : fontPt;
    }

    private static double lineBox(Font font, double fontPt) {
        if (font == null) {
            return 0;
        }
        java.awt.font.LineMetrics metrics = font.deriveFont((float) fontPt).getLineMetrics("Ag", RENDER_CONTEXT);
        float height = metrics.getAscent() + metrics.getDescent() + metrics.getLeading();
        return height > 0 ? height : 0;
    }

    private static Wrap measure(List<XSLFTextParagraph> paragraphs, TextStyle style, double widthPt, double fontPt) {
        int lines = 0;
        boolean shortChunk = false;
        for (XSLFTextParagraph paragraph : paragraphs) {
            Wrap wrapped = wrapParagraph(paragraph, widthPt, fontPt, style.font(), style.boldFont(), style.smallCaps());
            lines += wrapped.lines();
            shortChunk = shortChunk || wrapped.shortChunk();
        }
        return new Wrap(lines, shortChunk);
    }

    private static Wrap wrapParagraph(XSLFTextParagraph paragraph, double widthPt, double fontPt, Font regular, Font bold, boolean smallCaps) {
        List<XSLFTextRun> runs = paragraph.getTextRuns();
        if (runs.isEmpty()) {
            return new Wrap(1, false);
        }
        int lines = 1;
        double used = 0;
        boolean any = false;
        int charsOnLine = 0;
        boolean wrappedLine = false;
        boolean shortChunk = false;
        for (XSLFTextRun run : runs) {
            String raw = run.getRawText();
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            any = true;
            Font face = runFace(run, regular, bold);
            for (String token : tokens(raw)) {
                if (token.equals("\n")) {
                    shortChunk = shortChunk || shortWrappedChunk(wrappedLine, charsOnLine);
                    lines++;
                    used = 0;
                    charsOnLine = 0;
                    wrappedLine = false;
                    continue;
                }
                double width = textWidth(token, face, fontPt, smallCaps && runSmallCaps(run));
                if (token.equals(" ")) {
                    if (used > 0 && used + width <= widthPt) {
                        used += width;
                    }
                    continue;
                }
                boolean newLine = used > 0 && used + width > widthPt;
                if (newLine) {
                    shortChunk = shortChunk || shortWrappedChunk(wrappedLine, charsOnLine);
                    lines++;
                    charsOnLine = 0;
                    wrappedLine = true;
                    used = 0;
                }
                if (width <= widthPt) {
                    used = used == 0 ? width : used + width;
                } else {
                    lines += Math.max(0, (int) Math.ceil(width / widthPt) - 1);
                    used = width % widthPt;
                }
                charsOnLine += charsOnLine == 0 ? token.length() : 1 + token.length();
            }
        }
        if (!any) {
            return new Wrap(1, false);
        }
        shortChunk = shortChunk || shortWrappedChunk(wrappedLine, charsOnLine);
        return new Wrap(lines, shortChunk);
    }

    private static boolean shortWrappedChunk(boolean wrappedLine, int charsOnLine) {
        return wrappedLine && charsOnLine > 0 && charsOnLine < SHORT_CHUNK_CHARACTERS;
    }

    private record Wrap(int lines, boolean shortChunk) {
    }

    private static Font runFace(XSLFTextRun run, Font regular, Font bold) {
        String typeface = runTypeface(run);
        if (typeface != null) {
            Font weighted = Fonts.baseFont(typeface);
            if (weighted != null) {
                return weighted;
            }
        }
        return run.isBold() ? bold : regular;
    }

    private static String runTypeface(XSLFTextRun run) {
        if (run.getXmlObject() instanceof CTRegularTextRun ctRun && ctRun.isSetRPr() && ctRun.getRPr().isSetLatin()) {
            String typeface = ctRun.getRPr().getLatin().getTypeface();
            if (typeface != null && !typeface.isBlank() && !typeface.startsWith("+")) {
                return typeface;
            }
        }
        return null;
    }

    private static List<String> tokens(String raw) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '\n') {
                tokens.add(String.valueOf(c));
                i++;
                continue;
            }
            int end = i + 1;
            while (end < raw.length() && raw.charAt(end) != ' ' && raw.charAt(end) != '\n') {
                end++;
            }
            tokens.add(raw.substring(i, end));
            i = end;
        }
        return tokens;
    }

    private static boolean runSmallCaps(XSLFTextRun run) {
        if (run.getXmlObject() instanceof CTRegularTextRun ctRun && ctRun.isSetRPr() && ctRun.getRPr().isSetCap()) {
            return "small".equals(ctRun.getRPr().getCap().toString());
        }
        return true;
    }

    private static double textWidth(String text, Font font, double fontPt, boolean smallCaps) {
        if (!smallCaps) {
            return textWidth(text, font, fontPt);
        }
        double width = 0;
        int i = 0;
        while (i < text.length()) {
            boolean lower = Character.isLowerCase(text.charAt(i));
            int end = i + 1;
            while (end < text.length() && Character.isLowerCase(text.charAt(end)) == lower) {
                end++;
            }
            String chunk = text.substring(i, end);
            if (lower) {
                width += textWidth(chunk.toUpperCase(Locale.ROOT), font, fontPt * SMALL_CAPS_SCALE);
            } else {
                width += textWidth(chunk, font, fontPt);
            }
            i = end;
        }
        return width;
    }

    private static double textWidth(String text, Font font, double fontPt) {
        if (text.isEmpty()) {
            return 0;
        }
        if (font == null) {
            double em = fontPt * (text.equals(" ") ? 0.25 : 0.5);
            return text.length() * em;
        }
        Font sized = font.deriveFont((float) fontPt);
        return sized.getStringBounds(text, RENDER_CONTEXT).getWidth();
    }

    private static TextStyle textStyle(XSLFTextShape shape, CTTextBody txBody) {
        double fontPt = 18;
        double linePct = 1;
        double exactLinePt = 0;
        double spaceBeforePt = 0;
        double spaceAfterPt = 0;
        String typeface = null;
        boolean smallCaps = false;
        CTTextParagraphProperties level = level(txBody);
        if (level != null) {
            fontPt = fontPt(level, fontPt);
            typeface = Fonts.typeface(level, typeface);
            smallCaps = smallCaps(level);
            linePct = linePct(level, linePct);
            exactLinePt = exactLinePt(level, exactLinePt);
            spaceBeforePt = spacingPt(level.isSetSpcBef() ? level.getSpcBef() : null, spaceBeforePt);
            spaceAfterPt = spacingPt(level.isSetSpcAft() ? level.getSpcAft() : null, spaceAfterPt);
        }
        if (typeface == null || typeface.startsWith("+")) {
            typeface = Fonts.latinFamily(shape, typeface);
        }
        Font regular = Fonts.baseFont(typeface);
        return new TextStyle(fontPt, linePct, exactLinePt, spaceBeforePt, spaceAfterPt, typeface, smallCaps, regular, Fonts.boldFont(typeface, regular));
    }

    private static boolean smallCaps(CTTextParagraphProperties level) {
        CTTextCharacterProperties rPr = level.isSetDefRPr() ? level.getDefRPr() : null;
        return rPr != null && rPr.isSetCap() && "small".equals(rPr.getCap().toString());
    }

    private static double fontPt(CTTextParagraphProperties level, double fallback) {
        CTTextCharacterProperties rPr = level.isSetDefRPr() ? level.getDefRPr() : null;
        if (rPr != null && rPr.isSetSz()) {
            return rPr.getSz() / 100.0;
        }
        return fallback;
    }

    private static double linePct(CTTextParagraphProperties level, double fallback) {
        if (level.isSetLnSpc() && level.getLnSpc().isSetSpcPct()) {
            return number(level.getLnSpc().getSpcPct().getVal()) / FULL_SCALE;
        }
        return fallback;
    }

    private static double exactLinePt(CTTextParagraphProperties level, double fallback) {
        if (level.isSetLnSpc() && level.getLnSpc().isSetSpcPts()) {
            return number(level.getLnSpc().getSpcPts().getVal()) / 100.0;
        }
        return fallback;
    }

    private static double spacingPt(org.openxmlformats.schemas.drawingml.x2006.main.CTTextSpacing spacing, double fallback) {
        if (spacing == null) {
            return fallback;
        }
        if (spacing.isSetSpcPts()) {
            return number(spacing.getSpcPts().getVal()) / 100.0;
        }
        return fallback;
    }

    private record TextStyle(double fontPt, double linePct, double exactLinePt, double spaceBeforePt, double spaceAfterPt,
                             String typeface, boolean smallCaps, Font font, Font boldFont) {
    }
}
