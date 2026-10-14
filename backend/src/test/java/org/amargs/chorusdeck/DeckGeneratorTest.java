package org.amargs.chorusdeck;

import org.junit.jupiter.api.Test;

import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckGeneratorTest {

    @Test
    void lockedOutputIsRecognizedOnWindowsAndWsl() throws Exception {
        assertTrue(DeckGenerator.outputIsLocked(new AccessDeniedException("out_4x3.pptx")));
        assertTrue(DeckGenerator.outputIsLocked(new FileSystemException(
                "out_4x3.pptx",
                null,
                "The process cannot access the file because it is being used by another process")));
        assertFalse(DeckGenerator.outputIsLocked(new NoSuchFileException("out_4x3.pptx")));
    }
}
