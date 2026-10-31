package org.amargs.chorusdeck.deck;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which text boxes belong to each estrofe. The generator does not use this;
 * a deck sends the text-box positions it already chose.
 */
public final class Estrofes {

    private static final Pattern RITE = Pattern.compile("^([A-Za-z]+)");
    private static final Set<String> RITES = Set.of("RP", "G", "A", "CR", "S", "P", "C");

    private Estrofes() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Plan(List<Row> rows, String refrain, String where) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Row(int n, String line, List<Integer> slides) {
    }

    /** Absent when the song offers no choice: a rite, no refrão, or a single estrofe. */
    public static Plan plan(List<String> slides, String bookId) {
        if (slides == null || slides.isEmpty() || rite(bookId)) {
            return null;
        }
        List<Kind> kinds = new ArrayList<>();
        for (String slide : slides) {
            kinds.add(kind(slide));
        }
        if (kinds.stream().allMatch(kind -> kind == Kind.REFRAIN)) {
            return null;
        }
        if (kinds.stream().noneMatch(kind -> kind == Kind.REFRAIN || kind == Kind.MIXED)) {
            return null;
        }
        List<Integer> estrofes = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            Kind kind = kinds.get(i);
            if (kind == Kind.VERSE || kind == Kind.MIXED) {
                estrofes.add(i);
            }
        }
        if (estrofes.size() < 2) {
            return null;
        }
        List<Row> rows = new ArrayList<>();
        List<Integer> after = new ArrayList<>();
        List<Integer> inside = new ArrayList<>();
        for (int n = 1; n <= estrofes.size(); n++) {
            int end = n < estrofes.size() ? estrofes.get(n) : slides.size();
            int at = estrofes.get(n - 1);
            int from = n == 1 ? 0 : at;
            List<Integer> kept = new ArrayList<>();
            for (int i = from + 1; i <= end; i++) {
                kept.add(i);
            }
            rows.add(new Row(n, firstLine(slides.get(at)), List.copyOf(kept)));
            if (ownsRefrain(kinds, at, end)) {
                after.add(n);
            } else if (kinds.get(at) == Kind.MIXED) {
                inside.add(n);
            }
        }
        boolean opens = false;
        for (int i = 0; i < estrofes.get(0); i++) {
            if (kinds.get(i) == Kind.REFRAIN) {
                opens = true;
                break;
            }
        }
        return new Plan(List.copyOf(rows), refrainLine(slides, kinds), where(opens, after, inside, estrofes.size()));
    }

    public static boolean rite(String bookId) {
        if (bookId == null || bookId.isBlank()) {
            return false;
        }
        Matcher letters = RITE.matcher(bookId.trim());
        return letters.find() && RITES.contains(letters.group(1).toUpperCase(Locale.ROOT));
    }

    private static boolean ownsRefrain(List<Kind> kinds, int estrofe, int end) {
        for (int i = estrofe + 1; i < end; i++) {
            if (kinds.get(i) == Kind.REFRAIN) {
                return true;
            }
        }
        return false;
    }

    private static String where(boolean opens, List<Integer> after, List<Integer> inside, int estrofes) {
        List<String> parts = new ArrayList<>();
        if (opens) {
            parts.add("no início");
        }
        if (after.size() == estrofes) {
            parts.add("depois de cada estrofe");
        } else if (!after.isEmpty()) {
            parts.add("depois " + ordinals(after));
        }
        if (inside.size() == estrofes) {
            parts.add("no fim de cada estrofe");
        } else if (!inside.isEmpty()) {
            parts.add("no fim " + ordinals(inside));
        }
        return String.join(" e ", parts);
    }

    private static String ordinals(List<Integer> numbers) {
        List<String> parts = new ArrayList<>();
        for (int number : numbers) {
            parts.add("da " + number + ".ª");
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " e " + parts.get(parts.size() - 1);
    }

    private static String refrainLine(List<String> slides, List<Kind> kinds) {
        for (int i = 0; i < slides.size(); i++) {
            if (kinds.get(i) != Kind.REFRAIN && kinds.get(i) != Kind.MIXED) {
                continue;
            }
            for (String line : lines(slides.get(i))) {
                if (bold(line)) {
                    return plain(line);
                }
            }
        }
        return "";
    }

    private static String firstLine(String slide) {
        List<String> plainLines = new ArrayList<>();
        for (String line : lines(slide)) {
            if (bold(line)) {
                break;
            }
            if (!italic(line)) {
                plainLines.add(plain(line));
            }
        }
        if (plainLines.isEmpty()) {
            for (String line : lines(slide)) {
                return plain(line);
            }
            return "";
        }
        String shown = plainLines.get(0);
        if (plainLines.size() == 1) {
            return shown;
        }
        String[] words = plainLines.get(1).split("\\s+");
        int take = Math.min(2, words.length);
        StringBuilder text = new StringBuilder(shown);
        for (int i = 0; i < take; i++) {
            if (!words[i].isBlank()) {
                text.append(' ').append(words[i]);
            }
        }
        return text.toString();
    }

    private static Kind kind(String slide) {
        List<String> lines = lines(slide);
        if (lines.isEmpty()) {
            return Kind.VERSE;
        }
        int bold = 0;
        int italic = 0;
        for (String line : lines) {
            if (bold(line)) {
                bold++;
            } else if (italic(line)) {
                italic++;
            }
        }
        if (bold == lines.size()) {
            return Kind.REFRAIN;
        }
        if (italic == lines.size()) {
            return Kind.PRE;
        }
        if (bold > 0) {
            return Kind.MIXED;
        }
        return Kind.VERSE;
    }

    private static List<String> lines(String slide) {
        List<String> lines = new ArrayList<>();
        if (slide == null) {
            return lines;
        }
        for (String line : slide.split("\\R", -1)) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private static boolean bold(String line) {
        return line.startsWith("**") && line.endsWith("**") && line.length() >= 4;
    }

    private static boolean italic(String line) {
        return line.startsWith("*") && line.endsWith("*") && !line.startsWith("**") && line.length() >= 2;
    }

    private static String plain(String line) {
        if (bold(line)) {
            return line.substring(2, line.length() - 2).trim();
        }
        if (italic(line)) {
            return line.substring(1, line.length() - 1).trim();
        }
        return line.trim();
    }

    private enum Kind {
        REFRAIN, PRE, VERSE, MIXED
    }
}
