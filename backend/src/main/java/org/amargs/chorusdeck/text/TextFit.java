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
    private static final int MIN_SCALE = 10_000;
    private static final double EMU_PER_POINT = 12_700;
    private static final double DEFAULT_INSET_X = 91_440;
    private static final double DEFAULT_INSET_Y = 45_720;
    /** Lyric boxes are fitted inside this fraction of the measured box so the last line stays inside. */
    private static final double LYRIC_BOX_FRACTION = 0.88;
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

        boolean lyric = !Fonts.titlePlaceholder(shape);
        double textWidth = widthPt * (lyric ? 0.92 : 0.98);
        double targetHeight = lyric ? heightPt * LYRIC_BOX_FRACTION : heightPt;
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
        int fontScale = FULL_SCALE;
        if (!fits(paragraphs, style, textWidth, targetHeight, 1, 0)) {
            fontScale = fit(paragraphs, style, textWidth, targetHeight);
        }
        if (fontScale >= FULL_SCALE) {
            return 0;
        }
        int hundredths = (int) Math.floor(style.fontPt() * fontScale / FULL_SCALE * 100.0);
        return Math.max(hundredths, 800);
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

    private static int fit(List<XSLFTextParagraph> paragraphs, TextStyle style, double widthPt, double heightPt) {
        if (fits(paragraphs, style, widthPt, heightPt, 1, 0)) {
            return FULL_SCALE;
        }
        int best = MIN_SCALE;
        int low = MIN_SCALE;
        int high = FULL_SCALE;
        while (low <= high) {
            int mid = (low + high) / 2;
            if (fits(paragraphs, style, widthPt, heightPt, mid / (double) FULL_SCALE, 0)) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private static boolean fits(List<XSLFTextParagraph> paragraphs, TextStyle style, double widthPt, double heightPt, double fontScale, double lineReduction) {
        double fontPt = style.fontPt() * fontScale;
        double linePt = style.exactLinePt() > 0
                ? style.exactLinePt()
                : fontPt * style.linePct() * (1 - lineReduction);
        double total = 0;
        for (XSLFTextParagraph paragraph : paragraphs) {
            int lines = wrapLines(paragraph, widthPt, fontPt, style.font(), style.boldFont(), style.smallCaps());
            total += style.spaceBeforePt() + lines * linePt + style.spaceAfterPt();
        }
        return total <= heightPt + 0.5;
    }

    private static int wrapLines(XSLFTextParagraph paragraph, double widthPt, double fontPt, Font regular, Font bold, boolean smallCaps) {
        List<XSLFTextRun> runs = paragraph.getTextRuns();
        if (runs.isEmpty()) {
            return 1;
        }
        int lines = 1;
        double used = 0;
        boolean any = false;
        for (XSLFTextRun run : runs) {
            String raw = run.getRawText();
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            any = true;
            Font face = runFace(run, regular, bold);
            for (String token : tokens(raw)) {
                if (token.equals("\n")) {
                    lines++;
                    used = 0;
                    continue;
                }
                double width = textWidth(token, face, fontPt, smallCaps && runSmallCaps(run));
                if (token.equals(" ")) {
                    if (used > 0 && used + width <= widthPt) {
                        used += width;
                    }
                    continue;
                }
                if (used == 0 || used + width <= widthPt) {
                    if (width <= widthPt) {
                        used = used == 0 ? width : used + width;
                    } else {
                        lines += Math.max(0, (int) Math.ceil(width / widthPt) - 1);
                        used = width % widthPt;
                    }
                } else {
                    lines++;
                    if (width <= widthPt) {
                        used = width;
                    } else {
                        lines += Math.max(0, (int) Math.ceil(width / widthPt) - 1);
                        used = width % widthPt;
                    }
                }
            }
        }
        return any ? lines : 1;
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
