package org.amargs.chorusdeck.liturgy;

/** A fixed-date solemnity the parish keeps, matched by the calendar's English name. */
final class FixedSolemnity {

    static final String WHITE_COVER = "Capa - Solenidade";
    static final String PETER_PAUL_COVER = "Capa - Solenidade Pedro Paulo";
    static final String PEACE_COVER = "Capa - Solenidade Paz";

    private final String key;
    private final String apiName;
    private final String title;
    private final String cover;
    private final int month;
    private final int day;

    private FixedSolemnity(String key, String apiName, String title, String cover, int month, int day) {
        this.key = key;
        this.apiName = apiName;
        this.title = title;
        this.cover = cover;
        this.month = month;
        this.day = day;
    }

    String key() {
        return key;
    }

    String title() {
        return title;
    }

    String cover() {
        return cover;
    }

    private static final FixedSolemnity[] ALL = {
            new FixedSolemnity(
                    "mary-mother-of-god",
                    "Solemnity of Mary, the Holy Mother of God",
                    "Solenidade de Santa Maria, Mãe de Deus",
                    PEACE_COVER,
                    1, 1),
            new FixedSolemnity(
                    "john-the-baptist",
                    "The Nativity of Saint John the Baptist",
                    "Solenidade do Nascimento de São João Batista",
                    WHITE_COVER,
                    6, 24),
            new FixedSolemnity(
                    "peter-paul",
                    "Saints Peter and Paul",
                    "Solenidade dos Apóstolos São Pedro e São Paulo",
                    PETER_PAUL_COVER,
                    6, 29),
            new FixedSolemnity(
                    "assumption",
                    "The Assumption of the Blessed Virgin Mary",
                    "Solenidade da Assunção da Virgem Santa Maria",
                    WHITE_COVER,
                    8, 15),
            new FixedSolemnity("all-saints", "All Saints", "Solenidade de Todos os Santos", WHITE_COVER, 11, 1),
            new FixedSolemnity(
                    "immaculate-conception",
                    "The Immaculate Conception of the Blessed Virgin Mary",
                    "Solenidade da Imaculada Conceição da Virgem Santa Maria",
                    WHITE_COVER,
                    12, 8)
    };

    static FixedSolemnity byKey(String key) {
        if (key == null) {
            return null;
        }
        for (FixedSolemnity solemnity : ALL) {
            if (solemnity.key.equals(key)) {
                return solemnity;
            }
        }
        return null;
    }

    static FixedSolemnity on(java.time.LocalDate date) {
        if (date == null) {
            return null;
        }
        for (FixedSolemnity solemnity : ALL) {
            if (solemnity.month == date.getMonthValue() && solemnity.day == date.getDayOfMonth()) {
                return solemnity;
            }
        }
        return null;
    }

    static FixedSolemnity of(String apiName) {
        if (apiName == null) {
            return null;
        }
        for (FixedSolemnity solemnity : ALL) {
            if (solemnity.apiName.equals(apiName)) {
                return solemnity;
            }
        }
        return null;
    }
}
