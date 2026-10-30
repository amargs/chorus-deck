package org.amargs.chorusdeck.liturgy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every liturgical Sunday and the parish solemnities, once each.
 * A Sunday of year A is one row, whichever civil date it falls on.
 */
public final class Lectionary {

    public record Entry(
            String key,
            String name,
            String domingo,
            String season,
            String ano,
            String cover,
            boolean lenten,
            boolean solemnity,
            int place) {
    }

    private record Spec(String id, boolean cycled, String season, boolean lenten, boolean solemnity, String cover) {
    }

    private static final List<Entry> ALL;
    private static final Map<String, Entry> BY_KEY;

    static {
        List<Entry> entries = new ArrayList<>();
        List<Spec> sequence = sequence();
        for (String cycle : List.of("A", "B", "C")) {
            for (int place = 0; place < sequence.size(); place++) {
                Spec spec = sequence.get(place);
                if (!spec.cycled && !"A".equals(cycle)) {
                    continue;
                }
                String key = spec.cycled ? spec.id + "/" + cycle : spec.id;
                entries.add(new Entry(
                        key,
                        name(spec.id),
                        coverTitle(spec.id),
                        spec.season,
                        spec.cycled ? cycle : null,
                        spec.cover,
                        spec.lenten,
                        spec.solemnity,
                        place));
            }
        }
        ALL = List.copyOf(entries);
        Map<String, Entry> byKey = new LinkedHashMap<>();
        for (Entry entry : ALL) {
            byKey.put(entry.key, entry);
        }
        BY_KEY = Map.copyOf(byKey);
    }

    private Lectionary() {
    }

    public static List<Entry> all() {
        return ALL;
    }

    public static Entry find(String key) {
        if (key == null) {
            return null;
        }
        return BY_KEY.get(key);
    }

    private static List<Spec> sequence() {
        List<Spec> specs = new ArrayList<>();
        specs.add(spec("advent-1", true, "advento", false, false, null));
        specs.add(spec("advent-2", true, "advento", false, false, null));
        specs.add(fixed("immaculate-conception", "advento"));
        specs.add(spec("advent-3", true, "advento", false, false, "Capa - Domingo III Advento"));
        specs.add(spec("advent-4", true, "advento", false, false, null));
        specs.add(spec("christmas", false, "natal", false, true, null));
        specs.add(spec("holy-family", true, "natal", false, false, null));
        specs.add(fixed("mary-mother-of-god", "natal"));
        specs.add(spec("epiphany", true, "natal", false, false, null));
        specs.add(spec("baptism-of-the-lord", true, "natal", false, false, null));
        for (int number = 2; number <= 9; number++) {
            specs.add(spec("ordinary-" + number, true, "comum", false, false, null));
        }
        for (int number = 1; number <= 5; number++) {
            String cover = number == 4 ? "Capa - Domingo IV Quaresma" : null;
            specs.add(spec("lent-" + number, true, "quaresma", true, false, cover));
        }
        specs.add(spec("palm-sunday", true, "ramos", true, false, null));
        specs.add(spec("easter-sunday", true, "pascoa", false, false, null));
        for (int number = 2; number <= 6; number++) {
            specs.add(spec("easter-" + number, true, "pascoa", false, false, null));
        }
        specs.add(spec("easter-7", true, "pascoa", false, false, null));
        specs.add(spec("pentecost", true, "pentecostes", false, false, null));
        specs.add(spec("trinity", true, "comum", false, false, null));
        specs.add(spec("corpus-christi", true, "comum", false, true, null));
        specs.add(fixed("john-the-baptist", "comum"));
        specs.add(fixed("peter-paul", "comum"));
        for (int number = 10; number <= 19; number++) {
            specs.add(spec("ordinary-" + number, true, "comum", false, false, null));
        }
        specs.add(fixed("assumption", "comum"));
        for (int number = 20; number <= 30; number++) {
            specs.add(spec("ordinary-" + number, true, "comum", false, false, null));
        }
        specs.add(fixed("all-saints", "comum"));
        for (int number = 31; number <= 33; number++) {
            specs.add(spec("ordinary-" + number, true, "comum", false, false, null));
        }
        specs.add(spec("ordinary-34", true, "comum", false, false, null));
        return specs;
    }

    private static Spec spec(String id, boolean cycled, String season, boolean lenten, boolean solemnity, String cover) {
        return new Spec(id, cycled, season, lenten, solemnity, cover);
    }

    private static Spec fixed(String key, String season) {
        FixedSolemnity solemnity = FixedSolemnity.byKey(key);
        return new Spec(key, false, season, false, true, solemnity.cover());
    }

    private static String name(String id) {
        FixedSolemnity fixed = FixedSolemnity.byKey(id);
        if (fixed != null) {
            return fixed.title();
        }
        String roman = SundayLibrary.week(id);
        return switch (id) {
            case "christmas" -> "Solenidade do Natal do Senhor";
            case "corpus-christi" -> SundayLibrary.CORPUS_CHRISTI;
            case "palm-sunday" -> "Domingo de Ramos";
            case "pentecost" -> "Domingo de Pentecostes";
            case "easter-sunday" -> "Domingo de Páscoa";
            case "easter-7" -> "Solenidade da Ascensão do Senhor";
            case "trinity" -> "Solenidade da Santíssima Trindade";
            case "ordinary-34" -> "Solenidade de Nosso Senhor Jesus Cristo, Rei do Universo";
            case "holy-family" -> "Festa da Sagrada Família de Jesus, Maria e José";
            case "epiphany" -> "Solenidade da Epifania do Senhor";
            case "baptism-of-the-lord" -> "Solenidade do Batismo do Senhor";
            default -> roman == null ? id : roman + " Domingo " + seasonName(id);
        };
    }

    private static String seasonName(String id) {
        int dash = id.indexOf('-');
        String kind = dash < 0 ? id : id.substring(0, dash);
        return switch (kind) {
            case "advent" -> "do Advento";
            case "lent" -> "da Quaresma";
            case "easter" -> "da Páscoa";
            default -> "do Tempo Comum";
        };
    }

    /** The title stored for the cover. Numbered Sundays stay "Domingo II". */
    private static String coverTitle(String id) {
        String stored = SundayLibrary.domingo(id);
        if (!stored.equals(id)) {
            return stored;
        }
        return name(id);
    }
}
