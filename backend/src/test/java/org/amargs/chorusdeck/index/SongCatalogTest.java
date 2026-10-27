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
