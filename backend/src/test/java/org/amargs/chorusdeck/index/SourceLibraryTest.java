package org.amargs.chorusdeck.index;

import org.amargs.chorusdeck.config.AppConfig;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceLibraryTest {

    @Test
    void diffReportsOrphansNearTitlesAndAmbiguousNames() {
        List<SongCfg> songs = List.of(
                new SongCfg(1, "A festa da música", List.of("v")),
                new SongCfg(4, "A Ti Senhor meu Deus", List.of("v")),
                new SongCfg(8, "Adorasmus te o Christe", List.of("v")),
                new SongCfg(134, "Quero ser como tu, Maria", List.of("v")),
                new SongCfg(80, "Jesus Cristo és minha vida", List.of("v")),
                new SongCfg(190, "Jesus Cristo és minha vida", List.of("v")),
                new SongCfg(50, "Só no PowerPoint", List.of("v")));
        List<SongbookItem> books = List.of(
                new SongbookItem.Entry("2", "A festa da música"),
                new SongbookItem.Entry("3", "A Ti, Senhor, meu Deus"),
                new SongbookItem.Entry("12", "Adoramus te, O Christe"),
                new SongbookItem.Entry("147", "Quero ser como tu"),
                new SongbookItem.Entry("40", "Jesus Cristo és minha vida"),
                new SongbookItem.Entry("99", "Só no cancioneiro"),
                new SongbookItem.MissingPrefix("Sem código"));

        DiffService.Report report = DiffService.diff(songs, books);

        assertEquals(List.of(new DiffService.Report.BookOnly("99", "Só no cancioneiro")), report.songbookOnly());
        assertEquals(List.of(new DiffService.Report.SlideOnly(50, "Só no PowerPoint")), report.slideOnly());
        assertTrue(report.near().contains(new DiffService.Report.Near(
                "12", "Adoramus te, O Christe", 8, "Adorasmus te o Christe")));
        assertTrue(report.near().contains(new DiffService.Report.Near(
                "147", "Quero ser como tu", 134, "Quero ser como tu, Maria")));
        assertEquals(2, report.ambiguous().size());
        assertEquals(List.of("40"), report.ambiguous().get(0).bookIds());
        assertEquals(List.of("Sem código"), report.missingPrefix());
        assertTrue(report.noLyrics().isEmpty());
    }

    @Test
    void diffFlagsATitledSlideWhoseVersesAreBlank() {
        List<SongCfg> songs = List.of(
                new SongCfg(33, "Da pace cordium", List.of("")),
                new SongCfg(146, "Só Deus basta", List.of("  "), "159"),
                new SongCfg(1, "A festa da música", List.of("", "verso"), "2"));
        List<SongbookItem> books = List.of(
                new SongbookItem.Entry("159", "Só Deus basta"),
                new SongbookItem.Entry("2", "A festa da música"));

        DiffService.Report report = DiffService.diff(songs, books);

        assertEquals(List.of(
                new DiffService.Report.NoLyrics(33, null, "Da pace cordium"),
                new DiffService.Report.NoLyrics(146, "159", "Só Deus basta")), report.noLyrics());
        assertEquals(List.of(new DiffService.Report.SlideOnly(33, "Da pace cordium")), report.slideOnly());
        assertTrue(report.songbookOnly().isEmpty());
    }

    @Test
    void hashesChooseAFullRebuildOrASongbookUpdate() throws Exception {
        Path directory = Files.createTempDirectory("chorus-sources");
        Path config = directory.resolve("config.json");
        Files.writeString(config, """
                {"sourcePptx":"input.pptx","indexFile":"index.json","template4x3":"template.pptx","outputFile":"out.pptx"}
                """);
        Path pptx = directory.resolve("input.pptx");
        Path songbook = directory.resolve("songbook_default.docx");
        Path rites = directory.resolve("songbook_rites.docx");
        Path index = directory.resolve("index.json");
        Files.writeString(pptx, "slides");
        Files.writeString(songbook, "book");
        Files.writeString(rites, "rites");
        Files.writeString(index, "[]");

        AppConfig app = AppConfig.load(config);
        SourceSnapshot current = SourceHashes.of(app);
        assertEquals(SourceSnapshot.Refresh.FULL, SourceSnapshot.decide(null, current, true));
        SourceHashes.write(config, current);

        AppConfig reloaded = AppConfig.load(config);
        assertEquals(SourceSnapshot.Refresh.UNCHANGED, SourceSnapshot.decide(SourceHashes.read(config), SourceHashes.of(reloaded), true));

        Files.writeString(songbook, "book-edited");
        assertEquals(SourceSnapshot.Refresh.SONGBOOK,
                SourceSnapshot.decide(SourceHashes.read(config), SourceHashes.of(app), true));
        assertEquals(SourceSnapshot.Refresh.FULL,
                SourceSnapshot.decide(SourceHashes.read(config), SourceHashes.of(app), false));

        Files.writeString(pptx, "slides-edited");
        assertEquals(SourceSnapshot.Refresh.FULL,
                SourceSnapshot.decide(SourceHashes.read(config), SourceHashes.of(app), true));
    }
}
