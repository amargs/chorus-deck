package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.amargs.chorusdeck.deck.MassRequest;
import org.amargs.chorusdeck.deck.Season;
import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.liturgy.LiturgyDay;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
        LiturgyDay day = new LiturgyDay(
                LocalDate.of(2026, 10, 18),
                "Domingo XXIX",
                "comum",
                null,
                "A",
                new LiturgyDay.Reading("Is 45, 1", "Tomei Ciro"),
                new LiturgyDay.Reading("1Ts 1, 1", "Recordamos"),
                "Aclamai",
                new LiturgyDay.Reading("Mt 22, 15-21", "Dai a César"),
                null);
        Map<String, Object> preview = MassDeckRequests.preview(day);
        assertEquals("Domingo XXIX", preview.get("domingo"));
        assertEquals("Dai a César", preview.get("frase"));
        assertEquals("Mt 22, 15-21", preview.get("versiculo"));
        assertEquals(false, preview.get("omitsGloria"));
        assertEquals(false, preview.get("lenten"));
        assertEquals("2026-10-18 Domingo XXIX Tempo Comum - Ano A.pptx", preview.get("fileName"));
        assertEquals("accent1", preview.get("accent"));
        assertEquals("Capa - Tempo Comum", preview.get("cover"));
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
        LiturgyDay saints = new LiturgyDay(
                LocalDate.of(2027, 11, 1),
                "Solenidade de Todos os Santos",
                "comum",
                "Capa - Solenidade",
                "A",
                null, null, null, null, null);
        Map<String, Object> preview = MassDeckRequests.preview(saints);
        @SuppressWarnings("unchecked")
        Map<String, Object> solemnity = (Map<String, Object>) preview.get("solemnity");
        assertEquals("Solenidade de Todos os Santos", solemnity.get("title"));
        assertEquals("Capa - Solenidade", solemnity.get("cover"));
        assertEquals(
                "01/11/2027 é Solenidade de Todos os Santos. A liturgia deste dia não foi preenchida.",
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
        assertEquals(List.of("A liturgia deste dia não foi preenchida."), warnings);
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

        LiturgyDay day = new LiturgyDay(
                LocalDate.of(2026, 10, 18),
                "Domingo XXIX",
                "comum",
                null,
                "A",
                new LiturgyDay.Reading("Is 45, 1", "Tomei Ciro"),
                null,
                "Aclamai",
                new LiturgyDay.Reading("Mt 22, 15-21", "Dai a César"),
                null);
        MassRequest corrected = MassDeckRequests.apply(ready, day);
        assertEquals("Domingo XXIX corrigido", corrected.cover().domingo());
        assertEquals("Mt 22, 15", corrected.cover().versiculo());
        assertEquals("Mt 22, 15", corrected.liturgy().evangelho().referencia());
        assertEquals("Dai a César", corrected.cover().frase());
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
    void aSundaySolemnityStaysTheSundayUntilChosen() throws Exception {
        LiturgyDay saints = sunday(LocalDate.of(2026, 11, 1), "Domingo XXXI", "comum");
        Map<String, Object> preview = MassDeckRequests.preview(saints);
        @SuppressWarnings("unchecked")
        Map<String, Object> solemnity = (Map<String, Object>) preview.get("solemnity");
        assertEquals("Solenidade de Todos os Santos", solemnity.get("title"));
        assertEquals("Capa - Solenidade", solemnity.get("cover"));
        assertEquals(true, solemnity.get("outranks"));
        assertEquals(
                "01/11/2026 é também Solenidade de Todos os Santos. A solenidade prevalece sobre Domingo XXXI, mas as leituras são as do Domingo XXXI e não as de Solenidade de Todos os Santos.",
                solemnity.get("warning"));
        assertEquals("2026-11-01 Solenidade de Todos os Santos - Ano A.pptx", solemnity.get("fileName"));

        LiturgyDay assumption = sunday(LocalDate.of(2026, 8, 15), "Solenidade da Assunção da Virgem Santa Maria", "comum");
        @SuppressWarnings("unchecked")
        Map<String, Object> saturday = (Map<String, Object>) MassDeckRequests.preview(assumption).get("solemnity");
        assertEquals("Solenidade da Assunção da Virgem Santa Maria", saturday.get("title"));
        assertEquals(
                "15/08/2026 é Solenidade da Assunção da Virgem Santa Maria. A liturgia deste dia não foi preenchida.",
                saturday.get("warning"));
        assertFalse(saturday.containsKey("outranks"));
        @SuppressWarnings("unchecked")
        Map<String, Object> august = (Map<String, Object>) MassDeckRequests.preview(
                sunday(LocalDate.of(2027, 8, 15), "Domingo XX", "comum")).get("solemnity");
        assertEquals("Solenidade da Assunção da Virgem Santa Maria", august.get("title"));
        assertEquals("Capa - Solenidade", august.get("cover"));

        LiturgyDay apostles = sunday(LocalDate.of(2025, 6, 29), "Domingo XIII", "comum");
        @SuppressWarnings("unchecked")
        Map<String, Object> peter = (Map<String, Object>) MassDeckRequests.preview(apostles).get("solemnity");
        assertEquals("Capa - Solenidade Pedro Paulo", peter.get("cover"));
        assertEquals("accent5", peter.get("accent"));

        Map<String, Object> peterDay = MassDeckRequests.preview(new LiturgyDay(
                LocalDate.of(2026, 6, 29),
                "Solenidade de São Pedro e São Paulo",
                "comum",
                "Capa - Solenidade Pedro Paulo",
                "A",
                null,
                null,
                null,
                null,
                null));
        assertEquals("accent5", peterDay.get("accent"));
        assertEquals("Capa - Solenidade Pedro Paulo", peterDay.get("cover"));

        LiturgyDay immaculate = sunday(LocalDate.of(2024, 12, 8), "Domingo II", "advento");
        @SuppressWarnings("unchecked")
        Map<String, Object> advent = (Map<String, Object>) MassDeckRequests.preview(immaculate).get("solemnity");
        assertEquals(false, advent.get("outranks"));
        assertTrue(advent.get("warning").toString().contains("Este domingo do Advento prevalece"));
        assertEquals("accent4", advent.get("accent"));

        Path dir = Files.createTempDirectory("mass-solemnity");
        List<SongCfg> songs = List.of(new SongCfg(1, "Entrada", List.of("verso"), "118"));
        String options = "\"opcoes\":{\"credo\":\"longo\",\"interval\":\"vazio\"}";
        MassDeckRequests.Ready off = MassDeckRequests.parse(JSON.readTree("""
                {"date":"2026-11-01","output":"domingo.pptx","domingo":"Domingo XXXI","solemnity":false,%s}
                """.formatted(options)), songs, dir);
        MassRequest kept = MassDeckRequests.apply(off, saints);
        assertEquals("Domingo XXXI", kept.cover().domingo());
        assertEquals(null, kept.cover().cover());
        assertEquals("Ml 1, 14", kept.liturgy().primeira().referencia());

        MassRequest correctedReading = MassDeckRequests.apply(MassDeckRequests.parse(JSON.readTree("""
                {"date":"2026-11-01","output":"domingo.pptx","domingo":"Domingo XXXI","solemnity":false,
                 "versiculo":"","referencias":{"primeira":"Ap 7, 2-4"},%s}
                """.formatted(options)), songs, dir), saints);
        assertEquals("Domingo XXXI", correctedReading.cover().domingo());
        assertEquals(null, correctedReading.cover().cover());
        assertEquals("Ap 7, 2-4", correctedReading.liturgy().primeira().referencia());
        assertEquals("Mt 23, 1-12", correctedReading.cover().versiculo());

        MassDeckRequests.Ready on = MassDeckRequests.parse(JSON.readTree("""
                {"date":"2026-11-01","output":"santos.pptx","domingo":"Todos os Santos","solemnity":true,"accent":"accent2",
                 "versiculo":"","leituras":{"primeira":"Vi uma multidão"},
                 "referencias":{"primeira":"Ap 7, 2-4","segunda":""},%s}
                """.formatted(options)), songs, dir);
        MassRequest switched = MassDeckRequests.apply(on, saints);
        assertEquals("Todos os Santos", switched.cover().domingo());
        assertEquals("Capa - Solenidade", switched.cover().cover());
        assertEquals("Mt 23, 1-12", switched.cover().versiculo());
        assertEquals("Mt 23, 1-12", switched.liturgy().evangelho().referencia());
        assertEquals("Ap 7, 2-4", switched.liturgy().primeira().referencia());
        assertEquals("Vi uma multidão", switched.liturgy().primeira().citacao());
        assertEquals("1Jo 3, 1", switched.liturgy().segunda().referencia());
        assertEquals("accent2", switched.accent());

        MassRequest chosenCover = MassDeckRequests.apply(MassDeckRequests.parse(JSON.readTree("""
                {"date":"2026-11-01","output":"santos.pptx","domingo":"Todos os Santos","solemnity":true,
                 "manual":true,"cover":"Capa - Ramos",%s}
                """.formatted(options)), songs, dir), saints);
        assertEquals("Capa - Ramos", chosenCover.cover().cover());

        MassRequest chosenWithoutManual = MassDeckRequests.apply(MassDeckRequests.parse(JSON.readTree("""
                {"date":"2026-11-01","output":"domingo.pptx","domingo":"Domingo XXXI","solemnity":false,
                 "cover":"Capa - Natal",%s}
                """.formatted(options)), songs, dir), saints);
        assertEquals("Capa - Natal", chosenWithoutManual.cover().cover());

        IllegalArgumentException unknownCover = assertThrows(IllegalArgumentException.class, () -> MassDeckRequests.apply(
                MassDeckRequests.parse(JSON.readTree("""
                        {"date":"2026-10-18","output":"a.pptx","manual":true,"cover":"Capa - Inventada",%s}
                        """.formatted(options)), songs, dir),
                sunday(LocalDate.of(2026, 10, 18), "Domingo XXIX", "comum")));
        assertTrue(unknownCover.getMessage().contains("Unknown cover"));

        MassRequest feast = MassDeckRequests.apply(MassDeckRequests.parse(JSON.readTree("""
                {"date":"2024-12-08","output":"imaculada.pptx","solemnity":true,%s}
                """.formatted(options)), songs, dir), immaculate);
        Season season = Season.resolve(feast.cover().season(), feast.cover().cover());
        assertEquals("Capa - Solenidade", season.coverLayout());
        assertEquals("Solenidade da Imaculada Conceição da Virgem Santa Maria", feast.cover().domingo());
        assertTrue(season.omitsGloria());
        assertEquals("Tempo de Advento · Ano A", season.tempo("A"));

        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () -> MassDeckRequests.apply(
                MassDeckRequests.parse(JSON.readTree("""
                        {"date":"2026-10-18","output":"a.pptx","solemnity":true,%s}
                        """.formatted(options)), songs, dir),
                sunday(LocalDate.of(2026, 10, 18), "Domingo XXIX", "comum")));
        assertTrue(rejected.getMessage().contains("not a Sunday solemnity"));
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
