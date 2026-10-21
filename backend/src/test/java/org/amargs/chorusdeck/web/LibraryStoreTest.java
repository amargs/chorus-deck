package org.amargs.chorusdeck.web;

import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.index.SongIndex;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryStoreTest {

    @Test
    void aChosenIndexIsRememberedBesideTheApp() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        Path index = dir.resolve("shared").resolve("index.json");
        SongIndex.write(index, List.of(new SongCfg(1, "A festa da música", List.of("verso"), "118")));
        LibraryStore store = new LibraryStore(dir);

        assertNull(store.status().index());
        assertFalse(store.status().missing());
        assertEquals(index.toAbsolutePath().normalize(), store.save(index));

        LibraryStore again = new LibraryStore(dir);
        assertEquals(index.toAbsolutePath().normalize(), again.status().index());
        assertEquals(index.toAbsolutePath().normalize(), again.requireIndex());
    }

    @Test
    void aMissingSavedIndexAsksAgain() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(new SongCfg(1, "A festa da música", List.of("verso"))));
        LibraryStore store = new LibraryStore(dir);
        store.save(index);
        Files.delete(index);

        LibraryStore.Status status = store.status();
        assertNull(status.index());
        assertTrue(status.missing());
        assertThrows(IllegalArgumentException.class, store::requireIndex);
    }

    @Test
    void aFileThatIsNotAnIndexIsRefused() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        Path notes = dir.resolve("notes.json");
        Files.writeString(notes, "{\"hello\":1}");
        LibraryStore store = new LibraryStore(dir);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> store.save(notes));
        assertTrue(error.getMessage().contains("not a song index"));
        assertNull(store.status().index());
    }

    @Test
    void theOutputFolderIsRememberedAndSurvivesANewIndex() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        Path documents = Files.createDirectories(dir.resolve("Documentos"));
        Path elsewhere = Files.createDirectories(dir.resolve("Missa"));
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(new SongCfg(1, "A festa da música", List.of("verso"))));
        LibraryStore store = new LibraryStore(dir);

        assertNull(store.status().outputDirectory());
        assertEquals(documents.toAbsolutePath().normalize(), store.saveOutput(documents));
        assertEquals(documents.toAbsolutePath().normalize(), new LibraryStore(dir).status().outputDirectory());

        store.save(index);
        assertEquals(index.toAbsolutePath().normalize(), store.status().index());
        assertEquals(documents.toAbsolutePath().normalize(), store.status().outputDirectory());

        store.saveOutput(elsewhere);
        assertEquals(elsewhere.toAbsolutePath().normalize(), store.status().outputDirectory());
        Files.delete(elsewhere);
        assertNull(store.status().outputDirectory());
        assertEquals(index.toAbsolutePath().normalize(), store.status().index());
    }
}
