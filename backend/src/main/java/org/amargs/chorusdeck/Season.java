package org.amargs.chorusdeck;

import java.util.Locale;

final class Season {

    static final String ORDINARY_TITLE = "Título e letra";
    static final String FESTIVE_TITLE = "Título e letra - Natal e Páscoa";

    private final String coverLayout;
    private final String titleLayout;
    private final String headerScheme;
    private final String tempoName;

    private Season(String coverLayout, String titleLayout, String headerScheme, String tempoName) {
        this.coverLayout = coverLayout;
        this.titleLayout = titleLayout;
        this.headerScheme = headerScheme;
        this.tempoName = tempoName;
    }

    String coverLayout() {
        return coverLayout;
    }

    String titleLayout() {
        return titleLayout;
    }

    String headerScheme() {
        return headerScheme;
    }

    String tempo(String ano) {
        return tempoName + " · Ano " + ano;
    }

    static Season resolve(String seasonKey, String coverOverride) {
        Season season = byKey(seasonKey);
        if (coverOverride == null || coverOverride.isBlank()) {
            return season;
        }
        Season fromCover = byCover(coverOverride);
        if (fromCover != null) {
            return fromCover;
        }
        return new Season(coverOverride, season.titleLayout, season.headerScheme, season.tempoName);
    }

    static String ano(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("--ano requires A, B, or C.");
        }
        String year = raw.trim().toUpperCase(Locale.ROOT);
        if (!year.equals("A") && !year.equals("B") && !year.equals("C")) {
            throw new IllegalArgumentException("--ano requires A, B, or C.");
        }
        return year;
    }

    private static Season byKey(String raw) {
        String key = normalize(raw);
        return switch (key) {
            case "comum" -> comum();
            case "advento" -> advento();
            case "natal" -> natal();
            case "quaresma" -> quaresma();
            case "ramos" -> ramos();
            case "pascoa" -> pascoa();
            case "pentecostes" -> pentecostes();
            default -> throw new IllegalArgumentException("Unknown season: " + raw);
        };
    }

    private static Season byCover(String name) {
        return switch (name) {
            case "Capa - Tempo Comum" -> comum();
            case "Capa - Advento" -> advento();
            case "Capa - Natal" -> natal();
            case "Capa - Quaresma" -> quaresma();
            case "Capa - Domingo III Advento" -> rose(name, "Tempo do Advento");
            case "Capa - Domingo IV Quaresma" -> rose(name, "Tempo de Quaresma");
            case "Capa - Ramos" -> ramos();
            case "Capa - Páscoa" -> pascoa();
            case "Capa - Pentecostes" -> pentecostes();
            default -> null;
        };
    }

    private static Season comum() {
        return new Season("Capa - Tempo Comum", ORDINARY_TITLE, "accent1", "Tempo Comum");
    }

    private static Season advento() {
        return new Season("Capa - Advento", ORDINARY_TITLE, "accent2", "Tempo do Advento");
    }

    private static Season natal() {
        return new Season("Capa - Natal", FESTIVE_TITLE, null, "Tempo do Natal");
    }

    private static Season quaresma() {
        return new Season("Capa - Quaresma", ORDINARY_TITLE, "accent2", "Tempo de Quaresma");
    }

    private static Season ramos() {
        return new Season("Capa - Ramos", ORDINARY_TITLE, "accent5", "Tempo de Quaresma");
    }

    private static Season pascoa() {
        return new Season("Capa - Páscoa", FESTIVE_TITLE, null, "Tempo Pascal");
    }

    private static Season pentecostes() {
        return new Season("Capa - Pentecostes", ORDINARY_TITLE, "accent5", "Tempo Pascal");
    }

    private static Season rose(String cover, String tempoName) {
        return new Season(cover, ORDINARY_TITLE, "accent3", tempoName);
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("generate requires --season.");
        }
        return raw.trim().toLowerCase(Locale.ROOT)
                .replace("á", "a")
                .replace("ã", "a");
    }
}
