package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.amargs.chorusdeck.deck.Cover;
import org.amargs.chorusdeck.text.BoldMarkup;
import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.index.SongIndex;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongDeckServerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void thePageAsksForAnIndexAndThenReadsThatFile() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck");
        Path configIndex = dir.resolve("config-index.json");
        Path chosen = dir.resolve("shared").resolve("index.json");
        SongIndex.write(configIndex, List.of(new SongCfg(1, "From config", List.of("verso"))));
        SongIndex.write(chosen, List.of(new SongCfg(7, "From dropbox", List.of("verso"), "118")));
        Javalin app = app(dir, configIndex, false);
        try {
            HttpResponse<String> missing = send(app, HttpRequest.newBuilder(uri(app, "/api/songs")).GET().build());
            assertEquals(400, missing.statusCode());
            assertTrue(missing.body().contains("Choose the song index first."));

            HttpResponse<String> saved = send(app, HttpRequest.newBuilder(uri(app, "/api/library"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("index", chosen.toString()))))
                    .build());
            assertEquals(200, saved.statusCode());
            JsonNode library = JSON.readTree(saved.body());
            assertEquals(chosen.toAbsolutePath().normalize().toString(), library.get("index").asText());
            assertEquals(chosen.getParent().toAbsolutePath().normalize().toString(), library.get("directory").asText());

            JsonNode songs = JSON.readTree(send(app, HttpRequest.newBuilder(uri(app, "/api/songs")).GET().build()).body());
            assertEquals(1, songs.size());
            assertEquals(7, songs.get(0).get("id").asInt());

            HttpResponse<String> other = post(app, "{\"ids\":[1],\"output\":\"a.pptx\"}");
            assertEquals(400, other.statusCode());
            assertTrue(other.body().contains("Unknown song id: 1"));

            HttpResponse<String> generated = post(app, "{\"ids\":[7],\"output\":\"canto.pptx\"}");
            assertEquals(200, generated.statusCode());
            Path output = chosen.getParent().resolve("canto.pptx");
            assertEquals(output.toAbsolutePath().normalize().toString(), JSON.readTree(generated.body()).get("output").asText());
            assertTrue(Files.isRegularFile(output));
            assertFalse(Files.exists(dir.resolve("no-template.pptx")));
        } finally {
            app.stop();
        }
    }

    @Test
    void fontsListsTheTemplateFacesThatAreNotInstalled() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck");
        HttpResponse<String> response = get(app(dir, dir.resolve("index.json")), "/api/fonts");
        assertEquals(200, response.statusCode());
        assertEquals(JSON.valueToTree(Map.of("missing", BoldMarkup.missingFontLinks())), JSON.readTree(response.body()));
    }

    @Test
    void songListMarksASongWithoutVerses() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck");
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(
                new SongCfg(1, "A festa da música", List.of("verso"), "118"),
                new SongCfg(2, "Sem letra", List.of(), "4"),
                new SongCfg(3, "Sem número", List.of("verso"))));
        HttpResponse<String> response = get(app(dir, index), "/api/songs");
        assertEquals(200, response.statusCode());
        JsonNode songs = JSON.readTree(response.body());
        assertEquals("118", songs.get(0).get("bookId").asText());
        assertTrue(songs.get(0).get("hasVerses").asBoolean());
        assertFalse(songs.get(1).get("hasVerses").asBoolean());
        assertFalse(songs.get(2).has("bookId"));
    }

    @Test
    void generateRejectsAnEmptyListAnUnknownIdAndASongWithoutVerses() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck");
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(
                new SongCfg(1, "A festa da música", List.of("verso"), "118"),
                new SongCfg(2, "Sem letra", List.of(), "4")));
        Javalin app = app(dir, index);
        try {
            assertEquals(400, post(app, "{\"ids\":[]}").statusCode());
            HttpResponse<String> unknown = post(app, "{\"ids\":[9],\"output\":\"a.pptx\"}");
            assertEquals(400, unknown.statusCode());
            assertTrue(unknown.body().contains("Unknown song id: 9"));
            HttpResponse<String> empty = post(app, "{\"ids\":[2],\"output\":\"a.pptx\"}");
            assertEquals(400, empty.statusCode());
            assertTrue(empty.body().contains("Song 2 has no verses."));
        } finally {
            app.stop();
        }
    }

    @Test
    void generateRequiresCoverFieldsAndKeepsTheOnesThatWereSent() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck");
        List<SongCfg> songs = List.of(new SongCfg(1, "A festa da música", List.of("verso"), "118"));
        SongIndex.write(dir.resolve("index.json"), songs);
        JsonNode incomplete = JSON.readTree("""
                {"ids":[1],"output":"a.pptx","cover":{"season":"advento"}}
                """);
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> SongDeckRequests.parse(incomplete, songs, dir));
        assertTrue(missing.getMessage().contains("cover requires"));

        JsonNode withoutPhrase = JSON.readTree("""
                {
                  "ids": [1],
                  "output": "a.pptx",
                  "cover": {
                    "season": "advento",
                    "ano": "A",
                    "domingo": "Domingo III"
                  }
                }
                """);
        SongDeckRequests.Ready blankPhrase = SongDeckRequests.parse(withoutPhrase, songs, dir);
        assertEquals(new Cover("advento", null, "Domingo III", "", "", "A"), blankPhrase.cover());

        JsonNode body = JSON.readTree("""
                {
                  "ids": [1],
                  "output": "Domingo III Advento - Ano B.pptx",
                  "cover": {
                    "season": "advento",
                    "ano": "b",
                    "domingo": "Domingo III",
                    "frase": "A festa",
                    "versiculo": "Jo 1, 1",
                    "layout": "Capa - Domingo III Advento"
                  }
                }
                """);
        SongDeckRequests.Ready ready = SongDeckRequests.parse(body, songs, dir);
        assertEquals(List.of(1), ready.ids());
        assertEquals(dir.toAbsolutePath().normalize().resolve("Domingo III Advento - Ano B.pptx"), ready.output());
        assertEquals(new Cover(
                "advento",
                "Capa - Domingo III Advento",
                "Domingo III",
                "A festa",
                "Jo 1, 1",
                "B"), ready.cover());

        Javalin app = app(dir, dir.resolve("index.json"));
        try {
            HttpResponse<String> response = post(app, JSON.writeValueAsString(JSON.readTree("""
                    {"ids":[1],"output":"a.pptx","cover":{"season":"advento"}}
                    """)));
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("cover requires"));
        } finally {
            app.stop();
        }
    }

    @Test
    void anOtherCoverKeepsTheChosenLayoutAndHeader() throws Exception {
        List<SongCfg> songs = List.of(new SongCfg(1, "A festa da música", List.of("verso"), "118"));
        Path dir = Path.of("decks").toAbsolutePath();
        JsonNode body = JSON.readTree("""
                {
                  "ids": [1],
                  "output": "Ensaio.pptx",
                  "cover": {
                    "kind": "outro",
                    "layout": "Capa - Advento",
                    "domingo": "Ensaio",
                    "header": "Ensaio do coro",
                    "frase": "A festa",
                    "versiculo": "Sl 1"
                  }
                }
                """);
        SongDeckRequests.Ready ready = SongDeckRequests.parse(body, songs, dir);
        assertEquals(new Cover(
                "comum",
                "Capa - Advento",
                "Ensaio",
                "A festa",
                "Sl 1",
                null,
                "Ensaio do coro"), ready.cover());
        JsonNode unknown = JSON.readTree("""
                {"ids":[1],"output":"a.pptx","cover":{"kind":"outro","layout":"Capa inventada","domingo":"Ensaio","header":"Linha","frase":"A","versiculo":"Sl 1"}}
                """);
        assertThrows(IllegalArgumentException.class, () -> SongDeckRequests.parse(unknown, songs, dir));
    }

    @Test
    void aDeckWithoutACoverLeavesTheCoverEmpty() {
        List<SongCfg> songs = List.of(new SongCfg(1, "A festa da música", List.of("verso"), "118"));
        Path dir = Path.of("decks").toAbsolutePath();
        JsonNode body = JSON.createObjectNode()
                .put("output", "cânticos")
                .set("ids", JSON.createArrayNode().add(1));
        SongDeckRequests.Ready ready = SongDeckRequests.parse(body, songs, dir);
        assertNull(ready.cover());
        assertEquals(dir.resolve("cânticos.pptx"), ready.output());
    }

    @Test
    void aChosenDirectoryIsUsedAndASeparatedFileNameIsRejected() throws Exception {
        List<SongCfg> songs = List.of(new SongCfg(1, "A festa da música", List.of("verso"), "118"));
        Path fallback = Files.createTempDirectory("chorus-deck-default");
        Path chosen = Files.createTempDirectory("chorus-deck-chosen");
        var body = JSON.createObjectNode();
        body.put("output", "Missa.pptx");
        body.put("directory", chosen.toString());
        body.set("ids", JSON.createArrayNode().add(1));

        SongDeckRequests.Ready ready = SongDeckRequests.parse(body, songs, fallback);
        assertEquals(chosen.toAbsolutePath().normalize().resolve("Missa.pptx"), ready.output());

        body.put("output", "pasta\\Missa.pptx");
        assertThrows(IllegalArgumentException.class, () -> SongDeckRequests.parse(body, songs, fallback));
        body.put("output", "pasta/Missa.pptx");
        assertThrows(IllegalArgumentException.class, () -> SongDeckRequests.parse(body, songs, fallback));

        body.put("output", "Missa.pptx");
        body.put("directory", chosen.resolve("ausente").toString());
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> SongDeckRequests.parse(body, songs, fallback));
        assertTrue(missing.getMessage().contains("does not exist"));

        Path file = Files.createTempFile("chorus-deck", ".txt");
        try {
            body.put("directory", file.toString());
            IllegalArgumentException notFolder = assertThrows(
                    IllegalArgumentException.class,
                    () -> SongDeckRequests.parse(body, songs, fallback));
            assertTrue(notFolder.getMessage().contains("directory"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void openIsLimitedToTheLastGeneratedFile() throws Exception {
        Path file = Files.createTempFile("chorus-deck", ".pptx");
        Path last = file.toAbsolutePath().normalize();
        LocalFiles.OpenPlan select = LocalFiles.plan(last, "folder", last);
        assertEquals(LocalFiles.OpenKind.SELECT, select.kind());
        assertEquals(last, select.path());
        assertEquals(LocalFiles.OpenKind.FILE, LocalFiles.plan(last, "file", last).kind());

        Files.delete(file);
        LocalFiles.OpenPlan folder = LocalFiles.plan(last, "folder", last);
        assertEquals(LocalFiles.OpenKind.FOLDER, folder.kind());
        assertEquals(last.getParent(), folder.path());
        assertThrows(IllegalArgumentException.class, () -> LocalFiles.plan(last, "file", last));
        assertThrows(IllegalArgumentException.class, () -> LocalFiles.plan(last.resolveSibling("outro.pptx"), "folder", last));
        assertThrows(IllegalArgumentException.class, () -> LocalFiles.plan(last, "file", null));

        Path dir = Files.createTempDirectory("chorus-deck");
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(new SongCfg(1, "A festa da música", List.of("verso"), "118")));
        Javalin app = app(dir, index);
        try {
            HttpResponse<String> directory = send(app, HttpRequest.newBuilder(uri(app, "/api/directory")).GET().build());
            assertEquals(200, directory.statusCode());
            assertEquals(dir.toAbsolutePath().normalize().toString(), JSON.readTree(directory.body()).get("directory").asText());

            String json = JSON.writeValueAsString(Map.of("path", last.resolveSibling("outro.pptx").toString(), "what", "file"));
            HttpResponse<String> rejected = send(app, HttpRequest.newBuilder(uri(app, "/api/open"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build());
            assertEquals(400, rejected.statusCode());
            assertFalse(rejected.body().isBlank());
        } finally {
            app.stop();
        }
    }

    private static Javalin app(Path dir, Path index) throws Exception {
        return app(dir, index, true);
    }

    private static Javalin app(Path dir, Path index, boolean remember) throws Exception {
        if (remember && Files.isRegularFile(index)) {
            new LibraryStore(dir).save(index);
        }
        return SongDeckServer.create(dir.resolve("config.json")).start("127.0.0.1", 0);
    }

    private static HttpResponse<String> get(Javalin app, String path) throws Exception {
        try {
            return send(app, HttpRequest.newBuilder(uri(app, path)).GET().build());
        } finally {
            app.stop();
        }
    }

    private static HttpResponse<String> post(Javalin app, String json) throws Exception {
        return send(app, HttpRequest.newBuilder(uri(app, "/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    private static HttpResponse<String> send(Javalin app, HttpRequest request) throws Exception {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(Javalin app, String path) {
        return URI.create("http://127.0.0.1:" + app.port() + path);
    }
}
