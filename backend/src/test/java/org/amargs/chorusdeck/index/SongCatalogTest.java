package org.amargs.chorusdeck.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SongCatalogTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void aNewSongGetsTheNextIdAndLeavesTheOthers() throws Exception {
        List<SongCfg> songs = List.of(
                new SongCfg(2, "Já estava", List.of("verso"), "4"),
                new SongCfg(7, "Outro", List.of("verso"), "A1"));
        SongCatalog.Saved saved = SongCatalog.save(songs, body(null, "Novo cântico", "118", List.of("linha", "  ", "**refrão**")));

        assertEquals(8, saved.song().id());
        assertEquals("Novo cântico", saved.song().title());
        assertEquals(List.of("linha", "**refrão**"), saved.song().content());
        assertEquals("118", saved.song().bookId());
        assertEquals(songs, saved.songs().subList(0, 2));
        assertEquals(saved.song(), saved.songs().get(2));
    }

    @Test
    void anEditKeepsTheIdAndTheNeighbours() throws Exception {
        List<SongCfg> songs = List.of(
                new SongCfg(2, "Já estava", List.of("verso"), "4"),
                new SongCfg(7, "Outro", List.of("verso"), "A1"),
                new SongCfg(9, "Depois", List.of("verso"), "RP3"));
        SongCatalog.Saved saved = SongCatalog.save(songs, body(7, "Outro, corrigido", "", List.of()));

        assertEquals(7, saved.song().id());
        assertEquals("Outro, corrigido", saved.song().title());
        assertEquals(List.of(), saved.song().content());
        assertNull(saved.song().bookId());
        assertEquals(songs.get(0), saved.songs().get(0));
        assertEquals(songs.get(2), saved.songs().get(2));
    }

    @Test
    void aBlankTitleOrAnUnknownIdIsRefused() throws Exception {
        List<SongCfg> songs = List.of(new SongCfg(1, "A festa", List.of("verso"), "2"));
        IllegalArgumentException blank = assertThrows(
                IllegalArgumentException.class,
                () -> SongCatalog.save(songs, body(null, "  ", "2", List.of("verso"))));
        assertEquals("O cântico precisa de um título.", blank.getMessage());
        IllegalArgumentException unknown = assertThrows(
                IllegalArgumentException.class,
                () -> SongCatalog.save(songs, body(4, "Novo", "2", List.of("verso"))));
        assertEquals("Cântico desconhecido.", unknown.getMessage());
        assertEquals(songs, List.copyOf(songs));
    }

    @Test
    void anAlternativeTitleAndASecondLanguageAreStoredAndCanBeCleared() throws Exception {
        List<SongCfg> songs = List.of(new SongCfg(2, "Já estava", List.of("verso"), "4"));
        ObjectNode created = body(null, "Pelos caminhos", "12", List.of("um", "dois"));
        created.put("titleAlt", "  Per viam  ");
        created.set("contentAlt", JSON.valueToTree(List.of("unus", "  ", "duo")));
        SongCatalog.Saved saved = SongCatalog.save(songs, created);

        assertEquals("Per viam", saved.song().titleAlt());
        assertEquals(List.of("unus", "duo"), saved.song().contentAlt());
        assertEquals("pt", saved.song().lang());
        assertEquals("la", saved.song().langAlt());
        assertNull(saved.songs().get(0).titleAlt());

        ObjectNode named = body(saved.song().id(), "Pelos caminhos", "12", List.of("um", "dois"));
        named.put("titleAlt", "Per viam");
        named.set("contentAlt", JSON.valueToTree(List.of("unus", "duo")));
        named.put("lang", "en");
        named.put("langAlt", "fr");
        named.put("defaultLyrics", "contentAlt");
        SongCatalog.Saved withNames = SongCatalog.save(saved.songs(), named);
        assertEquals("en", withNames.song().lang());
        assertEquals("fr", withNames.song().langAlt());
        assertEquals("contentAlt", withNames.song().defaultLyrics());

        ObjectNode first = named.deepCopy();
        first.put("defaultLyrics", "content");
        assertNull(SongCatalog.save(withNames.songs(), first).song().defaultLyrics());

        ObjectNode badDefault = named.deepCopy();
        badDefault.put("defaultLyrics", "la");
        IllegalArgumentException notAColumn = assertThrows(
                IllegalArgumentException.class, () -> SongCatalog.save(withNames.songs(), badDefault));
        assertEquals("A língua por omissão tem de ser uma das línguas do cântico.", notAColumn.getMessage());

        ObjectNode same = named.deepCopy();
        same.put("langAlt", "en");
        IllegalArgumentException duplicate = assertThrows(
                IllegalArgumentException.class, () -> SongCatalog.save(withNames.songs(), same));
        assertEquals("As duas línguas têm de ser diferentes.", duplicate.getMessage());

        ObjectNode unknown = named.deepCopy();
        unknown.put("lang", "de");
        IllegalArgumentException bad = assertThrows(
                IllegalArgumentException.class, () -> SongCatalog.save(withNames.songs(), unknown));
        assertEquals(
                "A língua tem de ser português, inglês, espanhol, francês ou latim.",
                bad.getMessage());

        ObjectNode cleared = body(saved.song().id(), "Pelos caminhos", "12", List.of("um", "dois"));
        cleared.put("titleAlt", " ");
        cleared.set("contentAlt", JSON.valueToTree(List.of("  ")));
        SongCatalog.Saved again = SongCatalog.save(saved.songs(), cleared);
        assertNull(again.song().titleAlt());
        assertNull(again.song().contentAlt());
        assertNull(again.song().lang());
        assertNull(again.song().langAlt());
        assertNull(again.song().defaultLyrics());
        assertEquals(List.of("um", "dois"), again.song().content());
    }

    private static ObjectNode body(Integer id, String title, String bookId, List<String> content) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        if (id != null) {
            body.put("id", id);
        }
        body.put("title", title);
        body.put("bookId", bookId);
        body.set("content", JSON.valueToTree(content));
        return body;
    }
}
