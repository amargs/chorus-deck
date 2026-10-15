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
    private static Map<String, Path> indexed;

    private Fonts() {
    }

    /** One pass over the font folders, then load each requested family from that index. */
    static List<String> preload(String... families) {
        ensureIndex();
        List<String> missing = new ArrayList<>();
        for (String family : families) {
            Font font = baseFont(family);
            if (font == null && family.endsWith(" Bold")) {
                Font regular = baseFont(family.substring(0, family.length() - " Bold".length()));
                if (regular != null) {
                    font = regular.deriveFont(Font.BOLD);
                    LOADED.put(family, font);
                }
            }
            if (font == null) {
                missing.add(family);
                continue;
            }
            if (family.endsWith(" Bold")) {
                LOADED.putIfAbsent(family.substring(0, family.length() - " Bold".length()) + "\0bold", font);
            }
        }
        return missing;
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
        ensureIndex();
        String want = normalize(family);
        Path exact = indexed.get(want);
        if (exact != null) {
            return exact;
        }
        boolean wantWeight = want.matches(".*(bold|black|medium|light|thin).*");
        if (!wantWeight) {
            Path regular = indexed.get(want + "regular");
            if (regular != null) {
                return regular;
            }
            Path variable = null;
            for (Map.Entry<String, Path> entry : indexed.entrySet()) {
                String name = entry.getKey();
                if (name.startsWith(want) && name.contains("variable") && !name.contains("italic")) {
                    variable = entry.getValue();
                    break;
                }
            }
            return variable;
        }
        return null;
    }

    private static void ensureIndex() {
        if (indexed != null) {
            return;
        }
        Map<String, Path> found = new java.util.HashMap<>();
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
                    found.putIfAbsent(normalize(name.substring(0, name.length() - 4)), file);
                }
            } catch (IOException ignored) {
                // Try the next font directory.
            }
        }
        indexed = found;
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
        addWindowsUserFonts(dirs);
        return dirs;
    }

    private static void addWindowsUserFonts(List<Path> dirs) {
        Path users = Path.of("/mnt/c/Users");
        if (!Files.isDirectory(users)) {
            return;
        }
        try (var list = Files.list(users)) {
            for (Path user : list.filter(Files::isDirectory).toList()) {
                String name = user.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.equals("public") || name.equals("default") || name.equals("default user") || name.equals("all users")) {
                    continue;
                }
                dirs.add(user.resolve("AppData/Local/Microsoft/Windows/Fonts"));
            }
        } catch (IOException ignored) {
            // The system font folders are still searched.
        }
    }
}
