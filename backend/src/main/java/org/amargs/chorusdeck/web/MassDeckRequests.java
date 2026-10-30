package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.amargs.chorusdeck.deck.DeckSpec;
import org.amargs.chorusdeck.deck.MassRequest;
import org.amargs.chorusdeck.deck.Season;
import org.amargs.chorusdeck.deck.SlideChoice;
import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.liturgy.LiturgyClient;
import org.amargs.chorusdeck.liturgy.LiturgyDay;
import org.amargs.chorusdeck.liturgy.SundayLibrary;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Eucaristia page's liturgy preview, and the mass body it posts. */
public final class MassDeckRequests {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MassDeckRequests() {
    }

    public record Ready(
            MassRequest request,
            Path output,
            String domingo,
            String versiculo,
            boolean solemnity,
            String primeiraRef,
            String segundaRef,
            String accent,
            boolean manual,
            String cover,
            Map<String, SlideChoice> choices) {
    }

    public static Map<String, Object> preview(LiturgyDay day) {
        Season season = Season.resolve(day.seasonKey(), day.coverLayout());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", day.date().toString());
        body.put("domingo", text(day.domingo()));
        body.put("season", day.seasonKey());
        body.put("ano", text(day.ano()));
        body.put("frase", text(day.evangelho() == null ? null : day.evangelho().citacao()));
        body.put("versiculo", text(day.evangelho() == null ? null : day.evangelho().referencia()));
        body.put("primeira", reading(day.primeira()));
        body.put("segunda", reading(day.segunda()));
        body.put("salmo", text(day.refrao()));
        body.put("louvor", text(day.louvor()));
        body.put("omitsGloria", season.omitsGloria());
        body.put("lenten", season.usesLentenAcclamation());
        body.put("theme", theme(day));
        body.put("accent", accent(theme(day)));
        body.put("cover", season.coverLayout());
        if (day.domingo() != null && !day.domingo().isBlank() && day.ano() != null && !day.ano().isBlank()) {
            body.put("fileName", DeckSpec.fileName(day.date(), day.domingo(), day.seasonKey(), day.ano()));
        }
        body.put("warnings", warnings(day, season));
        Map<String, Object> overlap = solemnity(day);
        if (overlap != null) {
            body.put("solemnity", overlap);
        }
        return body;
    }

    public static MassRequest apply(Ready ready, LiturgyDay day) {
        String verse = ready.versiculo();
        if (verse == null || verse.isBlank()) {
            verse = null;
        }
        LiturgyClient.SundaySolemnity overlap = null;
        LiturgyDay source = day;
        if (ready.solemnity()) {
            overlap = LiturgyClient.sundayOverlap(day.date(), day.seasonKey());
            if (overlap == null) {
                throw new IllegalArgumentException("This date is not a Sunday solemnity.");
            }
            if (overlap.outranksSunday()) {
                source = blankScriptures(day);
            }
        }
        MassRequest mass = ready.request().withLiturgy(source).withCoverFields(ready.domingo(), verse)
                .withReadingReferences(ready.primeiraRef(), ready.segundaRef());
        if (overlap != null) {
            if (ready.domingo() == null || ready.domingo().isBlank()) {
                mass = mass.withCoverFields(overlap.title(), verse);
            }
            mass = mass.withCoverLayout(overlap.cover())
                    .withReadingReferences(ready.primeiraRef(), ready.segundaRef());
        }
        if (ready.cover() != null && !ready.cover().isBlank()) {
            mass = mass.withCoverLayout(coverLayout(ready.cover()));
        }
        return mass.withAccent(ready.accent());
    }

    private static LiturgyDay blankScriptures(LiturgyDay day) {
        LiturgyDay.Reading blank = new LiturgyDay.Reading("", "");
        return new LiturgyDay(
                day.date(),
                day.domingo(),
                day.seasonKey(),
                day.coverLayout(),
                day.ano(),
                blank,
                blank,
                "",
                blank,
                day.louvor());
    }

    private static String coverLayout(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (!Season.coverLayouts().contains(name)) {
            throw new IllegalArgumentException("Unknown cover: " + name);
        }
        return name;
    }

