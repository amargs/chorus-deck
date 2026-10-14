package org.amargs.chorusdeck;

import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSheet;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.openxmlformats.schemas.drawingml.x2006.main.CTOfficeStyleSheet;
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextBody;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextBodyProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextNormalAutofit;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraph;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraphProperties;
import org.openxmlformats.schemas.presentationml.x2006.main.CTPlaceholder;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PowerPoint does not run "shrink text on overflow" until someone edits the shape.
 * The fitted size is written into the run and style {@code sz} so the slide opens at that size.
 */
final class TextFit {

    private static final int FULL_SCALE = 100_000;
    private static final int MIN_SCALE = 10_000;
    private static final double EMU_PER_POINT = 12_700;
    private static final double DEFAULT_INSET_X = 91_440;
    private static final double DEFAULT_INSET_Y = 45_720;
    /** Lyric boxes are fitted inside this fraction of the measured box so the last line stays inside. */
    private static final double LYRIC_BOX_FRACTION = 0.88;
    private static final FontRenderContext RENDER_CONTEXT = new FontRenderContext(null, true, true);
    private static final Map<String, Font> FONTS = new ConcurrentHashMap<>();

    private TextFit() {
    }

    static void apply(XSLFTextShape shape) {
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

        TextStyle style = textStyle(shape, txBody);
        List<String> paragraphs = paragraphs(shape);
        boolean lyric = !titlePlaceholder(shape);
        double textWidth = widthPt * (lyric ? 0.92 : 0.98);
        double targetHeight = lyric ? heightPt * LYRIC_BOX_FRACTION : heightPt;
        int fontScale = FULL_SCALE;
        if (!fits(paragraphs, style, textWidth, targetHeight, 1, 0)) {
            fontScale = fit(paragraphs, style, textWidth, targetHeight);
        }
        CTTextNormalAutofit autofit = bodyPr.getNormAutofit();
        autofit.setFontScale(FULL_SCALE);
        autofit.setLnSpcReduction(0);
        if (fontScale < FULL_SCALE) {
            int hundredths = (int) Math.floor(style.fontPt() * fontScale / FULL_SCALE * 100.0);
            applyFontSize(txBody, Math.max(hundredths, 800));
        }
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

    private static List<String> paragraphs(XSLFTextShape shape) {
        List<String> paragraphs = new ArrayList<>();
        for (XSLFTextParagraph paragraph : shape.getTextParagraphs()) {
            paragraphs.add(paragraph.getText() == null ? "" : paragraph.getText());
        }
        if (paragraphs.isEmpty()) {
            paragraphs.add("");
        }
        return paragraphs;
    }

    private static int fit(List<String> paragraphs, TextStyle style, double widthPt, double heightPt) {
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

    private static boolean fits(List<String> paragraphs, TextStyle style, double widthPt, double heightPt, double fontScale, double lineReduction) {
        double fontPt = style.fontPt() * fontScale;
        double linePt = style.exactLinePt() > 0
                ? style.exactLinePt()
                : fontPt * style.linePct() * (1 - lineReduction);
        double total = 0;
        for (String paragraph : paragraphs) {
            int lines = wrapLines(paragraph, widthPt, fontPt, style.font());
            total += style.spaceBeforePt() + lines * linePt + style.spaceAfterPt();
        }
        return total <= heightPt + 0.5;
    }

    private static int wrapLines(String paragraph, double widthPt, double fontPt, Font font) {
        if (paragraph.isEmpty()) {
            return 1;
        }
        String[] words = paragraph.split(" ", -1);
        int lines = 1;
        double used = 0;
        for (String word : words) {
            double wordWidth = textWidth(word, font, fontPt);
            double space = textWidth(" ", font, fontPt);
            if (used == 0) {
                if (wordWidth <= widthPt) {
                    used = wordWidth;
                } else {
                    lines += Math.max(0, (int) Math.ceil(wordWidth / widthPt) - 1);
                    used = wordWidth % widthPt;
                }
            } else if (used + space + wordWidth <= widthPt) {
                used += space + wordWidth;
            } else {
                lines++;
                used = Math.min(wordWidth, widthPt);
                if (wordWidth > widthPt) {
                    lines += Math.max(0, (int) Math.ceil(wordWidth / widthPt) - 1);
                    used = wordWidth % widthPt;
                }
            }
        }
        return lines;
    }

    private static double textWidth(String text, Font font, double fontPt) {
        if (text.isEmpty()) {
            return 0;
        }
        if (font == null) {
            return text.length() * fontPt * 0.5;
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
        CTTextParagraphProperties level = txBody.getLstStyle() == null ? null : txBody.getLstStyle().getLvl1PPr();
        if (level != null) {
            fontPt = fontPt(level, fontPt);
            typeface = typeface(level, typeface);
            linePct = linePct(level, linePct);
            exactLinePt = exactLinePt(level, exactLinePt);
            spaceBeforePt = spacingPt(level.isSetSpcBef() ? level.getSpcBef() : null, spaceBeforePt);
            spaceAfterPt = spacingPt(level.isSetSpcAft() ? level.getSpcAft() : null, spaceAfterPt);
        }
        if (typeface == null || typeface.startsWith("+")) {
            typeface = themeTypeface(shape, typeface, titlePlaceholder(shape));
        }
        return new TextStyle(fontPt, linePct, exactLinePt, spaceBeforePt, spaceAfterPt, baseFont(typeface));
    }

    private static double fontPt(CTTextParagraphProperties level, double fallback) {
        CTTextCharacterProperties rPr = level.isSetDefRPr() ? level.getDefRPr() : null;
        if (rPr != null && rPr.isSetSz()) {
            return rPr.getSz() / 100.0;
        }
        return fallback;
    }

    private static String typeface(CTTextParagraphProperties level, String fallback) {
        CTTextCharacterProperties rPr = level.isSetDefRPr() ? level.getDefRPr() : null;
        if (rPr != null && rPr.isSetLatin() && rPr.getLatin().getTypeface() != null && !rPr.getLatin().getTypeface().isBlank()) {
            return rPr.getLatin().getTypeface();
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

    private static boolean titlePlaceholder(XSLFTextShape shape) {
        if (!(shape.getXmlObject() instanceof CTShape ctShape)
                || ctShape.getNvSpPr() == null
                || ctShape.getNvSpPr().getNvPr() == null
                || !ctShape.getNvSpPr().getNvPr().isSetPh()) {
            return false;
        }
        CTPlaceholder placeholder = ctShape.getNvSpPr().getNvPr().getPh();
        if (!placeholder.isSetType()) {
            return false;
        }
        String type = placeholder.getType().toString();
        return "title".equals(type) || "ctrTitle".equals(type);
    }

    private static String themeTypeface(XSLFTextShape shape, String current, boolean title) {
        XSLFSlideMaster master = master(shape);
        if (master == null || master.getTheme() == null) {
            return current == null ? "IBM Plex Sans" : current;
        }
        CTOfficeStyleSheet theme = master.getTheme().getXmlObject();
        boolean major = title || (current != null && current.startsWith("+mj"));
        String resolved = major
                ? theme.getThemeElements().getFontScheme().getMajorFont().getLatin().getTypeface()
                : theme.getThemeElements().getFontScheme().getMinorFont().getLatin().getTypeface();
        return resolved == null || resolved.isBlank() ? "IBM Plex Sans" : resolved;
    }

    private static XSLFSlideMaster master(XSLFShape shape) {
        XSLFSheet sheet = shape.getSheet();
        if (sheet instanceof XSLFSlide slide) {
            return slide.getSlideMaster();
        }
        if (sheet instanceof XSLFSlideLayout layout) {
            return layout.getSlideMaster();
        }
        return null;
    }

    private static Font baseFont(String family) {
        if (family == null || family.isBlank()) {
            return null;
        }
        return FONTS.computeIfAbsent(family, TextFit::loadFont);
    }

    private static Font loadFont(String family) {
        Path file = findFontFile(family);
        if (file != null) {
            try {
                return Font.createFont(Font.TRUETYPE_FONT, file.toFile());
            } catch (FontFormatException | IOException ignored) {
                return null;
            }
        }
        Font installed = new Font(family, Font.PLAIN, 12);
        String wanted = family.toLowerCase(Locale.ROOT);
        if (installed.getFamily().toLowerCase(Locale.ROOT).equals(wanted)
                || installed.getFontName().toLowerCase(Locale.ROOT).contains(wanted)) {
            return installed;
        }
        return null;
    }

    private static Path findFontFile(String family) {
        String want = normalize(family);
        Path best = null;
        int bestScore = 0;
        for (Path dir : fontDirs()) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var files = Files.walk(dir, 5)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!name.endsWith(".ttf") && !name.endsWith(".otf")) {
                        continue;
                    }
                    int score = score(normalize(name.substring(0, name.length() - 4)), want);
                    if (score > bestScore) {
                        bestScore = score;
                        best = file;
                    }
                }
            } catch (IOException ignored) {
                // Try the next font directory.
            }
        }
        return bestScore == 0 ? null : best;
    }

    private static int score(String fileName, String want) {
        if (fileName.equals(want)) {
            return 100;
        }
        boolean wantWeight = want.matches(".*(bold|black|medium|light|thin).*");
        if (!wantWeight && fileName.equals(want + "regular")) {
            return 90;
        }
        return 0;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static List<Path> fontDirs() {
        String home = System.getProperty("user.home");
        List<Path> dirs = new ArrayList<>();
        dirs.add(Path.of("C:/Windows/Fonts"));
        dirs.add(Path.of("/mnt/c/Windows/Fonts"));
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            dirs.add(Path.of(local, "Microsoft", "Windows", "Fonts"));
        }
        dirs.add(Path.of(home, "AppData", "Local", "Microsoft", "Windows", "Fonts"));
        dirs.add(Path.of(home, "Library", "Fonts"));
        dirs.add(Path.of("/Library/Fonts"));
        dirs.add(Path.of("/System/Library/Fonts"));
        dirs.add(Path.of(home, ".local", "share", "fonts"));
        dirs.add(Path.of(home, ".fonts"));
        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg != null && !xdg.isBlank()) {
            dirs.add(Path.of(xdg, "fonts"));
        }
        dirs.add(Path.of("/usr/local/share/fonts"));
        dirs.add(Path.of("/usr/share/fonts"));
        return dirs;
    }

    private record TextStyle(double fontPt, double linePct, double exactLinePt, double spaceBeforePt, double spaceAfterPt, Font font) {
    }
}
