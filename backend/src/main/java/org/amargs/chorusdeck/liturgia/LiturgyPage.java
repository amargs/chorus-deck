package org.amargs.chorusdeck.liturgia;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LiturgyPage {

    private static final Set<String> SMALL = Set.of(
            "a", "o", "e", "de", "da", "do", "das", "dos", "na", "no", "nas", "nos", "em");

    private static final Pattern HEADING = Pattern.compile(
            "(?m)^[ \\t]*((?:DOMINGO|NATAL|SEGUNDA-FEIRA|TERÇA-FEIRA|QUARTA-FEIRA|QUINTA-FEIRA|SEXTA-FEIRA|SÁBADO|EPIFANIA|ASCENSÃO|BATISMO)[^\\n]{0,180})[ \\t]*$");

    private static final Pattern MARKER = Pattern.compile(
            "(?i)LEITURA\\s+II|LEITURA\\s+I(?!I)|SALMO\\s+RESPONSORIAL"
                    + "|EVANGELHO\\s*[–—-]\\s*FORMA\\s+LONGA|EVANGELHO\\s*[–—-]\\s*FORMA\\s+BREVE"
                    + "|(?<![\\p{L}])EVANGELHO(?!\\s+de\\b)");

    private static final Pattern REFERENCE = Pattern.compile(
            "^(?:[1-3]\\s*)?\\p{L}+\\.?(?:\\s+\\p{L}+\\.?)?(?:\\s+\\d+)?(?:\\s*\\(\\d+\\))?\\s*,\\s*"
                    + "\\d+[a-z]{0,2}(?:\\.[a-z]{0,2})?"
                    + "(?:\\s*[.\\-–—]\\s*\\d+[a-z]{0,2}(?:\\.[a-z]{0,2})?)*"
                    + "(?:\\s*,\\s*\\d+[a-z]{0,2}(?:\\.[a-z]{0,2})?(?:\\s*[.\\-–—]\\s*\\d+[a-z]{0,2}(?:\\.[a-z]{0,2})?)*)*");

    private static final Pattern REFRAIN = Pattern.compile("(?i)Refrão:\\s*(.+?)(?:\\s*Repete-se|\\s*Ou:|$)");

    private static final Pattern BODY = Pattern.compile(
            "(?i)\\s+(?:Leitura\\s+d[oa]|Evangelho\\s+de|Todos\\s+os\\s+evangelistas|Naquele\\s+tempo|Palavra\\s+d[oa])\\b");

    private static final Pattern ANO = Pattern.compile("(?i)(?<![\\p{L}])Ano\\s+([ABC])\\b");

    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?[0-9a-fA-F]+);");

    private LiturgyPage() {
    }

    public static boolean challenge(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("just a moment")
                || lower.contains("cf-challenge")
                || lower.contains("cdn-cgi/challenge")
                || lower.contains("enable javascript and cookies");
    }

    public static LiturgyDay parse(LocalDate date, String html) {
        if (challenge(html)) {
            throw new IllegalArgumentException("That file is not a liturgy page for " + date + ".");
        }
        String text = toText(html);
        String heading = heading(text);
        String flat = text.replaceAll("\\s+", " ").trim();
        SeasonChoice season = season(heading);
        int regionAt = indexOfIgnoreCase(flat, "LITURGIA DA PALAVRA");
        String region = regionAt >= 0 ? flat.substring(regionAt) : flat;
        ParsedReadings readings = readings(region);
        return new LiturgyDay(
                date,
                domingo(heading),
                season.key,
                season.cover,
                year(flat, date),
                readings.primeira,
                readings.segunda,
                readings.refrao,
                readings.evangelho,
                null);
    }

    static String toText(String html) {
        String stripped = html
                .replaceAll("(?is)<script\\b[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style\\b[^>]*>.*?</style>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(?:p|div|h[1-6]|li|tr|section|article|header|blockquote)>", "\n")
                .replaceAll("<[^>]+>", " ");
        return unescape(stripped).replace('\u00a0', ' ');
    }

    private static String heading(String text) {
        Matcher matcher = HEADING.matcher(text);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Liturgy page has no day heading.");
        }
        return matcher.group(1).trim().replaceAll("\\s+", " ");
    }

    private static String year(String flat, LocalDate date) {
        int leitura = indexOfIgnoreCase(flat, "LEITURA I");
        String head = leitura >= 0 ? flat.substring(0, leitura) : flat;
        Matcher ano = ANO.matcher(head);
        if (ano.find()) {
            return ano.group(1).toUpperCase(Locale.ROOT);
        }
        return LiturgicalYear.cycle(date);
    }

    private static ParsedReadings readings(String region) {
        List<Section> sections = sections(region);
        LiturgyDay.Reading primeira = null;
        LiturgyDay.Reading segunda = null;
        LiturgyDay.Reading evangelho = null;
        boolean longGospel = false;
        String refrao = null;
        for (Section section : sections) {
            switch (section.kind) {
                case LEITURA_I -> primeira = reading(section.text);
                case LEITURA_II -> segunda = reading(section.text);
                case SALMO -> refrao = refrain(section.text);
                case FORMA_LONGA -> {
                    evangelho = reading(section.text);
                    longGospel = true;
                }
                case EVANGELHO, FORMA_BREVE -> {
                    if (!longGospel) {
                        evangelho = reading(section.text);
                    }
                }
                case SKIP -> {
                }
            }
        }
        if (primeira == null) {
            throw new IllegalArgumentException("Liturgy page has no first reading.");
        }
        if (refrao == null) {
            throw new IllegalArgumentException("Liturgy page has no psalm refrain.");
        }
        if (evangelho == null) {
            throw new IllegalArgumentException("Liturgy page has no gospel.");
        }
        return new ParsedReadings(primeira, segunda, refrao, evangelho);
    }

    private static List<Section> sections(String region) {
        Matcher matcher = MARKER.matcher(region);
        List<int[]> hits = new ArrayList<>();
        List<Kind> kinds = new ArrayList<>();
        while (matcher.find()) {
            hits.add(new int[] {matcher.start(), matcher.end()});
            kinds.add(kind(matcher.group()));
        }
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            int from = hits.get(i)[1];
            int to = i + 1 < hits.size() ? hits.get(i + 1)[0] : region.length();
            sections.add(new Section(kinds.get(i), region.substring(from, to)));
        }
        return sections;
    }

    private static Kind kind(String label) {
        String folded = fold(label);
        if (folded.startsWith("LEITURA II")) {
            return Kind.LEITURA_II;
        }
        if (folded.startsWith("LEITURA I")) {
            return Kind.LEITURA_I;
        }
        if (folded.startsWith("SALMO")) {
            return Kind.SALMO;
        }
        if (folded.contains("FORMA LONGA")) {
            return Kind.FORMA_LONGA;
        }
        if (folded.contains("FORMA BREVE")) {
            return Kind.FORMA_BREVE;
        }
        if (folded.startsWith("EVANGELHO")) {
            return Kind.EVANGELHO;
        }
        return Kind.SKIP;
    }

    private static LiturgyDay.Reading reading(String chunk) {
        String head = cutBody(chunk.replaceAll("\\s+", " ").trim());
        int open = head.indexOf('«');
        int close = open < 0 ? -1 : head.indexOf('»', open + 1);
        if (open >= 0 && close > open) {
            String referencia = head.substring(0, open).trim().replaceAll("\\s+", " ");
            String citacao = head.substring(open + 1, close).trim().replaceAll("\\s+", " ");
            if (referencia.isEmpty() || citacao.isEmpty()) {
                throw new IllegalArgumentException("A reading heading is missing its reference or citation.");
            }
            return new LiturgyDay.Reading(referencia, citacao);
        }
        Matcher reference = REFERENCE.matcher(head);
        if (!reference.find()) {
            throw new IllegalArgumentException("A reading heading is missing its reference or citation.");
        }
        String citacao = head.substring(reference.end()).trim().replaceAll("\\s+", " ");
        if (citacao.isEmpty()) {
            throw new IllegalArgumentException("A reading heading is missing its reference or citation.");
        }
        return new LiturgyDay.Reading(reference.group().trim().replaceAll("\\s+", " "), citacao);
    }

    private static String cutBody(String flat) {
        Matcher body = BODY.matcher(flat);
        if (body.find()) {
            return flat.substring(0, body.start()).trim();
        }
        return flat;
    }

    private static String refrain(String chunk) {
        Matcher matcher = REFRAIN.matcher(chunk.replaceAll("\\s+", " "));
        if (!matcher.find()) {
            throw new IllegalArgumentException("Liturgy page has no psalm refrain.");
        }
        String text = matcher.group(1).trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Liturgy page has no psalm refrain.");
        }
        return text;
    }

    static String domingo(String heading) {
        String trimmed = heading.trim().replaceAll("\\s+", " ");
        Matcher roman = Pattern.compile("(?i)^DOMINGO\\s+([IVXLCDM]+)\\b").matcher(trimmed);
        if (roman.find()) {
            return "Domingo " + roman.group(1).toUpperCase(Locale.ROOT);
        }
        String[] words = trimmed.toLowerCase(Locale.forLanguageTag("pt")).split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            String word = words[i];
            if (word.matches("[ivxlcdm]+")) {
                out.append(word.toUpperCase(Locale.ROOT));
            } else if (i > 0 && SMALL.contains(word)) {
                out.append(word);
            } else if (!word.isEmpty()) {
                out.append(Character.toTitleCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    private static SeasonChoice season(String heading) {
        String key = fold(heading);
        if (key.contains("RAMOS")) {
            return new SeasonChoice("ramos", null);
        }
        if (key.contains("PENTECOSTES")) {
            return new SeasonChoice("pentecostes", null);
        }
        if (key.matches(".*\\bDOMINGO\\s+III\\s+DO\\s+ADVENTO\\b.*")) {
            return new SeasonChoice("advento", "Capa - Domingo III Advento");
        }
        if (key.matches(".*\\bDOMINGO\\s+IV\\s+DA\\s+QUARESMA\\b.*")) {
            return new SeasonChoice("quaresma", "Capa - Domingo IV Quaresma");
        }
        if (key.contains("ADVENTO")) {
            return new SeasonChoice("advento", null);
        }
        if (key.contains("QUARESMA") || key.contains("CINZAS")) {
            return new SeasonChoice("quaresma", null);
        }
        if (key.contains("NATAL") || key.contains("SAGRADA FAMILIA") || key.contains("EPIFANIA") || key.contains("BATISMO")) {
            return new SeasonChoice("natal", null);
        }
        if (key.contains("PASCOA") || key.contains("PASCAL") || key.contains("ASCENSAO")) {
            return new SeasonChoice("pascoa", null);
        }
        if (key.contains("TEMPO COMUM") || key.contains("CRISTO REI")) {
            return new SeasonChoice("comum", null);
        }
        throw new IllegalArgumentException("Unknown liturgical day: " + heading.trim());
    }

    private static String fold(String raw) {
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return decomposed.toUpperCase(Locale.ROOT);
    }

    private static int indexOfIgnoreCase(String text, String needle) {
        return text.toUpperCase(Locale.ROOT).indexOf(needle.toUpperCase(Locale.ROOT));
    }

    private static String unescape(String text) {
        String named = text
                .replace("&nbsp;", " ")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»")
                .replace("&ndash;", "–")
                .replace("&mdash;", "—")
                .replace("&amp;", "&");
        Matcher matcher = NUMERIC_ENTITY.matcher(named);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            int code = token.charAt(0) == 'x' || token.charAt(0) == 'X'
                    ? Integer.parseInt(token.substring(1), 16)
                    : Integer.parseInt(token);
            matcher.appendReplacement(out, Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private enum Kind {
        LEITURA_I, LEITURA_II, SALMO, EVANGELHO, FORMA_LONGA, FORMA_BREVE, SKIP
    }

    private record Section(Kind kind, String text) {
    }

    private record SeasonChoice(String key, String cover) {
    }

    private record ParsedReadings(
            LiturgyDay.Reading primeira,
            LiturgyDay.Reading segunda,
            String refrao,
            LiturgyDay.Reading evangelho) {
    }
}
