package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.amargs.chorusdeck.deck.DeckSpec;
import org.amargs.chorusdeck.deck.MassRequest;
import org.amargs.chorusdeck.deck.Season;
import org.amargs.chorusdeck.deck.SlideChoice;
import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.liturgy.LiturgyClient;
import org.amargs.chorusdeck.liturgy.LiturgyDay;
import org.amargs.chorusdeck.liturgy.LiturgyFixtures;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MassDeckRequestsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void previewNamesTheCoverTheReadingsAndTheSlotsTheSeasonHides() {
        LiturgyDay day = LiturgyFixtures.ordinary29();
        Map<String, Object> preview = MassDeckRequests.preview(day);
        Season season = Season.resolve(day.seasonKey(), day.coverLayout());
        assertEquals(day.domingo(), preview.get("domingo"));
        assertEquals(day.evangelho().citacao(), preview.get("frase"));
        assertEquals(day.evangelho().referencia(), preview.get("versiculo"));
        assertEquals(season.omitsGloria(), preview.get("omitsGloria"));
        assertEquals(false, preview.get("lenten"));
        assertEquals(DeckSpec.fileName(day.date(), day.domingo(), day.seasonKey(), day.ano()), preview.get("fileName"));
        assertEquals("accent1", preview.get("accent"));
        assertEquals(season.coverLayout(), preview.get("cover"));
        assertEquals(List.of(), preview.get("warnings"));
        assertFalse(preview.containsKey("solemnity"));

        LiturgyDay lent = new LiturgyDay(
                LocalDate.of(2026, 2, 22),
                "Domingo I",
                "quaresma",
                null,
                "A",
                new LiturgyDay.Reading("Gn 2, 7", null),
                null,
                null,
                new LiturgyDay.Reading("Mt 4, 1", null),
                null);
        Map<String, Object> quiet = MassDeckRequests.preview(lent);
        assertEquals(true, quiet.get("omitsGloria"));
        assertEquals(true, quiet.get("lenten"));
        assertTrue(quiet.get("warnings").toString().contains("o Evangelho"));
        assertTrue(quiet.get("warnings").toString().contains("louvor"));
    }

    @Test
    void aWeekdaySolemnityWarnsThatTheDayWasNotFilled() {
        LocalDate date = LocalDate.of(2027, 11, 1);
        LiturgyClient.WeekdaySolemnity feast = LiturgyClient.weekdaySolemnity(date);
        LiturgyDay saints = new LiturgyDay(
                date, feast.title(), "comum", feast.cover(), "A", null, null, null, null, null);
        Map<String, Object> preview = MassDeckRequests.preview(saints);
        @SuppressWarnings("unchecked")
        Map<String, Object> solemnity = (Map<String, Object>) preview.get("solemnity");
        assertEquals(feast.title(), solemnity.get("title"));
        assertEquals(feast.cover(), solemnity.get("cover"));
        assertEquals(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " é " + feast.title() + ".",
                solemnity.get("warning"));
        assertEquals(true, solemnity.get("link"));
        assertFalse(solemnity.containsKey("outranks"));
        assertEquals(List.of(), preview.get("warnings"));
    }

    @Test
    void anUnfilledDayWarnsOnce() {
        LiturgyDay day = new LiturgyDay(
                LocalDate.of(2026, 10, 19), "2ªf da semana XXIX", "comum", null, "A",
                null, null, null, null, null);
        List<?> warnings = (List<?>) MassDeckRequests.preview(day).get("warnings");
        assertEquals(List.of("A liturgia deste dia não está disponível para ser preenchida automaticamente"), warnings);
    }

    @Test
    void parseKeepsACorrectedSundayAndRejectsASongThatCannotBeSung() throws Exception {
        Path dir = Files.createTempDirectory("mass-deck");
        List<SongCfg> songs = List.of(
                new SongCfg(1, "A festa da música", List.of("verso"), "118"),
                new SongCfg(2, "Sem letra", List.of(), "4"));
        MassDeckRequests.Ready ready = MassDeckRequests.parse(JSON.readTree("""
                {
                  "mode": "mass",
                  "date": "2026-10-18",
                  "output": "missa.pptx",
                  "domingo": "Domingo XXIX corrigido",
                  "versiculo": "Mt 22, 15",
                  "songs": { "entrada": "118", "gloria": "" },
                  "opcoes": { "credo": "breve", "interval": "vazio" }
                }
                """), songs, dir);
        assertEquals(dir.resolve("missa.pptx").toAbsolutePath().normalize(), ready.output());
        assertEquals("118", ready.request().entrada());
        assertEquals(null, ready.request().gloria());
        assertEquals(Map.of(), ready.choices());

        LiturgyDay day = LiturgyFixtures.ordinary29();
        MassRequest corrected = MassDeckRequests.apply(ready, day);
        assertEquals("Domingo XXIX corrigido", corrected.cover().domingo());
        assertEquals("Mt 22, 15", corrected.cover().versiculo());
        assertEquals("Mt 22, 15", corrected.liturgy().evangelho().referencia());
        assertEquals(day.evangelho().citacao(), corrected.cover().frase());
        assertFalse(corrected.longCreed());

        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class, () -> MassDeckRequests.parse(JSON.readTree("""
                {"date":"2026-10-18","output":"a.pptx","songs":{"entrada":"4"},"opcoes":{"credo":"longo"}}
                """), songs, dir));
        assertTrue(empty.getMessage().contains("no verses"));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> MassDeckRequests.parse(JSON.readTree("""
                {"date":"2026-10-18","output":"a.pptx","songs":{"cordeiro":"999"},"opcoes":{"credo":"longo"}}
                """), songs, dir));
        assertTrue(unknown.getMessage().contains("Unknown songbook id: 999"));
    }

    @Test
    void aMassSongCanChooseTheTitleAndTheLanguage() throws Exception {
        Path dir = Files.createTempDirectory("mass-deck");
        List<SongCfg> songs = List.of(new SongCfg(
                1, "Pelos caminhos", List.of("um", "dois"), "12", "Per viam", List.of("unus")));
        MassDeckRequests.Ready ready = MassDeckRequests.parse(JSON.readTree("""
                {
                  "date": "2026-10-18",
                  "output": "missa.pptx",
                  "songs": {
                    "entrada": {"bookId": "12", "title": "titleAlt", "lyrics": "contentAlt"}
                  },
                  "opcoes": {"credo": "breve", "interval": "vazio"}
                }
                """), songs, dir);
        assertEquals("12", ready.request().entrada());
        assertEquals(new SlideChoice("titleAlt", "contentAlt"), ready.choices().get("entrada"));

        MassDeckRequests.Ready shorter = MassDeckRequests.parse(JSON.readTree("""
                {
                  "date": "2026-10-18",
                  "output": "missa.pptx",
                  "songs": {
                    "entrada": {"bookId": "12", "slides": [1]}
                  },
                  "opcoes": {"credo": "breve", "interval": "vazio"}
                }
                """), songs, dir);
        assertEquals(List.of(1), shorter.choices().get("entrada").slides());
    }

    @Test
    void aSundaySolemnityStaysTheSundayUntilChosen() {
        LiturgyDay saints = sunday(LocalDate.of(2026, 11, 1), "Domingo XXXI", "comum");
        LiturgyClient.SundaySolemnity overlap = LiturgyClient.sundayOverlap(saints.date(), saints.seasonKey());
        Map<String, Object> solemnity = solemnity(saints);
        assertEquals(overlap.title(), solemnity.get("title"));
        assertEquals(overlap.cover(), solemnity.get("cover"));
        assertEquals(true, solemnity.get("outranks"));
        assertEquals("O Domingo 01/11/2026 é também " + overlap.title()
                + ". A celebração da solenidade prevalece sobre a liturgia de Domingo.", solemnity.get("warning"));
        assertEquals(DeckSpec.fileName(saints.date(), overlap.title(), saints.seasonKey(), saints.ano()),
                solemnity.get("fileName"));

        LiturgyDay immaculate = sunday(LocalDate.of(2024, 12, 8), "Domingo II", "advento");
        LiturgyClient.SundaySolemnity kept = LiturgyClient.sundayOverlap(immaculate.date(), immaculate.seasonKey());
        Map<String, Object> advent = solemnity(immaculate);
        assertEquals(false, advent.get("outranks"));
        assertEquals("accent4", advent.get("accent"));
        assertEquals("O Domingo 08/12/2024 é também " + kept.title() + ". No entanto, a liturgia de "
                + immaculate.domingo() + " do tempo de Advento prevalece sobre esta festa/solenidade.",
                advent.get("warning"));
    }

    @Test
    void peterAndPaulUsesTheRedAccent() {
        LiturgyDay apostles = sunday(LocalDate.of(2025, 6, 29), "Domingo XIII", "comum");
        LiturgyClient.SundaySolemnity overlap = LiturgyClient.sundayOverlap(apostles.date(), apostles.seasonKey());
        Map<String, Object> peter = solemnity(apostles);
        assertEquals(overlap.cover(), peter.get("cover"));
        assertEquals("accent5", peter.get("accent"));

        LocalDate weekday = LocalDate.of(2026, 6, 29);
        LiturgyClient.WeekdaySolemnity feast = LiturgyClient.weekdaySolemnity(weekday);
        Map<String, Object> peterDay = MassDeckRequests.preview(new LiturgyDay(
                weekday, feast.title(), "comum", feast.cover(), "A", null, null, null, null, null));
        assertEquals("accent5", peterDay.get("accent"));
        assertEquals(feast.cover(), peterDay.get("cover"));
    }

    @Test
    void aGoldSolemnityKeepsTheSeasonCover() {
        LiturgyDay day = new LiturgyDay(
                LocalDate.of(2027, 5, 16),
                "Solenidade da Ascensão do Senhor",
                "pascoa",
                null,
                "A",
                null, null, null, null, null);
        Map<String, Object> preview = MassDeckRequests.preview(day);
        assertEquals("accent4", preview.get("accent"));
        assertEquals(Season.resolve(day.seasonKey(), null).coverLayout(), preview.get("cover"));
    }

    @Test
    void keepingTheSundayLeavesItsReadings() throws Exception {
        LiturgyDay saints = sunday(LocalDate.of(2026, 11, 1), "Domingo XXXI", "comum");
        Path dir = Files.createTempDirectory("mass-solemnity");
        MassRequest kept = MassDeckRequests.apply(ready(dir, """
                {"date":"2026-11-01","output":"domingo.pptx","domingo":"Domingo XXXI","solemnity":false,%s}
                """), saints);
        assertEquals(saints.domingo(), kept.cover().domingo());
        assertEquals(null, kept.cover().cover());
        assertEquals(saints.primeira().referencia(), kept.liturgy().primeira().referencia());

        MassRequest corrected = MassDeckRequests.apply(ready(dir, """
                {"date":"2026-11-01","output":"domingo.pptx","domingo":"Domingo XXXI","solemnity":false,
                 "versiculo":"","referencias":{"primeira":"Ap 7, 2-4"},%s}
                """), saints);
        assertEquals("Ap 7, 2-4", corrected.liturgy().primeira().referencia());
        assertEquals(saints.evangelho().referencia(), corrected.cover().versiculo());
    }

    @Test
    void choosingTheSolemnityBlanksTheSundayReadings() throws Exception {
        LiturgyDay saints = sunday(LocalDate.of(2026, 11, 1), "Domingo XXXI", "comum");
        Path dir = Files.createTempDirectory("mass-solemnity");
        MassRequest switched = MassDeckRequests.apply(ready(dir, """
                {"date":"2026-11-01","output":"santos.pptx","domingo":"Todos os Santos","solemnity":true,"accent":"accent2",
                 "versiculo":"","leituras":{"primeira":"Vi uma multidão"},
                 "referencias":{"primeira":"Ap 7, 2-4","segunda":""},%s}
                """), saints);
        assertEquals("Todos os Santos", switched.cover().domingo());
        assertEquals(LiturgyClient.sundayOverlap(saints.date(), saints.seasonKey()).cover(), switched.cover().cover());
        assertEquals("", switched.cover().versiculo());
        assertEquals("", switched.liturgy().evangelho().referencia());
        assertEquals("Ap 7, 2-4", switched.liturgy().primeira().referencia());
        assertEquals("Vi uma multidão", switched.liturgy().primeira().citacao());
        assertEquals("accent2", switched.accent());

        LiturgyDay immaculate = sunday(LocalDate.of(2024, 12, 8), "Domingo II", "advento");
        MassRequest feast = MassDeckRequests.apply(ready(dir, """
                {"date":"2024-12-08","output":"imaculada.pptx","solemnity":true,%s}
                """), immaculate);
        LiturgyClient.SundaySolemnity overlap = LiturgyClient.sundayOverlap(immaculate.date(), immaculate.seasonKey());
        assertEquals(overlap.title(), feast.cover().domingo());
        assertEquals(overlap.cover(), feast.cover().cover());
    }

    @Test
    void aCoverInTheRequestReplacesTheResolvedOne() throws Exception {
        LiturgyDay saints = sunday(LocalDate.of(2026, 11, 1), "Domingo XXXI", "comum");
        Path dir = Files.createTempDirectory("mass-solemnity");
        MassRequest chosen = MassDeckRequests.apply(ready(dir, """
                {"date":"2026-11-01","output":"santos.pptx","domingo":"Todos os Santos","solemnity":true,
                 "manual":true,"cover":"Capa - Ramos",%s}
                """), saints);
        assertEquals("Capa - Ramos", chosen.cover().cover());

        MassRequest withoutManual = MassDeckRequests.apply(ready(dir, """
                {"date":"2026-11-01","output":"domingo.pptx","domingo":"Domingo XXXI","solemnity":false,
                 "cover":"Capa - Natal",%s}
                """), saints);
        assertEquals("Capa - Natal", withoutManual.cover().cover());

        IllegalArgumentException unknownCover = assertThrows(IllegalArgumentException.class, () -> MassDeckRequests.apply(
                ready(dir, """
                        {"date":"2026-10-18","output":"a.pptx","manual":true,"cover":"Capa - Inventada",%s}
                        """),
                LiturgyFixtures.ordinary29()));
        assertTrue(unknownCover.getMessage().contains("Unknown cover"));
    }

    @Test
    void aSolemnityOnAnOrdinarySundayIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("mass-solemnity");
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () -> MassDeckRequests.apply(
                ready(dir, """
                        {"date":"2026-10-18","output":"a.pptx","solemnity":true,%s}
                        """),
                LiturgyFixtures.ordinary29()));
        assertTrue(rejected.getMessage().contains("not a Sunday solemnity"));
    }

    private static MassDeckRequests.Ready ready(Path dir, String json) throws Exception {
        String options = "\"opcoes\":{\"credo\":\"longo\",\"interval\":\"vazio\"}";
        return MassDeckRequests.parse(
                JSON.readTree(json.formatted(options)),
                List.of(new SongCfg(1, "Entrada", List.of("verso"), "118")),
                dir);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> solemnity(LiturgyDay day) {
        return (Map<String, Object>) MassDeckRequests.preview(day).get("solemnity");
    }

    private static LiturgyDay sunday(LocalDate date, String domingo, String season) {
        return new LiturgyDay(
                date,
                domingo,
                season,
                null,
                "A",
                new LiturgyDay.Reading("Ml 1, 14", "Eu sou o grande Rei"),
                new LiturgyDay.Reading("1Jo 3, 1", "Vede que amor"),
                "Felizes",
                new LiturgyDay.Reading("Mt 23, 1-12", "Não vos deixeis"),
                null);
    }
}