    public static LocalDate date(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("date must be YYYY-MM-DD.");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date must be YYYY-MM-DD.");
        }
    }

    public static Ready parse(JsonNode body, List<SongCfg> songs, Path directory) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("The generate request must be an object.");
        }
        LocalDate date = date(text(body, "date"));
        JsonNode moments = body.get("songs");
        ObjectNode normalized = JSON.createObjectNode();
        Map<String, SlideChoice> choices = new LinkedHashMap<>();
        if (moments != null && !moments.isNull()) {
            if (!moments.isObject()) {
                throw new IllegalArgumentException("songs must be a mapping.");
            }
            moments.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    return;
                }
                String bookId;
                SlideChoice choice = null;
                if (value.isTextual()) {
                    bookId = value.asText().trim();
                } else if (value.isObject()) {
                    bookId = text(value, "bookId");
                    if (bookId == null) {
                        throw new IllegalArgumentException("A song moment needs a songbook id.");
                    }
                    choice = SlideChoice.from(value);
                } else {
                    throw new IllegalArgumentException("Song moments take a songbook id.");
                }
                if (bookId.isEmpty()) {
                    return;
                }
                checkBook(songs, bookId, choice);
                normalized.put(entry.getKey(), bookId);
                if (choice != null) {
                    choices.put(entry.getKey(), choice);
                }
            });
        }
        ObjectNode request = JSON.createObjectNode();
        request.put("date", date.toString());
        if (moments != null && !moments.isNull()) {
            request.set("songs", normalized);
        }
        copyGroup(body, request, "leituras");
        copyGroup(body, request, "opcoes");
        Path folder = LocalFiles.folder(text(body, "directory"), directory);
        JsonNode references = body.get("referencias");
        return new Ready(
                MassRequest.from(request, folder.resolve("eucaristia.json")),
                LocalFiles.deckFile(text(body, "output"), folder),
                text(body, "domingo"),
                text(body, "versiculo"),
                body.path("solemnity").asBoolean(false),
                text(references, "primeira"),
                text(references, "segunda"),
                accent(text(body, "accent")),
                body.path("manual").asBoolean(false),
                text(body, "cover"),
                choices);
    }

    private static void checkBook(List<SongCfg> songs, String bookId, SlideChoice choice) {
        SongCfg found = null;
        for (SongCfg song : songs) {
            if (!bookId.equals(song.bookId())) {
                continue;
            }
            if (found != null) {
                throw new IllegalArgumentException("Songbook id " + bookId + " matches more than one song.");
            }
            found = song;
        }
        if (found == null) {
            throw new IllegalArgumentException("Unknown songbook id: " + bookId);
        }
        if (choice == null) {
            SongCfg shown = found.prepared();
            if (shown.content() == null || shown.content().isEmpty()) {
                throw new IllegalArgumentException("Song " + found.id() + " has no verses.");
            }
            return;
        }
        choice.show(found);
    }

    private static void copyGroup(JsonNode body, ObjectNode request, String name) {
        JsonNode group = body.get(name);
        if (group != null && !group.isNull()) {
            request.set(name, group);
        }
    }

    private static Map<String, String> reading(LiturgyDay.Reading reading) {
        if (reading == null) {
            return null;
        }
        Map<String, String> view = new LinkedHashMap<>();
        view.put("referencia", text(reading.referencia()));
        view.put("citacao", text(reading.citacao()));
        return view;
    }

    private static Map<String, Object> solemnity(LiturgyDay day) {
        LiturgyClient.SundaySolemnity overlap = LiturgyClient.sundayOverlap(day.date(), day.seasonKey());
        if (overlap == null) {
            return weekdaySolemnity(day);
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("title", overlap.title());
        view.put("cover", overlap.cover());
        view.put("outranks", overlap.outranksSunday());
        view.put("accent", solemnityAccent(overlap.cover()));
        view.put("warning", overlapWarning(day, overlap));
        if (day.ano() != null && !day.ano().isBlank()) {
            view.put("fileName", DeckSpec.fileName(day.date(), overlap.title(), day.seasonKey(), day.ano()));
        }
        return view;
    }

    private static String overlapWarning(LiturgyDay day, LiturgyClient.SundaySolemnity overlap) {
        String when = day.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if (overlap.outranksSunday()) {
            return "O Domingo " + when + " é também " + overlap.title()
                    + ". A celebração da solenidade prevalece sobre a liturgia de Domingo.";
        }
        return "O Domingo " + when + " é também " + overlap.title() + ". No entanto, a liturgia de "
                + day.domingo() + " do tempo de " + seasonName(day.seasonKey())
                + " prevalece sobre esta festa/solenidade.";
    }

    private static String seasonName(String seasonKey) {
        return switch (seasonKey == null ? "" : seasonKey) {
            case "advento" -> "Advento";
            case "quaresma", "ramos" -> "Quaresma";
            case "pascoa" -> "Páscoa";
            default -> seasonKey == null ? "" : seasonKey;
        };
    }

    private static Map<String, Object> weekdaySolemnity(LiturgyDay day) {
        LiturgyClient.WeekdaySolemnity solemnity = LiturgyClient.weekdaySolemnity(day.date());
        if (solemnity == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("title", solemnity.title());
        view.put("cover", solemnity.cover());
        view.put("warning", day.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " é " + solemnity.title() + ".");
        view.put("link", true);
        return view;
    }

    static String accent(String theme) {
        if (theme == null || theme.isBlank()) {
            return null;
        }
        return switch (theme) {
            case "accent1", "accent2", "accent3", "accent4", "accent5", "accent6" -> theme;
            case "advento", "quaresma" -> "accent2";
            case "rosa" -> "accent3";
            case "natal", "pascoa" -> "accent4";
            case "ramos", "pentecostes" -> "accent5";
            case "comum" -> "accent1";
            default -> throw new IllegalArgumentException(
                    "accent must be accent1, accent2, accent3, accent4, accent5, or accent6.");
        };
    }

    private static String solemnityAccent(String cover) {
        return switch (cover) {
            case "Capa - Solenidade Pedro Paulo" -> "accent5";
            case "Capa - Solenidade Paz", "Capa - Maria" -> "accent6";
            default -> "accent4";
        };
    }

    private static String theme(LiturgyDay day) {
        String cover = day.coverLayout();
        if ("Capa - Solenidade Pedro Paulo".equals(cover)) {
            return "accent5";
        }
        if ("Capa - Solenidade Paz".equals(cover) || "Capa - Maria".equals(cover)) {
            return "accent6";
        }
        if (SundayLibrary.solemnityGold(day.domingo())) {
            return "accent4";
        }
        if ("Capa - Domingo III Advento".equals(cover) || "Capa - Domingo IV Quaresma".equals(cover)) {
            return "rosa";
        }
        return day.seasonKey();
    }

    private static List<String> warnings(LiturgyDay day, Season season) {
        List<String> warnings = new ArrayList<>();
        if (day.primeira() == null && day.evangelho() == null) {
            if (LiturgyClient.weekdaySolemnity(day.date()) == null) {
                warnings.add("A liturgia deste dia não está disponível para ser preenchida automaticamente");
            }
            return warnings;
        }
        List<String> missing = SundayLibrary.missingSentences(day);
        if (!missing.isEmpty()) {
            warnings.add("Não foi possível preencher automaticamente " + String.join(", ", labels(missing)) + ".");
        }
        if (season.usesLentenAcclamation() && (day.louvor() == null || day.louvor().isBlank())) {
            warnings.add("Falta o verso do louvor.");
        }
        return warnings;
    }

    private static List<String> labels(List<String> fields) {
        List<String> labels = new ArrayList<>();
        for (String field : fields) {
            labels.add(switch (field) {
                case "primeira.citacao" -> "a primeira leitura";
                case "segunda.citacao" -> "a segunda leitura";
                case "salmo" -> "o salmo";
                case "evangelho.citacao" -> "o Evangelho";
                default -> field;
            });
        }
        return labels;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
