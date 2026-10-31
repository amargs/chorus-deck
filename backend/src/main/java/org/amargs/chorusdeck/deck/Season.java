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

    /** Wash behind the readings. Rose, red, blue, and orange washes are not in assets yet, so those covers use green. A white solemnity uses gold. */
    public String scriptureBackground() {
        if ("Capa - Domingo III Advento".equals(coverLayout) || "Capa - Domingo IV Quaresma".equals(coverLayout)) {
            return "background_green.jpg";
        }
        if ("Capa - Solenidade".equals(coverLayout)) {
            return "background_gold.jpg";
        }
        return switch (tempoName) {
            case "Tempo de Advento", "Tempo de Quaresma" -> "background_purple.jpg";
            case "Tempo de Natal", "Tempo Pascal" -> "background_gold.jpg";
            default -> "background_green.jpg";
        };
    }

    /** Colors and logo painted onto the single {@code Capa} layout. */
    public CoverPaint coverPaint() {
        return switch (coverLayout) {
            case "Capa - Advento" -> scheme("accent2", 20_000, 80_000, 40_000, 0, 0, "logo_advento.png");
            case "Capa - Quaresma" -> scheme("accent2", 20_000, 80_000, 40_000, 0, 0, "logo_quaresma.png");
            case "Capa - Domingo III Advento" -> scheme("accent3", 40_000, 60_000, 25_000, 0, 0, "logo_gaudete.png");
            case "Capa - Domingo IV Quaresma" -> scheme("accent3", 40_000, 60_000, 25_000, 0, 0, "logo_laetere.png");
            case "Capa - Natal" -> scheme("accent4", 20_000, 80_000, 20_000, 0, 0, "logo_natal.png");
            case "Capa - Páscoa" -> scheme("accent4", 20_000, 80_000, 20_000, 0, 0, "logo_pascoa.png");
            case "Capa - Solenidade" -> scheme("accent4", 20_000, 80_000, 20_000, 0, 0, "logo_comunhao.png");
            case "Capa - Ramos" -> scheme("accent5", 60_000, 40_000, 20_000, 0, 0, "logo_ramos.png");
            case "Capa - Pentecostes" -> scheme("accent5", 60_000, 40_000, 20_000, 0, 0, "logo_espirito.png");
            case "Capa - Solenidade Pedro Paulo" -> scheme("accent5", 60_000, 40_000, 20_000, 0, 0, "logo_pedro_paulo.png");
            case "Capa - Solenidade Paz" -> scheme("accent6", 40_000, 60_000, 20_000, 75_000, 0, "logo_paz.png");
            case "Capa - Maria" -> scheme("accent6", 40_000, 60_000, 20_000, 75_000, 0, "logo_maria.png");
            case "Capa - Taizé" -> new CoverPaint(null, "E37931", 0, 0, 100_000, "accent4", 60_000, 40_000, "logo_taize.png");
            default -> scheme("accent1", 20_000, 80_000, 25_000, 0, 0, "logo_comum.png");
        };
    }

    private static CoverPaint scheme(
            String accent, int lumMod, int lumOff, int alpha, int textLumMod, int textLumOff, String logo) {
        return new CoverPaint(accent, null, lumMod, lumOff, alpha, accent, textLumMod, textLumOff, logo);
    }

    /**
     * Background and Domingo color taken from the old cover layouts.
     * {@code lumMod} and {@code lumOff} are DrawingML percentages (100000 = 100%).
     * {@code alpha} is opacity on the same scale. A text luminance of 0 leaves Domingo at the full accent.
     */
    public record CoverPaint(
            String backgroundScheme,
            String backgroundRgb,
            int lumMod,
            int lumOff,
            int alpha,
            String textScheme,
            int textLumMod,
            int textLumOff,
            String logo) {
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
                "Capa - Solenidade Pedro Paulo",
                "Capa - Solenidade Paz",
                "Capa - Maria",
                "Capa - Taizé");
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
        if ("Capa - Solenidade Paz".equals(coverOverride) || "Capa - Maria".equals(coverOverride)) {
            return new Season(coverOverride, ORDINARY_TITLE, "accent6", season.tempoName);
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
