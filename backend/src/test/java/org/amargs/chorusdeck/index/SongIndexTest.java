package org.amargs.chorusdeck.index;

import org.amargs.chorusdeck.model.SongCfg;
import org.amargs.chorusdeck.model.SongbookItem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongIndexTest {

    @Test
    void titleOnlySlideIndexesAsASongWithNoLyrics() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFSlide titleOnly = ppt.createSlide();
            XSLFTextBox title = titleOnly.createTextBox();
            title.setPlaceholder(Placeholder.TITLE);
            title.setText("12\nVem connosco a caminhar");

            XSLFSlide withLyrics = ppt.createSlide();
            XSLFTextBox otherTitle = withLyrics.createTextBox();
            otherTitle.setPlaceholder(Placeholder.TITLE);
            otherTitle.setText("Cântico:\nOutra");
            XSLFTextBox body = withLyrics.createTextBox();
            body.setText("verso");

            XSLFSlide count = ppt.createSlide();
            XSLFTextBox countTitle = count.createTextBox();
            countTitle.setPlaceholder(Placeholder.TITLE);
            countTitle.setText("Número Total de Cânticos: 176");
            XSLFSlide list = ppt.createSlide();
            list.createTextBox().setText("A festa da música");

            List<SongCfg> songs = SlideSongs.index(ppt).songs();

            assertEquals(List.of("Vem connosco a caminhar", "Outra"), songs.stream().map(SongCfg::title).toList());
            assertEquals(List.of(""), songs.get(0).content());
            assertEquals(List.of("verso"), songs.get(1).content());
        }
    }

    @Test
    void songTitleDropsTheLineBeforeTheBreakAndSurroundingSpace() {
        assertEquals("Misericordias domini", SlideSongs.songTitle("\n Misericordias domini"));
        assertEquals("Não sei como louvar-te", SlideSongs.songTitle("94\nNão sei como louvar-te "));
    }

    @Test
    void normalizerDropsPunctuationAndApostropheShape() {
        assertEquals(
                TitleNormalizer.normalize("A Ti Senhor meu Deus"),
                TitleNormalizer.normalize("A Ti, Senhor, meu Deus"));
        assertEquals(
                TitleNormalizer.normalize("A minh'alma descansa em paz"),
                TitleNormalizer.normalize("A minh’alma descansa em paz"));
        assertEquals("quero ser como tu maria", TitleNormalizer.normalize("  Quero   ser como tu, Maria "));
        assertFalse(TitleNormalizer.normalize("Adorasmus te o Christe")
                .equals(TitleNormalizer.normalize("Adoramus te, O Christe")));
    }

    @Test
    void bookIdAttachesOnNormalizedTitleWithoutChangingIds() {
        List<SongCfg> songs = List.of(
                new SongCfg(4, "A Ti Senhor meu Deus", List.of("verso"), "old"),
                new SongCfg(8, "Adorasmus te o Christe", List.of("verso")),
                new SongCfg(9, "Quero ser como tu, Maria", List.of("verso")));
        List<SongbookItem.Entry> books = List.of(
                new SongbookItem.Entry("3", "A Ti, Senhor, meu Deus"),
                new SongbookItem.Entry("12", "Adoramus te, O Christe"),
                new SongbookItem.Entry("147", "Quero ser como tu"));
        List<String> warnings = new ArrayList<>();

        List<SongCfg> attached = BookIds.attach(songs, books, warnings);

        assertEquals("3", attached.get(0).bookId());
        assertEquals(4, attached.get(0).id());
        assertNull(attached.get(1).bookId());
        assertNull(attached.get(2).bookId());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void reindexKeepsIdAndBookIdAndAppendsNewSongs() {
        List<SongCfg> previous = List.of(
                new SongCfg(8, "A Ti Senhor meu Deus", List.of("old"), "3"),
                new SongCfg(2, "A festa da música", List.of("old"), "2"),
                new SongCfg(4, "Saiu", List.of("old"), "9"));
        List<SongCfg> fresh = List.of(
                new SongCfg(1, "A Ti, Senhor, meu Deus", List.of("new")),
                new SongCfg(2, "Canção nova", List.of("new")),
                new SongCfg(3, "A festa da música", List.of("new")));

        SongIds.IdMatch match = SongIds.assign(fresh, previous);

        assertEquals(8, match.songs().get(0).id());
        assertEquals("3", match.songs().get(0).bookId());
        assertEquals("new", match.songs().get(0).content().get(0));
        assertEquals(9, match.songs().get(1).id());
        assertNull(match.songs().get(1).bookId());
        assertEquals(2, match.songs().get(2).id());
        assertEquals("2", match.songs().get(2).bookId());
        assertEquals(List.of(4), match.removed().stream().map(SongCfg::id).toList());
        assertEquals(List.of(9), match.added().stream().map(SongCfg::id).toList());
    }

    @Test
    void omittedBookIdStaysAbsentInJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(List.of(new SongCfg(1, "A", List.of("v"))));
        assertFalse(json.contains("bookId"));
        List<SongCfg> read = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<SongCfg>>() {});
        assertNull(read.get(0).bookId());
    }

    @Test
    void realLibraryMatchesPunctuationAndKeepsNearMissesUnmatched() throws Exception {
        Path dir = Path.of("data");
        if (!Files.isRegularFile(dir.resolve("index.json"))) {
            dir = Path.of("backend", "data");
        }
        Path index = dir.resolve("index.json");
        assertTrue(Files.isRegularFile(index), "index.json not found");
        List<SongCfg> songs = new ObjectMapper().readValue(index.toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<List<SongCfg>>() {});
        SongbookReader reader = new SongbookReader();
        List<SongbookItem.Entry> entries = new ArrayList<>();
        for (String name : List.of("songbook_default.docx", "songbook_rites.docx")) {
            for (SongbookItem item : reader.read(dir.resolve(name))) {
                if (item instanceof SongbookItem.Entry entry) {
                    entries.add(entry);
                }
            }
        }

        List<SongCfg> attached = BookIds.attach(songs, entries, new ArrayList<>());

        assertEquals("2", bookId(attached, "A festa da música"));
        assertEquals("3", bookId(attached, "A Ti Senhor meu Deus"));
        assertNull(bookId(attached, "Adorasmus te o Christe"));
        assertNull(bookId(attached, "Quero ser como tu, Maria"));
        assertEquals(4, song(attached, "A Ti Senhor meu Deus").id());
    }

    @Test
    void dropsBoldWhenTheOnlySlideIsFullyBold() {
        SongCfg single = new SongCfg(8, "Song", new ArrayList<>(List.of("**tudo em negrito**")));
        SongCfg multi = new SongCfg(6, "Song", new ArrayList<>(List.of("**refrão**", "verso")));
        SlideSongs.stripUniformBold(List.of(single, multi));
        assertEquals("tudo em negrito", single.content().get(0));
        assertEquals("**refrão**", multi.content().get(0));
    }

    private static String bookId(List<SongCfg> songs, String title) {
        return song(songs, title).bookId();
    }

    private static SongCfg song(List<SongCfg> songs, String title) {
        return songs.stream().filter(song -> title.equals(song.title())).findFirst().orElseThrow();
    }
}
