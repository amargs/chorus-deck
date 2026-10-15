package org.amargs.chorusdeck.liturgia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(day.evangelho().citacao(), day.cover().frase());
        assertNull(day.cover().cover());

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
}
