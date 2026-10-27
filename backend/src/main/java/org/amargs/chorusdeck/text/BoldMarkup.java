package org.amargs.chorusdeck.text;

import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BoldMarkup {

    public static final String SEMIBOLD = "IBM Plex Sans SemiBold";
    public static final String TITLE = "Livvic SemiBold";
    public static final String PLAYFAIR = "Playfair Display";
    public static final String PLAYFAIR_BOLD = "Playfair Display Bold";

    private BoldMarkup() {
    }

    public record FontLink(String family, String url) {
    }

    private record FontFace(String installedName, String family, String url) {
    }

    private static final List<FontFace> FACES = List.of(
            new FontFace(SEMIBOLD, "IBM Plex Sans", "https://fonts.google.com/specimen/IBM+Plex+Sans"),
            new FontFace(TITLE, "Livvic", "https://fonts.google.com/specimen/Livvic"),
            new FontFace(PLAYFAIR, "Playfair Display", "https://fonts.google.com/specimen/Playfair+Display"),
            new FontFace(PLAYFAIR_BOLD, "Playfair Display", "https://fonts.google.com/specimen/Playfair+Display"));

    public static void requireDeckFonts() {
        List<String> missing = Fonts.preload(SEMIBOLD, TITLE, PLAYFAIR, PLAYFAIR_BOLD);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    String.join(", ", missing) + (missing.size() == 1 ? " was not found." : " were not found.")
                            + " Install the missing font and run generate again.");
        }
    }

    /** Installed names that are absent, collapsed to one Google Fonts page per family. */
    public static List<FontLink> missingFontLinks() {
        return fontLinks(Fonts.preload(SEMIBOLD, TITLE, PLAYFAIR, PLAYFAIR_BOLD));
    }

    static List<FontLink> fontLinks(List<String> missing) {
        Set<String> names = new LinkedHashSet<>(missing);
        List<FontLink> links = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (FontFace face : FACES) {
            if (names.contains(face.installedName()) && seen.add(face.family())) {
                links.add(new FontLink(face.family(), face.url()));
            }
        }
        return links;
    }

    public static String mark(XSLFTextShape shape) {
        List<String> lines = new ArrayList<>();
        for (XSLFTextParagraph paragraph : shape.getTextParagraphs()) {
            lines.add(markParagraph(paragraph));
        }
        return String.join("\n", lines).trim();
    }

    public static String plain(String marked) {
        if (marked == null || marked.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        List<List<Segment>> lines = parse(marked);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                text.append('\n');
            }
            for (Segment segment : lines.get(i)) {
                text.append(segment.text());
            }
        }
        return text.toString();
    }

    public static String plainIfFullyBold(String marked) {
        if (!fullyBold(marked)) {
            return marked == null ? "" : marked;
        }
        return plain(marked);
    }

    static boolean fullyBold(String marked) {
        if (marked == null || !marked.contains("**")) {
            return false;
        }
        boolean bold = false;
        boolean sawBoldText = false;
        for (int i = 0; i < marked.length(); i++) {
            if (marked.startsWith("**", i)) {
                bold = !bold;
                i++;
                continue;
            }
            if (!Character.isWhitespace(marked.charAt(i))) {
                if (!bold) {
                    return false;
                }
                sawBoldText = true;
            }
        }
        return sawBoldText;
    }

    public static void apply(XSLFTextShape shape, String marked) {
        List<List<Segment>> parsed = parse(marked == null ? "" : marked);
        boolean layoutBold = layoutBold(shape);
        List<XSLFTextParagraph> paragraphs = shape.getTextParagraphs();
        int count = Math.min(paragraphs.size(), parsed.size());
        for (int i = 0; i < count; i++) {
            applyLine(paragraphs.get(i), parsed.get(i), SEMIBOLD, layoutBold);
        }
    }

    private static String markParagraph(XSLFTextParagraph paragraph) {
        StringBuilder text = new StringBuilder();
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        for (XSLFTextRun run : paragraph.getTextRuns()) {
            String raw = run.getRawText();
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            boolean nextBold = run.isBold() && !raw.isBlank();
            boolean nextItalic = run.isItalic() && !raw.isBlank();
            boolean nextUnderline = run.isUnderlined() && !raw.isBlank();
            if (nextBold != bold) {
                text.append("**");
                bold = nextBold;
            }
            if (nextItalic != italic) {
                text.append("*");
                italic = nextItalic;
            }
            if (nextUnderline != underline) {
                text.append("__");
                underline = nextUnderline;
            }
            text.append(raw);
        }
        if (underline) {
            text.append("__");
        }
        if (italic) {
            text.append("*");
        }
        if (bold) {
            text.append("**");
        }
        return text.toString();
    }

    private static List<List<Segment>> parse(String marked) {
        String[] lines = marked.split("\\R", -1);
        List<List<Segment>> paragraphs = new ArrayList<>();
        for (String line : lines) {
            paragraphs.add(parseLine(line));
        }
        return paragraphs;
    }

    private static List<Segment> parseLine(String line) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        for (int i = 0; i < line.length(); ) {
            if (line.startsWith("**", i)) {
                addSegment(segments, current, bold, italic, underline);
                bold = !bold;
                i += 2;
            } else if (line.startsWith("__", i)) {
                addSegment(segments, current, bold, italic, underline);
                underline = !underline;
                i += 2;
            } else if (line.charAt(i) == '*') {
                addSegment(segments, current, bold, italic, underline);
                italic = !italic;
                i += 1;
            } else {
                current.append(line.charAt(i));
                i += 1;
            }
        }
        addSegment(segments, current, bold, italic, underline);
        if (segments.isEmpty()) {
            segments.add(new Segment("", false, false, false));
        }
        return segments;
    }

    private static void addSegment(List<Segment> segments, StringBuilder current, boolean bold, boolean italic, boolean underline) {
        if (current.isEmpty()) {
            return;
        }
        segments.add(new Segment(current.toString(), bold, italic, underline));
        current.setLength(0);
    }

    private static boolean layoutBold(XSLFTextShape shape) {
        if (!(shape.getXmlObject() instanceof CTShape ctShape)
                || ctShape.getTxBody() == null
                || ctShape.getTxBody().getLstStyle() == null
                || !ctShape.getTxBody().getLstStyle().isSetLvl1PPr()) {
            return false;
        }
        CTTextCharacterProperties defaults = ctShape.getTxBody().getLstStyle().getLvl1PPr().getDefRPr();
        return defaults != null && defaults.isSetB() && defaults.getB();
    }

    private static void applyLine(XSLFTextParagraph paragraph, List<Segment> segments, String weightedFamily, boolean layoutBold) {
        List<XSLFTextRun> runs = paragraph.getTextRuns();
        if (runs.isEmpty() || segments.isEmpty()) {
            return;
        }
        XSLFTextRun first = runs.get(0);
        CTTextCharacterProperties base = copyProperties(first);
        writeRun(first, segments.get(0), base, weightedFamily, layoutBold);
        for (int i = 1; i < segments.size(); i++) {
            XSLFTextRun run = paragraph.addNewTextRun();
            writeRun(run, segments.get(i), base, weightedFamily, layoutBold);
        }
    }

    private static void writeRun(XSLFTextRun run, Segment segment, CTTextCharacterProperties base, String weightedFamily, boolean layoutBold) {
        if (base != null && run.getXmlObject() instanceof CTRegularTextRun ctRun) {
            ctRun.setRPr((CTTextCharacterProperties) base.copy());
        }
        run.setText(segment.text());
        if (segment.bold() && weightedFamily != null) {
            run.setBold(false);
            run.setFontFamily(weightedFamily);
        } else if (segment.bold() || layoutBold) {
            run.setBold(true);
        }
        run.setItalic(segment.italic());
        run.setUnderlined(segment.underline());
    }

    private static CTTextCharacterProperties copyProperties(XSLFTextRun run) {
        if (run.getXmlObject() instanceof CTRegularTextRun ctRun && ctRun.isSetRPr()) {
            return (CTTextCharacterProperties) ctRun.getRPr().copy();
        }
        return null;
    }

    private record Segment(String text, boolean bold, boolean italic, boolean underline) {
    }
}
