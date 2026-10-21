package org.amargs.chorusdeck.liturgy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.amargs.chorusdeck.deck.Cover;
import org.amargs.chorusdeck.web.MassDeckRequests;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class LiturgyClientTest {

    private static final String CALENDAR = """
            {
              "day": { "sunday_id": "ordinary-29", "sunday_cycle": "A", "season": "ordinary" },
              "readings": {
                "first_reading": "Is 45:1, 4-6",
                "second_reading": "1 Thes 1:1-5",
                "psalm_response": "Give the Lord glory and honour",
                "gospel": "Mt 22:15-21"
              }
            }
            """;

    @Test
    void aNewSundayIsStoredUntilThePortugueseSentencesAreFilled() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve("sundays.json");
        LocalDate date = LocalDate.of(2026, 10, 18);
        LiturgyClient client = new LiturgyClient(ignored -> CALENDAR);

        LiturgyDay blank = client.load(date, file);
        assertEquals("Domingo XXIX", blank.domingo());
        assertNull(blank.primeira().citacao());
        assertNull(blank.refrao());
        assertEquals(List.of("primeira.citacao", "segunda.citacao", "salmo", "evangelho.citacao"),
                SundayLibrary.missingSentences(blank));

        ObjectNode root = (ObjectNode) new ObjectMapper().readTree(file.toFile());
        ObjectNode sunday = (ObjectNode) root.get("sundays").get("ordinary-29/A");
        assertEquals("Domingo XXIX", sunday.get("domingo").asText());
        assertEquals("comum", sunday.get("season").asText());
        assertEquals("Is 45, 1, 4-6", sunday.get("primeira").get("referencia").asText());
        assertEquals("1Ts 1, 1-5", sunday.get("segunda").get("referencia").asText());
        assertEquals("Mt 22, 15-21", sunday.get("evangelho").get("referencia").asText());
        ((ObjectNode) sunday.get("primeira")).put("citacao", "Tomei Ciro");
        ((ObjectNode) sunday.get("segunda")).put("citacao", "Recordamos a vossa fé");
        sunday.put("salmo", "Aclamai a glória do Senhor.");
        ((ObjectNode) sunday.get("evangelho")).put("citacao", "Dai a César o que é de César e a Deus o que é de Deus");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);

        LiturgyDay day = client.load(date, file);
        assertEquals("Domingo XXIX", day.domingo());
        assertEquals("A", day.ano());
        assertEquals("Dai a César o que é de César e a Deus o que é de Deus", day.evangelho().citacao());
        assertEquals(day.evangelho().citacao(), Cover.from(day).frase());
        assertNull(Cover.from(day).cover());

        LiturgyClient offline = new LiturgyClient(ignored -> {
            throw new IOException("calendar down");
        });
        assertEquals("Domingo XXIX", offline.load(date, file).domingo());
    }

    @Test
    void gaudeteUsesTheRoseCover() throws IOException {
        RomanCalendar calendar = RomanCalendar.parse("""
                {
                  "day": { "sunday_id": "advent-3", "sunday_cycle": "B", "season": "advent" },
                  "readings": { "first_reading": "Is 61:1-2, 10-11", "gospel": "Jn 1:6-8, 19-28" }
                }
                """);
        Path file = Files.createTempDirectory("sundays").resolve("sundays.json");
        LiturgyDay day = new SundayLibrary(file).resolve(LocalDate.of(2026, 12, 13), calendar);
        assertEquals("Domingo III", day.domingo());
        assertNull(day.segunda());
        assertEquals(List.of("primeira.citacao", "salmo", "evangelho.citacao"), SundayLibrary.missingSentences(day));
        ObjectNode sunday = (ObjectNode) new ObjectMapper().readTree(file.toFile()).get("sundays").get("advent-3/B");
        assertEquals("Domingo III", sunday.get("domingo").asText());
        assertEquals("advento", sunday.get("season").asText());
        assertEquals("Capa - Domingo III Advento", sunday.get("cover").asText());
        assertEquals("Jo 1, 6-8, 19-28", sunday.get("evangelho").get("referencia").asText());
        assertNull(sunday.get("segunda"));
        assertEquals("", sunday.get("salmo").asText());
        assertNull(sunday.get("refrao"));

        LiturgyDay filled = new SundayLibrary(file).rememberSentences(
                LocalDate.of(2026, 12, 13), "O espírito do Senhor", null, "A minha alma", "Veio um homem", "Outra");
        assertEquals("O espírito do Senhor", filled.primeira().citacao());
        assertEquals("A minha alma", filled.refrao());
        assertEquals("Veio um homem", filled.evangelho().citacao());
        ObjectNode saved = (ObjectNode) new ObjectMapper().readTree(file.toFile()).get("sundays").get("advent-3/B");
        assertEquals("O espírito do Senhor", saved.get("primeira").get("citacao").asText());
        assertEquals("A minha alma", saved.get("salmo").asText());

        LiturgyDay kept = new SundayLibrary(file).rememberSentences(
                LocalDate.of(2026, 12, 13), "Uma frase diferente", null, "Outro salmo", null, null);
        assertEquals("O espírito do Senhor", kept.primeira().citacao());
        assertEquals("A minha alma", kept.refrao());
    }

    @Test
    void aStoredRefraoIsReadAsSalmo() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve("sundays.json");
        Files.writeString(file, """
                {
                  "dates": { "2026-10-18": "ordinary-29/A" },
                  "sundays": {
                    "ordinary-29/A": {
                      "domingo": "Domingo XXIX",
                      "season": "comum",
                      "primeira": { "referencia": "Is 45, 1, 4-6", "citacao": "Tomei Ciro" },
                      "refrao": "Aclamai o Senhor.",
                      "evangelho": { "referencia": "Mt 22, 15-21", "citacao": "Dai a César" }
                    }
                  }
                }
                """);
        assertEquals("Aclamai o Senhor.", new SundayLibrary(file).stored(LocalDate.of(2026, 10, 18)).refrao());
    }

    @Test
    void aWeekdaySolemnityKeepsItsNameAndCoverWithoutScriptures() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve("sundays.json");
        LiturgyClient client = new LiturgyClient(date -> {
            if (date.equals(LocalDate.of(2026, 8, 15))) {
                return day("solemnity", "The Assumption of the Blessed Virgin Mary", "ordinary", null);
            }
            if (date.equals(LocalDate.of(2026, 12, 8))) {
                return day("solemnity", "The Immaculate Conception of the Blessed Virgin Mary", "advent", null);
            }
            if (date.equals(LocalDate.of(2026, 6, 29))) {
                return day("solemnity", "Saints Peter and Paul", "ordinary", null);
            }
            throw new IOException("unexpected " + date);
        });

        LiturgyDay assumption = client.load(LocalDate.of(2026, 8, 15), file);
        assertEquals("Solenidade da Assunção da Virgem Santa Maria", assumption.domingo());
        assertEquals("Capa - Solenidade", assumption.coverLayout());
        assertEquals("comum", assumption.seasonKey());
        assertEquals("A", assumption.ano());
        assertNull(assumption.evangelho());
        assertNull(assumption.primeira());

        LiturgyDay immaculate = client.load(LocalDate.of(2026, 12, 8), file);
        assertEquals("Solenidade da Imaculada Conceição da Virgem Santa Maria", immaculate.domingo());
        assertEquals("Capa - Solenidade", immaculate.coverLayout());
        assertEquals("advento", immaculate.seasonKey());

        LiturgyDay apostles = client.load(LocalDate.of(2026, 6, 29), file);
        assertEquals("Solenidade de São Pedro e São Paulo", apostles.domingo());
        assertEquals("Capa - Solenidade Pedro Paulo", apostles.coverLayout());
        assertFalse(Files.exists(file));
    }

    @Test
    void aWeekdayCaptionComesFromTheSundayThatOpensTheWeek() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve("sundays.json");
        LiturgyClient client = new LiturgyClient(date -> switch (date.toString()) {
            case "2026-10-17" -> day("memorial", "Saint Ignatius of Antioch", "ordinary", null);
            case "2026-10-11" -> day("ferial", null, "ordinary", "ordinary-28");
            case "2026-10-19" -> day("ferial", null, "ordinary", null);
            case "2026-10-18" -> day("ferial", null, "ordinary", "ordinary-29");
            case "2026-03-30" -> day("ferial", null, "holy_week", null);
            case "2026-03-29" -> day("solemnity", "Passion Sunday (Palm Sunday)", "holy_week", "palm-sunday");
            default -> throw new IOException("unexpected " + date);
        });

        LiturgyDay saturday = client.load(LocalDate.of(2026, 10, 17), file);
        assertEquals("Sábado da semana XXVIII", saturday.domingo());
        assertEquals("comum", saturday.seasonKey());
        assertNull(saturday.coverLayout());
        assertNull(saturday.evangelho());

        assertEquals("2ªf da semana XXIX", client.load(LocalDate.of(2026, 10, 19), file).domingo());
        assertEquals("2ªf", client.load(LocalDate.of(2026, 3, 30), file).domingo());
        assertEquals("quaresma", client.load(LocalDate.of(2026, 3, 30), file).seasonKey());
        assertFalse(Files.exists(file));
    }

    @Test
    void holyFamilyKeepsItsPortugueseName() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve("sundays.json");
        Files.writeString(file, """
                {
                  "dates": { "2026-12-27": "holy-family/B" },
                  "sundays": {
                    "holy-family/B": {
                      "domingo": "holy-family",
                      "season": "natal",
                      "primeira": { "referencia": "Sir 3, 2-6.12-14", "citacao": "" },
                      "evangelho": { "referencia": "Lc 2, 22-40", "citacao": "" },
                      "salmo": ""
                    }
                  }
                }
                """);
        LiturgyClient client = new LiturgyClient(ignored -> """
                {
                  "day": {
                    "sunday_id": "holy-family",
                    "sunday_cycle": "B",
                    "season": "christmas",
                    "name": "Holy Family"
                  },
                  "readings": { "first_reading": "Sir 3:2-6, 12-14", "gospel": "Lk 2:22-40" }
                }
                """);

        LiturgyDay day = client.load(LocalDate.of(2026, 12, 27), file);
        assertEquals("Festa da Sagrada Família de Jesus, Maria e José", day.domingo());
        assertEquals("natal", day.seasonKey());
        assertEquals("Festa da Sagrada Família de Jesus, Maria e José",
                new ObjectMapper().readTree(file.toFile()).get("sundays").get("holy-family/B").get("domingo").asText());
    }

    @Test
    void epiphanyAndBaptismUseTheirPortugueseNames() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve("sundays.json");
        LiturgyClient client = new LiturgyClient(date -> switch (date.toString()) {
            case "2027-01-03" -> """
                    {
                      "day": { "sunday_id": "epiphany", "sunday_cycle": "B", "season": "ordinary", "name": "Epiphany" },
                      "readings": { "first_reading": "Is 60:1-6", "gospel": "Mt 2:1-12" }
                    }
                    """;
            case "2027-01-10" -> """
                    {
                      "day": { "sunday_id": "baptism-of-the-lord", "sunday_cycle": "B", "season": "ordinary", "name": "The Baptism of the Lord" },
                      "readings": { "first_reading": "Is 42:1-4, 6-7", "gospel": "Mt 3:13-17" }
                    }
                    """;
            default -> throw new IOException("unexpected " + date);
        });

        LiturgyDay epiphany = client.load(LocalDate.of(2027, 1, 3), file);
        assertEquals("Solenidade da Epifania do Senhor", epiphany.domingo());
        assertEquals("natal", epiphany.seasonKey());

        LiturgyDay baptism = client.load(LocalDate.of(2027, 1, 10), file);
        assertEquals("Solenidade do Batismo do Senhor", baptism.domingo());
        assertEquals("natal", baptism.seasonKey());
    }

    @Test
    void aSundaySolemnityStaysTheSunday() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve("sundays.json");
        LiturgyClient client = new LiturgyClient(ignored -> """
                {
                  "day": {
                    "sunday_id": "ordinary-31",
                    "sunday_cycle": "A",
                    "season": "ordinary",
                    "rank": "solemnity",
                    "name": "All Saints"
                  },
                  "readings": { "first_reading": "Mal 1:14-2:2, 8-10", "gospel": "Mt 23:1-12" }
                }
                """);

        LiturgyDay day = client.load(LocalDate.of(2026, 11, 1), file);
        assertEquals("Domingo XXXI", day.domingo());
        assertNull(day.coverLayout());
        assertEquals("comum", day.seasonKey());
        assertEquals("Mt 23, 1-12", day.evangelho().referencia());
        assertEquals(
                "2026-11-01 is also Solenidade de Todos os Santos. The deck keeps Domingo XXXI (A). "
                        + "The scriptures are that Sunday's, and this solemnity outranks it.",
                LiturgyClient.sundaySolemnity(day.date(), day, RomanCalendar.parse("""
                        { "day": { "sunday_cycle": "A", "season": "ordinary", "name": "All Saints" } }
                        """), FixedSolemnity.of("All Saints")));
        assertEquals("ordinary-31/A",
                new ObjectMapper().readTree(file.toFile()).get("dates").get("2026-11-01").asText());
    }

    @Test
    void movableSolemnitiesUseTheGoldAccent() throws IOException {
        assertEquals(LocalDate.of(2027, 5, 27), LiturgyClient.corpusChristi(2027));
        assertEquals(LocalDate.of(2026, 6, 4), LiturgyClient.corpusChristi(2026));
        Path file = Files.createTempDirectory("sundays").resolve("sundays.json");
        LiturgyClient client = new LiturgyClient(date -> switch (date.toString()) {
            case "2027-05-09" -> sunday("easter-7", "easter");
            case "2027-05-23" -> sunday("trinity", "ordinary");
            case "2027-05-27" -> throw new IOException("The calendar returned nothing.");
            case "2027-11-21" -> sunday("ordinary-34", "ordinary");
            default -> throw new IOException("unexpected " + date);
        });

        assertGold(client.load(LocalDate.of(2027, 5, 9), file),
                "Solenidade da Ascensão do Senhor", "pascoa");
        assertGold(client.load(LocalDate.of(2027, 5, 23), file),
                "Solenidade da Santíssima Trindade", "comum");
        assertGold(client.load(LocalDate.of(2027, 5, 27), file),
                "Solenidade do Santíssimo Corpo e Sangue de Cristo", "comum");
        assertGold(client.load(LocalDate.of(2027, 11, 21), file),
                "Solenidade de Nosso Senhor Jesus Cristo, Rei do Universo", "comum");
    }

    private static void assertGold(LiturgyDay day, String title, String season) {
        assertEquals(title, day.domingo());
        assertEquals(season, day.seasonKey());
        assertEquals("accent4", MassDeckRequests.preview(day).get("accent"));
        assertEquals("Capa - " + ("pascoa".equals(season) ? "Páscoa" : "Tempo Comum"),
                MassDeckRequests.preview(day).get("cover"));
    }

    private static String sunday(String sundayId, String season) {
        return """
                {
                  "day": { "sunday_id": "%s", "sunday_cycle": "B", "season": "%s" },
                  "readings": { "first_reading": "Acts 1:1-11", "gospel": "Mt 28:16-20" }
                }
                """.formatted(sundayId, season);
    }

    private static String day(String rank, String name, String season, String sundayId) {
        String id = sundayId == null ? "null" : "\"" + sundayId + "\"";
        String celebration = name == null ? "null" : "\"" + name + "\"";
        return """
                {
                  "day": {
                    "rank": "%s",
                    "name": %s,
                    "season": "%s",
                    "sunday_cycle": "A",
                    "sunday_id": %s
                  },
                  "readings": null
                }
                """.formatted(rank, celebration, season, id);
    }
}
