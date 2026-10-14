package org.amargs.chorusdeck.text;

import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSheet;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.openxmlformats.schemas.drawingml.x2006.main.CTOfficeStyleSheet;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextListStyle;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraphProperties;
import org.openxmlformats.schemas.presentationml.x2006.main.CTPlaceholder;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Installed typefaces, and the Latin family a shape inherits from the theme. */
final class Fonts {

    private static final Map<String, Font> LOADED = new ConcurrentHashMap<>();

    private Fonts() {
    }

    static String latinFamily(XSLFTextShape shape) {
        String typeface = null;
        if (shape.getXmlObject() instanceof CTShape ctShape && ctShape.getTxBody() != null && ctShape.getTxBody().getLstStyle() != null) {
            CTTextListStyle styles = ctShape.getTxBody().getLstStyle();
            if (styles.isSetLvl1PPr()) {
                typeface = typeface(styles.getLvl1PPr(), null);
            }
        }
        if (typeface == null || typeface.startsWith("+")) {
            typeface = latinFamily(shape, typeface);
        }
        return typeface;
    }

    static String latinFamily(XSLFTextShape shape, String current) {
        XSLFSlideMaster master = master(shape);
        if (master == null || master.getTheme() == null) {
            return current == null ? "IBM Plex Sans" : current;
        }
        CTOfficeStyleSheet theme = master.getTheme().getXmlObject();
        boolean major = titlePlaceholder(shape) || (current != null && current.startsWith("+mj"));
        String resolved = major
                ? theme.getThemeElements().getFontScheme().getMajorFont().getLatin().getTypeface()
                : theme.getThemeElements().getFontScheme().getMinorFont().getLatin().getTypeface();
        return resolved == null || resolved.isBlank() ? "IBM Plex Sans" : resolved;
    }

    static boolean fontFileAvailable(String family) {
        return load(family) != null;
    }

    static boolean titlePlaceholder(XSLFTextShape shape) {
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

    static String typeface(CTTextParagraphProperties level, String fallback) {
        CTTextCharacterProperties rPr = level.isSetDefRPr() ? level.getDefRPr() : null;
        if (rPr != null && rPr.isSetLatin() && rPr.getLatin().getTypeface() != null && !rPr.getLatin().getTypeface().isBlank()) {
            return rPr.getLatin().getTypeface();
        }
        return fallback;
    }

    static Font baseFont(String family) {
        if (family == null || family.isBlank()) {
            return null;
        }
        return LOADED.computeIfAbsent(family, Fonts::load);
    }

    static Font boldFont(String family, Font regular) {
        if (family == null || family.isBlank()) {
            return regular == null ? null : regular.deriveFont(Font.BOLD);
        }
        return LOADED.computeIfAbsent(family + "\0bold", key -> {
            Font loaded = load(boldFamilyName(family));
            if (loaded != null) {
                return loaded;
            }
            return regular == null ? new Font(Font.SANS_SERIF, Font.BOLD, 12) : regular.deriveFont(Font.BOLD);
        });
    }

    private static String boldFamilyName(String family) {
        String stripped = family.replaceAll("(?i)\\s+(semi\\s*bold|semibold|medium|light|thin|black|regular|bold)$", "").trim();
        return stripped + " Bold";
    }

    private static Font load(String family) {
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
}
