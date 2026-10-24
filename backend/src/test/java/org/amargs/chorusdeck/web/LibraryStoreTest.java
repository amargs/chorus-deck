package org.amargs.chorusdeck.web;

import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.index.SongIndex;
import org.amargs.chorusdeck.liturgy.SundayLibrary;
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
    void anIndexPathWithoutAModeAsksForAChoice() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        Path index = dir.resolve("dropbox").resolve("index.json");
        SongIndex.write(index, List.of(new SongCfg(4, "Já escolhido", List.of("verso"))));
        Files.writeString(dir.resolve(LibraryStore.FILE_NAME),
                "{\"indexFile\":\"" + index.toString().replace("\\", "\\\\") + "\"}");
        LibraryStore store = new LibraryStore(dir, seed(dir));

        LibraryStore.Status status = store.status();
        assertNull(status.index());
        assertNull(status.mode());
        assertFalse(status.missing());
        assertFalse(Files.exists(store.localDirectory().resolve("index.json")));
        assertThrows(IllegalArgumentException.class, store::requireIndex);
    }

    @Test
    void aMissingSavedIndexAsksAgain() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        Path shared = Files.createDirectories(dir.resolve("dropbox"));
        SongIndex.write(shared.resolve("index.json"), List.of(new SongCfg(1, "A festa da música", List.of("verso"))));
        LibraryStore store = new LibraryStore(dir, seed(dir));
        store.useFolder(shared);
        Files.delete(shared.resolve("index.json"));

        LibraryStore.Status status = store.status();
        assertNull(status.index());
        assertTrue(status.missing());
        assertThrows(IllegalArgumentException.class, store::requireIndex);
    }

    @Test
    void aFileThatIsNotAnIndexIsRefused() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        Path folder = Files.createDirectories(dir.resolve("bad"));
        Files.writeString(folder.resolve("index.json"), "{\"hello\":1}");
        LibraryStore store = new LibraryStore(dir, seed(dir));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> store.useFolder(folder));
        assertTrue(error.getMessage().contains("não é um índice de cânticos"));
        assertNull(store.status().index());
    }

    @Test
    void theOutputFolderIsRememberedAndSurvivesANewIndex() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        Path documents = Files.createDirectories(dir.resolve("Documentos"));
        Path elsewhere = Files.createDirectories(dir.resolve("Missa"));
        Path shared = Files.createDirectories(dir.resolve("dropbox"));
        SongIndex.write(shared.resolve("index.json"), List.of(new SongCfg(1, "A festa da música", List.of("verso"))));
        LibraryStore store = new LibraryStore(dir, seed(dir));

        assertNull(store.status().outputDirectory());
        assertEquals(documents.toAbsolutePath().normalize(), store.saveOutput(documents));
        assertEquals(documents.toAbsolutePath().normalize(), new LibraryStore(dir, seed(dir)).status().outputDirectory());

        store.useFolder(shared);
        assertEquals(shared.resolve("index.json").toAbsolutePath().normalize(), store.status().index());
        assertEquals(documents.toAbsolutePath().normalize(), store.status().outputDirectory());

        store.saveOutput(elsewhere);
        assertEquals(elsewhere.toAbsolutePath().normalize(), store.status().outputDirectory());
        Files.delete(elsewhere);
        assertNull(store.status().outputDirectory());
        assertEquals(shared.resolve("index.json").toAbsolutePath().normalize(), store.status().index());
    }

    @Test
    void localModeKeepsAnEditOnTheNextLaunch() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        LibraryStore store = new LibraryStore(dir, seed(dir));
        LibraryStore.Status first = store.useLocal();
        Path localIndex = store.localDirectory().resolve("index.json");
        assertEquals(localIndex.toAbsolutePath().normalize(), first.index());
        assertEquals(LibraryStore.MODE_LOCAL, first.mode());
        assertEquals("Do programa", SongIndex.read(localIndex).get(0).title());
        assertTrue(Files.isRegularFile(store.localDirectory().resolve(SundayLibrary.FILE_NAME)));

        SongIndex.write(localIndex, List.of(new SongCfg(9, "Acrescentado", List.of("verso"), "12")));
        LibraryStore again = new LibraryStore(dir, seed(dir));
        assertEquals("Acrescentado", SongIndex.read(again.useLocal().index()).get(0).title());
        assertEquals("Acrescentado", SongIndex.read(again.status().index()).get(0).title());
    }

    @Test
    void aFolderKeepsItsOwnFileAndFillsTheOneThatIsMissing() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        Path shared = Files.createDirectories(dir.resolve("dropbox"));
        SongIndex.write(shared.resolve("index.json"), List.of(new SongCfg(3, "Na pasta", List.of("verso"), "A1")));
        LibraryStore store = new LibraryStore(dir, seed(dir));

        LibraryStore.Status status = store.useFolder(shared);
        assertEquals(shared.resolve("index.json").toAbsolutePath().normalize(), status.index());
        assertEquals(shared.toAbsolutePath().normalize(), status.folder());
        assertEquals("Na pasta", SongIndex.read(status.index()).get(0).title());
        assertEquals("Do programa", Files.readString(shared.resolve(SundayLibrary.FILE_NAME)));

        Path other = Files.createDirectories(dir.resolve("outra"));
        Files.writeString(other.resolve(SundayLibrary.FILE_NAME), "Já tinha liturgia");
        status = store.useFolder(other);
        assertEquals("Na pasta", SongIndex.read(status.index()).get(0).title());
        assertEquals("Já tinha liturgia", Files.readString(other.resolve(SundayLibrary.FILE_NAME)));
        assertEquals("Na pasta", SongIndex.read(shared.resolve("index.json")).get(0).title());
    }

    @Test
    void switchingToLocalCopiesThePairInUse() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        Path shared = Files.createDirectories(dir.resolve("dropbox"));
        SongIndex.write(shared.resolve("index.json"), List.of(new SongCfg(5, "Partilhado", List.of("verso"))));
        Files.writeString(shared.resolve(SundayLibrary.FILE_NAME), "Liturgia partilhada");
        LibraryStore store = new LibraryStore(dir, seed(dir));
        store.useFolder(shared);

        LibraryStore.Status local = store.useLocal();
        assertEquals(LibraryStore.MODE_LOCAL, local.mode());
        assertEquals(shared.toAbsolutePath().normalize(), local.folder());
        assertEquals("Partilhado", SongIndex.read(local.index()).get(0).title());
        assertEquals("Liturgia partilhada", Files.readString(store.localDirectory().resolve(SundayLibrary.FILE_NAME)));

        SongIndex.write(shared.resolve("index.json"), List.of(new SongCfg(6, "Mudou depois", List.of("verso"))));
        assertEquals("Partilhado", SongIndex.read(new LibraryStore(dir, seed(dir)).status().index()).get(0).title());
    }

    @Test
    void importReplacesThePairInUseAndLeavesTheFolder() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        Path shared = Files.createDirectories(dir.resolve("dropbox"));
        SongIndex.write(shared.resolve("index.json"), List.of(new SongCfg(5, "Partilhado", List.of("verso"))));
        Files.writeString(shared.resolve(SundayLibrary.FILE_NAME), "Liturgia partilhada");
        LibraryStore store = new LibraryStore(dir, seed(dir));
        store.useFolder(shared);

        Path incoming = Files.createDirectories(dir.resolve("pen"));
        SongIndex.write(incoming.resolve("index.json"), List.of(new SongCfg(11, "Trazido", List.of("verso"), "A1")));
        Files.writeString(incoming.resolve(SundayLibrary.FILE_NAME), "Liturgia trazida");
        LibraryStore.Status status = store.importFrom(incoming);

        assertEquals(LibraryStore.MODE_FOLDER, status.mode());
        assertEquals(shared.toAbsolutePath().normalize(), status.folder());
        assertEquals(shared.resolve("index.json").toAbsolutePath().normalize(), status.index());
        assertEquals("Trazido", SongIndex.read(status.index()).get(0).title());
        assertEquals("Liturgia trazida", Files.readString(shared.resolve(SundayLibrary.FILE_NAME)));
        assertEquals("Trazido", SongIndex.read(incoming.resolve("index.json")).get(0).title());
    }

    @Test
    void importWithoutLiturgyKeepsTheCurrentOne() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        LibraryStore store = new LibraryStore(dir, seed(dir));
        store.useLocal();
        Path incoming = Files.createDirectories(dir.resolve("pen"));
        SongIndex.write(incoming.resolve("index.json"), List.of(new SongCfg(12, "Só cânticos", List.of("verso"))));

        LibraryStore.Status status = store.importFrom(incoming);

        assertEquals(LibraryStore.MODE_LOCAL, status.mode());
        assertEquals("Só cânticos", SongIndex.read(status.index()).get(0).title());
        assertEquals("Do programa", Files.readString(store.localDirectory().resolve(SundayLibrary.FILE_NAME)));
        Path empty = Files.createDirectories(dir.resolve("vazia"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> store.importFrom(empty));
        assertTrue(error.getMessage().contains("não tem um índice"));
        assertEquals("Só cânticos", SongIndex.read(store.status().index()).get(0).title());
    }

    @Test
    void exportCopiesThePairAndLeavesTheMode() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-library");
        Path shared = Files.createDirectories(dir.resolve("dropbox"));
        SongIndex.write(shared.resolve("index.json"), List.of(new SongCfg(8, "A exportar", List.of("verso"))));
        Files.writeString(shared.resolve(SundayLibrary.FILE_NAME), "Liturgia");
        LibraryStore store = new LibraryStore(dir, seed(dir));
        store.useFolder(shared);
        Path destination = Files.createDirectories(dir.resolve("copia"));

        assertEquals(destination.toAbsolutePath().normalize(), store.exportTo(destination));
        assertEquals("A exportar", SongIndex.read(destination.resolve("index.json")).get(0).title());
        assertEquals("Liturgia", Files.readString(destination.resolve(SundayLibrary.FILE_NAME)));
        LibraryStore.Status status = store.status();
        assertEquals(LibraryStore.MODE_FOLDER, status.mode());
        assertEquals(shared.toAbsolutePath().normalize(), status.folder());
    }

    private static LibrarySeed seed(Path dir) throws Exception {
        Path index = dir.resolve("seed-index.json");
        Path liturgy = dir.resolve("seed-liturgy.json");
        SongIndex.write(index, List.of(new SongCfg(1, "Do programa", List.of("verso"), "1")));
        Files.writeString(liturgy, "Do programa");
        return new LibrarySeed() {
            @Override
            public void copyIndex(Path destination) throws java.io.IOException {
                Files.createDirectories(destination.getParent());
                Files.copy(index, destination);
            }

            @Override
            public void copyLiturgy(Path destination) throws java.io.IOException {
                Files.createDirectories(destination.getParent());
                Files.copy(liturgy, destination);
            }
        };
    }
}
