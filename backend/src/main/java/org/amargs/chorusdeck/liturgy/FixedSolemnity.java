package org.amargs.chorusdeck.liturgy;

/** A fixed-date solemnity the parish keeps, matched by the calendar's English name. */
final class FixedSolemnity {

    static final String WHITE_COVER = "Capa - Solenidade";
    static final String PETER_PAUL_COVER = "Capa - Solenidade Pedro Paulo";

    private final String apiName;
    private final String title;
    private final String cover;

    private FixedSolemnity(String apiName, String title, String cover) {
        this.apiName = apiName;
        this.title = title;
        this.cover = cover;
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
                    "Natividade de São João Batista",
                    WHITE_COVER),
            new FixedSolemnity(
                    "Saints Peter and Paul",
                    "São Pedro e São Paulo, Apóstolos",
                    PETER_PAUL_COVER),
            new FixedSolemnity(
                    "The Assumption of the Blessed Virgin Mary",
                    "Assunção da Virgem Santa Maria",
                    WHITE_COVER),
            new FixedSolemnity("All Saints", "Todos os Santos", WHITE_COVER),
            new FixedSolemnity(
                    "The Immaculate Conception of the Blessed Virgin Mary",
                    "Imaculada Conceição da Virgem Santa Maria",
                    WHITE_COVER)
    };

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
