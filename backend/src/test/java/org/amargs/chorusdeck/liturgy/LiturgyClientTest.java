package org.amargs.chorusdeck.liturgy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.amargs.chorusdeck.deck.Cover;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiturgyClientTest {

    @Test
    void aNewSundayIsStoredUntilThePortugueseSentencesAreFilled() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve(SundayLibrary.FILE_NAME);
        LocalDate date = LiturgyFixtures.ORDINARY_29;
        LiturgyClient client = new LiturgyClient(ignored -> LiturgyFixtures.ORDINARY_29_CALENDAR);

        LiturgyDay blank = client.load(date, file);
        assertEquals("Domingo XXIX", blank.domingo());
        assertEquals("comum", blank.seasonKey());
        assertNull(blank.primeira().citacao());
        assertNull(blank.refrao());
        assertEquals(List.of("primeira.citacao", "segunda.citacao", "salmo", "evangelho.citacao"),
                SundayLibrary.missingSentences(blank));
        assertEquals("Is 45, 1, 4-6", blank.primeira().referencia());
        assertEquals("1Ts 1, 1-5", blank.segunda().referencia());
        assertEquals("Mt 22, 15-21", blank.evangelho().referencia());

        ObjectNode root = (ObjectNode) new ObjectMapper().readTree(file.toFile());
        ObjectNode sunday = (ObjectNode) root.get("sundays").get("ordinary-29/A");
        String gospel = "Dai a César o que é de César e a Deus o que é de Deus";
        ((ObjectNode) sunday.get("primeira")).put("citacao", "Tomei Ciro");
        ((ObjectNode) sunday.get("segunda")).put("citacao", "Recordamos a vossa fé");
        sunday.put("salmo", "Aclamai a glória do Senhor.");
        ((ObjectNode) sunday.get("evangelho")).put("citacao", gospel);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);

        LiturgyDay day = client.load(date, file);
        assertEquals("A", day.ano());
        assertEquals(gospel, day.evangelho().citacao());
        assertEquals(day.evangelho().citacao(), Cover.from(day).frase());
        assertNull(Cover.from(day).cover());

        LiturgyClient offline = new LiturgyClient(ignored -> {
            throw new IOException("calendar down");
        });
        assertEquals(day.domingo(), offline.load(date, file).domingo());
    }

    @Test
    void gaudeteUsesTheRoseCover() throws IOException {
        RomanCalendar calendar = RomanCalendar.parse("""
                {
                  "day": { "sunday_id": "advent-3", "sunday_cycle": "B", "season": "advent" },
                  "readings": { "first_reading": "Is 61:1-2, 10-11", "gospel": "Jn 1:6-8, 19-28" }
                }
                """);
        Path file = Files.createTempDirectory("sundays").resolve(SundayLibrary.FILE_NAME);
        LiturgyDay day = new SundayLibrary(file).resolve(LocalDate.of(2026, 12, 13), calendar);
        assertEquals("Domingo III", day.domingo());
        assertEquals("advento", day.seasonKey());
        assertEquals("Capa - Domingo III Advento", day.coverLayout());
        assertEquals("Jo 1, 6-8, 19-28", day.evangelho().referencia());
        assertNull(day.segunda());
        assertEquals(List.of("primeira.citacao", "salmo", "evangelho.citacao"), SundayLibrary.missingSentences(day));
        ObjectNode sunday = (ObjectNode) new ObjectMapper().readTree(file.toFile()).get("sundays").get("advent-3/B");
        assertEquals("", sunday.get("salmo").asText());
        assertFalse(sunday.has("refrao"));

        LiturgyDay filled = new SundayLibrary(file).rememberSentences(
                LocalDate.of(2026, 12, 13), "O espírito do Senhor", null, "A minha alma", "Veio um homem", "Outra");
        assertEquals("O espírito do Senhor", filled.primeira().citacao());
        assertEquals("A minha alma", filled.refrao());
        assertEquals("Veio um homem", filled.evangelho().citacao());

        LiturgyDay kept = new SundayLibrary(file).rememberSentences(
                LocalDate.of(2026, 12, 13), "Uma frase diferente", null, "Outro salmo", null, null);
        assertEquals(filled.primeira().citacao(), kept.primeira().citacao());
        assertEquals(filled.refrao(), kept.refrao());
    }

    @Test
    void aStoredRefraoIsReadAsSalmo() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve(SundayLibrary.FILE_NAME);
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
        Path file = Files.createTempDirectory("sundays").resolve(SundayLibrary.FILE_NAME);
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
            if (date.equals(LocalDate.of(2026, 1, 1))) {
                return day("solemnity", "Solemnity of Mary, the Holy Mother of God", "christmas", null);
            }
            throw new IOException("unexpected " + date);
        });

        LiturgyDay assumption = client.load(LocalDate.of(2026, 8, 15), file);
        assertEquals("Solenidade da Assunção da Virgem Santa Maria", assumption.domingo());
        assertEquals("Capa - Solenidade", assumption.coverLayout());
        assertEquals("comum", assumption.seasonKey());
        assertEquals("A", assumption.ano());
        assertEquals("", assumption.primeira().citacao());
        assertEquals("", assumption.evangelho().citacao());
        assertEquals("", assumption.refrao());

        LiturgyDay immaculate = client.load(LocalDate.of(2026, 12, 8), file);
        assertEquals("Solenidade da Imaculada Conceição da Virgem Santa Maria", immaculate.domingo());
        assertEquals("advento", immaculate.seasonKey());

        LiturgyDay mary = client.load(LocalDate.of(2026, 1, 1), file);
        assertEquals("Solenidade de Santa Maria, Mãe de Deus", mary.domingo());
        assertEquals("Capa - Solenidade Paz", mary.coverLayout());

        LiturgyDay apostles = client.load(LocalDate.of(2026, 6, 29), file);
        assertEquals("Solenidade dos Apóstolos São Pedro e São Paulo", apostles.domingo());
        assertEquals("Capa - Solenidade Pedro Paulo", apostles.coverLayout());
        assertFalse(Files.exists(file));
    }

    @Test
    void aWeekdayCaptionComesFromTheSundayThatOpensTheWeek() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve(SundayLibrary.FILE_NAME);
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
        assertNull(saturday.segunda());
        assertEquals("", saturday.primeira().citacao());
        assertEquals("", saturday.evangelho().citacao());
        assertEquals("", saturday.refrao());

        assertEquals("2ªf da semana XXIX", client.load(LocalDate.of(2026, 10, 19), file).domingo());
        assertEquals("2ªf", client.load(LocalDate.of(2026, 3, 30), file).domingo());
        assertEquals("quaresma", client.load(LocalDate.of(2026, 3, 30), file).seasonKey());
        assertFalse(Files.exists(file));
    }

    @Test
    void holyFamilyKeepsItsPortugueseName() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve(SundayLibrary.FILE_NAME);
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
        assertEquals(day.domingo(),
                new ObjectMapper().readTree(file.toFile()).get("sundays").get("holy-family/B").get("domingo").asText());
    }

    @Test
    void epiphanyAndBaptismUseTheirPortugueseNames() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve(SundayLibrary.FILE_NAME);
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
        Path file = Files.createTempDirectory("sundays").resolve(SundayLibrary.FILE_NAME);
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
        Path file = Files.createTempDirectory("sundays").resolve(SundayLibrary.FILE_NAME);
        LiturgyClient client = new LiturgyClient(date -> switch (date.toString()) {
            case "2027-05-09" -> sunday("easter-7", "easter");
            case "2027-05-23" -> sunday("trinity", "ordinary");
            case "2027-05-27" -> throw new IOException("The calendar returned nothing.");
            case "2027-11-21" -> sunday("ordinary-34", "ordinary");
            default -> throw new IOException("unexpected " + date);
        });

        assertNamed(client.load(LocalDate.of(2027, 5, 9), file),
                "Solenidade da Ascensão do Senhor", "pascoa");
        assertNamed(client.load(LocalDate.of(2027, 5, 23), file),
                "Solenidade da Santíssima Trindade", "comum");
        assertNamed(client.load(LocalDate.of(2027, 5, 27), file),
                "Solenidade do Santíssimo Corpo e Sangue de Cristo", "comum");
        assertNamed(client.load(LocalDate.of(2027, 11, 21), file),
                "Solenidade de Nosso Senhor Jesus Cristo, Rei do Universo", "comum");
        assertFalse(SundayLibrary.solemnityGold("Domingo XXIX"));
    }

    private static void assertNamed(LiturgyDay day, String title, String season) {
        assertEquals(title, day.domingo());
        assertEquals(season, day.seasonKey());
        assertTrue(SundayLibrary.solemnityGold(day.domingo()));
    }

    @Test
    void bibliotecaReplacesSentencesAndLeavesReferences() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve(SundayLibrary.FILE_NAME);
        Files.writeString(file, """
                {
                  "dates": {
                    "2027-03-07": "lent-1/B",
                    "2026-10-18": "ordinary-29/A"
                  },
                  "sundays": {
                    "ordinary-29/A": {
                      "domingo": "Domingo XXIX",
                      "season": "comum",
                      "primeira": { "referencia": "Is 45, 1.4-6", "citacao": "Frase antiga" },
                      "segunda": { "referencia": "1Ts 1, 1-5", "citacao": "Segunda antiga" },
                      "salmo": "Salmo antigo",
                      "evangelho": { "referencia": "Mt 22, 15-21", "citacao": "Evangelho antigo" }
                    },
                    "lent-1/B": {
                      "domingo": "Domingo I",
                      "season": "quaresma",
                      "primeira": { "referencia": "Gn 9, 8-15", "citacao": "" },
                      "salmo": "",
                      "evangelho": { "referencia": "Mc 1, 12-15", "citacao": "" }
                    }
                  }
                }
                """);
        List<SundayLibrary.SundayEntry> listed = new SundayLibrary(file).list();
        assertEquals(175, listed.size());
        assertEquals("advent-1/A", listed.get(0).key());
        assertEquals("I Domingo do Advento", listed.get(0).name());
        SundayLibrary.SundayEntry ordinary = listed.stream()
                .filter(entry -> entry.key().equals("ordinary-29/A")).findFirst().orElseThrow();
        SundayLibrary.SundayEntry lent = listed.stream()
                .filter(entry -> entry.key().equals("lent-1/B")).findFirst().orElseThrow();
        assertEquals(false, ordinary.incomplete());
        assertEquals("A", ordinary.ano());
        assertEquals(true, lent.incomplete());
        assertEquals(1, listed.stream().filter(entry -> entry.key().equals("all-saints")).count());
        assertEquals(1, listed.stream().filter(entry -> entry.key().equals("ordinary-29/A")).count());
        assertTrue(listed.stream().anyMatch(entry -> entry.key().equals("corpus-christi/C") && entry.solemnity()));
        int endOfYearA = indexOf(listed, "ordinary-34/A");
        assertTrue(endOfYearA < indexOf(listed, "advent-1/B"));
        assertTrue(indexOf(listed, "advent-2/A") < indexOf(listed, "immaculate-conception"));
        assertTrue(indexOf(listed, "immaculate-conception") < indexOf(listed, "advent-3/A"));

        SundayLibrary.SundayEntry replaced = new SundayLibrary(file).replaceSentences(
                "ordinary-29/A",
                "Frase nova",
                "Segunda nova",
                "Salmo novo",
                "Evangelho novo",
                "Louvor que não fica");
        assertEquals("Frase nova", replaced.primeira());
        assertEquals("Is 45, 1.4-6", replaced.primeiraRef());
        assertEquals("Segunda nova", replaced.segunda());
        assertEquals("1Ts 1, 1-5", replaced.segundaRef());
        assertEquals("", replaced.louvor());

        ObjectNode ordinaryNode = (ObjectNode) new ObjectMapper().readTree(file.toFile()).get("sundays").get("ordinary-29/A");
        assertEquals(replaced.primeiraRef(), ordinaryNode.get("primeira").get("referencia").asText());
        assertEquals(replaced.evangelhoRef(), ordinaryNode.get("evangelho").get("referencia").asText());
        assertFalse(ordinaryNode.has("louvor"));

        SundayLibrary.SundayEntry kept = new SundayLibrary(file).replaceSentences(
                "ordinary-29/A", null, null, null, null, null);
        assertEquals(replaced.primeira(), kept.primeira());

        SundayLibrary.SundayEntry lentDay = new SundayLibrary(file).replaceSentences(
                "lent-1/B", "Primeira da Quaresma", "Não criar", "Salmo da Quaresma", "Evangelho da Quaresma", "Louvor novo");
        assertEquals("Louvor novo", lentDay.louvor());
        assertEquals("Não criar", lentDay.segunda());
        ObjectNode lentNode = (ObjectNode) new ObjectMapper().readTree(file.toFile()).get("sundays").get("lent-1/B");
        assertEquals(lentDay.primeiraRef(), lentNode.get("primeira").get("referencia").asText());
        assertEquals(lentDay.segunda(), lentNode.get("segunda").get("citacao").asText());
        assertEquals(lentDay.louvor(), lentNode.get("louvor").asText());
        assertEquals(false, new SundayLibrary(file).list().stream()
                .filter(entry -> entry.key().equals("lent-1/B")).findFirst().orElseThrow().incomplete());
    }

    @Test
    void aStoredSolemnityIsUsedOnItsWeekday() throws IOException {
        Path file = Files.createTempDirectory("sundays").resolve(SundayLibrary.FILE_NAME);
        new SundayLibrary(file).replaceSentences(
                "all-saints", "Vi uma multidão", null, "Bendizei o Senhor", "Alegrai-vos e exultai", null);
        LiturgyClient client = new LiturgyClient(ignored -> """
                {
                  "day": { "sunday_cycle": "A", "season": "ordinary", "name": "All Saints" },
                  "readings": { "first_reading": "Rev 7:2-4, 9-14", "gospel": "Mt 5:1-12a" }
                }
                """);
        LiturgyDay day = client.load(LocalDate.of(2027, 11, 1), file);
        assertEquals("Solenidade de Todos os Santos", day.domingo());
        assertEquals("Vi uma multidão", day.primeira().citacao());
        assertEquals("Ap 7, 2-4, 9-14", day.primeira().referencia());
        assertEquals("Alegrai-vos e exultai", day.evangelho().citacao());
    }

    private static int indexOf(List<SundayLibrary.SundayEntry> entries, String key) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
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
