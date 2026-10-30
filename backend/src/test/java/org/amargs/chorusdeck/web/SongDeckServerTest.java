package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.amargs.chorusdeck.deck.Cover;
import org.amargs.chorusdeck.liturgy.LiturgyDay;
import org.amargs.chorusdeck.liturgy.RomanCalendar;
import org.amargs.chorusdeck.liturgy.SundayLibrary;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
            HttpResponse<String> open = send(app, HttpRequest.newBuilder(uri(app, "/api/library")).GET().build());
            assertEquals(200, open.statusCode());
            assertTrue(JSON.readTree(open.body()).get("needsChoice").asBoolean());

            HttpResponse<String> missing = send(app, HttpRequest.newBuilder(uri(app, "/api/songs")).GET().build());
            assertEquals(400, missing.statusCode());
            assertTrue(missing.body().contains("Escolha onde guardar a biblioteca."));

            HttpResponse<String> saved = send(app, HttpRequest.newBuilder(uri(app, "/api/library"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of(
                            "mode", "folder",
                            "directory", chosen.getParent().toString()))))
                    .build());
            assertEquals(200, saved.statusCode());
            JsonNode library = JSON.readTree(saved.body());
            assertEquals(chosen.toAbsolutePath().normalize().toString(), library.get("index").asText());
            assertEquals(chosen.getParent().toAbsolutePath().normalize().toString(), library.get("directory").asText());
            assertEquals("folder", library.get("mode").asText());
            assertEquals(LibraryStore.documents().toString(), library.get("outputDirectory").asText());

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
    void savingASongWritesTheIndexInUse() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck");
        Path shared = dir.resolve("shared");
        Path index = shared.resolve("index.json");
        SongIndex.write(index, List.of(
                new SongCfg(2, "Já estava", List.of("verso"), "4"),
                new SongCfg(7, "Incompleto", List.of())));
        Path other = dir.resolve("data").resolve("index.json");
        SongIndex.write(other, List.of(new SongCfg(1, "Local", List.of("verso"), "1")));
        Javalin app = app(dir, index);
        try {
            HttpResponse<String> created = putSong(app, """
                    {"title":"Nunca esteve no índice","bookId":"A1","content":["primeira","**refrão**"]}
                    """);
            assertEquals(200, created.statusCode(), created.body());
            assertEquals(8, JSON.readTree(created.body()).get("id").asInt());

            HttpResponse<String> edited = putSong(app, """
                    {"id":7,"title":"Incompleto","bookId":"RP3","content":["agora com letra"]}
                    """);
            assertEquals(200, edited.statusCode(), edited.body());

            List<SongCfg> stored = SongIndex.read(index);
            assertEquals(List.of(2, 7, 8), stored.stream().map(SongCfg::id).toList());
            assertEquals("Já estava", stored.get(0).title());
            assertEquals(List.of("verso"), stored.get(0).content());
            assertEquals("4", stored.get(0).bookId());
            assertEquals(List.of("agora com letra"), stored.get(1).content());
            assertEquals("RP3", stored.get(1).bookId());
            assertEquals(List.of("primeira", "**refrão**"), stored.get(2).content());
            assertEquals(List.of(new SongCfg(1, "Local", List.of("verso"), "1")), SongIndex.read(other));

            JsonNode list = JSON.readTree(send(app, HttpRequest.newBuilder(uri(app, "/api/songs")).GET().build()).body());
            assertEquals(3, list.size());
            assertTrue(list.get(2).get("hasVerses").asBoolean());
            assertEquals("A1", list.get(2).get("bookId").asText());
            JsonNode one = JSON.readTree(send(app, HttpRequest.newBuilder(uri(app, "/api/songs/8")).GET().build()).body());
            assertEquals("Nunca esteve no índice", one.get("title").asText());
            assertEquals("**refrão**", one.get("content").get(1).asText());
        } finally {
            app.stop();
        }
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
        assertEquals("/select,\"" + last + "\"", LocalFiles.selectParameters(last));
        assertEquals(
                "/select,\"D:\\Dropbox\\Missa\\Domingo XXX.pptx\"",
                LocalFiles.selectParameters(Path.of("D:\\Dropbox\\Missa\\Domingo XXX.pptx")));
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

    @Test
    void eucharistPreviewAndGenerateUseTheLoadedSunday() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck");
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(new SongCfg(1, "A festa da música", List.of("verso"), "118")));
        LiturgyDay day = new LiturgyDay(
                LocalDate.of(2026, 10, 18),
                "Domingo XXIX",
                "comum",
                null,
                "A",
                new LiturgyDay.Reading("Is 45, 1", "Tomei Ciro"),
                null,
                "Aclamai",
                new LiturgyDay.Reading("Mt 22, 15-21", "Dai a César"),
                null);
        new LibraryStore(dir).useFolder(index.getParent());
        Javalin app = SongDeckServer.create(dir, (date, file) -> day).start("127.0.0.1", 0);
        try {
            HttpResponse<String> preview = send(app, HttpRequest.newBuilder(uri(app, "/api/liturgy?date=2026-10-18")).GET().build());
            assertEquals(200, preview.statusCode());
            assertEquals("Domingo XXIX", JSON.readTree(preview.body()).get("domingo").asText());

            HttpResponse<String> badDate = send(app, HttpRequest.newBuilder(uri(app, "/api/liturgy?date=amanha")).GET().build());
            assertEquals(400, badDate.statusCode());

            HttpResponse<String> generated = post(app, """
                    {"mode":"mass","date":"2026-10-18","output":"missa.pptx","songs":{"entrada":"118"},"opcoes":{"credo":"longo","interval":"vazio"}}
                    """);
            assertEquals(200, generated.statusCode(), generated.body());
            assertTrue(Files.isRegularFile(dir.resolve("missa.pptx")));

            byte[] kept = Files.readAllBytes(dir.resolve("missa.pptx"));
            HttpResponse<String> occupied = post(app, """
                    {"mode":"mass","date":"2026-10-18","output":"missa.pptx","songs":{"entrada":"118"},"opcoes":{"credo":"longo","interval":"vazio"}}
                    """);
            assertEquals(409, occupied.statusCode());
            JsonNode conflict = JSON.readTree(occupied.body());
            assertTrue(conflict.get("exists").asBoolean());
            assertEquals("missa.pptx", conflict.get("name").asText());
            assertArrayEquals(kept, Files.readAllBytes(dir.resolve("missa.pptx")));

            HttpResponse<String> replaced = post(app, """
                    {"mode":"mass","date":"2026-10-18","output":"missa.pptx","replace":true,"songs":{"entrada":"118"},"opcoes":{"credo":"longo","interval":"vazio"}}
                    """);
            assertEquals(200, replaced.statusCode(), replaced.body());
            assertTrue(Files.isRegularFile(dir.resolve("missa.pptx")));
        } finally {
            app.stop();
        }
    }

    @Test
    void bibliotecaSavesSentencesTheNextEucharistReads() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck");
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(new SongCfg(1, "A festa da música", List.of("verso"), "118")));
        Files.writeString(dir.resolve(SundayLibrary.FILE_NAME), """
                {
                  "dates": { "2026-10-18": "ordinary-29/A", "2026-12-06": "advent-2/B" },
                  "sundays": {
                    "ordinary-29/A": {
                      "domingo": "Domingo XXIX",
                      "season": "comum",
                      "primeira": { "referencia": "Is 45, 1.4-6", "citacao": "Frase antiga" },
                      "segunda": { "referencia": "1Ts 1, 1-5", "citacao": "" },
                      "salmo": "",
                      "evangelho": { "referencia": "Mt 22, 15-21", "citacao": "" }
                    },
                    "advent-2/B": {
                      "domingo": "Domingo II",
                      "season": "advento",
                      "primeira": { "referencia": "Is 40, 1-5", "citacao": "" },
                      "salmo": "",
                      "evangelho": { "referencia": "Mc 1, 1-8", "citacao": "" }
                    }
                  }
                }
                """);
        RomanCalendar calendar = RomanCalendar.parse("""
                {
                  "day": { "sunday_id": "ordinary-29", "sunday_cycle": "A", "season": "ordinary" },
                  "readings": {
                    "first_reading": "Is 45:1, 4-6",
                    "second_reading": "1 Thes 1:1-5",
                    "gospel": "Mt 22:15-21"
                  }
                }
                """);
        new LibraryStore(dir).useFolder(index.getParent());
        Javalin app = SongDeckServer.create(dir, (date, file) -> {
            try {
                return new SundayLibrary(file).resolve(date, calendar);
            } catch (Exception e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }).start("127.0.0.1", 0);
        try {
            HttpResponse<String> listed = send(app, HttpRequest.newBuilder(uri(app, "/api/sundays")).GET().build());
            assertEquals(200, listed.statusCode(), listed.body());
            JsonNode days = JSON.readTree(listed.body());
            assertTrue(days.size() > 100);
            assertEquals("advent-1/A", days.get(0).get("key").asText());
            assertEquals("I Domingo do Advento", days.get(0).get("name").asText());
            JsonNode ordinary = null;
            int ordinaryCount = 0;
            for (JsonNode row : days) {
                if ("ordinary-29/A".equals(row.get("key").asText())) {
                    ordinary = row;
                    ordinaryCount++;
                }
            }
            assertEquals(1, ordinaryCount);
            assertEquals("comum", ordinary.get("season").asText());
            assertEquals("A", ordinary.get("ano").asText());
            assertEquals(true, ordinary.get("incomplete").asBoolean());

            HttpResponse<String> saved = send(app, HttpRequest.newBuilder(uri(app, "/api/sundays"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("""
                            {"key":"ordinary-29/A","primeira":"Frase da biblioteca","segunda":"Segunda da biblioteca","salmo":"Salmo da biblioteca","evangelho":"Evangelho da biblioteca","louvor":"Não fica"}
                            """))
                    .build());
            assertEquals(200, saved.statusCode(), saved.body());
            JsonNode preview = JSON.readTree(saved.body());
            assertEquals("Frase da biblioteca", preview.get("primeira").asText());
            assertEquals("Is 45, 1.4-6", preview.get("primeiraRef").asText());
            assertEquals("Evangelho da biblioteca", preview.get("evangelho").asText());
            assertEquals("Mt 22, 15-21", preview.get("evangelhoRef").asText());
            assertEquals("", preview.get("louvor").asText());

            JsonNode stored = JSON.readTree(Files.readString(dir.resolve(SundayLibrary.FILE_NAME)));
            JsonNode sunday = stored.get("sundays").get("ordinary-29/A");
            assertEquals("1Ts 1, 1-5", sunday.get("segunda").get("referencia").asText());
            assertEquals("Segunda da biblioteca", sunday.get("segunda").get("citacao").asText());
            assertFalse(sunday.has("louvor"));
            assertEquals("", stored.get("sundays").get("advent-2/B").get("evangelho").get("citacao").asText());

            HttpResponse<String> again = send(app, HttpRequest.newBuilder(uri(app, "/api/liturgy?date=2026-10-18")).GET().build());
            assertEquals(200, again.statusCode(), again.body());
            JsonNode read = JSON.readTree(again.body());
            assertEquals("Frase da biblioteca", read.get("primeira").get("citacao").asText());
            assertEquals("Evangelho da biblioteca", read.get("frase").asText());
            assertEquals("Salmo da biblioteca", read.get("salmo").asText());

            HttpResponse<String> missing = send(app, HttpRequest.newBuilder(uri(app, "/api/sundays"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"key\":\"not-a-sunday\",\"evangelho\":\"Não está\"}"))
                    .build());
            assertEquals(400, missing.statusCode());
        } finally {
            app.stop();
        }
    }

    @Test
    void eucharistDraftIsServedFromTheAppDirectory() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck");
        Javalin app = SongDeckServer.create(dir).start("127.0.0.1", 0);
        try {
            HttpResponse<String> missing = send(app, HttpRequest.newBuilder(uri(app, "/api/eucharist-draft")).GET().build());
            assertEquals(200, missing.statusCode());
            assertFalse(JSON.readTree(missing.body()).has("date"));

            HttpResponse<String> saved = send(app, HttpRequest.newBuilder(uri(app, "/api/eucharist-draft"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(
                            "{\"date\":\"2026-11-01\",\"form\":{\"frase\":\"A festa\"},\"slots\":{\"entrada\":7}}"))
                    .build());
            assertEquals(200, saved.statusCode());
            assertEquals("A festa", JSON.readTree(saved.body()).path("form").path("frase").asText());

            HttpResponse<String> again = send(app, HttpRequest.newBuilder(uri(app, "/api/eucharist-draft")).GET().build());
            assertEquals(7, JSON.readTree(again.body()).path("slots").path("entrada").asInt());

            HttpResponse<String> rejected = send(app, HttpRequest.newBuilder(uri(app, "/api/eucharist-draft"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"date\":\"domingo\"}"))
                    .build());
            assertEquals(400, rejected.statusCode());
            assertEquals("2026-11-01", JSON.readTree(Files.readString(dir.resolve("eucharist-draft.json"))).get("date").asText());

            HttpResponse<String> cleared = send(app, HttpRequest.newBuilder(uri(app, "/api/eucharist-draft"))
                    .DELETE()
                    .build());
            assertEquals(200, cleared.statusCode());
            assertFalse(Files.exists(dir.resolve("eucharist-draft.json")));
        } finally {
            app.stop();
        }
    }

    @Test
    void anExportedMassIsForgottenOnTheNextVisitUnlessTheDraftChanged() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck");
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(new SongCfg(1, "A festa da música", List.of("verso"), "118")));
        LiturgyDay day = new LiturgyDay(
                LocalDate.of(2026, 10, 18),
                "Domingo XXIX",
                "comum",
                null,
                "A",
                new LiturgyDay.Reading("Is 45, 1", "Tomei Ciro"),
                null,
                "Aclamai",
                new LiturgyDay.Reading("Mt 22, 15-21", "Dai a César"),
                null);
        new LibraryStore(dir).useFolder(index.getParent());
        Javalin app = SongDeckServer.create(dir, (date, file) -> day).start("127.0.0.1", 0);
        try {
            putDraft(app, "{\"date\":\"2026-11-01\",\"form\":{\"frase\":\"A festa\"}}");
            HttpResponse<String> songs = post(app, "{\"ids\":[1],\"output\":\"canto.pptx\"}");
            assertEquals(200, songs.statusCode(), songs.body());
            assertFalse(JSON.readTree(Files.readString(dir.resolve("eucharist-draft.json"))).path("exported").asBoolean());

            HttpResponse<String> generated = post(app, """
                    {"mode":"mass","date":"2026-10-18","output":"missa.pptx","songs":{"entrada":"118"},"opcoes":{"credo":"longo","interval":"vazio"}}
                    """);
            assertEquals(200, generated.statusCode(), generated.body());
            assertTrue(JSON.readTree(Files.readString(dir.resolve("eucharist-draft.json"))).path("exported").asBoolean());

            HttpResponse<String> occupied = post(app, """
                    {"mode":"mass","date":"2026-10-18","output":"missa.pptx","songs":{"entrada":"118"},"opcoes":{"credo":"longo","interval":"vazio"}}
                    """);
            assertEquals(409, occupied.statusCode());
            assertTrue(JSON.readTree(Files.readString(dir.resolve("eucharist-draft.json"))).path("exported").asBoolean());

            HttpResponse<String> nextVisit = send(app, HttpRequest.newBuilder(uri(app, "/api/eucharist-draft")).GET().build());
            assertEquals(200, nextVisit.statusCode());
            assertFalse(JSON.readTree(nextVisit.body()).has("date"));
            assertFalse(Files.exists(dir.resolve("eucharist-draft.json")));

            putDraft(app, "{\"date\":\"2026-11-01\",\"form\":{\"frase\":\"A festa\"}}");
            HttpResponse<String> again = post(app, """
                    {"mode":"mass","date":"2026-10-18","output":"outra.pptx","songs":{"entrada":"118"},"opcoes":{"credo":"longo","interval":"vazio"}}
                    """);
            assertEquals(200, again.statusCode(), again.body());
            putDraft(app, "{\"date\":\"2026-11-01\",\"form\":{\"frase\":\"Mudou\"}}");
            HttpResponse<String> kept = send(app, HttpRequest.newBuilder(uri(app, "/api/eucharist-draft")).GET().build());
            assertEquals("Mudou", JSON.readTree(kept.body()).path("form").path("frase").asText());
            assertFalse(JSON.readTree(Files.readString(dir.resolve("eucharist-draft.json"))).path("exported").asBoolean());
        } finally {
            app.stop();
        }
    }

    @Test
    void serveRemembersStateWithoutAConfigFile() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-serve");
        assertFalse(Files.exists(dir.resolve("config.json")));
        AtomicReference<URI> opened = new AtomicReference<>();
        AtomicReference<Path> profile = new AtomicReference<>();
        SongDeckServer.serve(dir, 0, (uri, folder) -> {
            opened.set(uri);
            profile.set(folder);
        });
        assertTrue(opened.get().toString().startsWith("http://127.0.0.1:"));
        assertTrue(opened.get().toString().endsWith("/"));
        assertEquals(dir.resolve("browser-profile").toAbsolutePath().normalize(), profile.get().toAbsolutePath().normalize());
    }

    @Test
    void aChosenExportFolderIsSuggestedAgain() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck");
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(new SongCfg(1, "A festa da música", List.of("verso"), "118")));
        Path chosen = Files.createDirectories(dir.resolve("destino"));
        Javalin app = app(dir, index);
        try {
            new LibraryStore(dir).useFolder(index.getParent());
            new LibraryStore(dir).saveOutput(chosen);
            HttpResponse<String> again = send(app, HttpRequest.newBuilder(uri(app, "/api/library")).GET().build());
            assertEquals(200, again.statusCode());
            assertEquals(
                    chosen.toAbsolutePath().normalize().toString(),
                    JSON.readTree(again.body()).get("outputDirectory").asText());
        } finally {
            app.stop();
        }
    }

    private static Javalin app(Path dir, Path index) throws Exception {
        return app(dir, index, true);
    }

    private static Javalin app(Path dir, Path index, boolean remember) throws Exception {
        if (remember && Files.isRegularFile(index)) {
            new LibraryStore(dir).useFolder(index.getParent());
        }
        return SongDeckServer.create(dir).start("127.0.0.1", 0);
    }

    private static HttpResponse<String> get(Javalin app, String path) throws Exception {
        try {
            return send(app, HttpRequest.newBuilder(uri(app, path)).GET().build());
        } finally {
            app.stop();
        }
    }

    private static HttpResponse<String> putSong(Javalin app, String json) throws Exception {
        return send(app, HttpRequest.newBuilder(uri(app, "/api/songs"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build());
    }

    private static void putDraft(Javalin app, String json) throws Exception {
        HttpResponse<String> saved = send(app, HttpRequest.newBuilder(uri(app, "/api/eucharist-draft"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build());
        assertEquals(200, saved.statusCode(), saved.body());
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
