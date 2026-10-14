package org.amargs.chorusdeck.index;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TitleNormalizer {

    private static final Pattern APOSTROPHE = Pattern.compile("[\\u2018\\u2019\\u201B\\u02BC\\u00B4`]");
    private static final Pattern PUNCTUATION = Pattern.compile(
            "[\\p{Punct}&&[^']]|[\\u00AB\\u00BB\\u201C\\u201D\\u201E\\u2013\\u2014\\u2026]");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    private TitleNormalizer() {
    }

    /** Case, spacing, punctuation, and apostrophe shape. Accents stay. */
    public static String normalize(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String text = APOSTROPHE.matcher(title.replace('\u00A0', ' ')).replaceAll("'");
        text = text.toLowerCase(Locale.ROOT);
        text = PUNCTUATION.matcher(text).replaceAll(" ");
        return SPACE.matcher(text).replaceAll(" ").trim();
    }
}
