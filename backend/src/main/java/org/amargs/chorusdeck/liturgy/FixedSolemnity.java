package org.amargs.chorusdeck.liturgy;

/** A fixed-date solemnity the parish keeps, matched by the calendar's English name. */
final class FixedSolemnity {

    static final String WHITE_COVER = "Capa - Solenidade";
    static final String PETER_PAUL_COVER = "Capa - Solenidade Pedro Paulo";

    private final String apiName;
    private final String title;
    private final String cover;
    private final int month;
    private final int day;

    private FixedSolemnity(String apiName, String title, String cover, int month, int day) {
        this.apiName = apiName;
        this.title = title;
        this.cover = cover;
        this.month = month;
        this.day = day;
    }

    String title() {
        return title;
    }

    String cover() {
        return cover;
    }

    private static final FixedSolemnity[] ALL = {
            new FixedSolemnity(
                    "The Nativity of Saint John the Baptist",
                    "Solenidade do Nascimento de São João Batista",
                    WHITE_COVER,
                    6, 24),
            new FixedSolemnity(
                    "Saints Peter and Paul",
                    "Solenidade de São Pedro e São Paulo",
                    PETER_PAUL_COVER,
                    6, 29),
            new FixedSolemnity(
                    "The Assumption of the Blessed Virgin Mary",
                    "Solenidade da Assunção da Virgem Santa Maria",
                    WHITE_COVER,
                    8, 15),
            new FixedSolemnity("All Saints", "Solenidade de Todos os Santos", WHITE_COVER, 11, 1),
            new FixedSolemnity(
                    "The Immaculate Conception of the Blessed Virgin Mary",
                    "Solenidade da Imaculada Conceição da Virgem Santa Maria",
                    WHITE_COVER,
                    12, 8)
    };

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
