package org.amargs.chorusdeck.deck;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckGeneratorTest {

    @Test
    void outputProbeAllowsAMissingOrClosedFile() throws Exception {
        Path missing = Files.createTempDirectory("chorus-deck").resolve("out.pptx");
        assertDoesNotThrow(() -> DeckFile.ensureWritable(missing));

        Path closed = Files.createTempFile("chorus-deck", ".pptx");
        assertDoesNotThrow(() -> DeckFile.ensureWritable(closed));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void outputProbeRejectsAFileHeldExclusive() throws Exception {
        Path file = Files.createTempFile("chorus-deck", ".pptx");
        Process lock = new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-Command",
                "$f = [System.IO.File]::Open('" + file.toAbsolutePath() + "', 'Open', 'ReadWrite', 'None'); "
                        + "[Console]::Out.WriteLine('locked'); [Console]::Out.Flush(); "
                        + "try { [Console]::In.ReadLine() | Out-Null } finally { $f.Close() }")
                .redirectErrorStream(true)
                .start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(lock.getInputStream(), StandardCharsets.UTF_8))) {
            if (!"locked".equals(reader.readLine())) {
                throw new IllegalStateException("Could not lock " + file);
            }
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class, () -> DeckFile.ensureWritable(file));
            assertTrue(error.getMessage().contains("because it is open"));
        } finally {
            lock.destroy();
        }
    }

    @Test
    void lockedOutputIsRecognizedOnWindowsAndWsl() throws Exception {
        assertTrue(DeckFile.isLocked(new AccessDeniedException("out_4x3.pptx")));
        assertTrue(DeckFile.isLocked(new FileSystemException(
                "out_4x3.pptx",
                null,
                "The process cannot access the file because it is being used by another process")));
        assertFalse(DeckFile.isLocked(new NoSuchFileException("out_4x3.pptx")));
    }
}
