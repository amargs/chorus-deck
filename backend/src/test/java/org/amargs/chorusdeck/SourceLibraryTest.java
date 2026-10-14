package org.amargs.chorusdeck;

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
        List<SongbookReader.Item> books = List.of(
                new SongbookReader.Entry("2", "A festa da música"),
                new SongbookReader.Entry("3", "A Ti, Senhor, meu Deus"),
                new SongbookReader.Entry("12", "Adoramus te, O Christe"),
                new SongbookReader.Entry("147", "Quero ser como tu"),
                new SongbookReader.Entry("40", "Jesus Cristo és minha vida"),
                new SongbookReader.Entry("99", "Só no cancioneiro"),
                new SongbookReader.MissingPrefix("Sem código"));

        SourceDiff.Report report = SourceDiff.diff(songs, books);

        assertEquals(List.of("99\tSó no cancioneiro"), report.songbookOnly());
        assertEquals(List.of("50\tSó no PowerPoint"), report.slideOnly());
        assertTrue(report.near().contains("12\tAdoramus te, O Christe\t8\tAdorasmus te o Christe"));
        assertTrue(report.near().contains("147\tQuero ser como tu\t134\tQuero ser como tu, Maria"));
        assertEquals(List.of(
                "| songbook id | songbook title    | slide id | slide title              |",
                "|-------------|-------------------|----------|--------------------------|",
                "| 147         | Quero ser como tu | 134      | Quero ser como tu, Maria |"),
                SourceDiff.table(List.of("147\tQuero ser como tu\t134\tQuero ser como tu, Maria"),
                        "songbook id", "songbook title", "slide id", "slide title"));
        assertEquals(2, report.ambiguous().size());
        assertTrue(report.ambiguous().get(0).startsWith("40\t"));
        assertEquals(List.of("Sem código"), report.missingPrefix());
        assertTrue(report.noLyrics().isEmpty());
    }

    @Test
    void diffFlagsATitledSlideWhoseVersesAreBlank() {
        List<SongCfg> songs = List.of(
                new SongCfg(33, "Da pace cordium", List.of("")),
                new SongCfg(146, "Só Deus basta", List.of("  "), "159"),
                new SongCfg(1, "A festa da música", List.of("", "verso"), "2"));
        List<SongbookReader.Item> books = List.of(
                new SongbookReader.Entry("159", "Só Deus basta"),
                new SongbookReader.Entry("2", "A festa da música"));

        SourceDiff.Report report = SourceDiff.diff(songs, books);

        assertEquals(List.of("33\t\tDa pace cordium", "146\t159\tSó Deus basta"), report.noLyrics());
        assertEquals(List.of("33\tDa pace cordium"), report.slideOnly());
        assertTrue(report.songbookOnly().isEmpty());
    }

    @Test
    void hashesChooseAFullRebuildOrASongbookUpdate() throws Exception {
        Path directory = Files.createTempDirectory("chorus-sources");
        Path config = directory.resolve("config.json");
        Files.writeString(config, """
                {"sourcePptx":"input.pptx","indexFile":"index.json","template4x3":"template.pptx","outputFile":"out.pptx","boldWeight":"medium"}
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
        SourceSnapshot current = SourceSnapshot.of(app);
        assertEquals(SourceSnapshot.Refresh.FULL, SourceSnapshot.decide(null, current, true));
        current.write(config);

        AppConfig reloaded = AppConfig.load(config);
        assertEquals("medium", reloaded.boldWeight());
        assertEquals(SourceSnapshot.Refresh.UNCHANGED, SourceSnapshot.decide(SourceSnapshot.read(config), SourceSnapshot.of(app), true));

        Files.writeString(songbook, "book-edited");
        assertEquals(SourceSnapshot.Refresh.SONGBOOK,
                SourceSnapshot.decide(SourceSnapshot.read(config), SourceSnapshot.of(app), true));
        assertEquals(SourceSnapshot.Refresh.FULL,
                SourceSnapshot.decide(SourceSnapshot.read(config), SourceSnapshot.of(app), false));

        Files.writeString(pptx, "slides-edited");
        assertEquals(SourceSnapshot.Refresh.FULL,
                SourceSnapshot.decide(SourceSnapshot.read(config), SourceSnapshot.of(app), true));
    }
}
