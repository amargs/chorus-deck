package org.amargs.chorusdeck.liturgy;

import org.amargs.chorusdeck.deck.MassRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LentenAcclamationTest {

    private static Path louvorFile() {
        Path data = Path.of("data", "louvor.json");
        if (Files.isRegularFile(data)) {
            return data;
        }
        Path nested = Path.of("backend", "data", "louvor.json");
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        throw new IllegalStateException("louvor.json not found");
    }

    @Test
    void eachLentSundayHasItsVerseAndYearBSharesOne() {
        Path file = louvorFile();
        assertTrue(LentenAcclamation.verse(file, "lent-1/A").startsWith("Nem só de pão"));
        assertEquals(LentenAcclamation.verse(file, "lent-1/A"), LentenAcclamation.verse(file, "lent-1/B"));
        assertEquals(LentenAcclamation.verse(file, "lent-1/A"), LentenAcclamation.verse(file, "lent-1/C"));
        assertEquals(LentenAcclamation.verse(file, "lent-3/B"), LentenAcclamation.verse(file, "lent-4/B"));
        assertTrue(LentenAcclamation.verse(file, "lent-3/B").startsWith("Deus amou tanto o mundo"));
        assertTrue(LentenAcclamation.verse(file, "lent-4/A").startsWith("Eu sou a luz do mundo"));
        assertTrue(LentenAcclamation.verse(file, "lent-4/C").startsWith("Vou partir"));
        assertTrue(LentenAcclamation.verse(file, "palm-sunday/A").startsWith("Cristo obedeceu"));
        assertEquals(LentenAcclamation.verse(file, "palm-sunday/A"), LentenAcclamation.verse(file, "palm-sunday/C"));
        assertNull(LentenAcclamation.verse(file, "ordinary-29/A"));
        assertNull(LentenAcclamation.verse(file, "advent-2/B"));
    }

    @Test
    void aMassFileVerseReplacesTheStoredOne() throws Exception {
        Path yaml = Files.createTempDirectory("mass").resolve("domingo.yml");
        Files.writeString(yaml, """
                date: 2026-03-01
                leituras:
                  louvor: "Verso desta missa"
                opcoes:
                  credo: longo
                """);
        LiturgyDay stored = new LiturgyDay(
                LocalDate.of(2026, 3, 1),
                "Domingo III",
                "quaresma",
                null,
                "B",
                new LiturgyDay.Reading("Ex 17, 3-7", "Dai-nos água"),
                new LiturgyDay.Reading("Rom 5, 1-2", "A paz"),
                "Aclamai",
                new LiturgyDay.Reading("Jo 4, 5-42", "Água viva"),
                LentenAcclamation.verse(louvorFile(), "lent-3/B"));
        LiturgyDay filled = MassRequest.load(yaml).withLiturgy(stored).liturgy();
        assertEquals("Verso desta missa", filled.louvor());
        assertEquals("Dai-nos água", filled.primeira().citacao());
    }

    @Test
    void aNewLentSundayStoresTheVerse() throws Exception {
        Path directory = Files.createTempDirectory("sundays");
        Files.copy(louvorFile(), directory.resolve("louvor.json"));
        Path file = directory.resolve(SundayLibrary.FILE_NAME);
        RomanCalendar calendar = RomanCalendar.parse("""
                {
                  "day": { "sunday_id": "lent-5", "sunday_cycle": "C", "season": "lent" },
                  "readings": { "first_reading": "Is 43:16-21", "gospel": "Jn 8:1-11" }
                }
                """);
        LiturgyDay day = new SundayLibrary(file).resolve(LocalDate.of(2028, 4, 2), calendar);
        assertEquals(LentenAcclamation.verse(louvorFile(), "lent-5/C"), day.louvor());
        assertTrue(Files.readString(file).contains("Convertei-vos a Mim"));
    }
}
