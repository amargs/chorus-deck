package org.amargs.chorusdeck.deck;

import java.util.List;
import java.util.Locale;

public final class Season {

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

    public String coverLayout() {
        return coverLayout;
    }

    public String titleLayout() {
        return titleLayout;
    }

    public String headerScheme() {
        return headerScheme;
    }

    /** The accent the slides use. Festive layouts keep gold without recoloring the header. */
    public String slideAccent() {
        return headerScheme == null ? "accent4" : headerScheme;
    }

    public Season withHeaderScheme(String scheme) {
        if (scheme == null || scheme.equals(headerScheme)) {
            return this;
        }
        return new Season(coverLayout, titleLayout, scheme, tempoName);
    }

    public String tempo(String ano) {
        return tempoName + " · Ano " + ano;
    }

    public boolean omitsGloria() {
        return tempoName.startsWith("Tempo de Advento") || tempoName.startsWith("Tempo de Quaresma");
    }

    public boolean usesLentenAcclamation() {
        return tempoName.startsWith("Tempo de Quaresma");
    }

    public String scriptureBackground() {
        return switch (coverLayout) {
            case "Capa - Domingo III Advento" -> "background_advento_rosa.jpg";
            case "Capa - Domingo IV Quaresma" -> "background_quaresma_rosa.jpg";
            default -> switch (tempoName) {
                case "Tempo de Advento" -> "background_advento.jpg";
                case "Tempo de Natal" -> "background_natal.jpg";
                case "Tempo de Quaresma" -> "background_quaresma.jpg";
                case "Tempo Pascal" -> "background_pascoa.jpg";
                default -> "background_comum.jpg";
            };
        };
    }

    public static List<String> coverLayouts() {
        return List.of(
                "Capa - Tempo Comum",
                "Capa - Advento",
                "Capa - Natal",
                "Capa - Quaresma",
                "Capa - Domingo III Advento",
                "Capa - Domingo IV Quaresma",
                "Capa - Ramos",
                "Capa - Páscoa",
                "Capa - Pentecostes",
                "Capa - Solenidade",
                "Capa - Solenidade Pedro Paulo");
    }

    public static Season resolve(String seasonKey, String coverOverride) {
        Season season = byKey(seasonKey);
        if (coverOverride == null || coverOverride.isBlank()) {
            return season;
        }
        if ("Capa - Solenidade".equals(coverOverride)) {
            return new Season(coverOverride, FESTIVE_TITLE, null, season.tempoName);
        }
        if ("Capa - Solenidade Pedro Paulo".equals(coverOverride)) {
            return new Season(coverOverride, ORDINARY_TITLE, "accent5", season.tempoName);
        }
        Season fromCover = byCover(coverOverride);
        if (fromCover != null) {
            return fromCover;
        }
        return new Season(coverOverride, season.titleLayout, season.headerScheme, season.tempoName);
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
            case "Capa - Domingo III Advento" -> rose(name, "Tempo de Advento");
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
        return new Season("Capa - Advento", ORDINARY_TITLE, "accent2", "Tempo de Advento");
    }

    private static Season natal() {
        return new Season("Capa - Natal", FESTIVE_TITLE, null, "Tempo de Natal");
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
